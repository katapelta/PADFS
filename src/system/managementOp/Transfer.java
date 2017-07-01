package system.managementOp;

import java.io.File;

import org.springframework.web.multipart.MultipartFile;

import system.SystemEnvironment.Constants;
import system.logger.PadFsLogger;
import system.logger.PadFsLogger.LogLevel;
import system.managers.LocalFsManager;
import system.managers.SqlManager;

public class Transfer extends ManagementOp{
	private Integer idUser;
	private String path;
	private MultipartFile file;
	private long label;
	private String checksum;
	
	public Transfer(Integer idUser, String path, MultipartFile file, long label, String checksum) {
		this.idUser 	= idUser;
		this.path		= path;
		this.file 		= file;
		this.label		= label;
		this.checksum   = checksum;
	}
	
	public boolean execute(){
		
		Long idHostedFile = LocalFsManager.uploadFile(idUser, path, file, label, checksum);
		
		
		if(idHostedFile != null){
			

			//check if the checksum sent is correct or if some network error caused a checksum mismatch
			File f = new File(LocalFsManager.getPhysicalFile(idUser,path,checksum));
			String computedChecksum = Constants.checksum(f);
			if(computedChecksum != null){
				if(computedChecksum.equals(checksum)){
						return true;
				}
				else{
					PadFsLogger.log(LogLevel.WARNING, "wrong checksum");
				}
			}
			else{
				PadFsLogger.log(LogLevel.WARNING, "empty checksum");
			}
			//TODO test this branch!
			if(!f.delete()){
				PadFsLogger.log(LogLevel.ERROR, "cannot delete file");
			}
		
			
			
			if(!SqlManager.deleteHostedFile(idHostedFile)){
				PadFsLogger.log(LogLevel.ERROR, "failed cleaning hostedFiles table");
			}
			
		}
		else{
			PadFsLogger.log(LogLevel.DEBUG, "cannot insert in DB the hosted file");
		}
		
		return false;
	}
	
}
