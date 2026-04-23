/*
 * Copyright (c) 2026 Denis Nikiforov
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.architeezy.archi.connector.ui.dialogs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.util.BasicMonitor;
import org.eclipse.emf.compare.Comparison;
import org.eclipse.emf.compare.ConflictKind;
import org.eclipse.emf.compare.Diff;
import org.eclipse.emf.compare.DifferenceSource;
import org.eclipse.emf.compare.DifferenceState;
import org.eclipse.emf.compare.Match;
import org.eclipse.emf.compare.merge.BatchMerger;
import org.eclipse.emf.compare.merge.IMerger;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.layout.TreeColumnLayout;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Tree;

import com.archimatetool.editor.ui.ArchiLabelProvider;
import com.archimatetool.model.IArchimateModel;
import com.architeezy.archi.connector.Messages;
import com.architeezy.archi.connector.io.ModelSerializer;
import com.architeezy.archi.connector.model.diff.ConflictTreeNode;
import com.architeezy.archi.connector.model.diff.DiffFormatter;
import com.architeezy.archi.connector.model.diff.Resolution;

/**
 * A simplified conflict-resolution dialog showing the model tree with three
 * columns: model structure, local change, and remote change.
 *
 * <p>
 * For conflicting rows the local and remote cells are clickable. Clicking a
 * cell selects that side (indicated by a checkmark prefix) and deselects the
 * other. The OK button is enabled only when every conflict has been resolved.
 *
 * <p>
 * Non-conflicting remote diffs are applied automatically on OK; conflicting
 * diffs are applied according to the user's per-row choice.
 */
@SuppressWarnings("checkstyle:ClassDataAbstractionCoupling")
public class ConflictResolutionDialog extends Dialog {

    private static final String CHECKMARK = "\u2714 ";

    private final Comparison comparison;

    private final Resource localResource;

    private final IMerger.Registry mergerRegistry;

    private final ModelSerializer serializer;

    private TreeViewer treeViewer;

    private List<ConflictTreeNode> allRoots;

    private List<ConflictTreeNode> conflictNodes;

    private final Map<ConflictTreeNode, Resolution> resolutions = new LinkedHashMap<>();

    private boolean showAllChanges;

    private byte[] mergedContent;

    private Exception mergeError;

    /**
     * Creates the dialog.
     *
     * @param parent the parent shell
     * @param comparison the EMF Compare result (left = local copy, right = remote)
     * @param localResource the local model resource, modified in-place on OK
     * @param mergerRegistry the merger registry used to apply diffs
     * @param serializer model serializer used to emit merged bytes on OK
     */
    public ConflictResolutionDialog(Shell parent, Comparison comparison, Resource localResource,
            IMerger.Registry mergerRegistry, ModelSerializer serializer) {
        super(parent);
        this.comparison = comparison;
        this.localResource = localResource;
        this.mergerRegistry = mergerRegistry;
        this.serializer = serializer;
        setShellStyle(getShellStyle() | SWT.RESIZE);
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText(Messages.ConflictDialog_title);
    }

    @Override
    @SuppressWarnings("checkstyle:MagicNumber")
    protected Point getInitialSize() {
        return new Point(820, 600);
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        var area = (Composite) super.createDialogArea(parent);
        area.setLayout(new GridLayout(1, false));

        var desc = new Label(area, SWT.WRAP);
        desc.setText(Messages.ConflictDialog_description);
        desc.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        var treeComposite = new Composite(area, SWT.NONE);
        var columnLayout = new TreeColumnLayout();
        treeComposite.setLayout(columnLayout);
        treeComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        treeViewer = new TreeViewer(treeComposite,
                SWT.BORDER | SWT.FULL_SELECTION | SWT.H_SCROLL | SWT.V_SCROLL);
        var tree = treeViewer.getTree();
        tree.setHeaderVisible(true);
        tree.setLinesVisible(true);

        setupTreeColumns(columnLayout);

        treeViewer.setContentProvider(new ConflictTreeContentProvider(() -> showAllChanges));
        allRoots = buildAllNodes();
        conflictNodes = ConflictTreeNode.collectConflictNodes(allRoots);
        treeViewer.setInput(filteredRoots());
        treeViewer.expandAll();

        installCellClickListener(tree);
        createBulkActionArea(area);

        return area;
    }

    private void setupTreeColumns(TreeColumnLayout columnLayout) {
        var col1 = new TreeViewerColumn(treeViewer, SWT.LEFT);
        col1.getColumn().setText(Messages.ConflictDialog_colStructure);
        col1.setLabelProvider(new StructureColumnProvider());
        columnLayout.setColumnData(col1.getColumn(), new ColumnWeightData(1, true));

        var col2 = new TreeViewerColumn(treeViewer, SWT.LEFT);
        col2.getColumn().setText(Messages.ConflictDialog_colLocal);
        col2.setLabelProvider(new ChangeColumnProvider(Resolution.LOCAL));
        columnLayout.setColumnData(col2.getColumn(), new ColumnWeightData(1, true));

        var col3 = new TreeViewerColumn(treeViewer, SWT.LEFT);
        col3.getColumn().setText(Messages.ConflictDialog_colRemote);
        col3.setLabelProvider(new ChangeColumnProvider(Resolution.REMOTE));
        columnLayout.setColumnData(col3.getColumn(), new ColumnWeightData(1, true));
    }

