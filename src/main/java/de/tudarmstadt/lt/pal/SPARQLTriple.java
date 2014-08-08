package de.tudarmstadt.lt.pal;
/**
 * Represents a triple that can be used in a SPARQL query. May contain
 * yet unmapped properties/resources.
 */
public class SPARQLTriple {
	public enum ConstantType {
		/**
		 * A constant that has not been mapped to an ontology
		 * (e.g. "author" or "Dan Brown")
		 **/
		MappedConstantType,
		/**
		 * A constant that has been mapped to an ontology
		 * (e.g. "dbpedia-owl:author" or "dbpedia:Dan_Brown")
		 **/
		UnmappedConstantType
	}
	
	public static class Element {
		public String name;
		// variables as well as constants may be typed
		public String type = null;
	}
	
	public static class Constant extends Element {
		public ConstantType type;
		
		public Constant(String name, ConstantType type) {
			this.name = name;
			this.type = type;
		}
		
		public String toString() {
			if (type == ConstantType.MappedConstantType) {
				return name;
			} else /*if (type == ConstantType.UnmappedConstantType)*/ {
				return "\"" + name + "\"";
			}
		}
	}
	
	public static class Variable extends Element {
		public Variable(String name, String type) {
			this.name = name;
			this.type = type;
		}
		
		public String toString() {
			return "?" + name;
		}
	}
	
	public Element subject;
	public Element predicate;
	public Element object;
	
	public SPARQLTriple(Element s, Element p, Element o) {
		subject = s;
		predicate = p;
		object = o;
	}
	
	public String toString() {
		return "[Subject: " + subject + "] [Predicate: " + predicate + "] [Object: " + object + "]";
	}
}