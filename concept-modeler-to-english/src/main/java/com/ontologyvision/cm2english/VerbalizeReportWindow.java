package com.ontologyvision.cm2english;

import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.ui.ProjectWindow;
import com.nomagic.magicdraw.ui.ProjectWindowsConfigurator;
import com.nomagic.magicdraw.ui.ProjectWindowsManager;
import com.nomagic.magicdraw.ui.WindowComponentInfo;
import com.nomagic.magicdraw.ui.WindowsManager;
import com.nomagic.magicdraw.ui.browser.WindowComponentContent;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package;

import javax.swing.ImageIcon;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A dockable MagicDraw tool window that displays the SBVR-English report for a concept model in a Swing
 * {@link JEditorPane}. Docking is project-scoped (one panel per open project), mirroring VOM's Reasoner
 * window pattern.
 *
 * <p>Phase B: the report's {@code href="iri:<IRI>"} term links are caught by a {@code HyperlinkListener} and
 * routed to {@link ConceptNavigator}, which selects the corresponding element in the Containment tree.
 */
public final class VerbalizeReportWindow {

    public static final String WINDOW_ID = "CM2ENGLISH_REPORT_WINDOW";

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
            STATE.put(project, new Holder(panel, manager));
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

    /** The report view: an HTML JEditorPane whose {@code iri:} links navigate to the element in the tree. */
    static final class ReportPanel extends JPanel {
        final JEditorPane editor = new JEditorPane();
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
            // Click a term -> select its model element in the Containment tree. getDescription() is the raw href
            // (getURL() is null for the custom "iri:" scheme, which JEditorPane cannot parse as a URL).
            editor.addHyperlinkListener(e -> {
                if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                    String href = e.getDescription();
                    if (href != null && href.startsWith("iri:")) {
                        ConceptNavigator.selectInTree(project, pkg, href);
                    }
                }
            });
            add(new JScrollPane(editor), BorderLayout.CENTER);
        }

        void setContext(Project project, Package pkg) {
            this.project = project;
            this.pkg = pkg;
        }

        void setHtml(String html, String title) {
            SwingUtilities.invokeLater(() -> {
                editor.setText(html);
                editor.setCaretPosition(0);
            });
        }
    }

    private static final class Holder {
        final ReportPanel panel;
        final ProjectWindowsManager manager;

        Holder(ReportPanel panel, ProjectWindowsManager manager) {
            this.panel = panel;
            this.manager = manager;
        }
    }

    /** A 1×1 transparent icon (the docking API wants a non-null Icon for the tab). */
    private static ImageIcon blankIcon() {
        return new ImageIcon(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));
    }
}
