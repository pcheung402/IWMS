package com.migrate.main;
import java.io.FileOutputStream;
import java.lang.*;
import java.lang.reflect.Constructor;
import java.util.Date;
import java.util.HashMap;

import javax.security.auth.*;
import javax.xml.parsers.ParserConfigurationException;

import com.filenet.api.collection.DocumentSet;
import com.filenet.api.collection.PageIterator;
import com.filenet.api.core.*;
import com.filenet.api.query.SearchSQL;
import com.filenet.api.query.SearchScope;
import com.filenet.api.util.*;

import com.fn.util.CPEUtil;
import com.fn.util.CSVParser;
import com.fn.util.FNUtilException;
import com.fn.util.FNUtilLogger;
import com.migrate.impl.ExportImpl;
public class Test {

	public static void main(String[] args) throws FNUtilException, ParserConfigurationException {
//		String uri = "http://172.22.21.22:9080/wsi/FNCEWS40MTOM";
//		Connection conn = Factory.Connection.getConnection(uri);
//		Subject subj = UserContext.createSubject(conn, "iwmsceadmin", "dev@iwms12", "FileNetP8");
//		UserContext.get().pushSubject(subj);
//		System.out.println("Connected");
//		
		FNUtilLogger log = new FNUtilLogger("batch001","bulkBatches");
		CPEUtil cpeUtil = new CPEUtil("mmatsts22.server.conf", log);
		log.info("Connected to P8 Domain "+ cpeUtil.getDomain().get_Name());
		
        SearchSQL sqlObject = new SearchSQL();
//        sqlObject.setMaxRecords(100);       

//        sqlObject.setWhereClause("StorageArea=Object('"+ getStorageAreaByName("").toString()+"')");
        String select = "r.DocumentTitle, r.Name, r.FoldersFiledIn, r.DateLastModified, r.DateCreated, r.VersionSeries, r.SecurityPolicy, r.MajorVersionNumber, r.MinorVersionNumber, r.Id, r.StorageArea, r.ClassDescription, r.MimeType, r.ContentElements, r.Annotations";
        sqlObject.setSelectList(select);
        String classAlias = "r";
        Boolean subClassToo = true;
        sqlObject.setFromClauseInitialValue("IWMSDocument", classAlias, subClassToo);
        SearchScope searchScope = new SearchScope(cpeUtil.getObjectStore());
        System.out.println ("Start retrieving : " + new Date());
        DocumentSet docSet = (DocumentSet)searchScope.fetchObjects(sqlObject,null,null ,Boolean.TRUE );
;
        PageIterator pageIter= docSet.pageIterator();
        pageIter.setPageSize(1000);
        while (pageIter.nextPage()) {
    		System.out.println("Retrieving next " + pageIter.getElementCount() + " records  :" + new Date());
        	for (Object obj : pageIter.getCurrentPage()) {
        		Document doc = (Document)obj;
        		String sa = doc.get_StorageArea().get_DisplayName();
        		if(!doc.get_Annotations().isEmpty())
        			System.out.println(doc.get_Id().toString() + "," + sa);

      		
        	}
        }
		
		
	}
}