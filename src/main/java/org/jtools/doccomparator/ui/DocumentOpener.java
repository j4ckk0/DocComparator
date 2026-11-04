package org.jtools.doccomparator.ui;

import java.nio.file.Path;

/** What a side's head and a drop ask of the comparison: documents opened, and why one could not be. */
interface DocumentOpener {

    /** Opens this document on its side, the other side kept. */
    void open(boolean onLeft, Path path);

    /** Opens two documents, left and right. */
    void open(Path left, Path right);

    /** Whether the left side has its document. */
    boolean leftOpened();

    /** Says, in the status line, why what was asked was not done. */
    void refuse(String why);
}
