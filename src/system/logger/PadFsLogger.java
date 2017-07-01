package system.logger;

import static org.fusesource.jansi.Ansi.ansi;
import static org.fusesource.jansi.Ansi.Color.WHITE;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.Ansi.Color;

import padfsThreads.Padfs;

import org.fusesource.jansi.AnsiConsole;

import system.SystemEnvironment.Variables;
import system.logger.PadFsLogger.LogLevel;

class WriteMsg{
	private LogLevel level 		= null;
	private String msg 			= null;
	private String className 	= null;
	private String methodName 	= null;
	private String th_name 		= null;
	private Ansi.Color fgColor 	= null;//Ansi.Color.DEFAULT;
	private Ansi.Color bgColor 	= null;//Ansi.Color.DEFAULT;
	private boolean bold 		= false;
	
	WriteMsg(LogLevel level, String msg, String className, String methodName,String th_name, String fgColor, String bgColor, boolean bold){
		setVar(level, msg, className, methodName, th_name);
		if(fgColor != null || fgColor != ""){
			this.fgColor = convertToAnsiColor(fgColor);
		}		
		if(bgColor != null || bgColor != ""){
			this.bgColor = convertToAnsiColor(bgColor);
		}
		if(bold == true){
			this.bold = bold;
		}
	}
	
	WriteMsg(LogLevel level, String msg, String className, String methodName,String th_name, String fgColor, String bgColor){
		setVar(level, msg, className, methodName, th_name);
		if(fgColor != null || fgColor != ""){
			this.fgColor = convertToAnsiColor(fgColor);
		}		
		if(bgColor != null || bgColor != ""){
			this.bgColor = convertToAnsiColor(bgColor);
		}
	}
	
	WriteMsg(LogLevel level, String msg, String className, String methodName,String th_name, String fgColor){
		setVar(level, msg, className, methodName, th_name);
		if(fgColor != null || fgColor != ""){
			this.fgColor = convertToAnsiColor(fgColor);
		}		
	}
	
	
	WriteMsg(LogLevel level, String msg, String className, String methodName,String th_name){
		setVar(level, msg, className, methodName, th_name);
	}
	
	private void setVar(LogLevel level, String msg, String className, String methodName,String th_name){
		this.level 		= level;
		this.msg 		= msg;
		this.className 	= className;
		this.methodName = methodName;
		this.th_name 	= th_name;
	}
	
	private Ansi.Color convertToAnsiColor( String color ){
		if(color == null){
			return Ansi.Color.DEFAULT;
		}
		color = color.toLowerCase();
		Ansi.Color a = Ansi.Color.DEFAULT;
		switch(color){
			case "black":
				a=Ansi.Color.BLACK;
				break;
			case "red":
				a=Ansi.Color.RED;
				break;
			case "green":
				a=Ansi.Color.GREEN;
				break;
			case "white":
				a=Ansi.Color.WHITE;
				break;
			case "blue":
				a=Ansi.Color.BLUE;
				break;
			case "yellow":
				a=Ansi.Color.YELLOW;
				break;
			default:
				a=Ansi.Color.DEFAULT;
		}
		return a;
	}
	
	public LogLevel getLevel(){
		return this.level;
	}
	
	public String getMsg(){
		return this.msg;
	}
	
	public String getClassName(){
		return this.className;
	}
	
	public String getMethodName(){
		return this.methodName;
	}
	
	public String getThName(){
		return this.th_name;
	}
	
	public Ansi.Color getFgColor(){
		return this.fgColor;
	}
	
	public Ansi.Color getBgColor(){
		return this.bgColor;
	}
	
	public boolean getIsBold(){
		return this.bold;
	}
}


public class PadFsLogger extends Thread{
	private static Logger logger = null;
	
	private static LinkedBlockingQueue<WriteMsg> lMessage = new LinkedBlockingQueue<WriteMsg>();
	
	
	private static	int num_char_for_data    = 12;
	private static	int num_char_for_level   = 6;
	
/*	private static	int num_char_for_thread = 10;
	
	private static	int num_char_for_package = 13;
	private static	int num_char_for_class   = 19;
	private static	int num_char_for_method  = 18;*/
	
	private static int num_total_th2Method = 45;
	
	/*
	private static	String format = "[%"+num_char_for_data+"s][%"+num_char_for_level+"s]"
			+ "[%"+num_char_for_class+"s][%"+num_char_for_method+"s]"
			+ " %s\n";
	
	private static	String format = "[%"+num_char_for_data+"s][%"+num_char_for_level+"s]"
			+ "[%"+num_char_for_thread+"s]"
			+ "[%"+num_char_for_package+"s][%"+num_char_for_class+"s][%"+num_char_for_method+"s]"
			+ " %s\n";
	 */
	
