package system.managers;

import java.sql.Clob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import padfsThreads.Padfs;
import restInterface.RestServer;
import system.SystemEnvironment;
import system.SystemEnvironment.Constants;
import system.SystemEnvironment.Constants.Permission;
import system.SystemEnvironment.Constants.ServerStatus;
import system.SystemEnvironment.Variables;
import system.containers.DirectoryListingItem;
import system.containers.FilePermission;
import system.containers.HostedFile;
import system.containers.MetaInfo;
import system.containers.Server;
import system.containers.User;
import system.logger.PadFsLogger;
import system.logger.PadFsLogger.LogLevel;
import system.merkleTree.MerkleTree;

public class SqlManager {
		private static Connection c;
		//private static Statement statement;
		private static String dbName; 

		private static final String DB_NAME = "padFsDB";
		private static final String DB_DRIVER = "org.h2.Driver";
	    private static final String DB_CONNECTION = "jdbc:h2:./";
	    private static final String DB_USER = "";
	    private static final String DB_PASSWORD = "";
		private static final String DB_dateFormat = "yyyy-MM-dd HH:mm:ss.SSS";
		 
		

/************************************************************************************
 * CONFIG INIT METHOD FOR DB
 ************************************************************************************/  
		/**
		 * Open the db OR create a new one
		 * @return true if open/create correctly
		 */
        public static boolean open() {
        	SqlManager.dbName = DB_CONNECTION+DB_NAME+";DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";  
            try {
				//Class.forName("org.sqlite.JDBC");
				//c = DriverManager.getConnection("jdbc:sqlite:"+dbName);
            	
				c = getDBConnection();
				
				return creaDB();
            } catch (Exception e) {
            	PadFsLogger.log(LogLevel.FATAL, e.getMessage());
                return false;
            }
            
        }
        
        public static void close(){
        	try {
				if(c != null && !c.isClosed()){
					c.close();
				}
			} catch (SQLException e) {
				PadFsLogger.log(LogLevel.ERROR, "cannot close DataBase");
			}
        }
        
        private static Statement createStatement(){
        	Statement statement = null;
			try {
				statement = c.createStatement(
						ResultSet.TYPE_SCROLL_INSENSITIVE,
				        ResultSet.CONCUR_UPDATABLE);
				statement.setQueryTimeout(30);  
			} catch (Exception e) {
				PadFsLogger.log(LogLevel.FATAL, "ERROR STATEMENT CREATION: "+e.getMessage());
			}
        	return statement;
        }
        
        private static int countRowRS(ResultSet resultSet){
        	int rows = -1;
        	try {
				if(resultSet.isClosed()){
					return -1;
				}
			
	        	try {
					resultSet.last();			
		        	rows = resultSet.getRow();
		        	resultSet.beforeFirst();
	        	} catch (SQLException e) {
	        		PadFsLogger.log(LogLevel.ERROR, e.getMessage());
				}
	        	return rows;
        	} catch (SQLException e1) {
				return -1;
			}
        }
        
        private static Connection getDBConnection() {
            Connection dbConnection = null;
            try {
                Class.forName(DB_DRIVER);
            } catch (ClassNotFoundException e) {
            	PadFsLogger.log(LogLevel.FATAL, e.getMessage());
            }
            try {
                dbConnection = DriverManager.getConnection(dbName, DB_USER, DB_PASSWORD);
                return dbConnection;
            } catch (SQLException e) {
            	PadFsLogger.log(LogLevel.FATAL, e.getMessage());
            }
            return dbConnection;
        }
        
        /**
         * Check if the table in the db exists
         * @param tableName
         * @return true if exists
         */
        private static boolean  checkTableExist(String tableName){
        	try {
        		String sql = "SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME = '"+tableName+"'";
				ResultSet rs = createStatement().executeQuery(sql);
				PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, "* "+sql);
				
				if(countRowRS(rs) > 0){
					rs.close();
					return true;
				}else{
					rs.close();
					return false;
				}
			} catch (Exception e) {
				PadFsLogger.log(LogLevel.WARNING, e.getClass().getName() + ": " + e.getMessage());
                return false;
            }
        }
        
        /**
         * Create the structure of the database IF NOT EXISTS
         * 
         * @return true if created 
         * @return false otherwise
         */
        private static boolean creaDB(){
        	try {
        		if(!checkTableExist("users")){
        			String sql = "CREATE TABLE users ("
        					+ "idUser INTEGER PRIMARY KEY  auto_increment  NOT NULL   , "
        					+ "user VARCHAR(255) NOT NULL   , "
        					+ "password VARCHAR(255) NOT NULL, "
        					+ " UNIQUE KEY uniqUsers (idUser, user) )";
        			try{
        				createStatement().execute(sql);
	        			PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, "* "+cleanStringSpace(sql));
	        		}
	        		catch(Exception e){
	        			PadFsLogger.log(LogLevel.ERROR, "cannot create users table: " + cleanStringSpace(sql));
	        			return false;
	        		}
        		}        		
        		

        		if(!checkTableExist("servers")){
        			String sql = "CREATE TABLE \"servers\" ("
        					+ "\"idServer\" 		BIGINT PRIMARY KEY auto_increment  	NOT NULL,"
        					+ "\"ip\" 				VARCHAR 				NOT NULL,"
        					+ "\"port\" 			INTEGER 				NOT NULL,"
        					+ "\"availableSpace\" 	VARCHAR	 				DEFAULT NULL,"
        					+ "\"totalSpace\" 		VARCHAR					DEFAULT NULL,"
        					+ "\"status\"	 		INTEGER 				NOT NULL DEFAULT (0),"
        					+ "\"keepAlive\" 		BOOL 					NOT NULL DEFAULT false,"
        					+ "\"keepAliveTime\" 	DATETIME 				NOT NULL DEFAULT (CURRENT_TIMESTAMP),"
        					+ "\"replicaGroupId\" 	INTEGER 				NOT NULL DEFAULT (-1),"
        					+ "\"replicaLabel\" 	BIGINT  				NOT NULL DEFAULT ("+Long.toUnsignedString(Constants.maxLabel)+"),"
        					+ "UNIQUE KEY uniqServers (\"ip\", \"port\")  )";
        			try{
        				createStatement().execute(sql);
	        			PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, "* "+cleanStringSpace(sql));
	        		}
	        		catch(Exception e){
	        			PadFsLogger.log(LogLevel.ERROR, "cannot create servers table: " + cleanStringSpace(sql));
	        			return false;
	        		}
        		}        		
        		
        		if(!checkTableExist("filesManaged")){
        			String sql = "CREATE TABLE \"filesManaged\" ("
        					+ "\"idFile\" 		 INTEGER PRIMARY KEY  	auto_increment NOT NULL ,"
        					+ "\"path\" 		 VARCHAR 				NOT NULL ,"
        					+ "\"dateTime\" 	 DATETIME 				NOT NULL  DEFAULT CURRENT_TIMESTAMP ,"
        					+ "\"size\" 		 VARCHAR 				NOT NULL  DEFAULT 0 ,"
        					+ "\"idOwner\" 		 INTEGER 				NOT NULL, "
        					+ "\"label\" 		 BIGINT 				NOT NULL, "
        					+ "\"updatesNumber\" INTEGER 				NOT NULL DEFAULT 1, "
        					+ "\"checksum\" 	 VARCHAR 				NOT NULL ,"
        					+ "\"checksumParent\" VARCHAR 						 ,"
        					+ "\"isDirectory\" 	 BOOL 					NOT NULL DEFAULT true)";

        			
        			try{
        				createStatement().execute(sql);
	        			PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, "* "+cleanStringSpace(sql));
	        		}
	        		catch(Exception e){
	        			PadFsLogger.log(LogLevel.ERROR, "cannot create filesManaged table: " + cleanStringSpace(sql));
	        			return false;
	        		}
        		}    
        		
        		if(!checkTableExist("directoryListing")){
        			String sql = "CREATE TABLE \"directoryListing\" ("
        					+ "\"idFile\" 		 INTEGER PRIMARY KEY  	auto_increment NOT NULL ,"
        					+ "\"path\" 		 VARCHAR 				NOT NULL ,"
        					+ "\"label\" 		 BIGINT 				NOT NULL ,"
        					+ "\"idOwner\" 		 INTEGER 				NOT NULL, "
        					+ "\"idParentDirectory\" VARCHAR 			NOT NULL, "
        					+ "\"size\"  			VARCHAR 			NOT NULL  DEFAULT 0 ,"
        					+ "\"dateTime\" 	 DATETIME 				NOT NULL  ,"
        					+ "\"isDirectory\" 	 BOOL 					NOT NULL)";
        			try{
        				createStatement().execute(sql);
	        			PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, "* "+cleanStringSpace(sql));
	        		}
	        		catch(Exception e){
	        			PadFsLogger.log(LogLevel.ERROR, "cannot create directoryListing table: " + cleanStringSpace(sql));
	        			return false;
	        		}
        		}    
        		
        		if(!checkTableExist("tmpFiles")){
        			String sql = "CREATE TABLE \"tmpFiles\" ("
        					+ "\"idTmpFile\" INTEGER PRIMARY KEY auto_increment NOT NULL,"
        					+ "\"path\" VARCHAR NOT NULL ,"
        					+ "\"dateTime\" DATETIME NOT NULL  DEFAULT (CURRENT_TIMESTAMP) ,"
        					+ "\"size\" VARCHAR NOT NULL  DEFAULT (0) ,"
        					+ "\"idUploader\" INTEGER NOT NULL)";
        			try{
        				createStatement().execute(sql);
	        			PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, "* "+cleanStringSpace(sql));
	        		}
	        		catch(Exception e){
	        			PadFsLogger.log(LogLevel.ERROR, "cannot create tmpFiles table: " + cleanStringSpace(sql));
	        			return false;
	        		}
        		}        		
        		
        		if(!checkTableExist("grant")){
        			String sql = "CREATE TABLE \"grant\" ("
        					+ "\"idFile\" INTEGER NOT NULL , "
        					+ "\"idUser\" INTEGER NOT NULL , "
        					+ "\"permission\" VARCHAR NOT NULL )";
        			try{
        				createStatement().execute(sql);
	        			PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, "* "+cleanStringSpace(sql));
	        		}
	        		catch(Exception e){
	        			PadFsLogger.log(LogLevel.ERROR, "cannot create grant table: " + cleanStringSpace(sql));
	        			return false;
	        		}
        		}        		
        		
        		/*if(!checkTableExist("upload")){
        			String sql = "CREATE TABLE \"upload\" ("
        					+ "\"idTmpFile\" INTEGER NOT NULL , "
        					+ "\"idUser\" INTEGER NOT NULL )";
	        		createStatement().execute(sql);
	    			PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, "* "+cleanStringSpace(sql));
        		}  */  
        		
        		/*if(!checkTableExist("own")){
        			String sql = "CREATE TABLE \"own\" ("
        					+ "\"idFile\" INTEGER NOT NULL , "
        					+ "\"idUser\" INTEGER NOT NULL )";
	        		createStatement().execute(sql);
	    			PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, "* "+cleanStringSpace(sql));
        		}  */  
        		
        		if(!checkTableExist("host")){
        			String sql = "CREATE TABLE \"host\" ("
        					+ "\"idFile\" INTEGER NOT NULL , "
        					+ "\"idServer\" INTEGER NOT NULL )";
        			try{
        				createStatement().execute(sql);
	        			PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, "* "+cleanStringSpace(sql));
	        		}
	        		catch(Exception e){
	        			PadFsLogger.log(LogLevel.ERROR, "cannot create host table: " + cleanStringSpace(sql));
	        			return false;
	        		}
        		}
        		
        	
        		
        		if(!checkTableExist("consensusGroups")){
        			String sql = "CREATE TABLE consensusGroups ("
        					+ "idConsensusGroup BIGINT PRIMARY KEY  auto_increment  NOT NULL, "
        					+ "hpn  			CLOB	NOT NULL  DEFAULT ('{\"number\":0,\"nodeId\":0}'), "
        					+ "hapn 			CLOB 	NOT NULL  DEFAULT ('{\"number\":0,\"nodeId\":0}'), "
        					+ "hapv 			CLOB 	DEFAULT (NULL), "
        					+ "hapvType 		CLOB	DEFAULT (NULL),"
        					+ "idConsensusRun 	INTEGER NOT NULL DEFAULT (0),"
        					+ " UNIQUE KEY uniqConsensusGroups ( idConsensusGroup ) )";
        			try{
        				createStatement().execute(sql);
	        			PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, "* "+cleanStringSpace(sql));
	        		}
	        		catch(Exception e){
	        			PadFsLogger.log(LogLevel.ERROR, "cannot create consensusGroups table: " + cleanStringSpace(sql));
	        			return false;
	        		}
        		}
        		
        		if(!checkTableExist("participate")){
        			String sql = "CREATE TABLE \"participate\" ("
        					+ "\"idConsensusGroup\" INTEGER NOT NULL , "
        					+ "\"idServer\" INTEGER NOT NULL, "
        					+ "UNIQUE KEY uniqParticipate (\"idConsensusGroup\", \"idServer\")  )";
        			try{
        				createStatement().execute(sql);
	        			PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, "* "+cleanStringSpace(sql));
	        		}
	        		catch(Exception e){
	        			PadFsLogger.log(LogLevel.ERROR, "cannot create participate table: " + cleanStringSpace(sql));
	        			return false;
	        		}
        		}
        		
        		if(!checkTableExist("filesHosted")){
        			String sql = "CREATE TABLE filesHosted ("
        					+ "idFile 			INTEGER PRIMARY KEY auto_increment NOT NULL ,"
        					+ "idUser		 	VARCHAR NOT NULL ,"
        					+ "logicalPath 		VARCHAR NOT NULL ,"
        					+ "physicalPath 	VARCHAR NOT NULL ,"
        					+ "checksum		 	VARCHAR NOT NULL ,"
        					+ "dateTime 		DATETIME NOT NULL  DEFAULT (CURRENT_TIMESTAMP) ,"
        					+ "size 			VARCHAR NOT NULL  DEFAULT (0) ,"
        					+ "uploadingFlag 	BOOL    NOT NULL  DEFAULT true ,"
        					+ "label 			BIGINT NOT NULL )";
        			try{
        				createStatement().execute(sql);
	        			PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, "* "+cleanStringSpace(sql));
	        		}
	        		catch(Exception e){
	        			PadFsLogger.log(LogLevel.ERROR, "cannot create filesHosted table: " + cleanStringSpace(sql));
	        			return false;
	        		}
        		}
        		
        		PadFsLogger.log(LogLevel.CONFIG, "- CREATO DB");

        		String []infoUser = null;
        		if( (infoUser=getInfoUser(Constants.defaultAdminUsername) ) ==null){	//TODO  mettere in Variables la password dell'admin per dare la possibilità di modificarla dal file di config
        				addUser(Constants.defaultAdminUsername,Constants.defaultAdminPassword);
        				infoUser=getInfoUser(Constants.defaultAdminUsername);
        		}
        		if( getDir(Constants.rootDirectory, Integer.valueOf(infoUser[0])) == null ){
        				addDir(Constants.rootDirectory, 
        						Constants.defaultAdminUsername, 
        						SystemEnvironment.getLabel(Constants.defaultAdminUsername,Constants.fileSeparator), 
        						Constants.uniqueIdAdminRootDirectory, 	//uniqueId. never used in the net
        						null);
        		}
        		
        		PadFsLogger.log(LogLevel.CONFIG, "- DEFAULT USER: "+Constants.defaultAdminUsername+ " PASSWORD: "+infoUser[1]);
        		
        		if(! globalConsensusGroupExist() )
        			addGlobalConsensusGroup();
        		
        		
        		/* initialize GlobalConsensusVariables */
        		Variables.consensusVariableManager.initializeGlobalConsensusVariables();
        		//Variables.GlobalConsensusVariables.init();
        		
        	}catch (Exception e) {
        		PadFsLogger.log(LogLevel.ERROR, e.getMessage());
                return false;
            }
        	
            return true;
        }
        
        /**
         * Add the global consensus group from Constants to database table "consensusGroups"
         */
        private static void addGlobalConsensusGroup() {
			SqlManager.insert("consensusGroups", new String[] {"idConsensusGroup"}, new String[]{String.valueOf(Constants.globalConsensusGroupId)}, true);
		}

        /**
         * Check if the globalConsensus group identifier in the Constants exists in the database table "consensusGroups"
         * @return true if exists
         * @retunr false otherwise
         */
        private static boolean globalConsensusGroupExist() {
			ResultSet res;
			res = SqlManager.select("consensusGroups", new String[] {"idConsensusGroup"}, " \"idConsensusGroup\" = "+Constants.globalConsensusGroupId);
			try {
				if(res.next()){
					res.close();
					return true;
				}else{
					res.close();
					return false;
				}
			} catch (SQLException e) {
				PadFsLogger.log(LogLevel.ERROR, e.getMessage());
				return false;
			}
		}

		public static List<MetaInfo>  getSharedFileWith(String username) {
			ResultSet res;
			List<MetaInfo> sharedFile = new ArrayList<>();
			int idUser = getIdUser(username);
			
			String sql = "SELECT F.* FROM " +
						 " PUBLIC.\"filesManaged\" AS F INNER JOIN PUBLIC.\"grant\" AS G ON F.\"idFile\" = G.\"idFile\"  "+
						 " WHERE F.\"idOwner\" <> G.\"idUser\" AND G.\"idUser\" = " +idUser;
			String nameOwner;
			res = executeQuery(sql,true);
			try {
				if (res==null) return null;

				while(res.next()){
					nameOwner = SqlManager.getUsername(res.getInt("idOwner"));


					sharedFile.add(new  MetaInfo(
							res.getLong("idFile"),
							res.getString("path"),
							res.getString("dateTime"),
							res.getString("size"),
							res.getInt("idOwner"),
							nameOwner,
							res.getLong("label"),
							res.getInt("updatesNumber"),
							res.getBoolean("isDirectory"),
							res.getString("checksum"),
							res.getString("checksumParent")
					));
				}
			} catch (SQLException e) {
				PadFsLogger.log(LogLevel.ERROR,e.getMessage());
			}


			return sharedFile;
		}
        
