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
import org.apache.log4j.Logger;

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
 * Interface for PAL to talk to SPARQL endpoints. A lot of the PAL "business logic" sits here.
 */
public class KnowledgeBaseConnector {
	
	Logger log = Logger.getLogger("de.tudarmstadt.lt.pal");
	
	String sparqlEndpoint;
	Collection<String> graphUris;
	String textIndexSearchPattern;
	
	/**
	 * Checks if a local name qualifies for being abbreviated with one of the available prefixes
	 * (e.g. "dbpedia:Dan_Brown"). Some URIs do not qualify because they contain unescaped
	 * characters listed below (e.g. an apostrophe, that is e.g.
	 * "http://dbpedia.org/resource/Dan_Brown's_friend"). URIs that do not qualify will be written
	 * out in full and enclosed in <...>
	 */
	private boolean checkIfLocalUriNameIsValidSPARQL(String localName) {
		String invalidPattern = ".*\\.|.*[\\&\\/\\(\\),%#':].*";
		return !localName.matches(invalidPattern);
	}
	
	/**
	 * Returns a short representation of the resource's URI using known namespace prefixes.
	 * Uses <code>?varName</code> instead if resource is null
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
					log.debug("URI contains bad local name: " + uri);
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

	/**
	 * @see KnowledgeBaseConnector#namespacePrefixes
	 */
	private void fillNamespacePrefixes() {
		namespacePrefixes.put("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "rdf");
		namespacePrefixes.put("http://www.w3.org/2000/01/rdf-schema#", "rdfs");
		namespacePrefixes.put("http://www.w3.org/2001/XMLSchema#", "xsd");
		namespacePrefixes.put("http://www.w3.org/2002/07/owl#", "owl");
		
		namespacePrefixes.put("http://dbpedia.org/ontology/", "dbpedia-owl");
		namespacePrefixes.put("http://dbpedia.org/property/", "dbpprop");
		namespacePrefixes.put("http://dbpedia.org/resource/", "dbpedia");
		namespacePrefixes.put("http://dbpedia.org/class/yago/", "yago");
		namespacePrefixes.put("http://xmlns.com/foaf/0.1/", "foaf");
		namespacePrefixes.put("http://jena.apache.org/text#", "text");
		namespacePrefixes.put("http://purl.org/ontology/bibo/", "bibo");
		namespacePrefixes.put("http://purl.org/ontology/mo/", "mo");
		namespacePrefixes.put("http://schema.org/", "schema");
		namespacePrefixes.put("http://purl.org/dc/terms/", "dc");
	}
	
	/**
	 * Lists namespaces that contain only RDF "meta" data (e.g. rdf:type or rdf:subClass).
	 * Namespaces in this blacklist will be ignored when searching for property candidates.
	 */
	Set<String> namespaceBlacklist = new HashSet<String>();

	/**
	 * @see KnowledgeBaseConnector#namespaceBlacklist
	 */
	private void fillNamespaceBlacklist() {
		namespaceBlacklist.add("http://www.w3.org/1999/02/22-rdf-syntax-ns#");
		namespaceBlacklist.add("http://www.w3.org/2000/01/rdf-schema#");
		namespaceBlacklist.add("http://www.w3.org/2001/XMLSchema#");
		namespaceBlacklist.add("http://www.w3.org/2002/07/owl#");
	}
	
	/**
	 * @see KnowledgeBaseConnector#namespaceBlacklist
	 */
	private boolean uriIsBlacklisted(String uri) {
		for (String blacklistedNS : namespaceBlacklist) {
			if (uri.startsWith(blacklistedNS)) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Constructs the SPARQL prefix declarations from <code>namespacePrefixes</code> map
	 */
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

	/**
	 * Constructs the SPARQL "FROM" declarations from <code>graphUris</code> map
	 */
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
	
	/**
	 * Initializes a new KnowledgeBaseConnector using the specified .properties file
	 */
	public KnowledgeBaseConnector(String file) throws IOException {
		this(new FileInputStream(file));
	}

	/**
	 * Initializes a new KnowledgeBaseConnector using the specified input stream of .properties file
	 */
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
	
	/**
	 * Initialized a new KnowledgeBaseConnector using the explicitly specified configuration
	 */
	public KnowledgeBaseConnector(String sparqlEndpoint, Collection<String> graphURIs, String textIndexSearchPattern) {
		this.sparqlEndpoint = sparqlEndpoint;
		init();
	}
	
	/**
	 * Fills out the configuration-provided text search pattern
	 * Example: $x <bif:contains> "'$text'" --> ?label <bif:contains> "'some text'"
	 */
	String fillTextIndexSearchPattern(String pattern, String x, String text) {
		return pattern.replace("$x", x).replace("$text", text);
	}
	
	private void init() {
		fillNamespacePrefixes();
		fillNamespaceBlacklist();
		retrieveClassesInUse();
		retrieveObjectProperties();
		fillDataTypeMappings();
	}

	/**
	 * Maps XML schema data types to an Answer.DataType
	 */
	Map<String, Answer.DataType> dataTypeMappings;
	
	/**
	 * @see KnowledgeBaseConnector#dataTypeMappings
	 */
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
	
	private QueryExecution getQueryExec(String query) {
		numQueries++;
		String from = getFromGraphDeclarations();
		query = query.replace("WHERE", from + "\nWHERE");
		query = getNamespacePrefixDeclarations(query) + "\n" + query;
		log.debug("SPARQL query: " + query.replace("\n", " "));
		return new QueryEngineHTTP(sparqlEndpoint, query);
	}
	
	/**
	 * A set of all owl:ObjectProperties provided by the SPARQL endpoint
	 */
	Set<String> objectProperties;
	
	/**
	 * @see KnowledgeBaseConnector#objectProperties
	 */
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
			log.info("Found " + objectProperties.size() + " object properties for endpoint " + sparqlEndpoint);
		} catch (Exception e) {
			log.error("Error retrieving list of object properties: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	/**
	 *  type URI -> number of entities with this type
	 */
	Map<String, Integer> classesInUse;
	
	/**
	 * @see KnowledgeBaseConnector#classesInUse
	 */
	Set<String> classesInUseSet;
	
	/**
	 * @see KnowledgeBaseConnector#classesInUse
	 */
	private void retrieveClassesInUse() {
		classesInUseSet = new HashSet<String>();
		classesInUse = new HashMap<String, Integer>();
		String query = "SELECT ?t (COUNT(?t) as ?count)  WHERE { ?s a ?t } GROUP BY ?t ORDER BY DESC(?count) LIMIT 10000";
		try {
			QueryExecution qexec = getQueryExec(query);
			ResultSet results = qexec.execSelect();
			while (results.hasNext()) {
				QuerySolution sol = results.next();
				RDFNode t = sol.get("?t");
				if (t.isResource()) {
					String classResourceUri = t.asResource().getURI();
					int count = sol.getLiteral("?count").getInt();
					classesInUse.put(classResourceUri, count);
					classesInUseSet.add(classResourceUri);
				} else {
					log.warn("Query \"" + query + "\" for endpoint " + sparqlEndpoint + " returned a non-resource ?t: " + t);
				}
			}
			log.info("Found " + classesInUseSet.size() + " classes in use for endpoint " + sparqlEndpoint);
			qexec.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	/*
	private int getClassCount(String rUri) {
		String query = "SELECT DISTINCT (COUNT(?s) AS ?count) WHERE {?s a <" + rUri + ">}";
		try {
			QueryExecution qexec = getQueryExec(query);
			ResultSet results = qexec.execSelect();
			while (results.hasNext()) {
				QuerySolution sol = results.next();
				return sol.getLiteral("?count").getInt();
			}
			qexec.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return 0;
	}*/

	/**
	 * @see KnowledgeBaseConnector#classesInUse
	 */
	public boolean resourceIsClass(String uri) {
		return classesInUseSet.contains(uri);
	}
	
	/**
	 * Returns either the best-matching type for the name candidates or null
	 */
	ComparablePair<MappedString, Float> getType(Collection<ComparablePair<MappedString, Float>> nameCandidates) {
		List<ComparablePair<MappedString, Float>> candidates = getTypeCandidates(nameCandidates);
		float bestScore = 0.0f;
		if (candidates.size() > 0) {
			// list is sorted in descending order
			bestScore = candidates.get(0).value;
			// Create sublist of equally-weighted top candidates (usually just one or two)
			int i;
			for (i = 0; i < candidates.size(); i++) {
				ComparablePair<MappedString, Float> c = candidates.get(i);
				if (c.value < bestScore) {
					break;
				}
				int typeCount = classesInUse.get(c.key.value);
				c.value += 0.01f * (float)Math.log(typeCount);
			}
			List<ComparablePair<MappedString, Float>> candidatesCut = candidates.subList(0, i);
			Collections.sort(candidatesCut);
			return candidatesCut.get(0);
		}
		return null;
	}
	
	/**
	 * Constructs a list of type candidates from {@link KnowledgeBaseConnector#classesInUse} that
	 * match the name candidates
	 */
	private List<ComparablePair<MappedString, Float>> getTypeCandidates(Collection<ComparablePair<MappedString, Float>> nameCandidates) {
		log.debug("Searching types for name candidates: " + nameCandidates);
		List<ComparablePair<MappedString, Float>> types = new LinkedList<ComparablePair<MappedString, Float>>();
		for (ComparablePair<MappedString, Float> c : nameCandidates) {
			String name = formatResourceName(c.key.value);
			for (String typeUri : classesInUse.keySet()) {
				String typeName = formatResourceName(getLocalName(typeUri));
				if (StringUtil.hasPart(typeName, name)) {
					float score = c.value * name.length() / (float)typeName.length();
					List<String> trace = new LinkedList<String>(c.key.trace);
					trace.add(getSPARQLResourceString(typeUri) + " (URI match)");
					MappedString mappedType = new MappedString(typeUri, trace);
					types.add(new ComparablePair<MappedString, Float>(mappedType, score));
				}
			}
		}
		Collections.sort(types);
		log.debug("Type candidates: " + types);
		return types;
	}
	
	/**
	 * Formats typical ontology type names like "EuropeanCapital123" in a more natural-language
	 * conformant format ("european capital")
	 */
	private String formatResourceName(String name) {
		StringBuilder formattedName = new StringBuilder();
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (i > 0 && (Character.isUpperCase(c) || c == '_' || c == '-')) {
				formattedName.append(' ');
			}
			if (Character.isLetter(c)) {
				formattedName.append(Character.toLowerCase(c));
			}
		}
		return formattedName.toString();
	}
	
	/**
	 * Extracts the local name of a URI, e.g.
	 * dbpedia.org/resource/Dan_Brown -> Dan_Brown
	 * http://www.w3.org/2001/XMLSchema#string -> string
	 */
	private String getLocalName(String uri) {
		char sep = '/';
		if (uri.contains("#")) {
			sep = '#';
		}
		int index = uri.lastIndexOf(sep);
		return uri.substring(index + 1);
	}
	
	/**
	 * Convenience method that formats the local name extracted from the URI
	 * 
	 * @see KnowledgeBaseConnector#formatResourceName(String)
	 * @see KnowledgeBaseConnector#getLocalName(String)
	 */
	private String getResourceName(String uri) {
		return formatResourceName(getLocalName(uri));
	}
	
	/**
	 * Cache for resource candidates
	 * 
	 * @see KnowledgeBaseConnector#getResourceCandidates(String, int)
	 */
	Map<String, List<ComparablePair<MappedString, Float>>> resourceCandidateCache = new HashMap<String, List<ComparablePair<MappedString, Float>>>();
	
	/**
	 * Returns a list of resources matching the given <code>name</code>, limited to
	 * <code>limit</code> results
	 */
	List<ComparablePair<MappedString, Float>> getResourceCandidates(String name, int limit) {
		List<ComparablePair<MappedString, Float>> candidates = resourceCandidateCache.get(name);
		if (candidates == null) {
			candidates = new LinkedList<ComparablePair<MappedString, Float>>();
			resourceCandidateCache.put(name, candidates);
			log.debug("Searching resources... [" + name + "]");
			if (name.contains("#")) {
				int sepIndex = name.indexOf('#');
				name = name.substring(0, sepIndex);
			}
			{
				String queryString = "SELECT DISTINCT ?subject ?name WHERE { \n"
			                       + "  { ?subject foaf:name ?name . " + fillTextIndexSearchPattern(textIndexSearchPattern, "?name", name) + "} UNION\n"
					               + "  { ?subject rdfs:label ?name . " + fillTextIndexSearchPattern(textIndexSearchPattern, "?name", name) + "} . \n"
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
							float labelScore = rName.isEmpty() ? 0 : (float)StringUtil.longestCommonSubstring(name, rName).length() / rName.length();
							String rNameFromURI = getResourceName(r.getURI());
							float resourceNameScore = rNameFromURI.isEmpty() ? 0 : (float)StringUtil.longestCommonSubstring(name, rNameFromURI).length() / rNameFromURI.length();
							float comboScore = labelScore * 0.5f + resourceNameScore * 0.5f;
							// Assign penalty for inexact matches
							float inexactMatchPenalty = 0.5f;
							if (comboScore < 1.0f) {
								comboScore = comboScore * inexactMatchPenalty;
								trace.add(shortUri + " (partial match)");
							} else {
								trace.add(shortUri + " (exact match)");
							}
							candidates.add(new ComparablePair<MappedString, Float>(new MappedString(shortUri, trace), comboScore));
						}
					}
					qexec.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
	
			Collections.sort(candidates);
			log.debug("Done searching resources. Results: " + candidates);
		}
		return candidates;
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
			log.error("Error executing SPARQL query \"" + queryStr.replace("\n", " ") + "\": " + e.getMessage());
			e.printStackTrace();
		}
		return res;
	}
	
	/**
	 * Constructs a SPARQL query from the specified query, adding PREFIX and FROM declarations
	 */
	public String queryToSPARQLFull(Query q) {
		String queryStr = queryToSPARQL(q);

		String from = getFromGraphDeclarations();
		queryStr = queryStr.replace("WHERE", from + "\nWHERE");
		queryStr = getNamespacePrefixDeclarations(queryStr) + "\n" + queryStr;
		
		return queryStr;
	}

	/**
	 * Constructs a SPARQL query from the specified query, retrieving resource labels where possible
	 */
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
			queryStr += "{ " + var.sparqlString() + " rdfs:label ?string . FILTER (lang(?string)=\"en\" || lang(?string)=\"\") } UNION";
			queryStr += "{ " + var.sparqlString() + " foaf:name ?string . FILTER (lang(?string)=\"en\" || lang(?string)=\"\") }";
			queryStr += "} .\n";
		}

		queryStr += "} GROUP BY " + q.focusVar.sparqlString() + " LIMIT " + limit;
		return queryStr;
	}

	/**
	 * Constructs a SPARQL query from the specified query
	 */
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
					res.add(varString);
				}
			}
			qexec.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return res;
	}
	
	String getTypeConstraintSPARQLString(TypeConstraint tc, String varName) {
		if (tc == null) {
			return "";
		} else if (tc.basicType == BasicType.Resource) {
			return "   ?" + varName + " a " + getSPARQLResourceString(tc.typeURI.value) + " . \n";
		} else/* if (tc.basicType == BasicType.Literal)*/ {
			String res = "   FILTER(";
			if (tc.typeURI != null && tc.typeURI.value.equals("_number_")) {
				res += "ISNUMERIC(?" + varName + ")";
			} else if (tc.typeURI != null) {
				res += "DATATYPE(?" + varName + ") = " + getSPARQLResourceString(tc.typeURI.value);
			} else {
				res += "ISLITERAL(?" + varName + ")";
			}
			res += ") . \n";
			return res;
		}
	}
	
	class PropertyCandidate {
		String uri;
		int count;
		@Override public String toString() { return uri + " (" + count + ")"; } 
	}
	Map<List<Object>, Collection<PropertyCandidate>> propCandidateCache = new HashMap<List<Object>, Collection<PropertyCandidate>>();
	
	/**
	 * Retrieves a list of property candidates for the given nameCandidates, resources and type
	 * constraints
	 */
	Collection<ComparablePair<MappedString, Float>> getPropertyCandidates(List<ComparablePair<MappedString, Float>> nameCandidates, String subjectURI, String objectURI,
			                                                          TypeConstraint subjectTC, TypeConstraint objectTC) {
		// Literals on the subject side don't make sense
		if (subjectTC != null && subjectTC.basicType == BasicType.Literal) {
			return new LinkedList<ComparablePair<MappedString, Float>>();
		}
		boolean subjectHasClassConstraint = subjectTC != null && subjectTC.basicType == BasicType.Resource;
		boolean objectHasClassConstraint = objectTC != null && objectTC.basicType == BasicType.Resource;
		// This doesn't make much sense (we need at least one constraint, otherwise we'll query the entire DB)
		if (subjectURI == null && objectURI == null && !subjectHasClassConstraint && !objectHasClassConstraint) {
			return new LinkedList<ComparablePair<MappedString, Float>>();
		}
		List<Object> cacheKey = Arrays.asList(subjectURI, objectURI, subjectTC, objectTC);
		boolean useCountScore = true;//nameCandidates == null;// && subjectIsVar ^ objectIsVar;
		
		Collection<PropertyCandidate> propCandidates = propCandidateCache.get(cacheKey);
		if (propCandidates == null) {
			propCandidates = new LinkedList<PropertyCandidate>();
			propCandidateCache.put(cacheKey, propCandidates);
			String querySubject = subjectURI == null ? "?s" : subjectURI;
			String queryObject = objectURI == null ? "?o" : objectURI;
			String query = "SELECT ?p ";
			// Count number of property "connections" if we have no clue about the property
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
				log.error("Error while executing query: \"" + query + "\": " + e.getMessage());
				return new LinkedList<ComparablePair<MappedString, Float>>();
			}

			while (propPreCandidates.hasNext()) {
				QuerySolution sol = propPreCandidates.next();
				Resource pRes = sol.getResource("p");
				// Probably the result set is empty (but has one null entry with count 0)
				if (pRes == null) {
					break;
				}
				String pUri = pRes.getURI();
				if (uriIsBlacklisted(pUri)) {
					continue;
				}
				PropertyCandidate pc = new PropertyCandidate();
				pc.uri = pUri;
				pc.count = sol.getLiteral("count").getInt();
				propCandidates.add(pc);
			}

			if (qexec != null) {
				qexec.close();
			}
		}

		List<ComparablePair<MappedString, Float>> result = new LinkedList<ComparablePair<MappedString, Float>>();
		for (PropertyCandidate pc : propCandidates) {
			String pUri = pc.uri;
			int count = pc.count;
			String pUriShortForm = getSPARQLResourceString(pUri);
			// Assign a very small bonus for higher number of connections
			// -> should only make a difference for near-tie situations
			float countScoreBonus = 0.0f;
			if (useCountScore) {
				// Use logarithm to avoid an untyped property with 100 items
				// to be scored as high as a typed property with 10 items
				countScoreBonus = 0.00001f * (float)Math.log(1 + count);
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
			
			String pName = formatResourceName(getLocalName(pUri));
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
		Collections.sort(result);
		int numResults = Math.min(result.size(), 10);
		
		result = result.subList(0, numResults);
		log.debug("Property candidates: " + result);
		return result;
	}
	
	public void close() {
		log.info("Closing KB Connector. Number of queries: " + numQueries);
	}
}
