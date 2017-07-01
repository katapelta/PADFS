package system.managers;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import system.containers.Server;
import system.logger.PadFsLogger;
import system.logger.PadFsLogger.LogLevel;

public class XmlManager {
	
	private String xmlFile;
	private String doc; 
	private XPath xpath;
	private DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
	private DocumentBuilder builder = null;
	private Document document = null;
	private Document xmlDocument = null;

	
	public XmlManager( String f ){
		this.xpath = XPathFactory.newInstance().newXPath();
		this.xmlFile = f;
		try {
			readDoc();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	private void readDoc() throws IOException{
		BufferedReader br = new BufferedReader(new FileReader(xmlFile));
		String everything ;
		try {
		    StringBuilder sb = new StringBuilder();
		    String line = br.readLine();

		    while (line != null) {
		        sb.append(line);
		        sb.append(System.lineSeparator());
		        line = br.readLine();
		    }
		    everything = sb.toString();
		} finally {
			br.close();
		}
		doc = everything;
		
		try {
		    builder = builderFactory.newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			PadFsLogger.log(LogLevel.WARNING, e.getClass().getName() + ": " + e.getMessage());
		}
		
		try {
			document = builder.parse(new FileInputStream(xmlFile));
			xmlDocument = builder.parse(new ByteArrayInputStream(doc.getBytes()));
		} catch (FileNotFoundException e) {
			PadFsLogger.log(LogLevel.WARNING, e.getClass().getName() + ": " + e.getMessage());
		} catch (SAXException e) {
			PadFsLogger.log(LogLevel.WARNING, e.getClass().getName() + ": " + e.getMessage());
		} catch (IOException e) {
			PadFsLogger.log(LogLevel.WARNING, e.getClass().getName() + ": " + e.getMessage());
		}
		
	}
	
	public String readXML(String exp) {
		try {			
			return xpath.compile(exp).evaluate(xmlDocument);
		} catch (XPathExpressionException e) {
			PadFsLogger.log(LogLevel.WARNING, e.getClass().getName() + ": " + e.getMessage());
		}
		return null;
    }
	
	public NodeList readXMLList(String exp){
		NodeList list = null;
		try {
			list = (NodeList) xpath.evaluate(exp, xmlDocument, XPathConstants.NODESET);
		} catch (XPathExpressionException e) {
			PadFsLogger.log(LogLevel.WARNING, "readXMLList[]"+e.getClass().getName() + ": " + e.getMessage());
		}
		return list;
	}
	
	public static List<Server> traverseServerList(NodeList rootNode){
		if(rootNode == null)
			return null;
		List<Server> ret = new ArrayList<Server>();
		for (int index = 0; index < rootNode.getLength(); index++) {
			Node aNode = rootNode.item(index);
			if (aNode.getNodeType() == Node.ELEMENT_NODE) {
				NodeList childNodes = aNode.getChildNodes();
				Node in = null;
				Map<String, String> tmp = new HashMap<>();
				for(int i=0;i< childNodes.getLength();i++) { //FORCE CONFIGURATION TO BE SERVER AND PORT
					in = childNodes.item(i);
					tmp.put(in.getNodeName(), in.getTextContent());
				}
				
				ret.add(new Server(-1,tmp.get("ip"),tmp.get("port"))); //initialize without a significant id
			}
		}
		return ret;
	}
	
	public void writeXML(String path, String def) {
		String p[] = path.split("/");
		Node n = (Node) document;
		for (int i = 0; i < p.length; i++) {
			NodeList kids = n.getChildNodes();
			Node nfound = null;
			for (int j = 0; j < kids.getLength(); j++)
				if (kids.item(j).getNodeName().equals(p[i])) {
					nfound = kids.item(j);
					break;
				}
			if (nfound == null) {
				nfound = document.createElement(p[i]);
				n.appendChild(nfound);
				n.appendChild(document.createTextNode("\n"));
			}
			n = nfound;
		}
		NodeList kids = n.getChildNodes();
		boolean overrided = false;
		for (int i = 0; i < kids.getLength(); i++)
			if (kids.item(i).getNodeType() == Node.TEXT_NODE) {
				// text node exists
				kids.item(i).setNodeValue(def); // override
				overrided = true;
				break;
			}
		if (!overrided)
			n.appendChild(document.createTextNode(def));

		TransformerFactory transformerFactory = TransformerFactory.newInstance();
		Transformer transformer;
		
		try {
			transformer = transformerFactory.newTransformer();
			transformer.transform(new DOMSource(document), new StreamResult(xmlFile));

		} catch (TransformerConfigurationException e) {
			PadFsLogger.log(LogLevel.WARNING, e.getClass().getName() + ": " + e.getMessage());
		} catch (TransformerException e) {
			PadFsLogger.log(LogLevel.WARNING, e.getClass().getName() + ": " + e.getMessage());
		}
	}

}
