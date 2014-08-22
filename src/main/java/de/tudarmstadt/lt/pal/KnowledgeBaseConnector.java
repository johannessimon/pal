package de.tudarmstadt.lt.pal;
import java.net.URLDecoder;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.ontology.OntModelSpec;
import com.hp.hpl.jena.ontology.OntProperty;
import com.hp.hpl.jena.ontology.OntResource;
import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.query.DatasetFactory;
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
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.vocabulary.RDF;

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
	 * Constructor to connect to local Fuseki server
	 */
	/*
	public KnowledgeBaseConnector() {
		sparqlEndpoint = "http://localhost:8890/sparql/";
		VirtDataset data = new VirtDataset("jdbc:virtuoso://localhost:1111", "dba", "dba");
		data.begin(ReadWrite.READ);
		model = data.getNamedModel("http://dbpedia.org");
		ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM, model);
//		ontModel.prepare();
//		Iterator<OntClass> it = ontModel.listClasses();
//		while (it.hasNext()) {
//			System.out.println(it.next());
//		}
		fillNamespacePrefixes();
	}*/
	
	/**
	 * Constructor to connect to local Virtuoso server
	 */
	public KnowledgeBaseConnector() {
//		sparqlEndpoint = "http://localhost:3030/ds/query";
		sparqlEndpoint = "http://localhost:8001/ds/query"; // nginx cache
//		Dataset data = TDBFactory.createDataset("/Users/jsimon/No-Backup/dbpedia37/tdb");
		data = DatasetFactory.assemble(
			    "/Users/jsimon/No-Backup/dbpedia37/dbpedia37-fuseki.ttl", 
			    "http://localhost/dbpedia37#text_dataset") ;
		data.begin(ReadWrite.READ);
		model = data.getDefaultModel();
		ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM, model);
		
