package de.tudarmstadt.lt.pal;
import java.net.URLDecoder;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

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
import com.hp.hpl.jena.rdf.model.InfModel;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.reasoner.ReasonerRegistry;
import com.hp.hpl.jena.tdb.TDBFactory;
import com.hp.hpl.jena.vocabulary.RDF;

import de.tudarmstadt.lt.pal.util.ComparablePair;

/**
 * Basic interface to ontology and triple store
 */
public class KnowledgeBaseConnector {
	
	Model model;
	OntModel ontModel;
	InfModel infModel;
	Dataset data;
	String sparqlEndpoint;
	
	public final String SPARQL_PREFIXES =
			"PREFIX dbo: <http://dbpedia.org/ontology/>\n" +
			"PREFIX dbp: <http://dbpedia.org/property/>\n" +
			"PREFIX res: <http://dbpedia.org/resource/>\n" +
			"PREFIX yago: <http://dbpedia.org/class/yago/>\n" +
			"PREFIX foaf: <http://xmlns.com/foaf/0.1/>\n" +
			"PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n" +
			"PREFIX owl: <http://www.w3.org/2002/07/owl#>\n" +
			"PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" +
			"PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n";
	
	public KnowledgeBaseConnector(String tdbDir) {
		// Create a TDB-backed dataset
		String modelDir = tdbDir;
		data = TDBFactory.createDataset(modelDir);
		data.begin(ReadWrite.READ);
		// Get model inside the transaction
		model = data.getDefaultModel();
		sparqlEndpoint = null;
		ontModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM, model);
		infModel = ModelFactory.createInfModel(ReasonerRegistry.getOWLMicroReasoner(), model); 
	}
	
	public KnowledgeBaseConnector(String tdbDir, String sparqlEndpoint) {
		this(tdbDir);
		this.sparqlEndpoint = sparqlEndpoint;
	}
	
	private QueryExecution getQueryExec(String query) {
		numQueries++;
		query = SPARQL_PREFIXES + "\n" + query;
//		System.out.println(query);
		QueryExecution qexec;
		if (sparqlEndpoint != null) {
			qexec = QueryExecutionFactory.sparqlService(sparqlEndpoint, query);
		} else {
			qexec = QueryExecutionFactory.create(query, model);
		}
		return qexec;
	}
	
	/**
	 * Returns the number of values the specified property of the specified
	 * resource <code>subject</code> has, i.e. the number of
	 * <code>(subject, prop, *)</code>
	 * triples.<br/>
	 * <b>Note:</b> This number may include resources as well as literals.
	 */
	/*
	int getSubjectPropertyScore(Resource subject, Property prop, String objectType) {
//		return subject.listProperties(prop).toList().size();
		int numResults = 0;
		String queryString = "SELECT (COUNT(*) AS ?count) WHERE { <" + subject + "> <" + prop + "> ?object . ";
		if (objectType != null) {
			queryString += "?object rdf:type <" + objectType + "> . ";
		}
		queryString += "}";
		
		QueryExecution qexec = getQueryExec(queryString);
		try {
			ResultSet res = qexec.execSelect();

			for (; res.hasNext(); ) {
				QuerySolution sol = res.next();
				numResults = sol.getLiteral("count").getInt();
			}
		} catch (Exception e) {
			System.out.println("Query FAILED: " + queryString);
		} finally {
			qexec.close();
		}
		return numResults;
	}*/

	/**
	 * Returns the number of resources that are subject of a
	 * <code>(*, prop, object)</code>
	 * triple.<br/>
	 */
	/*
	int getPropertyObjectScore(Property prop, Resource object, String subjectType) {
//		return model.listResourcesWithProperty(prop, object).toList().size();
		int numResults = 0;
		String queryString = "SELECT (COUNT(*) AS ?count) WHERE { ?subject <" + prop + "> <" + object + "> . ";
		if (subjectType != null) {
			queryString += "?subject rdf:type <" + subjectType + "> . ";
		}
		queryString += "}";
		
		QueryExecution qexec = getQueryExec(queryString);
		try {
			ResultSet res = qexec.execSelect();

			for (; res.hasNext(); ) {
				QuerySolution sol = res.next();
				numResults = sol.getLiteral("count").getInt();
			}
		} catch (Exception e) {
			System.out.println("Query FAILED: " + queryString);
		} finally {
			qexec.close();
		}
		return numResults;
	}
	*/
	
	/**
	 * Returns the property with the specified URI
	 */
	Property getProperty(String uri) {
		return model.getProperty(uri);
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
		String nameLC = name.toLowerCase();
		Collection<Resource> types = new LinkedList<Resource>();
		Resource classResource = getResource("http://www.w3.org/2002/07/owl#Class");
		Property labelProperty = getProperty("http://www.w3.org/2000/01/rdf-schema#label");
		StmtIterator it = model.listStatements(null, RDF.type, classResource);
		while (it.hasNext()) {
			Statement stmt = it.next();
			Resource type = stmt.getSubject();
			String typeName = formatTypeName(type.getLocalName());
			if (typeName.equals(nameLC)) {
				types.add(type);
				continue;
			}

			StmtIterator labels = model.listStatements(type, labelProperty, (String)null);
			while (labels.hasNext()) {
				Statement labelStmt = labels.next();
				if (labelStmt.getObject() != null) {
					Literal label = labelStmt.getObject().asLiteral();
					if (label.getLanguage() == "en" &&
						label.getString().toLowerCase().equals(nameLC)) {
						types.add(type);
						break;
					}
				}
			}
		}
		/*
		String queryString = "SELECT ?type WHERE { ?type rdf:type dbo:Class . "
				                  + "?type rdf:label '" + name + "' . }";
		QueryExecution qexec = getQueryExec(queryString);
		try {
			ResultSet resultSet = qexec.execSelect();
			while (resultSet.hasNext()) {
				QuerySolution s = resultSet.next();
				Resource type = s.getResource("type");
				types.add(type);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}*/
		return types;
	}
	
	/*private String getBifContainsConstraints(String name) {
		String[] tokens = name.split(" ");
		StringBuilder res = new StringBuilder();
		// TODO: So funktioniert das nicht...
		for (String token : tokens ) {
			res.append("?name <bif:contains> \"");
			res.append(token);
			res.append("\" . ");
		}
		return res.toString();
	}*/
	
	List<ComparablePair<Resource, Float>> getResourceCandidates(String name, int limit) {
		System.out.println("Searching resources... [" + name + "]");
		if (name.contains("#")) {
			int sepIndex = name.indexOf('#');
			name = name.substring(0, sepIndex);
		}
		List<ComparablePair<Resource, Float>> result = new LinkedList<ComparablePair<Resource, Float>>();
		{
//			String bifContainsConstraints = getBifContainsConstraints(name);
			String queryString = "SELECT DISTINCT ?subject ?name FROM <http://dbpedia.org> WHERE { \n"
				               + "  { ?subject foaf:name ?name . ?name <bif:contains> \"'" + name + "'\"} UNION\n"
				               + "  { ?subject rdfs:label ?name . ?name <bif:contains> \"'" + name + "'\"} . \n"
				               + "  ?subject a owl:Thing . \n"
					           + "  FILTER(langMatches(lang(?name), \"en\"))\n"
//				               + "         contains(?name, \"" + name + "\")) \n"
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
						float resourceNameScore = 1.0f - (float)Math.abs(r.getLocalName().length() - name.length()) / r.getLocalName().length();
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

		System.out.println("Done searching resources.");
		Collections.sort(result);
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
		if (queryString.contains("<null>")) {
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
	
	private String getSPARQLString(Resource r, String varName) {
		if (r == null) {
			return varName;
		} else {
			return "<" + r.getURI() + ">";
		}
	}
	
	/**
	 * Returns either an <code>OntClass</code> or a <code>DataRange</code> representation of the
	 * given resource or null if no such representation exists.
	 */
	private OntResource getOntResource(Resource r) {
		if (r != null) {
			OntResource ontR = ontModel.getOntResource(r.getURI());
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
	
	Collection<ComparablePair<Property, Float>> getPropertyCandidates(Collection<ComparablePair<String, Float>> nameCandidates, Resource subject, Resource object) {
		OntResource subjectType = getOntResource(subject);
		OntResource objectType = getOntResource(object);
		// Set subject/object is to null iff. it is a variable
		if (subjectType != null) {
			subject = null;
		}
		if (objectType != null) {
			object = null;
		}
		boolean subjectIsVar = subject == null;
		boolean objectIsVar = object == null;
		
		String querySubject = getSPARQLString(subject, "?s");
		String queryObject = getSPARQLString(object, "?o");
		String query = "SELECT ?p ";
		// Count number of property "connections" only if we have exactly one
		// variable (^ is XOR) and no clue about the property
		boolean useCountScore = nameCandidates == null && subjectIsVar ^ objectIsVar;
		if (useCountScore) {
			String countVar = subjectIsVar ? "?s" : "?o";
			query += "(COUNT(" + countVar + ") AS ?count)";
		}
		query += " FROM <http://dbpedia.org> WHERE { " + querySubject + " ?p " + queryObject + " } GROUP BY ?p";
		QueryExecution qexec = getQueryExec(query);
				
		List<ComparablePair<Property, Float>> result = new LinkedList<ComparablePair<Property, Float>>();
		ResultSet propPreCandidates = qexec.execSelect();
		while (propPreCandidates.hasNext()) {
			QuerySolution sol = propPreCandidates.next();
			Resource pRes = sol.getResource("p");
			float countScore = 1.0f;
			if (useCountScore) {
				countScore = sol.getLiteral("count").getInt();
			}
			OntProperty p = ontModel.getOntProperty(pRes.getURI());
			if (p == null) {
				continue;
			}
			String pName = p.getLocalName().toLowerCase();
			if (nameCandidates != null) {
				for (ComparablePair<String, Float> candidate : nameCandidates) {
					if (pName.startsWith(candidate.key)) {
						if (checkTypeConstraint(p.getDomain(), subjectType) &&
							checkTypeConstraint(p.getRange(), objectType)) {
							float score = (float)candidate.key.length() / pName.length() * candidate.value * countScore;
							result.add(new ComparablePair<Property, Float>(p, score));
						}
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
		data.end();
	}
}
