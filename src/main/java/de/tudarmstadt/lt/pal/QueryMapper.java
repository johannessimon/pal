package de.tudarmstadt.lt.pal;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.log4j.Logger;

import de.tudarmstadt.lt.pal.KnowledgeBaseConnector.Answer;
import de.tudarmstadt.lt.pal.MappedString.TraceElement;
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
	
	Logger log = Logger.getLogger("de.tudarmstadt.lt.pal");
	
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
	
	/**
	 * Maps a triple element to candidate resources
	 */
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
	
	/**
	 * Maps a triple predicate to ontology property candidates
	 */
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
			List<TraceElement> trace = new LinkedList<TraceElement>();
			trace.add(new TraceElement(nameLC, ""));
			wnc.addSynonym(synonyms, nameLC, trace, 1.0f);
		}
		
		List<ComparablePair<MappedString, Float>> nameCandidates = new LinkedList<ComparablePair<MappedString, Float>>();
		for (Entry<MappedString, Float> entry : synonyms.entrySet()) {
			nameCandidates.add(new ComparablePair<MappedString, Float>(entry.getKey(), entry.getValue()));
		}
		Collections.sort(nameCandidates);
		
		/*final int MAX_NUM_CANDIDATES = 100;
		if (nameCandidates.size() > MAX_NUM_CANDIDATES) {
			nameCandidates = nameCandidates.subList(0, MAX_NUM_CANDIDATES);
		}*/
		
		return kb.getPropertyCandidates(nameCandidates, subjectURI, objectURI, subjectTC, objectTC);
	}
	
	/**
	 * Determines a variable's best-bet type(s)
	 */
	private Collection<ComparablePair<TypeConstraint, Float>> mapVariableType(Variable var) {
		List<ComparablePair<TypeConstraint, Float>> res = new LinkedList<ComparablePair<TypeConstraint, Float>>();
		
		switch (var.unmappedType) {
		case Agent:
			res.add(new ComparablePair<TypeConstraint, Float>(new TypeConstraint(
					TypeConstraint.BasicType.Resource,
					new MappedString("schema:Person", Arrays.asList(
							new TraceElement(var.name, ""),
							new TraceElement("schema:Person (\"who\")", "http://schema.org/Person")))), 1.0f));

			res.add(new ComparablePair<TypeConstraint, Float>(new TypeConstraint(
					TypeConstraint.BasicType.Resource,
					new MappedString("schema:Organisation", Arrays.asList(
							new TraceElement(var.name, ""),
							new TraceElement("schema:Organisation (\"who\")", "http://schema.org/Organisation")))), 1.0f));
			break;
		case Place:
			res.add(new ComparablePair<TypeConstraint, Float>(new TypeConstraint(
					TypeConstraint.BasicType.Resource,
					new MappedString("schema:Place", Arrays.asList(
							new TraceElement(var.name, ""),
							new TraceElement("schema:Place (\"where\")", "http://schema.org/Place")))), 1.0f));
			break;
		case Date:
			res.add(new ComparablePair<TypeConstraint, Float>(new TypeConstraint(
					TypeConstraint.BasicType.Literal,
					new MappedString("xsd:date", Arrays.asList(
							new TraceElement(var.name, ""),
							new TraceElement("xsd:date (\"when\")", "http://www.w3.org/2001/XMLSchema#date")))), 1.0f));
			break;
		case Number:
			res.add(new ComparablePair<TypeConstraint, Float>(new TypeConstraint(
					TypeConstraint.BasicType.Literal,
					new MappedString("_number_", Arrays.asList(
							new TraceElement(var.name, ""),
							new TraceElement("number (\"many\")", "")))), 1.0f));
			break;
		case Literal:
			res.add(new ComparablePair<TypeConstraint, Float>(new TypeConstraint(
					TypeConstraint.BasicType.Literal, null), 1.0f));
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
				res.add(new ComparablePair<TypeConstraint, Float>(new TypeConstraint(
						TypeConstraint.BasicType.Resource,
						type.key), type.value));
			}
		}
		return res;
	}
	
	/**
	 * Generates a list of candidates SPARQL queries from the given pseudo query and returns the first
	 * query that yields any results (the latter being a way of filtering out non-sense queries)
	 */
	public Query getBestSPARQLQuery(Query pseudoQuery) {
		List<ComparablePair<Query, Float>> queryCandidates = buildSPARQLQuery(pseudoQuery);
		final int MAX_NUM_QUERY_CANDIDATES = 100;
		if (queryCandidates.size() > MAX_NUM_QUERY_CANDIDATES) {
			queryCandidates = queryCandidates.subList(0, MAX_NUM_QUERY_CANDIDATES);
		}
		log.debug("=================================================================");
		log.debug("Generated " + queryCandidates.size() + " SPARQL query candidates:");
		log.debug(queryCandidates);
		log.debug("=================================================================");
		for (ComparablePair<Query, Float> query : queryCandidates) {
			Collection<Answer> answer = kb.query(query.key);
			if (!answer.isEmpty()) {
				return query.key;
			}
		}
		return null;
	}
	
	/**
	 * Generates candidates SPARQL queries from a specified pseudo query
	 */
	public List<ComparablePair<Query, Float>> buildSPARQLQuery(Query pseudoQuery) {
		List<ComparablePair<Query, Float>> queryCandidates = new LinkedList<ComparablePair<Query, Float>>();
		queryCandidates.add(new ComparablePair<Query, Float>(pseudoQuery, 1.0f));
		
		// penalty for one missing type constraint
		final float NO_TYPE_CONSTRAINT_PENALTY = 0.1f;
		Variable[] vars = pseudoQuery.vars.values().toArray(new Variable[0]);
		int numVars = vars.length;
		// penalty for all type constraints missing
		final float NO_TYPE_CONSTRAINT_AT_ALL_PENALTY = (int)Math.round(Math.pow(NO_TYPE_CONSTRAINT_PENALTY, numVars));
		
		// for each variable, generate a new pseudo query for each possible type constraint
		// (no type, type 1, type 2, ...)
		for (int i = 0; i < numVars; i++) {
			List<ComparablePair<Query, Float>> _queryCandidates = new LinkedList<ComparablePair<Query, Float>>();
			Collection<ComparablePair<TypeConstraint, Float>> tcs = mapVariableType(vars[i]);
			tcs.add(null); // also consider no type constraint (convenient for processing in loop)
			for (ComparablePair<TypeConstraint, Float> tc : tcs) {
				for (ComparablePair<Query, Float> q : queryCandidates) {
					Query _q = (Query)q.key.clone();
					Variable[] _vars = _q.vars.values().toArray(new Variable[0]);
					float score = NO_TYPE_CONSTRAINT_PENALTY;
					if (tc != null) {
						score = q.value * tc.value;
						_vars[i].mappedType = tc.key;
					}
					if (score > NO_TYPE_CONSTRAINT_AT_ALL_PENALTY) {
						_queryCandidates.add(new ComparablePair<Query, Float>(_q, score));
					}
				}
			}
			
			queryCandidates = _queryCandidates;
		}

		log.debug("=================================================================");
		log.debug("Generated " + queryCandidates.size() + " pseudo query candidates:");
		log.debug(queryCandidates);
		log.debug("=================================================================");

		List<ComparablePair<Query, Float>> _queryCandidates = new LinkedList<ComparablePair<Query, Float>>();
		for (ComparablePair<Query, Float> q : queryCandidates) {
			_queryCandidates.addAll(_buildSPARQLQuery(q));
		}
		
		Collections.sort(_queryCandidates);
		
		return _queryCandidates;
	}
	
	private List<ComparablePair<Query, Float>> _buildSPARQLQuery(ComparablePair<Query, Float> scoredPseudoQuery) {
		Query pseudoQuery = scoredPseudoQuery.key;
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
	
	/**
	 * Maps a pseudo triple to a list of candidate SPARQL triples
	 */
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
