package com.migrate.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;
import java.sql.BatchUpdateException;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.regex.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Element;

import com.filenet.api.admin.StorageArea;
import com.filenet.api.collection.AnnotationSet;
import com.filenet.api.collection.ContentElementList;
import com.filenet.api.collection.DocumentSet;
import com.filenet.api.collection.FolderSet;
import com.filenet.api.collection.StringList;
import com.filenet.api.core.Annotation;
import com.filenet.api.core.ContentTransfer;
import com.filenet.api.core.Document;
import com.filenet.api.core.Factory;
import com.filenet.api.core.Folder;
import com.filenet.api.exception.EngineRuntimeException;
import com.filenet.api.exception.ExceptionCode;
import com.filenet.api.meta.ClassDescription;
import com.filenet.api.property.Properties;
import com.filenet.api.query.SearchSQL;
import com.filenet.api.query.SearchScope;
import com.filenet.api.security.SecurityPolicy;
import com.filenet.api.util.Id;
import com.filenet.api.util.UserContext;
import com.fn.util.CPEUtil;
import com.fn.util.FNUtilException;
import com.fn.util.FNUtilException.ExceptionCodeValue;
import com.fn.util.DocumentXML;
import com.fn.util.FNUtilLogger;
import com.fn.util.FNExportStatus;
import com.migrate.abs.BulkOperationThread;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

public class ExportImpl2 extends BulkOperationThread {
//	
//	
//	The following variable are inherited from BulkOperationThread
//	
//	
//	protected FNUtilLogger log;	
//	protected String batchSetId;
//	protected CPEUtil cpeUtil;
//	protected FileOutputStream bulkOperationOutputDataFile;
//	protected String mode;	
//	protected CSVParser csvParser;
//	protected Date batchStartTime;
//	protected Date batchEndTime;
//	protected String JDBCURL; 
//	protected String dbuser;
//	protected String dbpassword; 
//	protected java.sql.Connection conn;
//	protected String queryString;
//	protected Boolean previewOnly;
//	protected String dataLine;
//	protected HashMap<String, List<String>> classPropertiesMap;
//	protected HashMap<String,  HashMap<String,String>> propertyDefintion;
//	protected String[] isSystemProperties = {"F_ARCHIVEDATE","F_DELETEDATE","F_DOCCLASSNUMBER","F_DOCFORMAT","F_DOCLOCATION","F_DOCNUMBER","F_DOCTYPE","F_ENTRYDATE","F_PAGES","F_RETENTOFFSET"};
//
	
	private String docSubDir;
	private DocumentXML documentXML;
	private String[] propertiesNameArray;
	private ArrayList<String>  singleValuedProperties;
	private ArrayList<String>  multiValuedProperties;
	
	public ExportImpl2(String batchBaseDir, Document doc, FNUtilLogger log, CPEUtil cpeUtil, HashMap<String, List<String>> classPropertiesMap, HashMap<String,  HashMap<String,String>> propertyDefintion, String mode) {
		super(batchBaseDir, doc, log, cpeUtil,classPropertiesMap, propertyDefintion, mode);
		// TODO Auto-generated constructor stub
		this.docSubDir = this.batchBaseDir + File.separator + "documents" + File.separator + getDocSubDir(doc);	
	}
	

