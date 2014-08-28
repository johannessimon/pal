package de.tudarmstadt.lt.pal;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

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
		Map<String, Float> synonyms = new HashMap<>();
		
		if (p instanceof Constant) {
			Constant c = (Constant)p;
			if (c.type == Constant.Type.Mapped) {
				return Collections.singleton(new ComparablePair<Property, Float>(kb.getProperty(p.name), 1.0f));
			} else {
				String nameLC = p.name.toLowerCase();
				String pos = null;
				if (nameLC.contains("#")) {
					int sepIndex = nameLC.indexOf('#');
					pos = nameLC.substring(sepIndex + 1);
					nameLC = nameLC.substring(0, sepIndex);
					wnc.addSynonyms(synonyms, wnc.getRelatedWords(nameLC, pos));
				}
				wnc.addSynonym(synonyms, nameLC, 1.0f);
			}
		}
		
		List<ComparablePair<String, Float>> nameCandidates = new LinkedList<>();
		for (Entry<String, Float> entry : synonyms.entrySet()) {
			nameCandidates.add(new ComparablePair<>(entry.getKey(), entry.getValue()));
		}
		Collections.sort(nameCandidates);
		
		return kb.getPropertyCandidates(nameCandidates, subject, object, subjectTC, objectTC);
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
			Map<String, Float> nameCandidates = wnc.getSynonyms(var.name, "n");
			Set<ComparablePair<String, Float>> nameCandidateSet = new HashSet<>();
			nameCandidateSet.add(new ComparablePair<String, Float>(var.name, 1.0f));
			// This value can be anything < 1.0f so that the original term is preferred
			float synonymPenalty = 0.9f;
			for (Entry<String, Float> entry : nameCandidates.entrySet()) {
				nameCandidateSet.add(new ComparablePair<String, Float>(entry.getKey(), entry.getValue() * synonymPenalty));
			}
			List<ComparablePair<Resource, Float>> typeCandidates = kb.getTypeCandidates(nameCandidateSet);
			if (typeCandidates != null && !typeCandidates.isEmpty()) {
				Resource r = typeCandidates.iterator().next().key;
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
	
	public String getBestSPARQLQuery(PseudoQuery pseudoQuery) {
		List<ComparablePair<String, Float>> queryCandidates = buildSPARQLQuery(pseudoQuery);
		for (ComparablePair<String, Float> query : queryCandidates) {
			try {
				System.out.println("?" + pseudoQuery.focusVar.name + ":");
				Collection<String> answer = kb.query(query.key, pseudoQuery.focusVar.name);
				if (!answer.isEmpty()) {
					return query.key;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return null;
	}
	
	public List<ComparablePair<String, Float>> buildSPARQLQuery(PseudoQuery pseudoQuery) {
		Map<Variable, TypeConstraint> varTypeConstraints = new HashMap<>();
		for (Variable var : pseudoQuery.vars.values()) {
			varTypeConstraints.put(var, mapVariableType(var));
		}
		
		final float NO_TYPE_CONSTRAINT_PENALTY = 0.1f;
		List<ComparablePair<String, Float>> queryCandidates = new LinkedList<>();
		List<ComparablePair<String, Float>> queryCandidatesWithConstraints = buildSPARQLQuery(pseudoQuery, varTypeConstraints);
		List<ComparablePair<String, Float>> queryCandidatesWithoutConstraints = buildSPARQLQuery(pseudoQuery, new HashMap<Variable, TypeConstraint>());
		for (ComparablePair<String, Float> q : queryCandidatesWithoutConstraints) {
			q.value *= NO_TYPE_CONSTRAINT_PENALTY;
		}
		queryCandidates.addAll(queryCandidatesWithConstraints);
		queryCandidates.addAll(queryCandidatesWithoutConstraints);
		Collections.sort(queryCandidates);
		
		return queryCandidates;
	}
	
	public List<ComparablePair<String, Float>> buildSPARQLQuery(PseudoQuery pseudoQuery, Map<Variable, TypeConstraint> varTypeConstraints) {
		System.out.println("Building sparql query for pseudo query:");
		System.out.println(pseudoQuery);
		List<ComparablePair<String, Float>> queryCandidates = new LinkedList<>();
		// Add an empty "seed" query
		queryCandidates.add(new ComparablePair<String, Float>("", 1.0f));
		
		for (SPARQLTriple t : pseudoQuery.triples) {
			List<ComparablePair<String, Float>> updatedQueryCandidates = new LinkedList<>();
			for (ComparablePair<String, Float> baseQuery : queryCandidates) {
				List<ComparablePair<String, Float>> tripleQueryCandidates = new LinkedList<>();
				SPARQLTriple tSwapped = new SPARQLTriple(t.object, t.predicate, t.subject);
				tripleQueryCandidates.addAll(buildSPARQLTriple(t, varTypeConstraints));
				tripleQueryCandidates.addAll(buildSPARQLTriple(tSwapped, varTypeConstraints));
				for (ComparablePair<String, Float> tripleQuery : tripleQueryCandidates) {
					String tripleQueryStr = tripleQuery.key;
					float score = baseQuery.value * tripleQuery.value;
					String query = baseQuery.key + tripleQueryStr;
					updatedQueryCandidates.add(new ComparablePair<String, Float>(query, score));
				}
			}
			queryCandidates = updatedQueryCandidates;
		}
		// Sort in ascending order by score
		Collections.sort(queryCandidates);

		List<ComparablePair<String, Float>> completeQueryCandidates = new LinkedList<>();
		for (ComparablePair<String, Float> queryCandidate : queryCandidates) {
			String query = queryCandidate.key;
			float score = queryCandidate.value;
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
			completeQueryCandidates.add(new ComparablePair<String, Float>(finalQuery, score));
		}
		
		return completeQueryCandidates;
	}
	
	List<ComparablePair<String, Float>> buildSPARQLTriple(SPARQLTriple triple, Map<Variable, TypeConstraint> varTypeConstraints) {
		List<ComparablePair<String, Float>> res = new LinkedList<>();

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
					tripleQuery = kb.getSPARQLResourceString(subject) + " "
							    + kb.getSPARQLResourceString(prop) + " "
							    + triple.object + " . ";
					res.add(new ComparablePair<String, Float>(tripleQuery, comboScore));
				}
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
					tripleQuery = triple.subject + " "
								+ kb.getSPARQLResourceString(prop) + " "
								+ kb.getSPARQLResourceString(object) + " . ";
					res.add(new ComparablePair<String, Float>(tripleQuery, comboScore));
				}
			}
		// Relations between two variables
		} else if (triple.predicate != null) {
			TypeConstraint subjectTC = varTypeConstraints.get(triple.subject);
			TypeConstraint objectTC = varTypeConstraints.get(triple.object);
					
			Collection<ComparablePair<Property, Float>> propCandidates = mapProperty(triple.predicate, null, null, subjectTC, objectTC);
			System.out.println("prop candidates: " + propCandidates);
			for (ComparablePair<Property, Float> scoredProp : propCandidates) {
				Property prop = scoredProp.key;
				tripleQuery = triple.subject + " "
					    	+ kb.getSPARQLResourceString(prop) + " "
					    	+ triple.object + " . ";
				res.add(new ComparablePair<String, Float>(tripleQuery, scoredProp.value));
			}
		}
		
		Collections.sort(res);
		return res;
	}
}
