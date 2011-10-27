package org.jenkinsci.jira_scraper;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

/**
 * @author Kohsuke Kawaguchi
 */
public class JiraScraper {
    /**
     * JIRA doesn't have the SOAP API to create a component, so we need to do this via a HTTP POST and page scraping.
     */
    public void createComponent(String projectId, String subcomponent, String owner) throws IOException, SAXException, DocumentException {
        WebClient wc = createAuthenticatedSession();

        HtmlPage p = wc.getPage("http://issues.jenkins-ci.org/secure/project/AddComponent!default.jspa?pid=" + projectId);
        HtmlForm f = p.getFormByName("jiraform");
        f.getInputByName("name").setValueAttribute(subcomponent);
        f.getTextAreaByName("description").setText(subcomponent + " plugin");
        if (owner!=null)
            f.getInputByName("componentLead").setValueAttribute(owner);
        checkError((HtmlPage) f.submit());
    }

    public enum DefaultAssignee {
        PROJECT_DEFAULT,
        COMPONENT_LEAD,
        PROJECT_LEAD,
        UNASSIGNED
    }

    public void setDefaultAssignee(String projectId, String component, DefaultAssignee assignee) throws Exception {
        WebClient wc = createAuthenticatedSession();

        HtmlPage rsp = wc.getPage("http://issues.jenkins-ci.org/secure/project/SelectComponentAssignees!default.jspa?projectId=" + projectId);
        List<HtmlElement> rows = rsp.selectNodes("//TABLE[@class='grid']//TR");   // [TD[1]='COMPONENTNAME'] somehow doesn't work any more. how come?

        for (HtmlElement row : rows) {
            String caption = ((HtmlElement)row.selectSingleNode("TD[1]")).getTextContent();
            if (caption.equals(component)) {
                // figure out the name field
                HtmlElement r = (HtmlElement)row.selectSingleNode(".//INPUT[@type='radio']");
                String name = r.getAttribute("name");

                HtmlForm f = rsp.getFormByName("jiraform");
                f.getInputByName(name).setValueAttribute(String.valueOf(assignee.ordinal()));
                checkError((HtmlPage)f.submit());
                return;
            }
        }

        throw new IOException("Unable to find component "+component+" in the issue tracker");
    }

    /**
     * Creates a conversation that's already logged in as the current user.
     */
    public WebClient createAuthenticatedSession() throws DocumentException, IOException, SAXException {
        ConnectionInfo con = new ConnectionInfo();

        WebClient wc = new WebClient();
        wc.setJavaScriptEnabled(false);
        HtmlPage p = wc.getPage("http://issues.jenkins-ci.org/login.jsp");
        HtmlForm f = (HtmlForm)p.getElementById("login-form");
        f.getInputByName("os_username").setValueAttribute(con.userName);
        f.getInputByName("os_password").setValueAttribute(con.password);
        checkError((HtmlPage) f.submit());

        return wc;
    }

    /**
     * Check if this submission resulted in an error, and if so, report an exception.
     */
    private  HtmlPage checkError(HtmlPage rsp) throws DocumentException, IOException {
        System.out.println(rsp.getWebResponse().getContentAsString());

        HtmlElement e = (HtmlElement) rsp.selectSingleNode("//*[@class='errMsg']");
        if (e==null)
            e = (HtmlElement) rsp.selectSingleNode("//*[@class='errorArea']");
        if (e!=null) {
            StringWriter w = new StringWriter();
            new XMLWriter(w, OutputFormat.createCompactFormat()) {
                // just print text
                @Override
                protected void writeElement(Element element) throws IOException {
                    writeElementContent(element);
                }
            }.write(e);
            throw new IOException(w.toString());
        }
        return rsp;
    }
}
