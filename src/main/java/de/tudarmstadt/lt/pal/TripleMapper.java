package de.tudarmstadt.lt.pal;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.Resource;

import de.tudarmstadt.lt.pal.util.ComparablePair;
import de.tudarmstadt.lt.pal.wordnet.WordNetConnector;

/**
 * Maps pseudo-query triples to valid DBPedia triples.<br/>
 * Example: <code>[Subject: ?book] [Predicate: "written by"] [Object: "Dan Brown"]</code><br/>
 * Result: <code>[Subject: ?book] [Predicate: dbpedia-owl:author] [Object: dbpedia:Dan_Brown]</code>
 */
public class TripleMapper {
	KnowledgeBaseConnector kb;
	WordNetConnector wnc = new WordNetConnector("/Users/jsimon/No-Backup/wordnet31/dict");
	
	public TripleMapper(KnowledgeBaseConnector kb) {
		this.kb = kb;
	}
	
	private Collection<ComparablePair<Resource, Float>> mapResource(SPARQLTriple.Element e) {
		List<ComparablePair<Resource, Float>> candidates = null;
		
		if (e instanceof SPARQLTriple.Constant) {
			SPARQLTriple.Constant c = (SPARQLTriple.Constant)e;
			if (c.type == SPARQLTriple.ConstantType.MappedConstantType) {
				candidates = Collections.singletonList(new ComparablePair<Resource, Float>(kb.getResource(e.name), 1.0f));
			} else {
				// Get scores for 100 resource candidates and choose best 20
				int numCandidates = 100;
				int numCandidatesFiltered = 20;
				candidates = kb.getResourceCandidates(e.name, numCandidates);
				if (candidates.size() > numCandidatesFiltered) {
					candidates = candidates.subList(0, numCandidatesFiltered);
				}
			}
		}
		return candidates;
	}
	
	private Collection<ComparablePair<Property, Float>> mapProperty(SPARQLTriple.Element p, Resource subject, Resource object) {
		Collection<ComparablePair<Property, Float>> candidates = new LinkedList<ComparablePair<Property, Float>>();
		
		if (p instanceof SPARQLTriple.Constant) {
			SPARQLTriple.Constant c = (SPARQLTriple.Constant)p;
			if (c.type == SPARQLTriple.ConstantType.MappedConstantType) {
				candidates = Collections.singleton(new ComparablePair<Property, Float>(kb.getProperty(p.name), 1.0f));
			} else {
				String nameLC = p.name.toLowerCase();
				Map<String, Float> synonyms = null;
				synonyms = new HashMap<>();
				String pos = null;
				if (nameLC.contains("#")) {
					int sepIndex = nameLC.indexOf('#');
					pos = nameLC.substring(sepIndex + 1);
					nameLC = nameLC.substring(0, sepIndex);
					wnc.addSynonyms(synonyms, wnc.getSynonyms(nameLC, pos));
				}
				wnc.addSynonym(synonyms, nameLC, 1.0f);
				candidates = kb.getPropertyCandidates(synonyms, subject, object);
			}
		}
		return candidates;
	}
	
	public String buildSPARQLQuery(PseudoQuery pseudoQuery) {
		System.out.println("Building sparql query for pseudo query:");
		System.out.println(pseudoQuery);
		String query = "";
		int tripleIndex = 0;

		for (SPARQLTriple.Variable var : pseudoQuery.vars.values()) {
			String mappedType = null;
			if (var.typeName.contains(":")) {
				mappedType = var.typeName;
			} else {
				Collection<Resource> typeCandidates = kb.getTypeCandidates(var.typeName);
				if (typeCandidates != null && !typeCandidates.isEmpty()) {
					mappedType = typeCandidates.iterator().next().toString();
				}
			}
			
			var.typeName = mappedType;
		}
		
		for (SPARQLTriple t : pseudoQuery.triples) {
			tripleIndex++;
			
			String subjectType = t.subject.typeName;
			String objectType = t.object.typeName;
			ComparablePair<String, Float> tripleQuery = buildSPARQLTriple(t, tripleIndex);
			
			// Try again with subject and object swapped
			
			// Remember whether with or without type constraint was better
			String subjectTypeBest = t.subject.typeName;
			String objectTypeBest = t.object.typeName;
			t.subject.typeName = subjectType;
			t.object.typeName = objectType;
			SPARQLTriple.Element oldSubject = t.subject;
			t.subject = t.object;
			t.object = oldSubject;
			// Restore types
			ComparablePair<String, Float> _tripleQuery = buildSPARQLTriple(t, tripleIndex);
			// First try
			if (tripleQuery == null) {
				tripleQuery = _tripleQuery;
			// Second try
			} else {
				// Replace if better
				if (_tripleQuery.value > tripleQuery.value) {
					tripleQuery = _tripleQuery;
				// Restore type constraints if worse
				} else {
					// subject/object are still swapped
					t.object.typeName = subjectTypeBest;
					t.subject.typeName = objectTypeBest;
				}
			}
				
			String tripleQueryStr = tripleQuery.key;

			// We were unable to map one of the triples -> fail
			if (tripleQueryStr == null) {
				return null;
			}
			query += tripleQueryStr;
		}

		String finalQuery = "SELECT DISTINCT ";
		for (SPARQLTriple.Variable var : pseudoQuery.vars.values()) {
			finalQuery += "?" + var.name + " ";
			
			if (var.typeName != null) {
				query += "?" + var.name + " a " + kb.getSPARQLResourceString(var.typeName) + " . ";
			}
		}

		finalQuery += "WHERE { " + query + " }";
		return finalQuery;
	}
	