	private static final String format = "[%"+num_total_th2Method+"s] %s\n";	
	private static final String format2 = "[%"+num_total_th2Method+"s] %s\n";
	private static final String format3 = "[%"+num_char_for_data+"s][%"+num_char_for_level+"s]"+ "[%"+num_total_th2Method+"s]"+ " %s\n";
	
	
    public enum LogLevel{
    	NONE(0), 
    	FATAL(1),
    	CONFIG(2), 
    	ERROR(3), 
    	WARNING(4), 
    	INFO(5), 
    	DEBUG(6),
    	TRACE(7); 
    	//ALL > TRACE > DEBUG > INFO > WARN > ERROR > FATAL > OFF
    	
    	private int numVal;

    	LogLevel(int numVal) {
            this.numVal = numVal;
        }

        public int getNumVal() {
            return numVal;
        }
    }
    
	/**
	 * Retrive the extension (if exists) of the file
	 * @param fName File name
	 * @return String the extension of the file ex. '.log'
	 */
	private static String getFileExtension(String fName) {
	    try {
	        return fName.substring(fName.lastIndexOf("."));

	    } catch (Exception e) {
	        return "";
	    }

	}
	
	/**
	 * Retrive the file name with the path if is given 
	 * @param fName the name of the file
	 * @return String with the first part before the .
	 */
	private static String getFileName(String fName) {
	    try {
	        return fName.substring(0,fName.lastIndexOf("."));
	    } catch (Exception e) {
	        return "";
	    }
	}
	
	/**
	 * Generate the log file if not exists
	 * @param path the path of the file and the name
	 */
	public static boolean createLog( String path ){
		if( path != null ){			
			//CHECK IF EXISTS
			logger = Logger.getLogger("PadFS");  
		    FileHandler fh;  
		    logger.setUseParentHandlers(false);
		    logger.setLevel(Level.ALL);
		    
		    DateFormat df = new SimpleDateFormat("yyyyMMddHHmmss");
		    Date today = Calendar.getInstance().getTime();        
		    String reportDate = df.format(today);
		    		    
		    try {  
		    	File fc = new File(path);

		    	if(!Variables.isOwLog()){
				    String extension = getFileExtension(path);
				    String fileName  = getFileName(path);
				    
			    	if(fc.exists() && !fc.isDirectory()) { 
			    		path=fileName+"_"+reportDate+extension;
			    		fc = new File(path);
			    	}	
		    	}
		    			    	
		    	/* creazione albero directory */
		    	File directoryPath = fc.getParentFile();
		    	if(directoryPath != null)
		    		directoryPath.mkdirs();
		    	
		        fh = new FileHandler(path);  
		        logger.addHandler(fh);
		        SingleLineFormatter f = new SingleLineFormatter();
		        fh.setFormatter(f);  
		    } catch (IOException | SecurityException e) {  
		        log(LogLevel.ERROR,"LOG FILE CREATION ERROR - "+e.getClass().getName() + ": " + e.getMessage()); 
		    }

	    	system.logger.PadFsLogger.log(LogLevel.CONFIG, "- LOG FILE: <"+path+">");
		    return true;
		}else{
			logger = null;
		}
		return false;

	}
	
	/**
	 * Write to the log file 
	 * @param level A level expressed in LogLevel format
	 * @param msg A message to write  
	 */
	public synchronized static void writeLog( LogLevel level, String msg ){
		final Level DEBUG = new LogLevelExt("DEBUG", Level.INFO.intValue() + 1);
		final Level TRACE = new LogLevelExt("TRACE", Level.INFO.intValue() + 1000);
		final Level ERROR = new LogLevelExt("ERROR", Level.SEVERE.intValue() + 1);
		final Level FATAL = new LogLevelExt("FATAL", ERROR.intValue() + 100);

		if( logger != null ){
			switch(level){
				case  FATAL:
					logger.log(FATAL,msg);
					break;
				case  ERROR:
					logger.log(ERROR,msg);
					break;
				case  WARNING:
					logger.log(Level.WARNING,msg);
					break;
				case INFO:
					logger.log(Level.INFO,msg);
					break;
				case DEBUG:
					logger.log(DEBUG,msg);
					break;
				case TRACE:
					logger.log(TRACE,msg);
					break;
				case CONFIG:
					logger.log(Level.CONFIG,msg);
				    break;
				default:
					logger.log(Level.INFO,msg);
					break;
			} 
		}
	}
	
