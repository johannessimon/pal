package de.tudarmstadt.lt.pal.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.trees.GrammaticalRelation;

public class DependencyPatternParser {
	public static class DependencyPattern {
		Collection<DependencyNodePattern> parentPatterns;
		Collection<DependencyRelPattern> relPatterns;
		Collection<DependencyNodePattern> childPatterns;
		
		public TripleElementMapping subjectMapping;
		public TripleElementMapping predicateMapping;
		public TripleElementMapping objectMapping;
		
		int sourceLine; // for debugging purposes only
		
		@Override
		public String toString() { return "#" + sourceLine; }
		
		public boolean matches(IndexedWord parent, GrammaticalRelation rel, IndexedWord child) {
			boolean parentMatches = false;
			boolean relMatches = false;
			boolean childMatches = false;
			for (DependencyNodePattern p : parentPatterns) {
				if (p.matches(parent)) {
					parentMatches = true;
					break;
				}
			}
			for (DependencyRelPattern r : relPatterns) {
				if (r.matches(rel)) {
					relMatches = true;
					break;
				}
			}
			for (DependencyNodePattern p : childPatterns) {
				if (p.matches(child)) {
					childMatches = true;
					break;
				}
			}
			return parentMatches && relMatches && childMatches;
		}
		
		public IndexedWord mapTripleElement(IndexedWord e, TripleElementMapping m, IndexedWord parent, GrammaticalRelation rel, IndexedWord child) {
			switch (m) {
			case DependencyChild:
				return child;
			case DependencyParent:
				return parent;
			case Open:
				return e; // do not modify
			default: // case Wildcard:
				return null;
			}
		}
	}
	
	public static enum TripleElementMapping {
		DependencyParent, // "x"
		DependencyChild, // "y"
		Wildcard, // "*"
		Open // "?"
	}
	
	/**
	 * Pattern to match {@link edu.stanford.nlp.trees.GrammaticalRelation}
	 */
	public static class DependencyRelPattern {
		// GrammaticalRelation.getName().getShortName()
		public String name;
		// GrammaticalRelation.getSpecific()
		public String specific;
		
		public boolean matches(GrammaticalRelation rel) {
			return matchesRelName(rel, name) &&
				   (specific == null || rel.getSpecific() != null && rel.getSpecific().matches(specific));
		}
	}
	
	/**
	 * Use hierarchy of dependency types to match names
	 */
	static boolean matchesRelName(GrammaticalRelation rel, String namePattern) {
		if (rel == null) {
			return false;
		} else if (rel.getShortName().matches(namePattern)) {
			return true;
		}
		return matchesRelName(rel.getParent(), namePattern);
	}
	
	/**
	 * Pattern to match {@link edu.stanford.nlp.ling.IndexedWord}
	 */
	public static class DependencyNodePattern {
		// IndexedWord.lemma()
		public String lemma;
		// IndexedWord.tag()
		public String tag;
		
		public boolean matches(IndexedWord node) {
			return node.lemma().toLowerCase().matches(lemma) &&
				   (tag == null || node.tag().toLowerCase().matches(tag));
		}
	}
	
	public static Collection<DependencyPattern> parse(InputStream is) {
		List<DependencyPattern> res = new LinkedList<>();
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(is));
			String line;
			int lineNo = 1;
			while ((line = reader.readLine()) != null) {
				DependencyPattern p = parse(line);
				p.sourceLine = lineNo;
				res.add(p);
				lineNo++;
			}
			reader.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return res;
	}
	
	static DependencyPattern parse(String line) {
		String[] lineParts = line.split("\t");
		String[] triplePattern = lineParts[0].split(" ");
		String[] depPattern = lineParts[1].split(" ");
		
		DependencyPattern res = new DependencyPattern();
		res.parentPatterns = mapDependencyNodePattern(depPattern[0]);
		res.relPatterns = mapDependencyRelPattern(depPattern[1]);
		res.childPatterns = mapDependencyNodePattern(depPattern[2]);
		
		res.subjectMapping = mapTripleElementMapping(triplePattern[0]);
		if (triplePattern.length > 1) {
			res.predicateMapping = mapTripleElementMapping(triplePattern[1]);
			res.objectMapping = mapTripleElementMapping(triplePattern[2]);
		}
		
		return res;
	}
	
	static Collection<DependencyNodePattern> mapDependencyNodePattern(String p) {
		Collection<DependencyNodePattern> res = new LinkedList<>();
		String[] orParts = p.split("\\|");
		for (String orPart : orParts) {
			DependencyNodePattern pattern = new DependencyNodePattern();
			String[] pParts = orPart.split("#");
			pattern.lemma = pParts[0].replace("*", ".*");
			if (pParts.length > 1) {
				pattern.tag = pParts[1].replace("*", ".*");
			}
			res.add(pattern);
		}
		return res;
	}
	
	static Collection<DependencyRelPattern> mapDependencyRelPattern(String p) {
		Collection<DependencyRelPattern> res = new LinkedList<>();
		String[] orParts = p.split("\\|");
		for (String orPart : orParts) {
			DependencyRelPattern pattern = new DependencyRelPattern();
			String[] pParts = orPart.split("_");
			pattern.name = pParts[0].replace("*", ".*");
			if (pParts.length > 1) {
				pattern.specific = pParts[1].replace("*", ".*");
			}
			res.add(pattern);
		}
		return res;
	}
	
	static TripleElementMapping mapTripleElementMapping(String s) {
		if (s.equals("x")) {
			return TripleElementMapping.DependencyParent;
		} else if (s.equals("y")) {
			return TripleElementMapping.DependencyChild;
		} else if (s.equals("*")) {
			return TripleElementMapping.Wildcard;
		} else {
			return TripleElementMapping.Open;
		}
	}
}
