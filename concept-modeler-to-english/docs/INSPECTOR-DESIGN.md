# CM2English — Multi-View Linked Inspector + Interactive Exports (design)

Source: multi-agent research pass (diagram-nav, SVG export, VOM Phase-20, OWL view, exports → synthesis),
APIs verified by `javap` against `E:\Magic SW\MSoS26xHF1\lib\*` (MD 2026x) and the bundled OpenAPI examples.

## 0. Thesis
**One IRI is the join key for everything.** Every view publishes/consumes the same string
`OWLEntity.getIRI().toString()`. MagicDraw's own `SelectionProvider` is the in-tool bus; we only build the
thin **IRI ↔ MD `Element`** translation at the two JEditorPane seams.

## 1. Architecture
- **In-tool bus = `com.nomagic.magicdraw.ui.SelectionProvider`** (project-global; already aggregates the
  Containment tree + active diagram → one event). Copy VOM's `VomSelectionBus` → `SelectionBus`
  (`current(Project)`, `subscribe(Project, Consumer<List<Element>>)`), `com.nomagic.*` + JDK only.
- **JEditorPanes are not MD selection sources/sinks**, so:
  - **Pane → bus (click):** `HyperlinkListener.getDescription()` → raw `iri:<IRI>` (NOT `getURL()`, null for the
    custom scheme) → resolve IRI→`Element` → `tree.openNode(el, true)` (selects tree **and** active diagram) →
    re-publishes via `SelectionProvider` → re-targets the other panes. No pane-to-pane wiring.
  - **Bus → pane (echo):** subscribe; on `Element`, map Element→IRI, `JEditorPane.scrollToReference(anchor)` +
    toggle a `.hl` class on the matching `<a id=…>`.
- **Diagram is asymmetric:** `openNode(el,true)` only touches the *active* diagram and doesn't center. The
  richer "open the element's own diagram + center" (requirement 1) uses an explicit `DiagramNavigator.navigateTo()`.
- **owlapi stays isolated** (parent=null URLClassLoader). Only `byte[]`/`String` cross the boundary; all
  Manchester/Turtle rendering happens inside the jar and returns `String`.

## 2. Exact MagicDraw APIs
**Find diagrams containing an element**
`Project.getSymbolElementMap().getAllPresentationElements(Element) : List<PresentationElement>`;
`PresentationElement.getAbstractDiagramPresentationElement()` → dedupe owning diagrams.
Lazy-load trap: `dpe.ensureLoaded()` then re-query (symbols of never-opened diagrams may be absent).

**Open / center / select**
`AbstractDiagramPresentationElement.ensureLoaded()/open()`; `Project.getDiagram(Diagram):DiagramPresentationElement`;
`PresentationElement.setSelected(List)`, `getBounds()`(throws `NoRectangleDefinedException` for paths)/`getMiddlePoint()`;
`DiagramSurface.getDiagramSurface(pe).scrollRectToVisible(rect)/scrollToVisible(x,y)`.
**No public center primitive** → enlarged-rect-around-midpoint heuristic. 0-diagram → tree reveal only.
All Swing → `SwingUtilities.invokeLater`.

**Diagram → SVG/PNG**
`com.nomagic.magicdraw.export.image.ImageExporter.export(AbstractDiagramPresentationElement, ImageExporter.SVG, File)`
(example: `openapi/examples/imagegenerator/...ExportDiagramImages.java`).
**SVG element identity:** none by default (`<g class="element">`). Inject via the internal
`SVGExportEnricherFactory` (`com.dassault_systemes.*`) + a custom enricher returning `{data-iri, data-eid}`;
**MANDATORY `removeEnricher` in `finally`** (process-global singleton). `Project.getElementByID(String)` maps
`id`/`data-eid` back to an element. Reflection-guarded with a self-test (export one diagram, assert `id=` present);
fallback = zip the draw-ordered `<g class="element">` groups to the ordered PE list and inject `data-iri`.

## 3. Verbalizer (OntologyForMuggles) vs Plugin
**Verbalizer-only — 0 Cameo restarts (jar swap + headless JUnit):**
1. `OntologySerializer` — whole-ontology + per-entity Turtle/Manchester (per-entity via `getReferencingAxioms`;
   `removeOntology(slice)` to avoid manager leak).
2. Fragment emitter — body-only HTML + expose `sbvrCss()`/`script()` so a bundler merges one `<style>`.
3. Entity-heading anchors `id="gl-<shortForm>"` (glossary already has them) for `scrollToReference`.
4. Markdown output mode — `VerbalizerOptions.OutputMode {HTML, MARKDOWN}`; the token methods emit
   `**bold**`/`*italic*`/`` `code` `` and `## headings`/`- bullets`; reuses 100% of sentence generation.
5. (Already present) `entityLinks(true)` → `href="iri:<IRI>"`; `Format.MANCHESTER`.

**Plugin-only — needs restart per phase:** `SelectionBus`; bridge `verbalize(spec)`/`serializeOwl`/`serializeOwlEntity`;
`DiagramNavigator.navigateTo`; `ConceptNavigator`→`openNode(el,true)`+`navigateTo`; OWL dockable window (tabbed
JEditorPanes subscribing to the bus); `DiagramSvgExporter`; `BundleHtmlComposer` + `MarkdownComposer`.

## 4. Phased plan (≈3 restarts; verbalizer phases are free)
- **P1 — Diagram nav on term click** (plugin · 1 restart · LOW). `DiagramNavigator.navigateTo()` + many-diagram
  chooser; `ConceptNavigator` click also navigates. Folds in the still-untested Phase-B nav. *First user-visible win;
  exercises the IRI→Element→diagram spine everything reuses.*
- **P2 — OWL serialization in the verbalizer** (verbalizer · 0 restarts · LOW). Headless JUnit.
- **P3 — OWL view window + full bidirectional linking** (plugin · 1 restart · MEDIUM, riskiest). `SelectionBus`,
  tabbed OWL dock, pane subscribe + `.hl`/`scrollToReference`. Guard bus feedback ("ignore my own just-published IRI").
- **P4 — Interactive HTML export** (plugin · 1 restart · MEDIUM). `DiagramSvgExporter` (enricher + self-test),
  `BundleHtmlComposer` (merged style + English fragment + IRI-spanned OWL `<pre>` + inline `<svg>` + tiny vanilla-JS
  `iri:` linker). Browser-testable via Playwright MCP.
- **P5 — Markdown** (P5a verbalizer 0-restart; P5b plugin `MarkdownComposer`, rides P4's restart).

## 5. Top risks
1. `SVGExportEnricherFactory` is internal → reflection-guarded + `id=` self-test + draw-order fallback.
2. No public centering → enlarged-rect heuristic; path symbols need the `getMiddlePoint` fallback.
3. Lazy `SymbolElementMap` → `ensureLoaded()` + re-query.
4. **IRI string divergence** — `ConceptNavigator`'s name-stripped fragment vs `OWLEntity.getIRI()`; verify both
   ends emit the identical string for one element on first run (highest-leverage correctness check).
5. Bus feedback loops in P3 → the "ignore own just-published IRI within N ms" guard.

## 6. Decisions — see the conversation; recommended defaults:
OWL view = Manchester+Turtle tabs · dock scope = per-element (export = whole ontology) · many diagrams =
active-else-chooser · 0 diagrams = tree-reveal + status note · HTML SVG = inline (single file) · Markdown images =
SVG+PNG · entry = context-menu action + REST harness · SVG enrichment = per-export (isolated).