/************************************************************************************
 * FILE METHOD FOR DB
 ************************************************************************************/  
        /**
         * Check if the file i a TMP file. Check in THE DB
         * @param 	path	The file path 
         * @param 	idOwner The owner identifier of the file
         * 
         * @return 	true 	if is TMP
         * @return 	false 	otherwise
         */
        public static boolean isFileTMP(String path, int idOwner){

        	
        	if(!Padfs.validateString(new String[]{path})){
        		return false;
        	}
        	
        	ResultSet r = select("tmpFiles", new String[]{"idTmpFile"}, " \"path\"='"+path+"' AND \"idUploader\"="+idOwner);
			try {
				if (countRowRS(r)>0) {
					r.close();
					return false;
				}else{
					r.close();
					return true;
				}				
			} catch (SQLException e) {
				PadFsLogger.log(LogLevel.ERROR, e.getMessage());
				return false;
			}
        }
        
        
        /**
         * Check if the file for the user is present or not
         * @param path		The file path 
         * @param idOwner	The owner identifier of the file 
         * 
         * @return true 	if is present
         * @return false 	otherwise
         */
        public static boolean fileUserExists(String path, int idOwner){       	
        	
        	if(!Padfs.validateString(new String[]{path})){
        		return false;
        	}
        		
			ResultSet r = select("filesManaged", new String[]{"idFile"}, " \"isDirectory\" = 0 AND \"path\"='"+path+"' AND \"idOwner\"="+idOwner);
			if(r == null)
				return false;
			
			try {
				if (countRowRS(r) > 0) {
					r.close();
					return true;
				}else{
					r.close();
					return false;
				}
			} catch (SQLException e) {
				PadFsLogger.log(LogLevel.ERROR, e.getMessage());
				try {
					r.close();
				} catch (SQLException e1) {
					PadFsLogger.log(LogLevel.ERROR, e1.getMessage());
				}
				return false;
			}
        }
        
        /**
         * Check if the file for the user is hosted or not
         * @param path		The file path 
         * @param idOwner	The owner identifier of the file 
         * 
         * @return true 	if is present
         * @return false 	otherwise
         */
        public static boolean fileUserHosted(String path, int idOwner, String checksum){       	
        	
        	if(!Padfs.validateString(new String[]{path})){
        		return false;
        	}
        		
			ResultSet r = select("filesHosted", new String[]{"idFile"}, " \"logicalPath\"='"+path+"' AND \"idUser\"="+idOwner + " AND \"checksum\"='"+checksum+"'");
			try {
				if (countRowRS(r) > 0) {
					r.close();
					return true;
				}else{
					r.close();
					return false;
				}
			} catch (SQLException e) {
				PadFsLogger.log(LogLevel.ERROR, e.getMessage());
				try {
					r.close();
				} catch (SQLException e1) {
					PadFsLogger.log(LogLevel.ERROR, e1.getMessage());
				}
				return false;
			}
        }
        
        public static List<Long> getHostersId(Long metaInfoId) {
        	PadFsLogger.log(LogLevel.TRACE, "retrieve hosters of metaInfoId: "+metaInfoId+" file");
        	List<Long> ret = new LinkedList<>();
			ResultSet r = select("host",new String[]{"idServer"},"\"idFile\"="+metaInfoId);
			try {
				while(r.next()){
					ret.add(r.getLong("idServer"));
				}
			} catch (SQLException e) {
				PadFsLogger.log(LogLevel.ERROR, e.getMessage());
				return null;
			}
			return ret;
		}
        
        
        public static List<FilePermission> getPermissionList(Long idMetaInfo) {
        	PadFsLogger.log(LogLevel.TRACE, "retrieve permissions of metainfo: "+idMetaInfo+" metaInfoId");
        	List<FilePermission> ret = new LinkedList<>();
			ResultSet r = select("grant",new String[]{"idUser","permission"},"\"idFile\"="+idMetaInfo);
			try {
				while(r.next()){
					FilePermission tmp = new FilePermission(
							r.getInt("idUser"),
							Permission.convert(r.getInt("permission"))
							);
					ret.add(tmp);
				}
			} catch (SQLException e) {
				PadFsLogger.log(LogLevel.ERROR, e.getMessage());
				return null;
			}
			return ret;
		}
        
        /**
         * Return from the filesManaged table the file updates with the idFile 
         * @param idFile the id of the file 
         * @return the update number 
         * @return -1 otherwise 
         */
        public static int getUpdateNumber(long idFile){
        	if(idFile < 1){
        		return -1;
        	}
        	int i = -1;
        	ResultSet r = select("filesManaged", new String[]{"idFile","updatesNumber"}, "\"idFile\"="+idFile);
			try {
				if (countRowRS(r)>0 ) {
					r.next();
					i = r.getInt(2);
					r.close();
					return i;
				}else{
					r.close();
					return -1;					
				}				
			} catch (Exception e) {
				PadFsLogger.log(LogLevel.ERROR, e.getMessage());
				try {
					r.close();
				} catch (SQLException e1) {
					PadFsLogger.log(LogLevel.ERROR, e1.getMessage());
				}
				return -1;
			}
        }
                
        /**
         * Add file to the DB as tmp file
         * @param path	The file path
         * @param size  IN MB 
         * @param idUploader the identifier of the owner of the file
         * 
         * @return id   of the file insert 
         * @return null otherwise 
         */
        public static Long addFileASTMP(String path, String size, int idUploader, boolean autoCommit){
        	
        	
        	if(!Padfs.validateString(new String[]{path,size})){
        		PadFsLogger.log(LogLevel.ERROR, "PARAMETER ERROR - path: "+path+ " - size: "+size+" - idOwner: " + idUploader);
        		return null;
        	}
        	
        	String[] fields = {"path", "size", "idUploader"};
			String[] data 	= {path,size,String.valueOf(idUploader)};
			
			Long id = null;
			
			id = insert("tmpFiles",fields,data,autoCommit);
			
			if( id == null){
				PadFsLogger.log(LogLevel.ERROR, "INSERT ERROR - path: "+path+ " - idUploader: " + idUploader);
				return null;	
			}
			return id;	
        }
        
        
        /**
         * Retrieve the file id 
         * @param logicalPath    The logicalPath of the file
         * @param idOwner 		The owner identifier of the file
         * @param checksum		the checksum of the file
         * 
         * @return id   of the file
         * @return null otherwise
         */
        public static synchronized Long getHostedFileId(int idOwner, String logicalPath, String checksum) {
        	if(!Padfs.validateString(new String[]{logicalPath,checksum})){
        		PadFsLogger.log(LogLevel.ERROR, "PARAMETER ERROR [getIdFile] - logicalPath: "+logicalPath+ " - checksum: "+checksum+ " - idUser: " + idOwner);
        		return null;
        	}
 
        	Long i = null;        	
        	ResultSet r = select("filesHosted", new String[]{"idFile"}, " \"logicalPath\"='"+logicalPath+"' AND \"idUser\"= "+idOwner + " AND \"checksum\"= '"+checksum+"'");
			try {
				if (countRowRS(r)>0) {
					r.next();
					i = r.getLong("idFile");
					r.close();
					return i;
				}else{
					r.close();
					return null;
				}				
			} catch (SQLException e) {
				PadFsLogger.log(LogLevel.ERROR, e.getMessage());
				try {
					r.close();
				} catch (SQLException e1) {
					PadFsLogger.log(LogLevel.ERROR, e1.getMessage());
				}
				return null;
			}
		}
        
        /**
         * Retrive the file id 
         * @param path    The path of the file
         * @param idOwner The owner identifier of the file
         * 
         * @return id   of the file
         * @return null otherwise
         */
        public static Integer getIdFile(String path, int idOwner){
        	        	
        	if(!Padfs.validateString(new String[]{path})){
        		PadFsLogger.log(LogLevel.ERROR, "PARAMETER ERROR [getIdFile] - path: "+path+ " - idOwner: " + idOwner);
        		return null;
        	}
        	int i = -1;        	
        	ResultSet r = select("filesManaged", new String[]{"idFile"}, " \"path\"='"+path+"' AND \"idOwner\"= "+idOwner );
			try {
				if (countRowRS(r)>0) {
					r.next();
					i = r.getInt("idFile");
					r.close();
					return i;
				}else{
					r.close();
					return null;
				}				
			} catch (SQLException e) {
				PadFsLogger.log(LogLevel.ERROR, e.getMessage());
				try {
					r.close();
				} catch (SQLException e1) {
					PadFsLogger.log(LogLevel.ERROR, e1.getMessage());
				}
				return null;
			}
        }
        
        public static Long getIdTMPFile(String path, int idUploader){
        	
        	if(!Padfs.validateString(new String[]{path})){
        		PadFsLogger.log(LogLevel.ERROR, "PARAMETER ERROR - path: "+path+ " - idUploader: " + idUploader);
        		return null;
        	}
        	
        	long i = -1;
        	ResultSet r = select("tmpFiles", new String[]{"idTmpFile"}, " \"path\"='"+path+"' AND \"idUploader\"= "+idUploader);

			try {
				if (countRowRS(r)>0 ) {
					r.next();
					i = r.getLong("idTmpFile");
					r.close();
					return i;
				}else{					
					PadFsLogger.log(LogLevel.ERROR, "RETRIVE ID TEMP FILE ERROR: "+r.getInt("idTmpFile") );
					r.close();
					return null;
				}				
			} catch (SQLException e) {
				PadFsLogger.log(LogLevel.ERROR, e.getMessage());
				return null;
			}
        }
        
        /*
         * Execute the update of the file in the DB of the field tmp
         * @param path		file path
         * @param idOwner	
         * 
         * @return true if update is done
         
        public static Long mvTmp2FsDB(String path,  int idOwner){
        	
        	if(!Padfs.validateString(new String[]{path})){
        		PadFsLogger.log(LogLevel.ERROR, "PARAMETER ERROR - path: "+path+ " - idOwner: " + idOwner);
        		return null;
        	}
        	
        	String[] fields = new String[]{"idTmpFile","path", "dateTime", "size", "idUploader"};
        	String[] data   = null;
        	int rowId = -1;
        	
        	ResultSet r = select("tmpFiles", fields, " \"path\"='"+path+"' AND \"idUploader\"= "+idOwner);
			try {
				if (countRowRS(r)>0) {
					r.next();
					
					rowId = r.getInt("idTmpFile");
					
					fields = new String[]{
							"path",
							"dateTime",
							"size",
							"idOwner",
							"label",
							"isDirectory"
							};
		        	data   = new String[]{ 
		        			r.getString("path"),
		        			r.getString("dateTime"),
		        			r.getString("size"),
		        			r.getString("idUploader"),
		        			String.valueOf(Constants.hashFunction.evaluate(r.getString("path"))),
		        			"0"  
		        			};
		        	
		        	r.close();
		        	Long res = insert("filesManaged", fields, data,false) ;
		        	
		        	if(res != null){
		        		if( delete("tmpFiles", " \"idTmpFile\" = "+rowId+"  ",true) )
		        			return res;
		        	}
		        	rollback();
		        	r.close();
		        	return null;
				}else{
					r.close();
					return null; //NO RESULTS
				}
			} catch (SQLException e) {
				PadFsLogger.log(LogLevel.ERROR, e.getMessage());
				rollback();
				try {
					r.close();
				} catch (SQLException e1) {
					PadFsLogger.log(LogLevel.ERROR, e1.getMessage());
				}
				return null;
			}

        }
        */
        
        public static boolean deleteTmpDB(Long idFile){

    		if( delete("tmpFiles", "idTmpFile='"+idFile+"'",true) )
    			return true;
    		else
    			rollback();
    		
        	return false;				
        }

        /**
         * Insert in the database the files in the File System
         * @param logicalPath
		 * @param physicalPath
         * @param size
         * @param label
         * 
         * @return String file id inserted in the database
         * @return null   otherwise
         */
        public static synchronized Long insertHostedFile(Integer idUser, String logicalPath, String physicalPath, String size, long label, String checksum){
        	
        	if(!Padfs.validateString(new String[]{logicalPath,physicalPath,size}) ){
        		PadFsLogger.log(LogLevel.ERROR, "PARAMETER ERROR -  logical path: "+logicalPath+ " physical path: "+physicalPath+ " - size: " + size);
        		return null;
        	}
        	
        	if(idUser == null){
        		PadFsLogger.log(LogLevel.ERROR, "idUser can't be null");
        		return null;
        	}
        	
        	//check that the file with the same checksum is not already present
        	if(SqlManager.getHostedFileId(idUser, logicalPath, checksum) != null){
        		PadFsLogger.log(LogLevel.DEBUG, "file alredy present in DB");
        		return null;
        	}
        	
        	Long idFile = insert("filesHosted", 
        			new String[] {"idUser","logicalPath", "physicalPath", "size", "label", "checksum"}, 
        			new String[] {idUser.toString(), logicalPath,physicalPath, size, Long.toUnsignedString(label), checksum}, true ) ;
        	
        	if( idFile != null ){
        		return idFile;
        	}
        	return null;
        }
        
        
        public static Long insertMetaInfoWithoutPermission(String logicalPath, String size, long idOwner, long label, long[] serverIdList, String checksum, String checksumParent){
        	
        	if(!Padfs.validateString(new String[]{logicalPath,size}) ){
        		PadFsLogger.log(LogLevel.ERROR, "PARAMETER ERROR - logical path: "+logicalPath+" - size: " + size);
        		return null;
        	}
        	
        	Long idFile = null;
        	Long idFileTMP;
        	Integer updatesNumber = 0;
        	boolean alreadyInserted=false;
        	
        	ResultSet resp = select("filesManaged",new String[] {"idFile", "updatesNumber"}," \"path\"='"+logicalPath+"' AND \"idOwner\"="+idOwner);
        	if(countRowRS(resp)>0){
        		alreadyInserted=true;
        	}
        	try {
        		if(alreadyInserted){
					resp.next();
					idFile = resp.getLong("idFile");
					updatesNumber = resp.getInt("updatesNumber")+1;
					update("filesManaged", 
		        			new String[] {"path", "size", "idOwner", "label", "isDirectory","dateTime","updatesNumber","checksum"}, 
		        			new String[] {logicalPath,size, String.valueOf(idOwner), Long.toUnsignedString(label), "0", SystemEnvironment.getDateTime(),String.valueOf(updatesNumber),checksum},
		        			" \"idFile\"="+idFile,
		        			false ) ;
					
					resp.close();   
				}else{
					idFileTMP = insert("filesManaged", 
		        			new String[] {"path", "size", "idOwner", "label", "isDirectory","checksum","checksumParent"}, 
		        			new String[] {logicalPath,size, String.valueOf(idOwner), Long.toUnsignedString(label), "0",checksum,checksumParent}, false ) ;
					if(idFileTMP == null){
						PadFsLogger.log(LogLevel.ERROR, "idFile is null");
						return null;
					}
					idFile = idFileTMP;
					resp.close();
				}
				
				/*if(idFile == null){
					PadFsLogger.log(LogLevel.ERROR, "missing idFile");
	        		rollback();
	        		return null;
				}*/
        		
	        	/* update the list of the servers that hosted this file */
	        	if(alreadyInserted){
	        		/* if there exists already this server list remove it before we insert the new one */
	        		if(!delete("host", "\"idFile\"="+Long.toUnsignedString(idFile), false)){
	        			rollback();
    					PadFsLogger.log(LogLevel.ERROR, "can't delete from host table");
    					return null;
	        		}
	        	}
	        
	        		
    			for( long sid : serverIdList ){
    				if(! insertNoId("host", 
                			new String[] {"idFile", "idServer"}, 
                			new String[] {String.valueOf(idFile),String.valueOf(sid)}, false ) ){
    					
    					rollback();
    					PadFsLogger.log(LogLevel.ERROR, "can't update host table");
    					return null;
    				}
    			}   
    			commit();
    			return idFile;
        	
	        	
        	} catch (SQLException e) {
				idFile = null;
				PadFsLogger.log(LogLevel.ERROR, "query ERROR");
				try {
					resp.close();
				} catch (SQLException e1) {
					PadFsLogger.log(LogLevel.ERROR, "query ERROR");
				}
			}

        	return null;
        }
        
        
        
     
        
        
        
        /**
         * store in the filesManaged the metaInfo list
         * 
         * @param metaInfoList
         * @return
         */
		public static boolean storeMetaInfo(List<MetaInfo> metaInfoList) {
			

			MetaInfo m;
			String path;
			String dateTime;
			String size;
			int idOwner;
			long label;
			int updatesNumber;
			boolean isDirectory;
			String checksum;
			String checksumParent;
			
    		for(int i=0; i< metaInfoList.size(); i++){
    			String[] fields = new String[]{"path","dateTime","size","idOwner","label","updatesNumber","isDirectory","checksum","checksumParent"};
    			Long insertedId = null;
    			
    			m = metaInfoList.get(i);
    			if(m != null){
    				
    				path 		  = m.getPath();
    				dateTime 	  = m.getDateTime();
					size	  	  = m.getSize();
					idOwner		  = m.getIdOwner();
    				label	 	  = m.getLabel();
    				updatesNumber = m.getUpdatesNumber();
    				isDirectory	  = m.isDirectory();
    				checksum 	  = m.getChecksum();
    				checksumParent= m.getChecksumParent();
    				
    				String[] values = new String[ ]{path,
    												dateTime,
    												size,
    												String.valueOf(idOwner),
    												Long.toUnsignedString(label),
    												String.valueOf(updatesNumber),
    												Boolean.toString(isDirectory),
    												checksum,
    												checksumParent};

    				insertedId = insert("filesManaged",fields,values,false);
    				if(insertedId == null){
    					PadFsLogger.log(LogLevel.ERROR, "failed to store metaInfo");
    				}
    			}
    			


    			/* store hostersId */
    			if(m.getHostersId() != null && m.getHostersId().size() > 0){ 
    				/*
    				 * only files have hostersId. directory do not neet it
    				 */
	    			Iterator<Long> it = m.getHostersId().iterator();
	    			StringBuilder sql = new StringBuilder();
	    			sql.append("INSERT INTO host (idFile,idServer) VALUES ");
	    			while(it.hasNext()){
	    				sql.append("("
	    							+insertedId +","
	    							+it.next()
	    							+")");
	    				
	    				if(it.hasNext())
	    					sql.append(",");
	    			}
    			
	        		PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, "*** "+sql.toString());
	    			if(!executeQueryModifyOLD(sql.toString(),true)){
	    				rollback();
	    				PadFsLogger.log(LogLevel.ERROR, "failed updating host table");
	    				return false;
	    			}
    			}
    			
    			
    			/* store metaInfoList */
    			if(m.getPermissionList() != null && m.getPermissionList().size() > 0){ 
    				
	    			Iterator<FilePermission> it = m.getPermissionList().iterator();
	    			StringBuilder sql = new StringBuilder();
	    			sql.append("INSERT INTO grant (idFile,idUser,permission) VALUES ");
	    			while(it.hasNext()){
	    				FilePermission p = it.next();
	    				if(p != null){
		    				sql.append("("
		    							+insertedId +","
				    					+p.getIdUser()+","
						    			+p.getPermission().getNumVal()
		    							+")");
		    				
		    				if(it.hasNext())
		    					sql.append(",");
	    				}
	    			}
    			
	        		PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, "*** "+sql.toString());
	    			if(!executeQueryModifyOLD(sql.toString(),true)){
	    				rollback();
	    				PadFsLogger.log(LogLevel.ERROR, "failed updating grant table");
	    				return false;
	    			}
    			}
    			
    		}

    		return true;
		}

		//TODO change idFile to Long   (BIGINT in the DB)
		public static MetaInfo getMetaInfo(Integer idFile) {
			Long id = Long.valueOf(idFile);
			return getMetaInfo(id);
		}
		public static MetaInfo getMetaInfo(Long idFile) {
			String[] fields = new String[]{"idFile","path","dateTime","size","idOwner","label","updatesNumber","isDirectory","checksum","checksumParent"};
			ResultSet r = select("filesManaged", fields, 
						"\"idFile\" = "+idFile);
			
			MetaInfo metaInfo = null;
			if(r != null){
				try {
					
					if(r.next()){
						metaInfo = new MetaInfo(
										r.getLong(fields[0]),
										r.getString(fields[1]),
										r.getString(fields[2]),
										r.getString(fields[3]),
										r.getInt(fields[4]),
										r.getLong(fields[5]),
										r.getInt(fields[6]),
										r.getBoolean(fields[7]),
										r.getString(fields[8]),
										r.getString(fields[9])
										);
						
					}
					else{
						PadFsLogger.log(LogLevel.DEBUG,"no metaInfo with idFile " + idFile + " is present in the DB");
					}
					
					r.close();
				} catch (SQLException e) {
					PadFsLogger.log(LogLevel.ERROR,"failed retrieving the metaInfo: " + e.getMessage());
					
					try {
						r.close();
					} catch (SQLException e1) {
						PadFsLogger.log(LogLevel.ERROR,e1.getMessage());
						
					}
					return null;
				}

				return metaInfo;
			}
			
			PadFsLogger.log(LogLevel.ERROR, "resultSet is null");
			return null;
		}

		
		public static List<HostedFile> getHostedFiles() {
			String[] fields = new String[]{"idFile","logicalPath","physicalPath","dateTime",
					"size","idUser","label","checksum","uploadingFlag"};
			ResultSet r = select("fileshosted", fields, null);
			
			
			if(r != null){
				List<HostedFile> l = new LinkedList<HostedFile>();
				try {
					HostedFile file;
					while(r.next()){
						file = new HostedFile(
										r.getString(fields[1]), 
										r.getString(fields[2]),
										r.getString(fields[3]),
										r.getString(fields[4]),
										r.getInt(fields[5]),
										r.getLong(fields[6]),
										r.getString(fields[7]),
										r.getBoolean(fields[8])
										);
						l.add(file);
					}
					r.close();
				} catch (SQLException e) {
					PadFsLogger.log(LogLevel.ERROR,"failed creating the hostedFile list: " + e.getMessage());
					
					try {
						r.close();
					} catch (SQLException e1) {
						PadFsLogger.log(LogLevel.ERROR,e1.getMessage());
						
					}
					return null;
				}

				return l;
			}
			
			PadFsLogger.log(LogLevel.ERROR, "resultSet is null");
			return null;
		}
		
		
		public static List<MetaInfo> getManagedFiles() {
			return getMetaInfo(0,Constants.maxLabel,false);
		}
		
		
		public static MetaInfo getManagedFile(String user, String path, String checksum) {
			int idUser = getIdUser(user);
			return getManagedFile(idUser,path,checksum);
		}
		
		public static MetaInfo getManagedFile(int idUser, String path,String checksum) {	
			String where = "";
			if(checksum != null && !checksum.equals("") && !checksum.equals("null")){
				where = " AND \"checksum\" = '" + checksum + "' ";
			}
			
			String[] fields = new String[]{"idFile","path","dateTime","size","idOwner","label","updatesNumber","isDirectory","checksum","checksumParent"};
			ResultSet r = select("filesManaged", fields, 
						"\"idOwner\" = "+idUser+ 
						" AND \"path\" = '" + path +"'"+ where);
			
			if(r != null){
				try {
					MetaInfo metaInfo = null;
					if(r.next()){
						metaInfo = new MetaInfo(
								r.getLong(fields[0]),
								r.getString(fields[1]),
								r.getString(fields[2]),
								r.getString(fields[3]),
								r.getInt(fields[4]),
								r.getLong(fields[5]),
								r.getInt(fields[6]),
								r.getBoolean(fields[7]),
								r.getString(fields[8]),
								r.getString(fields[9])
								);
					}
					else{
						PadFsLogger.log(LogLevel.DEBUG, "file not found in filesManaged");
					}
					r.close();
					return metaInfo;
				} catch (SQLException e) {
					PadFsLogger.log(LogLevel.ERROR,"failed creating the metaInfo list: " + e.getMessage());
					
					try {
						r.close();
					} catch (SQLException e1) {
						PadFsLogger.log(LogLevel.ERROR,e1.getMessage());
						
					}
					return null;
				}

			}
			
			return null;
		}

		 /**
         * 
         * @param startLabel
         * @param endLabel
         * @return the list of metaInfo with label in between the 2 labels
         */
		public static List<MetaInfo> getMetaInfo(long startLabel, long endLabel) {
			return getMetaInfo(startLabel,endLabel,false);
		}
		
        /**
         * 
         * @param startLabel
         * @param endLabel
         * @param noDir  prevent to list the directories
         * @return the list of metaInfo with label in between the 2 labels
         */
    	public static List<MetaInfo> getMetaInfo(long startLabel, long endLabel, boolean noDir) {
    		String[] fields = new String[]{"idFile","path","dateTime","size","idOwner","label","updatesNumber","isDirectory","checksum","checksumParent"};
    		String dirFilter = "";
    		if(noDir) dirFilter = " AND \"isDirectory\" = 0 ";
			ResultSet r = select("filesManaged", fields, 
						"\"label\" >= "+Long.toUnsignedString(startLabel)+ 
						" AND \"label\" <= " + Long.toUnsignedString(endLabel) + dirFilter );
			
			
			if(r != null){
				List<MetaInfo> l = new LinkedList<MetaInfo>();
				try {
					MetaInfo metaInfo;
					while(r.next()){
						metaInfo = new MetaInfo(
										r.getLong(fields[0]),
										r.getString(fields[1]),
										r.getString(fields[2]),
										r.getString(fields[3]),
										r.getInt(fields[4]),
										r.getLong(fields[5]),
										r.getInt(fields[6]),
										r.getBoolean(fields[7]),
										r.getString(fields[8]),
										r.getString(fields[9])
										);
						l.add(metaInfo);
					}
					r.close();
				} catch (SQLException e) {
					PadFsLogger.log(LogLevel.ERROR,"failed creating the metaInfo list: " + e.getMessage());
					
					try {
						r.close();
					} catch (SQLException e1) {
						PadFsLogger.log(LogLevel.ERROR,e1.getMessage());
						
					}
					return null;
				}

				return l;
			}
			
			PadFsLogger.log(LogLevel.ERROR, "resultSet is null");
			return null;
		}
        
    	private static List<Long> getMetaInfoId(int startLabel, long endLabel) {
    		String[] fields = new String[]{"idFile"};
			ResultSet r = select("filesManaged", fields, 
						"\"label\" >= "+Long.toUnsignedString(startLabel)+ 
						" AND \"label\" <= " + Long.toUnsignedString(endLabel) );
			
			
			if(r != null){
				List<Long> l = new LinkedList<Long>();
				try {
					while(r.next()){
						l.add(r.getLong(fields[0]));
					}
					r.close();
				} catch (SQLException e) {
					PadFsLogger.log(LogLevel.ERROR,"failed creating the metaInfoId list: " + e.getMessage());
					
					try {
						r.close();
					} catch (SQLException e1) {
						PadFsLogger.log(LogLevel.ERROR,e1.getMessage());
					}
					return null;
				}

				return l;
			}
			
			PadFsLogger.log(LogLevel.ERROR, "resultSet is null");
			return null;
 		}
        
        
