package de.tudarmstadt.lt.pal;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

import org.junit.Test;

import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.Resource;

import de.tudarmstadt.lt.pal.SPARQLTriple.TypeConstraint;
import de.tudarmstadt.lt.pal.util.ComparablePair;

public class KnowledgeBaseConnectorTest extends TestCase {
	KnowledgeBaseConnector kb = new KnowledgeBaseConnector(/*"/Users/jsimon/No-Backup/dbpedia/data", "http://dbpedia.org/sparql"*/);
//	TripleMapper tripleMapper = new TripleMapper(kb);
	
	@Test
	public void testGetPropertyCandidates() {
		List<ComparablePair<String, Float>> nameCandidates = new LinkedList<>();
		// maps to dbprop:source, but does not match range constraint
		nameCandidates.add(new ComparablePair<>("source", 1.0f));
		// maps to dbpedia-owl:author, and matches range constraint
		nameCandidates.add(new ComparablePair<>("author", 1.0f));
		Resource resource = kb.getResource("http://dbpedia.org/resource/Wikipedia");
		TypeConstraint range = new TypeConstraint(TypeConstraint.BasicType.Resource, "http://dbpedia.org/ontology/Person");
		Collection<ComparablePair<Property, Float>> propCandidates = kb.getPropertyCandidates(nameCandidates, resource, null, null, range);
		assertEquals(2, propCandidates.size());
		Set<Property> expected = new HashSet<>();
		expected.add(kb.getProperty("http://dbpedia.org/ontology/author"));
		expected.add(kb.getProperty("http://dbpedia.org/property/author"));
		Set<Property> actual = new HashSet<>();
		Iterator<ComparablePair<Property, Float>> it = propCandidates.iterator();
		actual.add(it.next().key);
		actual.add(it.next().key);
		assertEquals(expected, actual);
		
		System.out.println(propCandidates);
	}

	@Test
	public void testGetResourceCandidates() {
		List<ComparablePair<Resource, Float>> candidates = kb.getResourceCandidates("Dan Brown", 10);
		System.out.println(candidates);
		ComparablePair<Property, Float> r1 = new ComparablePair<>(kb.getProperty("http://dbpedia.org/resource/Dan_Brown"), 1.0f);
		assertTrue(candidates.contains(r1));
	}
	
	/*
	@Test
	public void testCheckTypeConstraint() {
		OntResource superType = kb.getOntResource(kb.getResource("http://dbpedia.org/ontology/Agent"));
		OntResource subType = kb.getOntResource(kb.getResource("http://dbpedia.org/ontology/Person"));
		assertTrue(kb.checkTypeConstraint(null, null));
		assertFalse(kb.checkTypeConstraint(superType, null));
		assertTrue(kb.checkTypeConstraint(null, subType));
		assertTrue(kb.checkTypeConstraint(superType, subType));
	}*/
}
