package com.migrate.main;

import java.lang.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.cli.*;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.filenet.api.collection.PageIterator;
import com.filenet.api.collection.RepositoryRowSet;
import com.filenet.api.collection.DocumentSet;
import com.filenet.api.query.RepositoryRow;
import com.filenet.api.query.SearchSQL;
import com.filenet.api.query.SearchScope;
import com.filenet.api.util.Id;
import com.filenet.api.core.ContentTransfer;
import com.filenet.api.core.Document;
import com.filenet.api.core.Factory;
import com.fn.util.CPEUtil;
import com.fn.util.CSVParser;
import com.fn.util.FNUtilException;
import com.fn.util.FNUtilLogger;
import com.fn.util.CSVParser;
import com.migrate.impl.ExportImpl;
//import com.migrate.impl.ImportImpl;
import com.migrate.impl.RejectedExecutionHandlerImpl;
public class Launcher {
	
	static String batchSetId, configFileName, className, mode=null;
	static Integer threads = 10;
	static String folderPath="";
	static String saName;
	static FNUtilLogger log;
	static CPEUtil cpeUtil;	
	static FileOutputStream bulkOperationOutputDataFile;
	static Class<?> bulkOperationClass;
	static Date batchStartTime, batchEndTime;
	static  HashMap<String, String> propertyDefintion;
	static HashMap<String, List<String>> classPropertiesMap;

