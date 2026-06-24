package com.ontologyvision.cm2english;

import com.nomagic.magicdraw.actions.ActionsConfiguratorsManager;
import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.plugins.Plugin;

import java.io.File;

/**
 * Entry point. Registers a Containment-tree context-menu configurator that offers
 * "Verbalize to English…" on Concept Modeler packages.
 *
 * <p>The plugin's install directory is captured in {@link #PLUGIN_DIR} so the bridge can locate the
 * bundled verbalizer jar (under {@code <pluginDir>/lib/}) at runtime.
 */
public class ConceptToEnglishPlugin extends Plugin {

    /** Install directory of this plugin (set in {@link #init()}); the verbalizer jar lives in {@code lib/} here. */
    public static volatile File PLUGIN_DIR;

    private final VerbalizeBrowserConfigurator browserConfigurator = new VerbalizeBrowserConfigurator();
    private final Ontology4MugglesMenuConfigurator mainMenuConfigurator = new Ontology4MugglesMenuConfigurator();

    @Override
    public void init() {
        PLUGIN_DIR = getDescriptor().getPluginDirectory();
        ActionsConfiguratorsManager acm = ActionsConfiguratorsManager.getInstance();
        acm.addContainmentBrowserContextConfigurator(browserConfigurator);
        acm.addMainMenuConfigurator(mainMenuConfigurator);   // Tools ▸ Ontology4Muggles
        VerbalizeReportWindow.register();
        OwlViewWindow.register();
        log("Concept Modeler to English initialized (dir=" + PLUGIN_DIR + ").");
    }

    @Override
    public boolean close() {
        try {
            ActionsConfiguratorsManager acm = ActionsConfiguratorsManager.getInstance();
            acm.removeContainmentBrowserContextConfigurator(browserConfigurator);
            acm.removeMainMenuConfigurator(mainMenuConfigurator);
        } catch (Throwable ignored) {
            // best-effort on shutdown
        }
        return true;
    }

    @Override
    public boolean isSupported() {
        return true;
    }

    private static void log(String msg) {
        try {
            Application.getInstance().getGUILog().log("[CM2English] " + msg);
        } catch (Throwable ignored) {
            // GUI log may not be ready in some lifecycles; safe to ignore.
        }
    }
}