/************************************************************************************
 * SERVER METHOD FOR DB
 ************************************************************************************/    
        /**
         * Given a serverLabel return the list of the servers that manage the label in all groups
         * @param serverLabel the label 
         * 
         * @return list of servers id in the different group
         * @return null otherwise 
         */
		public synchronized static long[] getIdFromConsensusLabel(long serverLabel) {
			/*  
			 * (SELECT idServer FROM servers WHERE replicaLabel <= 8 AND replicaGroupId=1 
			 *   ORDER BY replicaLabel DESC LIMIT 1) 

				UNION
				
				(SELECT idServer FROM servers WHERE replicaLabel <= 8 AND replicaGroupId=2 
				ORDER BY replicaLabel DESC LIMIT 1) 
				
				UNION
				
				(SELECT idServer FROM servers WHERE replicaLabel <= 8 AND replicaGroupId=3 
				ORDER BY replicaLabel DESC LIMIT 1)
			 * 
			 * */
			ResultSet res = null;
			long[] result = new long[Constants.replicaNumber];
			StringBuilder query = new StringBuilder();
			
			for(int i = 0; i < Constants.replicaNumber; i++ ){
				if(!query.toString().equals("")){
					query.append(" UNION ");
				}
				query.append("SELECT * FROM (SELECT idServer FROM servers"
					+ " WHERE replicaLabel >= "+ Long.toUnsignedString(serverLabel) +" AND replicaGroupId="+i
					+ " ORDER BY replicaLabel ASC LIMIT 1) ");
			}

			PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, "* "+query.toString());
			res = executeQuery(query.toString(),true);

			try {
				for(int i = 0; i < Constants.replicaNumber; i++ ){
					if( res.next() ){
						result[i] = res.getLong("idServer");
					}else{
						PadFsLogger.log(LogLevel.ERROR, "NO SERVER FOUND");
						break;
					}
				}
				res.close();
			} catch (SQLException e) {
				PadFsLogger.log(LogLevel.ERROR, e.getMessage());
				try {
					res.close();
				} catch (SQLException e1) {
					PadFsLogger.log(LogLevel.ERROR, e1.getMessage());
				}
				return null;
			}

			return result;
		}

        /**
         * Retrive the list of the server
         *
         * @return List<Server> with the list of the server
         * @return null	otherwise
         */
        public static List<Server> getActiveServerList(){

        	ResultSet serverList = null;
        	List<Server> s = new LinkedList<>();

        	String[] fields = {"*"};

        	serverList = select("servers", fields, " \"keepAlive\" = '1' ");

        	try {
	        	if(countRowRS(serverList) > 0){
	        		s = resultSetServerToList(serverList);
	        	}
	        	serverList.close();

        	} catch (SQLException e) {
        		PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, e.getMessage());
        		return null;
			}
        	return s;
        }

        
        /**
         * Retrive the list of the server
         *
         * @return List<Server> with the list of the server
         * @return null	otherwise
         */
        public static List<Server> getReadyServerList() {
        	return getReadyServerList(false);
        }
        
        /**
         * Retrive the list of the server
         *
         * @return List<Server> with the list of the server
         * @return null	otherwise
         */
        public static List<Server> getReadyServerList(boolean includeMaintenance) {
        	ResultSet serverList = null;
        	List<Server> s = new LinkedList<>();

        	String[] fields = {"*"};
        	String op = "=";
        	if(includeMaintenance){
        		op = ">=";
        	}
        	serverList = select("servers", fields, " \"keepAlive\" = '1' AND \"status\" "+op+" '"+String.valueOf(ServerStatus.READY.getNumVal())+"'");

        	try {
	        	if(countRowRS(serverList) > 0){
	        		s = resultSetServerToList(serverList);
	        	}
	        	serverList.close();

        	} catch (SQLException e) {
        		PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, e.getMessage());
        		return null;
			}
        	return s;
        }

        
        public static Server getServer(Long id) {
        	ResultSet r = null;
        	Server s = null;
        	String[] fields = new String[]{"idServer","ip","port","availableSpace","totalSpace","status","keepAlive","keepAliveTime","replicaGroupId","replicaLabel"};
        	r = select("servers", fields, "\"idServer\"="+Long.toUnsignedString(id));
        	try {
	        	if(r.next()){
	        		s = new Server(id,
									r.getString(fields[1]),
									r.getString(fields[2]),
									r.getString(fields[3]),
									r.getString(fields[4]),
									ServerStatus.convert(r.getInt(fields[5])),
									r.getString(fields[6]),
									r.getString(fields[7]),
									r.getInt(fields[8]),
									r.getLong(fields[9])
									);
	        	}
	        	else{
	        		PadFsLogger.log(LogLevel.DEBUG, "no server found");
	        	}
	        	r.close();

        	} catch (SQLException e) {
        		PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, e.getMessage());
        		return s;
			}
        	return s;
		}
        
        /**
         * Retrive the list of the server
         *
         * @return List<Server> with the list of the server
         * @return null otherwise
         */
        public static List<Server> getServerList(){
        	ResultSet serverList = null;
        	List<Server> s = new LinkedList<>();
        	String[] fields = {"*"};
        	serverList = select("servers", fields, null);
        	try {
	        	if(countRowRS(serverList) > 0){
	        		s = resultSetServerToList(serverList);
	        	}
	        	serverList.close();

        	} catch (SQLException e) {
        		PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, e.getMessage());
        		return null;
			}
        	return s;
        }

        /**
         * Retrieve the next available label and group id
         *
         * @return Long Array with 2 data 0=> the start label with largest space 1=> the group id
         */
        public static long[] findNextLabel(){
    		long[] ret = new long[2];

    		Integer tmpGroupId = null;
    		Long 	tmpLabel   = null;

    		Integer newGroupId = null;
    		Long    newLabel 	 = null;
    		long 	maxRange = 0;

    		int 	prevGroupId = -1;
    		long    prevLabel 	= -1;

    		ResultSet rs = select("servers",new String[]{"replicaLabel","replicaGroupId"},null," ORDER BY replicaGroupId, replicaLabel ASC");

			PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, "findNextLabel");
    		try {
				while(rs.next()){
					tmpGroupId = rs.getInt("replicaGroupId");
					tmpLabel   = Long.parseUnsignedLong(rs.getString("replicaLabel"));

					PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, "label: "+rs.getString("replicaLabel") + "   groupId:"+tmpGroupId  + "  tmpLabel:"+Long.toUnsignedString(tmpLabel));
					// se cambio gruppo inizializzo prevLabel all'estremo inferiore del range di competenza del server con label maggiore
					if(prevGroupId != tmpGroupId){
						prevLabel = 0;
					}

					//calcolo del range di competenza del server con label tmpLabel e confronto col maxRange
					if( Long.compareUnsigned((tmpLabel - prevLabel) , maxRange ) > 0){
						//calcolo nuova label
						maxRange = tmpLabel - prevLabel;
						newLabel   = prevLabel+ Long.divideUnsigned((tmpLabel - prevLabel),2);
						newGroupId = tmpGroupId;
						PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, "set NewLabelAndGroupId  label: "+newLabel + "   groupId:"+newGroupId);

					}
					prevLabel	= tmpLabel;
					prevGroupId = tmpGroupId;

				}
				rs.close();
			} catch (SQLException e) {
				PadFsLogger.log(LogLevel.ERROR, "Problem retrieving label "+e.getMessage());
				try {
					rs.close();
				} catch (SQLException e1) {
					PadFsLogger.log(LogLevel.ERROR, "Problem close result set "+e1.getMessage());
				}
				return null;
			}

    		if(newGroupId == null || newLabel == null){
    			PadFsLogger.log(LogLevel.ERROR, "Problem retrieving the new label and groupId");
				return null;
    		}

    		PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, "Label retrieved - GROUP ID: "+ newGroupId+" LABEL: "+newLabel);
    		ret[0] = newLabel; 	//MAX LABEL ID
    		ret[1] = newGroupId;  //MAX GROUP ID

    		return ret;
    	}


        public synchronized static HashMap<String,List<String>> dumpServers(String table){
        	return dumpTable("servers",true);
        }




        /**
         * Retrive the id of all servers
         *
         * @return long[] with the list of the serverIds
         * @return null otherwise
         */
        public static long[] getAllServerId(){

        	ResultSet serverList = null;

        	String[] fields = {"idServer"};
        	ArrayList<Long> tmpID = new ArrayList<>();

        	serverList = select("servers", fields, null);
        	try {
        		while(serverList.next()){
    				tmpID.add(serverList.getLong("idServer"));
    			}
        		serverList.close();

        		long[] res = new long[tmpID.size()];
        	    for (int i = 0; i < tmpID.size(); i++)
        	         res[i] = tmpID.get(i);

        	    return res;
			} catch (SQLException e) {
				PadFsLogger.log(LogLevel.FATAL, e.getMessage());
				try {
					serverList.close();
				} catch (SQLException e1) {
					PadFsLogger.log(LogLevel.FATAL, e1.getMessage());
				}
				return null;
			}

        }

        /**
         * Retrive the list of the server of the selected Id
         * @serversId the list of Ids
         *
         * @return List<Server> with the list of the server
         * @return null otherwise
         */
        public static List<Server> getServerList(long[] serversId){

        	if(serversId.length <= 0){
        		return null;
        	}        	
        	
        	ResultSet serverList = null;
        	StringBuilder where = null;
        	List<Server> servers = new ArrayList<>();

        	for(int i = 0; i < serversId.length; i++){
        		if(where == null){
        			where = new StringBuilder();
        			where.append(serversId[i]);
        		}
        		else{
        			where.append(",");
        			where.append(serversId[i]);
        		}
        	}

        	if(where == null){
        		PadFsLogger.log(LogLevel.ERROR, "array is empty: serversId ");
        		return null;
        	}

        	String[] fields = {"idServer","ip","status","port","availableSpace","totalSpace","replicaGroupId","replicaLabel"};

        	serverList = select("servers", fields, "idServer in ("+where.toString()+")");
        	Server server;
			String ip, port;
			long id,label;
			int groupId;
			String availableSpace=null, totalSpace=null;
			ServerStatus status = ServerStatus.UNKNOWN;

        	try {
        		if(countRowRS(serverList) > 0){
					while(serverList.next()) {
						id 		= serverList.getLong("idServer");
						ip 		= serverList.getString("ip");
						port 	= String.valueOf(serverList.getInt("port"));
						groupId = serverList.getInt("replicaGroupId");
						label 	= serverList.getLong("replicaLabel");
						status	= ServerStatus.convert(serverList.getInt("status"));
						availableSpace = serverList.getString("availableSpace");
						totalSpace = serverList.getString("totalSpace");

						server 	= new Server(id,ip,port,availableSpace,totalSpace,status,null,null,groupId,label);
						servers.add(server);

					}
        		}
        		serverList.close();
			} catch (SQLException e) {
				PadFsLogger.log(LogLevel.ERROR, "getServerList(long): "+e.getMessage());
				try {
					serverList.close();
				} catch (SQLException e1) {
					PadFsLogger.log(LogLevel.ERROR, "getServerList(long): "+e1.getMessage());
				}
			}
        	return servers;
        }

        /**
         * Update the server as ENABLE
         * @param id 	the id of the server
         *
         * @return true if update is done
         * @return false otherwise
         */
        public synchronized static boolean setUPServer( Long id ){
        	return setKeepAlive(id,1);
        }

        /**
         * Update the server as DISABLE
         * @param id 	the ip of the server
         *
         * @return true if update is done
         * @return false otherwise
         */
        public synchronized static boolean setDOWNServer( Long id ){
        	return setKeepAlive(id,0);
        }


        /**
         * Update the keep alive time of the server and the status
         * @param id	 the server id
         * @param status the status of the server 0 => "NOT ACTIVE" | 1 => ACTIVE
         *
         * @return true if update is done
         * @return false otherwise
         */
        private synchronized static boolean setKeepAlive( Long id, int status ){
        	if(Long.compare(id, 0L)<=0){
        		return false;
        	}
        	if( (status != 0 && status != 1) ){
        		return false;
        	}

       		return (update("servers", new String[]{"keepalive", "keepAliveTime"}, new String[]{String.valueOf(status), SystemEnvironment.getDateTime()}, " \"idServer\" = "+Long.toUnsignedString(id), true));
        }

        /**
         * Given the ip and the port of the server check if the server is inserted in the database
         * @param ip	the ip address of the server
         * @param port	the port number of the server
         *
         * @return true  if is present in the database
         * @return false otherwise
         */
        public static synchronized boolean checkServerExists(String ip, String port){
        	if(!Padfs.validateString(new String[]{ip,port})){
        		return false;
        	}

        	String[] fields = {"ip","port"};

        	ResultSet s = select("servers", fields, " \"ip\"='"+ip+"' AND \"port\"="+port);
        	try {
        		if(countRowRS(s) > 0){
        			return true;
        		}
        		s.close();
			} catch (Exception e) {
				PadFsLogger.log(LogLevel.ERROR, "checkServerExists "+e.getClass().getName() + ": " + e.getMessage());
				try {
					s.close();
				} catch (SQLException e1) {
					PadFsLogger.log(LogLevel.ERROR, e1.getMessage());
				}
			}
        	return false;
        }


        /**
         *
         * @return the next available serverId
         */
		public static Long getNextServerId() {
			ResultSet s = select("servers",new String[]{"idServer"},null," ORDER BY \"idServer\" DESC LIMIT 1");
			Long ret = null;
			try {
				if(s.next())
					ret = s.getLong("idServer")+1;

				s.close();
			} catch (SQLException e) {
				PadFsLogger.log(LogLevel.ERROR, "Failed to retrieve the next serverId: " + e.getMessage());
				try {
					s.close();
				} catch (SQLException e1) {
					PadFsLogger.log(LogLevel.ERROR, e1.getMessage());
				}
			}

			return ret;
		}

		/**
		 *
		 * @param id the id of the server to be checked
		 * @return true if the server exists
		 * @return false if does not exist a server with this id
		 */
		public static boolean checkServerIdExists(Long id) {
			if(id == null)
				return false;
			ResultSet s = select("servers", new String[]{"idServer"}, " \"idServer\"="+id);

			try {
				if(s.next())
					return true;
				s.close();
			} catch (SQLException e) {
				PadFsLogger.log(LogLevel.ERROR, "Fail looking for a server with serverId="+id+": " + e.getMessage());
				try {
					s.close();
				} catch (SQLException e1) {
					PadFsLogger.log(LogLevel.ERROR, e1.getMessage());
				}
			}
			return false;
		}

        /**
         * DEPRECATED
         *
         * Add the server list to the database
         * @param serverList List of server object
         *
         * @return true if the insertion of ALL the servers is done
         * @return false otherwise

		private static synchronized boolean addServerToDb(List<Server> serverList){
    		//LOAD SERVER LIST
    		Server 	node 	= null;

    		String 	ip 		= null;
			String 	port 	= null;
			long 	id 		= -1;
			int 	groupId = -1;
			long 	label	= -1;

    		for(int i=0; i< serverList.size(); i++){
    			node = serverList.get(i);
    			if(node != null){
    				ip 	 	= node.getIp();
    				port 	= node.getPort();
    				groupId = node.getGroupId();
    				id		= node.getId();
    				label	= node.getLabel();

    				if(!checkServerExists(ip, port)){
    					if(!addServerToDB_idGroupLabel(ip, port, id, groupId, label)){
    		        		system.PadFsLogger.log(LogLevel.ERROR, "INSERT ERROR - IP: "+ip+"- PORT: "+port+"- IDSERVER: "+id+" - GROUPID: "+groupId+" - LABEL: "+Long.toUnsignedString(label));
    		        		rollback(); //if only one insert fail all the servers inserted are removed
    		        		return false;
    		        	}
    				}else{
		        		system.PadFsLogger.log(LogLevel.ERROR, "INSERT ERROR SERVER EXISTS - IP: "+ip+"- PORT: "+port+"- IDSERVER: "+id+" - GROUPID: "+groupId+" - LABEL: "+Long.toUnsignedString(label));
    				}
    			}
    		}

    		commit(); //COMMIT ONLY IF ALL THE SERVER IS INSERTED CORRECTLY
    		return true;
        }
        */




        /**
         * Add the server list to the database
         * @param serverList List of server object
         *
         * @return true if the insertion of ALL the servers is done
         * @return false otherwise
         */
        private static synchronized boolean addServerToDbNoCheck(List<Server> serverList){
    		//LOAD SERVER LIST
    		Server 	node 	= null;

    		String 	ip 		= null;
			String 	port 	= null;
			long 	id 		= -1;
			int 	groupId = -1;
			long 	label	= -1;
			String    avilableSpace=null, totalSpace = null;
			ServerStatus serverStatus = Constants.ServerStatus.UNKNOWN;

			StringBuilder sql = new StringBuilder();
			sql.append("INSERT INTO servers (idServer,ip,port,availableSpace,totalSpace,replicaGroupId,replicaLabel,status,keepAlive,keepAliveTime) VALUES ");

    		for(int i=0; i< serverList.size(); i++){
    			node = serverList.get(i);
    			if(node != null){
    				ip 	 		  = node.getIp();
    				port 		  = node.getPort();
					totalSpace	  = node.getTotalSpace();
					avilableSpace = node.getAvailableSpace(); //bytes

    				groupId 	  = node.getGroupId();
    				id		   	  = node.getId();
    				label		  = node.getLabel();
    				serverStatus  = node.getStatus();

    		    	sql.append(" ("+
    		    			Long.toUnsignedString(id)+",'"+
    		    			ip+"','"+
    		    			port+"','"+
							avilableSpace+"','"+
							totalSpace+"','"+
    		    			String.valueOf(groupId)+"',"+
    		    			Long.toUnsignedString(label)+", "+
    		    			serverStatus.getNumVal()+","+
    		    			"1"+",'"+
    		    			SystemEnvironment.getDateTime()+"') ");

    		    	if(i<(serverList.size()-1))
    		    		sql.append(",");
    			}
    		}
    		PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, "*** "+sql.toString());

    		if(executeQueryModifyOLD(sql.toString(),true))
    			return true;

    		return false;
        }


        /**
         * Method that allow to populate the servers table with a List<Server> passed as parameter
         * @param serverList	List of servers
         * @return true 	if operation terminate correctly
         * @return false 	otherwise
         */
        public static synchronized boolean truncateRepopulateServer(List<Server> serverList){
        	if(truncate("servers")){
        		if(addServerToDbNoCheck(serverList)){
        			//update Variables serverList
        			Variables.setServerList(serverList);
        			return true;
        		}
        	}
			return false;
        }

        /*
        public static boolean fixLocalhostValues(String creatorIp, String port) {
        	if(!Padfs.validateString(new String[]{creatorIp,port})){
        		return false;
        	}

        	if ( update("servers", new String[] {"ip","port"}, new String[] {creatorIp,port}, " ip = 'localhost' OR ip = '127.0.0.1' OR ip = '127.0.1.1'", true) != null){
        		PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, "fixLocalhostValues completed");
        		return true;
        	}

        	PadFsLogger.log(LogLevel.ERROR, "fixLocalhostValues FAILED");
        	return false;

		}*/


        /**
         * Add server to database and replica data. it do not commit.
         * @param ip		ip address of the server
         * @param port		port of the server
         * @param idServer	id of the server
         * @param groupId	the replica group identifier
         * @param label     the replica label
         *
         * @return true if insertion is done
         * @return false otherwise
         */
        public static boolean addServerToDB_idGroupLabel(String ip,String port,long idServer,int groupId, long label){

    		if(!Padfs.validateString(new String[]{ip,port})){
        		return false;
        	}

    		String[] fields = {"idServer","ip","port","replicaGroupId","replicaLabel","status","keepAlive","keepAliveTime"};
        	String[] data = {
        			Long.toUnsignedString(idServer),
        			ip,
        			port,
        			String.valueOf(groupId),
        			Long.toUnsignedString(label),
        			Integer.toString(ServerStatus.GLOBAL_SYNCHING.getNumVal()),
        			"1",
        			SystemEnvironment.getDateTime()
        	};
        	

    		
        	return insertNoId("servers", fields, data, false) ;
        }

        /**
         * Add the server to database
         * @param ip		the ip address
         * @param port		the server port
         * @param idServer	the id of the server
         *
         * @return true if insertion works
         * @return false otherwise
         */
        public static boolean addServerToDB_id(String ip,String port,long idServer){
        	if(!Padfs.validateString(new String[]{
        			ip,
        			port,
        			String.valueOf(idServer)})){
        		return false;
        	}

        	String[] fields = {"ip","port","idServer","keepAlive","keepAliveTime","status"};
        	String[] data = {ip,port,Long.toUnsignedString(idServer),"1", SystemEnvironment.getDateTime(),Integer.toString(ServerStatus.GLOBAL_SYNCHING.getNumVal())};

        	return ( insert("servers", fields, data, true) != null )?true:false;
        }

        /**
         * Update the server id in the database
         * @param ip		the ip of the server
         * @param port		the port of the server
         * @param idServer	the new id of the server
         *
         * @return true if update is done
         * @return false otherwise
         */
        public boolean updateServerId(String ip,String port, long idServer){
        	if(!Padfs.validateString(new String[]{ip,port})){
        		return false;
        	}

        	return update(
        			"servers",
        			new String[] { "idServer" }, new String[] { Long.toUnsignedString(idServer) },
        			" \"ip\" = "+ip+" AND \"port\" = "+port,
        			true);
        }


	/**
	 * Update the fields: keepalive, keepaliveTime, availableSpace, totalspace in database
	 * and UPDATE in variables
	 * @param idServer
	 * @param availableSpace
	 * @param totalSpace
     * @return true if update is done
	 * @return false otherwise
     */
	public static boolean updateServerExtraInfos(long idServer, String availableSpace, String totalSpace, ServerStatus serverStatus){
		if(!Padfs.validateString(new String[]{availableSpace,totalSpace})){
			return false;
		}

		if(idServer<=0){
			return false;
		}

		String s = SystemEnvironment.getDateTime();
	/*	int actualStatus = 0;
		
		ResultSet r = select("servers",new String[]{"status"}," \"idServer\" = "+idServer);
		try {
			if(!r.next()){
				PadFsLogger.log(LogLevel.ERROR, "server "+idServer + " is not in the database");
				return false;
			}
			actualStatus = r.getInt("status");
			
		} catch (SQLException e) {
			PadFsLogger.log(LogLevel.ERROR, "SQL exception: "+e.getMessage());
			return false;
		}
		
		
	/	if(actualStatus > serverStatus.getNumVal()){*/
		String[] fields = null;
		String[] values = null;
			fields = new String[] { "keepAlive", "keepAliveTime", "availableSpace" , "totalSpace", "status" };
			values = new String[] { "1", s, availableSpace, totalSpace ,String.valueOf(serverStatus.getNumVal()) };
	/*	}
		else{
			fields = new String[] { "keepAlive", "keepAliveTime", "availableSpace" , "totalSpace"};
			values = new String[] { "1", s, availableSpace, totalSpace };
		}
	*/	
		return update(
				"servers",
				fields,
				values,
				" \"idServer\" = "+idServer,
				true);
	}
	
	
	public static boolean updateServerStatus(long idServer, ServerStatus serverStatus) {
		if(idServer<=0){ 
			return false;
		}

		
		String s = SystemEnvironment.getDateTime();

		return update(
				"servers",
				new String[] { "keepAlive", "keepAliveTime","status" },
				new String[] { "1", s, String.valueOf(serverStatus.getNumVal())},
				" \"idServer\" = "+idServer,
				true) ;
		
	}
	
        /**
         * Update the server label and group id in the database
         * @param idServer	the id in the database of the server
         * @param label		the new label to assign
         * @param groupId	the new groupid to assign
         *
         * @return true if the update is done
         * @return false otherwise
         */
        public static boolean updateServerLabelGroupId(long idServer, long label, int groupId){
        	if(!Padfs.validateString(new String[]{
        			Long.toUnsignedString(label),
        			String.valueOf(groupId)
        			})){
        		return false;
        	}

        	if(idServer <= 0L){
        		return false;
        	}

        	return update(
        			"servers",
        			new String[] { "replicaLabel", "replicaGroupId" },
        			new String[] { Long.toUnsignedString(label), String.valueOf(groupId) },
        			" \"idServer\" = "+Long.toUnsignedString(idServer),
        			true);
        }



        /**
         * Retrieve the server less loaded from Database ( ratio = <aviable space>/<total space> )
         * @param maxServerNumber the number of max servers to retrieve
         *
         * @return List<Server> a list of at most replicaNumber servers loads less
         */
        public static List<Server> getServersLessLoaded(int maxServerNumber){

        	List<Server> ret = new ArrayList<Server>(maxServerNumber);
        	ResultSet rs = null;
        	ResultSet rs2 = null;
        	

			PadFsLogger.log(LogLevel.TRACE, "entering getServerLessLoaded"); 

        	String sql = "SELECT *, (availableSpace * 1.00)/totalSpace as ratio FROM servers "+
        				 "WHERE totalSpace <> 0 AND keepAlive <> 0 AND status =  "+ServerStatus.READY.getNumVal()+" "+
        				 "ORDER BY ratio desc LIMIT "+maxServerNumber;
        	
        	PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, "* "+sql);
        	if( (rs = executeQuery(sql, true)) == null )
        		return null;

        	int numServers = countRowRS(rs);
        	PadFsLogger.log(LogLevel.TRACE, "numServers retrieved = "+numServers+". maximum: " + maxServerNumber); 

        	if( numServers < maxServerNumber){
	        	sql = "SELECT *, '0' as ratio FROM servers WHERE totalSpace = 0 AND keepAlive <> 0  LIMIT "+(maxServerNumber-numServers);
	        	PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, "* "+sql);
	        	if( (rs2 = executeQuery(sql, true)) == null ){
	        		PadFsLogger.log(LogLevel.ERROR, "ResultSet is null");
	        	}
	        		
        	}

        	Server tmp      = null;
        	Long id         = null;
        	String ip       = null;
        	String port     = null;
        	Integer groupId = null;
        	Long label 	    = null;
        	ServerStatus status = Constants.ServerStatus.UNKNOWN;


        	for(int i=0; i<maxServerNumber; i++){
        		try {
        			//if active servers < replicaNumber return the retrieved servers
        			PadFsLogger.log(LogLevel.TRACE, "adding 1 server"); 
        			if(!rs.next()){
        				if(rs2 == null){
        					break;
        				}
        				else{
        					rs.close();
        					rs = rs2;
        					if(!rs.next()){
        						break;
        					}
        				}
        			}
        			


	        		id 		= rs.getLong("idServer");
	        		ip 		= rs.getString("ip");
	        		port 	= rs.getString("port");
					groupId = rs.getInt("replicaGroupId");
	        		label 	= Long.parseUnsignedLong(rs.getString("replicaLabel"));
	        		status  = ServerStatus.convert(rs.getInt("status"));

	        		tmp = new Server(id,ip,port,null,null, status,null,null,groupId,label);
	        		ret.add(tmp);
	        		PadFsLogger.log(LogLevel.TRACE, "1 server added"); 

        		} catch (SQLException e) {
        			PadFsLogger.log(LogLevel.ERROR, "Retrieve data from db : " + e.getMessage());
    				return null;
				}
        	}

        	try {
				rs.close();
			} catch (SQLException e) {
				PadFsLogger.log(LogLevel.ERROR, e.getMessage());
			}
        	return ret;
        }

