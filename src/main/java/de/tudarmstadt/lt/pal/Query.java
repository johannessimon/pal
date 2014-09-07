package de.tudarmstadt.lt.pal;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;

import de.tudarmstadt.lt.pal.Triple.Element;
import de.tudarmstadt.lt.pal.Triple.Variable;

/**
 * A pseudo query is a collection of (unmapped) <code>SPARQLTriple</code>'s.<br/>
 * For convenience, it also contains a map of all variables used in the triples.
 */
public class Query {
	public Collection<Triple> triples;
	/**
	 * Map of (variable name -> SPARQL variable) pairs
	 */
	public Map<String, Variable> vars;
	public Variable focusVar = null;

	public Query() {
		triples = new LinkedList<>();
		vars = new HashMap<>();
	}
	
	@Override
	public Object clone() {
		Query q = new Query();
		for (Entry<String, Variable> var : vars.entrySet()) {
			q.vars.put(var.getKey(), (Variable)var.getValue().clone());
		}
		for (Triple t : triples) {
			Element s = (t.subject instanceof Variable) ? q.vars.get(t.subject.name) : (Element)t.subject.clone();
			Element p = t.predicate != null ? (Element)t.predicate.clone() : null;
			Element o = (t.object instanceof Variable) ? q.vars.get(t.object.name) : (Element)t.object.clone();
			q.triples.add(new Triple(s, p, o));
		}
		q.focusVar = focusVar != null ? q.vars.get(focusVar.name) : null;
		return q;
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("SELECT * WHERE {\n");
		for(Triple t : triples) {
			sb.append("   ");
			sb.append(t);
			sb.append("\n");
		}
		for (Variable var : vars.values()) {
			if (var.unmappedType != null) {
				sb.append("   [?");
				sb.append(var.name);
				sb.append(" TYPE ");
				sb.append(var.mappedType != null ? var.mappedType : var.unmappedType);
				sb.append("]\n");
			}
		}
		sb.append("}");
		return sb.toString();
	}
	
	@Override
	public boolean equals(Object other) {
		Query otherPQ = (Query)other;
		if (otherPQ == null) {
			return false;
		}
		return triples.size() == otherPQ.triples.size() &&
			   triples.containsAll(otherPQ.triples);
	}
}
