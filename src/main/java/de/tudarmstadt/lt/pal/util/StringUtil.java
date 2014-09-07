package de.tudarmstadt.lt.pal.util;

public class StringUtil {
	/**
	 * Tests if <code>haystack</code> has word parts starting with <code>needle</code>
	 */
	public static boolean hasPartStartingWith(String haystack, String needle) {
		// ("abc def", "abc") -> match
		// ("abc def", "def") -> match
		// ("abc def", "bc") -> no match
		// ("abc def", "ef") -> no match
		return (" " + haystack).contains(" " + needle);
	}
}
