package de.tudarmstadt.lt.pal;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import com.hp.hpl.jena.ontology.OntClass;
import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.ontology.OntModelSpec;
import com.hp.hpl.jena.ontology.OntProperty;
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
import com.hp.hpl.jena.util.iterator.ExtendedIterator;
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
		System.out.println("QUERY " + query);
		query = SPARQL_PREFIXES + "\n" + query;
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
	}

	/**
	 * Returns the number of resources that are subject of a
	 * <code>(*, prop, object)</code>
	 * triple.<br/>
	 */
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
	
	Collection<Resource> getTypeCandidates(String name) {
		String nameLC = name.toLowerCase();
		Collection<Resource> types = new LinkedList<Resource>();
		Resource classResource = getResource("http://www.w3.org/2002/07/owl#Class");
		Property labelProperty = getProperty("http://www.w3.org/2000/01/rdf-schema#label");
		StmtIterator it = model.listStatements(null, RDF.type, classResource);
		while (it.hasNext()) {
			Statement stmt = it.next();
			Resource type = stmt.getSubject();
			// TODO: Remove numbers from yago!
			if (type.getLocalName().toLowerCase().equals(nameLC)) {
				types.add(type);
				continue;
			}

			// TODO: wieso wird hier "movie" nicht erkannt?
			// siehe http://dbpedia.org/ontology/Film
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
	
	List<ComparablePair<Resource, Float>> getResourceCandidates(String name, int limit) {
		System.out.println("Searching resources... [" + name + "]");
		if (name.contains("#")) {
			int sepIndex = name.indexOf('#');
			name = name.substring(0, sepIndex - 1);
		}
		List<ComparablePair<Resource, Float>> result = new LinkedList<ComparablePair<Resource, Float>>();
		{
			String queryString = "SELECT DISTINCT ?subject ?name WHERE { ?subject <http://xmlns.com/foaf/0.1/name> ?name . "
					           + "FILTER(contains (?name,\"" + name + "\")) } LIMIT " + limit;
			QueryExecution qexec = getQueryExec(queryString);
			try {
				ResultSet results = qexec.execSelect();
				for (; results.hasNext(); )
				{
					QuerySolution soln = results.nextSolution();
					String rName = soln.getLiteral("name").getString();
					Resource r = soln.getResource("subject");
					if (r != null) {
						result.add(new ComparablePair<Resource, Float>(r, (float)name.length() / rName.length()));
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
					System.out.println(var);
					res.add(var.toString());
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally { qexec.close(); }
		return res;
	}
	
	Collection<ComparablePair<Property, Float>> getPropertyCandidates(String name, Resource subject, Resource object) {
		Resource subjectClass = subject != null ? ontModel.getOntClass(subject.getURI()) : null;
		Resource objectClass = object != null ? ontModel.getOntClass(object.getURI()) : null;
		String nameLC = name.toLowerCase();
//		String pos = null;
		if (nameLC.contains("#")) {
			int sepIndex = nameLC.indexOf('#');
//			pos = nameLC.substring(sepIndex + 1);
			nameLC = nameLC.substring(0, sepIndex);
		}
		
		String querySubject = subjectClass == null && subject != null ? "<" + subject + ">": "?s";
		String queryObject = objectClass == null && object != null ? "<" + object + ">": "?o";
		String query = "SELECT DISTINCT ?p WHERE { " + querySubject + " ?p " + queryObject + " }";
		QueryExecution qexec = getQueryExec(query);
				
		List<ComparablePair<Property, Float>> result = new LinkedList<ComparablePair<Property, Float>>();
		ResultSet propPreCandidates = qexec.execSelect();
		while (propPreCandidates.hasNext()) {
			QuerySolution sol = propPreCandidates.next();
			Resource pRes = sol.getResource("p");
			if (pRes == null) {
				continue;
			}
			OntProperty p = ontModel.getOntProperty(pRes.getURI());
			if (p == null) {
				continue;
			}
			String pName = p.getLocalName().toLowerCase();
			if (pName.contains(nameLC)) {
				OntClass domain = p.getDomain() != null ? p.getDomain().asClass() : null;
				OntClass range = p.getRange() != null ? p.getRange().asClass() : null;
				if (domain != null && subjectClass != null) {
//					System.out.println("domain: " + (domain.hasSubClass(subjectClass) || domain.equals(subjectClass)));
					if (!domain.hasSubClass(subjectClass) && !domain.equals(subjectClass)) {
						continue;
					}
				}
				if (range != null && objectClass != null) {
//					System.out.println("range: " + (range.hasSubClass(objectClass) || range.equals(objectClass)));
					if (!range.hasSubClass(objectClass) && !range.equals(objectClass)) {
						continue;
					}
				}
				float score = (float)nameLC.length() / pName.length();
				result.add(new ComparablePair<Property, Float>(p, score));
			}
		}
		int numResults = Math.min(result.size(), 10);
		List<ComparablePair<Property, Float>> sortedResult = result.subList(0, numResults);
		Collections.sort(sortedResult);
		System.out.println("Done searching props.");
		
		return sortedResult;
	}
	
	public void close() {
		System.out.println("Closing KB Connector. Number of queries: " + numQueries);
		data.end();
	}
}