	public static void main(String[] args) 
			throws 	
			NoSuchMethodException, 
			SecurityException, 
			InstantiationException, 
			IllegalAccessException, 
			IllegalArgumentException, 
			InvocationTargetException, 
			IOException, 
			java.text.ParseException, 
			InterruptedException, 
			ParserConfigurationException, 
			SAXException {

		initialize(args);
        RejectedExecutionHandlerImpl rejectionHandler = new RejectedExecutionHandlerImpl();
        //Get the ThreadFactory implementation to use
        ThreadFactory threadFactory = Executors.defaultThreadFactory();
        //creating the ThreadPoolExecutor
        ThreadPoolExecutor executorPool = new ThreadPoolExecutor(threads, threads, 10, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(threads), threadFactory, rejectionHandler);
		Boolean isOverdue=false;
		waitToStart();
		
		if("ByGUID".equalsIgnoreCase(mode)) {
			BufferedReader reader = new BufferedReader(new FileReader( "." + File.separator + "data" + File.separator + "bulkBatches" + File.separator + batchSetId +".dat"));
			for(String line; (line=reader.readLine())!=null;){
    			Date date = new Date();
    			if (date.after(batchEndTime)) {
    				isOverdue = true;
    				break;
    			}
    			
    			CSVParser csvParser = new CSVParser();
    			String[] parsedLine = csvParser.lineParser(line);    			
    			Document doc = Factory.Document.fetchInstance(cpeUtil.getObjectStore(), new Id(parsedLine[0]), null);
    			doc.fetchProperties(new String[] {"DocumentTitle", "Name", "FoldersFiledIn", "DateLastModified", 
    					"DateCreated", "VersionSeries", "SecurityPolicy", "MajorVersionNumber", 
    					"MinorVersionNumber", "Id", "ClassDescription", "MimeType", /*"ContentElements",*/ "Annotations"});
    			if (doc.get_ContentElements().iterator().hasNext()) {
    			ContentTransfer ct = (ContentTransfer)doc.get_ContentElements().iterator().next();	    			
	    			Constructor<?> cons = bulkOperationClass.getConstructor(new Class[]
	    			{String.class,
	    			Document.class,
	    			FNUtilLogger.class,
	    			FileOutputStream.class,
	    			CPEUtil.class,
	    			HashMap.class,
	    			HashMap.class,
	    			String.class});	
	    			executorPool.execute((Runnable) cons.newInstance(new Object[] {batchSetId, doc, log, bulkOperationOutputDataFile,  cpeUtil, classPropertiesMap, propertyDefintion, mode}));	
    			} else {
    				log.error(String.format("No Content File, %s, %s", doc.get_Id().toString(), doc.get_ClassDescription().get_SymbolicName()));
    			}
			}
		} else {
		
        SearchSQL sqlObject = new SearchSQL();
        sqlObject.setMaxRecords(10000);       
        if("ByFolder".equalsIgnoreCase(mode)) {
        	sqlObject.setWhereClause("r.This INSUBFOLDER '" +folderPath + "'");
        } else if("BySA".equalsIgnoreCase(mode)) {
        	 sqlObject.setWhereClause("StorageArea=Object('"+ getStorageAreaByName(saName).toString()+"')");
        }         
        String select = "r.DocumentTitle, r.Name, r.FoldersFiledIn, r.DateLastModified, r.DateCreated, r.VersionSeries, r.SecurityPolicy, r.MajorVersionNumber, r.MinorVersionNumber, r.Id, r.ClassDescription, r.MimeType, r.ContentElements, r.Annotations";
//        String select = "r.DocumentTitle, r.Name, r.FoldersFiledIn, r.DateLastModified, r.DateCreated, r.VersionSeries, r.SecurityPolicy, r.MajorVersionNumber, r.MinorVersionNumber, r.Id, r.ClassDescription, r.MimeType, r.Annotations";

        sqlObject.setSelectList(select);
        String classAlias = "r";
        Boolean subClassToo = true;
        sqlObject.setFromClauseInitialValue(className, classAlias, subClassToo);
        SearchScope searchScope = new SearchScope(cpeUtil.getObjectStore());
        System.out.println ("Start retrieving : " + new Date());
        DocumentSet docSet = (DocumentSet)searchScope.fetchObjects(sqlObject,null,null ,Boolean.TRUE );
		log.info(String.format("start,%s",batchSetId));
        PageIterator pageIter= docSet.pageIterator();
        pageIter.setPageSize(1000);
        while (pageIter.nextPage()) {
    		System.out.println("Retrieving next " + pageIter.getElementCount() + " records  :" + new Date());
        	for (Object obj : pageIter.getCurrentPage()) {
        		Document doc = (Document)obj;
    			Date date = new Date();
    			if (date.after(batchEndTime)) {
    				isOverdue = true;
    				break;
    			}
    			if (doc.get_ContentElements().iterator().hasNext()) {
	    			ContentTransfer ct = (ContentTransfer)doc.get_ContentElements().iterator().next();
	    			Constructor<?> cons = bulkOperationClass.getConstructor(new Class[]
	    			{String.class,
	    			Document.class,
	    			FNUtilLogger.class,
	    			FileOutputStream.class,
	    			CPEUtil.class,
	    			HashMap.class,
	    			HashMap.class,
	    			String.class});	
	    			executorPool.execute((Runnable) cons.newInstance(new Object[] {batchSetId, doc, log, bulkOperationOutputDataFile,  cpeUtil, classPropertiesMap, propertyDefintion, mode}));
    			} else {
    				log.error(String.format("No Content File, %s, %s", doc.get_Id().toString(), doc.get_ClassDescription().get_SymbolicName()));
    			}
        	}
        }
		}
		executorPool.shutdown();	// orderly shutdown
		
		boolean finished = executorPool.awaitTermination(10, TimeUnit.SECONDS);	// wait until shutdown completed		
		bulkOperationOutputDataFile.flush();
		bulkOperationOutputDataFile.close();
		if (isOverdue) {
			log.info(String.format("overdue,%s",batchSetId));
		} else {
			log.info(String.format("finished,%s",batchSetId));
		}
	}
	
