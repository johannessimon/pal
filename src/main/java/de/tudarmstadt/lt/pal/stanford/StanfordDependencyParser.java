package de.tudarmstadt.lt.pal.stanford;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;

import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation;
import edu.stanford.nlp.util.CoreMap;

/**
 * Interface to stanford dependency parser, capable of using file-based parse caching
 */
public class StanfordDependencyParser {
	Properties props;
	StanfordCoreNLP pipeline = null;
	File tmpDir;
	
	Logger log = Logger.getLogger("de.tudarmstadt.lt.pal");
	
	public StanfordDependencyParser() {
		tmpDir = null;
		props = new Properties();
//		props.put("annotators", "tokenize, ssplit, pos, lemma, ner, parse, dcoref");
		props.put("annotators", "tokenize, ssplit, pos, lemma, parse");
	}
	
	/**
	 * @param tmpDir Temporary directory for parse tree cache
	 */
	/*public StanfordDependencyParser(String tmpDir) {
		this.tmpDir = new File(tmpDir);
		props = new Properties();
//		props.put("annotators", "tokenize, ssplit, pos, lemma, ner, parse, dcoref");
		props.put("annotators", "tokenize, ssplit, pos, lemma, parse");
	}*/
	
	/**
	 * Get the file in the cache directory representing a given sentence
	 */
	private File getCacheFile(String sentence) {
		int hash = Math.abs(sentence.hashCode());
		if (tmpDir != null) {
			return new File(tmpDir, Integer.toHexString(hash));
		} else {
			return null;
		}
	}
	
	/**
	 * Serializes a given dependency tree to a specific cache file
	 */
	private void writeTree(SemanticGraph tree, File cacheFile) {
		try {
			ObjectOutputStream o = new ObjectOutputStream(new FileOutputStream(cacheFile));
			o.writeObject(tree);
			o.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Deserializes a dependency tree from a specific cache file
	 */
	private SemanticGraph readTree(File cacheFile) {
		SemanticGraph tree = null;
		try {
			ObjectInputStream i = new ObjectInputStream(new FileInputStream(cacheFile));
			Object object = i.readObject();
			if (object instanceof SemanticGraph) {
				tree = (SemanticGraph)object;
			}
			i.close();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		return tree;
	}
	
	/**
	 * Parse the given sentence and return a collapsed dependency tree
	 */
	public SemanticGraph parse(String sentence) {
		File cacheFile = getCacheFile(sentence);
		if (cacheFile != null && cacheFile.exists()) {
			return readTree(cacheFile);
		}
		Annotation document = new Annotation(sentence);
		synchronized(this) {
			if (pipeline == null) {
				pipeline = new StanfordCoreNLP(props);
			}
		}
		synchronized(pipeline) {
			pipeline.annotate(document);
		}
		List<CoreMap> sentences = document.get(SentencesAnnotation.class);
		
		if (sentences == null || sentences.isEmpty()) {
			return null;
		}
		
		CoreMap firstSentence = sentences.get(0);
		SemanticGraph dependencies = firstSentence.get(CollapsedDependenciesAnnotation.class);
		
		if (cacheFile != null) {
			writeTree(dependencies, cacheFile);
		}
		
		log.debug("Dependency parse tree for sentence \"" + sentence + "\":");
		log.debug(dependencies.toDotFormat());
		
		return dependencies;
	}
	
	public void runInteractive() {
		if (pipeline == null) {
			pipeline = new StanfordCoreNLP(props);
		}
		
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

		try {
			while(true) {
				System.out.print("Q: ");
				String sentence = in.readLine();
				if (sentence == null) {
					break;
				}
				SemanticGraph res = parse(sentence);
				if (res != null) {
					System.out.println(res.toDotFormat());
				} else {
					System.out.println("Unable to parse!");
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		new StanfordDependencyParser().runInteractive();
	}
}
