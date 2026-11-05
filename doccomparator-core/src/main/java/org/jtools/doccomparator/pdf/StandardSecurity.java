package org.jtools.doccomparator.pdf;

/*-
 * #%L
 * DocComparator
 * %%
 * Copyright (C) 2026 jtools.org
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.jtools.doccomparator.i18n.I18n;
import org.jtools.doccomparator.pdf.PdfObjects.Bytes;

/**
 * The standard security handler of PDF (ISO 32000-2, 7.6.4), with the empty user password: what a file restricted
 * for printing or copying, but opened by anyone, is encrypted with. RC4 (revisions 2 to 4) and AES-128 (4) keys are
 * made from MD5, AES-256 ones (5, 6) from SHA-2. A file that asks a password to be opened is refused.
 */
final class StandardSecurity {

    private static final byte[] PADDING = {
        (byte) 0x28, (byte) 0xBF, (byte) 0x4E, (byte) 0x5E, (byte) 0x4E, (byte) 0x75, (byte) 0x8A, (byte) 0x41,
        (byte) 0x64, (byte) 0x00, (byte) 0x4E, (byte) 0x56, (byte) 0xFF, (byte) 0xFA, (byte) 0x01, (byte) 0x08,
        (byte) 0x2E, (byte) 0x2E, (byte) 0x00, (byte) 0xB6, (byte) 0xD0, (byte) 0x68, (byte) 0x3E, (byte) 0x80,
        (byte) 0x2F, (byte) 0x0C, (byte) 0xA9, (byte) 0xFE, (byte) 0x64, (byte) 0x53, (byte) 0x69, (byte) 0x7A};

    private enum Method {
        NONE, RC4, AES128, AES256
    }

    private final byte[] key;
    private final Method strings;
    private final Method streams;

    private StandardSecurity(byte[] key, Method strings, Method streams) {
        this.key = key;
        this.strings = strings;
        this.streams = streams;
    }

