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
		
		public String sparqlString() {
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
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return clone;
		}
	}
	
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
	
	public static class Constant extends Element {
		public enum Type {
			/**
			 * A constant that has not been mapped to an ontology
			 * (e.g. "author" or "Dan Brown")
			 **/
			Mapped,
			/**
			 * A constant that has been mapped to an ontology
			 * (e.g. "dbpedia-owl:author" or "dbpedia:Dan_Brown")
			 **/
			Unmapped
		}
		
		public Type type;
		
		public Constant(String name, Type type) {
			this.name = name;
			this.type = type;
		}
		
		public String toString() {
			if (type == Type.Mapped) {
				// dbpedia-owl:Agent vs. <http://dbpedia.org/ontology/Agent>
				return name.contains(":") ? name : "<" + name + ">";
			} else /*if (type == ConstantType.UnmappedConstantType)*/ {
				return "\"" + name + "\"";
			}
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = super.hashCode();
			result = prime * result + ((type == null) ? 0 : type.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (!super.equals(obj))
				return false;
			if (getClass() != obj.getClass())
				return false;
			Constant other = (Constant) obj;
			if (type != other.type)
				return false;
			return true;
		}
	}
	
	public static class Variable extends Element {
		public enum Type {
			Agent,
			Date,
			Place,
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
		/*
		private Variable(String name, TypeConstraint mappedType) {
			this.name = name;
			this.unmappedType = null;
			this.mappedType = mappedType;
		}*/
		
		public String sparqlString() {
			return "?" + name;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = super.hashCode();
			result = prime * result + ((unmappedType == null) ? 0 : unmappedType.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (!super.equals(obj))
				return false;
			if (getClass() != obj.getClass())
				return false;
			Variable other = (Variable) obj;
			if (unmappedType != other.unmappedType)
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "?" + name + " (" + unmappedType + ", "
					+ mappedType + ")";
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