package de.tudarmstadt.lt.pal.qald2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.xml.sax.SAXException;

import de.tudarmstadt.lt.pal.KnowledgeBaseConnector;
import de.tudarmstadt.lt.pal.Query;
import de.tudarmstadt.lt.pal.QueryMapper;
import de.tudarmstadt.lt.pal.stanford.StanfordDependencyParser;
import de.tudarmstadt.lt.pal.stanford.StanfordPseudoQueryBuilder;
import de.tudarmstadt.lt.pal.util.DateUtil;
import de.tudarmstadt.lt.pal.util.ParallelParameterized;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;

@RunWith(ParallelParameterized.class)
public class QALD2Test {
	QALD2Entry entry;
	KnowledgeBaseConnector kb = new KnowledgeBaseConnector(/*"/Users/jsimon/No-Backup/dbpedia/data", null*/);
	QueryMapper tripleMapper = new QueryMapper(kb);
	StanfordCoreNLP pipeline;
	StanfordPseudoQueryBuilder pseudoQueryBuilder = new StanfordPseudoQueryBuilder(kb);
	StanfordDependencyParser depParser = new StanfordDependencyParser("/Volumes/Bill/No-Backup/stanford-parser-tmp");
	
	public QALD2Test(String question, QALD2Entry entry) {
		this.entry = entry;
	}
	
	@Parameterized.Parameters(name="{0}")
	public static Collection<Object> initialize() throws ParserConfigurationException, SAXException, IOException {
		List<Object> params = new LinkedList<Object>();
		Collection<QALD2Entry> entries = QALD2XMLParser.parse(
				"/Volumes/Bill/Documents/Uni/Watson-Projekt/dbpedia-train-answers.xml",
				null);

		for (QALD2Entry entry : entries) {
			params.add(new Object[] { entry.question, entry });
		}
		
		return params;
	}
	
	@Test
	public void test() throws ParseException {
		Set<String> answers = new HashSet<String>();
		SemanticGraph dependencies = depParser.parse(entry.question);
		Query pseudoQuery = pseudoQueryBuilder.buildPseudoQuery(dependencies);
		assertTrue(pseudoQuery.triples != null);
		assertTrue(pseudoQuery.triples.size() > 0);
		assertTrue(pseudoQuery.vars != null);
		assertTrue(pseudoQuery.vars.size() > 0);
		assertTrue(pseudoQuery.focusVar != null);
		Query query = tripleMapper.getBestSPARQLQuery(pseudoQuery);
		assertTrue(query != null);
		System.out.println("QUERY: " + query);
		System.out.println("======= ANSWER =======");
		String focusVar = pseudoQuery.focusVar.name;
		try {
			System.out.println("?" + focusVar + ":");
			answers.addAll(kb.query(query));
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		String firstAnswer = null;
		if (answers.iterator().hasNext()) {
			firstAnswer = answers.iterator().next();
		}
		try {
			switch (entry.answerType) {
			case Resource:
				assertEquals(entry.answerResources, answers);
				break;
			case String:
				assertEquals(entry.answerString, firstAnswer);
				break;
			case Boolean:
				fail(); // Not implemented yet
				assertTrue(firstAnswer != null);
				assertEquals(entry.answerBoolean, Boolean.parseBoolean(firstAnswer));
				break;
			case Date:
				assertTrue(firstAnswer != null);
				assertEquals(entry.answerDate, DateUtil.parseDate(firstAnswer));
				break;
			case Number:
				assertTrue(firstAnswer != null);
				assertEquals(entry.answerNumber, NumberFormat.getInstance(Locale.US).parse(firstAnswer));
				break;
			}
		} catch (Exception e) {
			// This is a parse error on the JUnit side, we don't want this to show up as "error"
			fail();
		}
	}
	
	@After
	public void cleanup() {
		kb.close();
	}
}
