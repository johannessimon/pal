package de.tudarmstadt.lt.pal.stanford;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.tudarmstadt.lt.pal.KnowledgeBaseConnector;
import de.tudarmstadt.lt.pal.PseudoQuery;
import de.tudarmstadt.lt.pal.SPARQLTriple;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;


public class StanfordPseudoQueryBuilder {
	KnowledgeBaseConnector kb = new KnowledgeBaseConnector(/*"/Users/jsimon/No-Backup/dbpedia/data", "http://dbpedia.org/sparql"*/);
	
	public StanfordPseudoQueryBuilder(KnowledgeBaseConnector kb) {
		this.kb = kb;
	}
	
	public PseudoQuery buildPseudoQuery(SemanticGraph dependencies) {
		Set<StanfordTriple> triples = new HashSet<StanfordTriple>();
		IndexedWord root = dependencies.getFirstRoot();
		Set<IndexedWord> focusWords = new HashSet<IndexedWord>();
		StanfordTripleExtractor.getTriple(dependencies, root, triples, focusWords);
		Map<IndexedWord, SPARQLTriple.Variable> variables = new HashMap<IndexedWord, SPARQLTriple.Variable>();
		List<SPARQLTriple> queryTriples = new LinkedList<SPARQLTriple>();
		for (StanfordTriple t : triples) {
			SPARQLTriple.Element subject = nodeToSPARQLElement(dependencies, t.subject, variables);
			SPARQLTriple.Element predicate = nodeToSPARQLElement(dependencies, t.predicate, variables);
			SPARQLTriple.Element object = nodeToSPARQLElement(dependencies, t.object, variables);
//			System.out.println("[Subject: " + subject + "] [Predicate: " + predicate + "] [Object: " + object + "]");
			queryTriples.add(new SPARQLTriple(subject, predicate, object));
		}

		Map<String, SPARQLTriple.Variable> vars = new HashMap<String, SPARQLTriple.Variable>();
		for (IndexedWord var : variables.keySet()) {
			SPARQLTriple.Variable sparqlVar = variables.get(var);
			String type = var.lemma();
			switch (type.toLowerCase()) {
			case "who":
				type = "http://dbpedia.org/ontology/Person";
				break;
			case "where":
				type = "http://dbpedia.org/ontology/Place";
				break;
			case "when":
				type = "xsd:date";
				break;
			}
			// System.out.println(variables.get(var) + " <type> " + type);
			sparqlVar.typeName = type;
			vars.put(sparqlVar.name, sparqlVar);
		}
		
		PseudoQuery pseudoQuery = new PseudoQuery();
		pseudoQuery.triples = queryTriples;
		pseudoQuery.vars = vars;
		return pseudoQuery;
	}
	
	static String getVariableName(int index) {
		char firstVar = 'a';
		firstVar += index;
		return new String(new char[] { firstVar });
	}
	
	static SPARQLTriple.Variable registerVariable(Map<IndexedWord, SPARQLTriple.Variable> variables, IndexedWord word, SemanticGraph deps, String varType) {
		SPARQLTriple.Variable var = variables.get(word);
		if (var == null) {
			String varName = getNodeText(deps, word).replace(' ', '_');
			if (varType == null) {
				varType = varName;
			}
			var = new SPARQLTriple.Variable(varName, varType);
			variables.put(word, var);
		}
		return var;
	}
	
	public static String getWordTagSuffix(IndexedWord word) {
		if (word.tag() != null && !word.tag().isEmpty()) {
			String tag = word.tag().toLowerCase();
			if (tag.length() > 1) {
				tag = tag.substring(0, 1);
			}
			return "#" + tag;
		}
		return "";
	}
	
	private static String getNodeText(SemanticGraph deps, IndexedWord word) {
		String resStr = "";
		for (IndexedWord child : deps.getChildren(word)) {
			List<SemanticGraphEdge> edges = deps.getAllEdges(word, child);
			for (SemanticGraphEdge e : edges) {
				String relName = e.getRelation().getShortName();
				if (relName.equals("nn") ||
					relName.endsWith("mod") ||
					relName.equals("dep")) {
					resStr += getNodeText(deps, child) + " ";
					break;
				}
			}
		}
		resStr += word.lemma();//word.value();
		return resStr;
	}
	
	static SPARQLTriple.Element nodeToSPARQLElement(SemanticGraph deps, Object node, Map<IndexedWord, SPARQLTriple.Variable> variables) {
		SPARQLTriple.Element res = null;
		if (node instanceof String) {
			res = new SPARQLTriple.Constant((String)node, SPARQLTriple.ConstantType.UnmappedConstantType);
		} else if (node instanceof IndexedWord) {
			String resStr = "";
			IndexedWord word = (IndexedWord)node;
			
			if (word.tag().startsWith("N")) {
				resStr = getNodeText(deps, word) + getWordTagSuffix(word);
				// Un-proper noun (singular and plural)
				if (word.tag().equals("NN") || word.tag().equals("NNS")) {
					res = registerVariable(variables, word, deps, null);
				// Proper noun
				} else {
					res = new SPARQLTriple.Constant(resStr, SPARQLTriple.ConstantType.UnmappedConstantType);
				}
			} /*if (word.tag().startsWith("NNP")) {
				for (IndexedWord child : deps.getChildren(word)) {
					List<SemanticGraphEdge> edges = deps.getAllEdges(word, child);
					for (SemanticGraphEdge e : edges) {
						if (e.getRelation().getShortName().equals("nn")) {
							resStr += child.value() + " ";
							break;
						}
					}
				}
				resStr += word.value();
				res = new SPARQLTriple.Constant(resStr, SPARQLTriple.ConstantType.UnmappedConstantType);
			} */else if (word.tag().startsWith("W")) {
				if (word.value().toLowerCase().equals("who")) {
					res = registerVariable(variables, word, deps, "http://dbpedia.org/ontology/Person");
				} else if (word.value().toLowerCase().equals("where")) {
					res = registerVariable(variables, word, deps, "http://dbpedia.org/ontology/Place");
				}
			} else {
				res = new SPARQLTriple.Constant(word.lemma(), SPARQLTriple.ConstantType.UnmappedConstantType);
				res.name += getWordTagSuffix(word);
			}
		}
		
		return res;
	}
}
