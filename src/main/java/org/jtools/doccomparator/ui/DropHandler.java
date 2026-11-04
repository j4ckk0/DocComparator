package org.jtools.doccomparator.ui;

import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;

/**
 * Documents dropped: two at once, left and right; one, on the side it is dropped on (or the side given). As a list
 * of files (Windows, macOS) or of file URIs (Linux). Anything else goes to the handler given, if any: text dropped
 * on a path field is typed in it.
 */
final class DropHandler extends TransferHandler {

    /** A Linux desktop's way of dropping files: their URIs, one a line (FileUris). */
    private static final DataFlavor URI_LIST = uriList();

    private final DocumentOpener opener;
    /** What a document dropped without a side is dropped on: on its left half, it goes to the left. */
    private final JComponent area;
    private final Boolean side;
    private final TransferHandler otherwise;

    /** Documents dropped on the area: on the side given, or the half they are dropped on (side null). */
    DropHandler(DocumentOpener opener, JComponent area, Boolean side) {
        this(opener, area, side, null);
    }

    DropHandler(DocumentOpener opener, JComponent area, Boolean side, TransferHandler otherwise) {
        this.opener = opener;
        this.area = area;
        this.side = side;
        this.otherwise = otherwise;
    }

    private static DataFlavor uriList() {
        try {
            return new DataFlavor("text/uri-list;class=java.lang.String");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    private boolean hasFiles(TransferSupport support) {
        return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor) || support.isDataFlavorSupported(URI_LIST);
    }

    @Override
    public boolean canImport(TransferSupport support) {
        if (hasFiles(support)) {
            if (support.isDrop()) {
                support.setDropAction(COPY);
            }
            return true;
        }
        return otherwise != null && otherwise.canImport(support);
    }

    @Override
    public boolean importData(TransferSupport support) {
        if (!hasFiles(support)) {
            return otherwise != null && otherwise.importData(support);
        }
        List<Path> files = files(support);
        if (files.isEmpty()) {
            return false;
        }
        if (files.size() >= 2) {
            opener.open(files.get(0), files.get(1));
        } else {
            boolean onLeft = side != null ? side : leftHalf(support);
            opener.open(onLeft, files.getFirst());
        }
        return true;
    }

    private boolean leftHalf(TransferSupport support) {
        if (!support.isDrop()) {
            return !opener.leftOpened();   // pasted: the side not open yet, the left first
        }
        Point at = SwingUtilities.convertPoint(support.getComponent(), support.getDropLocation().getDropPoint(), area);
        return at.x < area.getWidth() / 2;
    }

    private List<Path> files(TransferSupport support) {
        try {
            Transferable data = support.getTransferable();
            if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                @SuppressWarnings("unchecked")
                List<File> files = (List<File>) data.getTransferData(DataFlavor.javaFileListFlavor);
                if (!files.isEmpty()) {
                    return files.stream().map(File::toPath).toList();
                }
            }
            if (support.isDataFlavorSupported(URI_LIST)) {
                return FileUris.paths((String) data.getTransferData(URI_LIST));
            }
        } catch (UnsupportedFlavorException | IOException e) {
            opener.refuse("Not dropped: " + e.getMessage());
        }
        return List.of();
    }

    @Override
    public int getSourceActions(JComponent component) {
        return otherwise != null ? otherwise.getSourceActions(component) : NONE;
    }
}
