package test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.net.SocketException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import jobManagement.consensus.JobConsMsg;
import jobManagement.jobOperation.JobOperation;
import jobManagement.jobOperation.JobOperation.PriorityQueueComparator;
import jobManagement.jobOperation.clientOp.Chmod;
import jobManagement.jobOperation.clientOp.Deldir;
import jobManagement.jobOperation.clientOp.GetFileInfo;
import jobManagement.jobOperation.clientOp.List;
import jobManagement.jobOperation.clientOp.Mkdir;
import jobManagement.jobOperation.clientOp.Put;
import jobManagement.jobOperation.clientOp.Remove;
import restInterface.RestInterface;
import restInterface.RestServer;
import restInterface.op.RestChmod;
import restInterface.op.RestDeldirResponse;
import restInterface.op.RestGetFileInfo;
import restInterface.op.RestList;
import restInterface.op.RestMkdir;
import restInterface.op.RestPut;
import restInterface.op.RestRemove;
import system.SystemEnvironment;
import system.SystemEnvironment.Constants;
import system.SystemEnvironment.Constants.Permission;
import system.SystemEnvironment.Constants.Rest;
import system.SystemEnvironment.Variables;
import system.logger.PadFsLogger.LogLevel;

public class ClientRestInterfaceTest {
		
	private static PriorityBlockingQueue<JobOperation> inOp;
	private static BlockingQueue<JobConsMsg<?>> inConsMsg;
	private static final String port = "8683";
	
	@BeforeClass
	public static void restInterfaceStart(){
		

			

			PriorityQueueComparator pqc = new PriorityQueueComparator();
			
			inConsMsg = new LinkedBlockingQueue<JobConsMsg<?>>();
			inOp 	  = new PriorityBlockingQueue<JobOperation>(10,pqc);

			try{			
				RestServer.restServerStart(inOp, inConsMsg, port, "localhost", LogLevel.WARNING);
				
			} catch ( SocketException e) {
				fail("SERVER TOMCAT START - PORT NOT FREE for TEST - "+ e.getClass().getName() + ": " + e.getMessage());
			}
		
		
	}
	
	@AfterClass
	public static void restInterfaceStop(){
		SpringApplication.exit(RestServer.getAppContext());	
	}
	
	@Test
	public void removeTest(){
		try{
			String url;
			RestTemplate restTemplate;
			ResponseEntity<RestRemove> resp1;
			
			String testUser = "testUser";
			String testPassword = "testPassword";
			String testPath = "/testPath";
			String testUserOwner = "testUserOwner";
			
			
			restTemplate = SystemEnvironment.generateRestTemplate();
			 
			url = RestInterface.Remove.generateUrl(Constants.localhost, port, testUser, testPassword, testUserOwner, testPath);
			
			resp1 = restTemplate.exchange(url, HttpMethod.GET, null, RestRemove.class);
			
			/* test client response */
			
			assertNotEquals(null,resp1);
			assertNotEquals(null,resp1.getBody());
			assertNotEquals(null,resp1.getBody().getStatus());
			assertEquals(Rest.status.error,resp1.getBody().getStatus());
			assertEquals(Rest.errors.networkDown,resp1.getBody().getError());
			
			
			/* test server job creation */
			assertEquals("the queue must be null if no operation is correctly created because of networkIsDown",0,inOp.size());
			
			
			Variables.setNetworkUp(true);
			
			
	
			
			AsynchTester t = new AsynchTester(
					new Runnable(){
						public void run(){
	
							ResponseEntity<RestRemove> resp2;
							RestTemplate restTemplate2;
							restTemplate2 = SystemEnvironment.generateRestTemplate();
							String url2 = RestInterface.Remove.generateUrl(Constants.localhost, port, testUser, testPassword, testUserOwner, testPath);
							
							resp2 = restTemplate2.exchange(url2, HttpMethod.GET, null, RestRemove.class);
							
							/* test client response */
							assertNotEquals(null,resp2);
							assertNotEquals(null,resp2.getBody());
							assertNotEquals(null,resp2.getBody().getStatus());
							assertNotEquals(Rest.status.error,resp2.getBody().getStatus());
							
						}
					}
				);
			t.start();
			
			
			
			
			
			/* test server job creation */
			JobOperation job = null;
			try {
				job = inOp.poll(1,TimeUnit.SECONDS);
			} catch (InterruptedException e1) {
				fail("thread must not be interrupted");
			}
			assertEquals("the jobOperation must be a Remove",true,Remove.class.isAssignableFrom(job.getClass()));
			
			assertNotEquals(null,job);
			
			Remove j = (Remove) job;
			assertEquals(testUser,j.getUsername());
			assertEquals(testUserOwner,j.getUsernameOwner());
			assertEquals(testPassword,j.getPassword());
			assertEquals(testPath,j.getPath());
			assertEquals(Constants.OperationPriority.REMOVE,j.getPriority());
			
			j.replyOperationCompleted();
			
			try {
				t.test();
			} catch (InterruptedException e) {
				fail("thread must not be interrupted");
			}
			
		}
		finally{
			Variables.setNetworkUp(false);
		}
	}

