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
	public Set<StanfordTriple> extractTriples(SemanticGraph deps, Set<IndexedWord> focusWords) {
		Set<StanfordTriple> triples = new HashSet<>();
		IndexedWord root = deps.getFirstRoot();
		getTriple(deps, root, triples, focusWords);
		return triples;
	}
	
	/**
	 * Recursively aggregates triples over dependency graph
	 */
	private StanfordTriple getTriple(SemanticGraph deps, IndexedWord node, Set<StanfordTriple> triples, Set<IndexedWord> focusWords) {
		Collection<IndexedWord> children = deps.getChildren(node);
		// we have a leaf!
		if (children.isEmpty()) {
			return null;
		}
		
		IndexedWord subject = null;
		IndexedWord predicate = null;
		IndexedWord object = null;
		for (IndexedWord child : children) {
			StanfordTriple childT = getTriple(deps, child, triples, focusWords);
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
			}
			
			if (ignoreWord(node) || ignoreWord(child)) {
				continue;
			}
			
			SemanticGraphEdge edge = deps.getAllEdges(node, child).get(0);
			GrammaticalRelation rel = edge.getRelation();
			String relName = edge.getRelation().getShortName();
			if (relName.equals("agent") || relName.equals("dobj")) {
				predicate = node;
				object = child;
			// Special case preposition (reversion of subject/object,
		    // parent is predicate)
			} else if (relName.equals("prep") &
					   rel.getSpecific() != null && rel.getSpecific().equals("of")) {
				// The name of the relation (e.g. "state of" or "height of" at the same
				// time hints at the type of the object (height -> number)
				if (node.tag().startsWith("N")) {
					object = node;
				}
//				resultT.predicate = node.lemma() + getWordTagSuffix(node); // no variable! (thus use String object)
				predicate = node;
				subject = child;
			} else if (relName.equals("prep")) {
				// If the object modifies a verb, then the
				// verb is part of the relation (predicate)
				if (node.tag().startsWith("V")) {
					// TODO: "born in" -> take "bear" as predicate and interprate "in" as dbpedia-owl:Place for target
//					resultT.predicate = node.lemma() + getWordTagSuffix(node);// + " " + rel.getSpecific();
					predicate = node;
				} else {
					// In this case we don't know anything about the predicate and will use
					// a wildcard (thus constraint = "there must be a relation between subject/object")
					predicate = null;///*rel.getSpecific() != null ? rel.getSpecific() : */ "[]";
					subject = node;
				}
				object = child;
			} else if (relName.equals("nsubj") || relName.equals("nsubjpass")) {
				// question word as predicate doesn't make sense
				if (!node.tag().startsWith("W")) {
					predicate = node;
					subject = child;
					if (child.value().toLowerCase().equals("who")) {
						focusWords.add(child);
					}
				}
			} else if (relName.equals("advmod")) {
				object = child;
				if (child.tag().startsWith("W")) {
					focusWords.add(child);
				}
			} else if (relName.equals("det")) {
				if (child.value().toLowerCase().equals("which")) {
					focusWords.add(node);
				}
			}
		}
		
		if (subject != null || predicate != null || object != null) {
			StanfordTriple triple = new StanfordTriple(subject, predicate, object);
			triples.add(triple);
			return triple;
		} else {
			return null;
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
}
