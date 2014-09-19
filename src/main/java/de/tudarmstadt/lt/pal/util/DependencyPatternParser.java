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
		Collection<DependencyNodePattern> yPatterns;
		Collection<DependencyRelPattern> relPatterns;
		Collection<DependencyNodePattern> zPatterns;
		
		public TripleElementMapping subjectMapping;
		public TripleElementMapping predicateMapping;
		public TripleElementMapping objectMapping;
		
		int sourceLine; // for debugging purposes only
		
		@Override
		public String toString() { return "#" + sourceLine; }
		
		public boolean isAntiPattern() { return subjectMapping == TripleElementMapping.Open &&
											    predicateMapping == TripleElementMapping.Open &&
											    objectMapping == TripleElementMapping.Open; }
		
		public boolean matches(GrammaticalRelation rel, IndexedWord x, IndexedWord y, IndexedWord z) {
			// x can be null, match only if it is either not used or not null
			if (subjectMapping.equals(TripleElementMapping.X) && x == null ||
				predicateMapping != null && predicateMapping.equals(TripleElementMapping.X) && x == null ||
				objectMapping != null && objectMapping.equals(TripleElementMapping.X) && x == null ) {
				return false;
			}
			boolean yMatches = false;
			boolean relMatches = false;
			boolean zMatches = false;
			for (DependencyNodePattern p : yPatterns) {
				if (p.matches(y)) {
					yMatches = true;
					break;
				}
			}
			for (DependencyRelPattern r : relPatterns) {
				if (r.matches(rel)) {
					relMatches = true;
					break;
				}
			}
			for (DependencyNodePattern p : zPatterns) {
				if (p.matches(z)) {
					zMatches = true;
					break;
				}
			}
			return yMatches && relMatches && zMatches;
		}
		
		public IndexedWord mapTripleElement(IndexedWord e, TripleElementMapping m, GrammaticalRelation rel, IndexedWord x, IndexedWord y, IndexedWord z) {
			switch (m) {
			case X:
				return x;
			case Y:
				return y;
			case Z:
				return z;
			case Open:
				return e; // do not modify
			default: // case Wildcard:
				return null;
			}
		}
	}
	
	public static enum TripleElementMapping {
		X, // "x"
		Y, // "y"
		Z, // "y"
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
		List<DependencyPattern> res = new LinkedList<DependencyPattern>();
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
		line = line.replaceAll("//.*", "").trim(); // Remove comment
		String[] lineParts = line.split("\t");
		String[] triplePattern = lineParts[0].split(" ");
		String[] depPattern = lineParts[1].split(" ");
		
		DependencyPattern res = new DependencyPattern();
		res.yPatterns = mapDependencyNodePattern(depPattern[0]);
		res.relPatterns = mapDependencyRelPattern(depPattern[1]);
		res.zPatterns = mapDependencyNodePattern(depPattern[2]);
		
		res.subjectMapping = mapTripleElementMapping(triplePattern[0]);
		if (triplePattern.length > 1) {
			res.predicateMapping = mapTripleElementMapping(triplePattern[1]);
		}
		if (triplePattern.length > 2) {
			res.objectMapping = mapTripleElementMapping(triplePattern[2]);
		}
		
		return res;
	}
	
	static Collection<DependencyNodePattern> mapDependencyNodePattern(String p) {
		Collection<DependencyNodePattern> res = new LinkedList<DependencyNodePattern>();
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
		Collection<DependencyRelPattern> res = new LinkedList<DependencyRelPattern>();
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
			return TripleElementMapping.X;
		} else if (s.equals("y")) {
			return TripleElementMapping.Y;
		} else if (s.equals("z")) {
			return TripleElementMapping.Z;
		} else if (s.equals("*")) {
			return TripleElementMapping.Wildcard;
		} else if (s.equals("?")) {
			return TripleElementMapping.Open;
		}
		return null;
	}
}
