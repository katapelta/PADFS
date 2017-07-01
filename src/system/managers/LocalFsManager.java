package system.managers;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.DecimalFormat;

import org.h2.util.IOUtils;
import org.springframework.web.multipart.MultipartFile;

import system.SystemEnvironment;
import system.SystemEnvironment.Constants;
import system.SystemEnvironment.Variables;
import system.logger.PadFsLogger;
import system.logger.PadFsLogger.LogLevel;

public class LocalFsManager {
	
	
	/*
	 * /**
	 * Move the file from TMP designated folder to FS main folder
	 * @param path		The filename in the db
	 * @param idOwner	The id of the owner
	 * @return	the name of the file, if file is moved correctly
	 * @return	null otherwise
	 
	public static String mv2FS(String path, int idOwner){
	
		String tmpPath   = Variables.getFileSystemTMPPath();
		String fsPath    = Variables.getFileSystemPath();
		String separator = Variables.getOSFileSeparator();
		
		Long idFile = null;
		
		String FROM = null;
		String TO   = null;
		
		File from = null;
		File to   = null;
		
		Long idTMPFile    = SqlManager.getIdTMPFile(path, idOwner);
		if(idTMPFile != null){
			idFile = SqlManager.mvTmp2FsDB(path, idOwner);
		
			FROM = tmpPath+separator+idTMPFile;
			TO   = Long.toUnsignedString(idFile);
	
			try {
				from = new File(FROM);
				to   = File.createTempFile(TO, "", new File(fsPath));
			} catch (IOException e) {
				PadFsLogger.log(LogLevel.ERROR, "can not create file");
				return null;
			}
	        
	        //MOVE FILE IN THE FILESYSTEM
	        if( !from.renameTo(to) ){
				PadFsLogger.log(LogLevel.ERROR, "FILE MOVE TMP->FS - FROM: "+FROM+" - TO: "+TO);
				return null;
	        }	        	
		}else{
			PadFsLogger.log(LogLevel.ERROR, "FILE MOVE TMP->FS");
			return null;
		}
		return to.getName();
	}
	
	 */
	
	
	public static boolean deleteTmpFile(String path,int idOwner){
		return deleteTmpFile(SqlManager.getIdTMPFile(path, idOwner));
	}
	
	public static boolean deleteTmpFile(Long idTmpFile){
		String tmpPath   = Variables.getFileSystemTMPPath();
		String separator = Variables.getOSFileSeparator();
		
		String FROM = null;
		
		File from = null;
		Long idTMPFile = idTmpFile;

		
		if(idTMPFile != null){
			if(!SqlManager.deleteTmpDB(idTMPFile)){
				PadFsLogger.log(LogLevel.ERROR, "Delete on TMP TABLE failed" );
				return false;
			}
			FROM = tmpPath+separator+idTMPFile;
		
			from = new File(FROM);
	        
	        //DELETE FILE IN THE FILESYSTEM
	        if( !from.delete() ){
				PadFsLogger.log(LogLevel.ERROR, "TMP FILE "+FROM+" NOT DELETED" );
				return false;
	        }	        	
	        PadFsLogger.log(LogLevel.DEBUG , "TMP FILE "+FROM+" DELETED" );
	        return true;
		}else{
			PadFsLogger.log(LogLevel.ERROR, "TMP FILE DELETE failed");
			return false;
		}
	}
	
	
	
