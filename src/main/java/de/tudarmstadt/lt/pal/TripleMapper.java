package de.tudarmstadt.lt.pal;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.hp.hpl.jena.ontology.OntResource;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.Resource;

import de.tudarmstadt.lt.pal.SPARQLTriple.Constant;
import de.tudarmstadt.lt.pal.SPARQLTriple.Element;
import de.tudarmstadt.lt.pal.SPARQLTriple.TypeConstraint;
import de.tudarmstadt.lt.pal.SPARQLTriple.Variable;
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
	
	private Collection<ComparablePair<Resource, Float>> mapResource(Element e) {
		List<ComparablePair<Resource, Float>> candidates = null;
		
		if (e instanceof Constant) {
			Constant c = (Constant)e;
			if (c.type == Constant.Type.Mapped) {
				candidates = Collections.singletonList(new ComparablePair<Resource, Float>(kb.getResource(e.name), 1.0f));
			} else {
				// Get scores for 100 resource candidates and choose best N
				int numCandidates = 100;
				int numCandidatesFiltered = 3;
				candidates = kb.getResourceCandidates(e.name, numCandidates);
				if (candidates.size() > numCandidatesFiltered) {
					candidates = candidates.subList(0, numCandidatesFiltered);
				}
			}
		}
		return candidates;
	}
	
	private Collection<ComparablePair<Property, Float>> mapProperty(Element p, Resource subject, Resource object, TypeConstraint subjectTC, TypeConstraint objectTC) {
		Map<String, Float> synonyms = null;
		
		if (p instanceof Constant) {
			Constant c = (Constant)p;
			if (c.type == Constant.Type.Mapped) {
				return Collections.singleton(new ComparablePair<Property, Float>(kb.getProperty(p.name), 1.0f));
			} else {
				String nameLC = p.name.toLowerCase();
				synonyms = new HashMap<>();
				String pos = null;
				if (nameLC.contains("#")) {
					int sepIndex = nameLC.indexOf('#');
					pos = nameLC.substring(sepIndex + 1);
					nameLC = nameLC.substring(0, sepIndex);
					wnc.addSynonyms(synonyms, wnc.getSynonyms(nameLC, pos));
				}
				wnc.addSynonym(synonyms, nameLC, 1.0f);
			}
		}
		
		return kb.getPropertyCandidates(synonyms, subject, object, subjectTC, objectTC);
	}
	
	private TypeConstraint mapVariableType(Variable var) {
		TypeConstraint.BasicType basicType = null;
		String typeURI = null;
		switch (var.type) {
		case Agent:
			basicType = TypeConstraint.BasicType.Resource;
			typeURI = "dbpedia-owl:Person";
			break;
		case Place:
			basicType = TypeConstraint.BasicType.Resource;
			typeURI = "dbpedia-owl:Place";
			break;
		case Date:
			basicType = TypeConstraint.BasicType.Literal;
			typeURI = "xsd:date";
			break;
		case Literal:
			basicType = TypeConstraint.BasicType.Literal;
			typeURI = null;
			break;
		case Unknown:
			Collection<Resource> typeCandidates = kb.getTypeCandidates(var.name);
			if (typeCandidates != null && !typeCandidates.isEmpty()) {
				Resource r = typeCandidates.iterator().next();
				OntResource or = kb.getOntResource(r);
				if (or != null && or.isClass()) {
					basicType = TypeConstraint.BasicType.Resource;
				} else if (or != null && or.isDataRange()) {
					basicType = TypeConstraint.BasicType.Literal;
				}
				typeURI = or.getURI();
			}
		}
		if (basicType != null) {
			return new TypeConstraint(basicType, typeURI);
		}
		return null;
	}
	
	public String buildSPARQLQuery(PseudoQuery pseudoQuery) {
		Map<Variable, TypeConstraint> varTypeConstraints = new HashMap<>();
		for (Variable var : pseudoQuery.vars.values()) {
			varTypeConstraints.put(var, mapVariableType(var));
		}
		
		final float NO_TYPE_CONSTRAINT_PENALTY = 0.1f;
		ComparablePair<String, Float> queryWithConstraints = buildSPARQLQuery(pseudoQuery, varTypeConstraints);
		ComparablePair<String, Float> queryWithoutConstraints = buildSPARQLQuery(pseudoQuery, new HashMap<Variable, TypeConstraint>());
		queryWithoutConstraints.value *= NO_TYPE_CONSTRAINT_PENALTY;
		
		if (queryWithConstraints.value > queryWithoutConstraints.value) {
			return queryWithConstraints.key;
		} else {
			return queryWithoutConstraints.key;
		}
	}
	
	public ComparablePair<String, Float> buildSPARQLQuery(PseudoQuery pseudoQuery, Map<Variable, TypeConstraint> varTypeConstraints) {
		System.out.println("Building sparql query for pseudo query:");
		System.out.println(pseudoQuery);
		String query = "";
		float score = 1.0f;
		
		for (SPARQLTriple t : pseudoQuery.triples) {
			ComparablePair<String, Float> tripleQuery = buildSPARQLTriple(t, varTypeConstraints);
			// Try again with subject and object swapped
			SPARQLTriple tSwapped = new SPARQLTriple(t.object, t.predicate, t.subject);
			ComparablePair<String, Float> _tripleQuery = buildSPARQLTriple(tSwapped, varTypeConstraints);
			if (_tripleQuery.value > tripleQuery.value) {
				tripleQuery = _tripleQuery;
			}
				
			String tripleQueryStr = tripleQuery.key;
			score *= tripleQuery.value;

			// We were unable to map one of the triples -> fail
			if (tripleQueryStr == null) {
				return new ComparablePair<>(null, 0.0f);
			}
			query += tripleQueryStr;
		}

		String finalQuery = "SELECT DISTINCT ";
		for (Variable var : pseudoQuery.vars.values()) {
			finalQuery += "?" + var.name + " ";
			
			TypeConstraint tc = varTypeConstraints.get(var);
			if (tc != null) {
				query += kb.getTypeConstraintSPARQLString(tc, var.name);
			}
		}

		final int RESULT_LIMIT = 1000;
		finalQuery += "WHERE { " + query + " } LIMIT " + RESULT_LIMIT;
		return new ComparablePair<>(finalQuery, score);
	}
	
	ComparablePair<String, Float> buildSPARQLTriple(SPARQLTriple triple, Map<Variable, TypeConstraint> varTypeConstraints) {
		Resource rMax = null;
		Property pMax = null;
		float maxScore = 0;

		Collection<ComparablePair<Resource, Float>> subjectCandidates = mapResource(triple.subject);
		Collection<ComparablePair<Resource, Float>> objectCandidates = mapResource(triple.object);
		
		String tripleQuery = null;
		if (subjectCandidates != null) {
			for (ComparablePair<Resource, Float> scoredSubject : subjectCandidates) {
				Resource subject = scoredSubject.key;
				// Only returns type constraint != null if object is a variable and has been assigned a type constraint
				TypeConstraint objectTC = varTypeConstraints.get(triple.object);

				Collection<ComparablePair<Property, Float>> propCandidates = mapProperty(triple.predicate, subject, null, null, objectTC);
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
				// Only returns type constraint != null if object is a variable and has been assigned a type constraint
				TypeConstraint subjectTC = varTypeConstraints.get(triple.subject);
						
				Collection<ComparablePair<Property, Float>> propCandidates = mapProperty(triple.predicate, null, object, subjectTC, null);
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
		
		return new ComparablePair<>(tripleQuery, maxScore);
	}
}
