package org.jtools.doccomparator.ui;

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

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JFileChooser;
import javax.swing.JMenu;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.jtools.doccomparator.compare.CompareOptions;
import org.jtools.doccomparator.compare.Comparison;
import org.jtools.doccomparator.compare.DocumentComparer;
import org.jtools.doccomparator.document.Document;
import org.jtools.doccomparator.i18n.I18n;
import org.jtools.doccomparator.reader.DocumentReader;
import org.jtools.doccomparator.reader.DocumentReaders;
import org.jtools.doccomparator.report.ComparisonWords;

/**
 * The comparison of two documents, all of it but the window: the ribbon (OptionControls), the head of each side
 * (DocumentHeader), the two sides, the map of the differences, and the status line (StatusLine). Keeps the
 * documents, reads and compares them away from the window's thread, and goes through the differences. Documents are
 * opened from a side's head, by dropping them on their side, or given at the start; text pasted or dropped on a side
 * is compared as a document.
 *
 * <p>Another program shows it as any component: open() gives it two files, or two DocumentSource of the program's
 * own; onCompared() tells each comparison; fileActions(), goActions() and the two menus are its commands, to put in the
 * program's own menus and tool bars. The language and the look are the program's: it calls languageChanged() once
 * I18n speaks another, and changes the look when onDarkMode() tells it Dark was ticked.
 */
public final class ComparatorPanel extends JPanel {

    /** Between the two documents' names, in the window's title. */
    private static final String BETWEEN_NAMES = " ↔ ";
    /** The name of a side without its document yet. */
    private static final String NO_NAME = "…";

