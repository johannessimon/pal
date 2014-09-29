package de.tudarmstadt.lt.pal;

import java.util.Arrays;
import java.util.List;


/**
 * Represents a triple that can be used in a SPARQL query. May contain
 * yet unmapped properties/resources.
 */
public class Triple implements Cloneable {
	
	public static abstract class Element implements Cloneable {
		public String name;
		public List<String> trace;
		
		public abstract boolean isConstant();
		
		public String sparqlString() {
			return name;
		}
		
		@Override
		public String toString() {
			return name;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((name == null) ? 0 : name.hashCode());
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
			return true;
		}
		
		@Override
		public Object clone() {
			Object clone = null;
			try {
				clone = super.clone();
			} catch (CloneNotSupportedException e) {
				e.printStackTrace();
			}
			return clone;
		}
	}
	
	/**
	 * A type constraint for a variable (or resource), which distinguishes between "resource" and
	 * "literal", as well as a specific URI for the type, e.g. (BasicType.Literal, xsd:date) or
	 * (BasicType.Resource, bibo:Book)
	 */
	public static class TypeConstraint {
		public enum BasicType {
			Resource,
			Literal
		}
		public BasicType basicType;
		public MappedString typeURI;
		
		public TypeConstraint(BasicType basicType, MappedString typeURI) {
			this.basicType = basicType;
			this.typeURI = typeURI;
		}
		
		@Override
		public String toString() {
			return typeURI + " (" + basicType + ")";
		}
	}
	
	/**
	 * A triple element that is no variable,
	 * e.g. "dbpedia:Dan_Brown" and "dbpedia-owl:author" in
	 * [dbpedia:Dan_Brown dbpedia-owl:author ?book]
	 */
	public static class Constant extends Element {
		public String type;
		
		public Constant(String name) {
			this.name = name;
		}
		
		@Override
		public boolean isConstant() {
			return true;
		}
	}
	
	/**
	 * A variable, e.g. "?book" in [dbpedia:Dan_Brown dbpedia-owl:author ?book]
	 */
	public static class Variable extends Element {
		public enum Type {
			Agent,
			Date,
			Place,
			Number,
			Literal,
			Unknown
		}
		
		public Type unmappedType;
		public TypeConstraint mappedType;
		
		public Variable(String name, Type unmappedType) {
			this.name = name;
			this.unmappedType = unmappedType;
			// A variable name does not have a real derivation trace
			this.trace = Arrays.asList(name);
		}
		
		@Override
		public boolean isConstant() {
			return false;
		}
		
		@Override
		public String sparqlString() {
			return "?" + name;
		}

		@Override
		public String toString() {
			return "?" + name + " (" + unmappedType + ", " + mappedType + ")";
		}
	}
	
	public Element subject;
	public Element predicate;
	public Element object;
	
	public Triple(Element s, Element p, Element o) {
		subject = s;
		predicate = p;
		object = o;
	}
	
	/**
	 * Produces a human-readable representation of this triple. Not meant for SPARQL etc.
	 */
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
		Triple other = (Triple) obj;
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