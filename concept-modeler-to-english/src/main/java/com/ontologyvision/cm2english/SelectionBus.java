package com.ontologyvision.cm2english;

import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.ui.SelectionProvider;
import com.nomagic.magicdraw.uml.BaseElement;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Shared-selection seam — a thin wrapper over MagicDraw's documented {@link SelectionProvider} so our views can
 * follow the ONE selection across the Containment tree and all diagrams. Selecting an element anywhere drives
 * every subscribing pane together; clicking a pane re-selects through the tree/diagram, which re-publishes here.
 * (Adapted from VOM's {@code VomSelectionBus}; {@code com.nomagic.*} + JDK only.)
 *
 * <p>Model elements implement both {@code Element} and {@code BaseElement}; the selection is filtered to
 * ontology {@link Element}s by {@code instanceof}.
 */
public final class SelectionBus {

    private SelectionBus() { }

    /** The currently selected elements (across tree + diagrams), or empty. */
    public static List<Element> current(Project project) {
        List<Element> out = new ArrayList<>();
        if (project == null) {
            return out;
        }
        for (BaseElement be : SelectionProvider.getInstance(project).getSelectedElements()) {
            if (be instanceof Element) {
                out.add((Element) be);
            }
        }
        return out;
    }

    /**
     * Subscribe {@code onChange} to selection changes; invoked on the EDT with the new selection each time it
     * changes. Returns a handle whose {@link AutoCloseable#close()} unsubscribes (a no-op if there's no project).
     */
    public static AutoCloseable subscribe(Project project, Consumer<List<Element>> onChange) {
        if (project == null) {
            return () -> { };
        }
        SelectionProvider sp = SelectionProvider.getInstance(project);
        SelectionProvider.SelectionChangedListener listener = event -> {
            List<Element> sel = new ArrayList<>();
            for (BaseElement be : event.getNewSelectedValues()) {
                if (be instanceof Element) {
                    sel.add((Element) be);
                }
            }
            onChange.accept(sel);
        };
        sp.addSelectionChangedListener(listener);
        return () -> sp.removeSelectionChangedListener(listener);
    }
}
