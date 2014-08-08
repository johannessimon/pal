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
			if (var.type != null) {
				sb.append("   [?");
				sb.append(var.name);
				sb.append(" TYPE ");
				sb.append(var.type);
				sb.append("]\n");
			}
		}
		sb.append("}");
		return sb.toString();
	}
}
