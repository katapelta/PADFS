package system.logger;

import java.util.logging.Level;

/**
 * ADD DEBUG TO LEVEL 
 */

public class LogLevelExt extends Level {

	private static final long serialVersionUID = 1L;
	
	public LogLevelExt(String name, int value){
	    super(name,value);
	  } 

	public LogLevelExt() {
		super(null,-1);
	}


}