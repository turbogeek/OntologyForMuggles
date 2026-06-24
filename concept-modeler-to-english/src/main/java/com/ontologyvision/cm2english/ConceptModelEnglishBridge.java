package com.ontologyvision.cm2english;

import com.nomagic.conceptmodeler.transform.CM2OWL;
import com.nomagic.conceptmodeler.transform.ConceptModelOWLTransformFactory;
import com.nomagic.magicdraw.core.Application;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package;

import java.awt.Desktop;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * The pipeline, validated end-to-end by the Phase-0 probe:
 *
 * <ol>
 *   <li><b>Export</b> the concept-model {@link Package} to OWL via the Concept Modeler API
 *       ({@code CM2OWL.exportFromModel(Package, OutputStream)}) — typed, compiled against
 *       {@code conceptmodeler_api.jar}.</li>
 *   <li><b>Verbalize</b> those OWL bytes with the OntologyForMuggles verbalizer
 *       ({@code OntologyVerbalizer.verbalizeOntology}) — loaded reflectively from the bundled
 *       self-contained CLI jar in a <i>parent=null</i> {@link URLClassLoader}, so its owlapi 5.5.1
 *       is isolated from the Concept Modeler's owlapi 5.1.19. The two never exchange objects, only
 *       a {@code byte[]}.</li>
 * </ol>
 */
public final class ConceptModelEnglishBridge {

    private ConceptModelEnglishBridge() { }

    /** Export the package to OWL and verbalize to a self-contained SBVR-English HTML string (no file written). */
    public static String verbalizePackageToHtml(Package pkg) throws Exception {
        byte[] owl = exportOwl(pkg);
        if (owl == null || owl.length == 0) {
            throw new IllegalStateException("The Concept Modeler produced no OWL for package \"" + safeName(pkg)
                    + "\". Choose a concept-model package that carries an ontology URI "
                    + "(not the root model or a plain UML package).");
        }
        // entityLinks=true: emit href="iri:<IRI>" anchors so the docked window can map clicks to model elements.
        return verbalize(owl, "Concept Model — English: " + safeName(pkg), true);
    }

    /** Export the package to OWL, verbalize to SBVR English HTML, write it to a temp file, and return it. */
    public static File verbalizePackageToHtmlFile(Package pkg) throws Exception {
        String name = safeName(pkg);
        byte[] owl = exportOwl(pkg);
        if (owl == null || owl.length == 0) {
            throw new IllegalStateException("The Concept Modeler produced no OWL for package \"" + name
                    + "\". Choose a concept-model package that carries an ontology URI "
                    + "(not the root model or a plain UML package).");
        }
        String html = verbalize(owl, "Concept Model — English: " + name);

        // Write to a stable, discoverable folder (not the volatile temp dir) so reports persist and can be
        // inspected after the fact: %USERPROFILE%\cm2english\ (falls back to java.io.tmpdir if unavailable).
        File outDir = new File(System.getProperty("user.home", System.getProperty("java.io.tmpdir")), "cm2english");
        if (!outDir.isDirectory() && !outDir.mkdirs()) {
            outDir = new File(System.getProperty("java.io.tmpdir"));
        }
        File htmlOut = new File(outDir, "cm2english-" + sanitize(name) + ".html");
        Files.write(htmlOut.toPath(), html.getBytes(StandardCharsets.UTF_8));
        // Also drop the raw OWL beside it, for inspection / reuse.
        Files.write(new File(outDir, "cm2english-" + sanitize(name) + ".owl").toPath(), owl);
        return htmlOut;
    }

    /** Concept Modeler export — typed against conceptmodeler_api.jar. */
    static byte[] exportOwl(Package pkg) {
        CM2OWL exporter = ConceptModelOWLTransformFactory.createOWLExporter();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        exporter.exportFromModel(pkg, baos);
        return baos.toByteArray();
    }

    /** Back-compat delegate: verbalize with default options (gloss anchors, no iri links). */
    static String verbalize(byte[] owlBytes, String title) throws Exception {
        return verbalize(owlBytes, title, false);
    }

