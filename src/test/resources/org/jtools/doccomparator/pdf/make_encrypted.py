#!/usr/bin/env python3
"""
Writes the encrypted PDFs PdfReaderTest reads, each with the same page — "Encrypted text reads the same." in
Helvetica, its widths those of the standard font, its family a string, so that strings are decrypted as well as
streams:

  aes128.pdf     AES-128 (revision 4, AESV2), opened without a password
  aes256.pdf     AES-256 (revision 6, AESV3), opened without a password
  rc4-40.pdf     RC4 40 bits (revision 2), opened without a password: Ghostscript's
  rc4-128.pdf    RC4 128 bits (revision 3), opened without a password: Ghostscript's
  password.pdf   RC4 128 bits, asking a password to be opened: Ghostscript's

The AES ones are written here, from ISO 32000-2, 7.6: no tool of this machine writes them; the RC4 ones by
Ghostscript (gs) from the plain file. Needs Python's "cryptography" package and Ghostscript.

    python3 src/test/resources/org/jtools/doccomparator/pdf/make_encrypted.py
"""

import hashlib
import os
import struct
import subprocess
import tempfile

from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes

HERE = os.path.dirname(os.path.abspath(__file__))
PADDING = bytes.fromhex('28BF4E5E4E758A4164004E56FFFA01082E2E00B6D0683E802F0CA9FE6453697A')
PERMISSIONS = -4
FILE_ID = bytes.fromhex('0123456789ABCDEF0123456789ABCDEF')
CONTENT = b'BT /F1 12 Tf 72 700 Td (Encrypted text reads the same.) Tj ET\n'


def objects(family):
    """The file's objects, by number: (dictionary, stream bytes or None); the family a string to encrypt."""
    return {
        1: (b'<< /Type /Catalog /Pages 2 0 R >>', None),
        2: (b'<< /Type /Pages /Kids [3 0 R] /Count 1 /MediaBox [0 0 612 792] >>', None),
        3: (b'<< /Type /Page /Parent 2 0 R /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>', None),
        4: (b'<< >>', CONTENT),
        5: (b'<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding '
            b'/FontDescriptor 6 0 R >>', None),
        6: (b'<< /Type /FontDescriptor /FontName /Helvetica /Flags 32 /FontFamily <' + family.hex().encode()
            + b'> >>', None),
    }


def write(path, objs, encrypt=None):
    out = bytearray(b'%PDF-1.7\n%\xe2\xe3\xcf\xd3\n')
    offsets = {}
    numbers = sorted(objs)
    if encrypt is not None:
        numbers.append(max(numbers) + 1)
        objs = dict(objs)
        objs[numbers[-1]] = (encrypt, None)
    for n in numbers:
        dictionary, stream = objs[n]
        offsets[n] = len(out)
        out += b'%d 0 obj\n' % n
        if stream is None:
            out += dictionary
        else:
            out += dictionary[:dictionary.rindex(b'>>')] + b' /Length %d >>\nstream\n' % len(stream) + stream
            out += b'\nendstream'
        out += b'\nendobj\n'
    xref = len(out)
    out += b'xref\n0 %d\n0000000000 65535 f \n' % (len(numbers) + 1)
    for n in numbers:
        out += b'%010d 00000 n \n' % offsets[n]
    trailer = b'<< /Size %d /Root 1 0 R /ID [<%s> <%s>]' % (len(numbers) + 1, FILE_ID.hex().encode(),
                                                          FILE_ID.hex().encode())
    if encrypt is not None:
        trailer += b' /Encrypt %d 0 R' % numbers[-1]
    out += b'trailer\n' + trailer + b' >>\nstartxref\n%d\n%%%%EOF\n' % xref
    with open(path, 'wb') as f:
        f.write(out)


def aes_cbc(key, data, iv=None, pad=True):
    if pad:
        n = 16 - len(data) % 16
        data = data + bytes([n]) * n
    iv = os.urandom(16) if iv is None else iv
    encryptor = Cipher(algorithms.AES(key), modes.CBC(iv)).encryptor()
    return iv + encryptor.update(data) + encryptor.finalize()


def rc4(key, data):
    s = list(range(256))
    j = 0
    for i in range(256):
        j = (j + s[i] + key[i % len(key)]) & 0xFF
        s[i], s[j] = s[j], s[i]
    out = bytearray()
    i = j = 0
    for b in data:
        i = (i + 1) & 0xFF
        j = (j + s[i]) & 0xFF
        s[i], s[j] = s[j], s[i]
        out.append(b ^ s[(s[i] + s[j]) & 0xFF])
    return bytes(out)