	@Test
	public void chmodTest(){
		try{
			String url;
			RestTemplate restTemplate;
			ResponseEntity<RestChmod> resp1;
	
			String testUser = "testUser";
			String testUserTarget = "testUserTarget";
			String testPassword = "testPassword";
			String testPath = "/testPath";
			String testUserOwner = "testUserOwner";
			Permission testPermission = Permission.readWrite;
			
			
			restTemplate = SystemEnvironment.generateRestTemplate();
			 
			url = RestInterface.Chmod.generateUrl(Constants.localhost, port, testUser, testPassword, testUserTarget, testPermission, testUserOwner, testPath);
			
			resp1 = restTemplate.exchange(url, HttpMethod.GET, null, RestChmod.class);
			
			/* test client response */
			
			assertNotEquals(null,resp1);
			assertNotEquals(null,resp1.getBody());
			assertNotEquals(null,resp1.getBody().getStatus());
			assertEquals(Rest.status.error,resp1.getBody().getStatus());
			assertEquals(Rest.errors.networkDown,resp1.getBody().getError());
			
			
			/* test server job creation */
			assertEquals("the queue must be null if no operation is correctly created because of networkIsDown",0,inOp.size());
			
			
			Variables.setNetworkUp(true);
			
			
	
			
			AsynchTester t = new AsynchTester(
					new Runnable(){
						public void run(){
	
							ResponseEntity<RestChmod> resp2;
							RestTemplate restTemplate2;
							restTemplate2 = SystemEnvironment.generateRestTemplate();
							String url2 = RestInterface.Chmod.generateUrl(Constants.localhost, port, testUser, testPassword, testUserTarget, testPermission, testUserOwner, testPath);
							
							resp2 = restTemplate2.exchange(url2, HttpMethod.GET, null, RestChmod.class);
							
							/* test client response */
							assertNotEquals(null,resp2);
							assertNotEquals(null,resp2.getBody());
							assertNotEquals(null,resp2.getBody().getStatus());
							assertNotEquals(Rest.status.error,resp2.getBody().getStatus());
							
						}
					}
				);
			t.start();
			
			
			
			
			
			/* test server job creation */
			JobOperation job = null;
			try {
				job = inOp.poll(1,TimeUnit.SECONDS);
			} catch (InterruptedException e1) {
				fail("thread must not be interrupted");
			}
			assertEquals("the jobOperation must be a Chmod",true,Chmod.class.isAssignableFrom(job.getClass()));
			
			assertNotEquals(null,job);
			
			Chmod j = (Chmod) job;
			assertEquals(testUser,j.getUsername());
			assertEquals(testUserOwner,j.getUsernameOwner());
			assertEquals(testPassword,j.getPassword());
			assertEquals(testPath,j.getPath());
			assertEquals(testUserTarget,j.getUsernameTarget());
			assertEquals(testPermission,j.getPermission());
			assertEquals(Constants.OperationPriority.CHMOD,j.getPriority());
			
			
			j.replyOperationCompleted();
			
			try {
				t.test();
			} catch (InterruptedException e) {
				fail("thread must not be interrupted");
			}
		
		}
		finally{
			Variables.setNetworkUp(false);
		}
	}
	
	
	@Test
	public void deldirTest(){
		try{
			String url;
			RestTemplate restTemplate;
			ResponseEntity<RestDeldirResponse> resp1;
	
			String testUser = "testUser";
			String testPassword = "testPassword";
			String testPath = "/testPath";
			String testUserOwner = "testUserOwner";
			
			
			restTemplate = SystemEnvironment.generateRestTemplate();
			 
			url = RestInterface.DelDir.generateUrl(Constants.localhost, port, testUser, testPassword, testUserOwner, testPath);
			
			resp1 = restTemplate.exchange(url, HttpMethod.GET, null, RestDeldirResponse.class);
			
			/* test client response */
			
			assertNotEquals(null,resp1);
			assertNotEquals(null,resp1.getBody());
			assertNotEquals(null,resp1.getBody().getStatus());
			assertEquals(Rest.status.error,resp1.getBody().getStatus());
			assertEquals(Rest.errors.networkDown,resp1.getBody().getError());
			
			
			/* test server job creation */
			assertEquals("the queue must be null if no operation is correctly created because of networkIsDown",0,inOp.size());
			
			
			Variables.setNetworkUp(true);
			
			
	
			
			AsynchTester t = new AsynchTester(
					new Runnable(){
						public void run(){
	
							ResponseEntity<RestDeldirResponse> resp2;
							RestTemplate restTemplate2;
							restTemplate2 = SystemEnvironment.generateRestTemplate();
							String url2 = RestInterface.DelDir.generateUrl(Constants.localhost, port, testUser, testPassword, testUserOwner, testPath);
							
							resp2 = restTemplate2.exchange(url2, HttpMethod.GET, null, RestDeldirResponse.class);
							
							/* test client response */
							assertNotEquals(null,resp2);
							assertNotEquals(null,resp2.getBody());
							assertNotEquals(null,resp2.getBody().getStatus());
							assertNotEquals(Rest.status.error,resp2.getBody().getStatus());
							
						}
					}
				);
			t.start();
			
			
			
			
			
			/* test server job creation */
			JobOperation job = null;
			try {
				job = inOp.poll(1,TimeUnit.SECONDS);
			} catch (InterruptedException e1) {
				fail("thread must not be interrupted");
			}
			assertEquals("the jobOperation must be a Deldir",true,Deldir.class.isAssignableFrom(job.getClass()));
			
			assertNotEquals(null,job);
			
			Deldir j = (Deldir) job;
			assertEquals(testUser,j.getUsername());
			assertEquals(testUserOwner,j.getUsernameOwner());
			assertEquals(testPassword,j.getPassword());
			assertEquals(testPath,j.getPath());
			assertEquals(Constants.OperationPriority.DELDIR,j.getPriority());
			
			
			j.replyOperationCompleted();
			
			try {
				t.test();
			} catch (InterruptedException e) {
				fail("thread must not be interrupted");
			}
			
		}
		finally{
			Variables.setNetworkUp(false);
		}
	}
	
