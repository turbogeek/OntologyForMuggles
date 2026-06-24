// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Daniel Brookshier
package com.ontologyvision.verbalizer;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.ManchesterSyntaxDocumentFormat;
import org.semanticweb.owlapi.formats.TurtleDocumentFormat;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLDocumentFormat;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.parameters.Imports;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Serializes an OWL ontology — or just the axioms that mention one entity — to <b>Turtle</b> or
 * <b>Manchester</b> syntax as a {@code String}. This is the "see the OWL" data source for an embedding host
 * (a MagicDraw OWL-view pane, or the OWL section of the interactive HTML / Markdown exports). OWL API only;
 * no MagicDraw, no host types — String in, String out.
 */
public final class OntologySerializer {

    private OntologySerializer() { }

    /** The whole ontology in Turtle. */
    public static String toTurtle(OWLOntology ont) {
        return serialize(ont, new TurtleDocumentFormat());
    }

    /** The whole ontology in Manchester syntax. */
    public static String toManchester(OWLOntology ont) {
        return serialize(ont, new ManchesterSyntaxDocumentFormat());
    }

    /** Just the axioms referencing the entity at {@code iri}, in Turtle. */
    public static String entityToTurtle(OWLOntology ont, String iri) {
        return entitySlice(ont, iri, false);
    }

    /** Just the axioms referencing the entity at {@code iri}, in Manchester syntax. */
    public static String entityToManchester(OWLOntology ont, String iri) {
        return entitySlice(ont, iri, true);
    }

    private static String serialize(OWLOntology ont, OWLDocumentFormat fmt) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ont.getOWLOntologyManager().saveOntology(ont, fmt, out);
            return out.toString(StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return "# serialization failed: " + t;
        }
    }

    /**
     * Build a tiny ontology from the axioms that reference the entity at {@code iriStr} and serialize it. The
     * slice gets its own manager which is discarded after, so it never leaks into the source ontology's manager.
     */
    private static String entitySlice(OWLOntology ont, String iriStr, boolean manchester) {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLOntology slice = null;
        try {
            IRI iri = IRI.create(iriStr);
            Set<OWLAxiom> axioms = new LinkedHashSet<>();
            for (OWLEntity e : ont.getEntitiesInSignature(iri)) {
                axioms.addAll(ont.getReferencingAxioms(e, Imports.INCLUDED));
            }
            slice = m.createOntology(axioms);
            return serialize(slice, manchester ? new ManchesterSyntaxDocumentFormat() : new TurtleDocumentFormat());
        } catch (Throwable t) {
            return "# entity serialization failed: " + t;
        } finally {
            if (slice != null) {
                try { m.removeOntology(slice); } catch (Throwable ignored) { }
            }
        }
    }
}
