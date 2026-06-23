package com.ontologyvision.cm2english;

import com.nomagic.magicdraw.actions.MDAction;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package;

import javax.swing.KeyStroke;
import java.awt.event.ActionEvent;
import java.io.File;

/**
 * The "Verbalize to English…" menu action. Captures the package the user right-clicked and, on
 * invocation, runs the export+verbalize pipeline and opens the resulting HTML report.
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
        try {
            File html = ConceptModelEnglishBridge.verbalizePackageToHtmlFile(pkg);
            ConceptModelEnglishBridge.openInBrowser(html);
            ConceptModelEnglishBridge.info("Wrote and opened report: " + html.getAbsolutePath());
        } catch (Throwable t) {
            ConceptModelEnglishBridge.reportError("Verbalize to English failed", t);
        }
    }
}
