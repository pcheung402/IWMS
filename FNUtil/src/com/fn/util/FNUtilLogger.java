package com.fn.util;


import org.apache.log4j.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class FNUtilLogger {
	private Logger log;
	public FNUtilLogger(String baseDir) throws FNUtilException {
		// TODO Auto-generated constructor stub
		try {
			java.util.Properties props = new java.util.Properties();
//			props.load(new FileInputStream(baseDir + File.separator + "log4j.properties"));
			System.out.println(this.getClass().getClassLoader().getResource("log4j.properties"));
			props.load(this.getClass().getClassLoader().getResourceAsStream("log4j.properties"));
			Files.createDirectories(Paths.get(baseDir + File.separator + "logs"));
			props.setProperty("log4j.appender.FileNetMigrateLogginAppender.File", baseDir + File.separator + "logs" + File.separator + "batch.log");
			LogManager.resetConfiguration();
			PropertyConfigurator.configure(props);
			log = Logger.getLogger("com.migrate");			
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
