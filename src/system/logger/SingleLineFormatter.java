package system.logger;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.MessageFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * FORMATTER FOR THE LOG FILE
 */

public class SingleLineFormatter extends Formatter {

	  Date dat = new Date();
	  private final static String format = "[{0,date,dd/MM/YYYY} {0,time,HH:mm:ss}]";
	  private MessageFormat formatter;
	  private Object args[] = new Object[1];

	  // Line separator string.  This is the value of the line.separator
	  // property at the moment that the SimpleFormatter was created.
	  //private String lineSeparator = (String) java.security.AccessController.doPrivileged(
	  //        new sun.security.action.GetPropertyAction("line.separator"));
	  private String lineSeparator = "\n";

	  /**
	   * Format the given LogRecord.
	   * @param record the log record to be formatted.
	   * @return a formatted log record
	   */
	  public synchronized String format(LogRecord record) {

	    StringBuilder sb = new StringBuilder();

	    // Minimize memory allocations here.
	    dat.setTime(record.getMillis());    
	    args[0] = dat;


	    // Date and time 
	    StringBuffer text = new StringBuffer();
	    if (formatter == null) {
	      formatter = new MessageFormat(format);
	    }
	    formatter.format(args, text, null);
	    sb.append(text);

	    // Class name 
//	    sb.append(" [");
//	    if (record.getSourceClassName() != null) {
//	      sb.append(record.getSourceClassName());
//	    } else {
//	      sb.append(record.getLoggerName());
//	    }
//	    sb.append("] ");

	    // Method name 
//	    if (record.getSourceMethodName() != null) {
//	      sb.append("[");
//	      sb.append(record.getSourceMethodName());
//	    }
//	    sb.append("] ");
	    
    
	    // Level
	    sb.append("["+String.format("%14s",record.getLevel().getLocalizedName())+"]:");

	    String message = formatMessage(record).replace("\n", "").replace("\r", "").replace("\t", "").replaceAll("^\\s+", "");	   

	    // Indent - the more serious, the more indented.
	    //sb.append( String.format("% ""s") );
	    sb.append(" ");
	    /*int iOffset = record.getLevel().intValue();
	    if(iOffset >= 900){
	    	sb.append(">>>>>>>>>>   ");
	    }*/

	    sb.append(message);
	    sb.append(lineSeparator);
	    if (record.getThrown() != null) {
	      try {
	        StringWriter sw = new StringWriter();
	        PrintWriter pw = new PrintWriter(sw);
	        record.getThrown().printStackTrace(pw);
	        pw.close();
	        sb.append(sw.toString());
	      } catch (Exception ex) {
	      }
	    }
	    return sb.toString();
	  }
	}
