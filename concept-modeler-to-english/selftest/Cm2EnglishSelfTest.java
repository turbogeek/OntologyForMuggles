package com.ontologyvision.cm2english;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Headless proof for the two pure-string transforms changed this round (no MagicDraw runtime needed):
 *   1) SwingReportHtml.forSwing      — restyles the report for the JEditorPane (bold headings, no dead toolbar).
 *   2) BundleHtmlComposer.composeHtml — references diagrams as sibling <img> docs, never inline <svg>.
 * Writes a pass/fail diagnostic to selftest/selftest-result.txt that Claude can read back.
 */
public class Cm2EnglishSelfTest {

    private static int pass = 0, fail = 0;
    private static final StringBuilder LOG = new StringBuilder();

    public static void main(String[] args) throws Exception {
        // A miniature of the verbalizer's real output: a <style>, the flex toolbar, an inline <script>,
        // an entity heading (.ehead + .kind) and a bulleted statement list.
        String report =
            "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>T</title>"
          + "<style>.ehead{font-size:14px;} .toolbar{display:flex;}</style></head>"
          + "<body class=\"\"><div class=\"wrap\"><h1>Concept Model</h1>"
          + "<div class=\"toolbar\"><label>SBVR colors: <select id=\"t-colormode\">"
          + "<option>Full color</option></select></label></div>"
          + "<div class=\"entity\"><div class=\"ehead\"><a class=\"term\" href=\"iri:x\">Carnivorous Plant</a>"
          + " <span class=\"kind\">(concept)</span></div>"
          + "<ul><li>Each Carnivorous Plant eats at least one animal.</li>"
          + "<li>Each Carnivorous Plant is a plant.</li></ul></div>"
          + "<script>console.log('inert in swing');</script></body></html>";

        String swing = SwingReportHtml.forSwing(report);
        check("forSwing: makes .ehead bold", swing.contains(".ehead{") && swing.contains("font-weight:bold"));
        check("forSwing: drops the flex toolbar", !swing.contains("class=\"toolbar\""));
        check("forSwing: drops <script>", !swing.toLowerCase().contains("<script"));
        check("forSwing: keeps the entity heading text", swing.contains("Carnivorous Plant"));
        check("forSwing: keeps the bullet statements", swing.contains("<li>Each Carnivorous Plant is a plant."));
        check("forSwing: keeps SBVR role classes", swing.contains("a.term{") && swing.contains("#157f3b"));
        check("forSwing: no flex/fixed left for the kit to choke on", !swing.contains("display:flex"));

        // Diagram referencing: build a DiagramSvg (package-private ctor is reachable here) with INLINE svg text;
        // the composer must reference the sibling file and NOT inline that svg.
        File svgFile = new File(new File("diagrams"), "African_Wildlife.svg");
        DiagramSvgExporter.DiagramSvg d = new DiagramSvgExporter.DiagramSvg(
                "African Wildlife", svgFile, "<svg id=\"st0\"><style>.st0{fill:red}</style><rect/></svg>");
        List<DiagramSvgExporter.DiagramSvg> diagrams = new ArrayList<>();
        diagrams.add(d);

        String english = "<!DOCTYPE html><html><head></head><body><h1>x</h1></body></html>";
        String html = BundleHtmlComposer.composeHtml(english, "@prefix : <#> . :a a :B .", diagrams, "African Wildlife");
        check("composeHtml: references the sibling svg via <img src=diagrams/...>",
                html.contains("<img class=\"cm2-diagram-img\"") && html.contains("src=\"diagrams/African_Wildlife.svg\""));
        check("composeHtml: does NOT inline the diagram <svg> (no id/style collisions)",
                !html.contains("<style>.st0{fill:red}") && !html.contains("<rect/>"));
        check("composeHtml: still embeds the OWL Turtle pane", html.contains("id=\"cm2-owl\"") && html.contains(":a a :B"));
        check("composeHtml: keeps the diagram styling hook", html.contains(".cm2-diagram-img{"));

        File out = new File("selftest", "selftest-result.txt");
        out.getParentFile().mkdirs();
        try (PrintWriter w = new PrintWriter(out, "UTF-8")) {
            w.println("Cm2English self-test  pass=" + pass + " fail=" + fail);
            w.println();
            w.print(LOG);
        }
        System.out.println("pass=" + pass + " fail=" + fail + " -> " + out.getAbsolutePath());
        if (fail > 0) {
            System.out.println(LOG);
        }
    }

    private static void check(String name, boolean ok) {
        if (ok) { pass++; } else { fail++; }
        LOG.append(ok ? "PASS  " : "FAIL  ").append(name).append('\n');
    }
}