	private static Id getStorageAreaByName(String saName) {
        SearchSQL sqlObject = new SearchSQL();
//        String select = "r.DocumentTitle, r.Name, r.FoldersFiledIn, r.DateLastModified, r.Id, r.ClassDescription, r.MimeType, r.ContentElements, r.Annotations, r.CaseNumber";
        String select = "r.Id, DisplayName";
        sqlObject.setSelectList(select);
//        String objClassName = "StorageArea";
        String classAlias = "r";
        Boolean subClassToo = true;
        sqlObject.setFromClauseInitialValue("StorageArea", classAlias, subClassToo);
        sqlObject.setWhereClause("DisplayName='"+saName+"'");
//        sqlObject.setMaxRecords(1);
//        String orderClause = "r.SubmissionDateTime ASC";
//        sqlObject.setOrderByClause(orderClause);
//        System.out.println("SQL : " + sqlObject.toString());
        SearchScope searchScope = new SearchScope(cpeUtil.getObjectStore());
        RepositoryRowSet rowSet = searchScope.fetchRows(sqlObject, null, null, Boolean.FALSE);
        RepositoryRow row = (RepositoryRow)rowSet.iterator().next();
        return row.getProperties().getIdValue("Id");		
	}
	
	private static void initialize(String[] args) throws java.text.ParseException, FileNotFoundException, IOException, ParserConfigurationException, SAXException {
//		
//		Parsing the input arguments
//		
//		
        Options options = new Options();
        Option optionBatchSetId = new Option("b", "batch", true, "batch id");
        optionBatchSetId.setRequired(true);
        options.addOption(optionBatchSetId);
        
        Option configFile = new Option("c", "config", true, "configuration file name");
        configFile.setRequired(true);
        options.addOption(configFile);
        
        Option docClass = new Option("d", "class", true, "document class");
        docClass.setRequired(true);
        options.addOption(docClass);
        
        
        Option byFolder = new Option("f", "folder", true, "by folder");
        byFolder.setRequired(false);
        options.addOption(byFolder);
        
        Option bySA = new Option("s", "sa", true, "by Storage Area");
        bySA.setRequired(false);
        options.addOption(bySA);
        
        Option byGUID = new Option("g", "guid", false, "by GUID");
        byGUID.setRequired(false);
        options.addOption(byGUID);
        
        Option optionThreads = new Option("t", "threads", true, "no. of threads");
        optionThreads.setRequired(false);
        options.addOption(optionThreads);
        


        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
//        CommandLine cmd = null;//not a good practice, it serves it purpose 

        try {
        	CommandLine cmd = parser.parse(options, args);
            batchSetId = cmd.getOptionValue("batch");
            mode = "ByFolder";
            if(cmd.hasOption("f")) {
            	mode = "ByFolder";
            	folderPath = cmd.getOptionValue("folder");
            } else if(cmd.hasOption("s")) {          	
            	mode = "BySA";
            	saName = cmd.getOptionValue("sa");
            } else if(cmd.hasOption("g")) {          	
            	mode = "ByGUID";
            }
            configFileName = cmd.getOptionValue("config");
            className = cmd.getOptionValue("class");
            
            if (cmd.hasOption("t")) 
            	threads = Integer.parseInt(cmd.getOptionValue("threads"));

        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("utility-name", options);

            System.exit(1);
        }
        
		SimpleDateFormat dateFormatter = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
		String bulkOPerationClassName = System.getProperty("bulk.operation.class");
		if(bulkOPerationClassName==null) {
			System.out.println("No BulkOoperationThread class provided");
			System.exit(1);
		}

		try {
			bulkOperationClass = Class.forName(bulkOPerationClassName);
			java.util.Properties props = new java.util.Properties();
			props.load(new FileInputStream("config/batchSetConfig/" + batchSetId + ".conf"));
			props.load(new FileInputStream("config/docdb.conf"));
			batchStartTime = dateFormatter.parse(props.getProperty("batchStartTime"));
			batchEndTime = dateFormatter.parse(props.getProperty("batchEndTime"));
		} catch (ClassNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			System.exit(1);
		}
		

		try {
			log = new FNUtilLogger(batchSetId,"bulkBatches");
			cpeUtil = new CPEUtil(configFileName, log);
			log.info("Connected to P8 Domain "+ cpeUtil.getDomain().get_Name());
			String bulkMoveOutputFilePath = "." + File.separator + "data" + File.separator + "bulkOutput" + File.separator + batchSetId +".dat";
			Files.deleteIfExists(Paths.get(bulkMoveOutputFilePath));
			bulkOperationOutputDataFile = new FileOutputStream(bulkMoveOutputFilePath, true);
		} catch (FNUtilException e) {
			if (e.exceptionCode.equals(FNUtilException.ExceptionCodeValue.CPE_CONFIG_FILE_NOT_FOUND)) {
				log.error(e.getMessage());				
			} else if (e.exceptionCode.equals(FNUtilException.ExceptionCodeValue.CPE_CONFIG_FILE_CANNOT_BE_OPENED)) {
				log.error(e.getMessage());				
			} 
		} catch (Exception e) {
			log.error("unhandled Exception");
			log.error(e.toString());
		}
		
		initClassPropertiesMap();
		initPropertyDefinition();
	}
	
