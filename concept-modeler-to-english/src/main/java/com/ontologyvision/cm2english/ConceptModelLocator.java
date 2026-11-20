package com.ontologyvision.cm2english;

import com.nomagic.conceptmodeler.profiles.ConceptModelingProfile;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package;
import com.nomagic.uml2.ext.magicdraw.mdprofiles.Stereotype;

import javax.swing.JOptionPane;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds the verbalizable concept model(s) in a project, so the user never has to know that a plain container
 * like <i>Imported Ontologies</i> has no ontology of its own.
 *
 * <p>CCM marks an ontology-bearing package with one of three Concept-Modeling-profile stereotypes —
 * «Concept Model», «Model», or «Information Model» — each carrying an {@code iri} tag. {@code CM2OWL} only
 * exports OWL for such a package; a container without the stereotype exports nothing (the "produced no OWL"
 * guard). This locator detects those packages structurally (via {@code ConceptModelStereotype.is(element)}),
 * which is cheap and avoids a trial CCM export per package.
 *
 * <p><b>Authored model vs. shipped library.</b> A real CCM project carries many ontology packages: the user's
 * own model <i>plus</i> the imported standard vocabularies (RDF, RDFS, OWL, XSD, Dublin Core, gUFO, the CCM
 * Support Library, …). A live-model probe showed the authored model is «Concept Model» with no library markers,
 * whereas the libraries carry «auxiliaryResource» and/or a «File Export Path» under {@code <install.root>}. So
 * auto-resolution prefers non-library «Concept Model» packages — which typically collapses to the single model
 * the user is working on — and only falls back to other ontologies if there is no authored concept model.
 *
 * <p><b>On the deprecation:</b> {@code ConceptModelingProfile} is marked deprecated in CCM 2026x but is the
 * typed, profile-scoped accessor present in the runtime CCM jar this plugin links against. It is preferred over
 * the non-deprecated {@code StereotypesHelper.hasStereotype(p, "Model")} because that matches a stereotype by
 * name across <i>all</i> profiles and would false-positive on UML's own «Model». (The library markers below are
 * read by name because «auxiliaryResource» / «File Export Path» are distinctive and not CCM-ontology stereotypes.)
 */
@SuppressWarnings("deprecation")
final class ConceptModelLocator {

    private ConceptModelLocator() { }

    /** Which CCM ontology stereotype a package carries (drives "authored model first" ranking). */
    private enum Kind { CONCEPT_MODEL, INFORMATION_MODEL, MODEL, NONE }

    /** The outcome of resolving which package to act on. */
    static final class Target {
        /** The package to verbalize/export, or {@code null} if none could be resolved. */
        final Package pkg;
        /** True if the user dismissed the chooser — the caller should quietly abort (no error). */
        final boolean cancelled;

        private Target(Package pkg, boolean cancelled) {
            this.pkg = pkg;
            this.cancelled = cancelled;
        }
    }

    /**
     * Resolve the package to act on, given the user's {@code chosen} package (right-click or live selection; may
     * be {@code null}).
     * <ul>
     *   <li>chosen is itself an ontology → honor it exactly (library or not — an explicit pick is respected).</li>
     *   <li>otherwise auto-locate: prefer authored, non-library «Concept Model» packages — one → use it,
     *       several → chooser; if there are none, widen to any non-library ontology; still none → fall back to
     *       {@code chosen} so the worker surfaces the friendly guard.</li>
     * </ul>
     */
    static Target resolve(Project project, Package chosen, String verb) {
        if (chosen != null && isOntologyPackage(chosen)) {
            return new Target(chosen, false);     // explicit pick — honor it
        }
        List<Package> all = findOntologyPackages(project);
        List<Package> authored = new ArrayList<>();
        List<Package> anyNonLibrary = new ArrayList<>();
        for (Package p : all) {
            if (isLibrary(p)) {
                continue;
            }
            anyNonLibrary.add(p);
            if (kindOf(p) == Kind.CONCEPT_MODEL) {
                authored.add(p);
            }
        }
        List<Package> pick = !authored.isEmpty() ? authored : anyNonLibrary;
        if (pick.isEmpty() && !all.isEmpty()) {
            pick = all;
        }
        if (pick.size() == 1) {
            return new Target(pick.get(0), false);
        }
        if (pick.size() > 1) {
            Package picked = choose(pick, verb);
            return new Target(picked, picked == null);   // null pick == user cancelled
        }
        return new Target(chosen, false);   // nothing sensible found — try whatever was chosen (may be null)
    }

    /** True if {@code p} is stereotyped «Concept Model» / «Model» / «Information Model». */
    static boolean isOntologyPackage(Package p) {
        return kindOf(p) != Kind.NONE;
    }

