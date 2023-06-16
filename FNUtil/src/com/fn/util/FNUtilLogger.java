package com.fn.util;


import org.apache.log4j.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.io.FileNotFoundException;
import java.nio.file.Paths;

public class FNUtilLogger {
	private Logger log;
	public FNUtilLogger(String log_Id, String log_dir) throws FNUtilException {
		// TODO Auto-generated constructor stub
		try {
			java.util.Properties props = new java.util.Properties();
			props.load(new FileInputStream("config/log4j.properties"));
			if (log_dir!=null) {
				props.setProperty("log4j.appender.BulkMoveLogginAppender.File", "logs/"+ log_dir +"/" + log_Id + ".log");
			} else {
				props.setProperty("log4j.appender.BulkMoveLogginAppender.File", "logs/" + log_Id + ".log");
			}
			LogManager.resetConfiguration();
			PropertyConfigurator.configure(props);
			log = Logger.getLogger("com.icris");			
		} catch(FileNotFoundException e) {
			throw new FNUtilException(FNUtilException.ExceptionCodeValue.BM_LOG4J_CONF_NOT_FOUND,"log4j.properties not found", e);
		} catch(IOException e) {
			throw new FNUtilException(FNUtilException.ExceptionCodeValue.BM_LOAD_LOG4J_CONFIG_ERROR,"cannot load log4j.properties", e);

		}
	}
	
	public void info(String logMessage){
		log.info(logMessage);
	}
	
	public void error(String errMessage) {
		log.error(errMessage);
	}
	
	public void warn(String logMessage){
		log.warn(logMessage);
	}

	
}
