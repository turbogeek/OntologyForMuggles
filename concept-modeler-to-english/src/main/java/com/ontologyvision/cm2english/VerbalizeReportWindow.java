package com.ontologyvision.cm2english;

import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.ui.ProjectWindow;
import com.nomagic.magicdraw.ui.ProjectWindowsConfigurator;
import com.nomagic.magicdraw.ui.ProjectWindowsManager;
import com.nomagic.magicdraw.ui.WindowComponentInfo;
import com.nomagic.magicdraw.ui.WindowsManager;
import com.nomagic.magicdraw.ui.browser.WindowComponentContent;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.NamedElement;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.AttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A dockable MagicDraw tool window that displays the SBVR-English report for a concept model in a Swing
 * {@link JEditorPane}, with a status bar pinned to the bottom of the window (always visible) that shows the
 * meaning of the hovered term. Docking is project-scoped (one panel per open project).
 *
 * <p>The report's own in-HTML gloss panel relies on {@code position:fixed} + JavaScript, neither of which a
 * {@code JEditorPane} supports, so the bridge verbalizes with {@code rollover(false)} (no in-HTML panel) and
 * this panel provides a native pinned gloss bar driven by hyperlink {@code ENTERED}/{@code EXITED} events.
 */
public final class VerbalizeReportWindow {

    public static final String WINDOW_ID = "CM2ENGLISH_REPORT_WINDOW";
    private static final String HINT = "Hover a term for its meaning · click a term to select it in the tree and open its diagram";

    /** Per-project window state: the panel to populate + the manager that can activate it. */
    private static final Map<Project, Holder> STATE = new ConcurrentHashMap<>();

    private VerbalizeReportWindow() { }

    /** Register the windows configurator once, at plugin init. */
    public static void register() {
        ProjectWindowsManager.ConfiguratorRegistry.addConfigurator(new Configurator());
    }

    /** Activate the report window for {@code project} and show a transient status line while work runs. */
    public static void showBusy(Project project, String message) {
        Holder h = STATE.get(project);
        if (h == null) {
            return;
        }
        h.panel.setHtml("<html><body style='font-family:sans-serif;color:#666;padding:12px'>" + escape(message)
                + "</body></html>", message);
        h.manager.activateWindow(project, WINDOW_ID);
    }

    /** Set the report HTML into the project's window, wire it to {@code pkg} for click-navigation, and show it. */
    public static void show(Project project, Package pkg, String html, String title) {
        Holder h = STATE.get(project);
        if (h == null) {
            ConceptModelEnglishBridge.info("Report window is not initialized for this project.");
            return;
        }
        h.panel.setContext(project, pkg);
        h.panel.setHtml(html, title);
        h.manager.activateWindow(project, WINDOW_ID);
    }

    private static String escape(String s) {
        return (s == null) ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ---- MagicDraw docking plumbing -----------------------------------------------------------------

    private static final class Configurator implements ProjectWindowsConfigurator {
        @Override
        public void configure(Project project, ProjectWindowsManager manager) {
            ReportPanel panel = new ReportPanel();
            AutoCloseable sub = SelectionBus.subscribe(project, panel::onSelection);
            STATE.put(project, new Holder(panel, manager, sub));
            WindowComponentInfo info = new WindowComponentInfo(
                    WINDOW_ID,
                    "Concept Model — English",
                    blankIcon(),
                    WindowsManager.SIDE_EAST,
                    WindowsManager.STATE_DOCKED,
                    false);
            manager.addWindowWithoutOpen(project, new ProjectWindow(info, new Content(panel)));
        }
    }

    /** Wraps the Swing panel for the docking framework. */
    private static final class Content implements WindowComponentContent {
        private final ReportPanel panel;

        Content(ReportPanel panel) {
            this.panel = panel;
        }

        @Override
        public Component getWindowComponent() {
            return panel;
        }

        @Override
        public Component getDefaultFocusComponent() {
            return panel.editor;
        }
    }

    /** The report view: an HTML JEditorPane + a pinned bottom gloss bar; {@code iri:} links navigate. */
    static final class ReportPanel extends JPanel {
        final JEditorPane editor = new JEditorPane();
        private final JLabel glossBar = new JLabel(HINT);
        private Project project;
        private Package pkg;

        ReportPanel() {
            super(new BorderLayout());
            editor.setEditable(false);
            editor.setContentType("text/html");
            editor.setEditorKit(new HTMLEditorKit());
            editor.setText("<html><body style='font-family:sans-serif;color:#888;padding:12px'>"
                    + "Right-click a concept-model package and choose "
                    + "<b>Concept Modeling &#9656; Verbalize to English</b> to populate this window.</body></html>");

            glossBar.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY),
                    BorderFactory.createEmptyBorder(4, 8, 4, 8)));
            glossBar.setForeground(Color.DARK_GRAY);

