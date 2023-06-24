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
import com.filenet.api.collection.RepositoryRowSet;
import com.filenet.api.core.*;
import com.filenet.api.query.RepositoryRow;
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
		FNUtilLogger log = new FNUtilLogger("C:\\temp");
		CPEUtil cpeUtil = new CPEUtil("D:\\iwms_batches\\batch_001\\batch.conf", log);
		log.info("Connected to P8 Domain "+ cpeUtil.getDomain().get_Name());
		
        SearchSQL sqlObject = new SearchSQL();
//        sqlObject.setMaxRecords(100);       

        sqlObject.setWhereClause("StorageArea='shelf201401'");
        String select = "Id";
        sqlObject.setSelectList(select);
        String classAlias = "r";
        Boolean subClassToo = true;
        sqlObject.setFromClauseInitialValue("IWMSDocument", null, subClassToo);
        System.out.println(sqlObject.toString());
        SearchScope searchScope = new SearchScope(cpeUtil.getObjectStore());
//        System.out.println ("Start retrieving : " + new Date());
//        DocumentSet docSet = (DocumentSet)searchScope.fetchObjects(sqlObject,null,null ,Boolean.TRUE );
        RepositoryRowSet rs = searchScope.fetchRows(sqlObject, null, null, true);
//        RepositoryRow row = (RepositoryRow)rs.iterator().next();
//        System.out.println(row.getProperties().getInteger32Value("Id"));
        PageIterator pageIter= rs.pageIterator();
        pageIter.setPageSize(10000);
        while (pageIter.nextPage()) {
    		System.out.println("Retrieving next " + pageIter.getElementCount() + " records  :" + new Date());
        	for (Object obj : pageIter.getCurrentPage()) {
        		RepositoryRow row = (RepositoryRow)obj;

        			System.out.println(row.getProperties().getIdValue("Id").toString());

      		
        	}
        }
		
		
	}
}