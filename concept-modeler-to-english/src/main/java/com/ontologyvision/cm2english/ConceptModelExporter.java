package com.ontologyvision.cm2english;

import com.nomagic.magicdraw.core.Project;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

/**
 * Orchestrates an export of a concept-model package: CCM OWL export → English verbalize → OWL Turtle serialize
 * → diagram SVG export → compose the chosen format. Touches the CCM API + verbalizer + diagram surface, so call
 * it OFF the EDT (from a {@code SwingWorker}); the diagram SVG export itself is the only UI-touching step.
 */
public final class ConceptModelExporter {

    private ConceptModelExporter() { }

    public enum Format { HTML, MARKDOWN }

    /** Export {@code pkg} into {@code outDir} as {@code fmt}; returns the primary output file. */
    public static File export(Project project, Package pkg, File outDir, Format fmt) throws Exception {
        outDir.mkdirs();
        String name = ConceptModelEnglishBridge.safeName(pkg);

        byte[] owl = ConceptModelEnglishBridge.exportOwl(pkg);
        if (owl == null || owl.length == 0) {
            throw new IllegalStateException("The Concept Modeler produced no OWL for \"" + name
                    + "\". Choose a concept-model package that carries an ontology URI.");
        }
        String english = ConceptModelEnglishBridge.verbalize(owl, "Concept Model — English: " + name, true);
        String turtle  = ConceptModelEnglishBridge.serializeOwl(owl, "turtle");

        List<DiagramSvgExporter.DiagramSvg> svgs = DiagramSvgExporter.exportDiagrams(project, pkg, new File(outDir, "diagrams"));

        // the raw OWL (CCM RDF/XML) too, for reuse
        Files.write(new File(outDir, sanitize(name) + ".owl").toPath(), owl);

        return (fmt == Format.MARKDOWN)
                ? MarkdownComposer.write(outDir, name, english, turtle, svgs)
                : BundleHtmlComposer.write(outDir, name, english, turtle, svgs);
    }

    private static String sanitize(String s) {
        return (s == null ? "model" : s.replaceAll("[^a-zA-Z0-9._-]+", "_"));
    }
}
