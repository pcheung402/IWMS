package com.migrate.abs;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.DocumentBuilder;

//import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.w3c.dom.Node;
import org.w3c.dom.Element;

import com.filenet.api.util.UserContext;
import com.filenet.api.core.*;
import com.filenet.api.collection.*;
import com.fn.util.CPEUtil;
import com.fn.util.CSVParser;
import com.fn.util.FNUtilException;
import com.fn.util.FNUtilLogger;

public abstract class BulkOperationThread implements Runnable {
	protected FNUtilLogger log;	
	protected String batchBaseDir;
	protected CPEUtil cpeUtil;
//	protected FileOutputStream bulkOperationOutputDataFile;
	protected String mode;	
//	protected CSVParser csvParser;
	protected Date batchStartTime;
	protected Date batchEndTime;
	protected String JDBCURL; 
	protected String dbuser;
	protected String dbpassword; 
	protected java.sql.Connection conn=null;
	protected String queryString;
	protected Boolean previewOnly;
	protected String dataLine;
	protected Document doc;
	protected HashMap<String, List<String>> classPropertiesMap;
	protected HashMap<String, String> propertyDefintion;
//	protected HashMap<Integer, String> classNumToSymNameMap = new HashMap<Integer, String>();
//	protected HashMap<Integer, ArrayList<String>> classIndexMap = new HashMap<Integer, ArrayList<String>>();
	protected String[] isSystemProperties = {"F_ARCHIVEDATE","F_DELETEDATE","F_DOCCLASSNUMBER","F_DOCFORMAT","F_DOCLOCATION","F_DOCNUMBER","F_DOCTYPE","F_ENTRYDATE","F_PAGES","F_RETENTOFFSET"};


	
	public BulkOperationThread(String batchBaseDir, Document doc, FNUtilLogger log,  CPEUtil cpeUtil, HashMap<String, List<String>> classPropertiesMap, HashMap<String, String> propertyDefintion, String  mode) {
//		this.classNumToSymNameMap.put(1, "ICRIS_Pend_Doc");
//		this.classNumToSymNameMap.put(2, "ICRIS_Reg_Doc");
//		this.classNumToSymNameMap.put(3, "ICRIS_Tmplt_Doc");
//		this.classNumToSymNameMap.put(4, "ICRIS_Migrate_Doc");
//		this.classNumToSymNameMap.put(5, "ICRIS_CR_Doc");
//		this.classNumToSymNameMap.put(6, "ICRIS_BR_Doc");
		this.log = log;
		this.batchBaseDir = batchBaseDir;
//		this.bulkOperationOutputDataFile = ofs;
		this.classPropertiesMap = classPropertiesMap;
		this.propertyDefintion = propertyDefintion;
		this.mode = mode;
		this.doc = doc;
//		this.conn = conn;
//		this.csvParser = new CSVParser();

		try {
			java.util.Properties props = new java.util.Properties();
			props.load(new FileInputStream(batchBaseDir + File.separator + "batch.conf"));
			this.cpeUtil = cpeUtil;
			loadBatchSetConfig();			
//			initClassPropertiesMap();
//			initPropertyDefinition();
//			loadClassIndexMap();
			
			
		} catch (FNUtilException e) {
			e.printStackTrace();
			if (e.exceptionCode.equals(FNUtilException.ExceptionCodeValue.CPE_USNAME_PASSWORD_INVALID)) {
				log.error(e.getMessage());				
			} else if (e.exceptionCode.equals(FNUtilException.ExceptionCodeValue.CPE_URI_INVALID)) {
				log.error(e.getMessage());				
			} else if (e.exceptionCode.equals(FNUtilException.ExceptionCodeValue.CPE_INVALID_OS_NAME)) {
				log.error(e.getMessage());				
			} else if (e.exceptionCode.equals(FNUtilException.ExceptionCodeValue.BM_LOAD_BATCH_SET_CONFIG_ERROR)) {
				log.error(e.getMessage());
			}
		} catch (Exception e) {
			log.error("unhandled CPEUtil Exception");
			log.error(e.toString());
		}
	}

	@Override
	public void run() {
		UserContext.get().pushSubject(cpeUtil.getSubject());
		try {
			if(conn==null) {
				this.conn = getMySQLConnection();
			}
			this.processBatchItem(this.doc);
			this.conn.close();

		} catch(FNUtilException e) {
			e.printStackTrace();
			FolderSet folderSet = doc.get_FoldersFiledIn();
			if(folderSet.isEmpty()) {
				log.error(String.format("%s,%s,null", e.exceptionCode, doc.get_Id(), doc.get_Name()));
			} else {
				Folder folder = (Folder)folderSet.iterator().next();
				log.error(String.format("%s,%s,%s", e.exceptionCode, doc.get_Id(), doc.get_Name(), folder.get_PathName()));
			}
		} catch (SQLException|IOException e) {
			e.printStackTrace();
			FolderSet folderSet = doc.get_FoldersFiledIn();
			if(folderSet.isEmpty()) {
				log.error(String.format("%s,%s,null", e.getMessage(), doc.get_Id(), doc.get_Name()));
			} else {
				Folder folder = (Folder)folderSet.iterator().next();
				folder.fetchProperties(new String[] {"PathName"});
				log.error(String.format("%s,%s,%s", e.getMessage(), doc.get_Id(), doc.get_Name(), folder.get_PathName()));
			}
		} 
	}
	
