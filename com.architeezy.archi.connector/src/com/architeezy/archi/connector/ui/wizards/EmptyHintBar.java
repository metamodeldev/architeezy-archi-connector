/*
 * Copyright (c) 2026 Denis Nikiforov
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.architeezy.archi.connector.ui.wizards;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Link;

/**
 * Single-line {@link Link} banner that wizards show when there's nothing in the
 * tree. Hides itself by default; activates when the page calls {@link #show}.
 * Anchors in the surrounding {@link org.eclipse.swt.layout.GridLayout} so it
 * can collapse without leaving an empty row when there is nothing to say.
 */
final class EmptyHintBar {

    private final Link link;

    EmptyHintBar(Composite parent) {
        link = new Link(parent, SWT.NONE);
        var gd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        gd.exclude = true;
        link.setLayoutData(gd);
        link.setVisible(false);
        link.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> {
            if (e.text != null && !e.text.isBlank()) {
                Program.launch(e.text);
            }
        }));
    }

    void show(String message) {
        if (link.isDisposed()) {
            return;
        }
        var gd = (GridData) link.getLayoutData();
        gd.exclude = message == null;
        link.setVisible(message != null);
        link.setText(message != null ? message : ""); //$NON-NLS-1$
        link.requestLayout();
    }

}
