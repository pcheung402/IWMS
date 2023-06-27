package com.migrate.main;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.*;
import java.lang.reflect.Constructor;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Date;

import javax.xml.parsers.ParserConfigurationException;

import com.filenet.api.core.Factory;
import com.filenet.api.collection.DocumentSet;
import com.filenet.api.collection.FolderSet;
import com.filenet.api.collection.PageIterator;
import com.filenet.api.core.Document;
import com.filenet.api.core.Folder;
import com.filenet.api.core.ObjectStore;
import com.filenet.api.query.SearchSQL;
import com.filenet.api.query.SearchScope;
import com.filenet.api.util.Id;
import com.fn.util.CPEUtil;
import com.fn.util.FNUtilException;
import com.fn.util.FNUtilLogger;

public class RetrieveFolderStructure {
	static private CPEUtil cpeUtil;
	static private ObjectStore objectStore = null;
	static private FNUtilLogger log;
	static private java.sql.Connection conn;
	public static void main(String[] args) throws ClassNotFoundException, FNUtilException, ParserConfigurationException, FileNotFoundException, IOException, SQLException {
		// TODO Auto-generated method stub
		log = new FNUtilLogger("C:\\temp");
		cpeUtil = new CPEUtil("mmatsts22.server.conf", log);
		log.info("Connected");
		java.util.Properties props = new java.util.Properties();
		props.load(new FileInputStream("config/docdb.conf"));
		String JDBCURL = "jdbc:mysql://"+ props.getProperty("DOCDBServer") + ":" + props.getProperty("DOCDBPort") + "/" + props.getProperty("DOCDBDatabase") + "?autoReconnect=true&failOverReadOnly=false";
		String dbuser = props.getProperty("DOCDBUser");
		String dbpassword = props.getProperty("DOCDBPassword");
		conn = DriverManager.getConnection(JDBCURL, dbuser, dbpassword);
		
        SearchSQL sqlObject = new SearchSQL();
        String select = "r.FolderName, r.PathName, r.Id, r.Parent";
        sqlObject.setSelectList(select);
        String classAlias = "r";
        Boolean subClassToo = true;
        sqlObject.setFromClauseInitialValue("Folder", classAlias, subClassToo);
        System.out.println("SQL : " + sqlObject.toString());
        SearchScope searchScope = new SearchScope(cpeUtil.getObjectStore());
        System.out.println ("Start retrieving : " + new Date());
        FolderSet folderSet = (FolderSet)searchScope.fetchObjects(sqlObject,null,null ,Boolean.TRUE );
		log.info(String.format("start"));
        PageIterator pageIter= folderSet.pageIterator();
        pageIter.setPageSize(1000);
        while (pageIter.nextPage()) {
    		System.out.println("Retrieving next " + pageIter.getElementCount() + " records  :" + new Date());
        	for (Object obj : pageIter.getCurrentPage()) {
        		Folder folder = (Folder)obj;
        		String folderName = folder.get_FolderName();
        		String pathName = folder.get_PathName();
        		Folder parent = folder.get_Parent();
        		System.out.printf("%s\t%s\n",folderName, pathName);
        		String queryString= "REPLACE INTO FOLDER (OBJECT_ID,PARENT_OBJECT_ID,FOLDER_PATH) VALUES (UUID_TO_BIN(?),UUID_TO_BIN(?),?)";
        		PreparedStatement insertStatement = conn.prepareCall(queryString);
        		insertStatement.setString(1, folder.get_Id().toString());
        		if(parent!=null) {
        			insertStatement.setString(2, parent.get_Id().toString());
        		} else {
        			insertStatement.setString(2, null);
        		}
        		insertStatement.setString(3, pathName);
        		insertStatement.execute();
        		insertStatement.close();
        	}
        }		
	}
}