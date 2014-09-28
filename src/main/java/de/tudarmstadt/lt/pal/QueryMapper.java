package de.tudarmstadt.lt.pal;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import de.tudarmstadt.lt.pal.KnowledgeBaseConnector.Answer;
import de.tudarmstadt.lt.pal.Triple.Constant;
import de.tudarmstadt.lt.pal.Triple.Element;
import de.tudarmstadt.lt.pal.Triple.TypeConstraint;
import de.tudarmstadt.lt.pal.Triple.Variable;
import de.tudarmstadt.lt.pal.util.ComparablePair;
import de.tudarmstadt.lt.pal.wordnet.WordNetConnector;

/**
 * Maps pseudo-query triples to valid DBPedia triples.<br/>
 * Example: <code>[Subject: ?book] [Predicate: "written by"] [Object: "Dan Brown"]</code><br/>
 * Result: <code>[Subject: ?book] [Predicate: dbpedia-owl:author] [Object: dbpedia:Dan_Brown]</code>
 */
public class QueryMapper {
	KnowledgeBaseConnector kb;
	WordNetConnector wnc;
	
	public QueryMapper(KnowledgeBaseConnector kb) {
		String wnHome = System.getenv("WNHOME");
		if (wnHome == null) {
			throw new IllegalArgumentException("WNHOME environment variable not set.");
		} else if (!new File(wnHome).exists()) {
			throw new IllegalArgumentException("WNHOME directory (" + wnHome + ") does not exist.");
		}
		wnc = new WordNetConnector(wnHome + "/dict");
		this.kb = kb;
	}
	
	private Collection<ComparablePair<MappedString, Float>> mapResource(Element e) {
		List<ComparablePair<MappedString, Float>> candidates = null;
		
		if (e != null && e.isConstant()) {
			// Get scores for 100 resource candidates and choose best N
			int numCandidates = 1000;
			int numCandidatesFiltered = 5;
			candidates = kb.getResourceCandidates(e.name, numCandidates);
			if (candidates.size() > numCandidatesFiltered) {
				candidates = candidates.subList(0, numCandidatesFiltered);
			}
		}
		return candidates;
	}
	
	private Collection<ComparablePair<MappedString, Float>> mapProperty(Element p, String subjectURI, String objectURI, TypeConstraint subjectTC, TypeConstraint objectTC) {
		Map<MappedString, Float> synonyms = new HashMap<MappedString, Float>();
		
		if (p != null && p.isConstant()) {
			String nameLC = p.name.toLowerCase();
			String pos = null;
			if (nameLC.contains("#")) {
				int sepIndex = nameLC.indexOf('#');
				pos = nameLC.substring(sepIndex + 1);
				nameLC = nameLC.substring(0, sepIndex);
				wnc.addSynonyms(synonyms, wnc.getRelatedWords(nameLC, pos));
			}
			List<String> trace = new LinkedList<String>();
			trace.add(nameLC);
			wnc.addSynonym(synonyms, nameLC, trace, 1.0f);
		}
		
		List<ComparablePair<MappedString, Float>> nameCandidates = new LinkedList<ComparablePair<MappedString, Float>>();
		for (Entry<MappedString, Float> entry : synonyms.entrySet()) {
			nameCandidates.add(new ComparablePair<MappedString, Float>(entry.getKey(), entry.getValue()));
		}
		Collections.sort(nameCandidates);
		
		return kb.getPropertyCandidates(nameCandidates, subjectURI, objectURI, subjectTC, objectTC);
	}
	
