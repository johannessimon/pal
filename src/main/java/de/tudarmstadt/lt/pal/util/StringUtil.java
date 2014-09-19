package de.tudarmstadt.lt.pal.util;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class StringUtil {
	/**
	 * Tests if <code>haystack</code> has word parts starting with <code>needle</code>
	 */
	public static boolean hasPartStartingWith(String haystack, String needle) {
		// ("abc def", "abc") -> match
		// ("abc def", "def") -> match
		// ("abc def", "de") -> match
		// ("abc def", "bc") -> no match
		// ("abc def", "ef") -> no match
		return (" " + haystack).contains(" " + needle);
	}
	/**
	 * Tests if <code>haystack</code> has word parts starting with <code>needle</code>
	 */
	public static boolean hasPart(String haystack, String needle) {
		// ("abc def", "abc") -> match
		// ("abc def", "def") -> match
		// ("abc def", "de") -> no match
		// ("abc def", "bc") -> no match
		// ("abc def", "ef") -> no match
		return (" " + haystack + " ").contains(" " + needle + " ");
	}
	
	public static String longestCommonSubstring(String S1, String S2)
	{
	    int Start = 0;
	    int Max = 0;
	    for (int i = 0; i < S1.length(); i++)
	    {
	        for (int j = 0; j < S2.length(); j++)
	        {
	            int x = 0;
	            while (S1.charAt(i + x) == S2.charAt(j + x))
	            {
	                x++;
	                if (((i + x) >= S1.length()) || ((j + x) >= S2.length())) break;
	            }
	            if (x > Max)
	            {
	                Max = x;
	                Start = i;
	            }
	         }
	    }
	    return S1.substring(Start, (Start + Max));
	}
	
	/**
	 * Returns all partial words of the given word, e.g.
	 * "official web site" -> "official", "web", "site", "official web", "web site", "official web site"
	 */
	/*public static Collection<ComparablePair<String, Float>> getPartialWords(String word) {
		Set<ComparablePair<String, Float>> res = new HashSet<>();
		String[] tokens = word.split(" ");
		for (int l = 1; l <= tokens.length; l++) {
			for (int i = 0; i < tokens.length; i++) {
				String partialWord = "";
				for (int j = 0; j < l && i+j < tokens.length; j++) {
					partialWord += " " + tokens[i+j];
				}
				partialWord = partialWord.trim();
				res.add(new ComparablePair<String, Float>(partialWord, (float)partialWord.length() / word.length()));
			}
		}
		return res;
	}*/
	
	/**
	 * Returns semantically meaningful partial words of the given word, e.g.
	 * "official web site" -> "official web site", "web site", "site"
	 */
	public static Collection<ComparablePair<String, Float>> getPartialMainWords(String word) {
		List<ComparablePair<String, Float>> res = new LinkedList<>();
		String[] tokens = word.split(" ");
		for (int i = tokens.length - 1; i >= 0; i--) {
			String partialWord = "";
			for (int j = i; j < tokens.length; j++) {
				partialWord += " " + tokens[j];
			}
			partialWord = partialWord.trim();
			res.add(new ComparablePair<String, Float>(partialWord, (float)partialWord.length() / word.length()));
		}
		return res;
	}
}