    private static Kind kindOf(Package p) {
        if (p == null) {
            return Kind.NONE;
        }
        try {
            ConceptModelingProfile prof = ConceptModelingProfile.getInstance(p);
            if (prof == null) {
                return Kind.NONE;
            }
            if (prof.conceptModel().is(p)) {
                return Kind.CONCEPT_MODEL;
            }
            if (prof.informationModel().is(p)) {
                return Kind.INFORMATION_MODEL;
            }
            if (prof.model().is(p)) {
                return Kind.MODEL;
            }
        } catch (Throwable t) {
            return Kind.NONE;
        }
        return Kind.NONE;
    }

    /** The ontology IRI declared on {@code p} (whichever ontology stereotype applies), or {@code null}. */
    static String iriOf(Package p) {
        if (p == null) {
            return null;
        }
        try {
            ConceptModelingProfile prof = ConceptModelingProfile.getInstance(p);
            if (prof == null) {
                return null;
            }
            if (prof.conceptModel().is(p)) {
                return blankToNull(prof.conceptModel().getIri(p));
            }
            if (prof.informationModel().is(p)) {
                return blankToNull(prof.informationModel().getIri(p));
            }
            if (prof.model().is(p)) {
                return blankToNull(prof.model().getIri(p));
            }
        } catch (Throwable ignored) {
            // profile absent / API shape mismatch — treated as "no IRI"
        }
        return null;
    }

    /**
     * True if {@code p} is a shipped/auxiliary ontology rather than the user's authored model — recognised by
     * «auxiliaryResource» or a «File Export Path» that targets the tool install ({@code <install.root>}).
     * Read by stereotype name via {@link StereotypesHelper} (these markers are distinctive, non-CCM-ontology).
     */
    private static boolean isLibrary(Package p) {
        if (hasStereotypeNamed(p, "auxiliaryResource")) {
            return true;
        }
        String fep = fileExportPath(p);
        return fep != null && fep.contains("<install.root>");
    }

    private static boolean hasStereotypeNamed(Package p, String name) {
        try {
            for (Stereotype st : StereotypesHelper.getStereotypes(p)) {
                if (name.equals(st.getName())) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
            // best-effort
        }
        return false;
    }

    private static String fileExportPath(Package p) {
        try {
            for (Stereotype st : StereotypesHelper.getStereotypes(p)) {
                if ("File Export Path".equals(st.getName())) {
                    List<?> vals = StereotypesHelper.getStereotypePropertyValue(p, st, "fileExportPath");
                    if (vals != null && !vals.isEmpty() && vals.get(0) != null) {
                        return String.valueOf(vals.get(0));
                    }
                }
            }
        } catch (Throwable ignored) {
            // best-effort
        }
        return null;
    }

    /** Every ontology package in {@code project} (the package tree only — concepts live under packages). */
    static List<Package> findOntologyPackages(Project project) {
        List<Package> out = new ArrayList<>();
        if (project == null) {
            return out;
        }
        try {
            for (Package model : project.getModels()) {
                if (isOntologyPackage(model)) {
                    out.add(model);
                }
                collect(model, out);
            }
        } catch (Throwable t) {
            Element root = project.getPrimaryModel();
            if (root != null) {
                collect(root, out);
            }
        }
        return out;
    }

    private static void collect(Element el, List<Package> out) {
        for (Element c : el.getOwnedElement()) {
            if (c instanceof Package) {
                Package p = (Package) c;
                if (isOntologyPackage(p)) {
                    out.add(p);
                }
                collect(p, out);   // sub-packages may hold nested ontologies
            }
        }
    }

    /** Ask the user which concept model to act on; returns the pick, or {@code null} if dismissed. */
    private static Package choose(List<Package> candidates, String verb) {
        String[] labels = new String[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            labels[i] = label(candidates.get(i));
        }
        Object sel = JOptionPane.showInputDialog(null,
                "This project has several concept models. Which one do you want to " + verb + "?",
                "Ontology4Muggles", JOptionPane.QUESTION_MESSAGE, null, labels, labels[0]);
        if (sel == null) {
            return null;
        }
        for (int i = 0; i < labels.length; i++) {
            if (labels[i].equals(sel)) {
                return candidates.get(i);
            }
        }
        return null;
    }

    private static String label(Package p) {
        String name = ConceptModelEnglishBridge.safeName(p);
        String iri = iriOf(p);
        return (iri == null) ? name : name + "  —  " + iri;
    }

    private static String blankToNull(String s) {
        return (s == null || s.trim().isEmpty()) ? null : s;
    }
}
