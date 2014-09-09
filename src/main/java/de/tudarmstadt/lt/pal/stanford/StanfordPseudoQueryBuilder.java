package de.tudarmstadt.lt.pal.stanford;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.tudarmstadt.lt.pal.KnowledgeBaseConnector;
import de.tudarmstadt.lt.pal.Query;
import de.tudarmstadt.lt.pal.Triple;
import de.tudarmstadt.lt.pal.Triple.Constant;
import de.tudarmstadt.lt.pal.Triple.Element;
import de.tudarmstadt.lt.pal.Triple.Variable;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;

/**
 * Builds pseudo queries using stanford dependency trees.
 */
public class StanfordPseudoQueryBuilder {
	KnowledgeBaseConnector kb;
	StanfordTripleExtractor tripleExtactor = new StanfordTripleExtractor();
	Set<String> ignoredWords = new HashSet<>();
	
	public StanfordPseudoQueryBuilder(KnowledgeBaseConnector kb) {
		this.kb = kb;
		// quantity words (e.g. in "How many films ...")
		ignoredWords.add("many");
		ignoredWords.add("much");
		ignoredWords.add("how");
		ignoredWords.add("where");
	}
	
	/**
	 * Build a pseudo query from the given dependency tree
	 */
	public Query buildPseudoQuery(SemanticGraph dependencies) {
		// extractTriples() will add focus words to passed focusWords set
		Set<StanfordTriple> triples = tripleExtactor.extractTriples(dependencies);
		Map<IndexedWord, Variable> variables = new HashMap<IndexedWord, Variable>();
		List<Triple> queryTriples = new LinkedList<Triple>();
		for (StanfordTriple t : triples) {
			Element subject = nodeToSPARQLElement(dependencies, t.subject, variables, false);
			Element predicate = nodeToSPARQLElement(dependencies, t.predicate, variables, true);
			Element object = nodeToSPARQLElement(dependencies, t.object, variables, false);
//			System.out.println("[Subject: " + subject + "] [Predicate: " + predicate + "] [Object: " + object + "]");
			queryTriples.add(new Triple(subject, predicate, object));
		}
		Variable focusVariable = variables.get(tripleExtactor.getFocusWord());

		Map<String, Variable> vars = new HashMap<String, Variable>();
		for (IndexedWord var : variables.keySet()) {
			Variable sparqlVar = variables.get(var);
			// Improvise if there's no obvious focus variable...
			// This will work in 90% of the cases, as there's often just one variable :-)
			if (focusVariable == null) {
				focusVariable = sparqlVar;
			}
			vars.put(sparqlVar.name, sparqlVar);
		}
		
		Query pseudoQuery = new Query();
		pseudoQuery.triples = queryTriples;
		pseudoQuery.vars = vars;
		pseudoQuery.focusVar = focusVariable;
		return pseudoQuery;
	}
	
	Variable.Type getVariableType(IndexedWord var) {
		Variable.Type varType = null;
		
		// Adjectives always refer to data properties (i.e. with literals as object)
		if (var.tag().startsWith("J")) {
			varType = Variable.Type.Literal;
		} else if (var.tag().startsWith("W")) {
			String wWord = var.value().toLowerCase();
			if (wWord.equals("who")) {
				varType = Variable.Type.Agent;
			} else if (wWord.equals("where")) {
				varType = Variable.Type.Place;
			} else if (wWord.equals("when")) {
				varType = Variable.Type.Date;
			}
		}
		
		if (varType == null) {
			varType = Variable.Type.Unknown;
		}
		return varType;
	}
	
	/**
	 * Returns a variable instance representing the given word in the tree, creating a new variable instance if neccessary
	 */
	Variable registerVariable(Map<IndexedWord, Variable> variables, IndexedWord word, SemanticGraph deps, Variable.Type varType) {
		Variable var = variables.get(word);
		if (var == null) {
//			String varName = word.lemma();
			String varName = getNodeText(deps, word).replace(' ', '_');
			if (varType == null) {
				varType = getVariableType(word);
			}
			var = new Variable(varName, varType);
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
//		boolean namedEntityCandidate = Character.isUpperCase(word.value().charAt(0));
		for (IndexedWord child : deps.getChildren(word)) {
			// Ignore words to the right (e.g. "video games published" will have
			// "published" as dependency on "games")
			if (child.index() > word.index()) {
				continue;
			}
			if (ignoredWords.contains(child.lemma().toLowerCase())) {
				continue;
			}
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
		char firstChar = word.value().charAt(0);
		// Words starting with an uppercase letter are often entities,
		// e.g. "Mean Hamster Software" vs. "video games"
		// here the first should stay as it is, whereas the second
		// should be normalized to "video game"
		if (Character.isUpperCase(firstChar)) {
			resStr += word.value();
		} else {
			resStr += word.lemma();
		}
		return resStr;
	}
	
	/**
	 * Determines which representation a given node in a dependency tree has in a SPARQL query (constant or variable).
	 * 
	 * In case of a variable, the variable is also added to the variable map passed.
	 */
	private Element nodeToSPARQLElement(SemanticGraph deps, IndexedWord word, Map<IndexedWord, Variable> variables, boolean isPredicate) {
		if (word == null) {
			return null;
		}
		
		// Proper nouns and predicates are constants
		if (isPredicate || word.tag().startsWith("NNP")) {
			String wordStr = getNodeText(deps, word) + getWordTagSuffix(word);
			return new Constant(wordStr);
		}
		
		return registerVariable(variables, word, deps, null);
	}
}
