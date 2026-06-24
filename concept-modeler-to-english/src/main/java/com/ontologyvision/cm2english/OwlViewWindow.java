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

import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingWorker;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A dockable window showing the selected element's OWL: a clickable <b>Manchester</b> tab (entity tokens are
 * {@code iri:} links that drive the same {@link SelectionBus}) + a viewable <b>Turtle</b> tab. It subscribes to
 * the bus, so selecting an element anywhere (Containment tree, diagram, the English pane, or this Manchester
 * tab) re-renders this view; a "Whole ontology" toggle switches between the selected entity's axioms and the
 * whole ontology. Reads the OWL bytes from {@link InspectorState} (set when a package is verbalized).
 */
public final class OwlViewWindow {

    public static final String WINDOW_ID = "CM2ENGLISH_OWL_WINDOW";
    private static final Map<Project, Holder> STATE = new ConcurrentHashMap<>();

    private OwlViewWindow() { }

    /** Register once, at plugin init. */
    public static void register() {
        ProjectWindowsManager.ConfiguratorRegistry.addConfigurator(new Configurator());
    }

    /** Bring the OWL window to front (used when there's no separate open action). */
    public static void activate(Project project) {
        Holder h = STATE.get(project);
        if (h != null) {
            h.manager.activateWindow(project, WINDOW_ID);
        }
    }

    private static final class Configurator implements ProjectWindowsConfigurator {
        @Override
        public void configure(Project project, ProjectWindowsManager manager) {
            OwlPanel panel = new OwlPanel(project);
            AutoCloseable sub = SelectionBus.subscribe(project, panel::onSelection);
            STATE.put(project, new Holder(panel, manager, sub));
            WindowComponentInfo info = new WindowComponentInfo(
                    WINDOW_ID, "Concept Model — OWL", blankIcon(),
                    WindowsManager.SIDE_SOUTH, WindowsManager.STATE_DOCKED, false);
            manager.addWindowWithoutOpen(project, new ProjectWindow(info, new Content(panel)));
        }
    }

    private static final class Content implements WindowComponentContent {
        private final OwlPanel panel;
        Content(OwlPanel panel) { this.panel = panel; }
        @Override public Component getWindowComponent() { return panel; }
        @Override public Component getDefaultFocusComponent() { return panel.manchester; }
    }

    static final class OwlPanel extends JPanel {
        private final Project project;
        final JEditorPane manchester = new JEditorPane();
        final JEditorPane turtle = new JEditorPane();
        private final JCheckBox whole = new JCheckBox("Whole ontology");
        private Element lastElement;

        OwlPanel(Project project) {
            super(new BorderLayout());
            this.project = project;

            manchester.setEditable(false);
            manchester.setContentType("text/html");
            manchester.setEditorKit(new HTMLEditorKit());
            manchester.setText(hint());
            manchester.addHyperlinkListener(e -> {
                if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                    String href = e.getDescription();
                    InspectorState.Ctx ctx = InspectorState.get(project);
                    if (href != null && href.startsWith("iri:") && ctx != null) {
                        ConceptNavigator.selectInTree(project, ctx.pkg, href);
                    }
                }
            });

            turtle.setEditable(false);
            turtle.setContentType("text/plain");
            turtle.setFont(new Font("Monospaced", Font.PLAIN, 12));
            turtle.setText("Select an element to see its OWL.");

            JTabbedPane tabs = new JTabbedPane();
            tabs.addTab("Manchester", new JScrollPane(manchester));
            tabs.addTab("Turtle", new JScrollPane(turtle));

            JPanel top = new JPanel(new BorderLayout());
            whole.addActionListener(a -> render(lastElement));
            top.add(whole, BorderLayout.WEST);

            add(top, BorderLayout.NORTH);
            add(tabs, BorderLayout.CENTER);
        }

        /** Bus callback — re-render for the (first) selected element, but only when this window is visible. */
        void onSelection(List<Element> sel) {
            lastElement = (sel == null || sel.isEmpty()) ? null : sel.get(0);
            if (isShowing()) {
                render(lastElement);
            }
        }

        private void render(Element el) {
            final InspectorState.Ctx ctx = InspectorState.get(project);
            if (ctx == null) {
                manchester.setText(hint());
                turtle.setText("Verbalize a concept model first (right-click a package → Verbalize to English).");
                return;
            }
            final String frag = whole.isSelected() ? null : fragOf(el);
            if (!whole.isSelected() && frag == null) {
                return; // per-element mode with no element selected — leave the current view
            }
            new SwingWorker<String[], Void>() {
                @Override
                protected String[] doInBackground() throws Exception {
                    return new String[] {
                            ConceptModelEnglishBridge.owlManchesterHtml(ctx.owl, frag),
                            ConceptModelEnglishBridge.owlTurtle(ctx.owl, frag)
                    };
                }

                @Override
                protected void done() {
                    try {
                        String[] r = get();
                        manchester.setText(SwingReportHtml.forSwing(r[0]));   // Swing-tuned restyle
                        manchester.setCaretPosition(0);
                        turtle.setText(r[1]);
                        turtle.setCaretPosition(0);
                    } catch (Throwable t) {
                        ConceptModelEnglishBridge.reportError("OWL view render failed", t);
                    }
                }
            }.execute();
        }

        private static String fragOf(Element el) {
            if (!(el instanceof NamedElement)) {
                return null;
            }
            String n = ((NamedElement) el).getName();
            return (n == null || n.isEmpty()) ? null : n.replaceAll("\\s+", "");
        }

        private static String hint() {
            return "<html><body style='font-family:sans-serif;color:#888;padding:12px'>"
                 + "Select an element (in the Containment tree, a diagram, or the English view) to see its OWL here."
                 + "</body></html>";
        }
    }

    private static final class Holder {
        final OwlPanel panel;
        final ProjectWindowsManager manager;
        final AutoCloseable sub;

        Holder(OwlPanel panel, ProjectWindowsManager manager, AutoCloseable sub) {
            this.panel = panel;
            this.manager = manager;
            this.sub = sub;
        }
    }

    private static ImageIcon blankIcon() {
        return new ImageIcon(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));
    }
}
