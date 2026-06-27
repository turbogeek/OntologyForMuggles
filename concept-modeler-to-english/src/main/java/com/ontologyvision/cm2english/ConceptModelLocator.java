package com.ontologyvision.cm2english;

import com.nomagic.conceptmodeler.profiles.ConceptModelingProfile;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package;

import javax.swing.JOptionPane;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds the verbalizable concept model(s) in a project, so the user never has to know that a plain container
 * like <i>Imported Ontologies</i> has no ontology of its own.
 *
 * <p>CCM marks an ontology-bearing package with one of three Concept-Modeling-profile stereotypes —
 * «Concept Model», «Model», or «Information Model» — each carrying an {@code iri} tag. {@code CM2OWL} only
 * exports OWL for such a package; a container without the stereotype exports nothing (the
 * "produced no OWL" guard). This locator detects those packages structurally (via
 * {@code ConceptModelStereotype.is(element)}), which is cheap and avoids a trial CCM export per package.
 *
 * <p>All CCM calls are guarded: if the Concept Modeling profile is absent (a non-CCM project) the methods
 * degrade to "found nothing", and the caller falls back to trying the user's chosen package directly.
 *
 * <p><b>On the deprecation:</b> {@code ConceptModelingProfile} is marked deprecated in CCM 2026x but is still
 * the typed, profile-scoped accessor present in the runtime CCM jar this plugin links against. It is preferred
 * here over the non-deprecated {@code StereotypesHelper.hasStereotype(p, "Model")} because that matches a
 * stereotype by name across <i>all</i> profiles and would false-positive on UML's own «Model». If a future CCM
 * removes this class, migrate to {@code StereotypesHelper} scoped explicitly to the "Concept Modeling" profile.
 */
@SuppressWarnings("deprecation")
final class ConceptModelLocator {

    private ConceptModelLocator() { }

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
     * Resolve the package to act on, given the user's {@code chosen} package (from a right-click or the live
     * selection; may be {@code null}).
     * <ul>
     *   <li>chosen is itself an ontology package → use it (fast path; works even if other detection fails).</li>
     *   <li>otherwise look for ontology packages in the whole project: exactly one → use it; several → ask;
     *       none → fall back to {@code chosen} (let the worker try it and surface the friendly guard).</li>
     * </ul>
     */
    static Target resolve(Project project, Package chosen, String verb) {
        if (chosen != null && isOntologyPackage(chosen)) {
            return new Target(chosen, false);
        }
        List<Package> candidates = findOntologyPackages(project);
        if (candidates.size() == 1) {
            return new Target(candidates.get(0), false);
        }
        if (candidates.size() > 1) {
            Package picked = choose(candidates, verb);
            return new Target(picked, picked == null);   // null pick == user cancelled
        }
        return new Target(chosen, false);   // none detected — try whatever was chosen (may be null)
    }

    /** True if {@code p} is stereotyped «Concept Model» / «Model» / «Information Model». */
    static boolean isOntologyPackage(Package p) {
        if (p == null) {
            return false;
        }
        try {
            ConceptModelingProfile prof = ConceptModelingProfile.getInstance(p);
            return prof != null && isOntologyPackage(prof, p);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isOntologyPackage(ConceptModelingProfile prof, Package p) {
        try {
            return prof.conceptModel().is(p) || prof.model().is(p) || prof.informationModel().is(p);
        } catch (Throwable t) {
            return false;
        }
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
            if (prof.model().is(p)) {
                return blankToNull(prof.model().getIri(p));
            }
            if (prof.informationModel().is(p)) {
                return blankToNull(prof.informationModel().getIri(p));
            }
        } catch (Throwable ignored) {
            // profile absent / API shape mismatch — treated as "no IRI"
        }
        return null;
    }

    /** Every ontology package in {@code project} (the package tree only — concepts live under packages). */
    static List<Package> findOntologyPackages(Project project) {
        List<Package> out = new ArrayList<>();
        if (project == null) {
            return out;
        }
        Element root;
        ConceptModelingProfile prof;
        try {
            root = project.getPrimaryModel();
            if (root == null) {
                return out;
            }
            prof = ConceptModelingProfile.getInstance(root);
        } catch (Throwable t) {
            return out;
        }
        if (prof == null) {
            return out;
        }
        collect(prof, root, out);
        return out;
    }

    private static void collect(ConceptModelingProfile prof, Element el, List<Package> out) {
        for (Element c : el.getOwnedElement()) {
            if (c instanceof Package) {
                Package p = (Package) c;
                if (isOntologyPackage(prof, p)) {
                    out.add(p);
                }
                collect(prof, p, out);   // sub-packages may hold nested ontologies
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