            editor.addHyperlinkListener(e -> {
                HyperlinkEvent.EventType t = e.getEventType();
                if (t == HyperlinkEvent.EventType.ACTIVATED) {
                    // Click a term -> select in the tree + open its diagram. getDescription() is the raw href
                    // (getURL() is null for the custom "iri:" scheme).
                    String href = e.getDescription();
                    if (href != null && href.startsWith("iri:")) {
                        ConceptNavigator.selectInTree(project, pkg, href);
                    }
                } else if (t == HyperlinkEvent.EventType.ENTERED) {
                    String gloss = linkTitle(e);
                    glossBar.setText((gloss == null || gloss.isEmpty()) ? HINT : gloss);
                } else if (t == HyperlinkEvent.EventType.EXITED) {
                    glossBar.setText(HINT);
                }
            });

            add(new JScrollPane(editor), BorderLayout.CENTER);
            add(glossBar, BorderLayout.SOUTH);   // pinned to the window bottom — always visible, never scrolls away
        }

        void setContext(Project project, Package pkg) {
            this.project = project;
            this.pkg = pkg;
        }

        void setHtml(String html, String title) {
            SwingUtilities.invokeLater(() -> {
                editor.setText(html);
                editor.setCaretPosition(0);
                glossBar.setText(HINT);
            });
        }

        /** Bus callback — when an element is selected elsewhere, scroll this report to its first mention. */
        void onSelection(List<Element> sel) {
            if (sel == null || sel.isEmpty() || !isShowing()) {
                return;
            }
            String name = nameOf(sel.get(0));
            if (name != null && !name.isEmpty()) {
                SwingUtilities.invokeLater(() -> scrollToText(name));
            }
        }

        private void scrollToText(String text) {
            try {
                javax.swing.text.Document doc = editor.getDocument();
                String s = doc.getText(0, doc.getLength());
                int idx = s.indexOf(text);
                if (idx >= 0) {
                    java.awt.geom.Rectangle2D r = editor.modelToView2D(idx);
                    if (r != null) {
                        editor.scrollRectToVisible(new java.awt.Rectangle(
                                (int) r.getX(), (int) r.getY(), (int) r.getWidth() + 1, (int) r.getHeight() + 40));
                    }
                }
            } catch (Throwable ignored) {
                // best-effort scroll
            }
        }

        private static String nameOf(Element el) {
            try {
                return (el instanceof NamedElement) ? ((NamedElement) el).getName() : null;
            } catch (Throwable t) {
                return null;
            }
        }

        /** The hovered anchor's {@code title} attribute (the gloss) — JEditorPane keeps it on the {@code <a>} element. */
        private static String linkTitle(HyperlinkEvent e) {
            try {
                javax.swing.text.Element src = e.getSourceElement();
                if (src == null) {
                    return null;
                }
                Object a = src.getAttributes().getAttribute(HTML.Tag.A);
                if (a instanceof AttributeSet) {
                    Object title = ((AttributeSet) a).getAttribute(HTML.Attribute.TITLE);
                    return (title == null) ? null : title.toString();
                }
            } catch (Throwable ignored) {
                // best-effort
            }
            return null;
        }
    }

    private static final class Holder {
        final ReportPanel panel;
        final ProjectWindowsManager manager;
        final AutoCloseable sub;

        Holder(ReportPanel panel, ProjectWindowsManager manager, AutoCloseable sub) {
            this.panel = panel;
            this.manager = manager;
            this.sub = sub;
        }
    }

    /** A 1×1 transparent icon (the docking API wants a non-null Icon for the tab). */
    private static ImageIcon blankIcon() {
        return new ImageIcon(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));
    }
}
