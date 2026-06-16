// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Daniel Brookshier
package com.ontologyvision.verbalizer;

import org.junit.Test;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.StreamDocumentSource;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.MissingImportHandlingStrategy;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyLoaderConfiguration;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;

import java.io.InputStream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests the reasoner-backed {@link Validator} with ELK (Apache-2.0, OWL 2 EL). ELK is a {@code testImplementation}
 * dependency only — the published library stays reasoner-free (the CLI bundles ELK separately for {@code --validate}).
 */
public class ValidatorTest
{
    /** The concrete reasoner used by the CLI's {@code --validate}; loaded directly here since it's on the test path. */
    private final OWLReasonerFactory elk = new ElkReasonerFactory();

    @Test
    public void consistentOntologyHasNoUnsatisfiableClasses () throws Exception
    {
        final OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        final OWLOntologyLoaderConfiguration cfg = new OWLOntologyLoaderConfiguration()
                .setMissingImportHandlingStrategy( MissingImportHandlingStrategy.SILENT );
        try ( InputStream in = getClass().getResourceAsStream( "/test.owl" ) )
        {
            assertNotNull( "missing test fixture /test.owl", in );
            final OWLOntology ont = m.loadOntologyFromOntologyDocument( new StreamDocumentSource( in ), cfg );
            final Validator.Result r = new Validator().validate( ont, elk );
            assertTrue( "test.owl should be consistent", r.consistent );
            assertTrue( "no unsatisfiable classes expected", r.unsatisfiable.isEmpty() );
            assertTrue( "overall ok", r.ok() );
        }
    }

    /**
     * A class with two disjoint superclasses can never have an instance. The ontology stays globally consistent
     * (it has no individuals), but the reasoner must flag the class as unsatisfiable — the exact "contradiction
     * and other issues" signal the validation pillar exists to surface.
     */
    @Test
    public void detectsUnsatisfiableClass () throws Exception
    {
        final OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        final OWLDataFactory df = m.getOWLDataFactory();
        final OWLOntology ont = m.createOntology( IRI.create( "urn:test:unsat" ) );

        final String ns = "urn:test:unsat#";
        final OWLClass a = df.getOWLClass( IRI.create( ns + "A" ) );
        final OWLClass b = df.getOWLClass( IRI.create( ns + "B" ) );
        final OWLClass c = df.getOWLClass( IRI.create( ns + "C" ) );

        m.addAxiom( ont, df.getOWLSubClassOfAxiom( a, b ) );
        m.addAxiom( ont, df.getOWLSubClassOfAxiom( a, c ) );
        m.addAxiom( ont, df.getOWLDisjointClassesAxiom( b, c ) );

        final Validator.Result r = new Validator().validate( ont, elk );
        assertTrue( "globally consistent — A simply can't be instantiated", r.consistent );
        assertFalse( "A makes the ontology not-ok", r.ok() );
        assertTrue( "A flagged unsatisfiable",
                    r.unsatisfiable.stream().anyMatch( x -> x.getIRI().equals( a.getIRI() ) ) );
    }
}
