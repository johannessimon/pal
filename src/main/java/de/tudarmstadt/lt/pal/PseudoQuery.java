package de.tudarmstadt.lt.pal;

import java.util.Collection;
import java.util.Map;

public class PseudoQuery {
	public Collection<SPARQLTriple> triples;
	/**
	 * Map of (variable name -> SPARQL variable) pairs
	 */
	public Map<String, SPARQLTriple.Variable> vars;
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("SELECT * WHERE {\n");
		for(SPARQLTriple t : triples) {
			sb.append("   ");
			sb.append(t);
			sb.append("\n");
		}
		for (SPARQLTriple.Variable var : vars.values()) {
			if (var.typeName != null) {
				sb.append("   [?");
				sb.append(var.name);
				sb.append(" TYPE ");
				sb.append(var.typeName);
				sb.append("]\n");
			}
		}
		sb.append("}");
		return sb.toString();
	}
	
	@Override
	public boolean equals(Object other) {
		PseudoQuery otherPQ = (PseudoQuery)other;
		if (otherPQ == null) {
			return false;
		}
		return triples.size() == otherPQ.triples.size() &&
			   triples.containsAll(otherPQ.triples);
	}
}
