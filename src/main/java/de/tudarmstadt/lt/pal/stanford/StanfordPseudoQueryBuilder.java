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

/**
 * Builds pseudo queries using stanford dependency trees.
 */
public class StanfordPseudoQueryBuilder {
	KnowledgeBaseConnector kb;
	StanfordTripleExtractor tripleExtactor = new StanfordTripleExtractor();
	
	public StanfordPseudoQueryBuilder(KnowledgeBaseConnector kb) {
		this.kb = kb;
	}
	
	/**
	 * Build a pseudo query from the given dependency tree
	 */
	public PseudoQuery buildPseudoQuery(SemanticGraph dependencies) {
		Set<IndexedWord> focusWords = new HashSet<IndexedWord>();
		// extractTriples() will add focus words to passed focusWords set
		Set<StanfordTriple> triples = tripleExtactor.extractTriples(dependencies, focusWords);
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
	
	/**
	 * Returns a variable instance representing the given word in the tree, creating a new variable instance if neccessary
	 */
	SPARQLTriple.Variable registerVariable(Map<IndexedWord, SPARQLTriple.Variable> variables, IndexedWord word, SemanticGraph deps, String varType) {
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
	
	/**
	 * Determines a POS-tag suffix for the given word
	 * 
	 * @return Single-character POS-tag preceded by a '#' (e.g. "#v"),
	 *         or an empty string if no POS tag is present
	 */
	private String getWordTagSuffix(IndexedWord word) {
		if (word.tag() != null && !word.tag().isEmpty()) {
			String tag = word.tag().toLowerCase();
			if (tag.length() > 1) {
				tag = tag.substring(0, 1);
			}
			return "#" + tag;
		}
		return "";
	}
	
	/**
	 * Aggregates the subtree of a given word to a single string,
	 * taking only certain semantic relations into account
	 * (noun compounds and modifiers)
	 */
	private String getNodeText(SemanticGraph deps, IndexedWord word) {
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
	
	/**
	 * Determines which representation a given node in a dependency tree has in a SPARQL query (constant or variable).
	 * 
	 * In case of a variable, the variable is also added to the variable map passed.
	 */
	private SPARQLTriple.Element nodeToSPARQLElement(SemanticGraph deps, Object node, Map<IndexedWord, SPARQLTriple.Variable> variables) {
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
			} else if (word.tag().startsWith("W")) {
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
