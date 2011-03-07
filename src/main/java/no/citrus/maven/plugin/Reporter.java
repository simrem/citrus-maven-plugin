package no.citrus.maven.plugin;

import java.io.File;
import java.io.FilenameFilter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import no.citrus.restapi.model.Category;
import no.citrus.restapi.model.Measure;
import no.citrus.restapi.model.MeasureList;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;

public class Reporter {
	private static final String FOLDER = "./target/surefire-reports/";
	public static void run() throws JAXBException{
		String[] reports = getReports();
		MeasureList measures = new MeasureList(new ArrayList<Measure>());
		if(reports != null)
		{
			for(int i = 0; i < reports.length; i++){
				//System.out.println("Forloop " + reports[i]);
				Measure m = parseXMLReport(reports[i]);
				measures.getList().add(m);

			}
		}
		convertToXml(measures);
	}

	private static Measure parseXMLReport(String string) {

		try{
			File file = new File(FOLDER + string);
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db = dbf.newDocumentBuilder();
			Document doc = db.parse(file);
			doc.getDocumentElement().normalize();
			NodeList testSuiteLst = doc.getElementsByTagName("testsuite");
			Element testSuite = (Element) testSuiteLst.item(0);
			Measure testSuiteMeasure = createTestSuiteMeasure(testSuite);

			List<Measure> measureLst = new ArrayList<Measure>();

			NodeList nodeLst = testSuite.getElementsByTagName("testcase");
			for (int s = 0; s < nodeLst.getLength(); s++) {

				Node fstNode = nodeLst.item(s);
				if (fstNode.getNodeType() == Node.ELEMENT_NODE) {

					Element fstElmnt = (Element) fstNode;
					measureLst.add(createTestCaseMeasure(fstElmnt));
				}
			}
			testSuiteMeasure.setChildren(measureLst);
//			System.out.println("TestSuite done" + testSuiteMeasure.source);
			return testSuiteMeasure;
		} catch (Exception e) {
//			System.out.println(e.getMessage());
			e.printStackTrace();
		}

		return null;
	}

	private static Measure createTestCaseMeasure(Element testCase){
		Measure testCaseMeasure = new Measure();
		testCaseMeasure.setName(testCase.getAttribute("name"));
		double time = Double.valueOf(testCase.getAttribute("time"));
		testCaseMeasure.setValue(time);
		testCaseMeasure.setDate(Calendar.getInstance().getTime());
		testCaseMeasure.setSource(testCase.getAttribute("classname"));
		testCaseMeasure.setCategory(Category.testCase);
		NodeList failureLst = testCase.getElementsByTagName("failure");
		if(failureLst.getLength() > 0)
			testCaseMeasure.setFailed(true);
		else
			testCaseMeasure.setFailed(false);
		return testCaseMeasure;
	}

	private static Measure createTestSuiteMeasure(Element testSuite) {
		Measure testSuiteMeasure = new Measure();
		testSuiteMeasure.setSource(testSuite.getAttribute("name"));
		testSuiteMeasure.setDate(Calendar.getInstance().getTime());
		double time = Double.valueOf(testSuite.getAttribute("time"));
		testSuiteMeasure.setValue(time);
		testSuiteMeasure.setCategory(Category.testSuite);
		testSuiteMeasure.setName("");
		return testSuiteMeasure;
	}

	private static String[] getReports() {
		File dir = new File("./target/surefire-reports");
		return dir.list(new FilenameFilter() {

			@Override
			public boolean accept(File arg0, String arg1) {
				return arg1.endsWith(".xml");
			}
		});
	}

	private static void convertToXml(MeasureList measures) throws JAXBException{
		JAXBContext context = JAXBContext.newInstance(MeasureList.class, Measure.class);
		Marshaller marshaller = context.createMarshaller();
		marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
		StringWriter sw = new StringWriter();

		marshaller.marshal(measures, sw);

		StringBuffer sb = sw.getBuffer();

		Client c = Client.create();  
		WebResource r = c.resource("http://localhost:8090/measure");  

		//TODO:
		//r.type("application/xml").post(String.class, sb.toString());   
	}

}

