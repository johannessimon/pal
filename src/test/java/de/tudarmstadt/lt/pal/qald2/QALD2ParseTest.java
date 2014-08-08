package de.tudarmstadt.lt.pal.qald2;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.text.ParseException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import javax.xml.parsers.ParserConfigurationException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.xml.sax.SAXException;

import de.tudarmstadt.lt.pal.KnowledgeBaseConnector;
import de.tudarmstadt.lt.pal.PseudoQuery;
import de.tudarmstast.lt.pal.stanford.StanfordDependencyParser;
import de.tudarmstast.lt.pal.stanford.StanfordPseudoQueryBuilder;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;

@RunWith(Parameterized.class)
public class QALD2ParseTest {
	StanfordCoreNLP pipeline;
	QALD2Entry entry;
	KnowledgeBaseConnector kb = new KnowledgeBaseConnector("/Users/jsimon/No-Backup/dbpedia/data", "http://dbpedia.org/sparql");
	StanfordPseudoQueryBuilder pseudoQueryBuilder = new StanfordPseudoQueryBuilder(kb);
	StanfordDependencyParser depParser = new StanfordDependencyParser();
	
	public QALD2ParseTest(String question, StanfordCoreNLP pipeline, QALD2Entry entry) {
		this.pipeline = pipeline;
		this.entry = entry;
	}
	
	@Parameterized.Parameters(name="{0}")
	public static Collection<Object> initialize() throws ParserConfigurationException, SAXException, IOException {
		List<Object> params = new LinkedList<Object>();
		Collection<QALD2Entry> entries = QALD2XMLParser.parse(
				"/Users/jsimon/Documents/Uni/Watson-Projekt/dbpedia-train-answers.xml",
				"/Users/jsimon/Documents/Uni/Watson-Projekt/dbpedia-train-answers-pseudoqueries.xml");
		
		Properties props = new Properties();
		props.put("annotators", "tokenize, ssplit, pos, lemma, ner, parse, dcoref");
		StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

		for (QALD2Entry entry : entries) {
			if (entry.pseudoQuery != null) {
				params.add(new Object[] { entry.question, pipeline, entry });
			}
		}
		
		return params;
	}
	
	@Test
	public void test() throws ParseException {
		StanfordPseudoQueryBuilder pseudoQueryBuilder = new StanfordPseudoQueryBuilder(kb);
		SemanticGraph dependencies = depParser.parse(entry.question);
		PseudoQuery pseudoQuery = pseudoQueryBuilder.buildPseudoQuery(dependencies);
		assertEquals(entry.pseudoQuery, pseudoQuery);
	}
}
