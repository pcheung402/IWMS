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
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
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
import com.fn.util.FNUtilLogger;
import com.migrate.abs.BulkOperationThread;

public class ExportImpl extends BulkOperationThread {
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
//	protected HashMap<String, String> propertyDefintion;
//	protected HashMap<Integer, String> classNumToSymNameMap = new HashMap<Integer, String>();
//	protected HashMap<Integer, ArrayList<String>> classIndexMap = new HashMap<Integer, ArrayList<String>>();
//	protected String[] isSystemProperties = {"F_ARCHIVEDATE","F_DELETEDATE","F_DOCCLASSNUMBER","F_DOCFORMAT","F_DOCLOCATION","F_DOCNUMBER","F_DOCTYPE","F_ENTRYDATE","F_PAGES","F_RETENTOFFSET"};
//
	
	DocumentBuilderFactory factory;
	DocumentBuilder builder;
	org.w3c.dom.Document xmlDoc;
	Element docNode;
	Element propertiesNode;
	Element contentsNode;
	String docSubDir;
	public ExportImpl(String batchBaseDir, Document doc, FNUtilLogger log, /*FileOutputStream ofs,*/ CPEUtil cpeUtil, HashMap<String, List<String>> classPropertiesMap, HashMap<String, String> propertyDefintion, String mode) {
		super(batchBaseDir, doc, log, /*ofs,*/ cpeUtil,classPropertiesMap, propertyDefintion, mode);
		// TODO Auto-generated constructor stub
		this.docSubDir = this.batchBaseDir + File.separator + "documents" + File.separator + getDocSubDir(doc.get_Id().toString());
		
	}
	

