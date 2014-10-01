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
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.xml.sax.SAXException;

import de.tudarmstadt.lt.pal.KnowledgeBaseConnector;
import de.tudarmstadt.lt.pal.KnowledgeBaseConnector.Answer;
import de.tudarmstadt.lt.pal.Query;
import de.tudarmstadt.lt.pal.QueryMapper;
import de.tudarmstadt.lt.pal.stanford.StanfordDependencyParser;
import de.tudarmstadt.lt.pal.stanford.StanfordPseudoQueryBuilder;
import de.tudarmstadt.lt.pal.util.ComparablePair;
import de.tudarmstadt.lt.pal.util.DateUtil;
import de.tudarmstadt.lt.pal.util.ParallelParameterized;
import edu.stanford.nlp.semgraph.SemanticGraph;

@RunWith(ParallelParameterized.class)
public class QALD2Test {
	QALD2Entry entry;
	static KnowledgeBaseConnector kb;
	static QueryMapper tripleMapper;
	static StanfordPseudoQueryBuilder pseudoQueryBuilder = new StanfordPseudoQueryBuilder();
	static StanfordDependencyParser depParser = new StanfordDependencyParser();
	private static float recall = 0.0f;
	private static float precision = 0.0f;
	private static int correctQuestions = 0;
	private static int partiallyCorrectQuestions = 0;
	private static int answeredQuestions = 0;
	
	public QALD2Test(String question, QALD2Entry entry) throws IOException {
		this.entry = entry;
	}
	
	@BeforeClass
	public static void init() throws IOException {
		kb = new KnowledgeBaseConnector("src/main/resources/sparql_endpoints/dbpedia-37-local.properties");
		tripleMapper = new QueryMapper(kb);
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
		ComparablePair<Query, Float> scoredQuery = tripleMapper.getBestSPARQLQuery(pseudoQuery);
		Query query = scoredQuery.key;
		assertTrue(query != null);
		try {
			Collection<Answer> queryAnswers = kb.query(query);
			for (Answer a : queryAnswers) {
				answers.add(a.value);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		answeredQuestions++;
		assertTrue(!answers.isEmpty());
		
		String firstAnswer = null;
		if (answers.iterator().hasNext()) {
			firstAnswer = answers.iterator().next();
		}
		try {
			switch (entry.answerType) {
			case Resource:
				Set<String> correctAnswers = new HashSet<String>(answers);
				correctAnswers.retainAll(entry.answerResources);
				recall += (float)correctAnswers.size() / entry.answerResources.size();
				precision += (float)correctAnswers.size() / answers.size();
				if (answers.equals(entry.answerResources)) {
					correctQuestions++;
				} else if (correctAnswers.size() > 0) {
					partiallyCorrectQuestions++;
				}
				assertEquals(entry.answerResources, answers);
				return;
			case String:
				if (entry.answerString.equals(firstAnswer)) {
					recall += 1;
					precision += 1;
					correctQuestions++;
				}
				assertEquals(entry.answerString, firstAnswer);
				return;
			case Boolean:
				fail(); // Not implemented yet
				assertTrue(firstAnswer != null);
				assertEquals(entry.answerBoolean, Boolean.parseBoolean(firstAnswer));
				return;
			case Date:
				if (firstAnswer != null && entry.answerDate.equals(DateUtil.parseDate(firstAnswer))) {
					recall += 1;
					precision += 1;
					correctQuestions++;
				}
				assertTrue(firstAnswer != null);
				assertEquals(entry.answerDate, DateUtil.parseDate(firstAnswer));
				return;
			case Number:
				if (firstAnswer != null && entry.answerNumber.equals(NumberFormat.getInstance(Locale.US).parse(firstAnswer))) {
					recall += 1;
					precision += 1;
					correctQuestions++;
				}
				assertTrue(firstAnswer != null);
				assertEquals(entry.answerNumber, NumberFormat.getInstance(Locale.US).parse(firstAnswer));
				return;
			default:
				fail();
			}
		} catch (Exception e) {
			// This is a parse error on the JUnit side, we don't want this to show up as "error"
			fail();
		}
		fail();
	}
	
	@After
	public void cleanup() {
		kb.close();
		System.out.println("Recall: " + recall);
		System.out.println("Precision: " + precision);
		System.out.println("Correct questions: " + correctQuestions);
		System.out.println("Partially correct questions: " + partiallyCorrectQuestions);
		System.out.println("Answered questions: " + answeredQuestions);
		System.out.println("Unanswered questions: " + (100 - answeredQuestions));
	}
	
	@AfterClass
	public static void printResults() {
		// no answer is always a precision of 1
		int unansweredQuestions = 100 - answeredQuestions;
		precision += unansweredQuestions;
		System.out.println("Recall: " + recall);
		System.out.println("Precision: " + precision);
		System.out.println("F1 score: " + 2*(recall*precision)/(recall+precision));
		System.out.println("Correct questions: " + correctQuestions);
		System.out.println("Partially correct questions: " + partiallyCorrectQuestions);
		System.out.println("Answered questions: " + answeredQuestions);
		System.out.println("Unanswered questions: " + unansweredQuestions);
	}
}
