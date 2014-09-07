package de.tudarmstadt.lt.pal;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

import org.junit.Test;

import com.hp.hpl.jena.ontology.OntProperty;

import de.tudarmstadt.lt.pal.Triple.TypeConstraint;
import de.tudarmstadt.lt.pal.util.ComparablePair;

public class KnowledgeBaseConnectorTest extends TestCase {
	KnowledgeBaseConnector kb = new KnowledgeBaseConnector(/*"/Users/jsimon/No-Backup/dbpedia/data", "http://dbpedia.org/sparql"*/);
//	TripleMapper tripleMapper = new TripleMapper(kb);
	
	@Test
	public void testGetPropertyCandidates() {
		List<ComparablePair<MappedString, Float>> nameCandidates = new LinkedList<>();
		// maps to dbprop:source, but does not match range constraint
		nameCandidates.add(new ComparablePair<>(new MappedString("source"), 1.0f));
		// maps to dbpedia-owl:author, and matches range constraint
		nameCandidates.add(new ComparablePair<>(new MappedString("author"), 1.0f));
		String resource = "http://dbpedia.org/resource/Wikipedia";
		TypeConstraint range = new TypeConstraint(TypeConstraint.BasicType.Resource, "http://dbpedia.org/ontology/Person");
		Collection<ComparablePair<MappedString, Float>> propCandidates = kb.getPropertyCandidates(nameCandidates, resource, null, null, range);
		assertEquals(2, propCandidates.size());
		Set<String> expected = new HashSet<>();
		expected.add("http://dbpedia.org/ontology/author");
		expected.add("http://dbpedia.org/property/author");
		Set<String> actual = new HashSet<>();
		Iterator<ComparablePair<MappedString, Float>> it = propCandidates.iterator();
		actual.add(it.next().key.word);
		actual.add(it.next().key.word);
		assertEquals(expected, actual);
		
		System.out.println(propCandidates);
	}

	@Test
	public void testGetOntProperty() {
		OntProperty p = kb.getOntProperty("http://dbpedia.org/ontology/author");
		assertTrue(p != null);
	}

	@Test
	public void testGetResourceCandidates() {
		List<ComparablePair<MappedString, Float>> candidates = kb.getResourceCandidates("Dan Brown", 10);
		System.out.println(candidates);
		ComparablePair<MappedString, Float> r1 = new ComparablePair<>(new MappedString("http://dbpedia.org/resource/Dan_Brown"), 1.0f);
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
