package com.ontologyvision.cm2english;

import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.uml.symbols.AbstractDiagramPresentationElement;
import com.nomagic.magicdraw.uml.symbols.DiagramSurface;
import com.nomagic.magicdraw.uml.symbols.PresentationElement;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.NamedElement;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Opens the diagram an element appears on, centers the view on its symbol, and selects it.
 *
 * <p>Decision (confirmed with the user): prefer the <b>active</b> diagram if the element is on it; else if it
 * is on exactly one diagram use that; else pop a <b>chooser</b>. If it is on no diagram, the caller's tree
 * reveal stands and we just post a status note. MagicDraw has no public "center" primitive, so centering is
 * approximated by scrolling an enlarged rectangle around the symbol's midpoint into view.
 */
public final class DiagramNavigator {

    private DiagramNavigator() { }

    /** Open + center + select the element on a diagram (active → single → chooser → none). */
    public static void navigateTo(Project project, Element element) {
        if (project == null || element == null) {
            return;
        }
        Map<AbstractDiagramPresentationElement, PresentationElement> byDiagram = symbolsByDiagram(project, element);
        if (byDiagram.isEmpty()) {
            ConceptModelEnglishBridge.info("\"" + nameOf(element) + "\" is not shown on any diagram (revealed in the tree).");
            return;
        }
        AbstractDiagramPresentationElement active = project.getActiveDiagram();
        AbstractDiagramPresentationElement target;
        if (active != null && byDiagram.containsKey(active)) {
            target = active;
        } else if (byDiagram.size() == 1) {
            target = byDiagram.keySet().iterator().next();
        } else {
            target = chooseDiagram(byDiagram.keySet());
        }
        if (target == null) {
            return; // chooser cancelled
        }
        openCenterSelect(target, byDiagram.get(target));
    }

    /** Distinct diagrams (preserving order) whose symbols represent {@code element}, with one representative symbol each. */
    private static Map<AbstractDiagramPresentationElement, PresentationElement> symbolsByDiagram(Project project, Element element) {
        Map<AbstractDiagramPresentationElement, PresentationElement> map = new LinkedHashMap<>();
        try {
            List<PresentationElement> pes = project.getSymbolElementMap().getAllPresentationElements(element);
            if (pes != null) {
                for (PresentationElement pe : pes) {
                    AbstractDiagramPresentationElement dpe = pe.getAbstractDiagramPresentationElement();
                    if (dpe != null) {
                        map.putIfAbsent(dpe, pe);
                    }
                }
            }
        } catch (Throwable t) {
            ConceptModelEnglishBridge.reportError("Diagram lookup failed", t);
        }
        return map;
    }

    private static AbstractDiagramPresentationElement chooseDiagram(Collection<AbstractDiagramPresentationElement> diagrams) {
        List<AbstractDiagramPresentationElement> list = new ArrayList<>(diagrams);
        String[] names = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            names[i] = diagramName(list.get(i));
        }
        Object sel = JOptionPane.showInputDialog(null, "This element appears on several diagrams. Open which one?",
                "Open diagram", JOptionPane.QUESTION_MESSAGE, null, names, names[0]);
        if (sel == null) {
            return null;
        }
        for (int i = 0; i < names.length; i++) {
            if (names[i].equals(sel)) {
                return list.get(i);
            }
        }
        return null;
    }

    private static void openCenterSelect(AbstractDiagramPresentationElement dpe, PresentationElement pe) {
        SwingUtilities.invokeLater(() -> {
            try {
                dpe.ensureLoaded();
                dpe.open();
                if (pe != null) {
                    pe.setSelected(true);
                    centerOn(pe);
                }
            } catch (Throwable t) {
                ConceptModelEnglishBridge.reportError("Open/center diagram failed", t);
            }
        });
    }

    /** Approximate "center on": scroll an enlarged rectangle around the symbol into view (no public center API). */
    private static void centerOn(PresentationElement pe) {
        try {
            DiagramSurface surface = DiagramSurface.getDiagramSurface(pe);
            if (surface == null) {
                return;
            }
            Rectangle r;
            try {
                r = pe.getBounds();
            } catch (Throwable noRect) {
                // path symbols (associations etc.) have no rectangle — fall back to the midpoint
                Point mid = pe.getMiddlePoint();
                surface.scrollToVisible(mid.x, mid.y);
                return;
            }
            int pad = 400;
            surface.scrollRectToVisible(new Rectangle(r.x - pad, r.y - pad, r.width + 2 * pad, r.height + 2 * pad));
        } catch (Throwable ignored) {
            // centering is best-effort
        }
    }

    private static String diagramName(AbstractDiagramPresentationElement dpe) {
        try {
            String n = dpe.getName();
            return (n == null || n.isEmpty()) ? String.valueOf(dpe) : n;
        } catch (Throwable t) {
            return String.valueOf(dpe);
        }
    }

    private static String nameOf(Element el) {
        try {
            return (el instanceof NamedElement) ? ((NamedElement) el).getName() : "element";
        } catch (Throwable t) {
            return "element";
        }
    }
}