	ComparablePair<String, Float> buildSPARQLTriple(SPARQLTriple t, int tripleIndex) {
		System.out.println("======== buildSPARQLTriple(SPARQLTriple, int) =========");
		Collection<ComparablePair<Resource, Float>> subjectCandidates = mapResource(t.subject);
		Collection<ComparablePair<Resource, Float>> objectCandidates = mapResource(t.object);
		System.out.println("subject type: " + t.subject.typeName);
		System.out.println("object type: " + t.object.typeName);
		System.out.println("subjectCandidates (" + t.subject + "): " + subjectCandidates);
		System.out.println("objectCandidates (" + t.object + "): " + objectCandidates);
		
		// Used to restore types later if necessary
		String subjectType = t.subject.typeName;
		String objectType = t.object.typeName;
		
		ComparablePair<String, Float> tripleQuery = null;
		// Try once with type constraints and if this doesn't work without type constraints
		// (i.e. 2 times max.)
		for (int tryNo = 0; tryNo < 2; tryNo++) {
			System.out.println("=== try " + tryNo);
			ComparablePair<String, Float> _tripleQuery = buildSPARQLTriple(t, subjectCandidates, objectCandidates);
			if (tryNo == 1) {
				float noTypeConstraintPenalty = 0.1f;
				// Of course there will be at least as well scored results without type constraints,
				// we have to assign a penalty to make these results comparable somehow
				_tripleQuery.value *= noTypeConstraintPenalty;
			}
			System.out.println("=== result: " + _tripleQuery);
			// First try
			if (tryNo == 0) {
				tripleQuery = _tripleQuery;
				
				if (t.subject.typeName != null || t.object.typeName != null) {
					t.subject.typeName = null;
					t.object.typeName = null;
				} else {
					break;
				}
			// Second try, better than first try
			} else if (_tripleQuery.value > tripleQuery.value) {
				tripleQuery = _tripleQuery;
			// Second try, *not* better than first try
			} else {
				// Restore types
				t.subject.typeName = subjectType;
				t.object.typeName = objectType;
			}
		}
		
		return tripleQuery;
	}
	
	ComparablePair<String, Float> buildSPARQLTriple(SPARQLTriple triple,
			                 Collection<ComparablePair<Resource, Float>> subjectCandidates,
			                 Collection<ComparablePair<Resource, Float>> objectCandidates) {
		Resource rMax = null;
		Property pMax = null;
		float maxScore = 0;
		
		String tripleQuery = null;
		if (subjectCandidates != null) {
			for (ComparablePair<Resource, Float> scoredSubject : subjectCandidates) {
				Resource subject = scoredSubject.key;
				Resource objectType = null;
				if (triple.object.typeName != null) {
					objectType = kb.getResource(triple.object.typeName);
				}
						
				Collection<ComparablePair<Property, Float>> propCandidates = mapProperty(triple.predicate, subject, objectType);
				System.out.println("prop candidates: " + propCandidates);
				for (ComparablePair<Property, Float> scoredProp : propCandidates) {
					Property prop = scoredProp.key;
					float comboScore = scoredSubject.value * scoredProp.value;
					if (comboScore > maxScore) {
						rMax = subject;
						pMax = prop;
						maxScore = comboScore;
					}
				}
			}
			if (pMax != null) {
				tripleQuery = kb.getSPARQLResourceString(rMax) + " "
						    + kb.getSPARQLResourceString(pMax) + " "
						    + triple.object + " . ";
			}
		} else if (objectCandidates != null) {
			for (ComparablePair<Resource, Float> scoredObject : objectCandidates) {
				Resource object = scoredObject.key;
				Resource subjectType = null;
				if (triple.subject.typeName != null) {
					subjectType = kb.getResource(triple.subject.typeName);
				}
						
				Collection<ComparablePair<Property, Float>> propCandidates = mapProperty(triple.predicate, subjectType, object);
				System.out.println("prop candidates: " + propCandidates);
				for (ComparablePair<Property, Float> scoredProp : propCandidates) {
					Property prop = scoredProp.key;
					float comboScore = scoredObject.value * scoredProp.value;
					if (comboScore > maxScore) {
						rMax = object;
						pMax = prop;
						maxScore = comboScore;
					}
				}
			}
			if (pMax != null) {
				tripleQuery = triple.subject + " "
						    + kb.getSPARQLResourceString(pMax) + " "
						    + kb.getSPARQLResourceString(rMax) + " . ";
			}
		}
		
		return new ComparablePair<String, Float>(tripleQuery, maxScore);
	}
}
