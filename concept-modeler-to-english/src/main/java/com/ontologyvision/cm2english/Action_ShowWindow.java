package com.ontologyvision.cm2english;

import com.nomagic.magicdraw.actions.MDAction;
import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.core.Project;

import javax.swing.KeyStroke;
import java.awt.event.ActionEvent;

/**
 * Brings one of the dockable Ontology4Muggles views to front. Custom {@code ProjectWindow}s registered via
 * {@code ProjectWindowsManager} do not auto-populate MagicDraw's Window menu, so these actions live in the
 * Tools &#9656; Ontology4Muggles menu to make the English / OWL panels discoverable.
 */
public class Action_ShowWindow extends MDAction {

    public enum W { ENGLISH, OWL }

    private final W which;

    public Action_ShowWindow(W which) {
        super(which == W.ENGLISH ? "SHOW_CM_ENGLISH_VIEW" : "SHOW_CM_OWL_VIEW",
                which == W.ENGLISH ? "Show English View" : "Show OWL View",
                (KeyStroke) null, null);
        this.which = which;
    }

    @Override
    public void updateState() {
        setEnabled(Application.getInstance().getProject() != null);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Project project = Application.getInstance().getProject();
        if (project == null) {
            return;
        }
        if (which == W.ENGLISH) {
            VerbalizeReportWindow.activate(project);
        } else {
            OwlViewWindow.activate(project);
        }
    }
}