	public synchronized static void writeLog( LogLevel level, String caller,  String msg ){
		final Level DEBUG = new LogLevelExt("DEBUG", Level.INFO.intValue() + 1);
		final Level TRACE = new LogLevelExt("TRACE", Level.INFO.intValue() + 1000);
		final Level ERROR = new LogLevelExt("ERROR", Level.SEVERE.intValue() + 1);
		final Level FATAL = new LogLevelExt("FATAL", ERROR.intValue() + 100);

		String output = String.format(format2, caller, msg);
		
		if( logger != null ){
			switch(level){
				case  FATAL:
					logger.log(FATAL,output);
					break;
				case  ERROR:
					logger.log(ERROR,output);
					break;
				case  WARNING:
					logger.log(Level.WARNING,output);
					break;
				case INFO:
					logger.log(Level.INFO,output);
					break;
				case DEBUG:
					logger.log(DEBUG,output);
					break;
				case TRACE:
					logger.log(TRACE,output);
					break;
				case CONFIG:
					logger.log(Level.CONFIG,output);
				    break;
				default:
					logger.log(Level.INFO,output);
					break;
			} 
		}
	}
	
	

	/**
	 * Manager PRINT LOG and Write log
	 * @param level 
	 * @param msg
	 */	
	public synchronized static void log( LogLevel level, String msg, String fgColor, String bgColor, boolean bold ) {
		LogLevel l = Variables.getLogLevel();
		l = (l == null)?LogLevel.WARNING:l;
		
		//Ottimizzazione non faccio inserire messaggi che sono con livello piu' basso
		if( l != LogLevel.NONE  ) {
			//System.out.println(level.getNumVal()+" <= "+l.getNumVal());
			if( level.getNumVal()  <=  l.getNumVal() ){		
				StackTraceElement element 	= new Throwable().fillInStackTrace().getStackTrace()[1];
				String className 	= element.getClassName();
				String methodName 	= element.getMethodName();
				String thName		= Thread.currentThread().getName();
				lMessage.add(new WriteMsg(level, msg, className, methodName, thName,fgColor,bgColor, bold));	
			}
		}
	}
	
	public synchronized static void log( LogLevel level, String msg, String fgColor, String bgColor ) {
		LogLevel l = Variables.getLogLevel();
		l = (l == null)?LogLevel.WARNING:l;
		
		//Ottimizzazione non faccio inserire messaggi che sono con livello piu' basso
		if( l != LogLevel.NONE  ) {
			if( level.getNumVal()  <=  l.getNumVal() ){		
				StackTraceElement element 	= new Throwable().fillInStackTrace().getStackTrace()[1];
				String className 	= element.getClassName();
				String methodName 	= element.getMethodName();
				String thName		= Thread.currentThread().getName();
				lMessage.add(new WriteMsg(level, msg, className, methodName, thName,fgColor,bgColor));	
			}
		}
	}
	
	public synchronized static void log( LogLevel level, String msg, String fgColor ) {
		LogLevel l = Variables.getLogLevel();
		l = (l == null)?LogLevel.WARNING:l;
		
		//Ottimizzazione non faccio inserire messaggi che sono con livello piu' basso
		if( l != LogLevel.NONE  ) {
			if( level.getNumVal()  <=  l.getNumVal() ){		
				StackTraceElement element 	= new Throwable().fillInStackTrace().getStackTrace()[1];
				String className 	= element.getClassName();
				String methodName 	= element.getMethodName();
				String thName		= Thread.currentThread().getName();
				lMessage.add(new WriteMsg(level, msg, className, methodName, thName,fgColor));	
			}
		}
	}

	
	public synchronized static void log( LogLevel level, String msg ) {
		LogLevel l = Variables.getLogLevel();
		l = (l == null)?LogLevel.WARNING:l;
		
		//Ottimizzazione non faccio inserire messaggi che sono con livello piu' basso
		if( l != LogLevel.NONE  ) {
			if( level.getNumVal()  <=  l.getNumVal() ){		
				StackTraceElement element 	= new Throwable().fillInStackTrace().getStackTrace()[1];
				String className 	= element.getClassName();
				String methodName 	= element.getMethodName();
				String thName		= Thread.currentThread().getName();
				lMessage.add(new WriteMsg(level, msg, className, methodName, thName));	
			}
		}	
	}
	