	@Test
	public void getFileInfoTest(){
		try{
			String url;
			RestTemplate restTemplate;
			ResponseEntity<RestGetFileInfo> resp1;
	
			String testUser = "testUser";
			String testPassword = "testPassword";
			String testPath = "/testPath";
			String testUserOwner = "testUserOwner";
			
			
			restTemplate = SystemEnvironment.generateRestTemplate();
			 
			url = RestInterface.GetFileInfo.generateUrl(Constants.localhost, port, testUser, testPassword, testUserOwner, testPath);
			
			resp1 = restTemplate.exchange(url, HttpMethod.GET, null, RestGetFileInfo.class);
			
			/* test client response */
			
			assertNotEquals(null,resp1);
			assertNotEquals(null,resp1.getBody());
			assertNotEquals(null,resp1.getBody().getStatus());
			assertEquals(Rest.status.error,resp1.getBody().getStatus());
			assertEquals(Rest.errors.networkDown,resp1.getBody().getError());
			
			
			/* test server job creation */
			assertEquals("the queue must be null if no operation is correctly created because of networkIsDown",0,inOp.size());
			
			
			Variables.setNetworkUp(true);
			
				
			AsynchTester t = new AsynchTester(
					new Runnable(){
						public void run(){
							ResponseEntity<RestGetFileInfo> resp2;
							RestTemplate restTemplate2;
							restTemplate2 = SystemEnvironment.generateRestTemplate();
							String url2 = RestInterface.GetFileInfo.generateUrl(Constants.localhost, port, testUser, testPassword, testUserOwner, testPath);
							
							resp2 = restTemplate2.exchange(url2, HttpMethod.GET, null, RestGetFileInfo.class);
							
							/* test client response */
							assertNotEquals(null,resp2);
							assertNotEquals(null,resp2.getBody());
							assertNotEquals(null,resp2.getBody().getStatus());
							assertEquals(Rest.status.error,resp2.getBody().getStatus());
							assertEquals(Rest.errors.fileNotFound, resp2.getBody().getError());
						
						}
					}
				);
			
			t.start();
			
			
			
			
			/* test server job creation */
			JobOperation job = null;
			try {
				job = inOp.poll(1,TimeUnit.SECONDS);
			} catch (InterruptedException e1) {
				fail("thread must not be interrupted");
			}
			assertEquals("the jobOperation must be a GetFileInfo",true,GetFileInfo.class.isAssignableFrom(job.getClass()));
			
			assertNotEquals(null,job);
			
			GetFileInfo j = (GetFileInfo) job;
			assertEquals(testUser,j.getUsername());
			assertEquals(testUserOwner,j.getUsernameOwner());
			assertEquals(testPassword,j.getPassword());
			assertEquals(testPath,j.getPath());
			assertEquals(Constants.OperationPriority.GET_FILE_INFO,j.getPriority());
			
			
			j.replyError(Rest.errors.fileNotFound);
			
			try {
				t.test();
				
	
			} catch (InterruptedException e) {
				fail("thread must not be interrupted");
			}
		
		}
		finally{
			Variables.setNetworkUp(false);
		}
	}
	
	
	@Test
	public void ListTest(){
		try{
			String url;
			RestTemplate restTemplate;
			ResponseEntity<RestList> resp1;
	
			String testUser = "testUser";
			String testPassword = "testPassword";
			String testPath = "/testPath";
			String testUserOwner = "testUserOwner";
			
			
			restTemplate = SystemEnvironment.generateRestTemplate();
			 
			url = RestInterface.List.generateUrl(Constants.localhost, port, testUser, testPassword, testUserOwner, testPath);
			
			resp1 = restTemplate.exchange(url, HttpMethod.GET, null, RestList.class);
			
			/* test client response */
			
			assertNotEquals(null,resp1);
			assertNotEquals(null,resp1.getBody());
			assertNotEquals(null,resp1.getBody().getStatus());
			assertEquals(Rest.status.error,resp1.getBody().getStatus());
			assertEquals(Rest.errors.networkDown,resp1.getBody().getError());
			
			
			/* test server job creation */
			assertEquals("the queue must be null if no operation is correctly created because of networkIsDown",0,inOp.size());
			
			
			Variables.setNetworkUp(true);
			
				
			AsynchTester t = new AsynchTester(
					new Runnable(){
						public void run(){
							ResponseEntity<RestList> resp2;
							RestTemplate restTemplate2;
							restTemplate2 = SystemEnvironment.generateRestTemplate();
							String url2 = RestInterface.List.generateUrl(Constants.localhost, port, testUser, testPassword, testUserOwner, testPath);
							
							resp2 = restTemplate2.exchange(url2, HttpMethod.GET, null, RestList.class);
							
							/* test client response */
							assertNotEquals(null,resp2);
							assertNotEquals(null,resp2.getBody());
							assertNotEquals(null,resp2.getBody().getStatus());
							assertEquals(Rest.status.error,resp2.getBody().getStatus());
							assertEquals(Rest.errors.directoryNotFound, resp2.getBody().getError());
						
						}
					}
				);
			
			t.start();
			
			
			
			
			/* test server job creation */
			JobOperation job = null;
			try {
				job = inOp.poll(1,TimeUnit.SECONDS);
			} catch (InterruptedException e1) {
				fail("thread must not be interrupted");
			}
			assertEquals("the jobOperation must be a List",true,List.class.isAssignableFrom(job.getClass()));
			
			assertNotEquals(null,job);
			
			List j = (List) job;
			assertEquals(testUser,j.getUsername());
			assertEquals(testUserOwner,j.getUsernameOwner());
			assertEquals(testPassword,j.getPassword());
			assertEquals(testPath,j.getPath());
			assertEquals(Constants.OperationPriority.LIST,j.getPriority());
			
			
			j.replyError(Rest.errors.directoryNotFound);
			
			try {
				t.test();
				
	
			} catch (InterruptedException e) {
				fail("thread must not be interrupted");
			}
		
		}
		finally{
			Variables.setNetworkUp(false);
		}
	}
	
	
	@Test
	public void MkdirTest(){
		try{
			String url;
			RestTemplate restTemplate;
			ResponseEntity<RestMkdir> resp1;
	
			String testUser = "testUser";
			String testPassword = "testPassword";
			String testPath = "/testPath";
			String testUserOwner = "testUserOwner";
			
			
			restTemplate = SystemEnvironment.generateRestTemplate();
			 
			url = RestInterface.Mkdir.generateUrl(Constants.localhost, port, testUser, testPassword, testUserOwner, testPath);
			
			resp1 = restTemplate.exchange(url, HttpMethod.GET, null, RestMkdir.class);
			
			/* test client response */
			
			assertNotEquals(null,resp1);
			assertNotEquals(null,resp1.getBody());
			assertNotEquals(null,resp1.getBody().getStatus());
			assertEquals(Rest.status.error,resp1.getBody().getStatus());
			assertEquals(Rest.errors.networkDown,resp1.getBody().getError());
			
			
			/* test server job creation */
			assertEquals("the queue must be null if no operation is correctly created because of networkIsDown",0,inOp.size());
			
			
			Variables.setNetworkUp(true);
			
				
			AsynchTester t = new AsynchTester(
					new Runnable(){
						public void run(){
							ResponseEntity<RestMkdir> resp2;
							RestTemplate restTemplate2;
							restTemplate2 = SystemEnvironment.generateRestTemplate();
							String url2 = RestInterface.Mkdir.generateUrl(Constants.localhost, port, testUser, testPassword, testUserOwner, testPath);
							
							resp2 = restTemplate2.exchange(url2, HttpMethod.GET, null, RestMkdir.class);
							
							/* test client response */
							assertNotEquals(null,resp2);
							assertNotEquals(null,resp2.getBody());
							assertNotEquals(null,resp2.getBody().getStatus());
							assertNotEquals(Rest.status.error,resp2.getBody().getStatus());
							
						
						}
					}
				);
			
			t.start();
			
			
			
			
			/* test server job creation */
			JobOperation job = null;
			try {
				job = inOp.poll(1,TimeUnit.SECONDS);
			} catch (InterruptedException e1) {
				fail("thread must not be interrupted");
			}
			assertEquals("the jobOperation must be a Mkdir",true,Mkdir.class.isAssignableFrom(job.getClass()));
			
			assertNotEquals(null,job);
			
			Mkdir j = (Mkdir) job;
			assertEquals(testUser,j.getUsername());
			assertEquals(testUserOwner,j.getUsernameOwner());
			assertEquals(testPassword,j.getPassword());
			assertEquals(testPath,j.getPath());
			assertEquals(Constants.OperationPriority.MKDIR,j.getPriority());
			
			
			j.replyOperationCompleted();
			
			try {
				t.test();
				
	
			} catch (InterruptedException e) {
				fail("thread must not be interrupted");
			}
		
		}
		finally{
			Variables.setNetworkUp(false);
		}
	}
	
	
	@Test
	public void PutTest() throws IOException{
		try{
			String url;
			RestTemplate restTemplate;
			ResponseEntity<RestPut> resp1;
	
			String testUser = "testUser";
			String testPassword = "testPassword";
			String testUserOwner = "testUserOwner";
			String testParentPath = "/testPath";
			String name = "file.txt";
			FileSystemResource file = new FileSystemResource(new File("pom.xml"));
			
			
			restTemplate = SystemEnvironment.generateRestTemplate();
			 
			url = RestInterface.Put.generateUrl(Constants.localhost, port);
			MultiValueMap<String,Object> postParam = 
					RestInterface.Put.generatePostParameters(testUser, testPassword, testUserOwner, testParentPath, name, file);
			
			
			HttpHeaders requestHeaders = new HttpHeaders();
			HttpEntity<?> httpEntity = new HttpEntity<Object>(postParam, requestHeaders);
			resp1 = restTemplate.exchange(url, HttpMethod.POST, httpEntity,RestPut.class);
			
			
			/* test client response */
			
			assertNotEquals(null,resp1);
			assertNotEquals(null,resp1.getBody());
			assertNotEquals(null,resp1.getBody().getStatus());
			assertEquals(Rest.status.error,resp1.getBody().getStatus());
			assertEquals(Rest.errors.networkDown,resp1.getBody().getError());
			
			
			/* test server job creation */
			assertEquals("the queue must be null if no operation is correctly created because of networkIsDown",0,inOp.size());
			
			
			Variables.setNetworkUp(true);
			
				
			AsynchTester t = new AsynchTester(
					new Runnable(){
						public void run(){
							
							
							ResponseEntity<RestPut> resp2;
							RestTemplate restTemplate2;
							restTemplate2 = SystemEnvironment.generateRestTemplate();
							

							HttpHeaders requestHeaders = new HttpHeaders();
							HttpEntity<?> httpEntity = new HttpEntity<Object>(postParam, requestHeaders);
							resp2 = restTemplate2.exchange(url, HttpMethod.POST, httpEntity,RestPut.class);
							
							/* test client response */
							assertNotEquals(null,resp2);
							assertNotEquals(null,resp2.getBody());
							assertNotEquals(null,resp2.getBody().getStatus());
							assertNotEquals(Rest.status.error,resp2.getBody().getStatus());
							
						
						}
					}
				);
			
			t.start();
			
			
			
			
			/* test server job creation */
			JobOperation job = null;
			try {
				job = inOp.poll(5,TimeUnit.SECONDS);
			} catch (InterruptedException e1) {
				fail("thread must not be interrupted");
			}
			assertEquals("the jobOperation must be a Put",true,Put.class.isAssignableFrom(job.getClass()));
			
			assertNotEquals(null,job);
			
			Put j = (Put) job;
			assertEquals(testUser,j.getUsername());
			assertEquals(testUserOwner,j.getUsernameOwner());
			assertEquals(testPassword,j.getPassword());
			assertEquals(testParentPath+Constants.fileSeparator+name,j.getPath());
			assertEquals(Constants.OperationPriority.PUT,j.getPriority());
			
			j.replyOperationCompleted();
			
			try {
				t.test();
				
	
			} catch (InterruptedException e) {
				fail("thread must not be interrupted");
			}
		
		}
		finally{
			Variables.setNetworkUp(false);
		}
	}
	
	
	class AsynchTester{
		private Thread thread;
		private volatile AssertionError exc;
		
		public AsynchTester(final Runnable runnable){
			thread = new Thread(
					new Runnable(){
						public void run(){
							try{
								runnable.run();
							}
							catch(AssertionError e){
								exc = e;
							}
						}
					}
					);
		}
		
		public void start(){
			thread.start();
		}
		
		public void test() throws InterruptedException{
			thread.join();
			if(exc != null){
				throw exc;
			}
		}
	}

}