	private java.sql.Connection getMySQLConnection(){
		java.sql.Connection result = null;
//		log.info("Create DB Connection");
		try {
			result = DriverManager.getConnection(this.JDBCURL, this.dbuser, this.dbpassword);
			result.setAutoCommit(true);
		}catch (SQLException e) {
			e.printStackTrace();
			System.exit(1);
		} 			
		return result;		
	}
	
	private void loadBatchSetConfig() throws FNUtilException {
		SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
		try {
			java.util.Properties props = new java.util.Properties();
			props.load(new FileInputStream(this.batchBaseDir + File.separator + "batch.conf"));
//			props.load(new FileInputStream("config/docdb.conf"));
			this.batchStartTime = formatter.parse(props.getProperty("batchStartTime"));
			this.batchEndTime = formatter.parse(props.getProperty("batchEndTime"));
			this.queryString = props.getProperty("RegBarcodeQuery");
			this.JDBCURL = "jdbc:mysql://"+ props.getProperty("DOCDBServer") + ":" + props.getProperty("DOCDBPort") + "/" + props.getProperty("DOCDBDatabase") + "?autoReconnect=true&failOverReadOnly=false";
			this.dbuser = props.getProperty("DOCDBUser");
			this.dbpassword = props.getProperty("DOCDBPassword");
			this.previewOnly = "TRUE".equalsIgnoreCase(props.getProperty("PreviewOnly", "False"))?Boolean.TRUE:Boolean.FALSE;
		} catch (IOException e) {
			throw new FNUtilException(FNUtilException.ExceptionCodeValue.BM_LOAD_BATCH_SET_CONFIG_ERROR,"Load batchSetConfig error : " + batchBaseDir + File.separator + "batch.conf", e);
		} catch (ParseException e) {
			throw new FNUtilException(FNUtilException.ExceptionCodeValue.BM_LOAD_BATCH_SET_CONFIG_ERROR,"BatchSetConfig time format error ", e);			
		} catch (NumberFormatException e) {
			throw new FNUtilException(FNUtilException.ExceptionCodeValue.BM_LOAD_BATCH_SET_CONFIG_ERROR,"BatchSetConfig integer format error", e);						
		}
	}
	
	
//	private void initClassPropertiesMap() throws ParserConfigurationException, SAXException, IOException {
//		classPropertiesMap = new HashMap<String, List<String>>();
//		File fXmlFile = new File( "." + File.separator + "config" + File.separator + "classesPropertiesMap.xml");
//		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
//		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
//		org.w3c.dom.Document doc = dBuilder.parse(fXmlFile);			
//		doc.getDocumentElement().normalize();
//		NodeList classNodeList = doc.getElementsByTagName("docClass");						
//		for (int j = 0; j < classNodeList.getLength(); j++) {
//			Node nNode = classNodeList.item(j);
//			if (nNode.getNodeType() == Node.ELEMENT_NODE) {
//				Element eElement = (Element) nNode;
//				NodeList propNodeList = eElement.getElementsByTagName("property");
//				ArrayList<String> propSymbolicNames = new ArrayList<String>();
//				for (int i = 0; i < propNodeList.getLength(); ++i) {
//					Node propNode = propNodeList.item(i);
//					Element propElement = (Element) propNode;
//					if(propNode.getNodeType()==Node.ELEMENT_NODE) {
//						propSymbolicNames.add(propElement.getTextContent());
//					}							
//				}
//				classPropertiesMap.put(eElement.getElementsByTagName("symName").item(0).getTextContent(), propSymbolicNames);
//			}				
//			
//		}		
//	}
	
//	private void initPropertyDefinition() throws ParserConfigurationException, SAXException, IOException {
//		propertyDefintion = new HashMap<String, String>();
//		File fXmlFile = new File( "." + File.separator + "config" + File.separator + "propertiesDefinitions.xml");
//		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
//		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
//		org.w3c.dom.Document doc = dBuilder.parse(fXmlFile);			
//		doc.getDocumentElement().normalize();
//		NodeList propertyNodeList = doc.getElementsByTagName("property");						
//		for (int j = 0; j < propertyNodeList.getLength(); j++) {
//			Node nNode = propertyNodeList.item(j);
//			
//			if (nNode.getNodeType() == Node.ELEMENT_NODE) {
//				Element eElement = (Element) nNode;
//				propertyDefintion.put(eElement.getElementsByTagName("symName").item(0).getTextContent(), eElement.getElementsByTagName("dataType").item(0).getTextContent());
//			}
//		}
//	}
		
//	private void loadClassIndexMap() throws IOException {
//		for (Integer i=0; i<6; ++i) {
//			Integer classNum = i + 1;			
//			String indexNameFilePath = "." + File.separator + "config" + File.separator + "index_name_list_" + String.format("%d", classNum) + ".conf";
//			BufferedReader br = new BufferedReader(new FileReader(new File(indexNameFilePath)));
//			ArrayList<String> indexArray = new ArrayList<String>();
//			String line;
//			while ((line=br.readLine())!=null) {
//				indexArray.add(line);
//			}
//			classIndexMap.put(classNum, indexArray);
//		}
//	}
	
//	public abstract void processBatchItem(String line) throws FNUtilException, SQLException, IOException;
	public abstract void processBatchItem(Document doc) throws FNUtilException, SQLException, IOException;
}
