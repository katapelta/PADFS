package padfsThreads;

import java.util.Iterator;
import java.util.List;

import system.SystemEnvironment;
import system.SystemEnvironment.Variables;
import system.logger.PadFsLogger;
import system.logger.PadFsLogger.LogLevel;
import system.managers.SqlManager;

public class Shutdown extends Thread{
	
	
	public void run() {
		PadFsLogger.log(LogLevel.INFO, "waiting shutdown...");
		
		Variables.setExitigState();
		
		/* waiting ExitOperation flow */ 
		while(SystemEnvironment.waitExit() == false){ 
			PadFsLogger.log(LogLevel.ERROR, "waiting shutdown..."); 
		}
		
		/* request to stop all other threads */
		List<StoppableThread> threadList = Variables.getOutOfFLowThreads();
		if(threadList != null){
			Iterator<StoppableThread> it = threadList.iterator();
			while(it.hasNext()){
				StoppableThread t = it.next();
				t.requestToStop();
			}
		}
		
		/* close DataBase connection */
		SqlManager.close();
		
	}

	
}
