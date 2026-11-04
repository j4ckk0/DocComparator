/*
 * Copyright 2026 j4ckk0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jtools.doccomparator.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.jtools.doccomparator.document.Picture;
import org.junit.jupiter.api.Test;

/** Pictures decoded from documents, which may have been made to harm: bounded, and writing nothing. */
class PictureImagesTest {

    @Test
    void nothingIsCachedOnDisk() {
        PictureImages.noCacheOnDisk();
        assertFalse(ImageIO.getUseCache(), "ImageIO would write its cache in the temporary directory");
    }

    @Test
    void aLargePictureIsDecodedSmaller() throws IOException {
        BufferedImage wide = new BufferedImage(3000, 40, BufferedImage.TYPE_INT_RGB);
        BufferedImage decoded = PictureImages.of(image("wide", png(wide)));
        assertNotNull(decoded);
        assertTrue(decoded.getWidth() <= PictureImages.PREVIEW, "decoded " + decoded.getWidth() + " wide");
    }

    @Test
    void aPictureSayingItIsHugeIsNotDecoded() {
        // a PNG whose header says 60000 × 60000 pixels, and holds none: refused on its word, not decoded
        assertNull(PictureImages.of(image("huge", pngHeader(60000, 60000))));
    }

    @Test
    void whatIsNoImageIsNotDecoded() {
        assertNull(PictureImages.of(image("garbage", new byte[] {1, 2, 3, 4})));
        assertNull(PictureImages.of(new Picture(Picture.Kind.LINKED, null, 10, 10, "png", "link:x", null)));
    }

    private static Picture image(String digest, byte[] bytes) {
        return new Picture(Picture.Kind.IMAGE, null, 100, 100, "png", digest, bytes);
    }

    private static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PictureImages.noCacheOnDisk();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    /** A PNG's signature and header (IHDR), nothing else. */
    private static byte[] pngHeader(int width, int height) {
        ByteBuffer header = ByteBuffer.allocate(13).putInt(width).putInt(height).put(new byte[] {8, 2, 0, 0, 0});
        CRC32 crc = new CRC32();
        crc.update("IHDR".getBytes());
        crc.update(header.array());
        return ByteBuffer.allocate(8 + 4 + 4 + 13 + 4)
                .put(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'})
                .putInt(13).put("IHDR".getBytes()).put(header.array()).putInt((int) crc.getValue()).array();
    }
}
