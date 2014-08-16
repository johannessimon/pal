package de.tudarmstadt.lt.pal.stanford;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation;
import edu.stanford.nlp.util.CoreMap;

public class StanfordDependencyParser {
	Properties props;
	StanfordCoreNLP pipeline = null;
	File tmpDir;
	
	public StanfordDependencyParser(String tmpDir) {
		this.tmpDir = new File(tmpDir);
		props = new Properties();
		props.put("annotators", "tokenize, ssplit, pos, lemma, ner, parse, dcoref");
	}
	
	private File getCacheFile(String sentence) {
		int hash = Math.abs(sentence.hashCode());
		return new File(tmpDir, Integer.toHexString(hash));
	}
	
	private void writeTree(SemanticGraph tree, File cacheFile) {
		try {
			ObjectOutputStream o = new ObjectOutputStream(new FileOutputStream(cacheFile));
			o.writeObject(tree);
			o.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
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
	
	public SemanticGraph parse(String sentence) {
		File cacheFile = getCacheFile(sentence);
		if (cacheFile.exists()) {
			return readTree(cacheFile);
		}
		Annotation document = new Annotation(sentence);
		if (pipeline == null) {
			pipeline = new StanfordCoreNLP(props);
		}
		pipeline.annotate(document);
		List<CoreMap> sentences = document.get(SentencesAnnotation.class);
		
		if (sentences == null || sentences.isEmpty()) {
			return null;
		}
		
		CoreMap firstSentence = sentences.get(0);
		SemanticGraph dependencies = firstSentence.get(CollapsedCCProcessedDependenciesAnnotation.class);
		
		writeTree(dependencies, cacheFile);
		
		return dependencies;
	}
}
