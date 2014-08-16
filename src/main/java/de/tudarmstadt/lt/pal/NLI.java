package de.tudarmstadt.lt.pal;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import de.tudarmstadt.lt.pal.stanford.StanfordDependencyParser;
import de.tudarmstadt.lt.pal.stanford.StanfordPseudoQueryBuilder;
import edu.stanford.nlp.semgraph.SemanticGraph;

/**
 * Simple front-end for PAL using the console.
 */
public class NLI {
	KnowledgeBaseConnector kb = new KnowledgeBaseConnector(/*"/Users/jsimon/No-Backup/dbpedia/data", "http://dbpedia.org/sparql"*/);
	TripleMapper tripleMapper = new TripleMapper(kb);
	StanfordPseudoQueryBuilder pseudoQueryBuilder = new StanfordPseudoQueryBuilder(kb);
	StanfordDependencyParser depParser = new StanfordDependencyParser("/Users/jsimon/No-Backup/stanford-parser-tmp");

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
				run(sentence);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		kb.close();
	}
	
	public Collection<String> run(String text) {
		List<String> answers = new LinkedList<String>();
		SemanticGraph dependencies = depParser.parse(text);
		PseudoQuery pseudoQuery = pseudoQueryBuilder.buildPseudoQuery(dependencies);
		System.out.println("PSEUDO QUERY: " + pseudoQuery);

		String query = tripleMapper.buildSPARQLQuery(pseudoQuery);
		System.out.println("QUERY: " + query);
		System.out.println("======= ANSWER =======");
		try {
			for (String var : pseudoQuery.vars.keySet()) {
				System.out.println("?" + var + ":");
				Collection<String> _answers = kb.query(query, var);
				answers.addAll(_answers);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return answers;
	}
}