	private ComparablePair<TypeConstraint, Float> mapVariableType(Variable var) {
		TypeConstraint.BasicType basicType = null;
		MappedString typeURI = null;
		float score = 1.0f;
		List<String> derivedTypeTrace = new LinkedList<String>();
		derivedTypeTrace.add(var.name);
		switch (var.unmappedType) {
		case Agent:
			basicType = TypeConstraint.BasicType.Resource;
			derivedTypeTrace.add("dbpedia-owl:Person (\"who\")");
			typeURI = new MappedString("dbpedia-owl:Person", derivedTypeTrace);
			break;
		case Place:
			basicType = TypeConstraint.BasicType.Resource;
			derivedTypeTrace.add("dbpedia-owl:Place (\"where\")");
			typeURI = new MappedString("dbpedia-owl:Place", derivedTypeTrace);
			break;
		case Date:
			basicType = TypeConstraint.BasicType.Literal;
			derivedTypeTrace.add("xsd:date (\"when\")");
			typeURI = new MappedString("xsd:date", derivedTypeTrace);
			break;
		case Number:
			basicType = TypeConstraint.BasicType.Literal;
			derivedTypeTrace.add("number (\"many\")");
			typeURI = new MappedString("_number_", derivedTypeTrace);
			break;
		case Literal:
			basicType = TypeConstraint.BasicType.Literal;
			typeURI = null;
			break;
		case Unknown:
			String varProperty = var.name.replaceAll("_", " ");
			Map<MappedString, Float> nameCandidates = wnc.getSynonymsAndHypernyms(varProperty, "n");
			Set<ComparablePair<MappedString, Float>> nameCandidateSet = new HashSet<ComparablePair<MappedString, Float>>();
			nameCandidateSet.add(new ComparablePair<MappedString, Float>(new MappedString(var.name), 1.0f));
			// This value can be anything < 1.0f so that the original term is preferred
			float synonymPenalty = 0.9f;
			for (Entry<MappedString, Float> entry : nameCandidates.entrySet()) {
				nameCandidateSet.add(new ComparablePair<MappedString, Float>(entry.getKey(), entry.getValue() * synonymPenalty));
			}
			ComparablePair<MappedString, Float> type = kb.getType(nameCandidateSet);
			if (type != null) {
				score = type.value;
				MappedString r = type.key;
				if (kb.resourceIsClass(r.value)) {
					basicType = TypeConstraint.BasicType.Resource;
				}
				typeURI = r;
			}
		}
		if (basicType != null) {
			return new ComparablePair<TypeConstraint, Float>(new TypeConstraint(basicType, typeURI), score);
		}
		return null;
	}
	
