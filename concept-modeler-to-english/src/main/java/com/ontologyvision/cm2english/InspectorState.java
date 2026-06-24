package com.ontologyvision.cm2english;

import com.nomagic.magicdraw.core.Project;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-project inspector context: the last-verbalized package + its exported OWL bytes. The verbalize action
 * sets it; the OWL-view window and selection highlighting read it, so they can render the selected element's OWL
 * (and resolve names) without re-running the CCM export.
 */
public final class InspectorState {

    private InspectorState() { }

    /** A verbalized package and the OWL bytes the Concept Modeler exported for it. */
    public static final class Ctx {
        public final Package pkg;
        public final byte[] owl;

        Ctx(Package pkg, byte[] owl) {
            this.pkg = pkg;
            this.owl = owl;
        }
    }

    private static final Map<Project, Ctx> STATE = new ConcurrentHashMap<>();

    public static void set(Project project, Package pkg, byte[] owl) {
        if (project != null && owl != null) {
            STATE.put(project, new Ctx(pkg, owl));
        }
    }

    public static Ctx get(Project project) {
        return (project == null) ? null : STATE.get(project);
    }
}
