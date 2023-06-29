package com.migrate.main;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class SQLViewGenerator {
	static FileOutputStream sqlScript;
	public static void main(String[] args) throws ParserConfigurationException, SAXException, IOException {
		sqlScript = new  FileOutputStream(new File("D:\\Temp\\createView.sql"));
		sqlScript.write(String.format("CREATE VIEW successExport AS\n").getBytes());
		sqlScript.write(String.format("SELECT\n").getBytes());
		// TODO Auto-generated method stub
//		propertyDefintion = new HashMap<String, String>();
//		propertyDefintion = new HashMap<String, HashMap<String,String>>();
//		File fXmlFile = new File((new Launcher()).getClass().getClassLoader().getResource("propertiesDefinitions.xml").getPath());
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		org.w3c.dom.Document doc = dBuilder.parse((new Launcher()).getClass().getClassLoader().getResourceAsStream("propertiesDefinitions.xml"));			
		doc.getDocumentElement().normalize();
		NodeList propertyNodeList = doc.getElementsByTagName("property");						
		for (int j = 0; j < propertyNodeList.getLength(); j++) {
			Node nNode = propertyNodeList.item(j);
			
			if (nNode.getNodeType() == Node.ELEMENT_NODE) {
				Element eElement = (Element) nNode;
				sqlScript.write(String.format("%s AS '%s',\n",eElement.getElementsByTagName("symName").item(0).getTextContent(), eElement.getElementsByTagName("description").item(0).getTextContent()).getBytes());
//				HashMap propertyAttrMap = new HashMap<String,String>();
//				propertyAttrMap.put("dataType",eElement.getElementsByTagName("dataType").item(0).getTextContent());
//				propertyAttrMap.put("displayName", eElement.getElementsByTagName("description").item(0).getTextContent());
//				propertyDefintion.put(eElement.getElementsByTagName("symName").item(0).getTextContent(), eElement.getElementsByTagName("dataType").item(0).getTextContent());
//				propertyDefintion.put(eElement.getElementsByTagName("symName").item(0).getTextContent(), propertyAttrMap);
//				System.out.println(eElement.getElementsByTagName("symName").item(0).getTextContent() + " / " + eElement.getElementsByTagName("dataType").item(0).getTextContent());
			}
			sqlScript.flush();

		}
		sqlScript.write(String.format("FROM document\n").getBytes());
		sqlScript.write(String.format("WHERE EXPORT_STATUS=0\n").getBytes());
		sqlScript.close();
	}

}
