package de.tudarmstadt.lt.pal;
import java.net.URLDecoder;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;

import de.tudarmstadt.lt.pal.Triple.TypeConstraint;
import de.tudarmstadt.lt.pal.Triple.TypeConstraint.BasicType;
import de.tudarmstadt.lt.pal.Triple.Variable;
import de.tudarmstadt.lt.pal.util.ComparablePair;
import de.tudarmstadt.lt.pal.util.StringUtil;

/**
 * Basic interface to ontology and triple store
 */
public class KnowledgeBaseConnector {
	
	String sparqlEndpoint;
	
	private boolean checkIfLocalUriNameIsValidSPARQL(String localName) {
		String invalidPattern = ".*\\.|.*[\\&\\/,].*";
		return !localName.matches(invalidPattern);
	}
	
	public final static String OWL_CLASS_URI = "http://www.w3.org/2002/07/owl#Class";
	public final static String OWL_OBJECT_PROPERTY_URI = "http://www.w3.org/2002/07/owl#ObjectProperty";
	
	/**
	 * Returns a short representation of the resource's URI using
	 * known namespace prefixes. Uses <code>?varName</code> instead
	 * if resource is null
	 */
	public String getSPARQLResourceString(String uri) {
		for (Entry<String, String> namespacePrefix : namespacePrefixes.entrySet()) {
			String ns = namespacePrefix.getKey();
			String prefix = namespacePrefix.getValue();
			if (uri.startsWith(ns)) {
				String localName = uri.substring(ns.length());
				if (checkIfLocalUriNameIsValidSPARQL(localName)) {
					 return prefix + ":" + localName;
				}/* else {
					System.err.println("URI contains bad local name: " + uri);
				}*/
			}
		}
		
		if (uri.startsWith("http://")) {
			return "<" + uri + ">";
		} else {
			return uri;
		}
	}
	
	/**
	 * Maps namespace -> prefix
	 */
	Map<String, String> namespacePrefixes;
	
	private void fillNamespacePrefixes() {
		namespacePrefixes = new HashMap<>();
		namespacePrefixes.put("http://dbpedia.org/ontology/", "dbpedia-owl");
		namespacePrefixes.put("http://dbpedia.org/property/", "dbpprop");
		namespacePrefixes.put("http://dbpedia.org/resource/", "dbpedia");
		namespacePrefixes.put("http://dbpedia.org/class/yago/", "yago");
		namespacePrefixes.put("http://xmlns.com/foaf/0.1/", "foaf");
		namespacePrefixes.put("http://www.w3.org/2001/XMLSchema#", "xsd");
		namespacePrefixes.put("http://www.w3.org/2002/07/owl#", "owl");
		namespacePrefixes.put("http://www.w3.org/2000/01/rdf-schema#", "rdfs");
		namespacePrefixes.put("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "rdf");
		namespacePrefixes.put("http://jena.apache.org/text#", "text");
	}
	
	private String getNamespacePrefixDeclarations() {
		StringBuilder res = new StringBuilder();
		for (Entry<String, String> entry : namespacePrefixes.entrySet()) {
			String prefix = entry.getValue();
			String ns = entry.getKey();
			res.append("PREFIX ");
			res.append(prefix);
			res.append(": <");
			res.append(ns);
			res.append(">\n");
		}
		return res.toString();
	}
	
	/**
	 * Constructor to connect to local Virtuoso server
	 */
	public KnowledgeBaseConnector(String sparqlEndpoint) {
//		sparqlEndpoint = "http://localhost:3030/ds/query";
//		sparqlEndpoint = "http://localhost:8001/sparql/"; // nginx cache
//		sparqlEndpoint = "http://localhost:8890/sparql/"; // virtuoso
//		sparqlEndpoint = "http://dbpedia.org/sparql/"; // DBPedia public
		this.sparqlEndpoint = sparqlEndpoint;
		
		fillNamespacePrefixes();
		retrieveClassesInUse();
	}
	
	private QueryExecution getQueryExec(String query) {
		numQueries++;
		query = getNamespacePrefixDeclarations() + "\n" + query;
		System.out.println(query.replace("\n", " "));
		QueryExecution qexec = null;
		qexec = QueryExecutionFactory.sparqlService(sparqlEndpoint, query);
		return qexec;
	}
	
	/**
	 * Formats typical ontology type names like "EuropeanCapital123" in a more
	 * natural-language conformant format ("european capital")
	 */
	private String formatResourceName(String name) {
		StringBuilder formattedName = new StringBuilder();
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (c == '_') {
				formattedName.append(' ');
				continue;
			}
			if (Character.isUpperCase(c) && i > 0) {
				formattedName.append(' ');
			}
			if (Character.isAlphabetic(c)) {
				formattedName.append(Character.toLowerCase(c));
			}
		}
		return formattedName.toString();
	}
	
