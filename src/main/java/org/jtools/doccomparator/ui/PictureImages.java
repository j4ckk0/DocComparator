package org.jtools.doccomparator.ui;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.jtools.doccomparator.document.Block;
import org.jtools.doccomparator.document.Paragraph;
import org.jtools.doccomparator.document.Picture;
import org.jtools.doccomparator.document.Run;
import org.jtools.doccomparator.document.Story;
import org.jtools.doccomparator.document.Table;
import org.jtools.doccomparator.document.TextBox;
import org.jtools.doccomparator.document.WordDocument;

/**
 * The pictures of the documents as images to draw, decoded once each, by digest, with the JDK alone (PNG, JPEG, GIF,
 * BMP, TIFF; not EMF, WMF, which are shown as what they are).
 *
 * <p>A picture comes from a document, which may have been made to harm: its size is read before it is decoded, and it
 * is decoded at most PREVIEW pixels on its longer side, a line in so many skipped — whatever it says it holds, it takes
 * no more memory than that; one larger than LARGEST in all is not decoded. Nothing is written: ImageIO's cache, on disk
 * in the temporary directory by default, is turned off (README, "No network").
 */
final class PictureImages {

    /** The longer side of a picture as decoded, in pixels: more than a side of the view shows. */
    static final int PREVIEW = 1000;
    /** The most pixels a picture may say it has to be decoded at all. */
    static final long LARGEST = 100_000_000L;

    private static final Map<String, Optional<BufferedImage>> DECODED = new ConcurrentHashMap<>();

    static {
        ImageIO.setUseCache(false);
    }

    private PictureImages() {
    }

    /** Turns off ImageIO's cache on disk, before anything reads an image. */
    static void noCacheOnDisk() {
        ImageIO.setUseCache(false);
    }

    /** The image of a picture, decoded; null for one that cannot be (linked, too large, a format the JDK has not). */
    static BufferedImage of(Picture picture) {
        if (picture.kind() != Picture.Kind.IMAGE || picture.bytes() == null) {
            return null;
        }
        return DECODED.computeIfAbsent(picture.digest(), digest -> Optional.ofNullable(decode(picture.bytes())))
                .orElse(null);
    }

    /**
     * Decodes every picture of these documents, away from the window's thread: drawing them then takes no time. Those
     * of documents no longer shown are let go.
     */
    static void prepare(WordDocument... documents) {
        Set<String> shown = new HashSet<>();
        for (WordDocument document : documents) {
            List<Story> stories = new ArrayList<>(List.of(document.body()));
            stories.addAll(document.headersAndFooters());
            stories.addAll(document.notes());
            for (Story story : stories) {
                prepare(story.blocks(), shown);
            }
        }
        DECODED.keySet().retainAll(shown);
    }

    private static void prepare(List<Block> blocks, Set<String> shown) {
        for (Block block : blocks) {
            switch (block) {
                case Paragraph paragraph -> {
                    for (Run run : paragraph.runs()) {
                        if (run.picture() != null) {
                            shown.add(run.picture().digest());
                            of(run.picture());
                        }
                    }
                }
                case TextBox box -> prepare(box.blocks(), shown);
                case Table table -> {
                    for (Table.Row row : table.rows()) {
                        for (Table.Cell cell : row.cells()) {
                            prepare(cell.blocks(), shown);
                        }
                    }
                }
            }
        }
    }

    private static BufferedImage decode(byte[] bytes) {
        try (ImageInputStream in = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return null;   // a format the JDK cannot read: EMF, WMF…
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                long width = reader.getWidth(0);
                long height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width * height > LARGEST) {
                    return null;
                }
                int step = (int) Math.max(1, Math.ceil(Math.max(width, height) / (double) PREVIEW));
                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceSubsampling(step, step, 0, 0);
                return reader.read(0, param);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            return null;   // a picture that cannot be read, or not in the memory there is, is shown as such
        }
    }
}