//		ontModel.prepare();
//		Iterator<OntClass> it = ontModel.listClasses();
//		while (it.hasNext()) {
//			System.out.println(it.next());
//		}
		fillNamespacePrefixes();
	}/*
	
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
	private String formatTypeName(String name) {
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
	
	Collection<Resource> getTypeCandidates(String name) {
		name = formatTypeName(name);
		Collection<Resource> types = new LinkedList<Resource>();
		Resource classResource = getResource("http://www.w3.org/2002/07/owl#Class");
		Property labelProperty = getProperty("http://www.w3.org/2000/01/rdf-schema#label");
		StmtIterator it = model.listStatements(null, RDF.type, classResource);
		while (it.hasNext()) {
			Statement stmt = it.next();
			Resource type = stmt.getSubject();
			String typeName = formatTypeName(type.getLocalName());
			if (typeName.equals(name)) {
				types.add(type);
				continue;
			}

			StmtIterator labels = model.listStatements(type, labelProperty, (String)null);
			while (labels.hasNext()) {
				Statement labelStmt = labels.next();
				if (labelStmt.getObject() != null) {
					Literal label = labelStmt.getObject().asLiteral();
					if (label.getLanguage() == "en" &&
						label.getString().toLowerCase().equals(name)) {
						types.add(type);
						break;
					}
				}
			}
		}
		System.out.println("type candidates for \"" + name + "\": " + types);
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
//				               + "  { ?subject foaf:name ?name . ?name <bif:contains> \"'" + name + "'\"} UNION\n"
//				               + "  { ?subject rdfs:label ?name . ?name <bif:contains> \"'" + name + "'\"} . \n"
							   // Important: text:query must come first for pre-filtering (otherwise it takes much longer)
						       + "  ?subject text:query('" + name + "' " + limit + ") . \n"
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
					String varString = var.toString();
					if (var.isResource()) {
						varString = URLDecoder.decode(varString, "UTF-8");
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
	
	/**
	 * Check if a type <code>toCheck</code> is either equal to or (if type is a class) a subclass of <code>type</code>
	 * 
	 * @return True if either <code>type</code> or <code>toCheck</code> is null or
	 * if <code>toCheck</code> is (either directly or transitively) equal to <code>type</code>
	 */
	/*
	boolean checkTypeConstraint(OntResource superType, OntResource subType) {
		// There is no type constraint
		if (superType == null) {
			return true;
		}
		// This means range/domain to check for is unknown
		if (subType == null) {
			return false;
		}
		// Class constraint (e.g. dbpedia-owl:Agent)
		if (superType.isClass()) {
			OntClass ontClass = superType.asClass();
			return ontClass.hasSubClass(subType) || ontClass.equals(subType);
		// Data range constraint (e.g. xsd:integer)
		} else if (superType.isDataRange()) {
			return superType.equals(subType);
		} else {
			return true;
		}
	}*/
	
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
	/*
	String getSPARQLTypeConstraint(OntResource type, String varName) {
		if (type == null) {
			return "";
		} else if (type.isClass()) {
			return "?" + varName + " a " + getSPARQLResourceString(type) + " . ";
		} else if (type.isDataRange()) {
			return "FILTER(DATATYPE(?" + varName + ") = " + getSPARQLResourceString(type) + ") . ";
		}
		return "";
	}*/
	
	Collection<ComparablePair<Property, Float>> getPropertyCandidates(Map<String, Float> nameCandidates, Resource subject, Resource object,
			                                                          TypeConstraint subjectTC, TypeConstraint objectTC) {
		String querySubject = subject == null ? "?s" : getSPARQLResourceString(subject);
		String queryObject = object == null ? "?o" : getSPARQLResourceString(object);
		String query = "SELECT ?p ";
		// Count number of property "connections" if we have no clue about the property
		boolean useCountScore = nameCandidates == null;// && subjectIsVar ^ objectIsVar;
		if (useCountScore) {
			String countVar = subject == null ? "?s" : "?o";
			query += "(COUNT(" + countVar + ") AS ?count)";
		}
		query += " WHERE { " + querySubject + " ?p " + queryObject + " . ";
		query += getTypeConstraintSPARQLString(subjectTC, "s");
		query += getTypeConstraintSPARQLString(objectTC, "o");
		query += "} GROUP BY ?p";
		QueryExecution qexec;
		try {
			qexec = getQueryExec(query);
		} catch (Exception e) {
			System.err.println("Error while executing query: " + e.getMessage());
			return new LinkedList<>();
		}
				
		List<ComparablePair<Property, Float>> result = new LinkedList<ComparablePair<Property, Float>>();
		ResultSet propPreCandidates = qexec.execSelect();
		while (propPreCandidates.hasNext()) {
			QuerySolution sol = propPreCandidates.next();
			Resource pRes = sol.getResource("p");
			// Probably the result set is empty (but has one null entry with count 0)
			if (pRes == null) {
				break;
			}
			float countScore = 1.0f;
			if (useCountScore) {
				// Use logarithm to avoid an untyped property with 100 items
				// to be scored as high as a typed property with 10 items
				countScore = (float)Math.log(sol.getLiteral("count").getInt());
			}
			OntProperty p = ontModel.getOntProperty(pRes.getURI());
			if (p == null) {
				continue;
			}
			float propertyTypeScore;
			// Slightly prefer object properties over non-object properties
			if (p.isObjectProperty()) {
				propertyTypeScore = 1.01f;
			} else {
				propertyTypeScore = 1.00f;
			}
			
			String pName = p.getLocalName().toLowerCase();
			if (nameCandidates != null) {
				for (Entry<String, Float> candidate : nameCandidates.entrySet()) {
					if (pName.startsWith(candidate.getKey())) {
						float score = (float)candidate.getKey().length() / pName.length() * candidate.getValue() * countScore * propertyTypeScore;
						result.add(new ComparablePair<Property, Float>(p, score));
					}
				}
			} else {
				result.add(new ComparablePair<Property, Float>(p, countScore));
			}
		}
		Collections.sort(result);
		int numResults = Math.min(result.size(), 10);
		
		return result.subList(0, numResults);
	}
	
	public void close() {
		System.out.println("Closing KB Connector. Number of queries: " + numQueries);
		ontModel.close();
		model.close();
		data.end();
	}
}
