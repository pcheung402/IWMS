package com.fn.util;

import java.io.Serializable;
import java.util.*;

import com.filenet.api.util.Id;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;


class Property implements Serializable{
	@XmlElement(name="Name")
	private String name;

	@XmlElement(name="Display Name")
	private String displayName;
	
	@XmlElement(name="Type")
	private String type;
	
	@XmlElement(name="Value")
	private String value;
	
	public Property(String name, String displayName, String type, String value) {
		this.name = name;
		this.displayName = displayName;
		this.type = type;
		this.value = value;
	}
	
}


class Annotation implements Serializable{
	@XmlElement(name="Id")
	private String id;

	@XmlElement(name="Date_Created")
	private String dateCreated;
	
	@XmlElement(name="Date_Last_Modified")
	private String dateLastModified;
	
	@XmlElement(name="ESN")
	private Integer esn;
	
	@XmlElement(name="Annotation_Path")
	private String path;
	
	public Annotation(Id id, Date dateCreated, Date dateLastModified, Integer esn, String path) {
		this.id = id.toString();
		this.dateCreated = dateCreated.toString();
		this.dateLastModified = dateLastModified.toString();
		this.esn = esn;
		this.path = path;
	}
	
}

class Content implements Serializable {
	@XmlElement(name="ESN")
	private Integer esn;
	
	@XmlElement(name="Path")
	private String path;
	
	public Content(Integer esn, String path) {
		this.esn = esn;
		this.path = path;
	}
}

@XmlRootElement(name = "Document")
@XmlAccessorType(XmlAccessType.FIELD)
public class DocumentXML implements Serializable {
	
	@XmlElementWrapper(name="Properties")
	@XmlElement(name="Property")
	private ArrayList<Property> properties;
	
	@XmlElement(name="Security")
	private String security;
	
	@XmlElementWrapper(name="Contents")
	@XmlElement(name="Content")
	private ArrayList<Content> contents;
	
	@XmlElementWrapper(name="Annotations")
	@XmlElement(name="Annotation")
	private ArrayList<Annotation> annotations;
	
	@XmlElementWrapper(name="Folders")
	@XmlElement(name="Folder")
	private ArrayList<String> folders;
	
		
	
	public DocumentXML() {
		this.properties = new ArrayList<Property>();
		this.contents = new ArrayList<Content>();
		this.annotations = new ArrayList<Annotation>();
		this.folders = new ArrayList<String>();
	}
	
	public void addProperty(String name, String displayName, String type, String value) {
		this.properties.add(new Property(name, displayName, type, value));
	}
	
	public void addContent(Integer esn, String path) {
		this.contents.add(new Content(esn, path));
	}
	
	public void setSecurity(String security) {
		this.security=security;
	}
	
	public void addAnnotation(Id id, Date dateCreated, Date dateLastModified, Integer esn, String path) {
		this.annotations.add(new Annotation(id, dateCreated, dateLastModified, esn, path));
	}
	
	public void addFolder(String path) {
		this.folders.add(path);
	}

}
