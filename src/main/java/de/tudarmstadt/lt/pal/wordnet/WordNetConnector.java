package de.tudarmstadt.lt.pal.wordnet;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.tudarmstadt.lt.pal.util.ComparablePair;
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
	
	public Collection<ComparablePair<String, Float>> getSynonyms(String word, String posStr) {
		Set<ComparablePair<String, Float>> synonyms = new HashSet<ComparablePair<String, Float>>();
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
			return null;
		}
		IIndexWord idxWord = dict.getIndexWord(word, pos);
		for (IWordID wordID : idxWord.getWordIDs()) {
			IWord w = dict.getWord(wordID);
			synonyms.addAll(getHyponyms(w.getSynset()));
			synonyms.add(new ComparablePair<String, Float>(w.getLemma(), 1.0f));
//			System.out.println("== " + w.getLexicalID());
			
			Map<IPointer, List<IWordID>> rMap = w.getRelatedMap();
			for (IPointer p : rMap.keySet()) {
//				System.out.println(p.getName());
//				System.out.println(p.getSymbol());
				List<IWordID> rWordIDs = rMap.get(p);
				for (IWordID rWordID : rWordIDs) {
					IWord rW = dict.getWord(rWordID);
					synonyms.add(new ComparablePair<String, Float>(rW.getLemma(), 1.0f));
					synonyms.addAll(getHyponyms(rW.getSynset()));
				}
			}
		}
		
//		System.out.println(synonyms);
		return synonyms;
	}
	
	public Set<ComparablePair<String, Float>> getHyponyms(ISynset s) {
		return getHyponyms(s, 1);
	}
	
	int maxDepth = 3;
	public Set<ComparablePair<String, Float>> getHyponyms(ISynset s, int depth) {
		if (depth == maxDepth) {
			return Collections.emptySet();
		}
		float scoreInThisDepth = 1 - (float)depth / maxDepth;
		Set<ComparablePair<String, Float>> res = new HashSet<>();
//		Map foo = word.getRelatedMap();
		List<ISynsetID> sIDs = s.getRelatedSynsets(Pointer.HYPONYM);
		for (ISynsetID sID : sIDs) {
			ISynset hS = dict.getSynset(sID);
			for (IWord h : hS.getWords()) {
//				if (h.getLemma().equals("bridge")) {
//					System.out.println("stop");
//				}
				res.add(new ComparablePair<String, Float>(h.getLemma(), scoreInThisDepth));
			}
			for (ComparablePair<String, Float> transitiveH : getHyponyms(hS, depth + 1)) {
				res.add(new ComparablePair<String, Float>(transitiveH.key, transitiveH.value));
			}
		}
		return res;
	}
	
	public static void main(String[] args) {
		WordNetConnector wnc = new WordNetConnector("/usr/local/Cellar/wordnet/3.1/dict/");
		wnc.getSynonyms("cross", "v");
	}
}