/************************************************************************************
 * USER METHOD FOR DB
 ************************************************************************************/
        /**
         * Return the id of the user if exists
         * @param username
         * @param password
         * @return String ID of the user
         * @return -1 if not found
         */
        public static Integer getIdUser(String username, String password){

        	if(!Padfs.validateString(new String[]{username, password})){
        		PadFsLogger.log(LogLevel.ERROR, "PARAMETER ERROR [getIdUser] - USERNAME: "+username+ " - PASSWORD: " + password);
        		return null;
        	}

        	ResultSet r = select("users", new String[]{"idUser"}, "\"user\"='"+username+"' AND \"password\"='"+password+"'");
        	try {
				while( r.next() ){
					return r.getInt("idUser");
				}
				r.close();
			} catch (SQLException e) {
				PadFsLogger.log(LogLevel.ERROR, "Read idUser "+e.getClass().getName() + ": " + e.getMessage());
				try {
					r.close();
				} catch (SQLException e1) {
					PadFsLogger.log(LogLevel.ERROR, e1.getMessage());
				}
				return null;
			}
			return null;
        }
        

		public static String getUsername(int idOwner) {
			
        	ResultSet r = select("users", new String[]{"user"}, "\"idUser\"="+idOwner);
        	try {
				while( r.next() ){
					return r.getString("user");
				}
				r.close();
			} catch (SQLException e) {
				PadFsLogger.log(LogLevel.ERROR, "Read user "+e.getClass().getName() + ": " + e.getMessage());
				try {
					r.close();
				} catch (SQLException e1) {
					PadFsLogger.log(LogLevel.ERROR, e1.getMessage());
				}
				return null;
			}
			return null;
		}

        public static int getIdUser(String username) {
        	if(!Padfs.validateString(new String[]{username})){
        		PadFsLogger.log(LogLevel.WARNING, "PARAMETER ERROR [getIdUser] - USERNAME: "+username);
        		return -1;
        	}
        	ResultSet r = select("users", new String[]{"idUser"}, "\"user\"='"+username+"'");
        	try {
				while( r.next() ){
					return r.getInt("idUser");
				}
				r.close();
			} catch (SQLException e) {
				PadFsLogger.log(LogLevel.ERROR, "Read idUser "+e.getClass().getName() + ": " + e.getMessage());
				try {
					r.close();
				} catch (SQLException e1) {
					PadFsLogger.log(LogLevel.ERROR, e1.getMessage());
				}
				return -1;
			}
			return -1;
		}

        
        /**
         * delete all filesManaged with label not greater than serverLabel
         * @param serverLabel
         * @return
         */
        public static boolean cleanFilesManaged(long serverLabel) {

        	List<Long> idList = getMetaInfoId(0, serverLabel);
			StringBuilder idListString = new StringBuilder();
			
			if(idList == null){
				PadFsLogger.log(LogLevel.ERROR, "idList is null");
				return false;
			}
			for(Iterator<Long> it = idList.iterator(); it.hasNext();){
				idListString.append(Long.toUnsignedString(it.next()));
				if(it.hasNext())
					idListString.append(",");
			}
			
			if(!delete("host", "\"idFile\" IN ("+idListString+")", false)){
				PadFsLogger.log(LogLevel.ERROR, "impossible to clean host Table");
				return false;
			}
			if(!delete("grant", "\"idFile\" IN ("+idListString+")", false)){
				PadFsLogger.log(LogLevel.ERROR, "impossible to clean grant Table");
				return false;
			}
			if(!delete("filesManaged", "\"label\" <= " + Long.toUnsignedString(serverLabel), true)){
				PadFsLogger.log(LogLevel.ERROR, "impossible to clean filesManaged Table");
				return false;
			}
			if(!delete("directoryListing", "\"label\" <= " + Long.toUnsignedString(serverLabel), true)){
				PadFsLogger.log(LogLevel.ERROR, "impossible to clean directoryListing Table");
				return false;
			}
					
        	return true;
		}

       

		/**
         * Return the info of the user if exists
         * @param username
         * @return String|null with ID of the user if found
         */
        public static String[] getInfoUser(String username){
        	String []ret = new String[2];

        	if(!Padfs.validateString(new String[]{username})){
        		PadFsLogger.log(LogLevel.ERROR, "PARAMETER ERROR [getIdUser] - USERNAME: "+username);
        		return null;
        	}

        	ResultSet r = select("users", new String[]{"idUser","password"}, "\"user\"='"+username+"'");

        	try {
        		if(countRowRS(r) <= 0){
            		return null;
            	}
				r.next();
				ret[0] = r.getString("idUser");
				ret[1] = r.getString("password");
				r.close();
				return ret;
			} catch (SQLException e) {
				PadFsLogger.log(LogLevel.ERROR, "Read idUser "+e.getClass().getName() + ": " + e.getMessage());
				try {
					r.close();
				} catch (SQLException e1) {
					PadFsLogger.log(LogLevel.ERROR, e1.getMessage());
				}
				return null;
			}
        }

	/**
	 * Retrieve a list of users
	 * @return List<User>
	 */
	public static List<User> getUserList(){

		List<User> s = new ArrayList<User>();
		ResultSet ret = select("users", new String[]{"idUser","user","password"},null,"ORDER BY idUser ASC");
		try {
			while (ret.next()){
					s.add(new User(Long.toUnsignedString(ret.getLong("idUser")),ret.getString("user"),ret.getString("password")));
			}
		} catch (SQLException e) {
			PadFsLogger.log(LogLevel.ERROR, e.getMessage());
			return null;
		}
		return s;

	}
        /**
         * Add user in the DB if not exists
		 * @param id
         * @param username
         * @param password
         * @return true|false of added user
         */
        public static boolean addUser(Long id, String username, String password){

        	if(!Padfs.validateString(new String[]{username, password}) || id == null){
        		PadFsLogger.log(LogLevel.ERROR, "PARAMETER ERROR [addUser] - ID: "+id+" - USERNAME: "+username+ " - PASSWORD: " + password);
        		return false;
        	}

        	String[] fields = { "idUser","user", "password" };
			String[] data 	= { Long.toUnsignedString(id), username, password };

			Long insert = insert("users",fields,data,true);

			
			//il caso NULL si verifica quando inserisco cancello e reinserisco il solito ID
			if( insert != null && insert >= 0L ){
				PadFsLogger.log(LogLevel.DEBUG,"INSERT ADD USER: "+insert,"white","yellow",true);
				return true;
			}
			else{
				PadFsLogger.log(LogLevel.ERROR,"INSERT ADD USER failed ","white","yellow",true);
				return false;
			}

        }

		/**
		 * Add user in the DB if not exists --> caso inserimento "admin" all'inizio
		 * @param username
		 * @param password
		 * @return true|false of added user
		 */
		public static boolean addUser(String username, String password){


			if(!Padfs.validateString(new String[]{username, password}) ){
				PadFsLogger.log(LogLevel.ERROR, "PARAMETER ERROR [addUser] - USERNAME: "+username+ " - PASSWORD: " + password);
				return false;
			}

			String[] fields = { "user", "password" };
			String[] data 	= { username, password };

			if(insert("users",fields,data,true)>0)
				return true;
			else
				return false;

		}


		/**
		 *
		 * @return the next available userId
		 */
		public static Long getNextUserId() {
				ResultSet s = select("users",new String[]{"idUser"},null,"ORDER BY \"idUser\" DESC LIMIT 1");
				Long ret = null;
				try {
					if(s.next())
						ret = s.getLong("idUser")+1;

					s.close();
				} catch (SQLException e) {
					PadFsLogger.log(LogLevel.ERROR, "Failed to retrieve the next user id: " + e.getMessage());
					try {
						s.close();
					} catch (SQLException e1) {
						PadFsLogger.log(LogLevel.ERROR, e1.getMessage());
					}
				}

				return ret;
			}



	/**
	 *
	 * @param id the id of the user to be checked
	 * @return true if the server exists
	 * @return false if does not exist a user with this id
	 */
	public static boolean checkUserIdExists(Long id) {
		if(id == null)
			return false;
		ResultSet s = select("users", new String[]{"idUser"}, " \"idUser\"="+id);

		try {
			if(s.next())
				return true;
			s.close();
		} catch (SQLException e) {
			PadFsLogger.log(LogLevel.ERROR, "Fail looking for a user with user id ="+id+": " + e.getMessage());
			try {
				s.close();
			} catch (SQLException e1) {
				PadFsLogger.log(LogLevel.ERROR, e1.getMessage());
			}
		}
		return false;
	}


		/**
		 * Remove user in the DB if exists
		 * @param idUser
		 * @return true if remove user , false otherwise
		 */
		public static boolean delUser(int idUser){
			if(idUser <= 0){
				PadFsLogger.log(LogLevel.ERROR, "PARAMETER ERROR [delUser] - idUser: "+idUser);
				return false;
			}
			return delete("users"," \"idUser\" = "+idUser, true);
		}


		public static boolean truncateRepopulateUsers(List<User> userList){
        	if(truncate("users")){
        		if(addUsersToDbNoCheck(userList))
        			return true;
        	}
			return false;
        }

        /**
         * Add the server list to the database
         * @param userList List of server object
         *
         * @return true if the insertion of ALL the servers is done
         * @return false otherwise
         */
        private static synchronized boolean addUsersToDbNoCheck(List<User> userList){
    		//LOAD SERVER LIST
    		User 	node 	= null;

    		String 	idUser 		= null;
			String 	username 	= null;
			String 	password 	= null;

			StringBuilder sql = new StringBuilder();
			sql.append("INSERT INTO users (idUser,user,password) VALUES ");

    		for(int i=0; i< userList.size(); i++){
    			node = userList.get(i);
    			if(node != null){
    				idUser 	 	= node.getId();
    				username 	= node.getUser();
    				password 	= node.getPassword();

    		    	sql.append(" ("+idUser+",'"+username+"','"+password+"') ");

    		    	if(i<(userList.size()-1))
    		    		sql.append(",");
    			}
    		}
    		PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, "*** "+sql.toString());
    		
    		if(executeQueryModifyOLD(sql.toString(),true)){
    			return true;
    		}
    		return false;
        }

