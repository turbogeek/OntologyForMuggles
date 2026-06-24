package com.ontologyvision.cm2english;

import com.nomagic.magicdraw.actions.MDAction;
import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package;

import javax.swing.JFileChooser;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;
import java.awt.Desktop;
import java.awt.event.ActionEvent;
import java.io.File;

/**
 * "Save to HTML…" / "Save to Markdown…" context-menu action. Prompts for an output folder, then exports the
 * package (English + OWL + diagram SVGs) on a background worker via {@link ConceptModelExporter}.
 */
public class Action_ExportConceptModel extends MDAction {

    private final Package pkg;
    private final ConceptModelExporter.Format fmt;

    public Action_ExportConceptModel(Package pkg, ConceptModelExporter.Format fmt) {
        super("EXPORT_CM_" + fmt,
                fmt == ConceptModelExporter.Format.HTML ? "Save to HTML…" : "Save to Markdown…",
                (KeyStroke) null, null);
        this.pkg = pkg;
        this.fmt = fmt;
    }

    @Override
    public void updateState() {
        setEnabled(pkg != null);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        final Project project = Application.getInstance().getProject();
        final String label = (fmt == ConceptModelExporter.Format.HTML) ? "HTML" : "Markdown";

        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle("Choose an output folder for the " + label + " export");
        if (fc.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        final File outDir = fc.getSelectedFile();
        final String name = ConceptModelEnglishBridge.safeName(pkg);
        ConceptModelEnglishBridge.info("Exporting \"" + name + "\" to " + label + " in " + outDir + " …");

        new SwingWorker<File, Void>() {
            @Override
            protected File doInBackground() throws Exception {
                return ConceptModelExporter.export(project, pkg, outDir, fmt);
            }

            @Override
            protected void done() {
                try {
                    File out = get();
                    ConceptModelEnglishBridge.info("Exported: " + out.getAbsolutePath());
                    try {
                        if (Desktop.isDesktopSupported()) {
                            Desktop.getDesktop().open(outDir);
                        }
                    } catch (Throwable ignored) {
                        // opening the folder is best-effort
                    }
                } catch (Throwable t) {
                    ConceptModelEnglishBridge.reportError("Export failed", t);
                }
            }
        }.execute();
    }
}
