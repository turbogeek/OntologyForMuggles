# Concept Modeler to English

A MagicDraw / Cameo plugin that adds **"Verbalize to English…"** to the Containment-tree right-click
menu for **Cameo Concept Modeler** packages. It turns a concept model into a readable, colored,
hyperlinked **SBVR Structured-English** HTML report.

## How it works

```
Right-click a concept-model package  ─▶  Concept Modeling ▸ Verbalize to English…
        │
        ▼  CM2OWL.exportFromModel(Package, OutputStream)         (Cameo Concept Modeler API)
   OWL bytes (RDF/XML)
        │
        ▼  OntologyVerbalizer.verbalizeOntology(ont, title)      (OntologyForMuggles, isolated classloader)
   self-contained English HTML  ─▶  opened in your browser
```

The verbalizer (`com.ontologyvision:ontology-to-english`) ships as a **self-contained CLI jar** in
`lib/` and is loaded at runtime in a `parent=null` classloader, so its owlapi **5.5.1** never collides
with the Concept Modeler's owlapi **5.1.19** — the two only ever exchange a `byte[]`.

This pipeline was validated end-to-end before the plugin was written (see the Phase-0 probe at
`TutorialForCatiaMagicApiMCP/scripts/conceptmodeler/CCMToEnglishProbe.groovy`).

## Prerequisites

- **MagicDraw / Cameo 2026x** with the **Concept Modeler** plugin installed (it provides
  `com.nomagic.conceptmodeler.transform.CM2OWL`).
- **JDK 21** to build (e.g. Semeru). The MagicDraw libraries are Java-21 bytecode; output targets Java 17.
- The **OntologyForMuggles** project (this plugin lives in a subfolder of it) built once to produce the verbalizer jar:
  ```
  cd ..
  .\gradlew.bat jar cliJar
  ```

## Build & install

```bat
:: compile-check only
gradlew.bat compileJava  -PmsosaHome="E:\Magic SW\MSoS26xHF1"

:: stage an installable plugin folder under build\plugin\com.ontologyvision.cm2english\
gradlew.bat assemblePlugin -PmsosaHome="E:\Magic SW\MSoS26xHF1"
:: (verbalizerLibsDir defaults to ..\build\libs — the parent OntologyForMuggles build output)
```

Then copy `build\plugin\com.ontologyvision.cm2english\` into `<MagicDraw>\plugins\` and **restart
MagicDraw**.

## Use

1. Open a Concept Modeling project.
2. In the Containment tree, **right-click an ontology package** (one that carries an ontology URI — not
   the root model or a plain UML package).
3. **Concept Modeling ▸ Verbalize to English…** — the SBVR English report opens in your browser. A copy
   of the report (`cm2english-<name>.html`) and the raw OWL (`cm2english-<name>.owl`) are written to your
   temp directory.

## Layout

| Path | Role |
|---|---|
| `descriptor/plugin.xml` | MagicDraw plugin descriptor (version stamped at assemble time) |
| `…/ConceptToEnglishPlugin.java` | entry point; registers the context-menu configurator |
| `…/VerbalizeBrowserConfigurator.java` | adds the action when a package is right-clicked |
| `…/Action_VerbalizeConceptModel.java` | the menu action |
| `…/ConceptModelEnglishBridge.java` | export (CCM) → verbalize (isolated classloader) → HTML |

## License

MIT, © 2026.