	public void run(){

		
		LogLevel level 		= null;
		String msg 	   		= null;
		WriteMsg wm	      	= null;
		String methodName 	= null;
		String className  	= null;
		String th_name    	= null;
		
		Ansi.Color fgColor 	= null;
		Ansi.Color bgColor  = null;
		boolean isBold		= false;
		
		
		while(true){
			try {
				wm = lMessage.take();
			} catch (InterruptedException e) {
				System.err.println("\n\n\t [PADFSLOGGER] ERROR RETRIEVE MESSAGE\n\n");
				Padfs.harakiri();
			}
			
			msg 		= wm.getMsg();
			level 		= wm.getLevel();
			methodName 	= wm.getMethodName();
			className 	= wm.getClassName();
			th_name		= wm.getThName();
			
			fgColor		= wm.getFgColor();
			bgColor		= wm.getBgColor();
			isBold		= wm.getIsBold();
			
			if(msg==null)
				msg = "############ TRY TO PRINT A NULL MESSAGE ############";
			
			LogLevel l = Variables.getLogLevel();
			l = (l == null)?LogLevel.WARNING:l;
			
			//DateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss"); //%19s
			//DateFormat dateFormat = new SimpleDateFormat("dd/MM HH:mm:ss"); //%14s
			DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss.SSS"); //%12s
	
			Date date = new Date();
			String dataFormat = dateFormat.format(date);
			
			//String format = "[%"+num_total_th2Method+"s]" + " %s\n";

			
			
			
			String packageCallerName 	= "";
			if((className.indexOf(".")!=-1)){
				packageCallerName 	= className.substring(0,className.indexOf("."));
				className 	= className.substring(className.indexOf(".")+1,className.length());
			}else{
				packageCallerName = "";
			}
			className = className.replace("$", ".");
			
			String tmp = th_name+"."+packageCallerName+"."+className+"."+methodName; //Complete path of the function
						
						
			if(tmp.length()>num_total_th2Method)
				tmp  = tmp.substring(tmp.length() - num_total_th2Method);
			/*
			if(th_name.length()>num_char_for_thread)
				th_name  = th_name.substring(th_name.length() - num_char_for_thread);
				
			if(packageCallerName.length()>num_char_for_package)
				packageCallerName  = packageCallerName.substring(packageCallerName.length() - num_char_for_package);
			
			if(classCallerName.length()>char_for_class)
				classCallerName  = classCallerName.substring(classCallerName.length() - char_for_class);
			
			if(methodCallerName.length()>char_for_method)
				methodCallerName = methodCallerName.substring(methodCallerName.length() -char_for_method);
			*/
			Boolean colouredOutput = Variables.getColouredOutput();
			if(colouredOutput){
				AnsiConsole.systemInstall();
			}
		    if( logger == null ){
		    	System.err.format(format,dataFormat,"ERROR",tmp,msg);
		    }else{
				if( l != LogLevel.NONE  ) {
					String lev = level.toString();
					if( level.getNumVal()  <=  l.getNumVal() ){
						if(level == LogLevel.ERROR){ 						
							lev = " ERROR";		 						 //ERROR
							if(colouredOutput) 
								System.err.print("["+dataFormat+"]" + "["+ ansi().bg(Color.MAGENTA).fg(WHITE).bold().a(lev).reset()+"]");
							else
								System.err.print("["+dataFormat+"]" + "["+ lev+"]");	
							System.err.format(format, tmp,msg);
							
						}else if(level == LogLevel.WARNING){ 	
							lev = "  WARN"; 							//WARNING
							if(colouredOutput) {
								System.err.print("["+dataFormat+"]" + "["+ ansi().bg(Color.YELLOW).fg(WHITE).bold().a(lev).reset()+"]");	
							}else
								System.err.print("["+dataFormat+"]" + "["+ lev+"]");	
							System.err.format(format, tmp,msg);
						
						}else if(level == LogLevel.FATAL){  
							lev = " FATAL";								//FATAL
							if(colouredOutput) 
								System.err.print("["+dataFormat+"]" + "["+ ansi().fg(WHITE).bg(Color.RED).bold().a(lev).reset()+"]");
							else
								System.err.print("["+dataFormat+"]" + "["+ lev+"]");	
							System.err.format(format, tmp,msg);
							Padfs.harakiri();
						}else{
							
							msg = msg.replace("\t", "\t\t\t");
							
							if(colouredOutput){
								Ansi print = ansi();
								if(fgColor!=null){
									print = print.fg(fgColor);
								}
								if(bgColor!=null){
									print = print.bg(bgColor);
								}
								if(isBold){
									print = print.bold();
								}
								System.out.format(format3,dataFormat, level.toString(),tmp,print.a(msg).reset());
							}else{
								System.out.format(format3,dataFormat, level.toString(),tmp,msg);
							}
						}
						
						/**
						 * Write log to logFile
						 */
						msg = msg.replace("\n", "").replace("\r", "").replace("\t", "");
						writeLog(level, tmp, msg);
					}
				}
		    }
		    if(colouredOutput){
		    	AnsiConsole.systemUninstall();
		    }
		}
	}
}