def encrypted(objs, object_key):
    """Each stream and the font family's string, encrypted with its object's key."""
    result = {}
    for n, (dictionary, stream) in objs.items():
        key = object_key(n)
        if n == 6:
            start = dictionary.index(b'/FontFamily <') + len(b'/FontFamily <')
            end = dictionary.index(b'>', start)
            plain = bytes.fromhex(dictionary[start:end].decode())
            dictionary = dictionary[:start] + aes_or_rc4(key, plain).hex().encode() + dictionary[end:]
        result[n] = (dictionary, None if stream is None else aes_or_rc4(key, stream))
    return result


aes_or_rc4 = None


def aes128(path):
    global aes_or_rc4
    owner_key = hashlib.md5(b'owner'.ljust(32, b'\0')[:5] + PADDING[:27]).digest()
    for _ in range(50):
        owner_key = hashlib.md5(owner_key).digest()
    o = rc4(owner_key, PADDING)
    for i in range(1, 20):
        o = rc4(bytes(b ^ i for b in owner_key), o)
    key = hashlib.md5(PADDING + o + struct.pack('<i', PERMISSIONS) + FILE_ID).digest()
    for _ in range(50):
        key = hashlib.md5(key).digest()
    u = rc4(key, hashlib.md5(PADDING + FILE_ID).digest())
    for i in range(1, 20):
        u = rc4(bytes(b ^ i for b in key), u)
    u += bytes(16)
    aes_or_rc4 = aes_cbc
    objs = encrypted(objects(b'Encrypted Family'),
                     lambda n: hashlib.md5(key + struct.pack('<i', n)[:3] + b'\0\0' + b'sAlT').digest())
    encrypt = (b'<< /Filter /Standard /V 4 /R 4 /Length 128 /CF << /StdCF << /CFM /AESV2 /Length 16 '
               b'/AuthEvent /DocOpen >> >> /StmF /StdCF /StrF /StdCF /O <' + o.hex().encode() + b'> /U <'
               + u.hex().encode() + b'> /P %d >>' % PERMISSIONS)
    write(path, objs, encrypt)


def hash_2b(password, salt, user):
    k = hashlib.sha256(password + salt + user).digest()
    rounds = 0
    while True:
        k1 = (password + k + user) * 64
        e = Cipher(algorithms.AES(k[:16]), modes.CBC(k[16:32])).encryptor().update(k1)
        k = {0: hashlib.sha256, 1: hashlib.sha384, 2: hashlib.sha512}[sum(e[:16]) % 3](e).digest()
        rounds += 1
        if rounds >= 64 and e[-1] <= rounds - 32:
            return k[:32]


def aes256(path):
    global aes_or_rc4
    key = os.urandom(32)
    validation, key_salt = os.urandom(8), os.urandom(8)
    u = hash_2b(b'', validation, b'') + validation + key_salt
    ue = aes_cbc(hash_2b(b'', key_salt, b''), key, iv=bytes(16), pad=False)[16:]
    owner_validation, owner_salt = os.urandom(8), os.urandom(8)
    o = hash_2b(b'owner', owner_validation, u) + owner_validation + owner_salt
    oe = aes_cbc(hash_2b(b'owner', owner_salt, u), key, iv=bytes(16), pad=False)[16:]
    perms_plain = struct.pack('<i', PERMISSIONS) + b'\xff\xff\xff\xff' + b'Tadb' + os.urandom(4)
    perms = Cipher(algorithms.AES(key), modes.ECB()).encryptor().update(perms_plain)
    aes_or_rc4 = aes_cbc
    objs = encrypted(objects(b'Encrypted Family'), lambda n: key)
    encrypt = (b'<< /Filter /Standard /V 5 /R 6 /Length 256 /CF << /StdCF << /CFM /AESV3 /Length 32 '
               b'/AuthEvent /DocOpen >> >> /StmF /StdCF /StrF /StdCF /O <' + o.hex().encode() + b'> /U <'
               + u.hex().encode() + b'> /OE <' + oe.hex().encode() + b'> /UE <' + ue.hex().encode()
               + b'> /Perms <' + perms.hex().encode() + b'> /P %d >>' % PERMISSIONS)
    write(path, objs, encrypt)


def main():
    aes128(os.path.join(HERE, 'aes128.pdf'))
    aes256(os.path.join(HERE, 'aes256.pdf'))
    with tempfile.TemporaryDirectory() as temporary:
        plain = os.path.join(temporary, 'plain.pdf')
        write(plain, objects(b'Encrypted Family'))
        for name, options in (('rc4-40.pdf', ['-dEncryptionR=2', '-dKeyLength=40']),
                              ('rc4-128.pdf', ['-dEncryptionR=3', '-dKeyLength=128']),
                              ('password.pdf', ['-dEncryptionR=3', '-dKeyLength=128', '-sUserPassword=secret'])):
            subprocess.run(['gs', '-q', '-sDEVICE=pdfwrite', '-sOwnerPassword=owner', '-dPermissions=-4', *options,
                            '-o', os.path.join(HERE, name), plain], check=True)
    print('written in', HERE)


if __name__ == '__main__':
    main()
