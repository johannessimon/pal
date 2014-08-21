package de.tudarmstadt.lt.pal;

import junit.framework.TestCase;

import org.junit.Test;

import de.tudarmstadt.lt.pal.SPARQLTriple.Constant;
import de.tudarmstadt.lt.pal.SPARQLTriple.Element;
import de.tudarmstadt.lt.pal.SPARQLTriple.Variable;

public class SPARQLTripleTest extends TestCase {
	@Test
	public void testEquals() {
		Element s1 = new Variable("a", Variable.Type.Unknown);
		Element p1 = new Constant("starringIn", Constant.Type.Unmapped);
		Element o1 = new Constant("Mission Impossible", Constant.Type.Unmapped);
		SPARQLTriple t1 = new SPARQLTriple(s1, p1, o1);
		
		Element s2 = new Variable("a", Variable.Type.Unknown);
		Element p2 = new Constant("starringIn", Constant.Type.Unmapped);
		Element o2 = new Constant("Mission Impossible", Constant.Type.Unmapped);
		SPARQLTriple t2 = new SPARQLTriple(s2, p2, o2);
		t1.equals(t2);
		assertEquals(t1, t2);
	}
}
