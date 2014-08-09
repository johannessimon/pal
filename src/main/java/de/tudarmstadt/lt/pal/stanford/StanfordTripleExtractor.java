package de.tudarmstast.lt.pal.stanford;
import java.util.Collection;
import java.util.Set;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.trees.GrammaticalRelation;


public class StanfordTripleExtractor {
	static boolean ignoreWord(IndexedWord word) {
		String wordValue = word.value().toLowerCase();
		// Ignore certain imperative forms ("give me...", "tell me..." and  "list all...")
		return word.tag().equals("VB")
				&& (wordValue.equals("give") ||
					wordValue.equals("tell") ||
					wordValue.equals("list"));
	}
	
	public static StanfordTriple getTriple(SemanticGraph deps, IndexedWord node, Set<StanfordTriple> triples, Set<IndexedWord> focusWords) {
		Collection<IndexedWord> children = deps.getChildren(node);
		// we have a leaf!
		if (children.isEmpty()) {
			return null;
		}
		
		StanfordTriple resultT = new StanfordTriple();
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
				resultT.predicate = node;
				resultT.object = child;
			// Special case preposition (reversion of subject/object,
		    // parent is predicate)
			} else if (relName.equals("prep") &
					   rel.getSpecific() != null && rel.getSpecific().equals("of")) {
				// The name of the relation (e.g. "state of" or "height of" at the same
				// time hints at the type of the object (height -> number)
				if (node.tag().startsWith("N")) {
					resultT.object = node;
				}
				resultT.predicate = node.value(); // no variable! (thus use String object)
				resultT.subject = child;
			} else if (relName.equals("prep")) {
				// If the object modifies a verb, then the
				// verb is part of the relation (predicate)
				if (node.tag().startsWith("V")) {
					resultT.predicate = node.value() + " " + rel.getSpecific();
				} else {
					// In this case we don't know anything about the predicate and will use
					// a wildcard (thus constraint = "there must be a relation between subject/object")
					resultT.predicate = /*rel.getSpecific() != null ? rel.getSpecific() : */ "[]";
					resultT.subject = node;
				}
				resultT.object = child;
			} else if (relName.equals("nsubj") || relName.equals("nsubjpass")) {
				// question word as predicate doesn't make sense
				if (!node.tag().startsWith("W")) {
					resultT.predicate = node;
					resultT.subject = child;
					if (child.value().toLowerCase().equals("who")) {
						focusWords.add(child);
					}
				}
			} else if (relName.equals("advmod")) {
				resultT.object = child;
				if (child.tag().startsWith("W")) {
					focusWords.add(child);
				}
			} else if (relName.equals("det")) {
				if (child.value().toLowerCase().equals("which")) {
					focusWords.add(node);
				}
			}
		}
		
		if (resultT.subject != null || resultT.predicate != null || resultT.object != null) {
			triples.add(resultT);
			return resultT;
		} else {
			return null;
		}
	}
}
