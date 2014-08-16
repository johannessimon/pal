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
		public String typeName = null;
		
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((name == null) ? 0 : name.hashCode());
			result = prime * result
					+ ((typeName == null) ? 0 : typeName.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Element other = (Element) obj;
			if (name == null) {
				if (other.name != null)
					return false;
			} else if (!name.equals(other.name))
				return false;
			if (typeName == null) {
				if (other.typeName != null)
					return false;
			} else if (!typeName.equals(other.typeName))
				return false;
			return true;
		}
	}
	
	public static class Constant extends Element {
		public ConstantType type;
		
		public Constant(String name, ConstantType type) {
			this.name = name;
			this.type = type;
		}
		
		public String toString() {
			if (type == ConstantType.MappedConstantType) {
				// dbpedia-owl:Agent vs. <http://dbpedia.org/ontology/Agent>
				return name.contains(":") ? name : "<" + name + ">";
			} else /*if (type == ConstantType.UnmappedConstantType)*/ {
				return "\"" + name + "\"";
			}
		}
		
		@Override
		public boolean equals(Object other) {
			if (!super.equals(other)) {
				return false;
			}
			Constant otherC = (Constant)other;
			if (otherC == null) {
				return false;
			}
			return this.type == otherC.type;
		}
	}
	
	public static class Variable extends Element {
		public Variable(String name, String type) {
			this.name = name;
			this.typeName = type;
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
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((object == null) ? 0 : object.hashCode());
		result = prime * result
				+ ((predicate == null) ? 0 : predicate.hashCode());
		result = prime * result + ((subject == null) ? 0 : subject.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SPARQLTriple other = (SPARQLTriple) obj;
		if (object == null) {
			if (other.object != null)
				return false;
		} else if (!object.equals(other.object))
			return false;
		if (predicate == null) {
			if (other.predicate != null)
				return false;
		} else if (!predicate.equals(other.predicate))
			return false;
		if (subject == null) {
			if (other.subject != null)
				return false;
		} else if (!subject.equals(other.subject))
			return false;
		return true;
	}
}