// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Daniel Brookshier
package com.ontologyvision.verbalizer;

import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.StreamDocumentSource;
import org.semanticweb.owlapi.model.MissingImportHandlingStrategy;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyLoaderConfiguration;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import java.io.InputStream;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Headless tests for {@link OntologySerializer} (OWL API only). */
public class OntologySerializerTest {

    private OWLOntology load(final String resource) throws Exception {
        final OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        final OWLOntologyLoaderConfiguration cfg = new OWLOntologyLoaderConfiguration()
                .setMissingImportHandlingStrategy(MissingImportHandlingStrategy.SILENT);
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            assertNotNull("missing fixture " + resource, in);
            return m.loadOntologyFromOntologyDocument(new StreamDocumentSource(in), cfg);
        }
    }

    @Test
    public void wholeOntologyTurtleAndManchester() throws Exception {
        final OWLOntology ont = load("/pizza.owl");

        final String ttl = OntologySerializer.toTurtle(ont);
        assertTrue("turtle has prefixes", ttl.contains("@prefix") || ttl.contains("PREFIX") || ttl.contains("@base"));
        assertTrue("turtle mentions Pizza", ttl.contains("Pizza"));

        final String man = OntologySerializer.toManchester(ont);
        assertTrue("manchester frames present", man.contains("Class:") || man.contains("Ontology:"));
        assertTrue("manchester mentions Pizza", man.contains("Pizza"));
    }

    @Test
    public void perEntitySliceIsASubset() throws Exception {
        final OWLOntology ont = load("/pizza.owl");
        final OWLClass cls = ont.getClassesInSignature().stream()
                .filter(c -> c.getIRI().toString().contains("pizza"))
                .findFirst()
                .orElse(ont.getClassesInSignature().iterator().next());

        final String slice = OntologySerializer.entityToManchester(ont, cls.getIRI().toString());
        assertTrue("slice mentions the entity short name", slice.contains(cls.getIRI().getShortForm()));

        final String whole = OntologySerializer.toManchester(ont);
        assertTrue("a one-entity slice is smaller than the whole ontology", slice.length() < whole.length());
    }
}
