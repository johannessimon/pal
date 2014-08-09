package de.tudarmstadt.lt.pal.stanford;

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
	StanfordCoreNLP pipeline;
	
	public SemanticGraph parse(String sentence) {
		Properties props = new Properties();
		props.put("annotators", "tokenize, ssplit, pos, lemma, ner, parse, dcoref");
		StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
		Annotation document = new Annotation(sentence);
		pipeline.annotate(document);
		List<CoreMap> sentences = document.get(SentencesAnnotation.class);
		
		if (sentences == null || sentences.isEmpty()) {
			return null;
		}
		
		CoreMap firstSentence = sentences.get(0);
		SemanticGraph dependencies = firstSentence.get(CollapsedCCProcessedDependenciesAnnotation.class);
		
		return dependencies;
	}
}
