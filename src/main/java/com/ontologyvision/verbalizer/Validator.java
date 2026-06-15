// SPDX-License-Identifier: MIT
// Copyright (c) 2026 Daniel Brookshier
package com.ontologyvision.verbalizer;

import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Optional reasoner-based validation — the first layer of the "REASON &amp; VALIDATE" pillar. Runs an OWL 2
 * reasoner to check <b>consistency</b> and find <b>unsatisfiable classes</b> (classes that can have no instance
 * without contradiction — the reasoner's way of surfacing "contradictions and other issues").
 *
 * <p><b>Dependency-light by design:</b> this class uses only the OWL API's {@link OWLReasoner} /
 * {@link OWLReasonerFactory} interfaces — the caller supplies the engine (ELK = Apache-2.0, HermiT = LGPL, …),
 * so the core library never hard-depends on a reasoner. The CLI bundles ELK; embedders pass their own factory.
 */
public final class Validator
{
    /** Outcome of a validation run. */
    public static final class Result
    {
        /** Whether the ontology is logically consistent (no global contradiction). */
        public final boolean consistent;
        /** Classes equivalent to {@code owl:Nothing} (unsatisfiable), excluding {@code owl:Nothing} itself. */
        public final List<OWLClass> unsatisfiable;

        Result ( final boolean consistent, final List<OWLClass> unsatisfiable )
        {
            this.consistent = consistent;
            this.unsatisfiable = unsatisfiable;
        }

        /** True iff consistent and nothing is unsatisfiable. */
        public boolean ok () { return consistent && unsatisfiable.isEmpty(); }
    }

    /**
     * Validate an ontology with the given reasoner.
     * @param ont     the ontology to check
     * @param factory the reasoner factory (e.g. ELK's {@code ElkReasonerFactory})
     * @return the consistency flag + the (sorted) unsatisfiable classes
     */
    public Result validate ( final OWLOntology ont, final OWLReasonerFactory factory )
    {
        final OWLReasoner reasoner = factory.createReasoner( ont );
        try
        {
            final boolean consistent = reasoner.isConsistent();
            final List<OWLClass> unsat = new ArrayList<>();
            if ( consistent )
            {
                for ( final OWLClass c : reasoner.getUnsatisfiableClasses().getEntitiesMinusBottom() ) unsat.add( c );
                unsat.sort( Comparator.comparing( c -> c.getIRI().toString() ) );
            }
            return new Result( consistent, unsat );
        }
        finally
        {
            reasoner.dispose();
        }
    }
}