/************************************************************************************
 * FOLDER MANAGER
 ************************************************************************************/
    /**
	 * Given a path of the directory and the username the method return the
	 * if the path exists and is a directory
	 * @param path
	 * @param username
     * @return true if exists and is a directory false otherwise
     */
	public static synchronized boolean checkDirExists(String path,String username, String uniqueId){
		if(!Padfs.validateString(new String[]{path, username}) ){
			return false;
		}

		//retrieve the user id
		int idOwner = getIdUser(username);
		if(idOwner == -1){
			return false;
		}
		return checkDirExists(path,idOwner,uniqueId) != null;
	}
	/**
	 * Given a path of the directory and the username the method return the
	 * if the path exists and is a directory
	 * @param path
	 * @param username
     * @return the uniqeuId of the directory if exists and is a directory, null otherwise
     */
	public static synchronized String checkDirExists(String path,Integer idOwner, String uniqueId){
		if(!Padfs.validateString(new String[]{path}) ){
			return null;
		}
	
		if(idOwner == null || idOwner == -1){
			return null;
		}

		String ret = null;

		/**
		 * SELECT idFile
		 * FROM filesManaged
		 * WHERE path = '<path>' AND
		 * 		 idOwner = '<idOwner>' AND
		 * 		 isDirectory = true
		 */
		
		String where = "";
		if(uniqueId != null && !uniqueId.equals("") && !uniqueId.equals("null")){
			where = " AND \"checksum\" = '" + uniqueId + "' ";
		}
		
		ResultSet r = select("filesManaged", new String[]{"idFile","checksum"},
				" \"path\"='"+path+"' AND \"idOwner\"="+idOwner + " AND isDirectory = TRUE" + where);
		try {
			if(r.next()){
				PadFsLogger.log(LogLevel.DEBUG, "directory exists: " + path + " - " + idOwner);
				ret = r.getString("checksum");
			}
			r.close();
			return ret;
		} catch (SQLException e) {
			PadFsLogger.log(LogLevel.ERROR, e.getMessage());
			try {
				r.close();
			} catch (SQLException e1) {
				PadFsLogger.log(LogLevel.ERROR, e1.getMessage());
			}
			return null;
		}
	}


	public static Long addDir (String path,String username, Long label, String uniqueId, String uniqueIdParent){
		if(!Padfs.validateString(new String[]{path, username,uniqueId}) ){
			return null;
		}

		//retrieve the user id
		Integer idOwner = getIdUser(username);
		if(idOwner == null || idOwner <= 0){
			return null;
		}

		/**
		 * INSERT INTO filesManaged (idFile,path,dateTime,size,idOwner, label,updatesNumber,checksum,isDirectory)
		 * VALUES ( NULL, <path>, <datetime>, 0, <idOwner>, <label>, 1, <checksum>, 1)
		 */
		String dateTime = SystemEnvironment.getDateTime();
		String[] fields = new String[]{"idFile","path","dateTime","size","idOwner","label","updatesNumber","checksum","checksumParent","isDirectory"};
		String[] data   = new String[]{null,path,dateTime,"0",String.valueOf(idOwner),Long.toUnsignedString(label),"1",uniqueId,uniqueIdParent,"true"};

		Long id = insert("filesManaged",fields,data, true); 
		if( id == null  ){
			PadFsLogger.log(LogLevel.ERROR, "Failed populate filesManaged");
			return null;
		}

		return id;
	}

	public static boolean delDir (String path,String username, Long label){
		if(!Padfs.validateString(new String[]{path, username}) ){
			return false;
		}

		//retrieve the user id
		int idOwner = getIdUser(username);
		if(idOwner == -1){
			return false;
		}

		int idDir = getIdFile(path,idOwner);

		if( (delete("filesManaged"," idFile =  "+idDir, true)) == false ){
			PadFsLogger.log(LogLevel.ERROR, "Failed delete directory "+path+" filesManaged");
			return false;
		}

		return true;
	}



