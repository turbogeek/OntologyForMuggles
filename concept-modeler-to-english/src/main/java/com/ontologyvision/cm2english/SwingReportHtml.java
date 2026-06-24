package com.ontologyvision.cm2english;

import java.util.regex.Matcher;

/**
 * Adapts the verbalizer's browser-grade HTML report for display in a Swing {@link javax.swing.JEditorPane}.
 *
 * <p>{@code JEditorPane}'s {@code HTMLEditorKit} is roughly CSS-1: it silently ignores {@code display:flex},
 * {@code position:fixed}, {@code border-radius}, and even numeric {@code font-weight:600} (only the keyword
 * {@code bold} works), and it renders {@code <select>}/{@code <input>} as live but non-functional form widgets.
 * The full report relies on all of those, so in the dock it reads as flat, unstyled text with a dead toolbar.
 *
 * <p>This swaps the report's {@code <style>} for a Swing-tuned stylesheet that uses only properties the kit
 * honors (and {@code bold} headings for real visual hierarchy), and strips the view-time chrome (the flex
 * toolbar, inline scripts, the fixed gloss panel). It deliberately keeps the verbalizer's class names
 * ({@code .ehead}, {@code .kind}, {@code .term}, …) so the same markup is restyled, not rewritten — the
 * exported-to-browser HTML stays untouched.
 */
final class SwingReportHtml {

    private SwingReportHtml() { }

    /** A JEditorPane-honored stylesheet for the report's class vocabulary (bold headings, tight lists, SBVR roles). */
    private static final String SWING_CSS =
            "body{font-family:'Segoe UI',Helvetica,Arial,sans-serif;font-size:12px;color:#1a1a1a;}"
          + ".wrap{margin:0;padding:8px 12px;}"
          + "h1{font-size:17px;font-weight:bold;color:#16324f;}"
          + "h2{font-size:14px;font-weight:bold;color:#16324f;}"
          + ".count{color:#999;font-weight:normal;font-size:11px;}"
          + ".sub{color:#777;font-size:11px;}"
          + ".stats{color:#777;font-size:11px;}"
          + ".legend{color:#666;font-size:11px;}"
          + ".note{color:#888;font-style:italic;font-size:11px;}"
          + ".entity{margin-top:12px;}"
          + ".ehead{font-size:13px;font-weight:bold;color:#16324f;}"
          + ".kind{color:#999;font-weight:normal;font-style:italic;}"
          + ".cmt{color:#555;font-style:italic;}"
          + "ul{margin-top:2px;margin-bottom:8px;}"
          + "li{margin-top:2px;margin-bottom:2px;}"
          + ".term{color:#157f3b;font-weight:bold;}"
          + "a.term{color:#157f3b;font-weight:bold;text-decoration:none;}"
          + ".verb{color:#1b6ca8;font-style:italic;}"
          + "a.verb{color:#1b6ca8;font-style:italic;text-decoration:none;}"
          + ".kw{color:#c8741a;font-weight:bold;}"
          + "a.kw{color:#c8741a;font-weight:bold;text-decoration:none;}"
          + ".name{color:#0a7d7d;font-weight:bold;}"
          + "a.name{color:#0a7d7d;font-weight:bold;text-decoration:none;}"
          + "table.gloss{border:1px solid #ddd;}"
          + "table.gloss th{font-weight:bold;color:#16324f;text-align:left;}"
          + "table.gloss td{color:#333;}";

    /**
     * Returns {@code html} restyled + de-chromed for a JEditorPane. Documents without a {@code <style>} block
     * (our own small busy/hint snippets) pass through unchanged.
     */
    static String forSwing(String html) {
        if (html == null || html.isEmpty() || !html.contains("<style")) {
            return html;
        }
        String out = html.replaceFirst("(?is)<style\\b[^>]*>.*?</style>",
                Matcher.quoteReplacement("<style>" + SWING_CSS + "</style>"));
        // The flex toolbar (no nested <div>) renders as dead Swing form controls — drop it.
        out = out.replaceAll("(?is)<div class=\"toolbar\">.*?</div>", "");
        // Inline scripts are ignored by the kit anyway; the fixed gloss panel is replaced by our native gloss bar.
        out = out.replaceAll("(?is)<script\\b[^>]*>.*?</script>", "");
        out = out.replaceAll("(?is)<div id=\"sbvr-gloss\"[^>]*>.*?</div>", "");
        return out;
    }
}
