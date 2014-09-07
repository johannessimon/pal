package de.tudarmstadt.lt.pal;

import java.util.LinkedList;
import java.util.List;

public class MappedString {
	public String word;
	public List<String> trace = new LinkedList<>();
	
	public MappedString(String word) {
		this.word = word;
		trace.add(word);
	}
	
	public MappedString(String word, List<String> trace) {
		this.word = word;
		this.trace.addAll(trace);
	}

	@Override
	public String toString() {
		return word;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((word == null) ? 0 : word.hashCode());
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
		if (word == null) {
			if (other.word != null)
				return false;
		} else if (!word.equals(other.word))
			return false;
		return true;
	}
}