	public void processBatchItem(Document doc) throws FNUtilException, IOException, SQLException {
		
		try {
			this.factory = DocumentBuilderFactory.newInstance();
			this.builder = factory.newDocumentBuilder();
			this.xmlDoc = builder.newDocument();
			this.docNode = this.xmlDoc.createElement("Document");
		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		ClassDescription cd = doc.get_ClassDescription();
		String classSymbolicName = cd.get_SymbolicName();

		if(createDocument(doc)) {
//			System.out.println("document created");
			addContent(doc);
			addAnnotation(doc);
			addContainer(doc);
//			bulkOperationOutputDataFile.write(String.format("%s,%s,%s\n",doc.get_Name(), doc.get_Id().toString(), classSymbolicName).getBytes());
//			bulkOperationOutputDataFile.flush();	       
			this.xmlDoc.appendChild(docNode);
			try {
				TransformerFactory transformerFactory = TransformerFactory.newInstance();
				Transformer transformer = transformerFactory.newTransformer();
		        DOMSource source = new DOMSource(this.xmlDoc);
//		        String xmlFilePath = "." + File.separator + "data" + File.separator + "bulkOutput" + File.separator + doc.get_Id().toString() +".xml";
//		        String xmlFilePath = this.batchBaseDir + File.separator + "documents" + File.separator + doc.get_Id().toString();
		        Files.createDirectories(Paths.get(this.batchBaseDir));
		        FileOutputStream output = new FileOutputStream(docSubDir + File.separator + "properties.xml");
		        StreamResult result = new StreamResult(output);
		        transformer.transform(source, result);			
			
			} catch (TransformerException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		
	}
	
	
	
	private Boolean createDocument(Document doc) throws SQLException {
		PreparedStatement deleteDocumentStatement = conn.prepareStatement("DELETE FROM DOCUMENT_DB.DOCUMENT OBJECT_ID=UUID_TO_BIN(?)");
		deleteDocumentStatement.setString(1, doc.get_Id().toString());
		deleteDocumentStatement.execute();
		Boolean result = Boolean.FALSE;
		propertiesNode = this.xmlDoc.createElement("Properties");
		ClassDescription cd = doc.get_ClassDescription();
		String classSymbolicName = cd.get_SymbolicName();
		PreparedStatement insertStatement = pepareInsertStatement(doc, classSymbolicName);
		
		if(insertStatement!=null) {
			insertStatement.execute();
			insertStatement.close();
			
			String[] propertiesNameArray = classPropertiesMap.get(classSymbolicName).toArray(new String[0]);
			
			for (int i = 0; i < propertiesNameArray.length; ++i) {
				String dataType = propertyDefintion.get(propertiesNameArray[i]);
				if("StringList".equalsIgnoreCase(dataType)||
						"DateList".equalsIgnoreCase(dataType)||
						"IdList".equalsIgnoreCase(dataType)||
						"IntegerList".equalsIgnoreCase(dataType)||
						"DoubleList".equalsIgnoreCase(dataType)||
						"StringList".equalsIgnoreCase(dataType)
						) {

					addMultiValuedProperty(doc, propertiesNameArray[i]);
				}
			}
			

			result = Boolean.TRUE;
		}
		return result;
	}
	
	private PreparedStatement pepareInsertStatement(Document doc, String classSymbolicName) throws SQLException {
		
		String[] propertiesNameArray = classPropertiesMap.get(classSymbolicName).toArray(new String[0]);
		ArrayList<String>  singleValuedProperties = new ArrayList<String>();
		ArrayList<String>  multiValuedProperties = new ArrayList<String>();
		for (int i = 0; i < propertiesNameArray.length; ++i) {
			String dataType = propertyDefintion.get(propertiesNameArray[i]);
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
		
		if(classPropertiesMap.get(classSymbolicName)==null) {
			log.error(String.format("Invalid Document Class : %s,%s", doc.get_Id().toString(), classSymbolicName));
			return null;
		} else {

			doc.fetchProperties(propertiesNameArray);
			Properties docProperties = doc.getProperties();
			Id vsId = doc.get_VersionSeries().get_Id();
			SecurityPolicy securityPolicy = doc.get_SecurityPolicy();
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
					"MIME_TYPE"	
			};

			String queryString= "REPLACE INTO DOCUMENT_DB.DOCUMENT ("
//					+ "OBJECT_ID,OBJECT_VSID,MAJOR_VER,MINOR_VER,DATE_CREATED,DATE_LAST_MODIFIED,SECURITY_POLICY,CLASS_SYMBOLIC_NAME,DOCUMENTTITLE,MIME_TYPE"
					+ String.join(",", sysProperties)
					+ "," 
					+ String.join(",", singleValuedProperties)									
					+ ") VALUES (UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,?,?,UUID_TO_BIN(?),?,?,?";
			
//			queryString += ",?".repeat(singleValuedProperties.size());
			for (String s:singleValuedProperties) {
				queryString += ",?";
			}
		
		
			queryString += ")";
			
			PreparedStatement insertStatement = conn.prepareCall(queryString);
			
			insertStatement.setString(1, doc.get_Id().toString());
			
			Element idPropertyNode = this.xmlDoc.createElement("Property");
			Element idNameNode = this.xmlDoc.createElement("Name");
			idNameNode.setTextContent("Id");
			idPropertyNode.appendChild(idNameNode);
			
			Element idTypeNode = this.xmlDoc.createElement("Type");
			idTypeNode.setTextContent("Id");
			idPropertyNode.appendChild(idTypeNode);
			
			Element idValueNode = this.xmlDoc.createElement("Value");
			idValueNode.setTextContent(doc.get_Id().toString());
			idPropertyNode.appendChild(idValueNode);
			
			propertiesNode.appendChild(idPropertyNode);
			
			insertStatement.setString(2, vsId.toString());
			
			Element vsIdPropertyNode = this.xmlDoc.createElement("Property");
			Element vsIdNameNode = this.xmlDoc.createElement("Name");
			vsIdNameNode.setTextContent("vsId");
			vsIdPropertyNode.appendChild(vsIdNameNode);
			
			Element vsIdTypeNode = this.xmlDoc.createElement("Type");
			vsIdTypeNode.setTextContent("Id");
			vsIdPropertyNode.appendChild(vsIdTypeNode);
			
			Element vsIdValueNode = this.xmlDoc.createElement("Value");
			vsIdValueNode.setTextContent(doc.get_VersionSeries().get_Id().toString());
			vsIdPropertyNode.appendChild(vsIdValueNode);
			
			propertiesNode.appendChild(vsIdPropertyNode);
			
			insertStatement.setInt(3, doc.get_MajorVersionNumber());
			
			Element majorVerPropertyNode = this.xmlDoc.createElement("Property");
			Element majorVerNameNode = this.xmlDoc.createElement("Name");
			majorVerNameNode.setTextContent("Major_Version");
			majorVerPropertyNode.appendChild(majorVerNameNode);
			
			Element majorVerTypeNode = this.xmlDoc.createElement("Type");
			majorVerTypeNode.setTextContent("Integer");
			majorVerPropertyNode.appendChild(majorVerTypeNode);
			
			Element majorVerValueNode = this.xmlDoc.createElement("Value");
			majorVerValueNode.setTextContent(String.valueOf(doc.get_MajorVersionNumber()));
			majorVerPropertyNode.appendChild(majorVerValueNode);
			
			propertiesNode.appendChild(majorVerPropertyNode);
			
			insertStatement.setInt(4, doc.get_MinorVersionNumber());
			
			Element minorVerPropertyNode = this.xmlDoc.createElement("Property");
			Element minorVerNameNode = this.xmlDoc.createElement("Name");
			minorVerNameNode.setTextContent("Minor_Version");
			minorVerPropertyNode.appendChild(minorVerNameNode);
			
			Element minorVerTypeNode = this.xmlDoc.createElement("Type");
			minorVerTypeNode.setTextContent("Integer");
			minorVerPropertyNode.appendChild(minorVerTypeNode);
			
			Element minorVerValueNode = this.xmlDoc.createElement("Value");
			minorVerValueNode.setTextContent(String.valueOf(doc.get_MinorVersionNumber()));
			minorVerPropertyNode.appendChild(minorVerValueNode);

			propertiesNode.appendChild(minorVerPropertyNode);
			
			if(dateCreated.after(new Date(0L))) {
				insertStatement.setDate(5, new java.sql.Date(dateCreated.getTime()));
								
				Element dateCreatedPropertyNode = this.xmlDoc.createElement("Property");
				Element dateCreatedNameNode = this.xmlDoc.createElement("Name");
				dateCreatedNameNode.setTextContent("Date_Created");
				dateCreatedPropertyNode.appendChild(dateCreatedNameNode);
				
				Element dateCreatedTypeNode = this.xmlDoc.createElement("Type");
				dateCreatedTypeNode.setTextContent("DateTime");
				dateCreatedPropertyNode.appendChild(dateCreatedTypeNode);
				
				Element dateCreatedValueNode = this.xmlDoc.createElement("Value");
				dateCreatedValueNode.setTextContent(dateCreated.toString());
				dateCreatedPropertyNode.appendChild(dateCreatedValueNode);
				
				propertiesNode.appendChild(dateCreatedPropertyNode);
			} else {
				log.error(String.format("Incorrect Date_Created value, set to null : %s, %s, %s", doc.get_Id().toString(), classSymbolicName, dateCreated.toString()));
				insertStatement.setDate(5, null);
			}

			if(dateLastModified.after(new Date(0L))) {
				insertStatement.setDate(6, new java.sql.Date(dateLastModified.getTime()));
				
				Element dateLastModifiedPropertyNode = this.xmlDoc.createElement("Property");
				Element dateLastModifiedNameNode = this.xmlDoc.createElement("Name");
				dateLastModifiedNameNode.setTextContent("Date_Last_Modified");
				dateLastModifiedPropertyNode.appendChild(dateLastModifiedNameNode);
				
				Element dateLastModifiedTypeNode = this.xmlDoc.createElement("Type");
				dateLastModifiedTypeNode.setTextContent("DateTime");
				dateLastModifiedPropertyNode.appendChild(dateLastModifiedTypeNode);
				
				Element dateLastModifiedValueNode = this.xmlDoc.createElement("Value");
				dateLastModifiedValueNode.setTextContent(dateLastModified.toString());
				dateLastModifiedPropertyNode.appendChild(dateLastModifiedValueNode);
				
				propertiesNode.appendChild(dateLastModifiedPropertyNode);
			} else {
				log.error(String.format("Incorrect Date_Last_Modified value, set to null : %s, %s, %s", doc.get_Id().toString(), classSymbolicName, dateLastModified.toString()));
				insertStatement.setDate(6, null);
			}
			

			insertStatement.setString(8, classSymbolicName);			
			
			Element symNamePropertyNode = this.xmlDoc.createElement("Property");
			Element symNameNode = this.xmlDoc.createElement("Name");
			symNameNode.setTextContent("Class_Symbolic_Name");
			symNamePropertyNode.appendChild(symNameNode);
			
			Element symNameTypeNode = this.xmlDoc.createElement("Type");
			symNameTypeNode.setTextContent("String");
			symNamePropertyNode.appendChild(symNameTypeNode);
			
			Element symNameValueNode = this.xmlDoc.createElement("Value");
			symNameValueNode.setTextContent(classSymbolicName);
			symNamePropertyNode.appendChild(symNameValueNode);
			
			propertiesNode.appendChild(symNamePropertyNode);
			
			String docTitle = docProperties.getStringValue("DocumentTitle");
			insertStatement.setString(9, docTitle);
			
			Element docTitlePropertyNode = this.xmlDoc.createElement("Property");
			Element docTitleNode = this.xmlDoc.createElement("Name");
			docTitleNode.setTextContent("Document_Title");
			docTitlePropertyNode.appendChild(docTitleNode);
			
			Element docTitleTypeNode = this.xmlDoc.createElement("Type");
			docTitleTypeNode.setTextContent("String");
			docTitlePropertyNode.appendChild(docTitleTypeNode);
			
			Element docTitleValueNode = this.xmlDoc.createElement("Value");
			docTitleValueNode.setTextContent(docTitle);
			docTitlePropertyNode.appendChild(docTitleValueNode);
			
			propertiesNode.appendChild(docTitlePropertyNode);
			
			String mimeType = doc.get_MimeType();
			insertStatement.setString(10, mimeType);
			
			Element mimePropertyNode = this.xmlDoc.createElement("Property");
			Element mimeNode = this.xmlDoc.createElement("Name");
			mimeNode.setTextContent("MIME_Type");
			mimePropertyNode.appendChild(mimeNode);
			
			Element mimeTypeNode = this.xmlDoc.createElement("Type");
			mimeTypeNode.setTextContent("String");
			mimePropertyNode.appendChild(mimeTypeNode);
			
			Element mimeValueNode = this.xmlDoc.createElement("Value");
			mimeValueNode.setTextContent(mimeType);
			mimePropertyNode.appendChild(mimeValueNode);
			
			propertiesNode.appendChild(mimePropertyNode);
			
//			System.out.println(queryString);
//			System.out.println("***" + classSymbolicName);
			Integer pos = 11;
			for (String s: singleValuedProperties) {
				Object docProperty= docProperties.getObjectValue(s);
				String dataType = propertyDefintion.get(s);
//				System.out.println("data type :" + s+":"+dataType);
//				System.out.println(s);
				if (docProperty == null) {
					insertStatement.setObject(pos , null);
				} else if ("Id".equalsIgnoreCase(dataType)) {
					insertStatement.setString(pos, ((Id)docProperty).toString());
										
					Element propertyNode = this.xmlDoc.createElement("Property");
					Element nameNode = this.xmlDoc.createElement("Name");
					nameNode.setTextContent(s);
					propertyNode.appendChild(nameNode);
					
					Element typeNode = this.xmlDoc.createElement("Type");
					typeNode.setTextContent("Id");
					propertyNode.appendChild(typeNode);
					
					Element valueNode = this.xmlDoc.createElement("Value");
					valueNode.setTextContent(((Id)docProperty).toString());
					propertyNode.appendChild(valueNode);
					
					propertiesNode.appendChild(propertyNode);
					
				} else if ("DateTime".equalsIgnoreCase(dataType)) {
					Date dateValue = ((Date)docProperty);
					if(dateValue.after(new Date(0L))) {
						insertStatement.setDate(pos, new java.sql.Date(dateValue.getTime()));
																
						Element propertyNode = this.xmlDoc.createElement("Property");
						Element nameNode = this.xmlDoc.createElement("Name");
						nameNode.setTextContent(s);
						propertyNode.appendChild(nameNode);
						
						Element typeNode = this.xmlDoc.createElement("Type");
						typeNode.setTextContent("DateTime");
						propertyNode.appendChild(typeNode);
						
						Element valueNode = this.xmlDoc.createElement("Value");
						valueNode.setTextContent(dateValue.toString());
						propertyNode.appendChild(valueNode);
						
						propertiesNode.appendChild(propertyNode);

					} else {
						log.error(String.format("Incorrect datetime value, set to null : %s, %s, %s, %s", doc.get_Id().toString(), classSymbolicName, s ,(Date)docProperty).toString());
						insertStatement.setDate(pos, null);
					}
				} else if ("Integer".equalsIgnoreCase(dataType)) {
					Integer intValue =  ((Integer)docProperty);
					insertStatement.setInt(pos, intValue);
					
					Element propertyNode = this.xmlDoc.createElement("Property");
					Element nameNode = this.xmlDoc.createElement("Name");
					nameNode.setTextContent(s);
					propertyNode.appendChild(nameNode);
					
					Element typeNode = this.xmlDoc.createElement("Type");
					typeNode.setTextContent("Integer");
					propertyNode.appendChild(typeNode);
					
					Element valueNode = this.xmlDoc.createElement("Value");
					valueNode.setTextContent(String.valueOf(intValue));
					propertyNode.appendChild(valueNode);
					
					propertiesNode.appendChild(propertyNode);

				} else if ("Double".equalsIgnoreCase(dataType)) {
					Double doubleValue = (Double)docProperty;
					insertStatement.setDouble(pos, doubleValue);
					
					Element propertyNode = this.xmlDoc.createElement("Property");
					Element nameNode = this.xmlDoc.createElement("Name");
					nameNode.setTextContent(s);
					propertyNode.appendChild(nameNode);
					
					Element typeNode = this.xmlDoc.createElement("Type");
					typeNode.setTextContent("Double");
					propertyNode.appendChild(typeNode);
					
					Element valueNode = this.xmlDoc.createElement("Value");
					valueNode.setTextContent(String.valueOf(doubleValue));
					propertyNode.appendChild(valueNode);
					
					propertiesNode.appendChild(propertyNode);

				} else if ("String".equalsIgnoreCase(dataType)) {
					String strValue = (String)docProperty;
					insertStatement.setString(pos, strValue);

					
					Element propertyNode = this.xmlDoc.createElement("Property");
					Element nameNode = this.xmlDoc.createElement("Name");
					nameNode.setTextContent(s);
					propertyNode.appendChild(nameNode);
					
					Element typeNode = this.xmlDoc.createElement("Type");
					typeNode.setTextContent("String");
					propertyNode.appendChild(typeNode);
					
					Element valueNode = this.xmlDoc.createElement("Value");
					valueNode.setTextContent(strValue);
					propertyNode.appendChild(valueNode);
					
					propertiesNode.appendChild(propertyNode);

				} else if ("Boolean".equalsIgnoreCase(dataType)) {
					
					Boolean boolValue = (Boolean)docProperty;
					insertStatement.setBoolean(pos, boolValue);
//					System.out.println("### " + s +"," + boolValue.toString());
					Element propertyNode = this.xmlDoc.createElement("Property");
					Element nameNode = this.xmlDoc.createElement("Name");
					nameNode.setTextContent(s);
					propertyNode.appendChild(nameNode);
					
					Element typeNode = this.xmlDoc.createElement("Type");
					typeNode.setTextContent("Boolean");
					propertyNode.appendChild(typeNode);
					
					Element valueNode = this.xmlDoc.createElement("Value");
					valueNode.setTextContent(String.valueOf(boolValue));
					propertyNode.appendChild(valueNode);
					
					propertiesNode.appendChild(propertyNode);
					
				}

				pos++;
			}

			this.docNode.appendChild(propertiesNode);
			
			if(securityPolicy!=null) {

				insertStatement.setString(7, securityPolicy.get_Id().toString());
				Element securityPolicyNode = this.xmlDoc.createElement("Security");
				securityPolicyNode.setTextContent(securityPolicy.get_Name());
				docNode.appendChild(securityPolicyNode);
			} else {
				insertStatement.setString(7, null);
			}
//		System.out.println(insertStatement.toString());
		return insertStatement;
		}
	}
		
	
	private void addMultiValuedProperty(Document doc, String propertySymbolicName) throws SQLException {
//		PreparedStatement deleteContentStatement = conn.prepareStatement("DELETE FROM DOCUMENT_DB.MULTI_VALUED_PROPERTY WHERE OWNER_OBJECT_ID=UUID_TO_BIN(?)");
//		deleteContentStatement.setString(1, doc.get_Id().toString());
//		deleteContentStatement.execute();
		
		
		String insertString= "INSERT INTO DOCUMENT_DB.MULTI_VALUED_PROPERTY (OWNER_OBJECT_ID, SYMBOLIC_NAME, TYPE, VALUE) VALUES (UUID_TO_BIN(?), ?, ?, ?)";
		PreparedStatement insertStatement = conn.prepareStatement(insertString);
		
		Properties properties = doc.getProperties();
		String propertyClassName = properties.get(propertySymbolicName).getClass().getSimpleName();
		if("PropertyStringListImpl".equalsIgnoreCase(propertyClassName)) {

			Element propertyNode = this.xmlDoc.createElement("Property");
			Element nameNode = this.xmlDoc.createElement("Name");
			nameNode.setTextContent(propertySymbolicName);
			propertyNode.appendChild(nameNode);
			
			Element typeNode = this.xmlDoc.createElement("Type");
			typeNode.setTextContent("String");
			propertyNode.appendChild(typeNode);						
			StringList strngList = properties.getStringListValue(propertySymbolicName);
			Iterator<String> it = strngList.iterator();
			while(it.hasNext()) {
				String str = it.next();
				insertStatement.setString(1, doc.get_Id().toString());
				insertStatement.setString(2, propertySymbolicName);
				insertStatement.setInt(3, 8);
				insertStatement.setString(4, str);
//				System.out.println(insertStatement.toString());
				insertStatement.addBatch();
//				insertStatement.executeUpdate();
				
				Element valueNode = this.xmlDoc.createElement("Value");
				valueNode.setTextContent(str);
				propertyNode.appendChild(valueNode);				
			}
			insertStatement.executeBatch();
			insertStatement.close();
//			try {
//				Thread.sleep(10000);
//			} catch (InterruptedException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
			propertiesNode.appendChild(propertyNode);
		}
				

	}
	private void addContent(Document doc) throws SQLException, IOException {
//		this.contentsNode = this.xmlDoc.createElement("Contents");
//		PreparedStatement deleteContentStatement = conn.prepareStatement("DELETE FROM DOCUMENT_DB.CONTENT WHERE DOCUMENT_OBJECT_ID=UUID_TO_BIN(?)");
//		deleteContentStatement.setString(1, doc.get_Id().toString());
//		deleteContentStatement.execute();
		
		String queryString= "INSERT INTO DOCUMENT_DB.CONTENT (OBJECT_ID, DOCUMENT_OBJECT_ID, ESN, CONTENT_TYPE, RETRIEVAL_NAME, CONTENT) VALUES (UUID_TO_BIN(UUID()), UUID_TO_BIN(?), ?, ?, ? , ?)";
		PreparedStatement insertStatement = conn.prepareStatement(queryString);
		
		ContentElementList cel = doc.get_ContentElements();
		Iterator<ContentTransfer> it = cel.iterator();
		while(it.hasNext()) {
			ContentTransfer ct = it.next();
			doc.fetchProperties(new String[] {"RetrievalName","ElementSequenceNumber"});
			InputStream is = ct.accessContentStream();
			
//			String filePath = "." + File.separator + "data" + File.separator + "bulkOutput" + File.separator + doc.get_Id().toString() + "_" + ct.get_RetrievalName();
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
            is = ct.accessContentStream();
			Element content = this.xmlDoc.createElement("Content");
			Element esn = this.xmlDoc.createElement("ESN");
			esn.setTextContent(String.valueOf(ct.get_ElementSequenceNumber()));
			Element annot_path = this.xmlDoc.createElement("Path");
			annot_path.setTextContent(file.getPath());
			content.appendChild(esn);
			content.appendChild(annot_path);
			this.contentsNode.appendChild(content);
			
			insertStatement.setString(1, doc.get_Id().toString());
			insertStatement.setInt(2, ct.get_ElementSequenceNumber());
			insertStatement.setString(3, ct.get_ContentType());
			insertStatement.setString(4, ct.get_RetrievalName());
			insertStatement.setBinaryStream(5, is);
			insertStatement.addBatch();
			


			
		}
		insertStatement.executeBatch();
		insertStatement.close();
		this.docNode.appendChild(contentsNode);
	}
	
	
	private void addAnnotation(Document doc) throws SQLException, IOException {
		Element annotationsNode = this.xmlDoc.createElement("Annotations");
//		PreparedStatement deleteAnnotStatement = conn.prepareStatement("DELETE FROM DOCUMENT_DB.ANNOTATION WHERE ANNOTATED_OBJECT_ID=UUID_TO_BIN(?)");
//		deleteAnnotStatement.setString(1, doc.get_Id().toString());
//		deleteAnnotStatement.execute();
//		deleteAnnotStatement.close();
		
		
		PreparedStatement addAnnotStatement = conn.prepareStatement("INSERT INTO DOCUMENT_DB.ANNOTATION (OBJECT_ID,ANNOTATED_OBJECT_ID,ANNOTATED_CONTENT_ELEMENT, DATE_CREATED, DATE_LAST_MODIFIED) VALUES (UUID_TO_BIN(?),UUID_TO_BIN(?),?,?,?)");		
		AnnotationSet annotSet =  doc.get_Annotations();
		
		Iterator<Annotation> annotIt = annotSet.iterator();
		while (annotIt.hasNext()) {
			Element annotationNode = this.xmlDoc.createElement("Annotation");
			Annotation annot = annotIt.next();
			annot.fetchProperties(new String[] {"Id","AnnotatedObject","AnnotatedContentElement", "DateCreated","DateLastModified","ContentElements","ElementSequenceNumber","ContentType","RetrievalName"});			
			com.filenet.api.property.Properties annotProperties = annot.getProperties();
			addAnnotStatement.setString(1, annot.get_Id().toString());
			addAnnotStatement.setString(2, annotProperties.getIdValue("AnnotatedObject").toString());
			addAnnotStatement.setInt(3, annot.get_AnnotatedContentElement());
			addAnnotStatement.setDate(4, new java.sql.Date(annot.get_DateCreated().getTime()));
			addAnnotStatement.setDate(5, new java.sql.Date(annot.get_DateLastModified().getTime()));
			addAnnotStatement.execute();
//			PreparedStatement deleteAnnotContentStatement = conn.prepareStatement("DELETE FROM DOCUMENT_DB.ANNOT_CONTENT WHERE ANNOT_OBJECT_ID=UUID_TO_BIN(?)");
//			deleteAnnotContentStatement.setString(1, annot.get_Id().toString());
//			deleteAnnotContentStatement.execute();
//			deleteAnnotContentStatement.close();			
			PreparedStatement addAnnotContentStatement = conn.prepareStatement("INSERT INTO  DOCUMENT_DB.ANNOT_CONTENT  (OBJECT_ID, ANNOT_OBJECT_ID, ESN, CONTENT_TYPE, RETRIEVAL_NAME, CONTENT) VALUES (UUID_TO_BIN(UUID()), UUID_TO_BIN(?), ?, ?, ? , ?)");		

			Element idNode = this.xmlDoc.createElement("Id");
			idNode.setTextContent(annot.get_Id().toString());
			annotationNode.appendChild(idNode);
			
			Element dateCreatedNode = this.xmlDoc.createElement("Date_Created");
			dateCreatedNode.setTextContent(annot.get_DateCreated().toString());
			annotationNode.appendChild(dateCreatedNode);
			
			Element dateLastModifiedNode = this.xmlDoc.createElement("Date_Last_Modified");
			dateLastModifiedNode.setTextContent(annot.get_DateCreated().toString());
			annotationNode.appendChild(dateLastModifiedNode);
			
			Element esnNode = this.xmlDoc.createElement("ESN");
			esnNode.setTextContent(String.valueOf(annot.get_AnnotatedContentElement()));
			annotationNode.appendChild(esnNode);
			
			ContentElementList annotCEL = annot.get_ContentElements();
			Iterator<ContentTransfer> it1 = annotCEL.iterator();
			while(it1.hasNext()) {
				Element annotPathNode = this.xmlDoc.createElement("Annotation_Path");
				ContentTransfer ct = it1.next();

				String filePath = this.docSubDir + File.separator + "annoations";
				Files.createDirectories(Paths.get(filePath));
				File file = new File(filePath + File.separator + annot.get_Id().toString() + "_" + ct.get_RetrievalName());
				
//				String filePath = "." + File.separator + "data" + File.separator + "bulkOutput" + File.separator + "annotation_" + annot.get_Id().toString() +"_"+String.valueOf(ct.get_ElementSequenceNumber()+".xml");
//				File file = new File(filePath);
				FileOutputStream os = new FileOutputStream(file, false);
				InputStream is = ct.accessContentStream();
	            int read;
	            byte[] bytes = new byte[8192];
	            while ((read = is.read(bytes)) != -1) {
	                os.write(bytes, 0, read);
	            }
	            os.close();
	            is.close();
				
				addAnnotContentStatement.setString(1, annot.get_Id().toString());
				addAnnotContentStatement.setInt(2, ct.get_ElementSequenceNumber());
				addAnnotContentStatement.setString(3, ct.get_ContentType());
				addAnnotContentStatement.setString(4, ct.get_RetrievalName());
				addAnnotContentStatement.setBinaryStream(5, ct.accessContentStream());				
				addAnnotContentStatement.addBatch();
				annotPathNode.setTextContent(file.getPath());
				annotationNode.appendChild(annotPathNode);
			}			
			addAnnotContentStatement.executeBatch();
			annotationsNode.appendChild(annotationNode);
		}
		addAnnotStatement.close();
		this.docNode.appendChild(annotationsNode);
	}
	private void addContainer(Document doc) throws SQLException {
		Element foldersNode = this.xmlDoc.createElement("Folders");
//		PreparedStatement deleteFolderStatement = conn.prepareStatement("DELETE FROM DOCUMENT_DB.CONTAINER WHERE CONTAINEE_OBJECT_ID=UUID_TO_BIN(?)");
//		deleteFolderStatement.setString(1, doc.get_Id().toString());
//		deleteFolderStatement.execute();
//		deleteFolderStatement.close();
		
		
		PreparedStatement addFolderStatement = conn.prepareStatement("INSERT INTO DOCUMENT_DB.CONTAINER (OBJECT_ID,CONTAINEE_OBJECT_ID) VALUES (UUID_TO_BIN(?),UUID_TO_BIN(?))");		
		doc.fetchProperties(new String[] {"FoldersFiledIn"});
		FolderSet folderSet =  doc.get_FoldersFiledIn();
		Iterator<Folder> folderIt = folderSet.iterator();
		while (folderIt.hasNext()) {
			Folder folder = folderIt.next();
			folder.fetchProperties(new String[] {"Id","PathName", "Parent"});
			addFolderStatement.setString(1, folder.get_Id().toString());
			addFolderStatement.setString(2, doc.get_Id().toString());
			addFolderStatement.execute();
			
			Element folderNode = this.xmlDoc.createElement("Folder");
			folderNode.setTextContent(folder.get_PathName());
			foldersNode.appendChild(folderNode);
		}
		addFolderStatement.close();
		this.docNode.appendChild(foldersNode);
	}
	
	private String getDocSubDir(String docId) {
		return docId;
	}
	
}

