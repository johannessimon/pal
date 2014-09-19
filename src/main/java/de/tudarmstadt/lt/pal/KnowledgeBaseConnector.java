package de.tudarmstadt.lt.pal;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.sparql.engine.http.QueryEngineHTTP;

import de.tudarmstadt.lt.pal.KnowledgeBaseConnector.Answer.DataType;
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
	Collection<String> graphUris;
	String textIndexSearchPattern;
	Map<String, Answer.DataType> dataTypeMappings;
	
	private boolean checkIfLocalUriNameIsValidSPARQL(String localName) {
		String invalidPattern = ".*\\.|.*[\\&\\/\\(\\),%#].*";
		return !localName.matches(invalidPattern);
	}
	
//	public final static String OWL_CLASS_URI = "http://www.w3.org/2002/07/owl#Class";
//	public final static String OWL_OBJECT_PROPERTY_URI = "http://www.w3.org/2002/07/owl#ObjectProperty";
	
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
	Map<String, String> namespacePrefixes = new HashMap<String, String>();
	
	private void fillNamespacePrefixes() {
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
		namespacePrefixes.put("http://purl.org/ontology/bibo/", "bibo");
		namespacePrefixes.put("http://purl.org/ontology/mo/", "mo");
	}
	
	private String getNamespacePrefixDeclarations(String query) {
		StringBuilder res = new StringBuilder();
		for (Entry<String, String> entry : namespacePrefixes.entrySet()) {
			String prefix = entry.getValue();
			String ns = entry.getKey();
			if (query.contains(prefix + ":")) {
				res.append("PREFIX ");
				res.append(prefix);
				res.append(": <");
				res.append(ns);
				res.append(">\n");
			}
		}
		return res.toString();
	}
	
	private String getFromGraphDeclarations() {
		StringBuilder res = new StringBuilder();
		if (graphUris != null) {
			for (String graphUri : graphUris) {
				res.append("\nFROM <");
				res.append(graphUri);
				res.append(">");
			}
		}
		return res.toString();
	}
	
	public KnowledgeBaseConnector(String file) throws IOException {
		this(new FileInputStream(file));
	}
	
	public KnowledgeBaseConnector(InputStream propertiesFile) throws IOException {
		Properties props = new Properties();
		props.load(propertiesFile);
		sparqlEndpoint = props.getProperty("url");
		if (props.containsKey("graphs")) {
			String graphs = props.getProperty("graphs");
			this.graphUris = Arrays.asList(StringUtils.split(graphs, ","));
		}
		textIndexSearchPattern = props.getProperty("textIndexSearchPattern");
		String prefixes = props.getProperty("prefixes");
		if (prefixes != null) {
			String[] prefixesArr = StringUtils.split(prefixes, ",");
			for (String prefix : prefixesArr) {
				int sepIndex = prefix.indexOf(':');
				String prefixKey = prefix.substring(0, sepIndex);
				String prefixValue = prefix.substring(sepIndex + 1);
				namespacePrefixes.put(prefixValue, prefixKey);
			}
		}
		init();
	}
	
	String fillTextIndexSearchPattern(String pattern, String x, String text) {
		return pattern.replace("$x", x).replace("$text", text);
	}
	
	private void init() {
		fillNamespacePrefixes();
		retrieveClassesInUse();
		retrieveObjectProperties();
		fillDataTypeMappings();
	}
	
	private void fillDataTypeMappings() {
		dataTypeMappings = new HashMap<String, DataType>();
		dataTypeMappings.put(null, Answer.DataType.String); // "pure" literals (no xsd:string)
		dataTypeMappings.put("http://www.w3.org/2001/XMLSchema#string", Answer.DataType.String);
		dataTypeMappings.put("http://www.w3.org/2001/XMLSchema#date", Answer.DataType.Date);
		dataTypeMappings.put("http://www.w3.org/2001/XMLSchema#integer", Answer.DataType.Number);
		dataTypeMappings.put("http://www.w3.org/2001/XMLSchema#float", Answer.DataType.Number);
		dataTypeMappings.put("http://www.w3.org/2001/XMLSchema#double", Answer.DataType.Number);
		dataTypeMappings.put("http://www.w3.org/2001/XMLSchema#decimal", Answer.DataType.Number);
		dataTypeMappings.put("http://www.w3.org/2001/XMLSchema#boolean", Answer.DataType.Boolean);
	}
	
	public KnowledgeBaseConnector(String sparqlEndpoint, Collection<String> graphURIs, String textIndexSearchPattern) {
		this.sparqlEndpoint = sparqlEndpoint;
		init();
	}
	
	private QueryExecution getQueryExec(String query) {
		numQueries++;
		String from = getFromGraphDeclarations();
		query = query.replace("WHERE", from + "\nWHERE");
		query = getNamespacePrefixDeclarations(query) + "\n" + query;
		System.out.println(query.replace("\n", " "));
//		QueryExecution qexec = null;
//		qexec = QueryExecutionFactory.sparqlService(sparqlEndpoint, query);
		return new QueryEngineHTTP(sparqlEndpoint, query);
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
	
	Set<String> objectProperties;
	private void retrieveObjectProperties() {
		objectProperties = new HashSet<String>();
		String query = "SELECT DISTINCT ?t WHERE { ?t a owl:ObjectProperty }";
		try {
			QueryExecution qexec = getQueryExec(query);
			ResultSet results = qexec.execSelect();
			while (results.hasNext()) {
				QuerySolution sol = results.next();
				Resource objectProperty = sol.getResource("?t");
				objectProperties.add(objectProperty.getURI());
			}
			qexec.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	// type -> number of entities with this type
	Map<Resource, Integer> classesInUse;
	Set<String> classesInUseSet;
	private void retrieveClassesInUse() {
		classesInUseSet = new HashSet<String>();
		classesInUse = new HashMap<Resource, Integer>();
//		String query = "SELECT DISTINCT ?t (COUNT(?s) AS ?count) WHERE {?s a ?t} GROUP BY ?t";
		String query = "SELECT DISTINCT ?t WHERE {?s a ?t}";
		try {
			QueryExecution qexec = getQueryExec(query);
			ResultSet results = qexec.execSelect();
			while (results.hasNext()) {
				QuerySolution sol = results.next();
				Resource classResource = sol.getResource("?t");
//				int count = sol.getLiteral("?count").getInt();
				classesInUse.put(classResource, null);
				classesInUseSet.add(classResource.getURI());
			}
			qexec.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private int getClassCount(Resource classResource) {
		Integer count = classesInUse.get(classResource);
		if (count == null) {
			String query = "SELECT DISTINCT (COUNT(?s) AS ?count) WHERE {?s a <" + classResource.getURI() + ">}";
			try {
				QueryExecution qexec = getQueryExec(query);
				ResultSet results = qexec.execSelect();
				while (results.hasNext()) {
					QuerySolution sol = results.next();
					count = sol.getLiteral("?count").getInt();
					classesInUse.put(classResource, count);
				}
				qexec.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		if (count == null) {
			count = 0;
		}
		return count;
	}
	
	public boolean resourceIsClass(String uri) {
		return classesInUseSet.contains(uri);
	}
	
	/*
	Map<Resource, Collection<String>> classesInUse;
	private void retrieveClassesInUse() {
		classesInUse = new HashMap<>();
		// Selects all classes that are actually in use along with their labels
		String query = "SELECT DISTINCT ?t ?l WHERE { ?t a owl:Class . ?s a ?t . ?t rdfs:label ?l . FILTER(lang(?l) = \"\" || langMatches(lang(?l), \"en\"))}";
		try {
			QueryExecution qexec = getQueryExec(query);
			ResultSet results = qexec.execSelect();
			while (results.hasNext()) {
				QuerySolution sol = results.next();
				Resource classResource = sol.getResource("?t");
//				String classLabel = sol.getLiteral("?l").toString();
				Collection<String> labels = classesInUse.get(classResource);
				if (labels == null) {
					labels = new LinkedList<String>();
					classesInUse.put(classResource, labels);
				}
				labels.add(classLabel);
			}
			qexec.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}*/
	
	List<ComparablePair<MappedString, Float>> getTypeCandidates(Collection<ComparablePair<MappedString, Float>> nameCandidates) {
		List<ComparablePair<MappedString, Float>> types = new LinkedList<ComparablePair<MappedString, Float>>();
		for (ComparablePair<MappedString, Float> c : nameCandidates) {
			String name = formatResourceName(c.key.value);
//			for (Entry<Resource, Collection<String>> typeEntry : classesInUse.entrySet()) {
			for (Resource type : classesInUse.keySet()) {
//				List<ComparablePair<MappedString, Float>> _thisTypeScores = new LinkedList<>();
//				Resource type = typeEntry.getKey();
//				Collection<String> labels = typeEntry.getValue();
				String typeName = formatResourceName(type.getLocalName());
				if (StringUtil.hasPart(typeName, name)) {
					int typeCount = getClassCount(type);
					float score = c.value * name.length() / (float)typeName.length() + 0.01f * (float)Math.log(typeCount);
					List<String> trace = new LinkedList<String>(c.key.trace);
					trace.add(getSPARQLResourceString(type.getURI()) + " (URI match)");
					MappedString mappedType = new MappedString(type.getURI(), trace);
//					_thisTypeScores.add(new ComparablePair<>(mappedType, c.value * score));
					types.add(new ComparablePair<MappedString, Float>(mappedType, score));
//					continue;
				}
				/*
				for (String label : labels) {
					if (StringUtil.hasPart(label, name)) {
						float score = name.length() / (float)label.length();
						List<String> trace = new LinkedList<>(c.key.trace);
						trace.add(getSPARQLResourceString(type.getURI()) + " (label match)");
						MappedString mappedType = new MappedString(type.getURI(), trace);
						_thisTypeScores.add(new ComparablePair<>(mappedType, c.value * score));
//						break;
					}
				}
				// Add only the best score of this type
				if (!_thisTypeScores.isEmpty()) {
					Collections.sort(_thisTypeScores);
					types.add(_thisTypeScores.get(0));
				}*/
			}
		}
		Collections.sort(types);
		return types;
	}
	
	private String getResourceName(String uri) {
		uri = uri.replace('_', ' ');
		if(uri.contains("#")) {
			int sepIndex = uri.lastIndexOf("#");
			return uri.substring(sepIndex + 1);
		} else if (uri.contains("/")) {
			int sepIndex = uri.lastIndexOf("/");
			return uri.substring(sepIndex + 1);
		}
		return uri;
	}
	
	public String getResourceLabel(String uri) {
		String query = "SELECT ?label WHERE { { <" + uri + "> rdfs:label ?label } UNION { <" + uri + "> foaf:name ?label } . FILTER(lang(?label) = \"\" || langMatches(lang(?label), \"en\"))}";
		try {
			QueryExecution qexec = getQueryExec(query);
			ResultSet results = qexec.execSelect();
			for (; results.hasNext(); )
			{
				QuerySolution soln = results.nextSolution();
				return soln.getLiteral("label").getString();
			}
			qexec.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return "(no label)";
	}
	
	List<ComparablePair<MappedString, Float>> getResourceCandidates(String name, int limit) {
		System.out.println("Searching resources... [" + name + "]");
		if (name.contains("#")) {
			int sepIndex = name.indexOf('#');
			name = name.substring(0, sepIndex);
		}
		List<ComparablePair<MappedString, Float>> result = new LinkedList<ComparablePair<MappedString, Float>>();
		{
			String queryString = "SELECT DISTINCT ?subject ?name WHERE { \n"
		                       + "  { ?subject foaf:name ?name . " + fillTextIndexSearchPattern(textIndexSearchPattern, "?name", name) + "} UNION\n"
				               + "  { ?subject rdfs:label ?name . " + fillTextIndexSearchPattern(textIndexSearchPattern, "?name", name) + "} . \n"
							   // Important: text:query must come first for pre-filtering (otherwise it takes much longer)
//						       + "  ?subject text:query('" + name + "' " + limit + ") . \n"
//		                       + "  ?subject a owl:Thing . \n"
							   + "  { ?subject foaf:name ?name . FILTER(lang(?name) = \"\" || langMatches(lang(?name), \"en\")) }\n"
                               + "  UNION\n"
                               + "  { ?subject rdfs:label ?name . FILTER(lang(?name) = \"\" || langMatches(lang(?name), \"en\")) }\n"
				               + "} \n"
					           + "LIMIT " + limit;
			try {
				QueryExecution qexec = getQueryExec(queryString);
				ResultSet results = qexec.execSelect();
				for (; results.hasNext(); )
				{
					QuerySolution soln = results.nextSolution();
					String rName = soln.getLiteral("name").getString();
					Resource r = soln.getResource("subject");
					if (r != null) {
						String shortUri = getSPARQLResourceString(r.getURI());
						List<String> trace = new LinkedList<String>();
						trace.add(name);
						float labelScore = (float)StringUtil.longestCommonSubstring(name, rName).length() / rName.length();
						String rNameFromURI = getResourceName(r.getURI());
						float resourceNameScore = (float)StringUtil.longestCommonSubstring(name, rNameFromURI).length() / rNameFromURI.length();
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
				qexec.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		Collections.sort(result);
		System.out.println("Done searching resources. Results: " + result);
		return result;
	}
	
	private static int numQueries = 0;
	
	public static class Answer {
		public enum DataType {
			Resource,
			String,
			Number,
			Date,
			Boolean
		}
		public DataType dataType;
		public String value;
		public String label; // Only used for resources
	}

	/**
	 * Executes the specified SPARQL query and returns the result(s) with
	 * respect to the focus variable
	 */
	public Collection<Answer> query(Query query) {
		final int RESULT_LIMIT = 1000;
		String queryStr = queryToSPARQLWithLabel(query, RESULT_LIMIT);
		Collection<Answer> res = new LinkedList<Answer>();
		
		try {
			QueryExecution qexec = getQueryExec(queryStr);
			ResultSet results = qexec.execSelect();
			for (; results.hasNext(); )
			{
				Answer a = new Answer();
				QuerySolution soln = results.nextSolution();
				RDFNode var = soln.get(query.focusVar.name);
				if (var != null) {
					if (var.isResource()) {
						Resource r = var.asResource();
//						a.value = URLDecoder.decode(r.getURI(), "UTF-8");
						a.value = r.getURI();
						a.dataType = Answer.DataType.Resource;
						RDFNode label = soln.get("_label");
						if (label != null) {
							a.label = ((Literal)label).getValue().toString();
						}
					} else if (var.isLiteral()) {
						Literal l = var.asLiteral();
						a.value = l.getValue().toString();
						a.dataType = dataTypeMappings.get(l.getDatatypeURI());
					}
					
					res.add(a);
				}
			}
			qexec.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return res;
	}
	
	public String queryToSPARQLFull(Query q) {
		String queryStr = queryToSPARQL(q);

		String from = getFromGraphDeclarations();
		queryStr = queryStr.replace("WHERE", from + "\nWHERE");
		queryStr = getNamespacePrefixDeclarations(queryStr) + "\n" + queryStr;
		
		return queryStr;
	}
	
	public String queryToSPARQLWithLabel(Query q, int limit) {
		String queryStr = "SELECT DISTINCT " + q.focusVar.sparqlString() + " (SAMPLE(?string) as ?_label) WHERE {\n";
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
		for (Variable var : q.vars.values()) {
			queryStr += "   OPTIONAL { ";
			queryStr += "{ " + var.sparqlString() + " rdfs:label ?string . FILTER (lang(?string)=\"en\") } UNION";
			queryStr += "{ " + var.sparqlString() + " foaf:name ?string . FILTER (lang(?string)=\"en\") }";
			queryStr += "} .\n";
		}

		queryStr += "} GROUP BY " + q.focusVar.sparqlString() + " LIMIT " + limit;
		return queryStr;
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
	 * Executes the specified SPARQL query and returns the first variable
	 * not listed in varsToIgnore for each row (unless the row contains only
	 * one variable)
	 */
	public Collection<String> query(String queryString, Set<String> varsToIgnore) {
		Collection<String> res = new LinkedList<String>();

		try {
			QueryExecution qexec = getQueryExec(queryString);
			ResultSet results = qexec.execSelect();
			for (; results.hasNext(); )
			{
				QuerySolution sol = results.nextSolution();
				Iterator<String> varNames = sol.varNames();
				while (varNames.hasNext()) {
					String varName = varNames.next();
					String val = null;
					if (!varsToIgnore.contains(varName) || !varNames.hasNext()) {
						RDFNode var = sol.get(varName);
						if (var.isLiteral()) {
							Literal l = (Literal)var;
							val = l.getValue().toString();
						} else if (var.isResource()) {
							Resource r = (Resource)var;
							val = r.getURI();
						}
						res.add(val);
						break;
					}
				}
			}
			qexec.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return res;
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
		
		try {
			QueryExecution qexec = getQueryExec(queryString);
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
	
	private static Map<String, Set<String>> uriTypes = new HashMap<String, Set<String>>();
	synchronized Set<String> getResourceTypes(String uri) {
		Set<String> types = uriTypes.get(uri);
		if (types == null) {
			types = new HashSet<String>();
			uriTypes.put(uri, types);
			QueryExecution qexec;
			ResultSet typeResults;
			qexec = getQueryExec("SELECT ?t WHERE { <" + uri + "> a ?t }");
			typeResults = qexec.execSelect();
					
			while (typeResults.hasNext()) {
				QuerySolution sol = typeResults.next();
				types.add(sol.getResource("t").getURI());
			}
			qexec.close();
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
		boolean subjectHasClassConstraint = subjectTC != null && subjectTC.basicType == BasicType.Resource;
		boolean objectHasClassConstraint = objectTC != null && objectTC.basicType == BasicType.Resource;
		// This doesn't make much sense (we need at least one constraint, otherwise we'll query the entire DB)
		if (subjectURI == null && objectURI == null && !subjectHasClassConstraint && !objectHasClassConstraint) {
			return new LinkedList<ComparablePair<MappedString, Float>>();
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
		QueryExecution qexec = null;
		ResultSet propPreCandidates;
		try {
			qexec = getQueryExec(query);
			propPreCandidates = qexec.execSelect();
		} catch (Exception e) {
			System.err.println("Error while executing query: " + query);
			return new LinkedList<ComparablePair<MappedString, Float>>();
		}
				
		List<ComparablePair<MappedString, Float>> result = new LinkedList<ComparablePair<MappedString, Float>>();
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
			boolean pIsObjectProperty = objectProperties.contains(pUri);
			float propertyTypeScore = 1.0f;
			// We know that the object is a resource, exclude all non-object properties in advance (e.g. DBPedia properties)
			if (objectURI != null && !pIsObjectProperty) {
				propertyTypeScore = 0.1f;
			}
			// Slightly prefer object properties over non-object properties in other cases
			else if (pIsObjectProperty) {
				propertyTypeScore = 1.01f;
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
				result.add(new ComparablePair<MappedString, Float>(mappedPUri, propertyTypeScore + countScoreBonus));
			}
		}
		if (qexec != null) {
			qexec.close();
		}
		Collections.sort(result);
		int numResults = Math.min(result.size(), 10);
		
		return result.subList(0, numResults);
	}
	
	public void close() {
		System.out.println("Closing KB Connector. Number of queries: " + numQueries);
	}
}
