package de.tudarmstadt.lt.pal;
import java.net.URLDecoder;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.hp.hpl.jena.ontology.OntClass;
import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.ontology.OntModelSpec;
import com.hp.hpl.jena.ontology.OntProperty;
import com.hp.hpl.jena.ontology.OntResource;
import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ReadWrite;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.tdb.TDBFactory;

import de.tudarmstadt.lt.pal.SPARQLTriple.TypeConstraint;
import de.tudarmstadt.lt.pal.SPARQLTriple.TypeConstraint.BasicType;
import de.tudarmstadt.lt.pal.util.ComparablePair;

/**
 * Basic interface to ontology and triple store
 */
public class KnowledgeBaseConnector {
	
	Model model;
	OntModel ontModel;
//	InfModel infModel;
	Dataset data;
	String sparqlEndpoint;
	
	/**
	 * Returns a short representation of the resource's URI using
	 * known namespace prefixes. Uses <code>?varName</code> instead
	 * if resource is null
	 */
	public String getSPARQLResourceString(Resource r) {
		String prefix = namespacePrefixes.get(r.getNameSpace());
		if (prefix != null) {
			return prefix + ":" + r.getLocalName();
		}
	
		return "<" + r.getURI() + ">";
	}
	public Resource getResourceFromSPARQLResourceString(String sparqlString) {
		String uri = sparqlString;
		for (Entry<String, String> prefix : namespacePrefixes.entrySet()) {
			if (sparqlString.startsWith(prefix.getValue())) {
				uri = prefix.getKey() + sparqlString.substring(prefix.getValue().length());
				break;
			}
		}
		return getResource(uri);
	}
	
	/**
	 * Returns a short representation of the resource's URI using
	 * known namespace prefixes.
	 */
	public String getSPARQLResourceString(String uri) {
		// Attempt to shorten URI using known prefixes
		if (uri.startsWith("http://")) {
			return getSPARQLResourceString(getResource(uri));
		}
		return uri;
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
	/*public KnowledgeBaseConnector() {
		sparqlEndpoint = "http://localhost:8890/sparql/";
		VirtDataset data = new VirtDataset("jdbc:virtuoso://localhost:1111/charset=UTF-8/", "dba", "dba");
		data.begin(ReadWrite.READ);
		model = data.getNamedModel("http://dbpedia.org");
		ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM, model);
		ontModel.prepare();
		Iterator<OntProperty> it = ontModel.listOntProperties();
		Graph g = ontModel.getGraph();
		while (it.hasNext()) {
			OntProperty p = it.next();
			System.out.println(p);
			Resource r3 = ontModel.getResource(p.getURI());
			boolean c = ontModel.containsResource(r3);
			OntResource _r3 = r3.as(OntResource.class);
			Resource r1 = ontModel.getOntResource(p.getURI());
			Resource r2 = ontModel.getOntResource(p);
		}
		fillNamespacePrefixes();
	}*/
	
	/**
	 * Constructor to connect to local Fuseki server
	 */
	
	public KnowledgeBaseConnector() {
//		sparqlEndpoint = "http://localhost:3030/ds/query";
		sparqlEndpoint = "http://localhost:8001/sparql/"; // nginx cache
//		sparqlEndpoint = "http://localhost:8890/sparql/"; // virtuoso
		data = TDBFactory.createDataset("/Users/jsimon/No-Backup/dbpedia37/tdb");
//		data = DatasetFactory.assemble(
//			    "/Users/jsimon/No-Backup/dbpedia37/dbpedia37-fuseki.ttl", 
//			    "http://localhost/dbpedia37#text_dataset") ;
		data.begin(ReadWrite.READ);
		model = data.getDefaultModel();
		ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM, model);
		
//		ontModel.prepare();
//		Iterator<OntClass> it = ontModel.listClasses();
//		while (it.hasNext()) {
//			System.out.println(it.next());
//		}
		fillNamespacePrefixes();
		retrieveClassesInUse();
	}
	/*
	public KnowledgeBaseConnector(String tdbDir) {
		// Create a TDB-backed dataset
		String modelDir = tdbDir;
		data = TDBFactory.createDataset(modelDir);
		data.begin(ReadWrite.READ);
		// Get model inside the transaction
		model = data.getDefaultModel();
		sparqlEndpoint = null;
		ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM, model);
//		infModel = ModelFactory.createInfModel(ReasonerRegistry.getOWLMicroReasoner(), model);
		fillNamespacePrefixes();
	}
	
	public KnowledgeBaseConnector(String tdbDir, String sparqlEndpoint) {
		this(tdbDir);
		this.sparqlEndpoint = sparqlEndpoint;
	}*/
	
