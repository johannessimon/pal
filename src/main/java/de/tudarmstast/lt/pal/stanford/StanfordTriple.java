package de.tudarmstast.lt.pal.stanford;
import edu.stanford.nlp.ling.IndexedWord;

/**
 * Represents an "abstract" triple based on a Stanford collapsed dependency tree.
 * Objects in this triple are not mapped to any specific ontology. They can be
 * either String objects or IndexedWord objects.
 */
class StanfordTriple {
	Object subject;
	Object predicate;
	Object object;
	
	StanfordTriple() {
	}
	
	StanfordTriple(IndexedWord s, IndexedWord p, IndexedWord o) {
		subject = s;
		predicate = p;
		object = o;
	}
	
	public String toString() {
		return "[Subject: " + subject + "] [Predicate: " + predicate + "] [Object: " + object + "]";
	}
}