package de.tudarmstadt.lt.pal.wordnet;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import edu.mit.jwi.Dictionary;
import edu.mit.jwi.IDictionary;
import edu.mit.jwi.item.IIndexWord;
import edu.mit.jwi.item.IPointer;
import edu.mit.jwi.item.ISynset;
import edu.mit.jwi.item.ISynsetID;
import edu.mit.jwi.item.IWord;
import edu.mit.jwi.item.IWordID;
import edu.mit.jwi.item.POS;
import edu.mit.jwi.item.Pointer;

public class WordNetConnector {
	IDictionary dict;
	public WordNetConnector(String dir) {
		try {
			dict = new Dictionary(new File(dir));
			dict.open();
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void addSynonyms(Map<String, Float> synonyms, Map<String, Float> toAdd) {
		addSynonyms(synonyms, toAdd, 1.0f);
	}
	
	public void addSynonyms(Map<String, Float> synonyms, Map<String, Float> toAdd, float factor) {
		for (Entry<String, Float> s : toAdd.entrySet()) {
			addSynonym(synonyms, s.getKey(), s.getValue());
		}
	}
	
	public void addSynonym(Map<String, Float> synonymScores, String synonym, float score) {
		Float existingScore = synonymScores.get(synonym);
		// Insert only if no such synonym exists yet or if
		// we've found a better score for the same synonym
		if (existingScore == null || score > existingScore) {
			synonymScores.put(synonym, score);
		}
	}
	
	public Map<String, Float> getSynonyms(String word, String posStr) {
		Map<String, Float> synonymScores = new HashMap<>();
		POS pos;
		if (posStr.startsWith("n")) {
			pos = POS.NOUN;
		} else if (posStr.startsWith("v")) {
			pos = POS.VERB;
		} else if (posStr.startsWith("j")) {
			pos = POS.ADJECTIVE;
		} else if (posStr.startsWith("a")) {
			pos = POS.ADVERB;
		} else {
			return new HashMap<>();
		}
		IIndexWord idxWord = dict.getIndexWord(word, pos);
		if (idxWord == null) {
			return new HashMap<>();
		}
		// Use hypernyms to find related words, but assign a penalty
		// as hypernyms are a "bad" way to find synonyms
		// (human is not a synonym of author, but the other way around)
		float hypernymPenalty = 0.0f;
		for (IWordID wordID : idxWord.getWordIDs()) {
			IWord w = dict.getWord(wordID);
			addSynonyms(synonymScores, getHyponyms(w.getSynset(), 3));
			addSynonyms(synonymScores, getHypernyms(w.getSynset(), 3), hypernymPenalty);
			addSynonym(synonymScores, w.getLemma(), 1.0f);
			// Get direct and transitive synonyms
			addSynonyms(synonymScores, getSynonyms(w, 1, 2, w.getLemma()));
			
			List<IWordID> rWordIDs = w.getRelatedWords(Pointer.DERIVATIONALLY_RELATED);
			for (IWordID rWordID : rWordIDs) {
				IWord rW = dict.getWord(rWordID);
				addSynonym(synonymScores, rW.getLemma(), 1.0f);
				addSynonyms(synonymScores, getHyponyms(w.getSynset(), 3));
				addSynonyms(synonymScores, getHypernyms(w.getSynset(), 3), hypernymPenalty);
				
				// Get direct and transitive synonyms
				addSynonyms(synonymScores, getSynonyms(rW, 1, 2, w.getLemma() + "->" + rW.getLemma()));
			}
		}
		
//		System.out.println(synonyms);
		return synonymScores;
	}
	
	public Map<String, Float> getSynonyms(IWord word, int depth, int maxDepth, String source) {
		Map<String, Float> res = new HashMap<>();
		if (depth > maxDepth) {
			return res;
		}
		List<IWordID> words = dict.getIndexWord(word.getLemma(), word.getPOS()).getWordIDs();
		for (IWordID sameWordInOtherSynset : words) {
			ISynset s = dict.getWord(sameWordInOtherSynset).getSynset();
			for (IWord synonym : s.getWords()) {
				float score = 1.0f - (float)depth / (maxDepth + 1);
				addSynonym(res, /*source + "/" + */synonym.getLemma(), score);
				addSynonyms(res, getSynonyms(synonym, depth + 1, maxDepth, source + "/" + synonym.getLemma()));
			}
		}
		return res;
	}
	
	public Map<String, Float> getHypernyms(ISynset s, int maxDepth) {
		return getRelatedWords(s, 1, maxDepth, Pointer.HYPERNYM);
	}
	
	public Map<String, Float> getHyponyms(ISynset s, int maxDepth) {
		return getRelatedWords(s, 1, maxDepth, Pointer.HYPONYM);
	}
	
	public Map<String, Float> getRelatedWords(ISynset s, int depth, int maxDepth, IPointer relationType) {
		if (depth == maxDepth) {
			return Collections.emptyMap();
		}
		float scoreInThisDepth = 1 - (float)depth / maxDepth;
		Map<String, Float> res = new HashMap<>();
//		Map foo = word.getRelatedMap();
		List<ISynsetID> sIDs = s.getRelatedSynsets(relationType);
		for (ISynsetID sID : sIDs) {
			ISynset hS = dict.getSynset(sID);
			for (IWord h : hS.getWords()) {
//				if (h.getLemma().equals("bridge")) {
//					System.out.println("stop");
//				}
				addSynonym(res, h.getLemma(), scoreInThisDepth);
			}
			addSynonyms(res, getRelatedWords(hS, depth + 1, maxDepth, relationType));
		}
		return res;
	}
	
	public static void main(String[] args) {
		WordNetConnector wnc = new WordNetConnector("/usr/local/Cellar/wordnet/3.1/dict/");
		wnc.getSynonyms("start", "v");
	}
}
