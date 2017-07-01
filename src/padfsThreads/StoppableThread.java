package padfsThreads;

import system.logger.PadFsLogger;
import system.logger.PadFsLogger.LogLevel;

public class StoppableThread extends Thread{
	private Object waitObj;
	private boolean exitingRequested = false;
	
	public StoppableThread(){
		waitObj = new Object();
	}
	
	public void requestToStop() {
		synchronized(waitObj){
			exitingRequested = true;
			waitObj.notify();
			
			PadFsLogger.log(LogLevel.TRACE, "requesting to stop "+this.getName()+", waiting...");
			try{
				waitObj.wait();
			}
			catch(InterruptedException e){ 
				PadFsLogger.log(LogLevel.ERROR, "Thread Interrupted while requesting to stop "+this.getName()); 
			}
		}
	}
	
	/**
	 *  sleep for the given amount of time or until a requestToStop call will arrive 
	 *  
	 */
	protected void waitFor(long time){
		try{
			synchronized(waitObj){
				if(exitingRequested == false){
					PadFsLogger.log(LogLevel.TRACE, "stoppableThread waiting / "+this.getName());
					waitObj.wait(time);
				}
				
			}
		}
		catch(InterruptedException e){ ; }
	}
	
	protected boolean signalStopCompleted(){
		synchronized(waitObj){
			if(exitingRequested == false){
				PadFsLogger.log(LogLevel.ERROR, "try to signal Stop Completed but stop is not been requested");
				return false;
			}
			else{
				PadFsLogger.log(LogLevel.TRACE, "stop "+this.getName()+", completed");
				waitObj.notify();
				return true;
			}
			
		}
	}
	
	protected boolean getExitingRequested(){
		return exitingRequested;
	}
}
