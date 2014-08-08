package de.tudarmstadt.lt.pal;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import de.tudarmstast.lt.pal.stanford.StanfordDependencyParser;
import de.tudarmstast.lt.pal.stanford.StanfordPseudoQueryBuilder;
import edu.stanford.nlp.semgraph.SemanticGraph;

/**
 * Simple front-end for PAL using the console.
 */
public class NLI {
	KnowledgeBaseConnector kb = new KnowledgeBaseConnector("/Users/jsimon/No-Backup/dbpedia/data", "http://dbpedia.org/sparql");
	TripleMapper tripleMapper = new TripleMapper(kb);
	StanfordPseudoQueryBuilder pseudoQueryBuilder = new StanfordPseudoQueryBuilder(kb);
	StanfordDependencyParser depParser = new StanfordDependencyParser();

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

		Set<String> variableSet = new HashSet<String>();
		String query = tripleMapper.buildSPARQLQuery(pseudoQuery);
		System.out.println("QUERY: " + query);
		System.out.println("======= ANSWER =======");
		try {
			for (String var : variableSet) {
				System.out.println("?" + var + ":");
				answers.addAll(kb.query(query, var));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return answers;
	}
}
