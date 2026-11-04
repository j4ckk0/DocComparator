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

import java.awt.Cursor;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import org.jtools.doccomparator.i18n.I18n;

/**
 * The panel's work away from the window's thread — reading, comparing —, one at a time: while it runs, the status line
 * says so and the cursor waits; its result goes to the window's thread, unless other work has replaced it; a failure is
 * said in the status line and a message.
 */
final class BackgroundWork {

    private final JComponent panel;
    private final StatusLine statusLine;
    private SwingWorker<?, ?> running;

    BackgroundWork(JComponent panel, StatusLine statusLine) {
        this.panel = panel;
        this.statusLine = statusLine;
    }

    /**
     * Runs work, the status saying so (the text of saying), then gives its result to done on the window's thread. A
     * failure is said in the status line (failed's text, why as its argument) and a message, under title's text.
     */
    <T> void start(String saying, Callable<T> work, Consumer<T> done, String failed, String title) {
        statusLine.say(I18n.text(saying));
        panel.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        SwingWorker<T, Void> worker = new SwingWorker<>() {
            @Override
            protected T doInBackground() throws Exception {
                return work.call();
            }

            @Override
            protected void done() {
                if (isCancelled() || running != this) {
                    return;
                }
                running = null;
                panel.setCursor(null);
                try {
                    done.accept(get());
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    statusLine.say(I18n.text(failed, cause.getMessage()));
                    JOptionPane.showMessageDialog(panel, cause.getMessage(), I18n.text(title),
                            JOptionPane.ERROR_MESSAGE);
                } catch (InterruptedException _) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        running = worker;
        worker.execute();
    }

    /** Stops the work running, if any: its result is given to no one. */
    void cancel() {
        if (running != null) {
            running.cancel(false);
            running = null;
            panel.setCursor(null);
        }
    }
}
