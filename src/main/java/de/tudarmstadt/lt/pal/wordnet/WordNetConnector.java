package de.tudarmstadt.lt.pal.wordnet;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import de.tudarmstadt.lt.pal.MappedString;
import de.tudarmstadt.lt.pal.util.ComparablePair;
import de.tudarmstadt.lt.pal.util.StringUtil;
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
		} catch (IOException e) {
			throw new RuntimeException("Failed to initialize WordNetConnector from " + dir, e);
		}
	}
	
	public void addSynonyms(Map<MappedString, Float> synonyms, Map<MappedString, Float> toAdd) {
		addSynonyms(synonyms, toAdd, 1.0f);
	}
	
	public void addSynonyms(Map<MappedString, Float> synonyms, Map<MappedString, Float> toAdd, float factor) {
		for (Entry<MappedString, Float> s : toAdd.entrySet()) {
			addSynonym(synonyms, s.getKey(), s.getValue()*factor);
		}
	}
	

	public void addSynonym(Map<MappedString, Float> synonymScores, String synonym, List<String> trace, float score) {
		MappedString mappedWord = new MappedString(synonym, trace);
		addSynonym(synonymScores, mappedWord, score);
	}
	
	public void addSynonym(Map<MappedString, Float> synonymScores, MappedString synonym, float score) {
		synonym.value = synonym.value.replaceAll("_", " ");
		Float existingScore = synonymScores.get(synonym);
		// Insert only if no such synonym exists yet or if
		// we've found a better score for the same synonym
		if (existingScore == null || score > existingScore) {
			synonymScores.put(synonym, score);
		}
	}
	
	private POS posFromString(String posStr) {
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
			pos = null;
		}
		return pos;
	}
	
	public Map<MappedString, Float> getSynonymsAndHypernyms(String word, String posStr) {
		Map<MappedString, Float> synonymScores = new HashMap<MappedString, Float>();
		POS pos = posFromString(posStr);
		if (pos == null) {
			return new HashMap<MappedString, Float>();
		}
		Collection<ComparablePair<String, Float>> partialWords = StringUtil.getPartialMainWords(word);
		for (ComparablePair<String, Float> partialWord : partialWords) {
			IIndexWord idxWord;
			synchronized (dict) {
				idxWord = dict.getIndexWord(partialWord.key, pos);
			}
			if (idxWord == null) {
				continue;
			}
			List<String> trace = new LinkedList<String>();
			trace.add(word);
			if (!partialWord.key.equals(word)) {
				trace.add(idxWord.getLemma() + " (partial word)");
			}
			for (IWordID wordID : idxWord.getWordIDs()) {
				IWord w;
				synchronized (dict) {
					w = dict.getWord(wordID);
				}
				// Get direct and transitive synonyms
				addSynonyms(synonymScores, getSynonyms(w, 1, 2, trace), partialWord.value);
				// must be > 0.1 because queries without type constraints are scored with a factor of 0.1
				float hypernymPenalty = 0.5f;
				addSynonyms(synonymScores, getHypernyms(w.getSynset(), 3, trace), partialWord.value*hypernymPenalty);
			}
		}
		
		return synonymScores;
	}
	
	public Map<MappedString, Float> getRelatedWords(String word, String posStr) {
		Map<MappedString, Float> synonymScores = new HashMap<MappedString, Float>();
		POS pos = posFromString(posStr);
		if (pos == null) {
			return new HashMap<MappedString, Float>();
		}
		List<String> trace = Arrays.asList(word);
		Collection<ComparablePair<String, Float>> partialWords = StringUtil.getPartialMainWords(word);
		for (ComparablePair<String, Float> partialWord : partialWords) {
			float factor = partialWord.value;
			IIndexWord idxWord;
			synchronized (dict) {
				idxWord = dict.getIndexWord(partialWord.key, pos);
			}
			if (idxWord == null) {
				continue;
			}
			// Use hypernyms to find related words, but assign a penalty
			// as hypernyms are a "bad" way to find synonyms
			// (human is not a synonym of author, but the other way around)
			float hypernymPenalty = 0.1f;
			for (IWordID wordID : idxWord.getWordIDs()) {
				IWord w;
				synchronized (dict) {
					w = dict.getWord(wordID);
				}
				List<String> _trace = new LinkedList<String>(trace);
				// only add "partial node" notice if it actually is only a part
				if (!w.getLemma().equals(word)) {
					_trace.add(w.getLemma() + " (partial word)");
				}
				addSynonyms(synonymScores, getHyponyms(w.getSynset(), 3, _trace), factor);
				addSynonyms(synonymScores, getHypernyms(w.getSynset(), 3, _trace), factor*hypernymPenalty);
				addSynonym(synonymScores, w.getLemma(), _trace, factor);
				// Get direct and transitive synonyms
				addSynonyms(synonymScores, getSynonyms(w, 1, 2, _trace), factor);
				
				List<IWordID> rWordIDs = w.getRelatedWords(Pointer.DERIVATIONALLY_RELATED);
				for (IWordID rWordID : rWordIDs) {
					IWord rW;
					synchronized (dict) {
						rW = dict.getWord(rWordID);
					}
					List<String> __trace = new LinkedList<String>(_trace);
					__trace.add(rW.getLemma() + " (related form)");
					addSynonym(synonymScores, rW.getLemma(), __trace, factor);
					addSynonyms(synonymScores, getHyponyms(rW.getSynset(), 3, __trace), factor);
					addSynonyms(synonymScores, getHypernyms(rW.getSynset(), 3, __trace), factor*hypernymPenalty);
					
					// Get direct and transitive synonyms
					addSynonyms(synonymScores, getSynonyms(rW, 1, 2, __trace));
				}
			}
		}
		
		return synonymScores;
	}
	
	public Map<MappedString, Float> getSynonyms(IWord word, int depth, int maxDepth, List<String> trace) {
		Map<MappedString, Float> res = new HashMap<MappedString, Float>();
		if (depth > maxDepth) {
			return res;
		}
		List<IWordID> words;
		synchronized (dict) {
			words = dict.getIndexWord(word.getLemma(), word.getPOS()).getWordIDs();
		}
		for (IWordID sameWordInOtherSynset : words) {
			ISynset s;
			synchronized (dict) {
				s = dict.getWord(sameWordInOtherSynset).getSynset();
			}
			for (IWord synonym : s.getWords()) {
				float score = 1.0f - (float)depth / (maxDepth + 1);
				List<String> _trace = new LinkedList<String>(trace);
				// we don't have to mention that a word is a synonym of itself
				if (!word.getLemma().equals(synonym.getLemma())) {
					_trace.add(synonym.getLemma() + " (synonym)");
				}
				addSynonym(res, synonym.getLemma(), _trace, score);
				addSynonyms(res, getSynonyms(synonym, depth + 1, maxDepth, _trace));
			}
		}
		return res;
	}
	
	public Map<MappedString, Float> getHypernyms(ISynset s, int maxDepth, List<String> trace) {
		return getRelatedWords(s, 1, maxDepth, Pointer.HYPERNYM, true, trace);
	}
	
	public Map<MappedString, Float> getHyponyms(ISynset s, int maxDepth, List<String> trace) {
		return getRelatedWords(s, 1, maxDepth, Pointer.HYPONYM, true, trace);
	}
	
	public Map<MappedString, Float> getRelatedWords(ISynset s, int depth, int maxDepth, IPointer relationType, boolean assignDepthPenalty, List<String> trace) {
		if (depth == maxDepth) {
			return Collections.emptyMap();
		}
		float scoreInThisDepth = 1.0f;
		if (assignDepthPenalty) {
			scoreInThisDepth -= (float)depth / maxDepth;
		}
		Map<MappedString, Float> res = new HashMap<MappedString, Float>();
//		Map foo = word.getRelatedMap();
		List<ISynsetID> sIDs = s.getRelatedSynsets(relationType);
		for (ISynsetID sID : sIDs) {
			ISynset hS;
			synchronized (dict) {
				hS = dict.getSynset(sID);
			}
			List<String> _trace = new LinkedList<String>(trace);
			_trace.add(hS.getGloss() + " (" + relationType.getName() + ")");
			for (IWord h : hS.getWords()) {
				List<String> __trace = new LinkedList<String>(trace);
				__trace.add(h.getLemma() + " (" + relationType.getName() + ")");
				addSynonym(res, h.getLemma(), __trace, scoreInThisDepth);
			}
			addSynonyms(res, getRelatedWords(hS, depth + 1, maxDepth, relationType, assignDepthPenalty, _trace));
		}
		return res;
	}
}
