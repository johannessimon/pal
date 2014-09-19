package de.tudarmstadt.lt.pal.qald2;

import java.util.HashSet;
import java.util.Set;

public class PrecisionRecallMeter {
	private int measurements = 0;
	private int nonEmptyMeasurements = 0;
	private float recall = 0.0f;
	private float precision = 0.0f;
	private float fMeasure = 0.0f;
	private int correct = 0;
	private int partiallyCorrect = 0;
	private int incorrect = 0;
	
	public void newTestCase() {
		measurements++;
	}
	
	public void addMeasurement(Set<String> expected, Set<String> actual) {
		if (expected.isEmpty()) {
			addWrongMeasurement();
			return;
		}
		nonEmptyMeasurements++;
		Set<String> correctActual = new HashSet<String>(actual);
		correctActual.retainAll(expected);
		float _recall = (float)correctActual.size() / expected.size();
		float _precision = (float)correctActual.size() / actual.size();
		recall  += _recall;
		precision += _precision;
		if (_precision + _recall > 0) {
			float _fMeasure = 2*(_recall*_precision)/(_recall+_precision);
			fMeasure += _fMeasure;
		}
		if (expected.equals(actual)) {
			correct++;
		} else if (correctActual.size() > 0) {
			partiallyCorrect++;
		} else {
			incorrect++;
		}
	}
	
	public void addWrongMeasurement() {
		nonEmptyMeasurements++;
		incorrect++;
	}
	
	public void printResults() {
		int emptyMeasurements = measurements - nonEmptyMeasurements;
		// All empty measurements mean recall of 0 and precision of 1
		precision += emptyMeasurements;
		System.out.println("Recall: " + recall / measurements);
		System.out.println("Precision: " + precision / measurements);
		System.out.println("F1 score: " + fMeasure / measurements);
		System.out.println("Correct: " + correct);
		System.out.println("Partially correct: " + partiallyCorrect);
		System.out.println("Incorrect: " + incorrect);
		System.out.println("Measurements: " + measurements);
		System.out.println("Non-empty measurements: " + nonEmptyMeasurements);
	}
}
