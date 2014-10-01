package de.tudarmstadt.lt.pal.qald2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
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
import de.tudarmstadt.lt.pal.util.ParallelParameterized;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;

@RunWith(ParallelParameterized.class)
public class QALD2TestWithoutAnswers {
	QALD2Entry entry;
	static KnowledgeBaseConnector kb;
	static QueryMapper tripleMapper;
	StanfordCoreNLP pipeline;
	static StanfordPseudoQueryBuilder pseudoQueryBuilder = new StanfordPseudoQueryBuilder();
	static StanfordDependencyParser depParser = new StanfordDependencyParser();
	private static PrecisionRecallMeter prMeter = new PrecisionRecallMeter();
	
	public final static String CHALLENGE_NAME = "qald2";
	public final static String DATASET_NAME = "dbpedia-train";
//	public final static String DATASET_NAME = "qald-4_multilingual_test";

	static Document answerDoc;
	static Element datasetElement;
	
	@BeforeClass
	public static void prepare() throws IOException {
		answerDoc = DocumentHelper.createDocument();
		datasetElement = answerDoc.addElement("dataset").addAttribute("id", DATASET_NAME);
		kb = new KnowledgeBaseConnector("src/main/resources/sparql_endpoints/dbpedia-37-local.properties");
		tripleMapper = new QueryMapper(kb);
	}
	
	@AfterClass
	public static void finish() throws IOException {
        OutputFormat format = OutputFormat.createPrettyPrint();
		XMLWriter writer = new XMLWriter(new FileWriter("/Volumes/Bill/Documents/Uni/Watson-Projekt/" + CHALLENGE_NAME + "/processed/" + DATASET_NAME + "-answers.xml"), format);
		writer.write(answerDoc);
		writer.close();
	}
	
	public QALD2TestWithoutAnswers(String question, QALD2Entry entry) throws IOException {
		this.entry = entry;
	}
	
	@Parameterized.Parameters(name="{0}")
	public static Collection<Object> initialize() throws ParserConfigurationException, SAXException, IOException {
		List<Object> params = new LinkedList<Object>();
		Collection<QALD2Entry> entries = QALD2XMLParser.parse(
				"/Volumes/Bill/Documents/Uni/Watson-Projekt/" + CHALLENGE_NAME + "/" + DATASET_NAME + ".xml",
				null);

		for (QALD2Entry entry : entries) {
			params.add(new Object[] { entry.question, entry });
		}
		
		return params;
	}
	
	@Test
	public void test() throws ParseException {
		
		Set<String> expectedAnswers = new HashSet<String>();
		if (entry.query != null && !entry.query.contains("OUT OF SCOPE")) {
			Set<String> varsToIgnore = new HashSet<String>();
			varsToIgnore.add("string");
			expectedAnswers.addAll(kb.query(entry.query, varsToIgnore));
		}
		prMeter.newTestCase(expectedAnswers);
		if (entry.query != null && entry.query.contains("OUT OF SCOPE")) {
			prMeter.addWrongMeasurement();
			fail("OUT OF SCOPE not implemented yet");
		}

		Element q;
		synchronized(datasetElement) {
			q = datasetElement.addElement("question")
				.addAttribute("id", Integer.toString(entry.id))
				.addAttribute("answertype", entry.answerType.toString().toLowerCase());
			q.addElement("string").addText(entry.question);
		}
		
		SemanticGraph dependencies = depParser.parse(entry.question);
		Query pseudoQuery = pseudoQueryBuilder.buildPseudoQuery(dependencies);
		System.out.println(pseudoQuery);
		assertTrue(pseudoQuery.triples != null);
		assertTrue(pseudoQuery.triples.size() > 0);
		assertTrue(pseudoQuery.vars != null);
		assertTrue(pseudoQuery.vars.size() > 0);
		assertTrue(pseudoQuery.focusVar != null);
		ComparablePair<Query, Float> scoredQuery = tripleMapper.getBestSPARQLQuery(pseudoQuery);
		assertTrue(scoredQuery != null);
		if (scoredQuery.value < 0.01) {
			fail("Answer confidence too low!");
		}
		Query query = scoredQuery.key;
		System.out.println("QUERY: " + query);
		System.out.println("======= ANSWER =======");
		String focusVar = pseudoQuery.focusVar.name;
		
		try {
			System.out.println("?" + focusVar + ":");
			Collection<Answer> answers = kb.query(query);
			assertTrue(answers != null);
			assertTrue(!answers.isEmpty());
			Answer firstAnswer = answers.iterator().next();
			String answerType = firstAnswer.dataType.toString().toLowerCase();
			synchronized(datasetElement) {
				q.addElement("query").addText(kb.queryToSPARQL(query));
				Element answersElement = q.addElement("answers");
				Set<String> answerValues = new HashSet<String>();
				for (Answer a : answers) {
					answerValues.add(a.value);
					String dataType = a.dataType.toString().toLowerCase();
					if (answerType.equals("resource")) {
						dataType = "uri";
					}
					Element answerElement = answersElement.addElement("answer");
					answerElement.addElement(dataType).addText(a.value);
					if (a.label != null) {
						answerElement.addElement("string").addText(a.label);
					}
				}
				prMeter.addMeasurement(expectedAnswers, answerValues);
				assertEquals(expectedAnswers, answerValues);
			}
		} catch (Exception e) {
			e.printStackTrace();
			fail();
		}
	}
	
	@After
	public void cleanup() {
		kb.close();
	}
	
	@AfterClass
	public static void printResults() {
		prMeter.printResults();
	}
}