	Map<Resource, Collection<String>> classesInUse;
	private void retrieveClassesInUse() {
		classesInUse = new HashMap<>();
		// Selects all classes that are actually in use along with their labels
		String query = "SELECT DISTINCT ?t ?l WHERE { ?t a owl:Class . ?s a ?t . ?t rdfs:label ?l . FILTER(langMatches(lang(?l), \"en\"))}";
		QueryExecution qexec = getQueryExec(query);
		ResultSet results = qexec.execSelect();
		while (results.hasNext()) {
			QuerySolution sol = results.next();
			Resource classResource = sol.getResource("?t");
			String classLabel = sol.getLiteral("?l").toString();
			Collection<String> labels = classesInUse.get(classResource);
			if (labels == null) {
				labels = new LinkedList<String>();
				classesInUse.put(classResource, labels);
			}
			labels.add(classLabel);
		}
	}
	
	List<ComparablePair<MappedString, Float>> getTypeCandidates(Collection<ComparablePair<MappedString, Float>> nameCandidates) {
		List<ComparablePair<MappedString, Float>> types = new LinkedList<>();
		for (ComparablePair<MappedString, Float> c : nameCandidates) {
			String name = formatResourceName(c.key.value);
			for (Entry<Resource, Collection<String>> typeEntry : classesInUse.entrySet()) {
				Resource type = typeEntry.getKey();
				Collection<String> labels = typeEntry.getValue();
				String typeName = formatResourceName(type.getLocalName());
				if (typeName.equals(name)) {
					List<String> trace = new LinkedList<>(c.key.trace);
					trace.add(getSPARQLResourceString(type.getURI()) + " (URI match)");
					MappedString mappedType = new MappedString(type.getURI(), trace);
					types.add(new ComparablePair<>(mappedType, c.value));
					continue;
				}
	
				for (String label : labels) {
					if (label.toLowerCase().equals(name)) {
						List<String> trace = new LinkedList<>(c.key.trace);
						trace.add(getSPARQLResourceString(type.getURI()) + " (label match)");
						MappedString mappedType = new MappedString(type.getURI(), trace);
						types.add(new ComparablePair<>(mappedType, c.value));
						break;
					}
				}
			}
		}
		Collections.sort(types);
		return types;
	}
	
	private String getResourceName(String uri) {
		if(uri.contains("#")) {
			int sepIndex = uri.lastIndexOf("#");
			return uri.substring(sepIndex + 1);
		} else if (uri.contains("/")) {
			int sepIndex = uri.lastIndexOf("/");
			return uri.substring(sepIndex + 1);
		}
		return uri;
	}
	
