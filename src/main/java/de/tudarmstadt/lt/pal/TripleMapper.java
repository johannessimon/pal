package de.tudarmstadt.lt.pal;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.Resource;

import de.tudarmstadt.lt.pal.util.ComparablePair;

/**
 * Maps pseudo-query triples to valid DBPedia triples.<br/>
 * Example: <code>[Subject: ?book] [Predicate: "written by"] [Object: "Dan Brown"]</code><br/>
 * Result: <code>[Subject: ?book] [Predicate: dbpedia-owl:author] [Object: dbpedia:Dan_Brown]</code>
 */
public class TripleMapper {
	KnowledgeBaseConnector kb;
	
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
				// Get scores for 100 resource candidates and choose best 10
				int numCandidates = 100;
				int numCandidatesFiltered = 10;
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
			// Wildcard (something like "*")
			if (c.name.equals("[]")) {
				return null;
			}
			if (c.type == SPARQLTriple.ConstantType.MappedConstantType) {
				candidates = Collections.singleton(new ComparablePair<Property, Float>(kb.getProperty(p.name), 1.0f));
			} else {
				candidates = kb.getPropertyCandidates(p.name, subject, object);
			}
		}
		return candidates;
	}
	
	private Collection<ComparablePair<Property, Float>> mapProperty(SPARQLTriple triple) {
		SPARQLTriple.Element p = triple.predicate;
		Collection<ComparablePair<Property, Float>> candidates = new LinkedList<ComparablePair<Property, Float>>();
		
		if (p instanceof SPARQLTriple.Constant) {
			SPARQLTriple.Constant c = (SPARQLTriple.Constant)p;
			// Wildcard (something like "*")
			if (c.name.equals("[]")) {
				return null;
			}
			if (c.type == SPARQLTriple.ConstantType.MappedConstantType) {
				candidates = Collections.singleton(new ComparablePair<Property, Float>(kb.getProperty(p.name), 1.0f));
			} else {
				candidates = kb.getPropertyCandidates(p.name, triple.subject.type, triple.object.type);
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
			if (var.type.contains(":")) {
				mappedType = var.type;
			} else {
				Collection<Resource> typeCandidates = kb.getTypeCandidates(var.type);
				if (typeCandidates != null && !typeCandidates.isEmpty()) {
					mappedType = typeCandidates.iterator().next().toString();
				}
			}
			
			var.type = mappedType;
		}
		
		for (SPARQLTriple t : pseudoQuery.triples) {
			tripleIndex++;
			
			String tripleQuery = buildSPARQLTriple(t, tripleIndex);
			// Try again with subject and object swapped
			if (tripleQuery == null) {
				SPARQLTriple.Element oldSubject = t.subject;
				t.subject = t.object;
				t.object = oldSubject;
				tripleQuery = buildSPARQLTriple(t, tripleIndex);
			}

			// We were unable to map one of the triples -> fail
			if (tripleQuery == null) {
				return null;
			}
			query += tripleQuery;
		}

		String finalQuery = "SELECT DISTINCT ";
		for (SPARQLTriple.Variable var : pseudoQuery.vars.values()) {
			finalQuery += "?" + var.name + " ";
			
			if (var.type != null) {
				query += "?" + var.name + " rdf:type <" + var.type + "> . ";
			}
		}

		finalQuery += "WHERE { " + query + " }";
		return finalQuery;
	}
	
	String buildSPARQLTriple(SPARQLTriple t, int tripleIndex) {
		// wildcard predicate (unbound predicate variable)
		String wcPredicate = "?p" + tripleIndex;
		
		Collection<ComparablePair<Resource, Float>> subjectCandidates = mapResource(t.subject);
		Collection<ComparablePair<Resource, Float>> objectCandidates = mapResource(t.object);
//		Collection<ComparablePair<Property, Float>> propCandidates = mapProperty(t);
		
		System.out.println("subjectCandidates (" + t.subject + "): " + subjectCandidates);
		System.out.println("objectCandidates (" + t.object + "): " + objectCandidates);
//		System.out.println("propertyCandidates (" + t.predicate + "): " + propCandidates);
		
		String tripleQuery = "";
		// Try once with type constraints and if this doesn't work without type constraints
		// (i.e. 2 times max.)
		for (int tryNo = 0; tryNo < 2; tryNo++) {
			tripleQuery = buildSPARQLTriple(t, subjectCandidates, objectCandidates, wcPredicate);
			if (tripleQuery != null) {
				break;
			} else if (t.subject.type != null || t.object.type != null) {
				t.subject.type = null;
				t.object.type = null;
			} else {
				break;
			}
		}
		return tripleQuery;
	}
	
	String buildSPARQLTriple(SPARQLTriple triple,
			                 Collection<ComparablePair<Resource, Float>> subjectCandidates,
			                 Collection<ComparablePair<Resource, Float>> objectCandidates,
//			                 Collection<ComparablePair<Property, Float>> propCandidates,
			                 String wcPredicate) {
		Resource rMax = null;
		Property pMax = null;
		float maxScore = 0;
		
		boolean propertyNeedsMap = !triple.predicate.name.equals("[]");
		
		String tripleQuery = null;
		if (subjectCandidates != null && propertyNeedsMap) {
			for (ComparablePair<Resource, Float> scoredSubject : subjectCandidates) {
				Resource subject = scoredSubject.key;
				Resource objectType = null;
				if (triple.object.type != null) {
					objectType = kb.getResource(triple.object.type);
				}
						
				Collection<ComparablePair<Property, Float>> propCandidates = mapProperty(triple.predicate, subject, objectType);
				for (ComparablePair<Property, Float> scoredProp : propCandidates) {
					Property prop = scoredProp.key;
					int score = kb.getSubjectPropertyScore(subject, prop, triple.object.type);
					if (score > 0) {
						float comboScore = scoredSubject.value * scoredProp.value;
						if (comboScore > maxScore) {
							rMax = subject;
							pMax = prop;
							maxScore = comboScore;
						}
					}
				}
			}
			if (pMax != null) {
				tripleQuery = "<" + rMax + "> <" + pMax + "> " + triple.object + " . ";
			}
		} else if (propertyNeedsMap && objectCandidates != null) {
			for (ComparablePair<Resource, Float> scoredObject : objectCandidates) {
				Resource object = scoredObject.key;
				Resource subjectType = null;
				if (triple.subject.type != null) {
					subjectType = kb.getResource(triple.subject.type);
				}
						
				Collection<ComparablePair<Property, Float>> propCandidates = mapProperty(triple.predicate, subjectType, object);
				for (ComparablePair<Property, Float> scoredProp : propCandidates) {
					Property prop = scoredProp.key;
					int score = kb.getPropertyObjectScore(prop, object, triple.subject.type);
					if (score > 0) {
						float comboScore = scoredObject.value * scoredProp.value;
						if (comboScore > maxScore) {
							rMax = object;
							pMax = prop;
							maxScore = comboScore;
						}
					}
				}
			}
			if (pMax != null) {
				tripleQuery = triple.subject + " <" + pMax + "> <" + rMax + "> . ";
			}
		} else if (subjectCandidates != null && !subjectCandidates.isEmpty()) {
			rMax = subjectCandidates.iterator().next().key;
			tripleQuery = "<" + rMax + "> " + wcPredicate + " " + triple.object + " . ";
		} else if (objectCandidates != null && !objectCandidates.isEmpty()) {
			rMax = objectCandidates.iterator().next().key;
			tripleQuery = triple.subject + " " + wcPredicate + " <" + rMax + "> . ";
		}
		return tripleQuery;
	}
}
