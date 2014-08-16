package de.tudarmstadt.lt.pal;

import junit.framework.TestCase;

import org.junit.Test;

import de.tudarmstadt.lt.pal.SPARQLTriple.Constant;
import de.tudarmstadt.lt.pal.SPARQLTriple.ConstantType;
import de.tudarmstadt.lt.pal.SPARQLTriple.Element;
import de.tudarmstadt.lt.pal.SPARQLTriple.Variable;

public class SPARQLTripleTest extends TestCase {
	@Test
	public void testEquals() {
		Element s1 = new Variable("a", "actor");
		Element p1 = new Constant("starringIn", ConstantType.UnmappedConstantType);
		Element o1 = new Constant("Mission Impossible", ConstantType.UnmappedConstantType);
		SPARQLTriple t1 = new SPARQLTriple(s1, p1, o1);
		
		Element s2 = new Variable("a", "actor");
		Element p2 = new Constant("starringIn", ConstantType.UnmappedConstantType);
		Element o2 = new Constant("Mission Impossible", ConstantType.UnmappedConstantType);
		SPARQLTriple t2 = new SPARQLTriple(s2, p2, o2);
		t1.equals(t2);
		assertEquals(t1, t2);
	}
}
