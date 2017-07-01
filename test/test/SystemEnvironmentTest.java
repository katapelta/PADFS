package test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

import system.SystemEnvironment;

public class SystemEnvironmentTest {
	
	@Test
	public void removeEndingSlashTest(){
		assertEquals("test/subdirectory", SystemEnvironment.removeEndingSlashes("test/subdirectory"));
		assertEquals("test/subdirectory", SystemEnvironment.removeEndingSlashes("test/subdirectory/"));
		assertEquals("/test/subdirectory", SystemEnvironment.removeEndingSlashes("/test/subdirectory//"));

		assertEquals("\\test\\subdirectory", SystemEnvironment.removeEndingSlashes("\\test\\subdirectory\\\\"));
		assertEquals("test\\subdirectory", SystemEnvironment.removeEndingSlashes("test\\subdirectory\\"));
		

		assertEquals(null, SystemEnvironment.removeEndingSlashes(null));
		assertEquals("", SystemEnvironment.removeEndingSlashes(""));
	}
	
	@Test
	public void removeStartingSlashTest(){
		assertEquals("test/subdirectory", SystemEnvironment.removeStartingSlashes("test/subdirectory"));
		assertEquals("test/subdirectory/", SystemEnvironment.removeStartingSlashes("test/subdirectory/"));
		assertEquals("test/subdirectory//", SystemEnvironment.removeStartingSlashes("/test/subdirectory//"));

		assertEquals("test\\subdirectory\\\\", SystemEnvironment.removeStartingSlashes("\\test\\subdirectory\\\\"));
		
		assertEquals(null, SystemEnvironment.removeStartingSlashes(null));
		assertEquals("", SystemEnvironment.removeStartingSlashes(""));
	}

	@Test
	public void getLabelTest() {
		String path = null;
		String username = null;

		try{
			assertEquals("the label should be null if username and path are null",null, SystemEnvironment.getLabel(username, path));
			
			path = "/test";
			assertEquals("the label should be null if username is null",null, SystemEnvironment.getLabel(username, path));
			
			username = "Rossi";
			assertNotEquals("the label should not be null if username and path are not null",null, SystemEnvironment.getLabel(username, path));
			
			path = null;
			assertEquals("the label should be null if path is null",null, SystemEnvironment.getLabel(username, path));
		}
		catch(Exception e){
			fail("the getLabel method should not throw "+e.getMessage()+" exception");
		}
		
	}

	@Test
	public void normalizePathTest(){
		try{
			assertEquals("/test/subdirectory", SystemEnvironment.normalizePath("test/subdirectory"));
			assertEquals("/test/subdirectory", SystemEnvironment.normalizePath("test/subdirectory/"));
			assertEquals("/test/subdirectory", SystemEnvironment.normalizePath("/test/subdirectory/"));
			assertEquals("/test/subdirectory", SystemEnvironment.normalizePath("/test/subdirectory///"));
	
			assertEquals("/", SystemEnvironment.normalizePath("/"));
			assertEquals("/", SystemEnvironment.normalizePath(""));
			
			assertEquals("the normalizedPath should be null if the path is null", null, SystemEnvironment.normalizePath(null));
		}
		catch(Exception e){
			fail("the normalizePath method should not throw "+e.getMessage()+" exception");
		}
	}

	@Test
	public void getParentPathTest(){
		try{
			assertEquals("/test", SystemEnvironment.getParentPath("/test/subdirectory"));
			assertEquals("/test/sub", SystemEnvironment.getParentPath("/test/sub/subdirectory"));
			assertEquals("/", SystemEnvironment.getParentPath("/test"));
			assertEquals("the getParentPath should be null if the path is null", null, SystemEnvironment.getParentPath(null));
		}
		catch(Exception e){
			fail("the getParentPath method should not throw "+e.getMessage()+" exception");
		}
	}
	
	@Test
	public void getLogicalPathTest(){
		try{
			assertEquals("/test/subdirectory/fileName.txt", SystemEnvironment.getLogicalPath("/test/subdirectory","fileName.txt"));
			assertEquals("/test/subdirectory/fileName.txt", SystemEnvironment.getLogicalPath("test\\subdirectory\\","fileName.txt"));
			assertEquals("/fileName.txt", SystemEnvironment.getLogicalPath("","fileName.txt"));
			assertEquals("/fileName.txt", SystemEnvironment.getLogicalPath(null,"fileName.txt"));
			assertEquals(null, SystemEnvironment.getLogicalPath("foo",null));
			assertEquals(null, SystemEnvironment.getLogicalPath(null,null));
			
			assertEquals("/test/subdirectory/fileName.txt", SystemEnvironment.getLogicalPath("/test/subdirectory/fileName.txt"));
			assertEquals("/test/subdirectory/fileName.txt", SystemEnvironment.getLogicalPath("test\\subdirectory\\fileName.txt"));
			assertEquals("/fileName.txt", SystemEnvironment.getLogicalPath("fileName.txt"));
			assertEquals(null, SystemEnvironment.getLogicalPath(null));
			assertEquals("/", SystemEnvironment.getLogicalPath(""));
			
		}
		catch(Exception e){
			fail("the getLogicalPath method should not throw "+e.getMessage()+" exception");
		}
	}
	
	@Test
	public void idListToArrayTest(){
		long result[] = SystemEnvironment.idListToArray("1,2,3");
		assertNotEquals(null, result);
		assertEquals("the length of the array must be 3",3,result.length);
		assertEquals(1L,result[0]);
		assertEquals(2L,result[1]);
		assertEquals(3L,result[2]);
		
		result = SystemEnvironment.idListToArray("");
		assertNotEquals(null, result);
		assertEquals("the length of the array must be 0",0,result.length);
		
		result = SystemEnvironment.idListToArray(null);
		assertEquals(null, result);
		
		
		result = SystemEnvironment.idListToArray("7");
		assertNotEquals(null, result);
		assertEquals("the length of the array must be 1",1,result.length);
		assertEquals(7L,result[0]);
		
		try{
			result = SystemEnvironment.idListToArray("5,b,3");
			fail("idListToArray must throw an exception if the input is literal");
		}
		catch(NumberFormatException e){
			
		}
		catch(Exception e){
			fail("wrong exception " + e.getMessage());
		}
	}

}
