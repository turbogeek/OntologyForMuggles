package com.ontologyvision.cm2english;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * The "simpler version": a Markdown export — the English reading as text, the OWL as a fenced {@code ```turtle}
 * block, and the diagrams as image references to the sibling {@code diagrams/*.svg} files. Static (no JS); clean
 * for docs and git diffs.
 */
public final class MarkdownComposer {

    private MarkdownComposer() { }

    public static String composeMarkdown(String englishHtml, String owlTurtle,
                                         List<DiagramSvgExporter.DiagramSvg> diagrams, String title) {
        StringBuilder md = new StringBuilder();
        md.append("# ").append(title == null || title.isEmpty() ? "Concept Model" : title).append("\n\n");
        md.append(htmlToText(englishHtml)).append("\n\n");
        md.append("## OWL (Turtle)\n\n```turtle\n").append(owlTurtle == null ? "" : owlTurtle.trim()).append("\n```\n\n");
        if (diagrams != null && !diagrams.isEmpty()) {
            md.append("## Diagrams\n\n");
            for (DiagramSvgExporter.DiagramSvg d : diagrams) {
                md.append("### ").append(d.name).append("\n\n")
                  .append("![").append(d.name).append("](diagrams/").append(d.name).append(".svg)\n\n");
            }
        }
        return md.toString();
    }

    /** Write {@code <pkg>.md} into {@code outDir}; returns the file. */
    public static File write(File outDir, String pkgName, String englishHtml, String owlTurtle,
                             List<DiagramSvgExporter.DiagramSvg> diagrams) throws Exception {
        outDir.mkdirs();
        String md = composeMarkdown(englishHtml, owlTurtle, diagrams, pkgName);
        File mdFile = new File(outDir, sanitize(pkgName) + ".md");
        Files.write(mdFile.toPath(), md.getBytes(StandardCharsets.UTF_8));
        return mdFile;
    }

    /** Crude HTML -> readable text for the simple Markdown export (keeps line structure, drops the chrome). */
    static String htmlToText(String html) {
        if (html == null) {
            return "";
        }
        String s = html;
        s = s.replaceAll("(?is)<style.*?</style>", "");
        s = s.replaceAll("(?is)<script.*?</script>", "");
        s = s.replaceAll("(?i)<li[^>]*>", "\n- ");
        s = s.replaceAll("(?i)</(p|div|li|tr|h1|h2|h3|h4|section|article)>", "\n");
        s = s.replaceAll("(?i)<br\\s*/?>", "\n");
        s = s.replaceAll("<[^>]+>", "");
        s = s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
             .replace("&nbsp;", " ").replace("&mdash;", "—").replace("&middot;", "·").replace("&#9656;", "▸");
        StringBuilder out = new StringBuilder();
        for (String line : s.split("\n")) {
            String t = line.replaceAll("[ \\t]+", " ").trim();
            if (!t.isEmpty()) {
                out.append(t).append("\n");
            }
        }
        return out.toString().trim();
    }

    private static String sanitize(String s) {
        return (s == null ? "model" : s.replaceAll("[^a-zA-Z0-9._-]+", "_"));
    }
}
