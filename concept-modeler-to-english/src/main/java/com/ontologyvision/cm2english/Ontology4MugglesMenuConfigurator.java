package com.ontologyvision.cm2english;

import com.nomagic.actions.AMConfigurator;
import com.nomagic.actions.ActionsCategory;
import com.nomagic.actions.ActionsManager;
import com.nomagic.actions.NMAction;
import com.nomagic.magicdraw.actions.ActionsID;
import com.nomagic.magicdraw.actions.MDActionsCategory;

/**
 * Adds an <b>Ontology4Muggles</b> submenu to MagicDraw's main <b>Tools</b> menu — the same actions offered on
 * the Containment-tree right-click (verbalize / export), plus actions to surface the two dockable views. This
 * gives the plugin a clearly-branded home in the menu bar, distinct from the bundled <i>Cameo Concept Modeler</i>
 * menu it was previously confused with.
 *
 * <p>The main menu bar is built once, so a single guarded insert is enough; the actions resolve the active
 * package from the live selection ({@link ConceptModelSelection}) since there is no right-click context here.
 */
public final class Ontology4MugglesMenuConfigurator implements AMConfigurator {

    /** Category id for our Tools submenu (display name is what the user sees). */
    private static final String SUBMENU_ID = "ONTOLOGY4MUGGLES";
    private static final String SUBMENU_NAME = "Ontology4Muggles";

    @Override
    public void configure(ActionsManager manager) {
        ActionsCategory tools = manager.getCategory(ActionsID.TOOLS);
        if (tools == null) {
            return;
        }
        if (findSubmenu(tools) != null) {
            return; // already installed on this menu bar
        }
        MDActionsCategory submenu = new MDActionsCategory(SUBMENU_ID, SUBMENU_NAME);
        submenu.setNested(true);
        submenu.addAction(new Action_VerbalizeConceptModel());                                  // current selection
        submenu.addAction(new Action_ExportConceptModel(ConceptModelExporter.Format.HTML));
        submenu.addAction(new Action_ExportConceptModel(ConceptModelExporter.Format.MARKDOWN));
        submenu.addAction(new Action_ShowWindow(Action_ShowWindow.W.ENGLISH));
        submenu.addAction(new Action_ShowWindow(Action_ShowWindow.W.OWL));
        tools.addAction(submenu);
    }

    @Override
    public int getPriority() {
        // MEDIUM_PRIORITY == 1000; the literal avoids a hard constant dependency (matches VerbalizeBrowserConfigurator).
        return 1000;
    }

    /** Find our already-installed submenu under {@code tools}, or {@code null}. */
    private static NMAction findSubmenu(ActionsCategory tools) {
        for (NMAction a : tools.getActions()) {
            if (a != null && SUBMENU_ID.equals(a.getID())) {
                return a;
            }
        }
        return null;
    }
}