/************************************************************************************
 * CONSENSUS
 ************************************************************************************/
	/**
	 * Retrieve the server id that partecipate to the selected group
	 * @param groupId the group id
	 * @return	List of server , null otherwise
     */
	public static List<Server> getServerListConsGroup(Long groupId){
		if(groupId == null) return null;
		List <Server> ret = new LinkedList<>();

		ResultSet r = select("participate", new String[]{"idServer"}, " idConsensusGroup = "+groupId);
		try {
			Server s;
			while(r.next()){
				s = getServer(r.getLong("idServer"));
				if(s != null)
					ret.add(s);
			}
			r.close();
		} catch (SQLException e) {
			PadFsLogger.log(LogLevel.ERROR,"getServerConsGroup error creating the list: " + e.getMessage());
			try {
				r.close();
			} catch (SQLException e1) {
				PadFsLogger.log(LogLevel.ERROR,e1.getMessage());
			}
			return null;
		}

		return ret;
	}



        /**
         * The method retrieve the id of the consensus group, if exists, in witch the
         * serverList partecipate
         *
         * @param serverIdList comma separated id list
         * @return null|integer the id of the group
         */
        public static Long getConsGroupId( long[] serverIdList  ){
        	String serverList = null;

        	if(serverIdList == null){
        		PadFsLogger.log(LogLevel.ERROR, "the idList is null");
        		return null;
        	}

        	StringBuilder builder = new StringBuilder();
    		for(int i = 0; i < serverIdList.length; i++){
    			if(i!=0)
    				builder.append(",");
    			builder.append(String.valueOf(serverIdList[i]));
    		}

    		serverList = builder.toString();

        	String sql;
        	ResultSet rs = null;

        	if(!Padfs.validateString(new String[]{serverList})){
        		return null;
        	}

        	Long l ;
        	sql = "SELECT \"idConsensusGroup\", count(\"idConsensusGroup\") as \"num\""
        			+ " FROM \"participate\" "
        			+ " WHERE \"idServer\" IN ( "+serverList+" )"
        			+ " GROUP BY \"idConsensusGroup\" "
        			+ " HAVING \"num\" = "+SystemEnvironment.Constants.replicaNumber
        			+ " LIMIT 1";
        	PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, "* "+sql);

        	try {
        		rs = createStatement().executeQuery(sql);
        		if ( countRowRS(rs) > 0 ){
        			rs.next();
        			l = rs.getLong("idConsensusGroup");
        			rs.close();
        			return l;
        		}else{
        			PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, "consGroup with server ids: "+serverList+" not found ");
        			rs.close();
                    return null;
        		}

        	}catch (Exception e) {
        		PadFsLogger.log(LogLevel.ERROR, e.getMessage());
        		try {
					rs.close();
				} catch (Exception e1) {
					PadFsLogger.log(LogLevel.ERROR, e1.getMessage());
				}
                return null;
            }
        }

        /**
         * The method retrieve the id of the consensus group, if exists, in witch the
         * serverList partecipate
         *
         * @return null|integer the id of the group
         */
        public static Long getGlobalConsGroupId( ){
        	return Constants.globalConsensusGroupId;
        }

        /**
         * Add a new consGroup
         * @param serverIdList
         * @return
         */
		public synchronized static Long addConsGroup(long[] serverIdList) {
			if(!Padfs.validateString(new String[]{serverIdList.toString()})){
        		return null;
        	}

			//CREATE GROUP
			Long groupId = null;
			groupId = insert("consensusGroups",null,null, true); 

			PadFsLogger.log(LogLevel.DEBUG, "groupId generated: "+groupId); 
			
			if(groupId == null){
				PadFsLogger.log(LogLevel.ERROR, "Failed adding a new consensusGroup");
			}


			//POPULATE GROUP
			String[] fields 	= null;
			String[] data 		= null;
			for(int i=0; i<serverIdList.length; i++){
				fields = new String[]{"idServer", "idConsensusGroup"};
				data   = new String[]{String.valueOf(serverIdList[i]),groupId.toString()};
				if( !(insertNoId("participate",fields,data, true)) ){
					PadFsLogger.log(LogLevel.ERROR, "Failed populate consensusGroup");
					return null;
				}
			}
			return (groupId);
		}

		/**
		 * Set the cons variables into database
		 * @param consVar		HashMap variable name, variable value
		 * @param consGroupId   the id of consGroup
		 * @return true|false
		 */
		public static boolean setConsVars(Long consGroupId , HashMap<String,String> consVar){
        	return update(
        			"consensusGroups",
        			new String[] { "hpn", "hapv", "hapn", "hapvType", "idConsensusRun" },
        			new String[] { consVar.get("hpn"),  consVar.get("hapv"), consVar.get("hapn"), consVar.get("hapvType"), consVar.get("idConsensusRun") },
        			" \"idConsensusGroup\" = "+consGroupId,
        			true) ;
		}

		/**
		 * Retrieve the cons value for consGroupId
		 * @param consGroupId
		 * @return null
		 * @return HashMap<String,String> var name , var value
		 */
		public static HashMap<String,String> getConsVar(Long consGroupId){
			HashMap<String,String> ret = new HashMap<>(4);

			if(consGroupId == null || consGroupId < 0){
				PadFsLogger.log(LogLevel.ERROR, "consGroupId not valid");
				return null;
			}

			String sql;
        	ResultSet rs = null;

        	sql = "SELECT \"hpn\", \"hapv\", \"hapn\", \"hapvType\", \"idConsensusRun\" "
        			+ " FROM PUBLIC.\"consensusGroups\" "
        			+ " WHERE \"idConsensusGroup\" = "+consGroupId
        			+ " LIMIT 1";
        	PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, "* "+sql);

        	try {
        		rs = createStatement().executeQuery(sql);
        		if( countRowRS(rs) > 0 ){
        			rs.next();

        			Clob hpn 		= rs.getClob("hpn");
        			Clob hapn 		= rs.getClob("hapn");
        			Clob hapv 		= rs.getClob("hapv");
        			Clob hapvType 	= rs.getClob("hapvType");

        			String S_hpn  		= (hpn!=null	 )?hpn.getSubString(1L, (int)hpn.length())			:null;
        			String S_hapn 		= (hapn!=null	 )?hapn.getSubString(1L, (int)hapn.length())		:null;
        			String S_hapv 		= (hapv!=null	 )?hapv.getSubString(1L, (int)hapv.length())		:null;
        			String S_hapvType 	= (hapvType!=null)?hapvType.getSubString(1L, (int)hapvType.length()):null;

        			ret.put( "hpn"			, S_hpn			);
        			ret.put( "hapn"			, S_hapn		);
        			ret.put( "hapv"			, S_hapv		);
        			ret.put( "hapvType"		, S_hapvType	);
        			ret.put( "idConsensusRun", String.valueOf(rs.getInt("idConsensusRun")) 			);

        			PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, "ConsVar "+ret.toString());

        			rs.close();
            		return ret;
        		}else{
        			PadFsLogger.log(LogLevel.ERROR, "ConsVar NULL for consGroup "+consGroupId);
        			rs.close();
        			return null;
        		}
        	}catch (Exception e) {
        		PadFsLogger.log(LogLevel.ERROR, e.getClass().getName() + ": " + e.getMessage());
        		try {
        			if(rs != null)
        				rs.close();
				} catch (SQLException e1) {
					PadFsLogger.log(LogLevel.ERROR, e1.getMessage());
				}
                return null;
            }
		}



/************************************************************************************
 * DB SUPPORT FUNCTION
 ************************************************************************************/

	/**
	 *
	 * @param serverList
	 * @param userList
	 * @param creatorIpList
	 * @param creatorPort
	 * @param creatorId
     * @return
     */
	public synchronized static boolean globalConsensusSync(List<Server> serverList,
																List<User>  userList,
																List<String> creatorIpList,
																String creatorPort,
																String creatorId){
			PadFsLogger.log(LogLevel.INFO, "globalConsensusSynch Started");

			/* update users */
			if( ! truncateRepopulateUsers(userList) ){
				PadFsLogger.log(LogLevel.FATAL, "Synch servers failed updating userList");
				return false;
			}

			PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, "globalConsensusSynch Users Completata. Inizio servers");

			/* update servers */

			serverList= RestServer.fixReceivedServerList(serverList, creatorIpList, creatorPort, creatorId);
			if(serverList != null){ // otherwise preserve old data
				Variables.setServerList(serverList);
				SystemEnvironment.updateVariables(serverList);
			}
				
			if( ! truncateRepopulateServer(serverList) ){
				PadFsLogger.log(LogLevel.FATAL, "Synch servers failed updating serverList");
			}
			/*
			sql = new StringBuilder();
			sql.append("INSERT INTO servers ('idServer','ip','port','availableSpace','totalSpace','keepAlive','keepAliveTime','replicaGroupId','replicaLabel') VALUES ");
			sql.append(jsonData.substring(start, end).replaceAll("\\{", "(").replaceAll("\\}", ")"));
			PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, "SYNCH SQL 2: "+sql.toString());
			if ( ! executeQueryModify(sql.toString(), true) ){
				PadFsLogger.log(LogLevel.FATAL, "Synch servers failed");
				return false;
			}
			*/

			PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, "globalConsensusSynch Completata");
			return true;
		}


		/**
		 *
		 * @return List<User> for the globalConsensus synchronization. To be used in SqlManager.globalConsensusSynch(String)
		 */
		public synchronized static List<User> getGlobalConsensusSyncDataUserList(){
			List<User> userList = new LinkedList<User>();
			User user;
			String[] fields = new String[]{"idUser","user","password"};

			ResultSet r = select("users", fields, null);
			try {
				while(r.next()){
					user = new User(r.getString(fields[0]),
									r.getString(fields[1]),
									r.getString(fields[2])
									);
					userList.add(user);
				}
				r.close();
			} catch (SQLException e) {
				PadFsLogger.log(LogLevel.ERROR,"SynchDataUserList error creating the list: " + e.getMessage());
				try {
					r.close();
				} catch (SQLException e1) {
					PadFsLogger.log(LogLevel.ERROR,e1.getMessage());
				}
			}

			return userList;

		}

		public synchronized static List<Server> getGlobalConsensusSyncDataServerList(){
			List<Server> userList = new LinkedList<Server>();
			Server server;
			String[] fields = new String[]{"idServer","ip","port","availableSpace","totalSpace","status","keepAlive","keepAliveTime","replicaGroupId","replicaLabel"};

			ResultSet r = select("servers", fields, null);
			try {
				while(r.next()){
					server = new Server(r.getLong(fields[0]),
									r.getString(fields[1]),
									r.getString(fields[2]),
									r.getString(fields[3]),
									r.getString(fields[4]),
									ServerStatus.convert(r.getInt(fields[5])),
									r.getString(fields[6]),
									r.getString(fields[7]),
									r.getInt(fields[8]),
									r.getLong(fields[9])
									);
					userList.add(server);
				}
				r.close();
			} catch (SQLException e) {
				PadFsLogger.log(LogLevel.ERROR,"SynchDataUserList error creating the list: " + e.getMessage());
				try {
					r.close();
				} catch (SQLException e1) {
					PadFsLogger.log(LogLevel.ERROR,e1.getMessage());
				}
			}

			return userList;
		}


		private synchronized static List<Server> resultSetServerToList(ResultSet res){
			List<Server> s = new LinkedList<>();

			PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, "CONVERSION: ");
			try {
				while(res.next()){
					String ip 		= res.getString("ip");
					long id 		= res.getLong("idServer");
					String port 	= res.getString("port");
					String label	= res.getString("replicaLabel");
					int groupId 	= res.getInt("replicaGroupId");

					Long labelLong 		= (label!=null)?Long.parseUnsignedLong(label):null;
					ServerStatus status = ServerStatus.convert(res.getInt("status"));


					PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, "\t id:"+id+" ip:"+ip+" port:"+port+" label:"+label+" groupId:"+groupId);

					s.add(new Server(id,ip,port,null,null, status ,null,null, groupId, labelLong));
				}
			} catch (SQLException e) {
				PadFsLogger.log(LogLevel.ERROR, "List Server creator from result set error: "+e.getMessage());
			}
			return s;
		}

		
		/*
         * Retrive the last update/insert ID
         * @return id|null of the last operation
         
        private synchronized static String getLastId(){
        	ResultSet generatedKeys = null;
        	String sql = "SELECT SCOPE_IDENTITY()";
        	//String sql = "SELECT @@identity";
        	String s;
			try {
				PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, "* "+sql);
				generatedKeys = createStatement().executeQuery(sql);

	        	if (generatedKeys.next()) {
	        		s = String.valueOf(generatedKeys.getLong(1));
	        		generatedKeys.close();
	        		PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, "LAST_ID: "+s);
	        		return s;
	        	}
			} catch (SQLException e) {
        		PadFsLogger.log(LogLevel.ERROR, e.getMessage());
        		try {
					generatedKeys.close();
				} catch (SQLException e1) {
					PadFsLogger.log(LogLevel.ERROR, e1.getMessage());
				}
			}

    		PadFsLogger.log(LogLevel.ERROR, "impossible to retrieve LAST_ID");
    		return null;
        }
        */

        /**
         * Rollback in case of failure
         * @return true if rollback
         * @return false otherwise
         */
		private synchronized static boolean rollback(){
			try {
				c.rollback();
			} catch (SQLException e) {
        		PadFsLogger.log(LogLevel.FATAL, e.getMessage());
        		return false;
			}
			return true;
		}

		/**
		 *
		 * @return
		 */
		public synchronized static boolean commit(){
			try {
				c.commit();
			} catch (SQLException e) {
				rollback();
				PadFsLogger.log(LogLevel.FATAL, e.getMessage());
        		return false;
			}
			return true;
		}



