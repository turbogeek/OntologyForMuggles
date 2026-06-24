package com.ontologyvision.cm2english;

import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.uml.BaseElement;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.NamedElement;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Resolves a clicked {@code href="iri:<IRI>"} link from the report back to a model element and selects it in
 * the Containment tree.
 *
 * <p>The Concept Modeler derives an OWL entity's IRI <i>fragment</i> from the element's name with whitespace
 * removed (e.g. "Plant Parts" → {@code …#PlantParts}, "lion" → {@code …#lion}). So we resolve by matching the
 * IRI fragment to a named element under the verbalized package. (A later refinement could read the exact URI
 * the Concept Modeler stamps on each element; fragment-matching covers the common case.)
 */
public final class ConceptNavigator {

    private ConceptNavigator() { }

    /** Decode an {@code iri:…} href, resolve it to an element under {@code scope}, and open it in the tree. */
    public static void selectInTree(Project project, Element scope, String href) {
        try {
            if (project == null || scope == null || href == null) {
                return;
            }
            String iri = href.startsWith("iri:") ? href.substring(4) : href;
            iri = URLDecoder.decode(iri, StandardCharsets.UTF_8);
            String fragment = fragmentOf(iri);
            if (fragment.isEmpty()) {
                return;
            }
            Element el = findByFragment(scope, fragment);
            if (el instanceof BaseElement) {
                project.getBrowser().getContainmentTree().openNode((BaseElement) el);
            } else {
                ConceptModelEnglishBridge.info("No model element matches \"" + fragment + "\".");
            }
        } catch (Throwable t) {
            ConceptModelEnglishBridge.reportError("Navigate-to-element failed", t);
        }
    }

    private static String fragmentOf(String iri) {
        int h = iri.lastIndexOf('#');
        if (h >= 0) {
            return iri.substring(h + 1);
        }
        int s = iri.lastIndexOf('/');
        return (s >= 0) ? iri.substring(s + 1) : iri;
    }

    /** First {@link NamedElement} under {@code scope} (depth-first) whose whitespace-stripped name == fragment. */
    private static Element findByFragment(Element scope, String fragment) {
        for (Element el : scope.getOwnedElement()) {
            if (el instanceof NamedElement) {
                String n = ((NamedElement) el).getName();
                if (n != null && !n.isEmpty() && n.replaceAll("\\s+", "").equals(fragment)) {
                    return el;
                }
            }
            Element deep = findByFragment(el, fragment);
            if (deep != null) {
                return deep;
            }
        }
        return null;
    }
}
