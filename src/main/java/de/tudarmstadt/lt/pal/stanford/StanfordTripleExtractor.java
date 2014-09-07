package de.tudarmstadt.lt.pal.stanford;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

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
	/**
	 * Builds StanfordTriple's consisting of nodes from a stanford dependency tree (as opposed to SPARQLTriples)
	 */
	public Set<StanfordTriple> extractTriples(SemanticGraph deps) {
		this.deps = deps;
		focusWord = null;
		triples = new HashSet<>();
//		varConstraints = new HashMap<>();
		IndexedWord root = deps.getFirstRoot();
		handleNode(root);
		return triples;
	}
	
	private SemanticGraph deps;
	private IndexedWord focusWord;
	private Set<StanfordTriple> triples;
//	private Map<IndexedWord, String> varConstraints;
	
	public IndexedWord getFocusWord() { return focusWord; }
	
	/**
	 * Recursively collect triples over dependency graph
	 */
	private StanfordTriple handleNode(IndexedWord node) {
		Collection<IndexedWord> children = deps.getChildren(node);
		// we have a leaf!
		if (children.isEmpty()) {
			return null;
		}
		
		IndexedWord subject = null;
		IndexedWord predicate = null;
		IndexedWord object = null;
		for (IndexedWord child : children) {
			StanfordTriple childT = handleNode(child);
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
			String relName = edge.getRelation().getShortName();
			// "be" as verb is a special case, as predicate is usually "hidden" in adjective
			if (node.lemma().equals("be") || node.lemma().equals("have")) {
				if (relName.contains("subj")) {
					subject = child;
				} else if (relName.contains("obj") || relName.contains("dep") || relName.contains("prep")) {
					predicate = child;
					object = child;
				}
			} else if (relName.equals("agent") || relName.equals("dobj")) {
				predicate = node;
				object = child;
			// Special case preposition (reversion of subject/object,
		    // parent is predicate)
			} else if (relName.equals("prep") &&
					   rel.getSpecific() != null && rel.getSpecific().equals("of")) {
				// The name of the relation (e.g. "state of" or "height of" at the same
				// time hints at the type of the object (height -> number)
				if (node.tag().startsWith("N")) {
					object = node;
				}
				predicate = node;
				subject = child;
			} else if (relName.equals("prep")) {
				if (node.tag().startsWith("N")) {
					// In this case we don't know anything about the predicate and will use
					// a wildcard (thus constraint = "there must be a relation between subject/object")
					predicate = null;
					subject = node;
				} else {
					// TODO: "born in" -> take "bear" as predicate and interprate "in" as dbpedia-owl:Place for target
					predicate = node;
				}
				object = child;
			} else if (relName.equals("nsubj") || relName.equals("nsubjpass")) {
				// question word as predicate doesn't make sense
				if (!node.tag().startsWith("W")) {
					predicate = node;
					subject = child;
					if (child.value().toLowerCase().equals("who")) {
						focusWord = child;
					}
				}
			} else if (relName.equals("advmod")) {
				object = child;
				if (child.tag().startsWith("W")) {
					focusWord = child;
				}
			} else if (relName.equals("det")) {
				if (child.value().toLowerCase().equals("which")) {
					focusWord = node;
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
}