/************************************************************************************
 * GENERIC DB METHOD MANIPULATION
 ************************************************************************************/
		private synchronized static String prepareFields(String [] fields){
			StringBuilder f = new StringBuilder();
        	for(String field:fields){
        		if(field.compareTo("*")!=0){
	        		f.append("\"");
	        		f.append(field);
	        		f.append("\",");
        		}else{
        			f.append(field);
        			f.append(",");
        		}
        	}
    		f.replace(f.length()-1, f.length(), ""); //remove ,
    		return f.toString();
		}

		/**
		 * Retrive the data in the selected table found
		 * @param tableName the name of the table
		 * @return ArrayList with an hashmap for each row
		 * @return null if the tableName is not correct or SqlException
		 */
		public synchronized static ArrayList<HashMap<?,?>> selectTable(String tableName){
			ArrayList<HashMap<?,?>> ret		= null;
			ResultSet r 			= null;
			ResultSetMetaData md 	= null;

			int columns = 0;
			int num 	= 0;

			if(!Padfs.validateString(new String[]{tableName})){
				return null;
			}

			if(!checkTableExist(tableName)){
				return null;
			}

			try {
				r = select(tableName, new String[]{"*"}, null);
				if((num=countRowRS(r))<=0) {
					r.close();
					return null;
				}
				md		= r.getMetaData();
				columns = md.getColumnCount();
				ret 	= new ArrayList<HashMap<?,?>>(num+1);

				while (r.next()){
					HashMap<String,String> row = new HashMap<String,String>(columns);
					try {
						for (int i = 1; i <= columns; i++) {
							switch (tableName) {
								case "servers": //special print (decoding) for the columns ex. 2 => READY in servers
									if (md.getColumnName(i).equals("status")) {
										ServerStatus ss = ServerStatus.convert(r.getInt(i));
										//Constants.ServerStatus ss = SystemEnvironment.convertToServerStatus(
											//	Integer.parseInt(r.getObject(i).toString()));
										row.put(md.getColumnName(i), ss.toString());
									} else {
										row.put(md.getColumnName(i), String.valueOf(r.getObject(i)));
									}
									break;
								default:
									row.put(md.getColumnName(i), String.valueOf(r.getObject(i)));
							}
						}
						ret.add(row);
					}catch (Exception e){
						e.printStackTrace();
					}
				}
				r.close();
			}catch (SQLException e){
				try {
					if(r!=null)
						r.close();
				} catch (SQLException e1) {
					e1.printStackTrace();
				}
				return null;
			}

			return ret;
		}


		/**
		 * Perform the select function for the Database
		 * @param table		The table
		 * @param fields	The field to retrieve (if necessary or null)
		 * @param where		The condition (if necessary or null)
		 *
		 * @return ResultSet if data is present
		 * @return null 	 otherwise
		 */
        private synchronized static ResultSet select(String table, String[] fields, String where, String other){
        	//TODO check user password sqli
        	String sql;
        	ResultSet rs;

        	if(!Padfs.validateString(new String[]{table, fields.toString()})){
        		return null;
        	}

       		sql = "SELECT "+prepareFields(fields)+" FROM PUBLIC.\""+table+"\"";

        	if(where != null)
        		sql += " WHERE "+where;

        	if(other != null)
        		sql += " "+other;

        	PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, "* "+sql);

        	if( (rs = executeQuery(sql, true)) != null ){
        		return rs;
        	}
        	return null;
        }

        /**
		 * Perform the select function for the Database
		 * @param table		The table
		 * @param fields	The field to retrieve (if necessary or null)
		 * @param where		The condition (if necessary or null)
		 *
		 * @return ResultSet if data is present
		 * @return null 	 otherwise
		 */
        private synchronized static ResultSet select(String table, String[] fields, String where){
        	return select(table, fields, where, null);
        }



        /**
         * Insert data in the DB WITH THE COMMIT
         * @param table		The table
         * @param fields	The fields to manipulate
         * @param data		The data do associate to the fields
         *
         * @return id|null of last index insert
         */
        public synchronized static boolean insertNoId(String table, String[] fields, String[] data, boolean autoCommit){
        	//TODO check user password sqli

        	//check if the fields length mismatch with data length
        	if(fields!= null && data!=null){
        		if(fields.length != data.length){
        			PadFsLogger.log(LogLevel.ERROR, "fields mismatch");
        			return false;
        		}
        	}else if(!(fields == null && data == null)){ // (if precedente) se almeno uno dei due e' null   allora  (if attuale) se non sono entrambi null   allora
        		PadFsLogger.log(LogLevel.ERROR, "fields or data are null");
        		return false;
        	}

        	
        	String sql;
        	if( fields != null && data != null){
        		StringBuilder set = new StringBuilder();
            	String val = null;
            	for(int i=0;i<fields.length;i++){
            		val = data[i];

            		if( val == null ){
            			set.append("NULL");
            		}else{
            			if(StringUtils.isNumeric(val)){
            				set.append(" "+val.toString()+" ");
            			}else{
            				set.append("'"+val.toString()+"'");
            			}
            		}

            		if(i<fields.length-1)
            			set.append(", ");
            	}


            	sql = "INSERT INTO PUBLIC.\""+table+"\" ("+prepareFields(fields)+") VALUES ("+set+")";
        	}else if(fields == null && data == null){
            	sql = "INSERT INTO PUBLIC.\""+table+"\" DEFAULT VALUES;";
        	}else{
            	PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, "* NULL");
        		return false;
        	}

        	PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, "* "+sql);

        	if( executeQueryModifyOLD(sql, false) ){
        	//	id = getLastId();
				try {
					if(autoCommit == true)
						c.commit();
				} catch (SQLException e) {
	        		PadFsLogger.log(LogLevel.ERROR, "*commit - "+ e.getMessage());
				}

        		return true;
        	}
        	PadFsLogger.log(LogLevel.ERROR, "can't execute query "+sql);
        	return false;
        }


        /**
         * insertOrReplace data in the DB WITH THE COMMIT
         * @param table		The table
         * @param fields	The fields to manipulate
         * @param data		The data do associate to the fields
         *
         * @return id|null of last index insert
         */
        public synchronized static Long insertOrReplace(String table, String[] fields, String[] data, boolean autoCommit){
        	//TODO check user password sqli

        	//check if the fields length mismatch with data length
        	if(fields!= null && data!=null){
        		if(fields.length != data.length)
        			return null;
        	}else if(!(fields == null && data == null)){ // (if precedente) se almeno uno dei due e' null   allora  (if attuale) se non sono entrambi null   allora
        		return null;
        	}

        	Long id = null;


        	String sql;
        	if( fields != null && data != null){
        		StringBuilder set = new StringBuilder();
            	String val = null;
            	for(int i=0;i<fields.length;i++){
            		val = data[i];

            		if( val == null ){
            			set.append("NULL");
            		}else{
            			set.append("'"+val.toString()+"'");
            		}

            		if(i<fields.length-1)
            			set.append(", ");
            	}
        		sql = "INSERT OR REPLACE INTO PUBLIC."+table+" ("+String.join(",", fields)+") VALUES ("+set+")";
        	}else if(fields == null && data == null){
            	sql = "INSERT OR REPLACE INTO PUBLIC."+table+" DEFAULT VALUES;";
        	}else{
            	PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, "* NULL");
        		return null;
        	}

        	PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, "* "+sql);

        	id =executeQueryModify(sql, false) ;

        		

			try {
				if(autoCommit == true)
					c.commit();
			} catch (SQLException e) {
        		PadFsLogger.log(LogLevel.ERROR, "*commit - "+ e.getMessage());
			}

    		return id;
        	
        }




        /**
         * Update data in the DB WITH THE COMMIT
         * @param table		The table
         * @param fields	The fields to manipulate
         * @param data		The data do associate to the fields
         *
         * @return true  if the update complete correctly
         * @return false otherwise
         */
        public synchronized static boolean update(String table, String[] fields, String[] data, String where, boolean autoCommit){
        	
        	//TODO check sqli
        	
        	if(fields.length != data.length)
        		return false;

        	StringBuilder set = new StringBuilder();


        	String val = null;
        	for(int i=0;i<fields.length;i++){
        		val = data[i];

        		if( val == null ){
        			set.append(fields[i].toString()+"=NULL");
        		}else{
        			set.append(fields[i].toString()+"='"+val.toString()+"'");
        		}

        		if(i<fields.length-1)
        			set.append(", ");
        	}

        	String sql = "UPDATE PUBLIC."+table+" SET "+set.toString()+" WHERE "+where;
        	PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, "* "+sql);

        	if( executeQueryModifyOLD(sql, false) ){

        		//id = getLastId();

				try {
					if(autoCommit == true)
						c.commit();
				} catch (SQLException e) { 
	        		PadFsLogger.log(LogLevel.ERROR, "*commit - "+ e.getMessage());
				}

        		return true;
        	}
        	return false;

        }

        /**
         * Execute the delete function
         * @param table	The table name
         * @param where	The condition for the delete
         * @param autoCommit true or false
         *
         * @return true if succeed
         * @return false otherwise
         */
        public synchronized static boolean delete(String table, String where, boolean autoCommit){

        	if(!Padfs.validateString(new String[]{table, where})){
        		PadFsLogger.log(LogLevel.ERROR, "Table name :"+table+" OR WHERE: "+where+" condition not correct");
        		return false;
        	}

        	String sql = "DELETE FROM PUBLIC.\""+table+"\" WHERE "+where;
        	PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, "* "+sql);

        	if( executeQueryModifyOLD(sql, autoCommit) ){
        		return true;
        	}
        	return false;
        }


        /**
         * Truncate the table
         * @param table The table name
         *
         * @return true if succeed
         * @return false otherwise
         */
        static synchronized public boolean truncate(String table){

        	String sql;

        	if(!Padfs.validateString(new String[]{table})){
        		return false;
        	}

        	sql = "DELETE FROM PUBLIC.\""+table+"\" ";
        	PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, "* "+sql);

        	if( executeQueryModifyOLD(sql, false)  ){
        		return true;
        	}
        	return false;
        }

        

        public synchronized static HashMap<String,List<String>> dumpTable(String table,boolean autocommit){
        	ResultSetMetaData rmsd;

        	String sql = "SELECT * FROM PUBLIC.\""+table+"\" ";
        	PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, "* "+sql);
        	ResultSet r = executeQuery(sql, autocommit);

        	HashMap<String,List<String>> h = new HashMap<>();


			try {
				rmsd = r.getMetaData();

	        	int columnCount = rmsd.getColumnCount();

	        	ArrayList<LinkedList<String>> columnList = new ArrayList<>(columnCount);

	        	String columnName ;
	        	String debugMessage = "";
	        	for(int i = 0; i < columnCount ; i++){
	        		columnName = rmsd.getColumnName(i);
	        		columnList.set(i,new LinkedList<String>());
	        		h.put(columnName, columnList.get(i));

	        		if(debugMessage.equals(""))
	        			debugMessage+=columnName;
	        		else
	        			debugMessage+=","+columnName;

	        	}

	        	PadFsLogger.log(Constants.sqlMainFunctionsLogLevel , "columnRetrieved:  "+debugMessage);

	        	while(r.next()){
	        		for(int i = 0; i < columnCount ; i++){ 
		        		columnList.get(i).add(r.getString(i));
		        	}
	        	}
	        	
	        	r.close();
			} catch (SQLException e) {
				PadFsLogger.log(LogLevel.ERROR, "sqlError dumping table '"+table+"': "+ e.getMessage());
				try {
					r.close();
				} catch (SQLException e1) {
					PadFsLogger.log(LogLevel.ERROR, e1.getMessage());
				}
        		return null;
			}
        	
        	
        	return h;
        }

       
        
        /**
         * Execute a query 
         * @param query		 sql 
         * @param autoCommit true if the query must execute the commit by itself
         * 
         * @return true
         * @return false
         */
		private static synchronized boolean executeQueryModifyOLD(String query, boolean autoCommit) {
			
			
        	try {// Starts transaction.
				c.setAutoCommit(false);
			} catch (SQLException e) {
        		PadFsLogger.log(LogLevel.ERROR, "*setAutoCommit TO FALSE - "+ e.getMessage());
			} 
        	        	
        	if(!Padfs.validateString(new String[]{query})){
        		PadFsLogger.log(LogLevel.ERROR, "query not valid");
        		return false;
        	}
        	
        	//PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, "*** "+query);
        	
        	try {
        		createStatement().execute(query); //it return false if the query is an update
        		
        		if(autoCommit == true){
        			if(!commit()){
        				PadFsLogger.log(LogLevel.ERROR, "can't commit");
            			return false;
        			}
        		}
        		
        		return true;
        	}catch (Exception e) {
        		PadFsLogger.log(LogLevel.ERROR, e.getMessage());
                return false;
            }
		}
        
        
        /**
         * Execute a query 
         * @param query		 sql 
         * @param autoCommit true if the query must execute the commit by itself
         * 
         * @return ResultSet
         * @return null
         */
		private static synchronized ResultSet executeQuery(String query, boolean autoCommit) {
			//TODO check user password sqli
        	ResultSet rs=null;
        	
        	try {// Starts transaction.
				c.setAutoCommit(false);
			} catch (SQLException e) {
        		PadFsLogger.log(LogLevel.ERROR, "*setAutoCommit TO FALSE - "+ e.getMessage());
			} 
        	
        	
        	if(!Padfs.validateString(new String[]{query})){
        		return null;
        	}
        	
        	//PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, "*** "+query);
        	
        	try {
        		rs = createStatement().executeQuery(query); 
        		
        		if(autoCommit == true)
        			commit();
        		return rs;
        	}catch (Exception e) {
        		PadFsLogger.log(LogLevel.ERROR, e.getMessage());
                return null;
            }
		}

		/**
		 * The method execute a query as usual but return a Map representation of the resultSet
		 * @param query
		 * @return Map<String, ArrayList<String>> where the KEY is the first columt that return
		 */
		public synchronized static  Map<String, ArrayList<String>> execQueryReturnMap(String query){
			ResultSet r = executeQueryPublic(query, true);
			Map<String, ArrayList<String>> ret = new HashMap<>();

			ArrayList<String> rows = null;
			try {
				ResultSetMetaData meta = r.getMetaData();
				int columnCount = meta.getColumnCount();

				while(r.next()){ //rows
					rows = new ArrayList<>();
					for(int i = 1; i <= columnCount; i++){ //column
						rows.add(r.getString(i));
					}
					ret.put(r.getString(1),rows);
				}
			} catch (SQLException e) {
				PadFsLogger.log(LogLevel.WARNING, "SQL exception during a query executed from controlPanel: "+e.getMessage()+"  query:"+query);
			}
			return ret;
		}

		/*public static long getNewLabel(int groupId) {
			// 
			//SELECT label FROM servers WHERE groupId = $groupId ORDER BY label ASC
			
			ResultSet res;
			res = select("servers", new String[]{"replicaLabel"}, " replicaGroupId = " +groupId + " ORDER BY replicaLabel ASC");
			
			long maxRange = 0;
			long labelStart=0;
			
			
			try {
				long label,oldLabel;
				
				res.next();
				label=Long.parseUnsignedLong(res.getString("replicaLabel"));
				
				labelStart=label;
				
				if(res.isAfterLast()){
					// if there is only one server in the group, it has to manage the whole range
					maxRange = Constants.numLabels;	
				}
				else 
					while(!res.isAfterLast()){
					oldLabel = label;
					res.next();
					label = Long.parseUnsignedLong(res.getString("replicaLabel"));
					
					if(Long.compareUnsigned(label-oldLabel, maxRange)>0){
						maxRange = label-oldLabel;
						labelStart = oldLabel;
					}
				}
				
				long newLabel;
				newLabel = Long.divideUnsigned(maxRange, 2) + labelStart;
				return newLabel;
			} catch (SQLException e) {
				PadFsLogger.log(LogLevel.FATAL,  e.getMessage());
				return Constants.numLabels;   // out of range returns  ( an hash function never return something greater or equal this number
			}
			
			
		}
		*/
		
        /**
         * Replace the multiple space inside the string passed with one sigle space
         * @param str The string 
         * @return String 
         */
        private static String cleanStringSpace (String str){
        	return str.replaceAll("\\s+", " ").trim();
        }
        
      
	
		/**
		 * 
		 * @param query the insert/update query
		 * @param autoCommit
		 * @return the id of the first inserted/modified row
		 * @return null if the insert/update failed
		 */
        private static synchronized Long executeQueryModify(String query, boolean autoCommit){
      
        	if(!Padfs.validateString(new String[]{query})){
        		PadFsLogger.log(LogLevel.ERROR, "wrong query");
        		return null;
        	}

        	try{
        		c.setAutoCommit(false);
				PreparedStatement statement = c.prepareStatement(query,Statement.RETURN_GENERATED_KEYS);
	        	int affectedRows = statement.executeUpdate();

		        if (affectedRows == 0) {
		        	PadFsLogger.log(LogLevel.ERROR, "insertion failed, no rows affected");
		            return null;
		        }

		        
		        ResultSet generatedKeys = statement.getGeneratedKeys();
	            if (generatedKeys.next()) {
	                Long id = generatedKeys.getLong(1);

			        if(autoCommit == true)
	        			commit();
			        
	                generatedKeys.close();
	                return id;
	            }
	            else {
	            	PadFsLogger.log(LogLevel.TRACE, "no keys generated");
	                return null;
	            }
	        }
        	catch(Exception e){
        		PadFsLogger.log(LogLevel.ERROR, e.getMessage());
        		return null;
        	}
        }
        
        
        
        /**
         * Insert the data in the fields of the table.
         * Use this function ONLY if you let the database generate an id
         * @param table
         * @param fields
         * @param data
         * @param autoCommit
         * @return the autogenerated id of the first inserted row
         * @return null if no id is generated by the db or no rows are inserted
         */
        public synchronized static Long insert(String table, String[] fields, String[] data, boolean autoCommit){
        	//TODO check user password sqli

        	//check if the fields length mismatch with data length
        	if(fields!= null && data!=null){
        		if(fields.length != data.length){
        			PadFsLogger.log(LogLevel.ERROR, "fields mismatch");
        			return null;
        		}
        	}else if(!(fields == null && data == null)){ // (if precedente) se almeno uno dei due e' null   allora  (if attuale) se non sono entrambi null   allora
        		PadFsLogger.log(LogLevel.ERROR, "fields or data are null");
        		return null;
        	}

        	Long id = null;

       		/*
           	PadFsLogger.log(LogLevel.DEBUG, " **** "+fields+" **** "+ data);
        	for(int i = 0; i< fields.length;i++){
        		PadFsLogger.log(LogLevel.DEBUG, " **** "+fields[i]+" **** "+ data[i]);
        	}*/
        	
        	String sql;
        	if( fields != null && data != null){
        		StringBuilder set = new StringBuilder();
            	String val = null;
            	for(int i=0;i<fields.length;i++){
            		val = data[i];

            		if( val == null ){
            			set.append(" NULL ");
            		}else{
            			if(StringUtils.isNumeric(val)){
            				set.append(" "+val.toString()+" ");
            			}else{
            				set.append("'"+val.toString()+"'");
            			}
            		}

            		if(i<fields.length-1)
            			set.append(", ");
            	}


            	sql = "INSERT INTO PUBLIC.\""+table+"\" ("+prepareFields(fields)+") VALUES ("+set+")";
        	}else if(fields == null && data == null){
            	sql = "INSERT INTO PUBLIC.\""+table+"\" DEFAULT VALUES;";
        	}else{
            	PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, "* NULL");
        		return null;
        	}

        	PadFsLogger.log(Constants.sqlMainFunctionsLogLevel, "* "+sql);

        	

    		id = executeQueryModify(sql,autoCommit);

			try {
				if(autoCommit == true)
					c.commit();
			} catch (SQLException e) {
        		PadFsLogger.log(LogLevel.ERROR, "*commit - "+ e.getMessage());
			}

    		return id; 
        
        }

        
        /*
         * this 2 functions are required for test purpose in http://ip:port/query/
         */
        //TODO remove this 2 funcitons OR set a password in the padfsController when used
        public static synchronized Long executeQueryModifyPublic(String query, boolean autoCommit) {
        	return executeQueryModify(query,autoCommit);
        }
        
        public static synchronized ResultSet executeQueryPublic(String query, boolean autoCommit) {
        	return executeQuery(query,autoCommit);
        }

        
        public static String getPhysicalFile(Integer idUser, String path) {
        	return getPhysicalFile(idUser,path,null);
        }
        
        /**
         * 
         * @param user
         * @param path
         * @return the physical path of the hosted file
         */
		public static String getPhysicalFile(Integer idUser, String path, String checksum) {
			if(idUser ==null){
				PadFsLogger.log(LogLevel.ERROR, "idUser can't be null");
				return null;
			}
			
			if(path ==null){
				PadFsLogger.log(LogLevel.ERROR, "path can't be null");
				return null;
			}
			
			String checkChecksum = "";
			if(checksum != null){
				checkChecksum = " AND \"checksum\"='"+checksum+"' ";
			}
			
			String logicalPath = SystemEnvironment.getLogicalPath(path);
			ResultSet r = select("filesHosted",new String[]{"physicalPath"},
					"\"logicalPath\" = '"+logicalPath+"' AND "+
					"\"idUser\" = "+idUser.toString() +
					checkChecksum);
			
			try{
				if(r.next()){
					return r.getString("physicalPath");
				}
			}catch(SQLException e){
				PadFsLogger.log(LogLevel.ERROR,e.getMessage());
			}

			PadFsLogger.log(LogLevel.WARNING, "impossible to retrieve physicalPath");
			return null;
		}

		public static synchronized boolean updateMetaInfoNoPermission(Integer idOwner, String path, List<Long> hosterServers) {
			Integer idFile = SqlManager.getIdFile(path, idOwner);
			
			if(delete("host","\"idFile\"="+idFile,false)){
				for( long sid : hosterServers ){
    				if(! insertNoId("host", 
                			new String[] {"idFile", "idServer"}, 
                			new String[] {String.valueOf(idFile),String.valueOf(sid)}, false ) ){
    					
    					rollback();
    					PadFsLogger.log(LogLevel.ERROR, "can't update host table");
    					return false;
    				}
    			}   
    			commit();
    			return true;
			}
			else{
					rollback();
			}
			
			PadFsLogger.log(LogLevel.ERROR, "cannot update MetaInfo");
			
			return false;
		}

		public static synchronized boolean deleteHostedFile(Long idHostedFile) {
			return delete("filesHosted", "\"idFile\" = "+idHostedFile, true);
		}

		public static synchronized boolean deleteManagedFile(int idOwner, String path) {
			Integer idFile = getIdFile(path, idOwner);
			if(idFile == null){
				PadFsLogger.log(LogLevel.DEBUG, "file not found");
				return false;
			}
			
			if( delete("host", "\"idFile\" = "+idFile, false)){
			
				if( delete("filesManaged", 
						"\"idOwner\" = '"+Long.toUnsignedString(idOwner) + "'" +
						" AND \"path\" = '"+path+"'", 
						true)){
					
					if( delete("grant", "\"idFile\" = "+idFile, true)){
						return true;
					}
					else{
						PadFsLogger.log(LogLevel.ERROR, "cannot delete entry from grant table");
					}
				}
				
				rollback();
				PadFsLogger.log(LogLevel.ERROR, "cannot delete entry from filesManaged table");
			}
			PadFsLogger.log(LogLevel.ERROR, "cannot delete file");
			return false;
		}


		public static boolean unflagHostedFile(int idOwner, String logicalPath, String checksum) {
			if(!Padfs.validateString(new String[]{logicalPath,checksum})){
        		PadFsLogger.log(LogLevel.ERROR, "PARAMETER ERROR [getIdFile] - logicalPath: "+logicalPath+ " - checksum: "+checksum+ " - idUser: " + idOwner);
        		return false;
        	}
 
        	
			return update("filesHosted", new String[]{"uploadingFlag"}, new String[]{"false"}, 
					" \"logicalPath\"='"+logicalPath+"' AND \"idUser\"= "+idOwner + " AND \"checksum\"= '"+checksum+"'",
					true);
		
		}

		public static String getDateFormat() {
			return DB_dateFormat ;
		}
		

		/**
		 * 
		 * @param path
		 * @param idOwner
		 * @return the id of the directory <idOwner,path>
		 * @return null if an error occurs
		 */
		public static Integer getDir(String path, Integer idOwner) {
			if(!Padfs.validateString(new String[]{path}) ){
				PadFsLogger.log(LogLevel.DEBUG, "invalid path"); 
				return null;
			}
		
			if(idOwner == null || idOwner == -1){
				PadFsLogger.log(LogLevel.DEBUG, "invalid idOwner"); 
				return null;
			}

			Integer ret = null;

			/**
			 * SELECT idFile
			 * FROM filesManaged
			 * WHERE path = '<path>' AND
			 * 		 idOwner = '<idOwner>' AND
			 * 		 isDirectory = true
			 */
			ResultSet r = select("filesManaged", new String[]{"idFile"},
					" \"path\"='"+path+"' AND \"idOwner\"="+idOwner + " AND isDirectory = TRUE");
			
			
			try {

				PadFsLogger.log(LogLevel.DEBUG, "checking");
				if (r.next()) { //if exists
					ret = r.getInt("idFile");

					PadFsLogger.log(LogLevel.DEBUG, "checking OK"); 
				}else{
					ret = null;

					PadFsLogger.log(LogLevel.DEBUG, "checking NOT OK");
				}
				r.close();
				return ret;
			} catch (SQLException e) {
				PadFsLogger.log(LogLevel.ERROR, e.getMessage());
				try {
					r.close();
				} catch (SQLException e1) {
					PadFsLogger.log(LogLevel.ERROR, e1.getMessage());
				}
 
				return null;
			}
		}

		/**
		 * add the "path" in the directoryListing table without duplicate. 
		 * 		
		 * 
		 * @param idDir
		 * @param idOwner
		 * @param path
		 * @param isDir
		 * @return true if the file is inserted or it is already inserted
		 * @return false if some error occured
		 */
		public static boolean addInDirectoryListing(DirectoryListingItem item) {
			String idParentDir 	= item.getUniqueIdParentDirectory();
			Integer idOwner = item.getIdOwner();
			String path 	= item.getPath();
			Boolean isDir	= item.isDirectory();
			Long label 		= item.getLabel();
			String size		= item.getSize();
			String dateTime = item.getDateTime();
			
			if(!Padfs.validateString(new String[]{path}) ){
				PadFsLogger.log(LogLevel.ERROR, "PARAMETER ERROR [addInDirectory] - path: "+path);
				return false;
			}
			
			

			String[] fields = { "path", "idOwner" , "idParentDirectory" , "isDirectory", "label", "size", "dateTime" };
			String[] data 	= { path, String.valueOf(idOwner), String.valueOf(idParentDir), String.valueOf(isDir), Long.toUnsignedString(label), size, dateTime};

			ResultSet r = select("directoryListing", new String[]{"idFile"}, "\"path\" = '"+path+"' AND \"idOwner\" ="+idOwner+
																		" AND \"idParentDirectory\" = '"+idParentDir+"' AND \"isDirectory\" = "+isDir);
			boolean alreadyPresent = false;
			try {
				if(r != null && r.next() ) { 
					alreadyPresent = true; 
				}	
			} catch (SQLException e) {
				PadFsLogger.log(LogLevel.ERROR, e.getMessage());
			}
			finally{
				try {
					r.close();
				} catch (SQLException e) {
					PadFsLogger.log(LogLevel.ERROR, "cannot close ResultSet "+e.getMessage());
				}
			}
			
			
			
			if(alreadyPresent || insert("directoryListing",fields,data,true)>0)
				return true;
			else
				return false;
		}


		/**
		 * 
		 * @param user
		 * @param path
		 * @return the content of the directory <user,path>
		 * @return null if an error occurs
		 */
		public static List<DirectoryListingItem> getDirectoryListing(String user, String path) {
			/* get uniqueIdParentDirectory <user,path> */
			String idDir =  getUniqueIdDir(path, getIdUser(user));
			if(idDir == null){
				PadFsLogger.log(LogLevel.DEBUG, "idParentDir is null");
				return null;
			}
			
			return getDirectoryListing( "\"idParentDirectory\" = '" + idDir + "'");
		}



		public static String getUniqueIdDir(String path, Integer idOwner) {
			if(path == null || idOwner == null){
				PadFsLogger.log(LogLevel.DEBUG, "arguments can not be null");
				return null;
			}
			
			if(!Padfs.validateString(new String[]{path}) ){
				PadFsLogger.log(LogLevel.DEBUG, "invalid path"); 
				return null;
			}
		
			if(idOwner == null || idOwner == -1){
				PadFsLogger.log(LogLevel.DEBUG, "invalid idOwner"); 
				return null;
			}

			String ret = null;

			/**
			 * SELECT idFile
			 * FROM filesManaged
			 * WHERE path = '<path>' AND
			 * 		 idOwner = '<idOwner>' AND
			 * 		 isDirectory = true
			 */
			ResultSet r = select("filesManaged", new String[]{"idFile","checksum"},
					" \"path\"='"+path+"' AND \"idOwner\"="+idOwner + " AND isDirectory = TRUE");
			
			
			try {

				PadFsLogger.log(LogLevel.DEBUG, "checking");
				if (r.next()) { //if exists
					ret = r.getString("checksum");

					PadFsLogger.log(LogLevel.DEBUG, "checking OK"); 
				}else{
					ret = null;

					PadFsLogger.log(LogLevel.DEBUG, "checking NOT OK");
				}
				r.close();
				return ret;
			} catch (SQLException e) {
				PadFsLogger.log(LogLevel.ERROR, e.getMessage());
				try {
					r.close();
				} catch (SQLException e1) {
					PadFsLogger.log(LogLevel.ERROR, e1.getMessage());
				}

				
				return null;
			}
		}

		/**
		 * 
		 * @return all the contents of the table directoryListing
		 * @return null if an error occurs
		 */
		public static List<DirectoryListingItem> getDirectoryListing() {
			return getDirectoryListing(null);
		}

		
		/**
		 * 
		 * @return all the contents of the table directoryListing
		 * @return null if an error occurs
		 */
		private static List<DirectoryListingItem> getDirectoryListing(String where) {
			List<DirectoryListingItem> ret = new LinkedList<>();

			/* get contents of directory */
			ResultSet r = select("directoryListing", new String[]{"idFile","path","isDirectory","idOwner","idParentDirectory","size","dateTime"}, where); //no need to read the labels. they can be computed at runtime
			try {
				while(r != null && r.next() ) { 
					
					
					DirectoryListingItem item = new DirectoryListingItem(r.getString("path"),
																		 r.getInt("idOwner"),
																		 r.getString("size"),
																		 r.getString("dateTime"),
																		 r.getBoolean("isDirectory"),
																		 r.getString("idParentDirectory")
																		);
					
					ret.add(item);
				}	

				PadFsLogger.log(LogLevel.DEBUG, "directoryList created");
			} catch (SQLException e) {
				PadFsLogger.log(LogLevel.ERROR, e.getMessage());
			}
			finally{
				try {
					r.close();
				} catch (SQLException e) {
					PadFsLogger.log(LogLevel.ERROR, "cannot close ResultSet "+e.getMessage());
				}
			}
			
			return ret;
		}

		public static boolean removeFromDirectoryListing(DirectoryListingItem item) {
			if(!Padfs.validateString(new String[]{item.getPath(),item.getUniqueIdParentDirectory()}) ){
				PadFsLogger.log(LogLevel.ERROR, "PARAMETER ERROR [deleteFromDirectory] - path: "+item.getPath());
				return false;
			}
			return delete("directoryListing", 
					"\"path\" = '" + item.getPath() + "' AND \"idOwner\" = " + item.getIdOwner() + 
					" AND \"idParentDirectory\" = '" + item.getUniqueIdParentDirectory() + "' AND \"isDirectory\" = " + item.isDirectory(), 
					true);
		}

		
		/**
		 * remove the "path" in the directoryListing table. 
		 * 		
		 * 
		 * @param idDir
		 * @param idOwner
		 * @param path
		 * @param isDir
		 * @return true if the file is deleted or it do not exists
		 * @return false if some error occured
		 *
		public static boolean deleteFromDirectoryListing(Integer idDir, Integer idOwner, String path, Boolean isDir) {
			if(!Padfs.validateString(new String[]{path}) ){
				PadFsLogger.log(LogLevel.ERROR, "PARAMETER ERROR [deleteFromDirectory] - path: "+path);
				return false;
			}
			
			return delete("directoryListing",
					"\"path\" = '"+path+"' AND \"idOwner\" ="+idOwner+" AND \"idParentDirectory\" = "+idDir+" AND \"isDirectory\" = "+isDir,
					true);			
			
		}*/

		public static List<DirectoryListingItem> getDirectoryListing(long startLabel, long endLabel) {
			String where = " \"label\" >= " + startLabel + " AND \"label\" <= "+ endLabel;
			return getDirectoryListing(where);
		}

		public static boolean storeDirectoryListing(List<DirectoryListingItem> directoryList) {

			DirectoryListingItem item;
			String path;
			int idOwner;
			long label;
			boolean isDirectory;
			String idParentDirectory;
			String size;
			String dateTime;
			
    		for(int i=0; i< directoryList.size(); i++){
    			String[] fields = new String[]{"path","idOwner","label","isDirectory","idParentDirectory","size","dateTime"};
    			Long insertedId = null;
    			
    			item = directoryList.get(i);
    			if(item != null){
    				
    				path 		  		= item.getPath();
					idOwner		  		= item.getIdOwner();
    				label	 	  		= item.getLabel();
    				isDirectory	  		= item.isDirectory();
    				idParentDirectory	= item.getUniqueIdParentDirectory();
    				size 				= item.getSize();
    				dateTime			= item.getDateTime();
    				
    				String[] values = new String[ ]{path,    												
    												String.valueOf(idOwner),
    												Long.toUnsignedString(label),
    												Boolean.toString(isDirectory),
    												idParentDirectory,
    												size,
    												dateTime
    												};

    				insertedId = insert("directoryListing",fields,values,false);
    				if(insertedId == null){
    					PadFsLogger.log(LogLevel.ERROR, "failed to store directoryList");
    					return false;
    				}
    			}
    			
    		}

    		return true;
		}

		public static Permission getPermission(int idUser, Integer idFile) {
			
			if(idFile == null || idFile < 0 || idUser < 0){
				PadFsLogger.log(LogLevel.TRACE, "missing idUser");
				return null;
			}
			
			ResultSet rs = select("grant", new String[]{"permission"}, " \"idUser\" = " + idUser + " AND \"idFile\" = " + idFile );
			
			Integer perm = null;
			try {
				if(rs.next()){
					perm = rs.getInt("permission");
					Permission p = Permission.convert(perm);
					return p;
				}
				else{
					PadFsLogger.log(LogLevel.DEBUG, "no permission found");
				}
			} catch (SQLException e) {
				PadFsLogger.log(LogLevel.DEBUG, "no permission found: "+e.getMessage());				
			}
			
			return Permission.unset;
			
		}

		public static boolean storePermission(Integer idUser, Integer idFile, Permission permission) {
			PadFsLogger.log(LogLevel.TRACE, "Store permission "+idUser+","+idFile+","+permission.toString());
			if(idUser == null || idFile == null){
				PadFsLogger.log(LogLevel.ERROR, "idUser or idFile is null");
				return false;
			}
			String[] fields = new String[]{"idUser","idFile","permission"};
			String[] data = new String[]{idUser.toString(),idFile.toString(),String.valueOf(permission.getNumVal())};
			String where = "\"idFile\" = " + idFile + " AND \"idUser\" = " + idUser;
			
			boolean r;
						
			if(permission == null || permission == Permission.unset){
				r = delete("grant", where, true);
				if(!r){
					PadFsLogger.log(LogLevel.DEBUG, "CANNOT delete permission");
				}
			}
			else{
				Permission p = getPermission(idUser, idFile);
				if(p == null){
					PadFsLogger.log(LogLevel.ERROR, "cannot check if permission exists");
					return false;
				}
				if(p == Permission.unset){
					r = insertNoId("grant",fields,data,true);
					if(!r){
						PadFsLogger.log(LogLevel.DEBUG, "CANNOT insert new permission");
					}
				}
				else{
					r = update("grant",fields,data,where,true);
					if(!r){
						PadFsLogger.log(LogLevel.DEBUG, "CANNOT update permission");
					}
				}
			}
			
				
			return r;
		}


	public static Map<String, ArrayList<String>> getDataForProgressBar() {
		String query = "SELECT idServer, availableSpace, totalSpace FROM servers WHERE status > 0 ORDER BY idServer ASC ";
		Map<String, ArrayList<String>> ret = SqlManager.execQueryReturnMap(query);
		return ret;
	}

	public static MerkleTree generateMerkleTreeFromDB() {
		MerkleTree T = new MerkleTree();
		
		/* 
		 * TODO "generateMerkle Tree from DB" left as future work. globalSynch should be able to selectively synch starting from merkle tree
		 * 
		 */
		
		
		return T;
	}

	/**
	 * retrieve from the database the old serverId assigned to this server
	 * @return
	 */
	public static Long retrieveOldIdFromDB() {
		List<Server> servList = getServerList();
		for(Server s : servList){
			if(s != null && s.getPort() != null && s.getIp() != null && Variables.getServerPort() != null && Variables.getMyInterfaceIpList() != null){
				if(s.getPort().equals(Variables.getServerPort()) && (Variables.getMyInterfaceIpList().contains(s.getIp()) || Constants.localhostAddresses.contains(s.getIp()) )){
					return s.getId();
				}
			}
			else{
				PadFsLogger.log(LogLevel.WARNING, "myPort: "+String.valueOf(Variables.getServerPort())+"  myIntList: "+Variables.getMyInterfaceIpListToString()+"server: " + s );
			}
		}
		PadFsLogger.log(LogLevel.ERROR, "cannot find old Ip assigned to this server");
		return null;
	}


}