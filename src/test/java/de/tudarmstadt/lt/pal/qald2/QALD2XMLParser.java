package de.tudarmstadt.lt.pal.qald2;

import java.io.IOException;
import java.net.URLDecoder;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import de.tudarmstadt.lt.pal.PseudoQuery;
import de.tudarmstadt.lt.pal.SPARQLTriple;
import de.tudarmstadt.lt.pal.SPARQLTriple.Constant;
import de.tudarmstadt.lt.pal.SPARQLTriple.Variable;

/**
 * Parser for the QALD-2 challenge <br/>
 * <br/>
 * See http://greententacle.techfak.uni-bielefeld.de/~cunger/qald/index.php?x=home&q=2
 */
public class QALD2XMLParser {
	public static Collection<QALD2Entry> parse(String xmlFile, String xmlPseudoQueryFile) throws ParserConfigurationException, SAXException, IOException {
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		Document doc = dBuilder.parse(xmlFile);
		Document doc2 = xmlPseudoQueryFile != null ? dBuilder.parse(xmlPseudoQueryFile) : null;
		
		LinkedList<QALD2Entry> entries = new LinkedList<QALD2Entry>();
		
		NodeList entryNodes = doc.getElementsByTagName("question");
		for (int i = 0; i < entryNodes.getLength(); i++) {
			Node entry = entryNodes.item(i);
			if (entry.getNodeType() == Node.ELEMENT_NODE) {
				Element entryElement = (Element)entry;
				QALD2Entry qald2Entry = new QALD2Entry();
				entries.add(qald2Entry);
				qald2Entry.aggregation = Boolean.parseBoolean(entryElement.getAttribute("aggregation"));
				String answerType = entryElement.getAttribute("answertype");
				if (answerType.equals("resource")) {
					qald2Entry.answerType = QALD2Entry.AnswerType.Resource;
				} else if (answerType.equals("string")) {
					qald2Entry.answerType = QALD2Entry.AnswerType.String;
				} else if (answerType.equals("number")) {
					qald2Entry.answerType = QALD2Entry.AnswerType.Number;
				} else if (answerType.equals("date")) {
					qald2Entry.answerType = QALD2Entry.AnswerType.Date;
				} else if (answerType.equals("boolean")) {
					qald2Entry.answerType = QALD2Entry.AnswerType.Boolean;
				}
				qald2Entry.id = Integer.parseInt(entryElement.getAttribute("id"));
				qald2Entry.onlydbo = Boolean.parseBoolean(entryElement.getAttribute("onlydbo"));
				
				NodeList questionStringNodes = entryElement.getElementsByTagName("string");
				if (questionStringNodes.getLength() > 0) {
					qald2Entry.question = questionStringNodes.item(0).getTextContent().trim();
				}
				
				NodeList keywordNodes = entryElement.getElementsByTagName("keywords");
				if (keywordNodes.getLength() > 0) {
					Node keywordNode = keywordNodes.item(0);
					String[] keywords = keywordNode.getTextContent().split(",");
					qald2Entry.keywords = new HashSet<String>();
					for (String keyword : keywords) {
						qald2Entry.keywords.add(keyword.trim());
					}
				}

				String tagName = "answer";
				if (qald2Entry.answerType == QALD2Entry.AnswerType.Resource) {
					// In this case there's a "string" answer (just the name of the
					// resource) and a "uri" answer. We're interested in the latter.
					tagName = "uri";
				}
				NodeList answerNodes = entryElement.getElementsByTagName(tagName);
				if (answerNodes.getLength() > 0) {
					qald2Entry.answerResources = new HashSet<String>();
				}
				try {
					for (int j = 0; j < answerNodes.getLength(); j++) {
						Node answerNode = answerNodes.item(j);
						String answerText = answerNode.getTextContent().trim();
						switch (qald2Entry.answerType) {
						case Resource:
							qald2Entry.answerResources.add(URLDecoder.decode(answerText, "UTF-8"));
							break;
						case String:
							qald2Entry.answerString = answerText;
							break;
						case Boolean:
							qald2Entry.answerBoolean = Boolean.parseBoolean(answerText);
							break;
						case Date:
							DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
							qald2Entry.answerDate = df.parse(answerText);
							break;
						case Number:
							qald2Entry.answerNumber = NumberFormat.getInstance(Locale.US).parse(answerText);
							break;
						}
					}
				} catch (ParseException e) {
					e.printStackTrace();
				}
				
				Node pseudoQueryNode = doc2 != null ? doc2.getElementById(Integer.toString(qald2Entry.id)) : null;
				if (pseudoQueryNode != null && pseudoQueryNode.getNodeType() == Node.ELEMENT_NODE) {
					qald2Entry.pseudoQuery = new PseudoQuery();
					qald2Entry.pseudoQuery.triples = new HashSet<SPARQLTriple>();
					Element pseudoQueryElement = (Element)pseudoQueryNode;
					
					Map<String, Variable> variables = new HashMap<String, Variable>();
					qald2Entry.pseudoQuery.vars = variables;
					NodeList vars = pseudoQueryElement.getElementsByTagName("var");
					for (int j = 0; j < vars.getLength(); j++) {
						Element var = (Element)vars.item(j);
						String name = var.getAttribute("name");
						Variable.Type type = Variable.Type.valueOf(var.getAttribute("type"));
						variables.put(name, new Variable(name, type));
					}
					
					NodeList pseudoTriples = pseudoQueryElement.getElementsByTagName("triple");
					for (int j = 0; j < pseudoTriples.getLength(); j++) {
						Node triple = pseudoTriples.item(j);
						String[] elements = triple.getTextContent().split("\t");
						SPARQLTriple.Element[] sparqlElements = new SPARQLTriple.Element[3];
						if (elements.length == 3) {
							for (int k = 0; k < 3; k++) {
								String e = elements[k];
								if (e.startsWith("?")) { // Variable 
									String varName = e.substring(1); // remove "?"
									sparqlElements[k] = variables.get(varName);
								} else if (e.equals("[]")) { // Wildcard
									sparqlElements[k] = null;
								} else {
									sparqlElements[k] = new Constant(e, Constant.Type.Unmapped);
								}
							}
							SPARQLTriple sparqlTriple = new SPARQLTriple(sparqlElements[0], sparqlElements[1], sparqlElements[2]);
							qald2Entry.pseudoQuery.triples.add(sparqlTriple);
						}
					}
				} else {
				}
			}	
		}
		
		return entries;
	}
}
