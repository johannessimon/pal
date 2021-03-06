package de.tudarmstadt.lt.pal;

import java.util.LinkedList;
import java.util.List;

/**
 * String that is mapped from one representation to another, possibly via multiple steps
 * e.g. write -> author (synonym) -> dbpedia-owl:author (URI match)
 */
public class MappedString {
	public String value;
	public List<TraceElement> trace = new LinkedList<TraceElement>();
	
	public static class TraceElement {
		public String value;
		public String url;
		public TraceElement(String value, String url) {
			this.value = value;
			this.url = url;
		}
		@Override public String toString() { return value + " (" + url + ")"; }
	}
	
	public MappedString(String word) {
		this.value = word;
		trace.add(new TraceElement(word, ""));
	}
	
	public MappedString(String word, List<TraceElement> trace) {
		this.value = word;
		this.trace.addAll(trace);
	}

	@Override
	public String toString() {
		return value;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((value == null) ? 0 : value.hashCode());
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
		MappedString other = (MappedString) obj;
		if (value == null) {
			if (other.value != null)
				return false;
		} else if (!value.equals(other.value))
			return false;
		return true;
	}
}