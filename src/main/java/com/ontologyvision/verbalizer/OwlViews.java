// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Daniel Brookshier
package com.ontologyvision.verbalizer;

import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.parameters.Imports;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Convenience renderers for an embedded "OWL view": <b>clickable Manchester-syntax HTML</b> (entity tokens are
 * {@code <a href="iri:<IRI>">} anchors, exactly like the English view) for the whole ontology or for a single
 * entity selected by IRI fragment. Lets a host (the MagicDraw OWL-view pane) show the OWL and have its terms
 * drive the same selection bus as the English pane — without the host doing any owlapi work.
 */
public final class OwlViews {

    private OwlViews() { }

    /** Clickable Manchester HTML for the whole ontology ({@code frag == null}) or for one entity by fragment. */
    public static String manchesterHtml(OWLOntology ont, String frag) {
        VerbalizerOptions opts = VerbalizerOptions.builder()
                .formats(VerbalizerOptions.Format.MANCHESTER)
                .entityLinks(true)
                .rollover(false)
                .build();
        if (frag == null || frag.isEmpty()) {
            return new OntologyVerbalizer().verbalizeOntology(ont, "Manchester", opts);
        }
        Set<OWLEntity> focus = new LinkedHashSet<>();
        for (OWLEntity e : ont.getSignature(Imports.INCLUDED)) {
            if (frag.equals(e.getIRI().getShortForm()) || frag.equals(e.getIRI().getFragment())) {
                focus.add(e);
            }
        }
        return new OntologyVerbalizer().verbalizeEntities(ont, focus, "Manchester — " + frag, opts);
    }
}
