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

    private final Package pkg;

    public Action_VerbalizeConceptModel(Package pkg) {
        super("VERBALIZE_CM_TO_ENGLISH", "Verbalize to English…", (KeyStroke) null, null);
        this.pkg = pkg;
    }

    @Override
    public void updateState() {
        setEnabled(pkg != null);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        final Project project = Application.getInstance().getProject();
        final String name = ConceptModelEnglishBridge.safeName(pkg);
        VerbalizeReportWindow.showBusy(project, "Verbalizing \"" + name + "\" …");
        // OWL export + verbalize are heavy and the OWL API is not thread-safe — run off the EDT,
        // then update the pane on the EDT (SwingWorker.done()).
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return ConceptModelEnglishBridge.verbalizePackageToHtml(pkg);
            }

            @Override
            protected void done() {
                try {
                    VerbalizeReportWindow.show(project, get(), name);
                } catch (Throwable t) {
                    ConceptModelEnglishBridge.reportError("Verbalize to English failed", t);
                }
            }
        }.execute();
    }
}
