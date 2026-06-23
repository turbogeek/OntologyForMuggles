package com.ontologyvision.cm2english;

import com.nomagic.actions.ActionsManager;
import com.nomagic.magicdraw.actions.BrowserContextAMConfigurator;
import com.nomagic.magicdraw.actions.MDActionsCategory;
import com.nomagic.magicdraw.ui.browser.Node;
import com.nomagic.magicdraw.ui.browser.Tree;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package;

/**
 * Adds the "Verbalize to English…" action to the Containment-tree right-click menu whenever a UML
 * {@link Package} is selected. (Concept models are packages; the action itself reports a friendly
 * message if the chosen package has no ontology URI to export.)
 *
 * <p>A browser context menu is rebuilt on every right-click, so this creates a fresh category + action
 * each time {@code configure} runs — no de-duplication needed.
 */
public class VerbalizeBrowserConfigurator implements BrowserContextAMConfigurator {

    @Override
    public void configure(ActionsManager manager, Tree tree) {
        Package pkg = selectedPackage(tree);
        if (pkg == null) {
            return; // not a package selection — offer nothing
        }
        MDActionsCategory category = new MDActionsCategory("CM2ENGLISH_CATEGORY", "Concept Modeling");
        category.setNested(true);
        category.addAction(new Action_VerbalizeConceptModel(pkg));
        manager.addCategory(category);
    }

    private static Package selectedPackage(Tree tree) {
        if (tree == null) {
            return null;
        }
        Node node = tree.getSelectedNode();
        if (node == null) {
            return null;
        }
        Object userObject = node.getUserObject();
        return (userObject instanceof Package) ? (Package) userObject : null;
    }

    @Override
    public int getPriority() {
        // Ordering of this configurator relative to others on the containment context menu.
        // AMConfigurator.MEDIUM_PRIORITY is 1000; use the literal to avoid a hard constant dependency.
        return 1000;
    }
}
