package de.tudarmstadt.lt.pal.stanford;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;

public class StanfordUtil {
	public static boolean wordIsProperNoun(SemanticGraph g, IndexedWord w) {
		// Check if it has "the" as determiner, which makes it a proper noun
//		for (IndexedWord child : g.getChildren(w)) {
//			if (child.value().toLowerCase().equals("the")) {
//				return true;
//			}
//		}
		// Note that IndexWord.index() starts with 1, not 0
		return w.tag().startsWith("NNP") || w.index() > 1 && Character.isUpperCase(w.value().charAt(0));
	}
}