	private QueryExecution getQueryExec(String query) {
		numQueries++;
		query = getNamespacePrefixDeclarations() + "\n" + query;
		System.out.println(query.replaceAll("\n", " "));
		QueryExecution qexec;
		if (sparqlEndpoint != null) {
			qexec = QueryExecutionFactory.sparqlService(sparqlEndpoint, query);
		} else {
			qexec = QueryExecutionFactory.create(query, model);
		}
		return qexec;
	}
	
	/**
	 * Returns the property with the specified URI
	 */
	Property getProperty(String uri) {
		return model.getProperty(uri);
	}
	
	/**
	 * Returns the ontology property with the specified URI
	 */
	OntProperty getOntProperty(String uri) {
		return ontModel.getOntProperty(uri);
	}

	/**
	 * Returns the resource with the specified URI
	 */
	Resource getResource(String uri) {
		return model.getResource(uri);
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
	
	List<ComparablePair<Resource, Float>> getTypeCandidates(Collection<ComparablePair<String, Float>> nameCandidates) {
		List<ComparablePair<Resource, Float>> types = new LinkedList<>();
		for (ComparablePair<String, Float> c : nameCandidates) {
			String name = formatResourceName(c.key);
			for (Entry<Resource, Collection<String>> typeEntry : classesInUse.entrySet()) {
				Resource type = typeEntry.getKey();
				Collection<String> labels = typeEntry.getValue();
				String typeName = formatResourceName(type.getLocalName());
				if (typeName.equals(name)) {
					types.add(new ComparablePair<Resource, Float>(type, c.value));
					continue;
				}
	
				for (String label : labels) {
					if (label.toLowerCase().equals(name)) {
						types.add(new ComparablePair<Resource, Float>(type, c.value));
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
	
	List<ComparablePair<Resource, Float>> getResourceCandidates(String name, int limit) {
		System.out.println("Searching resources... [" + name + "]");
		if (name.contains("#")) {
			int sepIndex = name.indexOf('#');
			name = name.substring(0, sepIndex);
		}
		List<ComparablePair<Resource, Float>> result = new LinkedList<ComparablePair<Resource, Float>>();
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
						float labelScore = (float)name.length() / rName.length();
						String rNameFromURI = getResourceName(r.getURI());
						float resourceNameScore = 1.0f - (float)Math.abs(rNameFromURI.length() - name.length()) / rNameFromURI.length();
						float comboScore = labelScore * 0.5f + resourceNameScore * 0.5f;
						// Assign penalty for inexact matches
						float inexactMatchPenalty = 0.5f;
						if (comboScore < 1.0f) {
							comboScore = comboScore * inexactMatchPenalty;
						}
						result.add(new ComparablePair<Resource, Float>(r, comboScore));
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
	public Collection<String> query(String queryString, String focusVariable) {
		Collection<String> res = new LinkedList<String>();
		
		// Invalid query check
		if (queryString == null || queryString.contains("<null>")) {
			return res;
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
					System.out.println(varString);
					res.add(varString);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally { qexec.close(); }
		return res;
	}
	
	/**
	 * Returns either an <code>OntClass</code> or a <code>DataRange</code> representation of the
	 * given resource or null if no such representation exists.
	 */
	OntResource getOntResource(Resource r) {
		if (r != null) {
			OntResource ontR = ontModel.getOntResource(r);
			if (ontR.isClass() || ontR.isDataRange()) {
				return ontR;
			}
		}
		return null;
	}
	
	String getTypeConstraintSPARQLString(TypeConstraint tc, String varName) {
		if (tc == null) {
			return "";
		} else if (tc.basicType == BasicType.Resource) {
			return "?" + varName + " a " + getSPARQLResourceString(tc.typeURI) + " . ";
		} else/* if (tc.basicType == BasicType.Literal)*/ {
			String res = "FILTER(ISLITERAL(?" + varName + ")";
			if (tc.typeURI != null) {
				res += " && DATATYPE(?" + varName + ") = " + getSPARQLResourceString(tc.typeURI);
			}
			res += ") . ";
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
	 * Check if a type <code>toCheck</code> is either equal to or (if type is a class) a subclass of <code>type</code>
	 * 
	 * @return True if either <code>type</code> or <code>toCheck</code> is null or
	 * if <code>toCheck</code> is (either directly or transitively) equal to <code>type</code>
	 */
	boolean checkTypeConstraint(OntResource type, OntResource toCheck) {
		if (type == null || toCheck == null) {
			return true;
		}
		// Class constraint (e.g. dbpedia-owl:Agent)
		if (type.isClass()) {
			OntClass ontClass = type.asClass();
			return ontClass.hasSubClass(toCheck) || ontClass.equals(toCheck);
			// Data range constraint (e.g. xsd:integer)
		} else if (type.isDataRange()) {
			return type.equals(toCheck);
		} else {
			return true;
		}
	}
	
	/**
	 * nameCandidates must be sorted in ascending order
	 */
	Collection<ComparablePair<Property, Float>> getPropertyCandidates(List<ComparablePair<String, Float>> nameCandidates, Resource subject, Resource object,
			                                                          TypeConstraint subjectTC, TypeConstraint objectTC) {
		// This doesn't make much sense (we need at least one constraint, otherwise we'll query the entire DB)
		if (subject == null && object == null && subjectTC == null && objectTC == null) {
			return new LinkedList<>();
		}
		String querySubject = subject == null ? "?s" : getSPARQLResourceString(subject);
		String queryObject = object == null ? "?o" : getSPARQLResourceString(object);
		String query = "SELECT ?p ";
		// Count number of property "connections" if we have no clue about the property
		boolean useCountScore = true;//nameCandidates == null;// && subjectIsVar ^ objectIsVar;
		if (useCountScore) {
			String countVar = subject == null ? "?s" : "?o";
			query += "(COUNT(" + countVar + ") AS ?count)";
		}
		query += " WHERE { ";
		// We know that the object is a resource, exclude all non-object properties in advance (e.g. DBPedia properties)
//		if (object != null) {
//			query += "?p a owl:ObjectProperty . ";
//		}
		// This will take too long, append a lucene text search (though this has problems in some cases, e.g. when the prefix is too short)
//		if (subject == null && object == null) {
//			int numCandidates = 10;
//			Collection<ComparablePair<String, Float>> reducedNamedCandidates = nameCandidates.subList(0, Math.min(numCandidates, nameCandidates.size()));
//			query += "?p text:query " + getLuceneQueryString(reducedNamedCandidates) + " . ";
//			query += "?p rdfs:label ?l . ?l <bif:contains> " + getLuceneQueryString(reducedNamedCandidates) + " . ";
//		}
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
			System.err.println("Error while executing query: " + e.getMessage());
			return new LinkedList<>();
		}
				
		List<ComparablePair<Property, Float>> result = new LinkedList<ComparablePair<Property, Float>>();
		while (propPreCandidates.hasNext()) {
			QuerySolution sol = propPreCandidates.next();
			Resource pRes = sol.getResource("p");
			// Probably the result set is empty (but has one null entry with count 0)
			if (pRes == null) {
				break;
			}
			// Assign a very small bonus for higher number of connections
			// -> should only make a difference for near-tie situations
			float countScoreBonus = 0.0f;
			if (useCountScore) {
				// Use logarithm to avoid an untyped property with 100 items
				// to be scored as high as a typed property with 10 items
				countScoreBonus = 0.00001f * (float)Math.log(1 + sol.getLiteral("count").getInt());
			}
			OntProperty p = ontModel.getOntProperty(pRes.getURI());
			if (p == null) {
				continue;
			}
			// We know that the object is a resource, exclude all non-object properties in advance (e.g. DBPedia properties)
			if (object != null && !p.isObjectProperty()) {
				continue;
			}
			float propertyTypeScore;
			// Slightly prefer object properties over non-object properties
			if (p.isObjectProperty()) {
				propertyTypeScore = 1.01f;
			} else {
				propertyTypeScore = 1.00f;
			}
			
			String pName = formatResourceName(p.getLocalName());
			if (nameCandidates != null && !nameCandidates.isEmpty()) {
				for (ComparablePair<String, Float> candidate : nameCandidates) {
					if (pName.startsWith(candidate.key)) {
						float score = (float)candidate.key.length() / pName.length() * candidate.value * propertyTypeScore + countScoreBonus;
						result.add(new ComparablePair<Property, Float>(p, score));
					}
				}
			} else {
				result.add(new ComparablePair<Property, Float>(p, countScoreBonus));
			}
		}
		Collections.sort(result);
		int numResults = Math.min(result.size(), 10);
		
		return result.subList(0, numResults);
	}
	
	public void close() {
		System.out.println("Closing KB Connector. Number of queries: " + numQueries);
//		ontModel.close();
//		model.close();
//		data.end();
	}
}
