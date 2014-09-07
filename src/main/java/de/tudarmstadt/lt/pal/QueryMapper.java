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
import com.hp.hpl.jena.rdf.model.Resource;

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
	WordNetConnector wnc = new WordNetConnector("/Volumes/Bill/No-Backup/wordnet31/dict");
	
	public QueryMapper(KnowledgeBaseConnector kb) {
		this.kb = kb;
	}
	
	private Collection<ComparablePair<MappedString, Float>> mapResource(Element e) {
		List<ComparablePair<MappedString, Float>> candidates = null;
		
		if (e instanceof Constant) {
			Constant c = (Constant)e;
			if (c.type == Constant.Type.Mapped) {
				candidates = Collections.singletonList(new ComparablePair<MappedString, Float>(new MappedString(e.name), 1.0f));
			} else {
				// Get scores for 100 resource candidates and choose best N
				int numCandidates = 1000;
				int numCandidatesFiltered = 3;
				candidates = kb.getResourceCandidates(e.name, numCandidates);
				if (candidates.size() > numCandidatesFiltered) {
					candidates = candidates.subList(0, numCandidatesFiltered);
				}
			}
		}
		return candidates;
	}
	
	private Collection<ComparablePair<MappedString, Float>> mapProperty(Element p, String subjectURI, String objectURI, TypeConstraint subjectTC, TypeConstraint objectTC) {
		Map<MappedString, Float> synonyms = new HashMap<>();
		
		if (p instanceof Constant) {
			Constant c = (Constant)p;
			if (c.type == Constant.Type.Mapped) {
				return Collections.singleton(new ComparablePair<MappedString, Float>(new MappedString(p.name), 1.0f));
			} else {
				String nameLC = p.name.toLowerCase();
				String pos = null;
				if (nameLC.contains("#")) {
					int sepIndex = nameLC.indexOf('#');
					pos = nameLC.substring(sepIndex + 1);
					nameLC = nameLC.substring(0, sepIndex);
					wnc.addSynonyms(synonyms, wnc.getRelatedWords(nameLC, pos));
				}
				List<String> trace = new LinkedList<>();
				trace.add(nameLC);
				wnc.addSynonym(synonyms, nameLC, trace, 1.0f);
			}
		}
		
		List<ComparablePair<MappedString, Float>> nameCandidates = new LinkedList<>();
		for (Entry<MappedString, Float> entry : synonyms.entrySet()) {
			nameCandidates.add(new ComparablePair<>(entry.getKey(), entry.getValue()));
		}
		Collections.sort(nameCandidates);
		
		return kb.getPropertyCandidates(nameCandidates, subjectURI, objectURI, subjectTC, objectTC);
	}
	
	private TypeConstraint mapVariableType(Variable var) {
		TypeConstraint.BasicType basicType = null;
		String typeURI = null;
		switch (var.unmappedType) {
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
			String varProperty = var.name.replaceAll("_", " ");
			Map<MappedString, Float> nameCandidates = wnc.getSynonyms(varProperty, "n");
			Set<ComparablePair<MappedString, Float>> nameCandidateSet = new HashSet<>();
			nameCandidateSet.add(new ComparablePair<MappedString, Float>(new MappedString(var.name), 1.0f));
			// This value can be anything < 1.0f so that the original term is preferred
			float synonymPenalty = 0.9f;
			for (Entry<MappedString, Float> entry : nameCandidates.entrySet()) {
				nameCandidateSet.add(new ComparablePair<MappedString, Float>(entry.getKey(), entry.getValue() * synonymPenalty));
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
	
	public Query getBestSPARQLQuery(Query pseudoQuery) {
		List<ComparablePair<Query, Float>> queryCandidates = buildSPARQLQuery(pseudoQuery);
		for (ComparablePair<Query, Float> query : queryCandidates) {
			try {
				System.out.println("?" + pseudoQuery.focusVar.name + ":");
				Collection<String> answer = kb.query(query.key);
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
		for (Variable var : pseudoQueryWithTC.vars.values()) {
			var.mappedType = mapVariableType(var);
		}
		
		final float NO_TYPE_CONSTRAINT_PENALTY = 0.1f;
		List<ComparablePair<Query, Float>> queryCandidates = new LinkedList<>();
		List<ComparablePair<Query, Float>> queryCandidatesWithTC = _buildSPARQLQuery(pseudoQueryWithTC);
		List<ComparablePair<Query, Float>> queryCandidatesWithoutTC = _buildSPARQLQuery(pseudoQueryWithoutTC);
		for (ComparablePair<Query, Float> q : queryCandidatesWithoutTC) {
			q.value *= NO_TYPE_CONSTRAINT_PENALTY;
		}
		queryCandidates.addAll(queryCandidatesWithTC);
		queryCandidates.addAll(queryCandidatesWithoutTC);
		Collections.sort(queryCandidates);
		
		return queryCandidates;
	}
	
	private List<ComparablePair<Query, Float>> _buildSPARQLQuery(Query pseudoQuery) {
		System.out.println("Building sparql query for pseudo query:");
		System.out.println(pseudoQuery);
		List<ComparablePair<Query, Float>> queryCandidates = new LinkedList<>();
		Query _baseQuery = (Query)pseudoQuery.clone();
		_baseQuery.triples.clear();
		// Add an empty "seed" query
		queryCandidates.add(new ComparablePair<Query, Float>(_baseQuery, 1.0f));
		
		for (Triple t : pseudoQuery.triples) {
			List<ComparablePair<Query, Float>> updatedQueryCandidates = new LinkedList<>();
			for (ComparablePair<Query, Float> baseQuery : queryCandidates) {
				List<ComparablePair<Triple, Float>> tripleQueryCandidates = new LinkedList<>();
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
		List<ComparablePair<Triple, Float>> res = new LinkedList<>();

		Collection<ComparablePair<MappedString, Float>> subjectCandidates = mapResource(triple.subject);
		Collection<ComparablePair<MappedString, Float>> objectCandidates = mapResource(triple.object);
		Variable subjectVar = (triple.subject instanceof Variable) ? (Variable)triple.subject : null;
		Variable objectVar = (triple.object instanceof Variable) ? (Variable)triple.object : null;
		
		if (subjectCandidates != null) {
			for (ComparablePair<MappedString, Float> scoredSubject : subjectCandidates) {
				MappedString subject = scoredSubject.key;
				// Only returns type constraint != null if object is a variable and has been assigned a type constraint

				Collection<ComparablePair<MappedString, Float>> propCandidates = mapProperty(triple.predicate, subject.word, null, null, objectVar.mappedType);
				System.out.println("prop candidates: " + propCandidates);
				for (ComparablePair<MappedString, Float> scoredProp : propCandidates) {
					String prop = scoredProp.key.word;
					float comboScore = scoredSubject.value * scoredProp.value;
					
					Constant subjectElement = new Constant(subject.word, Constant.Type.Mapped);
					subjectElement.trace = subject.trace;
					Constant predicateElement = new Constant(prop, Constant.Type.Mapped);
					predicateElement.trace = scoredProp.key.trace;
					Triple mappedTriple = new Triple(subjectElement, predicateElement, objectVar);
					res.add(new ComparablePair<Triple, Float>(mappedTriple, comboScore));
				}
			}
		} else if (objectCandidates != null) {
			for (ComparablePair<MappedString, Float> scoredObject : objectCandidates) {
				MappedString object = scoredObject.key;

				Collection<ComparablePair<MappedString, Float>> propCandidates = mapProperty(triple.predicate, null, object.word, subjectVar.mappedType, null);
				System.out.println("prop candidates: " + propCandidates);
				for (ComparablePair<MappedString, Float> scoredProp : propCandidates) {
					String prop = scoredProp.key.word;
					float comboScore = scoredObject.value * scoredProp.value;
					
					Constant predicateElement = new Constant(prop, Constant.Type.Mapped);
					predicateElement.trace = scoredProp.key.trace;
					Constant objectElement = new Constant(object.word, Constant.Type.Mapped);
					objectElement.trace = object.trace;
					Triple mappedTriple = new Triple(subjectVar, predicateElement, objectElement);
					res.add(new ComparablePair<Triple, Float>(mappedTriple, comboScore));
				}
			}
		// Relations between two variables
		} else if (triple.predicate != null) {
			Collection<ComparablePair<MappedString, Float>> propCandidates = mapProperty(triple.predicate, null, null, subjectVar.mappedType, objectVar.mappedType);
			System.out.println("prop candidates: " + propCandidates);
			for (ComparablePair<MappedString, Float> scoredProp : propCandidates) {
				String prop = scoredProp.key.word;
				Constant predicateElement = new Constant(prop, Constant.Type.Mapped);
				predicateElement.trace = scoredProp.key.trace;
				Triple mappedTriple = new Triple(subjectVar, predicateElement, objectVar);
				res.add(new ComparablePair<Triple, Float>(mappedTriple, scoredProp.value));
			}
		}
		
		Collections.sort(res);
		return res;
	}
}
