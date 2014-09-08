package de.tudarmstadt.lt.pal.stanford;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
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
	
	public StanfordTripleExtractor() {
		patterns = DependencyPatternParser.parse(new File("src/main/resources/dep_patterns.txt"));
		focusPatterns = DependencyPatternParser.parse(new File("src/main/resources/focus_patterns.txt"));
	}
	
	/**
	 * Builds StanfordTriple's consisting of nodes from a stanford dependency tree (as opposed to SPARQLTriples)
	 */
	public Set<StanfordTriple> extractTriples(SemanticGraph deps) {
		this.deps = deps;
		focusWord = null;
		triples = new HashSet<>();
//		varConstraints = new HashMap<>();
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
//	private Map<IndexedWord, String> varConstraints;
	
	public IndexedWord getFocusWord() { return focusWord; }
	
	// prevent possible infinite loops
	private final static int MAX_NODE_DEPTH = 10;
	/**
	 * Recursively collect triples over dependency graph
	 * @throws DependencyTreeTooDeepException 
	 */
	private StanfordTriple handleNode(IndexedWord node, int depth) throws DependencyTreeTooDeepException {
		if (depth > MAX_NODE_DEPTH) {
			throw new DependencyTreeTooDeepException();
		}
		
		Collection<IndexedWord> children = deps.getChildren(node);
		// we have a leaf!
		if (children.isEmpty()) {
			return null;
		}
		
		IndexedWord subject = null;
		IndexedWord predicate = null;
		IndexedWord object = null;
		for (IndexedWord child : children) {
			StanfordTriple childT = handleNode(child, depth + 1);
			
			// If a dependency implies a semantic constraint but does not
			// specify either subject or object, then this is determined by
			// the head of the parent dependency
			// Example 1: agent(written, Pamuk) specifies "[?] written by Pamuk",
			//            leaving out the subject. In this case we can use the
			//            parent relation vmod(books, written) to tell ? is "books".
			// Example 2: prep_from(author, Turkey) specifies "author from Turkey",
			//            i.e. both subject and object
			if (childT != null && childT.predicate != null) {
				if (childT.object == null) {
					childT.object = node;
				} else if (childT.subject == null) {
					childT.subject = node;
				}
				// Now the triple is complete
				triples.add(childT);
			}
			
			if (ignoreWord(node) || ignoreWord(child)) {
				continue;
			}
			
			SemanticGraphEdge edge = deps.getAllEdges(node, child).get(0);
			GrammaticalRelation rel = edge.getRelation();
			
			for (DependencyPattern depPattern : patterns) {
				if (depPattern.matches(node, rel, child)) {
					subject = depPattern.mapTripleElement(subject, depPattern.subjectMapping, node, rel, child);
					predicate = depPattern.mapTripleElement(predicate, depPattern.predicateMapping, node, rel, child);
					object = depPattern.mapTripleElement(object, depPattern.objectMapping, node, rel, child);
					break;
				}
			}
			
			for (DependencyPattern focusPattern : focusPatterns) {
				if (focusPattern.matches(node, rel, child)) {
					focusWord = focusPattern.mapTripleElement(null, focusPattern.subjectMapping, node, rel, child);
					break;
				}
			}
		}

		StanfordTriple triple = new StanfordTriple(subject, predicate, object);
		// There must be at least a subject and an object (no matter if variable or constant),
		// however the predicate may be a wildcard, i.e. null.
		// Wildcard predicates will later be replaced by a specific predicate, e.g.
		// the most common predicate between the subject and the object.
		if (subject != null && object != null) {
			triples.add(triple);
		} //else {
//			return null;
//		}
		return triple;
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
