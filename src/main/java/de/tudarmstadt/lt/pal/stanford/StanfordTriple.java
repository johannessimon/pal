package de.tudarmstadt.lt.pal.stanford;
import edu.stanford.nlp.ling.IndexedWord;

/**
 * Represents an "abstract" triple based on a Stanford collapsed dependency tree.
 * Objects in this triple are not mapped to any specific ontology.<br/>
 * <br/>
 * StanfordTriple's are a sort of intermediate representation between stanford dependencies
 * and SPARQLTriple's.
 */
class StanfordTriple {
	IndexedWord subject;
	IndexedWord predicate;
	IndexedWord object;
	
	StanfordTriple(IndexedWord s, IndexedWord p, IndexedWord o) {
		subject = s;
		predicate = p;
		object = o;
	}
	
	public String toString() {
		return "[Subject: " + subject + "] [Predicate: " + predicate + "] [Object: " + object + "]";
	}
}