	public Query getBestSPARQLQuery(Query pseudoQuery) {
		List<ComparablePair<Query, Float>> queryCandidates = buildSPARQLQuery(pseudoQuery);
		for (ComparablePair<Query, Float> query : queryCandidates) {
			try {
//				System.out.println("?" + pseudoQuery.focusVar.name + ":");
				Collection<Answer> answer = kb.query(query.key);
				if (!answer.isEmpty()) {
					return query.key;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return null;
	}
	
	public List<ComparablePair<Query, Float>> buildSPARQLQuery(Query pseudoQuery) {
		Query pseudoQueryWithTC = (Query)pseudoQuery.clone();
		Query pseudoQueryWithoutTC = (Query)pseudoQuery.clone();
		final float NO_TYPE_CONSTRAINT_PENALTY = 0.1f;
		float typeConstraintScore = 1.0f;
		for (Variable var : pseudoQueryWithTC.vars.values()) {
			ComparablePair<TypeConstraint, Float> tc = mapVariableType(var);
			// Adding this type would cause the query to be scored lower than an untyped query
			if (tc != null && tc.value > NO_TYPE_CONSTRAINT_PENALTY) {
				var.mappedType = tc.key;
				typeConstraintScore *= tc.value;
			}
		}
		
		List<ComparablePair<Query, Float>> queryCandidates = new LinkedList<ComparablePair<Query, Float>>();
		if (typeConstraintScore > NO_TYPE_CONSTRAINT_PENALTY) {
			queryCandidates.addAll(_buildSPARQLQuery(new ComparablePair<Query, Float>(pseudoQueryWithTC, typeConstraintScore)));
		}
		queryCandidates.addAll(_buildSPARQLQuery(new ComparablePair<Query, Float>(pseudoQueryWithoutTC, NO_TYPE_CONSTRAINT_PENALTY)));
		Collections.sort(queryCandidates);
		
		return queryCandidates;
	}
	
	private List<ComparablePair<Query, Float>> _buildSPARQLQuery(ComparablePair<Query, Float> scoredPseudoQuery) {
		Query pseudoQuery = scoredPseudoQuery.key;
//		System.out.println("Building sparql query for pseudo query:");
//		System.out.println(pseudoQuery);
		List<ComparablePair<Query, Float>> queryCandidates = new LinkedList<ComparablePair<Query, Float>>();
		Query _baseQuery = (Query)pseudoQuery.clone();
		_baseQuery.triples.clear();
		// Add an empty "seed" query
		queryCandidates.add(new ComparablePair<Query, Float>(_baseQuery, scoredPseudoQuery.value));
		
		for (Triple t : pseudoQuery.triples) {
			List<ComparablePair<Query, Float>> updatedQueryCandidates = new LinkedList<ComparablePair<Query, Float>>();
			for (ComparablePair<Query, Float> baseQuery : queryCandidates) {
				List<ComparablePair<Triple, Float>> tripleQueryCandidates = new LinkedList<ComparablePair<Triple, Float>>();
				Triple tSwapped = new Triple(t.object, t.predicate, t.subject);
				tripleQueryCandidates.addAll(buildSPARQLTriple(t, pseudoQuery));
				tripleQueryCandidates.addAll(buildSPARQLTriple(tSwapped, pseudoQuery));
				for (ComparablePair<Triple, Float> tripleQueryEntry : tripleQueryCandidates) {
					Triple tripleQuery = tripleQueryEntry.key;
					float score = baseQuery.value * tripleQueryEntry.value;
					Query query = (Query)baseQuery.key.clone();
					query.triples.add(tripleQuery);
					updatedQueryCandidates.add(new ComparablePair<Query, Float>(query, score));
				}
			}
			queryCandidates = updatedQueryCandidates;
		}
		// Sort in ascending order by score
		Collections.sort(queryCandidates);
		
		return queryCandidates;
	}
	
	List<ComparablePair<Triple, Float>> buildSPARQLTriple(Triple triple, Query query) {
		List<ComparablePair<Triple, Float>> res = new LinkedList<ComparablePair<Triple, Float>>();

		// Try at least N resources (or more if the first N resources do not yield any property matches)
		int minNumResourcesToTry = 3;
		int numResourcesTried = 0;
		Collection<ComparablePair<MappedString, Float>> subjectCandidates = mapResource(triple.subject);
		Collection<ComparablePair<MappedString, Float>> objectCandidates = mapResource(triple.object);
		Variable subjectVar = (triple.subject instanceof Variable) ? (Variable)triple.subject : null;
		Variable objectVar = (triple.object instanceof Variable) ? (Variable)triple.object : null;
		
		if (subjectCandidates != null && objectVar != null) {
			for (ComparablePair<MappedString, Float> scoredSubject : subjectCandidates) {
				MappedString subject = scoredSubject.key;
				// Only returns type constraint != null if object is a variable and has been assigned a type constraint

				Collection<ComparablePair<MappedString, Float>> propCandidates = mapProperty(triple.predicate, subject.value, null, null, objectVar.mappedType);
//				System.out.println("prop candidates: " + propCandidates);
				for (ComparablePair<MappedString, Float> scoredProp : propCandidates) {
					String prop = scoredProp.key.value;
					float comboScore = scoredSubject.value * scoredProp.value;
					
					Constant subjectElement = new Constant(subject.value);
					subjectElement.trace = subject.trace;
					Constant predicateElement = new Constant(prop);
					predicateElement.trace = scoredProp.key.trace;
					Triple mappedTriple = new Triple(subjectElement, predicateElement, objectVar);
					res.add(new ComparablePair<Triple, Float>(mappedTriple, comboScore));
				}
				numResourcesTried++;
				if (numResourcesTried >= minNumResourcesToTry && !res.isEmpty()) {
					break;
				}
			}
		} else if (objectCandidates != null && subjectVar != null) {
			for (ComparablePair<MappedString, Float> scoredObject : objectCandidates) {
				MappedString object = scoredObject.key;

				Collection<ComparablePair<MappedString, Float>> propCandidates = mapProperty(triple.predicate, null, object.value, subjectVar.mappedType, null);
//				System.out.println("prop candidates: " + propCandidates);
				for (ComparablePair<MappedString, Float> scoredProp : propCandidates) {
					String prop = scoredProp.key.value;
					float comboScore = scoredObject.value * scoredProp.value;
					
					Constant predicateElement = new Constant(prop);
					predicateElement.trace = scoredProp.key.trace;
					Constant objectElement = new Constant(object.value);
					objectElement.trace = object.trace;
					Triple mappedTriple = new Triple(subjectVar, predicateElement, objectElement);
					res.add(new ComparablePair<Triple, Float>(mappedTriple, comboScore));
				}
				numResourcesTried++;
				if (numResourcesTried >= minNumResourcesToTry && !res.isEmpty()) {
					break;
				}
			}
		// Relations between two variables
		} else if (subjectVar != null && objectVar != null && triple.predicate != null) {
			Collection<ComparablePair<MappedString, Float>> propCandidates = mapProperty(triple.predicate, null, null, subjectVar.mappedType, objectVar.mappedType);
//			System.out.println("prop candidates: " + propCandidates);
			for (ComparablePair<MappedString, Float> scoredProp : propCandidates) {
				String prop = scoredProp.key.value;
				Constant predicateElement = new Constant(prop);
				predicateElement.trace = scoredProp.key.trace;
				Triple mappedTriple = new Triple(subjectVar, predicateElement, objectVar);
				res.add(new ComparablePair<Triple, Float>(mappedTriple, scoredProp.value));
			}
		}
		
		Collections.sort(res);
		return res;
	}
}