	/**
	 * Write the file to the tmp folder and to the DB as Temp file
	 * @param user
	 * @param password
	 * @param file
	 * @return idFile if file is upload correctly
	 * @return null otherwise
	 */
	public static Long uploadFileTMP(String user, MultipartFile file){
		
		String pathFile = null;
		String name = null;
		BufferedOutputStream out = null;
		
		int idOwner = -1;
		Long idFile  = null;
		DecimalFormat df = new DecimalFormat("#.###");
				
		if (!file.isEmpty()) {
			name = file.getOriginalFilename();
			try {			
				
				Double sizeLong = Double.valueOf((file.getSize()/1024.0000));//MB	
				String size = df.format(sizeLong).toString();


				idOwner = SqlManager.getIdUser(user);
				if( idOwner == -1 ){
					PadFsLogger.log(LogLevel.ERROR, "[FILE NOT UPLOADED]: Error retrive user id from DB");
					return null;
				}

				idFile = SqlManager.addFileASTMP(name, size, idOwner, false);				
				if( idFile == null ){
					PadFsLogger.log(LogLevel.ERROR, "[FILE NOT UPLOADED]: Error add to database");
					return null;
				}
				
				// prepare folder path
				pathFile = prepareTmpPath(idFile);

				InputStream in = file.getInputStream();
				//read file from stream and create in local
				out = new BufferedOutputStream(new FileOutputStream(new File(pathFile)));
				IOUtils.copy(in, out);
				out.flush();
				out.close();
				in.close();
				
				SqlManager.commit(); // only if file is uploaded, otherwise an exception is thrown first than this line
				
				PadFsLogger.log(LogLevel.DEBUG, "[FILE UPLOADED] TO TEMP "+pathFile);
				return (idFile);
			} catch (Exception e) {
				PadFsLogger.log(LogLevel.ERROR, "[FILE NOT UPLOADED]: " + name + " | ERROR: " + e.getMessage());
				return null;
			}
		} else {
			PadFsLogger.log(LogLevel.WARNING, "[FILE NOT UPLOADED]: file empty.");
			return null;
		}
	}
	
	private static String prepareTmpPath(Long idFile) {

		String tempFolderPath = Variables.getFileSystemTMPPath();
		return tempFolderPath + Variables.getOSFileSeparator() + idFile;
	}

	/**
	 * Write the file to the FS folder
	 * @param user
	 * @param file
	 * @return String the path in the file system
	 * @return null	  otherwise
	 */
	public static Long uploadFile(Integer idUser, String path, MultipartFile file, Long label, String checksum){
		String encodedPath = encode(path);
		File pathFile = null;
		BufferedOutputStream stream = null;
				
		if (!file.isEmpty()) { 
			try {
				byte[] bytes = file.getBytes();
				
				// prepare folder path
				pathFile = LocalFsManager.createFSPath(idUser,encodedPath);
				if(pathFile == null){
					PadFsLogger.log(LogLevel.ERROR, "HOSTED FILE ERROR");
					return null;
				}
						
				//read file from stream and create in local
				stream = new BufferedOutputStream(new FileOutputStream(pathFile));
				stream.write(bytes);
				stream.close();
								
				PadFsLogger.log(LogLevel.INFO, "HOSTED NEW FILE: "+pathFile.getName());
				
			} catch (Exception e) {
				PadFsLogger.log(LogLevel.ERROR, "HOSTED FILE ERROR: " + e.getMessage());
				return null;
			}
		} else {
			PadFsLogger.log(LogLevel.WARNING, "HOSTED FILE ERROR: file empty.");
			return null;
		}
		
		
		Long size = file.getSize()/1024; //KB
		Long idHostedFile = SqlManager.insertHostedFile(idUser,path, pathFile.getName(), String.valueOf(size), label, checksum);
		
		return idHostedFile;
	}
	
	private static File createFSPath(Integer idUser, String path) {

		String FSPath = Variables.getFileSystemPath();
		String logicalPath = SystemEnvironment.getLogicalPath(path);
		String extension = "";
		String p = logicalPath;
		int dotPosition = logicalPath.lastIndexOf('.');
		if(dotPosition > 0){
			 extension = logicalPath.substring(dotPosition);
			 p = logicalPath.substring(0, dotPosition);
		}
		String name = idUser+"_"+p+"_";
		File f;
		try {
			f = File.createTempFile(name, extension, new File(FSPath));
		} catch (IOException e) {
			PadFsLogger.log(LogLevel.ERROR, e.getMessage());
			return null;
		}
		return  f;

	}


