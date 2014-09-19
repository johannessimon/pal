package de.tudarmstadt.lt.pal.stanford;

import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import de.tudarmstadt.lt.pal.util.DependencyPatternParser;
import de.tudarmstadt.lt.pal.util.DependencyPatternParser.DependencyPattern;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.trees.GrammaticalRelation;

/**
 * Builds <code>StanfordTriple</code>'s from a Stanford dependency tree. This is used as an intermediate step
 * between Stanford dependencies and <code>SPARQLTriple</code>'s (and finally SPARQL queries).<br/>
 * This class does not declare any triple elements as variables or constants, nor does it perform
 * any mapping between natural language and actual ontology elements.
 */
public class StanfordTripleExtractor {
	Collection<DependencyPattern> patterns;
	Collection<DependencyPattern> focusPatterns;
	Collection<DependencyPattern> typePatterns;
	Map<IndexedWord, IndexedWord> typeConstraints;
	
	public StanfordTripleExtractor() {
		InputStream depPatternsIS = getClass().getClassLoader().getResourceAsStream("dep_patterns.txt");
		InputStream focusPatternsIS = getClass().getClassLoader().getResourceAsStream("focus_patterns.txt");
		InputStream typePatternsIS = getClass().getClassLoader().getResourceAsStream("type_patterns.txt");
		patterns = DependencyPatternParser.parse(depPatternsIS);
		focusPatterns = DependencyPatternParser.parse(focusPatternsIS);
		typePatterns = DependencyPatternParser.parse(typePatternsIS);
	}
	
	/**
	 * Builds StanfordTriple's consisting of nodes from a stanford dependency tree (as opposed to SPARQLTriples)
	 */
	public Set<StanfordTriple> extractTriples(SemanticGraph deps) {
		this.deps = deps;
		focusWord = null;
		triples = new HashSet<StanfordTriple>();
		typeConstraints = new HashMap<IndexedWord, IndexedWord>();
		IndexedWord root = deps.getFirstRoot();
		try {
			handleNode(root, 0);
		} catch (DependencyTreeTooDeepException e) {
			System.err.println("Error: Dependency tree is either too deep or contains infinite loop!");
		}
		return triples;
	}
	
	private SemanticGraph deps;
	private IndexedWord focusWord;
	private Set<StanfordTriple> triples;
	
	public IndexedWord getFocusWord() { return focusWord; }
	
	// prevent possible infinite loops
	private final static int MAX_NODE_DEPTH = 10;
	
	/**
	 * Recursively collect triples over dependency graph
	 * @throws DependencyTreeTooDeepException 
	 */
	private void handleNode(IndexedWord y, int depth) throws DependencyTreeTooDeepException {
		if (depth > MAX_NODE_DEPTH) {
			throw new DependencyTreeTooDeepException();
		}
		if (StanfordUtil.wordIsProperNoun(deps, y)) {
			// Constant elements are never subject of any triples (only object)
			return;
		}
		
		Collection<IndexedWord> children = deps.getChildren(y);
		
		IndexedWord x = deps.getParent(y);
		IndexedWord subject = null;
		IndexedWord predicate = null;
		IndexedWord object = null;
		for (IndexedWord z : children) {
			handleNode(z, depth + 1);
			if (ignoreWord(y) || ignoreWord(z)) {
				continue;
			}
			
			SemanticGraphEdge edge = deps.getAllEdges(y, z).get(0);
			GrammaticalRelation rel = edge.getRelation();
			
			for (DependencyPattern depPattern : patterns) {
				if (depPattern.matches(rel, x, y, z)) {
					if (depPattern.isAntiPattern()) {
						break; // Skips x->y->z triple altogether
					}
					subject = depPattern.mapTripleElement(subject, depPattern.subjectMapping, rel, x, y, z);
					predicate = depPattern.mapTripleElement(predicate, depPattern.predicateMapping, rel, x, y, z);
					object = depPattern.mapTripleElement(object, depPattern.objectMapping, rel, x, y, z);
					break;
				}
			}
			
			for (DependencyPattern focusPattern : focusPatterns) {
				if (focusPattern.matches(rel, x, y, z)) {
					focusWord = focusPattern.mapTripleElement(null, focusPattern.subjectMapping, rel, x, y, z);
					break;
				}
			}
			
			for (DependencyPattern typePattern : typePatterns) {
				if (typePattern.matches(rel, x, y, z)) {
					IndexedWord word = typePattern.mapTripleElement(null, typePattern.subjectMapping, rel, x, y, z);
					IndexedWord type = typePattern.mapTripleElement(null, typePattern.predicateMapping, rel, x, y, z);
					typeConstraints.put(word, type);
					break;
				}
			}
		}

		// There must be at least a subject and an object (no matter if variable or constant),
		// however the predicate may be a wildcard, i.e. null.
		// Wildcard predicates will later be replaced by a specific predicate, e.g.
		// the most common predicate between the subject and the object.
		if (subject != null && object != null) {
			triples.add(new StanfordTriple(subject, predicate, object));
		}
	}
	
	private boolean ignoreWord(IndexedWord word) {
		String wordValue = word.value().toLowerCase();
		// Ignore certain imperative forms ("give me...", "tell me..." and  "list all...")
		return word.tag().equals("VB")
				&& (wordValue.equals("give") ||
					wordValue.equals("tell") ||
					wordValue.equals("list"));
	}
	
	private class DependencyTreeTooDeepException extends Exception {
		private static final long serialVersionUID = 1L;
	}
}