    /**
     * Verbalize OWL bytes to a self-contained HTML report using the bundled verbalizer jar, loaded in an
     * isolated classloader. When {@code entityLinks} is true, named entities are emitted as {@code href="iri:…"}
     * anchors (for click-to-navigate in the docked window); otherwise the default gloss anchors are used.
     */
    static String verbalize(byte[] owlBytes, String title, boolean entityLinks) throws Exception {
        File jar = findVerbalizerJar();
        URL[] urls = { jar.toURI().toURL() };
        ClassLoader previousContext = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader vcl = new URLClassLoader(urls, null)) {           // parent=null => isolated owlapi
            Thread.currentThread().setContextClassLoader(vcl);                // owlapi finds parsers via context CL
            Class<?> owlManagerCls = Class.forName("org.semanticweb.owlapi.apibinding.OWLManager", true, vcl);
            Object manager = owlManagerCls.getMethod("createOWLOntologyManager").invoke(null);
            applySilentImportHandling(manager, vcl);

            Method loadFromStream = findSingleParam(manager.getClass(),
                    "loadOntologyFromOntologyDocument", "java.io.InputStream");
            if (loadFromStream == null) {
                throw new NoSuchMethodException("OWLOntologyManager.loadOntologyFromOntologyDocument(InputStream)");
            }
            Object ontology = loadFromStream.invoke(manager, new ByteArrayInputStream(owlBytes));

            Class<?> verbalizerCls = Class.forName("com.ontologyvision.verbalizer.OntologyVerbalizer", true, vcl);
            Object verbalizer = verbalizerCls.getDeclaredConstructor().newInstance();
            Class<?> ontologyFace = Class.forName("org.semanticweb.owlapi.model.OWLOntology", true, vcl);
            if (entityLinks) {
                // VerbalizerOptions.builder().entityLinks(true).build() — reflectively, in the isolated CL.
                Class<?> optsCls = Class.forName("com.ontologyvision.verbalizer.VerbalizerOptions", true, vcl);
                Object builder = optsCls.getMethod("builder").invoke(null);
                builder = builder.getClass().getMethod("entityLinks", boolean.class).invoke(builder, Boolean.TRUE);
                // No in-HTML rollover panel/script: it relies on position:fixed + JS, which JEditorPane lacks; the
                // window provides a native pinned gloss bar instead (terms still carry the gloss as a title tooltip).
                builder = builder.getClass().getMethod("rollover", boolean.class).invoke(builder, Boolean.FALSE);
                Object opts = builder.getClass().getMethod("build").invoke(builder);
                Method vm3 = verbalizerCls.getMethod("verbalizeOntology", ontologyFace, String.class, optsCls);
                return (String) vm3.invoke(verbalizer, ontology, title, opts);
            }
            Method verbalizeOntology = verbalizerCls.getMethod("verbalizeOntology", ontologyFace, String.class);
            return (String) verbalizeOntology.invoke(verbalizer, ontology, title);
        } finally {
            Thread.currentThread().setContextClassLoader(previousContext);
        }
    }

    /** Serialize the OWL bytes to {@code "turtle"}/{@code "manchester"} text via OntologySerializer (isolated CL). */
    static String serializeOwl(byte[] owlBytes, String fmt) throws Exception {
        return inIsolatedClassLoader(owlBytes, (vcl, ontology, ontFace) -> {
            Class<?> serCls = Class.forName("com.ontologyvision.verbalizer.OntologySerializer", true, vcl);
            String method = "manchester".equalsIgnoreCase(fmt) ? "toManchester" : "toTurtle";
            return (String) serCls.getMethod(method, ontFace).invoke(null, ontology);
        });
    }

    /** Serialize just the axioms referencing {@code iri} to {@code "turtle"}/{@code "manchester"} text. */
    static String serializeOwlEntity(byte[] owlBytes, String iri, String fmt) throws Exception {
        return inIsolatedClassLoader(owlBytes, (vcl, ontology, ontFace) -> {
            Class<?> serCls = Class.forName("com.ontologyvision.verbalizer.OntologySerializer", true, vcl);
            String method = "manchester".equalsIgnoreCase(fmt) ? "entityToManchester" : "entityToTurtle";
            return (String) serCls.getMethod(method, ontFace, String.class).invoke(null, ontology, iri);
        });
    }

    /** Run {@code fn} with an OWLOntology loaded from {@code owlBytes} in the isolated verbalizer classloader. */
    private static String inIsolatedClassLoader(byte[] owlBytes, IsolatedFn fn) throws Exception {
        File jar = findVerbalizerJar();
        URL[] urls = { jar.toURI().toURL() };
        ClassLoader prev = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader vcl = new URLClassLoader(urls, null)) {
            Thread.currentThread().setContextClassLoader(vcl);
            Class<?> owlManagerCls = Class.forName("org.semanticweb.owlapi.apibinding.OWLManager", true, vcl);
            Object manager = owlManagerCls.getMethod("createOWLOntologyManager").invoke(null);
            applySilentImportHandling(manager, vcl);
            Method loadFromStream = findSingleParam(manager.getClass(),
                    "loadOntologyFromOntologyDocument", "java.io.InputStream");
            if (loadFromStream == null) {
                throw new NoSuchMethodException("OWLOntologyManager.loadOntologyFromOntologyDocument(InputStream)");
            }
            Object ontology = loadFromStream.invoke(manager, new ByteArrayInputStream(owlBytes));
            Class<?> ontFace = Class.forName("org.semanticweb.owlapi.model.OWLOntology", true, vcl);
            return fn.apply(vcl, ontology, ontFace);
        } finally {
            Thread.currentThread().setContextClassLoader(prev);
        }
    }

    @FunctionalInterface
    private interface IsolatedFn {
        String apply(ClassLoader vcl, Object ontology, Class<?> ontologyFace) throws Exception;
    }

    /** Best-effort: tolerate unresolved owl:imports (e.g. BFO) so a fragment still verbalizes. */
    private static void applySilentImportHandling(Object manager, ClassLoader vcl) {
        try {
            Class<?> cfgCls = Class.forName("org.semanticweb.owlapi.model.OWLOntologyLoaderConfiguration", true, vcl);
            Object cfg = cfgCls.getDeclaredConstructor().newInstance();
            Class<?> stratCls = Class.forName("org.semanticweb.owlapi.model.MissingImportHandlingStrategy", true, vcl);
            Object silent = stratCls.getMethod("valueOf", String.class).invoke(null, "SILENT");
            cfg = cfgCls.getMethod("setMissingImportHandlingStrategy", stratCls).invoke(cfg, silent);
            Method setCfg = findSingleParam(manager.getClass(), "setOntologyLoaderConfiguration", null);
            if (setCfg != null) {
                setCfg.invoke(manager, cfg);
            }
        } catch (Throwable ignored) {
            // proceed with owlapi defaults
        }
    }

    private static Method findSingleParam(Class<?> cls, String name, String paramTypeName) {
        for (Method m : cls.getMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != 1) {
                continue;
            }
            if (paramTypeName == null || m.getParameterTypes()[0].getName().equals(paramTypeName)) {
                return m;
            }
        }
        return null;
    }

    /** Locate the bundled verbalizer jar under {@code <pluginDir>/lib/} (prefer the self-contained CLI jar). */
    static File findVerbalizerJar() throws FileNotFoundException {
        File dir = ConceptToEnglishPlugin.PLUGIN_DIR;
        File lib = (dir == null) ? null : new File(dir, "lib");
        if (lib != null && lib.isDirectory()) {
            File[] cli = lib.listFiles((d, n) -> n.matches("ontology-to-english-.*-cli\\.jar"));
            if (cli != null && cli.length > 0) {
                return cli[0];
            }
            File[] slim = lib.listFiles((d, n) -> n.matches("ontology-to-english-[0-9].*\\.jar") && !n.contains("sources"));
            if (slim != null && slim.length > 0) {
                return slim[0];
            }
        }
        throw new FileNotFoundException("Verbalizer jar not found under " + lib
                + " (expected ontology-to-english-*-cli.jar). Re-run the plugin's assemblePlugin task.");
    }

    static void openInBrowser(File html) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(html.toURI());
                return;
            }
        } catch (Throwable t) {
            // fall through to logging the path
        }
        info("Open this report in a browser: " + html.getAbsolutePath());
    }

    static String safeName(Package pkg) {
        try {
            String n = pkg.getName();
            return (n == null || n.isEmpty()) ? "model" : n;
        } catch (Throwable t) {
            return "model";
        }
    }

    static String sanitize(String s) {
        return (s == null ? "model" : s.replaceAll("[^a-zA-Z0-9._-]+", "_"));
    }

    static void info(String msg) {
        try {
            Application.getInstance().getGUILog().log("[CM2English] " + msg);
        } catch (Throwable ignored) {
            // ignore
        }
    }

    static void reportError(String msg, Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        try {
            Application.getInstance().getGUILog().log("[CM2English][ERROR] " + msg + "\n" + sw);
        } catch (Throwable ignored) {
            // last resort
        }
    }
}
