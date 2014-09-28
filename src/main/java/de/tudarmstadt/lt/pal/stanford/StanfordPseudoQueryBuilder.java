package de.tudarmstadt.lt.pal.stanford;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.tudarmstadt.lt.pal.Query;
import de.tudarmstadt.lt.pal.Triple;
import de.tudarmstadt.lt.pal.Triple.Constant;
import de.tudarmstadt.lt.pal.Triple.Element;
import de.tudarmstadt.lt.pal.Triple.Variable;
import de.tudarmstadt.lt.pal.Triple.Variable.Type;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;

/**
 * Builds pseudo queries using stanford dependency trees.
 */
public class StanfordPseudoQueryBuilder {
	StanfordTripleExtractor tripleExtactor = new StanfordTripleExtractor();
	Set<String> ignoredWords = new HashSet<String>();
	
	public StanfordPseudoQueryBuilder() {
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
		Map<IndexedWord, IndexedWord> typeConstraints = tripleExtactor.typeConstraints;
		Map<IndexedWord, Variable> variables = new HashMap<IndexedWord, Variable>();
		List<Triple> queryTriples = new LinkedList<Triple>();
		for (StanfordTriple t : triples) {
			Element subject = nodeToSPARQLElement(dependencies, t.subject, variables, typeConstraints, false);
			Element predicate = nodeToSPARQLElement(dependencies, t.predicate, variables, typeConstraints, true);
			Element object = nodeToSPARQLElement(dependencies, t.object, variables, typeConstraints, false);
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
	
	Variable.Type getVariableType(IndexedWord var, IndexedWord type) {
		Variable.Type varType = null;
		
		if (type != null) {
			String typeStr = type.value().toLowerCase();
			if (typeStr.equals("who")) {
				varType = Variable.Type.Agent;
			} else if (typeStr.equals("where")) {
				varType = Variable.Type.Place;
			} else if (typeStr.equals("when")) {
				varType = Variable.Type.Date;
			} else if (typeStr.equals("many")) {
				varType = Variable.Type.Number;
			}
		}
		// Adjectives always refer to data properties (i.e. with literals as object)
		else if (var.tag().startsWith("J")) {
			varType = Variable.Type.Literal;
		}
		
		if (varType == null) {
			varType = Variable.Type.Unknown;
		}
		return varType;
	}
	
	/**
	 * Returns a variable instance representing the given word in the tree, creating a new variable instance if neccessary
	 */
	Variable registerVariable(Map<IndexedWord, Variable> variables, IndexedWord word, SemanticGraph deps, IndexedWord type) {
		Variable var = variables.get(word);
		if (var == null) {
//			String varName = word.lemma();
			String varName = getNodeText(deps, word).replace(' ', '_');
			Type varType = getVariableType(word, type);
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
		String prefix = "";
		String suffix = "";
		String resStr;
		// Words starting with an uppercase letter are often entities,
		// e.g. "Mean Hamster Software" vs. "video games"
		// here the first should stay as it is, whereas the second
		// should be normalized to "video game"
		if (StanfordUtil.wordIsProperNoun(deps, word)) {
			resStr = word.value();
		} else {
			resStr = word.lemma();
		}
		
//		boolean namedEntityCandidate = Character.isUpperCase(word.value().charAt(0));
		for (IndexedWord child : deps.getChildren(word)) {
			boolean isRightChild = child.index() > word.index();
			boolean isProperNounChild = StanfordUtil.wordIsProperNoun(deps, child);
			String part = "";
			if (ignoredWords.contains(child.lemma().toLowerCase())) {
				continue;
			}
			List<SemanticGraphEdge> edges = deps.getAllEdges(word, child);
			for (SemanticGraphEdge e : edges) {
				String relName = e.getRelation().getShortName();
				if (StanfordUtil.wordIsProperNoun(deps, word)) {
					// words on the left side must be proper nouns
					// e.g. "*The* Pillars of the Earth" vs. "*the* Brooklyn Bridge"
					if (isRightChild || isProperNounChild) {
						String specific = e.getRelation().getSpecific();
						if (specific != null) {
							part += specific + " ";
						}
						part += getNodeText(deps, child);
					}
				// Ignore words to the right (e.g. "video games published" will have
				// "published" as dependency on "games")
				} else if (!isRightChild) {
					if (relName.equals("nn") ||
						relName.endsWith("mod") ||
						relName.equals("dep")) {
						part += getNodeText(deps, child);
					}
				}
				if (!part.isEmpty()) {
					if (isRightChild) {
						suffix += " " + part;
					} else {
						prefix += part + " ";
					}
					break;
				}
			}
		}
		return prefix + resStr + suffix;
	}
	
	/**
	 * Determines which representation a given node in a dependency tree has in a SPARQL query (constant or variable).
	 * 
	 * In case of a variable, the variable is also added to the variable map passed.
	 */
	private Element nodeToSPARQLElement(SemanticGraph deps, IndexedWord word, Map<IndexedWord, Variable> variables, Map<IndexedWord, IndexedWord> typeConstraints, boolean isPredicate) {
		if (word == null) {
			return null;
		}
		
		// Proper nouns and predicates are constants
		if (isPredicate || StanfordUtil.wordIsProperNoun(deps, word)) {
			String wordStr = getNodeText(deps, word) + getWordTagSuffix(word);
			Constant c = new Constant(wordStr);
			if (typeConstraints.containsKey(word)) {
				c.type = typeConstraints.get(word).lemma();
			}
			return c;
		}
		
		return registerVariable(variables, word, deps, typeConstraints.get(word));
	}
}
