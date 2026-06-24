package com.ontologyvision.cm2english;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * Composes a single self-contained interactive HTML report from the verbalizer's English HTML + the OWL
 * (Turtle) + the diagram SVGs. The English already carries {@code <a href="iri:<IRI>">} term links; this injects
 * an OWL pane, the diagrams as <b>inline</b> SVG, and a small vanilla-JS linker that — on an English term click —
 * scrolls/highlights the OWL pane and highlights the matching diagram shape (shapes carry {@code data-frag} once
 * the SVG enricher lands in v2; until then the diagrams are viewable and the OWL cross-link works).
 *
 * <p>The diagrams are referenced as sibling {@code diagrams/*.svg} files (written by {@link DiagramSvgExporter})
 * rather than inlined. Inlining several MagicDraw SVGs into one document collides their global {@code <style>}
 * class names and {@code clipPath}/gradient {@code id}s (HTML has no per-{@code <svg>} scope), which renders the
 * later diagrams' styles over the earlier ones — the "partially broken images" symptom. Referencing each SVG as
 * its own document restores isolation. (Per-shape click-to-navigate returns in v2 with the SVG enricher.)
 */
public final class BundleHtmlComposer {

    private BundleHtmlComposer() { }

    private static final String CSS =
            "<style>"
          + ".cm2-owl{background:#f7f7f7;border:1px solid #ddd;padding:8px;overflow:auto;max-height:50vh;"
          + "font-family:monospace;font-size:12px;white-space:pre-wrap;}"
          + ".cm2-diagram{border:1px solid #ddd;padding:8px;overflow:auto;margin:8px 0;}"
          + ".cm2-diagram-img{max-width:100%;height:auto;display:block;}"
          + ".cm2-hl{outline:3px solid orange;outline-offset:2px;}"
          + "</style>";

    private static final String SCRIPT =
            "<script>(function(){document.addEventListener('click',function(e){"
          + "var a=e.target.closest?e.target.closest('a[href^=\"iri:\"]'):null;if(!a)return;e.preventDefault();"
          + "var iri=decodeURIComponent(a.getAttribute('href').slice(4));"
          + "var frag=iri.split('#').pop().split('/').pop();"
          + "var owl=document.getElementById('cm2-owl');"
          + "if(owl){var t=owl.textContent||'';var i=t.indexOf(frag);if(i>=0){owl.scrollTop=Math.max(0,(i/t.length)*owl.scrollHeight-40);}}"
          + "var prev=document.querySelectorAll('.cm2-hl');for(var k=0;k<prev.length;k++)prev[k].classList.remove('cm2-hl');"
          + "var hits=document.querySelectorAll('[data-frag=\"'+frag+'\"]');for(var j=0;j<hits.length;j++)hits[j].classList.add('cm2-hl');"
          + "if(hits.length&&hits[0].scrollIntoView)hits[0].scrollIntoView({block:'center'});"
          + "},true);})();</script>";

    /** Inject the OWL pane + inline diagrams + linker into the English HTML. */
    public static String composeHtml(String englishHtml, String owlTurtle,
                                     List<DiagramSvgExporter.DiagramSvg> diagrams, String title) {
        StringBuilder extra = new StringBuilder();
        extra.append("<hr/><h2>OWL (Turtle)</h2><pre id=\"cm2-owl\" class=\"cm2-owl\">")
             .append(esc(owlTurtle == null ? "" : owlTurtle)).append("</pre>");
        if (diagrams != null && !diagrams.isEmpty()) {
            extra.append("<hr/><h2>Diagrams</h2>");
            for (DiagramSvgExporter.DiagramSvg d : diagrams) {
                // Reference the sibling SVG as its own document — isolated styles/ids, so multiple diagrams render
                // correctly (see the class note). The file lives in the diagrams/ folder next to this HTML.
                String rel = "diagrams/" + (d.file != null ? d.file.getName() : esc(d.name) + ".svg");
                extra.append("<h3>").append(esc(d.name)).append("</h3><div class=\"cm2-diagram\">")
                     .append("<img class=\"cm2-diagram-img\" alt=\"").append(escAttr(d.name))
                     .append("\" src=\"").append(escAttr(rel)).append("\"/></div>");
            }
        }
        extra.append(SCRIPT);

        String out = (englishHtml == null || englishHtml.isEmpty())
                ? "<html><head></head><body></body></html>" : englishHtml;
        out = injectBefore(out, "</head>", CSS);
        out = injectBefore(out, "</body>", extra.toString());
        return out;
    }

    /** Write {@code <pkg>-english.html} + {@code <pkg>.ttl} into {@code outDir}; returns the html file. */
    public static File write(File outDir, String pkgName, String englishHtml, String owlTurtle,
                             List<DiagramSvgExporter.DiagramSvg> diagrams) throws Exception {
        outDir.mkdirs();
        String safe = sanitize(pkgName);
        if (owlTurtle != null) {
            Files.write(new File(outDir, safe + ".ttl").toPath(), owlTurtle.getBytes(StandardCharsets.UTF_8));
        }
        String html = composeHtml(englishHtml, owlTurtle, diagrams, pkgName);
        File htmlFile = new File(outDir, safe + "-english.html");
        Files.write(htmlFile.toPath(), html.getBytes(StandardCharsets.UTF_8));
        return htmlFile;
    }

    private static String injectBefore(String html, String marker, String insert) {
        int i = html.toLowerCase().indexOf(marker.toLowerCase());
        return (i < 0) ? html + insert : html.substring(0, i) + insert + html.substring(i);
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Escape for a double-quoted HTML attribute value (adds quote escaping on top of {@link #esc}). */
    private static String escAttr(String s) {
        return esc(s == null ? "" : s).replace("\"", "&quot;");
    }

    private static String sanitize(String s) {
        return (s == null ? "model" : s.replaceAll("[^a-zA-Z0-9._-]+", "_"));
    }
}
