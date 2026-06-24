package com.ontologyvision.cm2english;

import com.nomagic.magicdraw.core.Project;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package;

import java.util.List;

/**
 * Resolves "the concept-model package the user means right now" from the live MagicDraw selection — so the
 * main-menu (Tools &#9656; Ontology4Muggles) actions can operate without a right-click package to capture.
 *
 * <p>Strategy: prefer a directly-selected {@link Package}; otherwise walk up the owner chain of the first
 * selected element to its nearest enclosing package. Reuses {@link SelectionBus} (MagicDraw's
 * {@code SelectionProvider}) so it tracks the Containment tree and the active diagram uniformly.
 */
public final class ConceptModelSelection {

    private ConceptModelSelection() { }

    /** The selected package (or the nearest owning package of the selection), or {@code null}. */
    public static Package current(Project project) {
        if (project == null) {
            return null;
        }
        List<Element> sel = SelectionBus.current(project);
        for (Element e : sel) {
            if (e instanceof Package) {
                return (Package) e;
            }
        }
        for (Element e : sel) {
            for (Element o = e; o != null; o = o.getOwner()) {
                if (o instanceof Package) {
                    return (Package) o;
                }
            }
        }
        return null;
    }
}