    private void installCellClickListener(Tree tree) {
        tree.addMouseListener(new MouseAdapter() {

            @Override
            public void mouseDown(MouseEvent e) {
                onTreeMouseDown(e);
            }

        });
    }

    static Resolution resolutionForColumn(int columnIndex) {
        if (columnIndex == 1) {
            return Resolution.LOCAL;
        }
        if (columnIndex == 2) {
            return Resolution.REMOTE;
        }
        return null;
    }

    private void onTreeMouseDown(MouseEvent e) {
        var cell = treeViewer.getCell(new Point(e.x, e.y));
        if (cell == null) {
            return;
        }
        if (!(cell.getElement() instanceof ConflictTreeNode node) || !node.isConflict()) {
            return;
        }
        var chosen = resolutionForColumn(cell.getColumnIndex());
        if (chosen == null) {
            return;
        }
        if ((e.stateMask & SWT.CTRL) != 0) {
            resolutions.remove(node);
        } else {
            resolutions.put(node, chosen);
        }
        // Full refresh so parent nodes pick up the new conflict state color.
        treeViewer.refresh();
        updateOkButton();
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private void createBulkActionArea(Composite parent) {
        var bulk = new Composite(parent, SWT.NONE);
        bulk.setLayout(new GridLayout(3, false));
        bulk.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        var acceptAllLocal = new Button(bulk, SWT.PUSH);
        acceptAllLocal.setText(Messages.ConflictDialog_acceptAllLocal);
        acceptAllLocal.addListener(SWT.Selection, e -> acceptAll(Resolution.LOCAL));

        var acceptAllRemote = new Button(bulk, SWT.PUSH);
        acceptAllRemote.setText(Messages.ConflictDialog_acceptAllRemote);
        acceptAllRemote.addListener(SWT.Selection, e -> acceptAll(Resolution.REMOTE));

        var showAllCheckbox = new Button(bulk, SWT.CHECK);
        showAllCheckbox.setText(Messages.ConflictDialog_showAllChanges);
        showAllCheckbox.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false));
        showAllCheckbox.addListener(SWT.Selection, e -> {
            showAllChanges = showAllCheckbox.getSelection();
            treeViewer.setInput(filteredRoots());
            treeViewer.expandAll();
        });
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        super.createButtonsForButtonBar(parent);
        updateOkButton();
    }

    @Override
    protected void okPressed() {
        try {
            applyResolutions();
            var model = (IArchimateModel) localResource.getContents().get(0);
            mergedContent = serializer.serialize(model);
        } catch (Exception e) {
            mergeError = e;
        }
        super.okPressed();
    }

    /**
     * Returns the serialized merged model bytes, or {@code null} if the dialog
     * was cancelled or an error occurred during serialization.
     *
     * @return merged model bytes, or {@code null} if cancelled or serialization
     *         failed
     */
    public byte[] getMergedContent() {
        return mergedContent;
    }

    /**
     * Returns any exception thrown during diff application or serialization in
     * {@link #okPressed()}, or {@code null} if there was no error.
     *
     * @return the serialization or merge exception, or {@code null}
     */
    public Exception getMergeError() {
        return mergeError;
    }

    // -------------------------------------------------------------------------
    // Tree building
    // -------------------------------------------------------------------------

    private List<ConflictTreeNode> buildAllNodes() {
        var result = new ArrayList<ConflictTreeNode>();
        for (var match : comparison.getMatches()) {
            var node = buildNode(match);
            if (node.hasAnyChange()) {
                result.add(node);
            }
        }
        return result;
    }

    private List<ConflictTreeNode> filteredRoots() {
        if (showAllChanges) {
            return allRoots;
        }
        return allRoots.stream()
                .filter(ConflictTreeNode::hasConflictInSubtree)
                .toList();
    }

    private static org.eclipse.emf.ecore.EObject pickRepresentativeElement(Match match) {
        var o = match.getOrigin();
        if (o != null) {
            return o;
        }
        var l = match.getLeft();
        return l != null ? l : match.getRight();
    }

    private ConflictTreeNode buildNode(Match match) {
        // Prefer origin (base); fall back to the side that introduced the element.
        var element = pickRepresentativeElement(match);

        var addedLocal = match.getOrigin() == null && match.getLeft() != null;
        var addedRemote = match.getOrigin() == null && match.getRight() != null;
        var deletedLocal = match.getOrigin() != null && match.getLeft() == null;
        var deletedRemote = match.getOrigin() != null && match.getRight() == null;

        var localDiffs = match.getDifferences().stream()
                .filter(d -> d.getSource() == DifferenceSource.LEFT)
                .toList();
        var remoteDiffs = match.getDifferences().stream()
                .filter(d -> d.getSource() == DifferenceSource.RIGHT)
                .toList();

        var children = new ArrayList<ConflictTreeNode>();
        for (var sub : match.getSubmatches()) {
            var child = buildNode(sub);
            if (child.hasAnyChange()) {
                children.add(child);
            }
        }

        return new ConflictTreeNode(element, DiffFormatter.extractLabel(element), localDiffs, remoteDiffs, children,
                addedLocal, addedRemote, deletedLocal, deletedRemote);
    }

    // -------------------------------------------------------------------------
    // Resolution logic
    // -------------------------------------------------------------------------

    private void acceptAll(Resolution resolution) {
        for (var node : conflictNodes) {
            resolutions.put(node, resolution);
        }
        treeViewer.refresh();
        updateOkButton();
    }

    private void updateOkButton() {
        var ok = getButton(IDialogConstants.OK_ID);
        if (ok == null || ok.isDisposed()) {
            return;
        }
        ok.setEnabled(conflictNodes.stream().allMatch(resolutions::containsKey));
    }

    private void applyResolutions() {
        var toApply = new ArrayList<Diff>();

        // Auto-apply non-conflicting remote diffs
        comparison.getDifferences().stream()
                .filter(d -> d.getSource() == DifferenceSource.RIGHT)
                .filter(d -> d.getState() == DifferenceState.UNRESOLVED)
                .filter(d -> d.getConflict() == null || d.getConflict().getKind() != ConflictKind.REAL)
                .forEach(toApply::add);

        // Apply conflicting remote diffs for nodes where the user chose REMOTE
        collectChosenRemoteDiffs(allRoots, toApply);

        if (!toApply.isEmpty()) {
            var merger = new BatchMerger(mergerRegistry);
            merger.copyAllRightToLeft(toApply, new BasicMonitor());
        }
    }

    private void collectChosenRemoteDiffs(List<ConflictTreeNode> nodes, List<Diff> result) {
        for (var node : nodes) {
            if (node.isConflict() && resolutions.get(node) == Resolution.REMOTE) {
                node.remoteDiffs().stream()
                        .filter(d -> d.getConflict() != null
                                && d.getConflict().getKind() == ConflictKind.REAL)
                        .forEach(result::add);
            }
            collectChosenRemoteDiffs(node.children(), result);
        }
    }

    // -------------------------------------------------------------------------
    // Label providers
    // -------------------------------------------------------------------------

    private boolean hasUnresolvedConflict(ConflictTreeNode node) {
        if (node.isConflict() && !resolutions.containsKey(node)) {
            return true;
        }
        return node.children().stream().anyMatch(this::hasUnresolvedConflict);
    }

    private final class StructureColumnProvider extends ColumnLabelProvider {

        @Override
        public String getText(Object element) {
            return element instanceof ConflictTreeNode node ? node.label() : "";
        }

        @Override
        public Image getImage(Object element) {
            if (element instanceof ConflictTreeNode node && node.modelElement() != null) {
                return ArchiLabelProvider.INSTANCE.getImage(node.modelElement());
            }
            return null;
        }

        @Override
        public Color getForeground(Object element) {
            if (element instanceof ConflictTreeNode node && hasUnresolvedConflict(node)) {
                return Display.getCurrent().getSystemColor(SWT.COLOR_RED);
            }
            return null;
        }

    }

    private final class ChangeColumnProvider extends ColumnLabelProvider {

        private final Resolution side;

        ChangeColumnProvider(Resolution side) {
            this.side = side;
        }

        @Override
        public String getText(Object element) {
            if (!(element instanceof ConflictTreeNode node)) {
                return "";
            }
            var diffs = side == Resolution.LOCAL ? node.localDiffs() : node.remoteDiffs();
            var structural = getStructuralChangeLabel(node);

            // When the object itself is added/deleted, other diffs on the same side
            // (e.g. reference assignments) are part of the structural operation and
            // should not be listed separately.
            var content = structural.isEmpty() ? DiffFormatter.formatDiffs(diffs) : structural;

            if (content.isEmpty()) {
                return "";
            }
            if (node.isConflict()) {
                return resolutions.get(node) == side ? CHECKMARK + content : content;
            }
            return CHECKMARK + content;
        }

        private String getStructuralChangeLabel(ConflictTreeNode node) {
            var added = side == Resolution.LOCAL ? node.addedLocal() : node.addedRemote();
            if (added) {
                return Messages.ConflictDialog_changeAdded;
            }
            var deleted = side == Resolution.LOCAL ? node.deletedLocal() : node.deletedRemote();
            if (deleted) {
                return Messages.ConflictDialog_changeDeleted;
            }
            return "";
        }

    }

}