	/**
	 * Retrive a file and return the stream of it
	 * @param name
	 * @param user
	 * @param password
	 * @return FileStream|null the stream representation of the file
	 */
	public static FileInputStream downloadFile(String name, Integer idOwner, String password){
		
		Integer idFile = null;
		
		if(idOwner == null){ //CHECK USER EXISTS
			return null;
		}
		
		if(!SqlManager.fileUserExists(name, idOwner)){ //CHECK IF THE FILE EXISTS FOR THE SELECTED USER
			return null;
		}
		
		if(SqlManager.isFileTMP(name, idOwner)){ //CHECK IF THE FILE IS A TMP FILE
			return null;
		}
		
		if( (idFile=SqlManager.getIdFile(name, idOwner)) == null ){
			return null;
		}
		
		File file = new File(Variables.getFileSystemPath()+Variables.getOSFileSeparator()+idFile.toString());
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(file);
			return fis;
		} catch (FileNotFoundException e) {
			PadFsLogger.log(LogLevel.ERROR, "[downloadFile] [FILE DOWNLOAD FAILED]: FILENAME: "+name+" - USER: "+idOwner+" - PASSWORD: "+password+ " | ERROR: " + e.getMessage());
			return null;
		}
	}
	

	/*public static String getPhysicalFile(Integer idUser, String path) {
		return Variables.getFileSystemPath() + Variables.getOSFileSeparator() + SqlManager.getPhysicalFile(idUser,path);
	}*/
	
	public static String getPhysicalFile(Integer idUser, String path, String checksum) {
		return Variables.getFileSystemPath() + Variables.getOSFileSeparator() + SqlManager.getPhysicalFile(idUser,path,checksum);
	}

	
	public static File getPhysicalTmpFile(Long idTmpFile) {
		return new File(prepareTmpPath(idTmpFile));
	}

	public static synchronized boolean deletePhysicalFile(int idUser, String logicalPath, String checksum) {
		String physicalPath = getPhysicalFile(idUser, logicalPath, checksum);
		Long hostedId = SqlManager.getHostedFileId(idUser, logicalPath, checksum);
		
		/* delete the file entry in the DB */
		if(hostedId == null){
			PadFsLogger.log(LogLevel.ERROR, "cannot retrieve hosted file from DB");
			return false;
		}
		if(!SqlManager.deleteHostedFile(hostedId)){
			PadFsLogger.log(LogLevel.ERROR, "cannot delete hosted file from DB");
			return false;
		}
		
		
		/* delete the physical file from LocalFS */
		File f = new File(physicalPath);
		try{
			if(f.delete()){
				PadFsLogger.log(LogLevel.DEBUG, "file deleted");
				return true;
			}

			PadFsLogger.log(LogLevel.ERROR, "cannot delete physical file");
			return false;
		}
		
		catch(Exception e){
			PadFsLogger.log(LogLevel.ERROR, "cannot delete physical file. e: "+e.getMessage());
			return false;
		}
	
		
	}

	public static boolean isHosted(Integer idOwner, String path, String checksum) {
		
		Long idFile = SqlManager.getHostedFileId(idOwner, path, checksum);
		if(idFile != null && idFile > 0){
			/* file already hosted */
			PadFsLogger.log(LogLevel.TRACE, "file is hosted");
			return true;
		}

		PadFsLogger.log(LogLevel.TRACE, "file is not hosted");
		return false;
	}

	
	public static String encode(String path) {
		try {
			path = URLEncoder.encode(path, Constants.UTF8);
		} catch (UnsupportedEncodingException e) {
			PadFsLogger.log(LogLevel.ERROR, "failed to encode path: "+path);
			return null;
		}
		return path;
	}
	
	public static String decode(String path) {
		try {
			path = URLDecoder.decode(path, Constants.UTF8);
		} catch (UnsupportedEncodingException e) {
			PadFsLogger.log(LogLevel.ERROR, "failed to decode path: "+path);
			return null;
		}
		return path;
	}
	
}
