package de.tudarmstadt.lt.pal.qald2;

import java.util.Date;
import java.util.Set;

import de.tudarmstadt.lt.pal.Query;

/**
 * An instance of this class represents a question (with type, answer, etc.) in the QALD-2 challenge
 */
public class QALD2Entry {
	public enum AnswerType {
		Resource,
		String,
		Number,
		Date,
		Boolean
	}
	
	int id;
	boolean aggregation;
	AnswerType answerType;
	boolean onlydbo;
	String question;
	Set<String> keywords;
	String query;
	Query pseudoQuery;
	
	Set<String>	answerResources;
	String		answerString;
	Number		answerNumber;
	Date		answerDate;
	boolean		answerBoolean;
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("QALD2Entry (" + id + ") {\n");
		sb.append("   Q: " + question + "\n");
		sb.append("   A: " + answerResources + "\n");
		sb.append("}");
		return sb.toString();
	}
}