    static StandardSecurity of(Map<String, Object> encrypt, byte[] id, PdfFile file) throws PdfException {
        if (!"Standard".equals(PdfObjects.name(file.resolve(encrypt.get("Filter"))))) {
            throw new PdfException(file.error(I18n.text("error.pdfEncryption")));
        }
        int version = PdfObjects.integer(file.resolve(encrypt.get("V")), 0);
        int revision = PdfObjects.integer(file.resolve(encrypt.get("R")), 0);
        byte[] owner = bytes(file.resolve(encrypt.get("O")));
        byte[] user = bytes(file.resolve(encrypt.get("U")));
        int permissions = PdfObjects.integer(file.resolve(encrypt.get("P")), 0);
        boolean metadata = !Boolean.FALSE.equals(file.resolve(encrypt.get("EncryptMetadata")));
        Method strings = Method.RC4;
        Method streams = Method.RC4;
        if (version >= 4) {
            Map<String, Object> filters = PdfObjects.dictionary(file.resolve(encrypt.get("CF")));
            strings = method(filters, PdfObjects.name(file.resolve(encrypt.get("StrF"))), file);
            streams = method(filters, PdfObjects.name(file.resolve(encrypt.get("StmF"))), file);
        }
        if (owner == null || user == null || revision < 2 || revision > 6) {
            throw new PdfException(file.error(I18n.text("error.pdfEncryption")));
        }
        byte[] key;
        if (revision >= 5) {
            if (user.length < 48) {
                throw new PdfException(file.error(I18n.text("error.pdfEncryption")));
            }
            byte[] none = new byte[0];
            byte[] check = hash(none, Arrays.copyOfRange(user, 32, 40), none, revision);
            if (!Arrays.equals(check, 0, 32, user, 0, 32)) {
                throw new PdfException(file.error(I18n.text("error.pdfPassword")));
            }
            byte[] intermediate = hash(none, Arrays.copyOfRange(user, 40, 48), none, revision);
            byte[] userKey = bytes(file.resolve(encrypt.get("UE")));
            if (userKey == null || userKey.length < 32) {
                throw new PdfException(file.error(I18n.text("error.pdfEncryption")));
            }
            key = aes(Cipher.DECRYPT_MODE, intermediate, new byte[16], Arrays.copyOf(userKey, 32));
        } else {
            int length = revision == 2 ? 5 : PdfObjects.integer(file.resolve(encrypt.get("Length")), 40) / 8;
            length = Math.max(5, Math.min(16, length));
            MessageDigest md5 = digest("MD5");
            md5.update(PADDING);
            md5.update(Arrays.copyOf(owner, Math.min(owner.length, 32)));
            md5.update(new byte[] {(byte) permissions, (byte) (permissions >> 8), (byte) (permissions >> 16),
                (byte) (permissions >> 24)});
            md5.update(id);
            if (revision >= 4 && !metadata) {
                md5.update(new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF});
            }
            key = Arrays.copyOf(md5.digest(), length);
            if (revision >= 3) {
                for (int i = 0; i < 50; i++) {
                    key = Arrays.copyOf(digest("MD5").digest(key), length);
                }
            }
            byte[] expected;
            int compared;
            if (revision == 2) {
                expected = rc4(key, PADDING);
                compared = 32;
            } else {
                MessageDigest check = digest("MD5");
                check.update(PADDING);
                check.update(id);
                expected = rc4(key, check.digest());
                for (int i = 1; i <= 19; i++) {
                    byte[] round = new byte[key.length];
                    for (int k = 0; k < key.length; k++) {
                        round[k] = (byte) (key[k] ^ i);
                    }
                    expected = rc4(round, expected);
                }
                compared = 16;
            }
            if (user.length < compared || !Arrays.equals(expected, 0, compared, user, 0, compared)) {
                throw new PdfException(file.error(I18n.text("error.pdfPassword")));
            }
        }
        return new StandardSecurity(key, strings, streams);
    }

    private static Method method(Map<String, Object> filters, String name, PdfFile file) throws PdfException {
        if (name == null || name.equals("Identity")) {
            return Method.NONE;
        }
        Map<String, Object> filter = filters == null ? null : PdfObjects.dictionary(file.resolve(filters.get(name)));
        String method = filter == null ? null : PdfObjects.name(file.resolve(filter.get("CFM")));
        return switch (method == null ? "None" : method) {
            case "V2" -> Method.RC4;
            case "AESV2" -> Method.AES128;
            case "AESV3" -> Method.AES256;
            case "None" -> Method.NONE;
            default -> throw new PdfException(file.error(I18n.text("error.pdfEncryption")));
        };
    }

    private static byte[] bytes(Object value) {
        return value instanceof Bytes bytes ? bytes.value() : null;
    }

    byte[] decryptString(byte[] bytes, int number, int generation) {
        return decrypt(strings, bytes, number, generation);
    }

    byte[] decryptStream(byte[] bytes, int number, int generation, Map<String, Object> dictionary) {
        if ("Metadata".equals(PdfObjects.name(dictionary.get("Type")))) {
            return bytes;   // read by nothing here, and often left in the clear
        }
        if (dictionary.get("Filter") instanceof PdfObjects.Name filter && filter.value().equals("Crypt")
                || dictionary.get("Filter") instanceof java.util.List<?> list && !list.isEmpty()
                && list.getFirst() instanceof PdfObjects.Name first && first.value().equals("Crypt")) {
            return bytes;   // its own crypt filter: Identity is the only one written in practice
        }
        return decrypt(streams, bytes, number, generation);
    }

    private byte[] decrypt(Method method, byte[] bytes, int number, int generation) {
        if (method == Method.NONE) {
            return bytes;
        }
        if (method == Method.AES256) {
            return aesData(key, bytes);
        }
        MessageDigest md5 = digest("MD5");
        md5.update(key);
        md5.update(new byte[] {(byte) number, (byte) (number >> 8), (byte) (number >> 16), (byte) generation,
            (byte) (generation >> 8)});
        if (method == Method.AES128) {
            md5.update("sAlT".getBytes(StandardCharsets.ISO_8859_1));
        }
        byte[] objectKey = Arrays.copyOf(md5.digest(), Math.min(key.length + 5, 16));
        return method == Method.RC4 ? rc4(objectKey, bytes) : aesData(objectKey, bytes);
    }

    /** AES in CBC mode, the first 16 bytes the vector; a damaged padding kept as it is rather than refused. */
    private static byte[] aesData(byte[] key, byte[] bytes) {
        if (bytes.length < 32 || bytes.length % 16 != 0) {
            return bytes.length <= 16 ? new byte[0] : bytes;
        }
        try {
            byte[] plain = aes(Cipher.DECRYPT_MODE, key, Arrays.copyOf(bytes, 16),
                    Arrays.copyOfRange(bytes, 16, bytes.length));
            int pad = plain.length == 0 ? 0 : plain[plain.length - 1] & 0xFF;
            return pad >= 1 && pad <= 16 && pad <= plain.length ? Arrays.copyOf(plain, plain.length - pad) : plain;
        } catch (PdfException _) {
            return bytes;
        }
    }

    private static byte[] aes(int mode, byte[] key, byte[] iv, byte[] bytes) throws PdfException {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(mode, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            return cipher.doFinal(bytes);
        } catch (GeneralSecurityException e) {
            throw new PdfException(e.getMessage());
        }
    }

    /** The hash of revisions 5 (SHA-256) and 6 (ISO 32000-2, algorithm 2.B), for the empty password here. */
    private static byte[] hash(byte[] password, byte[] salt, byte[] userData, int revision) throws PdfException {
        MessageDigest sha256 = digest("SHA-256");
        sha256.update(password);
        sha256.update(salt);
        sha256.update(userData);
        byte[] k = sha256.digest();
        if (revision == 5) {
            return k;
        }
        for (int round = 0; ; ) {
            int unit = password.length + k.length + userData.length;
            byte[] k1 = new byte[unit * 64];
            for (int i = 0; i < 64; i++) {
                System.arraycopy(password, 0, k1, i * unit, password.length);
                System.arraycopy(k, 0, k1, i * unit + password.length, k.length);
                System.arraycopy(userData, 0, k1, i * unit + password.length + k.length, userData.length);
            }
            byte[] e = aes(Cipher.ENCRYPT_MODE, Arrays.copyOf(k, 16), Arrays.copyOfRange(k, 16, 32), k1);
            int sum = 0;
            for (int i = 0; i < 16; i++) {
                sum += e[i] & 0xFF;
            }
            k = digest(switch (sum % 3) {
                case 0 -> "SHA-256";
                case 1 -> "SHA-384";
                default -> "SHA-512";
            }).digest(e);
            round++;
            if (round >= 64 && (e[e.length - 1] & 0xFF) <= round - 32) {
                return Arrays.copyOf(k, 32);
            }
        }
    }

    static byte[] rc4(byte[] key, byte[] bytes) {
        int[] s = new int[256];
        for (int i = 0; i < 256; i++) {
            s[i] = i;
        }
        for (int i = 0, j = 0; i < 256; i++) {
            j = (j + s[i] + (key[i % key.length] & 0xFF)) & 0xFF;
            int t = s[i];
            s[i] = s[j];
            s[j] = t;
        }
        byte[] out = new byte[bytes.length];
        for (int n = 0, i = 0, j = 0; n < bytes.length; n++) {
            i = (i + 1) & 0xFF;
            j = (j + s[i]) & 0xFF;
            int t = s[i];
            s[i] = s[j];
            s[j] = t;
            out[n] = (byte) (bytes[n] ^ s[(s[i] + s[j]) & 0xFF]);
        }
        return out;
    }

    private static MessageDigest digest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every JDK has " + algorithm, e);
        }
    }
}