    private final ComparisonView view = new ComparisonView();
    private final JScrollPane scroll = new JScrollPane(view);
    /** The labels of the panel and of its parts, which it gives them: set again together when the language changes. */
    private final I18nLabels labels = new I18nLabels();
    private final StatusLine statusLine = new StatusLine(labels);
    private final BackgroundWork work = new BackgroundWork(this, statusLine);
    private final Action openLeft = action("action.openLeft", "action.openLeft.tip",
            KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK), () -> choose(Side.LEFT));
    private final Action openRight = action("action.openRight", "action.openRight.tip",
            KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK),
            () -> choose(Side.RIGHT));
    private final Action pasteHere = action("action.pasteHere", "action.pasteHere.tip",
            KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK), () -> paste(view.chosenSide()));
    private final Action pasteLeft = action("action.pasteLeft", "action.pasteLeft.tip", null, () -> paste(Side.LEFT));
    private final Action pasteRight = action("action.pasteRight", "action.pasteRight.tip", null,
            () -> paste(Side.RIGHT));
    private final Action swap = action("action.swap", "action.swap.tip", null, this::swap);
    private final Action previous = action("action.previous", "action.previous.tip",
            KeyStroke.getKeyStroke(KeyEvent.VK_F7, InputEvent.SHIFT_DOWN_MASK), () -> goToDifference(false));
    private final Action next = action("action.next", "action.next.tip", KeyStroke.getKeyStroke(KeyEvent.VK_F7, 0),
            () -> goToDifference(true));
    private final OptionControls controls = new OptionControls(labels, this::compare, this::shownChanged);
    private final DocumentOpener opener = new DocumentOpener() {
        @Override
        public void open(Side side, Path path) {
            ComparatorPanel.this.open(side, path);
        }

        @Override
        public void open(Path left, Path right) {
            ComparatorPanel.this.open(left, right);
        }

        @Override
        public void paste(Side side, String text) {
            ComparatorPanel.this.paste(side, text);
        }

        @Override
        public Side chosenSide() {
            return view.chosenSide();
        }

        @Override
        public void refuse(String why) {
            statusLine.say(why);
        }
    };
    /** The head of each side: its own, whatever the side is given (swapping the sides keeps the heads in place). */
    private final Map<Side, DocumentHeader> heads = new EnumMap<>(Side.class);
    private final Map<Side, SideState> sides = new EnumMap<>(Map.of(Side.LEFT, SideState.EMPTY,
            Side.RIGHT, SideState.EMPTY));
    private Comparison comparison;
    private Consumer<Comparison> comparedListener = _ -> { };
    private Consumer<String> namesListener = _ -> { };
    private File lastDirectory;

    public ComparatorPanel() {
        super(new BorderLayout());
        heads.put(Side.LEFT, new DocumentHeader(Side.LEFT, openLeft, opener, this, labels));
        heads.put(Side.RIGHT, new DocumentHeader(Side.RIGHT, openRight, opener, this, labels));
        showEmptySides();
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.setBorder(BorderFactory.createEmptyBorder());

        JPanel headers = new JPanel(new HeadsLayout(view, scroll, heads));
        headers.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
        headers.add(heads.get(Side.LEFT));
        headers.add(heads.get(Side.RIGHT));
        view.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                headers.revalidate();
            }
        });
        JPanel top = new JPanel(new BorderLayout());
        top.add(controls.ribbon(previous, next, swap), BorderLayout.NORTH);
        top.add(headers, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout());
        center.add(scroll, BorderLayout.CENTER);
        center.add(new DifferenceMap(view, scroll, labels), BorderLayout.EAST);
        add(center, BorderLayout.CENTER);
        add(statusLine, BorderLayout.SOUTH);

        view.addPropertyChangeListener(ComparisonView.CURRENT_LINE, _ -> updatePosition());
        view.addPropertyChangeListener(ComparisonView.CHOSEN_SIDE, _ -> showChosenSide());
        showChosenSide();
        view.setComponentPopupMenu(new ClipboardMenu(view, pasteHere, labels));
        TransferHandler drop = new DropHandler(opener, this, null);
        view.setTransferHandler(drop);
        setTransferHandler(drop);
        for (Action action : List.of(openLeft, openRight, pasteHere, previous, next, controls.ribbonAction())) {
            KeyStroke key = (KeyStroke) action.getValue(Action.ACCELERATOR_KEY);
            getInputMap(WHEN_IN_FOCUSED_WINDOW).put(key, action);
            getActionMap().put(action, action);
        }
        showEmptyView();
        updateActions();
    }

    /**
     * The language has changed: every label in it, and what is shown said again — the comparison holds no words of its
     * own, the view puts it into words as it shows it: nothing is read or compared again.
     */
    public void languageChanged() {
        labels.apply();
        namesListener.accept(documentNames());
        if (comparison != null) {
            show(comparison);
        } else {
            compare();   // one document alone, or none: shown again from what is read already
        }
    }

    /** Told the documents' names each time they change (documentNames()), for the window's title. */
    public void onDocumentNames(Consumer<String> listener) {
        namesListener = listener;
        listener.accept(documentNames());
    }

    /** Told when Dark is ticked or not, in the ribbon or the View menu: the look is the program's to change. */
    public void onDarkMode(Consumer<Boolean> listener) {
        controls.onDarkMode(listener);
    }

    /** Shows whether the look is dark, in the ribbon's Dark box and the View menu's Dark item, telling no one. */
    public void showDarkMode(boolean dark) {
        controls.showDarkMode(dark);
    }

    /** Told when the ribbon is opened (true) or collapsed. */
    public void onRibbon(Consumer<Boolean> listener) {
        controls.onRibbon(listener);
    }

    /** Opens the ribbon, or collapses it to its strip, telling no one. */
    public void showRibbon(boolean open) {
        controls.showRibbon(open);
    }

    /** Open the left or the right document, paste on the left or the right, swap the sides: for a File menu. */
    public List<Action> fileActions() {
        return List.of(openLeft, openRight, pasteLeft, pasteRight, swap);
    }

    /** The previous and the next difference (Shift+F7, F7), for a Go menu or a tool bar. */
    public List<Action> goActions() {
        return List.of(previous, next);
    }

    /** The views, the ribbon and the dark look, as a menu of the window. */
    public JMenu viewMenu() {
        return controls.viewMenu();
    }

    /** What is compared, as a menu of the window. */
    public JMenu compareMenu() {
        return controls.compareMenu();
    }

    // -----------------------------------------------------------------------------------------------------------------
    // The documents

    /** Opens these two files and compares them; null leaves a side empty. */
    public void open(Path left, Path right) {
        open(left == null ? null : new DocumentSource.File(left), right == null ? null : new DocumentSource.File(right));
    }

    /** Opens these two documents, each read as its source reads it, and compares them; null leaves a side empty. */
    public void open(DocumentSource left, DocumentSource right) {
        sides.put(Side.LEFT, left == null ? SideState.EMPTY : SideState.of(left));
        sides.put(Side.RIGHT, right == null ? SideState.EMPTY : SideState.of(right));
        compare();
    }

    /** Puts this document on a side, in place of the one there, and compares it with the other side's. */
    public void open(Side side, DocumentSource source) {
        put(side, source);
    }

    /** Told each new comparison, once both documents are read and compared: the differences, to count or report. */
    public void onCompared(Consumer<Comparison> listener) {
        comparedListener = listener;
    }

    /** The comparison shown; null while one side or both wait for their document. */
    public Comparison comparison() {
        return comparison;
    }

    /** Puts this file on a side, in place of the document there, and compares it with the other side's. */
    public void open(Side side, Path path) {
        put(side, new DocumentSource.File(path));
    }

    /** Compares this text on a side, as a document of a paragraph a line (TextReader). */
    void paste(Side side, String text) {
        put(side, new DocumentSource.Pasted(text));
    }

    private void put(Side side, DocumentSource source) {
        sides.put(side, SideState.of(source));
        compare();
    }

    /** What the clipboard holds, on a side: text compared as a document, documents opened as dropped ones are. */
    private void paste(Side side) {
        Transferable held;
        try {
            held = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
        } catch (IllegalStateException _) {   // another program has it at that moment
            held = null;
        }
        if (held == null) {
            statusLine.say(I18n.text("paste.nothing"));
            return;
        }
        new DropHandler(opener, this, side).importData(new TransferHandler.TransferSupport(this, held));
    }

    private void choose(Side side) {
        JFileChooser chooser = new JFileChooser(lastDirectory);
        chooser.setDialogTitle(I18n.text(side.of("chooser.left", "chooser.right")));
        // every format's extensions, too many to list after its name
        FileNameExtensionFilter documents = new FileNameExtensionFilter(I18n.text("chooser.documents"),
                DocumentReaders.extensions().toArray(String[]::new));
        chooser.addChoosableFileFilter(documents);
        for (DocumentReader reader : DocumentReaders.all()) {
            chooser.addChoosableFileFilter(filter(reader.formatName(), reader.extensions()));
        }
        chooser.setFileFilter(documents);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            lastDirectory = chooser.getCurrentDirectory();
            open(side, chooser.getSelectedFile().toPath());
        }
    }

    /** A file chooser's filter: its name, the extensions listed after it ("Text files (*.txt, *.md)"). */
    private static FileNameExtensionFilter filter(String name, List<String> extensions) {
        String listed = String.join(", ", extensions.stream().map(e -> "*." + e).toList());
        return new FileNameExtensionFilter(I18n.text("chooser.filter", name, listed), extensions.toArray(String[]::new));
    }

    private void swap() {
        SideState left = sides.get(Side.LEFT);
        sides.put(Side.LEFT, sides.get(Side.RIGHT));
        sides.put(Side.RIGHT, left);
        compare();
    }

    /**
     * Reads what is not read yet, then compares, away from the window's thread; shows the result. A comparison asked
     * for while another runs replaces it.
     */
    private void compare() {
        sides.forEach((side, state) -> heads.get(side).show(state.source(), null));
        namesListener.accept(documentNames());
        updateActions();
        work.cancel();
        showEmptySides();
        SideState left = sides.get(Side.LEFT);
        SideState right = sides.get(Side.RIGHT);
        if (!left.isOpen() && !right.isOpen()) {
            comparison = null;
            showEmptyView();
            view.setWaitingSide(null);
            statusLine.say("");
            return;
        }
        if (!left.isOpen() || !right.isOpen()) {
            showAlone(left.isOpen() ? Side.LEFT : Side.RIGHT);
            return;
        }
        CompareOptions options = controls.options();
        work.start("status.comparing", () -> {
            Document a = left.document();
            Document b = right.document();
            PictureImages.prepare(a, b);   // decoded here, away from the window's thread
            return DocumentComparer.compare(a, b, options);
        }, result -> {
            sides.put(Side.LEFT, left.withRead(result.left()));
            sides.put(Side.RIGHT, right.withRead(result.right()));
            show(result);
            comparedListener.accept(result);
        }, "status.notCompared", "dialog.cannotCompare");
    }

    /**
     * One document open, the other not yet: the one shown on its side as it is, nothing compared; the other side a
     * place for its document — as Beyond Compare shows a file before the other.
     */
    private void showAlone(Side side) {
        comparison = null;
        SideState state = sides.get(side);
        work.start("status.reading", () -> {
            Document document = state.document();
            PictureImages.prepare(document);
            return document;
        }, document -> {
            sides.put(side, state.withRead(document));
            heads.get(side).show(state.source(), document);
            Lines.Shown shown = controls.shown();
            Palette palette = Palette.of(shown.documentView());
            view.setLines(Lines.alone(document, side, shown, view.fonts(), palette), -1, palette);
            view.setWaitingSide(side.other());
            statusLine.say(I18n.text("status.openTheOther"));
            updateActions();
        }, "status.notRead", "dialog.cannotRead");
    }

    /** The head of the side text pasted goes to, shown so. */
    private void showChosenSide() {
        heads.forEach((side, head) -> head.setChosen(side == view.chosenSide()));
    }

    /** No document open: the two empty sides, in the colours of the look in use. */
    private void showEmptyView() {
        view.setLines(List.of(), -1, Palette.of(controls.shown().documentView()));
    }

    /** While a side has no document: what goes there, and how to open it; the side opened, its document's name. */
    private void showEmptySides() {
        view.setEmptySides(emptySide(Side.LEFT, "empty.left", openLeft),
                emptySide(Side.RIGHT, "empty.right", openRight));
    }

    private EmptySide emptySide(Side side, String nameKey, Action open) {
        DocumentSource source = sides.get(side).source();
        return source != null ? EmptySide.opened(source.name())
                : EmptySide.waiting(nameKey, shortcut(open), shortcut(pasteHere));
    }

    private static KeyStroke shortcut(Action action) {
        return (KeyStroke) action.getValue(Action.ACCELERATOR_KEY);
    }

    /** Whether a comparison is shown: both documents read and compared. */
    public boolean compared() {
        return comparison != null;
    }

    /** Shows a comparison, keeping the place the view was at; the heads still say what the sides were given to open. */
    public void show(Comparison result) {
        comparison = result;
        sides.forEach((side, state) -> heads.get(side).show(state.source(), state.read()));
        Point at = scroll.getViewport().getViewPosition();
        view.setWaitingSide(null);
        Lines.Shown shown = controls.shown();
        Palette palette = Palette.of(shown.documentView());
        view.setLines(Lines.of(result, shown, view.fonts(), palette), -1, palette);
        SwingUtilities.invokeLater(() -> scroll.getViewport().setViewPosition(new Point(0, Math.min(at.y,
                Math.max(0, view.getHeight() - scroll.getViewport().getHeight())))));
        int[] count = view.differenceCount();
        statusLine.say(result.same() ? I18n.text("status.same") : ComparisonWords.count(count[0]));
        updatePosition();
        updateActions();
    }

    /**
     * The lines to show have changed (the view, if viewChanged): shown again; a document alone only for another view,
     * the one choice its lines follow — from what is read already.
     */
    private void shownChanged(boolean viewChanged) {
        if (comparison != null) {
            show(comparison);
        } else if (viewChanged && anyOpen()) {
            compare();
        }
    }

    /** The documents' names, left then right: "a.docx ↔ b.docx", "a.docx ↔ …"; empty when none is open. */
    String documentNames() {
        if (!anyOpen()) {
            return "";
        }
        return name(sides.get(Side.LEFT)) + BETWEEN_NAMES + name(sides.get(Side.RIGHT));
    }

    private static String name(SideState side) {
        return side.isOpen() ? side.source().name() : NO_NAME;
    }

    /** Whether either side has something to compare. */
    private boolean anyOpen() {
        return sides.values().stream().anyMatch(SideState::isOpen);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Going through the differences

    private void goToDifference(boolean forward) {
        int line = view.difference(forward);
        if (line >= 0) {
            view.setCurrent(line, true);
        } else {
            Toolkit.getDefaultToolkit().beep();
        }
    }

    private void updatePosition() {
        int[] count = view.differenceCount();
        statusLine.showPosition(count[1], count[0]);
        updateActions();
    }

    private void updateActions() {
        boolean compared = comparison != null;
        previous.setEnabled(compared && view.difference(false) >= 0);
        next.setEnabled(compared && view.difference(true) >= 0);
        swap.setEnabled(anyOpen());
    }

    /** An action named by the text of nameKey, its tooltip tipKey's. */
    private Action action(String nameKey, String tipKey, KeyStroke shortcut, Runnable run) {
        Action action = labels.action(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                run.run();
            }
        }, nameKey, tipKey);
        if (shortcut != null) {
            action.putValue(Action.ACCELERATOR_KEY, shortcut);
        }
        return action;
    }

    /**
     * Another look, set by the program: once every part of the window has taken it, the comparison shown again, laid
     * out in its fonts; the right-click menu, not a part of the window, told too.
     */
    @Override
    public void updateUI() {
        super.updateUI();
        if (view == null) {
            return;   // called as the panel is made, before its parts
        }
        SwingUtilities.invokeLater(() -> {
            if (view.getComponentPopupMenu() != null) {
                SwingUtilities.updateComponentTreeUI(view.getComponentPopupMenu());
            }
            if (comparison != null) {
                show(comparison);
            } else if (anyOpen()) {
                compare();
            } else {
                showEmptyView();
            }
        });
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(1280, 820);
    }
}
