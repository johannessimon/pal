package de.tudarmstadt.lt.pal;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;

import de.tudarmstadt.lt.pal.KnowledgeBaseConnector.Answer;
import de.tudarmstadt.lt.pal.stanford.StanfordDependencyParser;
import de.tudarmstadt.lt.pal.stanford.StanfordPseudoQueryBuilder;
import de.tudarmstadt.lt.pal.util.ComparablePair;
import edu.stanford.nlp.semgraph.SemanticGraph;

/**
 * Simple front-end for PAL using the console. This is more meant as a playground than an end user
 * interface.
 */
public class NLI {
	KnowledgeBaseConnector kb;
	QueryMapper tripleMapper;
	StanfordPseudoQueryBuilder pseudoQueryBuilder = new StanfordPseudoQueryBuilder();
	StanfordDependencyParser depParser = new StanfordDependencyParser();
	
	public NLI() {
		try {
			InputStream is = getClass().getClassLoader().getResourceAsStream("sparql_endpoints/dbpedia-37-local.properties");
			kb = new KnowledgeBaseConnector(is);
			tripleMapper = new QueryMapper(kb);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		new NLI().runInteractive();
	}
	
	public void runInteractive() {
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

		try {
			while(true) {
				System.out.print("Q: ");
				String sentence = in.readLine();
				if (sentence == null) {
					break;
				}
				Collection<Answer> answers = run(sentence);
				if (answers != null) {
					for (Answer a : answers ) {
						System.out.println(a.value);
					}
				} else {
					System.out.println("Sorry, I couldn't interpret your question :(");
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		kb.close();
	}
	
	public Collection<Answer> run(String text) {
		SemanticGraph dependencies = depParser.parse(text);
		Query pseudoQuery = pseudoQueryBuilder.buildPseudoQuery(dependencies);
		ComparablePair<Query, Float> scoredQuery = tripleMapper.getBestSPARQLQuery(pseudoQuery);
		Query query = scoredQuery.key;
		if (query != null) {
			return kb.query(query);
		}
		return null;
	}
}
