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

        File tmp = new File(System.getProperty("java.io.tmpdir"));
        File htmlOut = new File(tmp, "cm2english-" + sanitize(name) + ".html");
        Files.write(htmlOut.toPath(), html.getBytes(StandardCharsets.UTF_8));
        // Also drop the raw OWL beside it, for inspection / reuse.
        Files.write(new File(tmp, "cm2english-" + sanitize(name) + ".owl").toPath(), owl);
        return htmlOut;
    }

    /** Concept Modeler export — typed against conceptmodeler_api.jar. */
    static byte[] exportOwl(Package pkg) {
        CM2OWL exporter = ConceptModelOWLTransformFactory.createOWLExporter();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        exporter.exportFromModel(pkg, baos);
        return baos.toByteArray();
    }

    /**
     * Verbalize OWL bytes to a self-contained HTML report using the bundled verbalizer jar, loaded in an
     * isolated classloader. Mirrors the validated probe exactly.
     */
    static String verbalize(byte[] owlBytes, String title) throws Exception {
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
            Method verbalizeOntology = verbalizerCls.getMethod("verbalizeOntology", ontologyFace, String.class);
            return (String) verbalizeOntology.invoke(verbalizer, ontology, title);
        } finally {
            Thread.currentThread().setContextClassLoader(previousContext);
        }
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