	private static void initClassPropertiesMap() throws ParserConfigurationException, SAXException, IOException {
		classPropertiesMap = new HashMap<String, List<String>>();
		File fXmlFile = new File( "." + File.separator + "config" + File.separator + "classesPropertiesMap.xml");
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		org.w3c.dom.Document doc = dBuilder.parse(fXmlFile);			
		doc.getDocumentElement().normalize();
		NodeList classNodeList = doc.getElementsByTagName("docClass");						
		for (int j = 0; j < classNodeList.getLength(); j++) {
			Node nNode = classNodeList.item(j);
			if (nNode.getNodeType() == Node.ELEMENT_NODE) {
				Element eElement = (Element) nNode;
				NodeList propNodeList = eElement.getElementsByTagName("property");
				ArrayList<String> propSymbolicNames = new ArrayList<String>();
//				System.out.println("#### "+eElement.getElementsByTagName("symName").item(0).getTextContent()+" #####");
				for (int i = 0; i < propNodeList.getLength(); ++i) {
					Node propNode = propNodeList.item(i);
					Element propElement = (Element) propNode;
					if(propNode.getNodeType()==Node.ELEMENT_NODE) {
						propSymbolicNames.add(propElement.getTextContent());
//						System.out.println(propElement.getTextContent());
					}							
				}
				classPropertiesMap.put(eElement.getElementsByTagName("symName").item(0).getTextContent(), propSymbolicNames);
			}				
			
		}		
	}
	
	private static void initPropertyDefinition() throws ParserConfigurationException, SAXException, IOException {
		propertyDefintion = new HashMap<String, String>();
		File fXmlFile = new File( "." + File.separator + "config" + File.separator + "propertiesDefinitions.xml");
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		org.w3c.dom.Document doc = dBuilder.parse(fXmlFile);			
		doc.getDocumentElement().normalize();
		NodeList propertyNodeList = doc.getElementsByTagName("property");						
		for (int j = 0; j < propertyNodeList.getLength(); j++) {
			Node nNode = propertyNodeList.item(j);
			
			if (nNode.getNodeType() == Node.ELEMENT_NODE) {
				Element eElement = (Element) nNode;
				propertyDefintion.put(eElement.getElementsByTagName("symName").item(0).getTextContent(), eElement.getElementsByTagName("dataType").item(0).getTextContent());
//				System.out.println(eElement.getElementsByTagName("symName").item(0).getTextContent() + " / " + eElement.getElementsByTagName("dataType").item(0).getTextContent());
			}
		}
	}
	
	private static void waitToStart() {
		log.info(String.format("waiting %s,%s",batchSetId, batchStartTime.toString()));
		try {
			Date curDate = new Date();
			TimeUnit.MILLISECONDS.sleep(batchStartTime.getTime() - curDate.getTime());
		} catch (InterruptedException e) {
		
		}
	}
}
