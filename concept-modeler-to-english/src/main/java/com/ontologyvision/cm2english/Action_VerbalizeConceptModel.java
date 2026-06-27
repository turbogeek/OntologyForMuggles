package com.ontologyvision.cm2english;

import com.nomagic.magicdraw.actions.MDAction;
import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package;

import javax.swing.KeyStroke;
import javax.swing.SwingWorker;
import java.awt.event.ActionEvent;

/**
 * The "Verbalize to English…" menu action. Captures the package the user right-clicked and, on
 * invocation, exports + verbalizes it and shows the report in the dockable {@link VerbalizeReportWindow}.
 */
public class Action_VerbalizeConceptModel extends MDAction {

    /** Captured package (right-click), or {@code null} for the Tools-menu variant that uses the live selection. */
    private final Package fixedPkg;

    /** Containment-tree right-click: act on the captured package. */
    public Action_VerbalizeConceptModel(Package pkg) {
        this(pkg, "VERBALIZE_CM_TO_ENGLISH");
    }

    /** Tools ▸ Ontology4Muggles: act on whatever package is currently selected. */
    public Action_VerbalizeConceptModel() {
        this(null, "VERBALIZE_CM_TO_ENGLISH_MENU");
    }

    private Action_VerbalizeConceptModel(Package pkg, String id) {
        super(id, "Verbalize to English…", (KeyStroke) null, null);
        this.fixedPkg = pkg;
    }

    @Override
    public void updateState() {
        setEnabled(fixedPkg != null
                || ConceptModelSelection.current(Application.getInstance().getProject()) != null);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        final Project project = Application.getInstance().getProject();
        final Package chosen = (fixedPkg != null) ? fixedPkg : ConceptModelSelection.current(project);
        // Auto-locate the real concept model if the user pointed at a container (e.g. "Imported Ontologies").
        final ConceptModelLocator.Target target = ConceptModelLocator.resolve(project, chosen, "verbalize");
        if (target.cancelled) {
            return;
        }
        final Package pkg = target.pkg;
        if (pkg == null) {
            ConceptModelEnglishBridge.info("No concept model found. Select (or open) a «Concept Model» "
                    + "package — one that carries an ontology IRI — then choose Verbalize to English.");
            return;
        }
        final String name = ConceptModelEnglishBridge.safeName(pkg);
        VerbalizeReportWindow.showBusy(project, "Verbalizing \"" + name + "\" …");
        // OWL export + verbalize are heavy and the OWL API is not thread-safe — run off the EDT,
        // then update the pane on the EDT (SwingWorker.done()).
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                byte[] owl = ConceptModelEnglishBridge.exportOwl(pkg);
                if (owl == null || owl.length == 0) {
                    throw new IllegalStateException("The Concept Modeler produced no OWL for \"" + name
                            + "\". Choose a concept-model package that carries an ontology URI.");
                }
                InspectorState.set(project, pkg, owl);   // shared with the OWL-view window + selection highlighting
                return ConceptModelEnglishBridge.verbalize(owl, "Concept Model — English: " + name, true);
            }

            @Override
            protected void done() {
                try {
                    VerbalizeReportWindow.show(project, pkg, get(), name);
                } catch (Throwable t) {
                    ConceptModelEnglishBridge.reportError("Verbalize to English failed", t);
                }
            }
        }.execute();
    }
}
