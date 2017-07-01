package test;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import system.HashFunction;

public class HashFunctionTest {
	
	@Test
	public void hashFunctionTest(){
		HashFunction H = new HashFunction();
		
		
		/* test that same argument generate same value */
		String test = "test/string";
		Long testHash = null;
		assertEquals(null, H.evaluate(null));
		
		testHash = H.evaluate(test);
		assertEquals("2 evaluation of same argument must be the same value",testHash, H.evaluate(test));
		
		
	}
	

	/*
	 * TODO test some requirements like random distribution
	 */
	
	

}
