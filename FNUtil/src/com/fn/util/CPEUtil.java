package com.fn.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.*;
import java.nio.file.Paths;
import javax.security.auth.Subject;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.util.concurrent.*;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Element;

import com.filenet.api.collection.*;
import com.filenet.api.core.*;
import com.filenet.api.exception.EngineRuntimeException;
import com.filenet.api.exception.ExceptionCode;
import com.filenet.api.util.UserContext;
import com.filenet.api.admin.*;
import com.filenet.api.constants.*;
import com.filenet.api.replication.*;
import com.fn.util.*;

public class CPEUtil {
	private String CONF_FILE_DIR;
	private FNUtilLogger log;
	private final String CPEDomain = "CPEDomain";
	private final String CPEServer="CPEServer";
	private final String CPEPort="CPEPort";
	private final String CPEUser="CPEUser";
	private final String CPEPassword="CPEPassword";
	
	private Connection con;
	private Domain dom;
	private ObjectStoreSet ost;
	private ObjectStore os = null;
//	private StoragePolicySet sps;
//	private HashMap<String, FileStorageArea> saMap = new HashMap<String, FileStorageArea>(); // Mapping from class name to current storage area
//	private HashMap<String, StoragePolicy> spMap= new HashMap<String, StoragePolicy>(); // Mapping from storgae policy name to storage policy object
//	private HashMap<String, String> classtoSpMap = new HashMap<String, String>(); // Mapping from class name to storage policy name
	private boolean isConnected;
	private UserContext uc;
	private Subject sub;
//	private Integer CFS_IS_Retries;
//	private DocumentBuilderFactory factory;
//	private DocumentBuilder builder;
//	private Big5ToUTFMapper big5ToUTF16Mapper;
//	private Semaphore semFileStore;
	
	public CPEUtil(String configFile,FNUtilLogger log) throws FNUtilException, ParserConfigurationException{
		this.log = log;
		try {
			InputStream input = new FileInputStream(configFile);
	        Properties props = new Properties();
	        // load a properties file
	        props.load(input);
			String userName = props.getProperty(CPEUser);
			String password = props.getProperty(CPEPassword);
			String protocol = props.getProperty("Protocol", "http");
			String uri = protocol + "://" + props.getProperty(CPEServer)+":" +props.getProperty(CPEPort) +"/wsi/FNCEWS40MTOM";
	        con = Factory.Connection.getConnection(uri);
	        this.sub = UserContext.createSubject(con,userName,password,"FileNetP8");
	        UserContext.get().pushSubject(this.sub);
	        dom = Factory.Domain.fetchInstance(con, null, null);
	        ost = dom.get_ObjectStores();
	        Iterator<ObjectStore> itos=ost.iterator();
	        while (itos.hasNext()) {
	        	ObjectStore temp = itos.next();
	        	if (temp.get_Name().equals(props.getProperty("CPEOS"))) {
	        		this.os=temp;
	        		break;
	        	}
	        }
        	if(this.os==null) {
        		throw new FNUtilException(FNUtilException.ExceptionCodeValue.CPE_INVALID_OS_NAME, "Invalid Objec Store Name in File " + CONF_FILE_DIR);
        	}     	
			isConnected = true;	
			return;
				
		} catch (FileNotFoundException e) {
			throw new FNUtilException(FNUtilException.ExceptionCodeValue.CPE_CONFIG_FILE_NOT_FOUND, "File " + CONF_FILE_DIR + " not found", e);
		} catch (IOException e) {
			throw new FNUtilException(FNUtilException.ExceptionCodeValue.CPE_CONFIG_FILE_CANNOT_BE_OPENED, "Error in opening/reading" + configFile + " file", e);
		} catch (IllegalArgumentException e) {
			throw new FNUtilException(FNUtilException.ExceptionCodeValue.CPE_CONFIG_FILE_ILLEGAL_ARGUMENT, "Illegal argument in " + configFile, e);
		} catch (EngineRuntimeException e) {
			if (e.getExceptionCode().equals(ExceptionCode.E_NOT_AUTHENTICATED)) {
				throw new FNUtilException(FNUtilException.ExceptionCodeValue.CPE_USNAME_PASSWORD_INVALID, "userName or password is invalid", e);				
			} else if (e.getExceptionCode().equals(ExceptionCode.API_INVALID_URI)) {
				throw new FNUtilException(FNUtilException.ExceptionCodeValue.CPE_URI_INVALID, "userName or password is invalid", e);				
			}
			else {
				throw e;
			}
		}catch (Exception e) {
			System.out.println("unhandled CPEUtil Exception..CPEUtil Constructor");
			e.printStackTrace();
		}
		
	}
	public Connection getConnection() {
		return this.con;
	}
	
	public Domain getDomain() {
		return dom;
	}
	
	public ObjectStore getObjectStore() {
		return this.os;
	}
	

	public Subject getSubject() {
		return this.sub;
	}
	
}