	List<ComparablePair<MappedString, Float>> getResourceCandidates(String name, int limit) {
		System.out.println("Searching resources... [" + name + "]");
		if (name.contains("#")) {
			int sepIndex = name.indexOf('#');
			name = name.substring(0, sepIndex);
		}
		List<ComparablePair<MappedString, Float>> result = new LinkedList<>();
		{
			String queryString = "SELECT DISTINCT ?subject ?name WHERE { \n"
				               + "  { ?subject foaf:name ?name . ?name <bif:contains> \"'" + name + "'\"} UNION\n"
				               + "  { ?subject rdfs:label ?name . ?name <bif:contains> \"'" + name + "'\"} . \n"
							   // Important: text:query must come first for pre-filtering (otherwise it takes much longer)
//						       + "  ?subject text:query('" + name + "' " + limit + ") . \n"
//		                       + "  ?subject a owl:Thing . \n"
							   + "  { ?subject foaf:name ?name . FILTER(langMatches(lang(?name), \"en\") && contains(?name, \"" + name + "\")) }\n"
                               + "  UNION\n"
                               + "  { ?subject rdfs:label ?name . FILTER(langMatches(lang(?name), \"en\") && contains(?name, \"" + name + "\")) }\n"
//				               + "         strstarts(str(?subject), \"http://dbpedia.org\"))\n"
				               + "} \n"
					           + "LIMIT " + limit;
			QueryExecution qexec = getQueryExec(queryString);
			try {
				ResultSet results = qexec.execSelect();
				for (; results.hasNext(); )
				{
					QuerySolution soln = results.nextSolution();
					String rName = soln.getLiteral("name").getString();
					Resource r = soln.getResource("subject");
					if (r != null) {
						String shortUri = getSPARQLResourceString(r.getURI());
						List<String> trace = new LinkedList<>();
						trace.add(name);
						float labelScore = (float)name.length() / rName.length();
						String rNameFromURI = getResourceName(r.getURI());
						float resourceNameScore = 1.0f - (float)Math.abs(rNameFromURI.length() - name.length()) / rNameFromURI.length();
						float comboScore = labelScore * 0.5f + resourceNameScore * 0.5f;
						// Assign penalty for inexact matches
						float inexactMatchPenalty = 0.5f;
						if (comboScore < 1.0f) {
							comboScore = comboScore * inexactMatchPenalty;
							trace.add(shortUri + " (partial match)");
						} else {
							trace.add(shortUri + " (exact match)");
						}
						result.add(new ComparablePair<MappedString, Float>(new MappedString(shortUri, trace), comboScore));
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally { qexec.close(); }
		}

		Collections.sort(result);
		System.out.println("Done searching resources. Results: " + result);
		return result;
	}
	
	private static int numQueries = 0;
	

	/**
	 * Executes the specified SPARQL query and returns the result(s) with
	 * respect to the focus variable
	 */
	public Collection<String> query(Query query) {
		String queryStr = queryToSPARQL(query);
		return query(queryStr, query.focusVar.name);
	}
	
	public String queryToSPARQL(Query q) {
		String queryStr = "SELECT DISTINCT ?" + q.focusVar.name + " WHERE {\n";
		for (Variable var : q.vars.values()) {
			if (var.mappedType != null) {
				queryStr += getTypeConstraintSPARQLString(var.mappedType, var.name);
			}
		}
		for (Triple t : q.triples) {
			queryStr += "   ";
			queryStr += t.subject.sparqlString() + " ";
			queryStr += t.predicate.sparqlString() + " ";
			queryStr += t.object.sparqlString() + " .\n";
		}

		queryStr += "}";
		return queryStr;
	}
	
	/**
	 * Executes the specified SPARQL query and returns the result(s) with
	 * respect to the focus variable
	 */
	public Collection<String> query(String queryString, String focusVariable) {
		Collection<String> res = new LinkedList<String>();
		
		// Invalid query check
		if (queryString == null || queryString.contains("<null>")) {
			return res;
		}
		

		final int RESULT_LIMIT = 1000;
		if (!queryString.contains("LIMIT")) {
			queryString += " LIMIT " + RESULT_LIMIT;
		}
		
		QueryExecution qexec = getQueryExec(queryString);
		try {
			ResultSet results = qexec.execSelect();
			for (; results.hasNext(); )
			{
				QuerySolution soln = results.nextSolution();
				RDFNode var = soln.get(focusVariable);
				if (var != null) {
					String varString = null;
					if (var.isResource()) {
						Resource r = var.asResource();
						varString = URLDecoder.decode(r.getURI(), "UTF-8");
					} if (var.isLiteral()) {
						Literal l = var.asLiteral();
						varString = l.getValue().toString();
					}
//					System.out.println(varString);
					res.add(varString);
				}
			}
			qexec.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return res;
	}
	
	private static Map<String, Set<String>> uriTypes = new HashMap<>();
	synchronized Set<String> getResourceTypes(String uri) {
		Set<String> types = uriTypes.get(uri);
		if (types == null) {
			types = new HashSet<>();
			uriTypes.put(uri, types);
			System.out.println(System.identityHashCode(uriTypes) + " " + uriTypes.size());
			QueryExecution qexec;
			ResultSet typeResults;
			qexec = getQueryExec("SELECT ?t WHERE { <" + uri + "> a ?t }");
			typeResults = qexec.execSelect();
					
			while (typeResults.hasNext()) {
				QuerySolution sol = typeResults.next();
				types.add(sol.getResource("t").getURI());
			}
		} else {
			System.out.println("HIT");
		}
		return types;
	}
	
	String getTypeConstraintSPARQLString(TypeConstraint tc, String varName) {
		if (tc == null) {
			return "";
		} else if (tc.basicType == BasicType.Resource) {
			return "   ?" + varName + " a " + getSPARQLResourceString(tc.typeURI.value) + " . \n";
		} else/* if (tc.basicType == BasicType.Literal)*/ {
			String res = "   FILTER(ISLITERAL(?" + varName + ")";
			if (tc.typeURI != null) {
				res += " && DATATYPE(?" + varName + ") = " + getSPARQLResourceString(tc.typeURI.value);
			}
			res += ") . \n";
			return res;
		}
	}
	
	String getLuceneQueryString(Collection<ComparablePair<String, Float>> nameCandidates) {
		StringBuilder query = new StringBuilder();
		query.append("\"");
		for (ComparablePair<String, Float> p : nameCandidates) {
			String name = p.key;
			query.append(name.replaceAll("_", " ").toLowerCase());
			query.append(" ");
		}
		query.append("\"");
		return query.toString();
	}
	
	/**
	 * nameCandidates must be sorted in ascending order
	 */
	Collection<ComparablePair<MappedString, Float>> getPropertyCandidates(List<ComparablePair<MappedString, Float>> nameCandidates, String subjectURI, String objectURI,
			                                                          TypeConstraint subjectTC, TypeConstraint objectTC) {
		// This doesn't make much sense (we need at least one constraint, otherwise we'll query the entire DB)
		if (subjectURI == null && objectURI == null && subjectTC == null && objectTC == null) {
			return new LinkedList<>();
		}
		String querySubject = subjectURI == null ? "?s" : subjectURI;
		String queryObject = objectURI == null ? "?o" : objectURI;
		String query = "SELECT ?p ";
		// Count number of property "connections" if we have no clue about the property
		boolean useCountScore = true;//nameCandidates == null;// && subjectIsVar ^ objectIsVar;
		if (useCountScore) {
			String countVar = subjectURI == null ? "?s" : "?o";
			query += "(COUNT(" + countVar + ") AS ?count)";
		}
		query += " WHERE { ";
		query += querySubject + " ?p " + queryObject + " . ";
		query += getTypeConstraintSPARQLString(subjectTC, "s");
		query += getTypeConstraintSPARQLString(objectTC, "o");
		query += "}";
		if (useCountScore) {
			query += " GROUP BY ?p ORDER BY DESC(?count)";
		}
		query += " LIMIT 1000";
		QueryExecution qexec;
		ResultSet propPreCandidates;
		try {
			qexec = getQueryExec(query);
			propPreCandidates = qexec.execSelect();
		} catch (Exception e) {
			System.err.println("Error while executing query: " + query);
			return new LinkedList<>();
		}
				
		List<ComparablePair<MappedString, Float>> result = new LinkedList<>();
		while (propPreCandidates.hasNext()) {
			QuerySolution sol = propPreCandidates.next();
			Resource pRes = sol.getResource("p");
			// Probably the result set is empty (but has one null entry with count 0)
			if (pRes == null) {
				break;
			}
			String pUri = pRes.getURI();
			String pUriShortForm = getSPARQLResourceString(pUri);
			// Assign a very small bonus for higher number of connections
			// -> should only make a difference for near-tie situations
			float countScoreBonus = 0.0f;
			if (useCountScore) {
				// Use logarithm to avoid an untyped property with 100 items
				// to be scored as high as a typed property with 10 items
				countScoreBonus = 0.00001f * (float)Math.log(1 + sol.getLiteral("count").getInt());
			}
			Set<String> pTypes = getResourceTypes(pUri);
			boolean pIsObjectProperty = pTypes.contains(OWL_OBJECT_PROPERTY_URI);
			// We know that the object is a resource, exclude all non-object properties in advance (e.g. DBPedia properties)
			if (objectURI != null && !pIsObjectProperty) {
				continue;
			}
			float propertyTypeScore;
			// Slightly prefer object properties over non-object properties
			if (pIsObjectProperty) {
				propertyTypeScore = 1.01f;
			} else {
				propertyTypeScore = 1.00f;
			}
			
			String pName = formatResourceName(pRes.getLocalName());
			if (nameCandidates != null && !nameCandidates.isEmpty()) {
				for (ComparablePair<MappedString, Float> candidate : nameCandidates) {
					String candidateWord = candidate.key.value;
					if (StringUtil.hasPartStartingWith(pName, candidateWord)) {
						float score = (float)candidateWord.length() / pName.length() * candidate.value * propertyTypeScore + countScoreBonus;
						MappedString mappedPUri = new MappedString(pUriShortForm, candidate.key.trace);
						mappedPUri.trace.add(pUriShortForm + " (URI match)");
						result.add(new ComparablePair<MappedString, Float>(mappedPUri, score));
					}
				}
			} else {
				MappedString mappedPUri = new MappedString(pUriShortForm);
				result.add(new ComparablePair<MappedString, Float>(mappedPUri, countScoreBonus));
			}
		}
		Collections.sort(result);
		int numResults = Math.min(result.size(), 10);
		
		return result.subList(0, numResults);
	}
	
	public void close() {
		System.out.println("Closing KB Connector. Number of queries: " + numQueries);
	}
}
