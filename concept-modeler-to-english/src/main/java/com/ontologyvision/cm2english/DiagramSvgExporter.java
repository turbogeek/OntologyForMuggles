package com.ontologyvision.cm2english;

import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.export.image.ImageExporter;
import com.nomagic.magicdraw.uml.symbols.AbstractDiagramPresentationElement;
import com.nomagic.magicdraw.uml.symbols.PresentationElement;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Exports the diagrams that a package's concepts appear on to SVG files, and reads each back as a String for
 * inlining into the interactive HTML export.
 *
 * <p>v1: plain SVG (viewable). v2 (planned) registers an {@code SVGExportEnricher} so each shape's {@code <g>}
 * carries {@code data-iri}, making diagram shapes click-to-navigate — guarded by reflection + a self-test, with
 * graceful fallback to plain SVG.
 */
public final class DiagramSvgExporter {

    private DiagramSvgExporter() { }

    /** One exported diagram: its (sanitized) name, the written SVG file, and the SVG text. */
    public static final class DiagramSvg {
        public final String name;
        public final File file;
        public final String svg;

        DiagramSvg(String name, File file, String svg) {
            this.name = name;
            this.file = file;
            this.svg = svg;
        }
    }

    /** Export every diagram any concept under {@code scope} appears on, into {@code outDir}, as SVG. */
    public static List<DiagramSvg> exportDiagrams(Project project, Element scope, File outDir) {
        List<DiagramSvg> result = new ArrayList<>();
        if (project == null || scope == null) {
            return result;
        }
        Set<AbstractDiagramPresentationElement> diagrams = collectDiagrams(project, scope);
        if (diagrams.isEmpty()) {
            return result;
        }
        outDir.mkdirs();
        int i = 0;
        Set<String> usedNames = new LinkedHashSet<>();
        for (AbstractDiagramPresentationElement dpe : diagrams) {
            try {
                dpe.ensureLoaded();
                String base = uniqueName(sanitize(diagramName(dpe, i + 1)), usedNames);
                File svgFile = new File(outDir, base + ".svg");
                ImageExporter.export(dpe, ImageExporter.SVG, svgFile);   // public Open API
                String svg = new String(Files.readAllBytes(svgFile.toPath()), StandardCharsets.UTF_8);
                result.add(new DiagramSvg(base, svgFile, svg));
                i++;
            } catch (Throwable t) {
                ConceptModelEnglishBridge.reportError("SVG export failed for a diagram", t);
            }
        }
        return result;
    }

    private static Set<AbstractDiagramPresentationElement> collectDiagrams(Project project, Element scope) {
        Set<AbstractDiagramPresentationElement> set = new LinkedHashSet<>();
        collect(project, scope, set);
        return set;
    }

    private static void collect(Project project, Element el, Set<AbstractDiagramPresentationElement> set) {
        try {
            List<PresentationElement> pes = project.getSymbolElementMap().getAllPresentationElements(el);
            if (pes != null) {
                for (PresentationElement pe : pes) {
                    AbstractDiagramPresentationElement d = pe.getAbstractDiagramPresentationElement();
                    if (d != null) {
                        set.add(d);
                    }
                }
            }
        } catch (Throwable ignored) {
            // skip elements that fault during lookup
        }
        for (Element c : el.getOwnedElement()) {
            collect(project, c, set);
        }
    }

    private static String diagramName(AbstractDiagramPresentationElement dpe, int ordinal) {
        try {
            String n = dpe.getName();
            return (n == null || n.isEmpty()) ? ("diagram-" + ordinal) : n;
        } catch (Throwable t) {
            return "diagram-" + ordinal;
        }
    }

    private static String sanitize(String s) {
        return (s == null ? "diagram" : s.replaceAll("[^a-zA-Z0-9._-]+", "_"));
    }

    private static String uniqueName(String base, Set<String> used) {
        String name = base;
        int n = 2;
        while (!used.add(name)) {
            name = base + "-" + (n++);
        }
        return name;
    }
}
