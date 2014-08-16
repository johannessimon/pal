package de.tudarmstadt.lt.pal;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import org.junit.Test;

import com.hp.hpl.jena.ontology.OntResource;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.Resource;

import de.tudarmstadt.lt.pal.util.ComparablePair;

public class KnowledgeBaseConnectorTest extends TestCase {
	KnowledgeBaseConnector kb = new KnowledgeBaseConnector(/*"/Users/jsimon/No-Backup/dbpedia/data", "http://dbpedia.org/sparql"*/);
//	TripleMapper tripleMapper = new TripleMapper(kb);
	
	@Test
	public void testGetPropertyCandidates() {
		Map<String, Float> nameCandidates = new HashMap<>();
		// maps to dbprop:source, but does not match range constraint
		nameCandidates.put("source", 1.0f);
		// maps to dbpedia-owl:author, and matches range constraint
		nameCandidates.put("author", 1.0f);
		Resource resource = kb.getResource("http://dbpedia.org/resource/Wikipedia");
		OntResource range = kb.getOntResource(kb.getResource("http://dbpedia.org/ontology/Person"));
		Collection<ComparablePair<Property, Float>> propCandidates = kb.getPropertyCandidates(nameCandidates, resource, range);
		assertEquals(2, propCandidates.size());
		ComparablePair<Property, Float> prop1 = new ComparablePair<>(kb.getProperty("http://dbpedia.org/ontology/author"), 1.0f);
		ComparablePair<Property, Float> prop2 = new ComparablePair<>(kb.getProperty("http://dbpedia.org/property/author"), 1.0f);
		assertTrue(propCandidates.contains(prop1));
		assertTrue(propCandidates.contains(prop2));
		
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