	public void processBatchItem(Document doc) throws FNUtilException, IOException, SQLException {
		documentXML = new DocumentXML();
		ClassDescription cd = doc.get_ClassDescription();
		String classSymbolicName = cd.get_SymbolicName();
		if(classPropertiesMap.get(classSymbolicName)==null) {
			log.error(String.format("Invalid Document Class : %s,%s", doc.get_Id().toString(), classSymbolicName));
			return;
		}		
		this.propertiesNameArray = classPropertiesMap.get(classSymbolicName).toArray(new String[0]);
		this.singleValuedProperties = new ArrayList<String>();
		this.multiValuedProperties = new ArrayList<String>();
		for (int i = 0; i < propertiesNameArray.length; ++i) {
			String dataType = propertyDefintion.get(propertiesNameArray[i]).get("dataType");

			if("String".equalsIgnoreCase(dataType)||
					"DateTime".equalsIgnoreCase(dataType)||
					"Id".equalsIgnoreCase(dataType)||
					"Integer".equalsIgnoreCase(dataType)||
					"Double".equalsIgnoreCase(dataType)||
					"Boolean".equalsIgnoreCase(dataType)
					) {
				singleValuedProperties.add(propertiesNameArray[i]);
			} else if ("StringList".equalsIgnoreCase(dataType)||
					"DateTimeList".equalsIgnoreCase(dataType)||
					"IdList".equalsIgnoreCase(dataType)||
					"IntegerList".equalsIgnoreCase(dataType)||
					"DoubleList".equalsIgnoreCase(dataType)||
					"BooleanList".equalsIgnoreCase(dataType)
					) {
				multiValuedProperties.add(propertiesNameArray[i]);
			}
		}

		
		if(createDocument(doc)) {
//			System.out.println("document created");
			addContent(doc);
			addAnnotation(doc);
			addContainer(doc);
			try {				
		          JAXBContext jaxbContext = JAXBContext.newInstance(DocumentXML.class);
		          Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
		 
		          jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE); // To format XML
		 
		          //Print XML String to Console
		          jaxbMarshaller.marshal(documentXML, new File(docSubDir + File.separator + "properties.xml"));
		           
			
			} catch (JAXBException e) {
				// TODO Auto-generated catch block
				logExportError(doc, FNExportStatus.FAIL_CREATE_PROPTERY_FILE, "Fail create Properties.xml");
			}
		}

		
	}
	
	
	
	private Boolean createDocument(Document doc) throws SQLException, IOException {

		PreparedStatement deleteDocumentStatement = conn.prepareStatement("DELETE FROM DOCUMENT WHERE OBJECT_ID=UUID_TO_BIN(?)");
		deleteDocumentStatement.setString(1, doc.get_Id().toString());
		deleteDocumentStatement.execute();
		Boolean result = Boolean.FALSE;
		ClassDescription cd = doc.get_ClassDescription();
		String classSymbolicName = cd.get_SymbolicName();
		
		PreparedStatement insertStatement = pepareInsertStatement(doc, classSymbolicName);
		

		
		if(insertStatement!=null) {
			insertStatement.execute();
			insertStatement.close();
			Files.createDirectories(Paths.get(this.docSubDir));
			
//			String[] propertiesNameArray = classPropertiesMap.get(classSymbolicName).toArray(new String[0]);
//			
//			for (int i = 0; i < propertiesNameArray.length; ++i) {
//				String dataType = propertyDefintion.get(propertiesNameArray[i]).get("dataType");
//				if("StringList".equalsIgnoreCase(dataType)||
//						"DateList".equalsIgnoreCase(dataType)||
//						"IdList".equalsIgnoreCase(dataType)||
//						"IntegerList".equalsIgnoreCase(dataType)||
//						"DoubleList".equalsIgnoreCase(dataType)||
//						"StringList".equalsIgnoreCase(dataType)
//						) {
//
//					addMultiValuedProperty(doc, propertiesNameArray[i]);
//				}
//			}
			

			result = Boolean.TRUE;
		}
		return result;
	}
	
	private PreparedStatement pepareInsertStatement(Document doc, String classSymbolicName) throws SQLException {

		doc.fetchProperties(propertiesNameArray);
		Properties docProperties = doc.getProperties();
		Id vsId = doc.get_VersionSeries().get_Id();
		SecurityPolicy securityPolicy = doc.get_SecurityPolicy();
		securityPolicy.fetchProperties(new String[] {"DisplayName"});
		Date dateCreated = doc.get_DateCreated();
		Date dateLastModified = doc.get_DateLastModified();
		
		String[] sysProperties = {
				"OBJECT_ID",
				"OBJECT_VSID",
				"MAJOR_VER",
				"MINOR_VER",
				"DATE_CREATED",
				"DATE_LAST_MODIFIED",
				"SECURITY_POLICY",
				"CLASS_SYMBOLIC_NAME",
				"DOCUMENTTITLE",
				"MIME_TYPE",
				"SECURITY_NAME",
				"STORAGE_AREA_NAME"
		};

		String queryString= "REPLACE INTO DOCUMENT ("
//					+ "OBJECT_ID,OBJECT_VSID,MAJOR_VER,MINOR_VER,DATE_CREATED,DATE_LAST_MODIFIED,SECURITY_POLICY,CLASS_SYMBOLIC_NAME,DOCUMENTTITLE,MIME_TYPE"
				+ String.join(",", sysProperties)
				+ "," 
				+ String.join(",", singleValuedProperties)									
				+ ") VALUES (UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,?,?,UUID_TO_BIN(?),?,?,?,?,?";
		
//			queryString += ",?".repeat(singleValuedProperties.size());
		for (String s:singleValuedProperties) {
			queryString += ",?";
		}
	
	
		queryString += ")";
;			
			PreparedStatement insertStatement = conn.prepareCall(queryString);
			
			insertStatement.setString(1, doc.get_Id().toString());
			documentXML.addProperty("Id", "Object Id", "Id", doc.get_Id().toString());
					
		insertStatement.setString(2, vsId.toString());
		documentXML.addProperty("Name", "vsId", "Id", doc.get_VersionSeries().get_Id().toString());
					
		insertStatement.setInt(3, doc.get_MajorVersionNumber());
		documentXML.addProperty("Major_Version", "Major Version", "Integer", String.valueOf(doc.get_MajorVersionNumber()));
					
		insertStatement.setInt(4, doc.get_MinorVersionNumber());
		documentXML.addProperty("Minor_Version", "Minor Version", "Integer", String.valueOf(doc.get_MinorVersionNumber()));
		
		
		if(dateCreated.after(new Date(0L))) {
			insertStatement.setTimestamp(5, new java.sql.Timestamp(dateCreated.getTime()));
			documentXML.addProperty("Date_Created", "Date Created", "DateTime", dateCreated.toString());
							

		} else {
			log.error(String.format("Incorrect Date_Created value, set to null : %s, %s, %s", doc.get_Id().toString(), classSymbolicName, dateCreated.toString()));
			insertStatement.setTimestamp(5, null);
		}

		if(dateLastModified.after(new Date(0L))) {
			insertStatement.setTimestamp(6, new java.sql.Timestamp(dateLastModified.getTime()));
			documentXML.addProperty("Date_Last_Modified", "Date Last Modified", "DateTime", dateLastModified.toString());
			
		} else {
			log.error(String.format("Incorrect Date_Last_Modified value, set to null : %s, %s, %s", doc.get_Id().toString(), classSymbolicName, dateLastModified.toString()));
			insertStatement.setTimestamp(6, null);
		}
		

		insertStatement.setString(8, classSymbolicName);
		documentXML.addProperty("Class_Symbolic_Name", "Class Name", "String", classSymbolicName);
		
		String docTitle = docProperties.getStringValue("DocumentTitle");
		insertStatement.setString(9, docTitle);
		documentXML.addProperty("Document_Title", "Document Title", "String", docTitle);
					
		String mimeType = doc.get_MimeType();
		insertStatement.setString(10, mimeType);
		documentXML.addProperty("MIME_Type", "MIME Type", "String", mimeType);
		
		StorageArea sa = doc.get_StorageArea();
		sa.fetchProperties(new String[] {"DisplayName"});
		String storageAreaName = sa.get_DisplayName();
		insertStatement.setString(12, storageAreaName);
		documentXML.addProperty("STORAGE_AREA", "Storage Area", "String", storageAreaName);
		
		Integer pos = sysProperties.length + 1;
		for (String s: singleValuedProperties) {
			Object docProperty= docProperties.getObjectValue(s);
			String dataType = propertyDefintion.get(s).get("dataType");
			String displayName = propertyDefintion.get(s).get("displayName");
			String propertyValueStr=null;
//				System.out.println("data type :" + s+":"+dataType);
			if (docProperty == null) {
				insertStatement.setObject(pos , null);
			} else if ("Id".equalsIgnoreCase(dataType)) {
				insertStatement.setString(pos,propertyValueStr);
				propertyValueStr = ((Id)docProperty).toString();
			} else if ("DateTime".equalsIgnoreCase(dataType)) {
				Date dateValue = ((Date)docProperty);
				propertyValueStr =dateValue.toString();
				insertStatement.setDate(pos, new java.sql.Date(dateValue.getTime()));
			} else if ("Integer".equalsIgnoreCase(dataType)) {
				Integer intValue =  ((Integer)docProperty);
				propertyValueStr = String.valueOf(intValue);
				insertStatement.setInt(pos, intValue);
			} else if ("Double".equalsIgnoreCase(dataType)) {
				Double doubleValue = (Double)docProperty;
				propertyValueStr = String.valueOf(doubleValue);
				insertStatement.setDouble(pos, doubleValue);						
			} else if ("Boolean".equalsIgnoreCase(dataType)) {						
				Boolean boolValue = (Boolean)docProperty;
				propertyValueStr = String.valueOf(boolValue);
				insertStatement.setBoolean(pos, boolValue);
			}  else if ("String".equalsIgnoreCase(dataType)) {						
				propertyValueStr = (String)docProperty;
				insertStatement.setString(pos, propertyValueStr);
			}
			
			documentXML.addProperty(s, displayName, dataType, propertyValueStr);														
			pos++;
		}
		
		if(securityPolicy!=null) {

			insertStatement.setString(7, securityPolicy.get_Id().toString());
			insertStatement.setString(11, securityPolicy.get_DisplayName());
			documentXML.setSecurity(securityPolicy.get_DisplayName());
		} 

		return insertStatement;
		
	}
		
	
	private void addMultiValuedProperty(Document doc, String propertySymbolicName) throws SQLException {
		
		String insertString= "INSERT INTO MULTI_VALUED_PROPERTY (OWNER_OBJECT_ID, SYMBOLIC_NAME, TYPE, VALUE) VALUES (UUID_TO_BIN(?), ?, ?, ?)";
		PreparedStatement insertStatement = conn.prepareStatement(insertString);
		
		Properties properties = doc.getProperties();
		String propertyClassName = properties.get(propertySymbolicName).getClass().getSimpleName();
		String displayName = propertyDefintion.get(propertySymbolicName).get("displayName");
		if("PropertyStringListImpl".equalsIgnoreCase(propertyClassName)) {
						
			StringList strngList = properties.getStringListValue(propertySymbolicName);
			Iterator<String> it = strngList.iterator();
			while(it.hasNext()) {
				String str = it.next();
				insertStatement.setString(1, doc.get_Id().toString());
				insertStatement.setString(2, propertySymbolicName);
				insertStatement.setInt(3, 8);
				insertStatement.setString(4, str);

				insertStatement.addBatch();
				documentXML.addProperty(propertySymbolicName, displayName, "String", str);				
			}
			
			insertStatement.executeBatch();
			insertStatement.close();
		}
				

	}
	private void addContent(Document doc) throws SQLException {
		if (doc.get_ContentElements().iterator().hasNext()) {
			
			String queryString= "INSERT INTO CONTENT (OBJECT_ID, DOCUMENT_OBJECT_ID, ESN, CONTENT_TYPE, RETRIEVAL_NAME, CONTENT) VALUES (UUID_TO_BIN(UUID()), UUID_TO_BIN(?), ?, ?, ? , ?)";
			PreparedStatement insertStatement = conn.prepareStatement(queryString);
			
			ContentElementList cel = doc.get_ContentElements();
			Iterator<ContentTransfer> it = cel.iterator();
			while(it.hasNext()) {
				ContentTransfer ct = it.next();
				doc.fetchProperties(new String[] {"RetrievalName","ElementSequenceNumber"});
				try {
					InputStream is = ct.accessContentStream();
					String filePath = this.docSubDir + File.separator + "contents" /*+ File.separator + doc.get_Id().toString() + "_" + ct.get_RetrievalName()*/;
					Files.createDirectories(Paths.get(filePath));
					File file = new File(filePath + File.separator + doc.get_Id().toString() + "_" + ct.get_RetrievalName());
					FileOutputStream os = new FileOutputStream(file, false);
		            int read;
		            byte[] bytes = new byte[8192];
		            while ((read = is.read(bytes)) != -1) {
		                os.write(bytes, 0, read);
		            }
		            os.close();
		            is.close();
//		            is = ct.accessContentStream();
					
					insertStatement.setString(1, doc.get_Id().toString());
					insertStatement.setInt(2, ct.get_ElementSequenceNumber());
					insertStatement.setString(3, ct.get_ContentType());
					insertStatement.setString(4, ct.get_RetrievalName());
//					insertStatement.setBinaryStream(5, is);
					insertStatement.setNull(5, java.sql.Types.NULL);
					insertStatement.addBatch();						
					documentXML.addContent(ct.get_ElementSequenceNumber(), file.getPath());
				} catch (EngineRuntimeException e) {
					if(e.getExceptionCode()==ExceptionCode.CONTENT_FCA_FILE_DOES_NOT_EXIST) {
						logExportError(doc, FNExportStatus.CONTENT_FCA_FILE_DOES_NOT_EXIST, e.getMessage());
					}
					else
						logExportError(doc, FNExportStatus.OTHER_ERROR, e.getMessage());

				} catch (IOException e) {
					logExportError(doc, FNExportStatus.EXPORT_CONTENT_ERROR, e.getMessage());
				}
			
			}
			try {
				insertStatement.executeBatch();
			} catch (BatchUpdateException e) {
				logExportError(doc, FNExportStatus.INSERT_CONTENT_ERROR, e.getMessage());
			}			
			insertStatement.close();		
		} else {
			logExportError(doc, FNExportStatus.NO_CONTENT_FILE, "No Content File" );

		}

	}
	
	
	private void addAnnotation(Document doc) throws SQLException, IOException {
		
		PreparedStatement addAnnotStatement = conn.prepareStatement("INSERT INTO ANNOTATION (OBJECT_ID,ANNOTATED_OBJECT_ID,ANNOTATED_CONTENT_ELEMENT, DATE_CREATED, DATE_LAST_MODIFIED) VALUES (UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,?)");		
		AnnotationSet annotSet =  doc.get_Annotations();
		
		Iterator<Annotation> annotIt = annotSet.iterator();
		while (annotIt.hasNext()) {
			Annotation annot = annotIt.next();
			annot.fetchProperties(new String[] {"Id","AnnotatedObject","AnnotatedContentElement", "DateCreated","DateLastModified","ContentElements","ElementSequenceNumber","ContentType","RetrievalName"});			
			com.filenet.api.property.Properties annotProperties = annot.getProperties();
			addAnnotStatement.setString(1, annot.get_Id().toString());
			addAnnotStatement.setString(2, annotProperties.getIdValue("AnnotatedObject").toString());
			addAnnotStatement.setInt(3, annot.get_AnnotatedContentElement());
			addAnnotStatement.setTimestamp(4, new java.sql.Timestamp(annot.get_DateCreated().getTime()));
			addAnnotStatement.setTimestamp(5, new java.sql.Timestamp(annot.get_DateLastModified().getTime()));
			addAnnotStatement.execute();
			
			PreparedStatement addAnnotContentStatement = conn.prepareStatement("INSERT INTO  DOCUMENT_DB.ANNOT_CONTENT  (OBJECT_ID, ANNOT_OBJECT_ID, ESN, CONTENT_TYPE, RETRIEVAL_NAME, CONTENT) VALUES (UUID_TO_BIN(UUID()), UUID_TO_BIN(?), ?, ?, ? , ?)");		
			
			ContentElementList annotCEL = annot.get_ContentElements();
			Iterator<ContentTransfer> it1 = annotCEL.iterator();
			while(it1.hasNext()) {
				String filePath = this.docSubDir + File.separator + "annoations";
				Files.createDirectories(Paths.get(filePath));
				ContentTransfer ct = it1.next();
				File file = new File(filePath + File.separator + annot.get_Id().toString() + "_" + ct.get_RetrievalName());
				InputStream is = null;
				FileOutputStream os = null;
				try {
					is = ct.accessContentStream();					

					os = new FileOutputStream(file, false);
					
		            int read;
		            byte[] bytes = new byte[8192];
		            while ((read = is.read(bytes)) != -1) {
		                os.write(bytes, 0, read);
		            }
		            os.close();
		            is.close();
				} catch (EngineRuntimeException e) {
					if(e.getExceptionCode()==ExceptionCode.CONTENT_FCA_FILE_DOES_NOT_EXIST)
						logExportError(doc, FNExportStatus.EXPORT_ANNOTATION_ERROR, e.getMessage());
						
				} catch (IOException e) {
					logExportError(doc, FNExportStatus.EXPORT_ANNOTATION_ERROR, e.getMessage());
				}


				addAnnotContentStatement.setString(1, annot.get_Id().toString());
				addAnnotContentStatement.setInt(2, ct.get_ElementSequenceNumber());
				addAnnotContentStatement.setString(3, ct.get_ContentType());
				addAnnotContentStatement.setString(4, ct.get_RetrievalName());
//				addAnnotContentStatement.setBinaryStream(5, ct.accessContentStream());
				addAnnotContentStatement.setNull(5, java.sql.Types.NULL);
				addAnnotContentStatement.addBatch();
				documentXML.addAnnotation(annot.get_Id(), annot.get_DateCreated(), annot.get_DateLastModified(), annot.get_AnnotatedContentElement(), file.getPath());
			}
			try {
				addAnnotContentStatement.executeBatch();
			} catch (BatchUpdateException e) {
				logExportError(doc, FNExportStatus.INSERT_ANNOATION_ERROR, e.getMessage());

			}
		}
		addAnnotStatement.close();
	}
	private void addContainer(Document doc) throws SQLException {
	
		PreparedStatement addFolderStatement = conn.prepareStatement("INSERT INTO CONTAINER (OBJECT_ID,CONTAINEE_OBJECT_ID) VALUES (UUID_TO_BIN(?),UUID_TO_BIN(?))");		
		doc.fetchProperties(new String[] {"FoldersFiledIn"});
		FolderSet folderSet =  doc.get_FoldersFiledIn();
		Iterator<Folder> folderIt = folderSet.iterator();
		while (folderIt.hasNext()) {
			Folder folder = folderIt.next();
			folder.fetchProperties(new String[] {"Id","PathName", "Parent"});
			addFolderStatement.setString(1, folder.get_Id().toString());
			addFolderStatement.setString(2, doc.get_Id().toString());
			addFolderStatement.execute();
			documentXML.addFolder(folder.get_PathName());
		}
		addFolderStatement.close();
	}
	
	private String getDocSubDir(Document doc) {
		return doc.get_Id().toString();
	}
	
	private void updateDocumentExportStatus(Document doc, Integer exportStatusCOde) throws SQLException{
		String updateString = "UPDATE DOCUMENT SET EXPORT_STATUS=EXPORT_STATUS | ? WHERE OBJECT_ID=UUID_TO_BIN(?)";
		PreparedStatement updateStatement = conn.prepareCall(updateString);
		updateStatement.setInt(1, exportStatusCOde);
		updateStatement.setString(2, doc.get_Id().toString());
		updateStatement.execute();
	}
	
	private void logExportError(Document doc, Integer errorCode, String errorMeesage) throws SQLException {
		log.error(String.format("%s,%d,%s,%s",doc.get_Id().toString(),errorCode ,doc.get_StorageArea().get_DisplayName(), errorMeesage));
		updateDocumentExportStatus(doc,errorCode);
		
	}
}

