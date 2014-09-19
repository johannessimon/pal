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
import de.tudarmstadt.lt.pal.util.ParallelParameterized;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;

@RunWith(ParallelParameterized.class)
public class QALD2TestWithoutAnswers {
	QALD2Entry entry;
	KnowledgeBaseConnector kb;
	QueryMapper tripleMapper;
	StanfordCoreNLP pipeline;
	StanfordPseudoQueryBuilder pseudoQueryBuilder = new StanfordPseudoQueryBuilder();
	StanfordDependencyParser depParser = new StanfordDependencyParser();
	private static PrecisionRecallMeter prMeter = new PrecisionRecallMeter();
	
	public final static String DATASET_NAME = "dbpedia-train";

	static Document answerDoc;
	static Element datasetElement;
	
	@BeforeClass
	public static void prepare() {
		answerDoc = DocumentHelper.createDocument();
		datasetElement = answerDoc.addElement("dataset").addAttribute("id", DATASET_NAME);
	}
	
	@AfterClass
	public static void finish() throws IOException {
        OutputFormat format = OutputFormat.createPrettyPrint();
		XMLWriter writer = new XMLWriter(new FileWriter("/Volumes/Bill/Documents/Uni/Watson-Projekt/qald2/processed/" + DATASET_NAME + "-answers.xml"), format);
		writer.write(answerDoc);
		writer.close();
	}
	
	public QALD2TestWithoutAnswers(String question, QALD2Entry entry) throws IOException {
		this.entry = entry;
		kb = new KnowledgeBaseConnector("src/main/resources/sparql_endpoints/dbpedia-37-local.properties");
		tripleMapper = new QueryMapper(kb);
	}
	
	@Parameterized.Parameters(name="{0}")
	public static Collection<Object> initialize() throws ParserConfigurationException, SAXException, IOException {
		List<Object> params = new LinkedList<Object>();
		Collection<QALD2Entry> entries = QALD2XMLParser.parse(
				"/Volumes/Bill/Documents/Uni/Watson-Projekt/qald2/" + DATASET_NAME + ".xml",
				null);

		for (QALD2Entry entry : entries) {
			params.add(new Object[] { entry.question, entry });
		}
		
		return params;
	}
	
	String determineAnswerType(String answer) {
		if (answer.startsWith("http://")) {
			return "resource";
		}
		return "UNKNOWN";
	}
	
	String determineAnswerDataType(String answer) {
		if (answer.startsWith("http://")) {
			return "uri";
		}
		return "UNKNOWN";
	}
	
	@Test
	public void test() throws ParseException {
		prMeter.newTestCase();

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
		Query query = tripleMapper.getBestSPARQLQuery(pseudoQuery);
		assertTrue(query != null);
		System.out.println("QUERY: " + query);
		System.out.println("======= ANSWER =======");
		String focusVar = pseudoQuery.focusVar.name;
		
		Set<String> expectedAnswers = new HashSet<>();
		
		if (entry.query != null && !entry.query.contains("OUT OF SCOPE")) {
			Set<String> varsToIgnore = new HashSet<>();
			varsToIgnore.add("string");
			expectedAnswers.addAll(kb.query(entry.query, varsToIgnore));
		}
		
		try {
			System.out.println("?" + focusVar + ":");
			Collection<Answer> answers = kb.query(query);
			assertTrue(!answers.isEmpty());
			Answer firstAnswer = answers.iterator().next();
			String answerType = firstAnswer.dataType.toString().toLowerCase();
			synchronized(datasetElement) {
				q.addElement("query").addText(kb.queryToSPARQL(query));
				Element answersElement = q.addElement("answers");
				Set<String> answerValues = new HashSet<>();
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
