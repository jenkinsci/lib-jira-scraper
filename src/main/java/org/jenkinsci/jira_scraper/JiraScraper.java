package org.jenkinsci.jira_scraper;

import com.atlassian.jira.rest.client.JiraRestClient;
import com.atlassian.jira.rest.client.NullProgressMonitor;
import com.atlassian.jira.rest.client.auth.BasicHttpAuthenticationHandler;
import com.atlassian.jira.rest.client.domain.AssigneeType;
import com.atlassian.jira.rest.client.domain.BasicComponent;
import com.atlassian.jira.rest.client.domain.Component;
import com.atlassian.jira.rest.client.domain.input.ComponentInput;
import com.atlassian.jira.rest.client.internal.jersey.JerseyJiraRestClientFactory;

import java.io.IOException;
import java.net.URI;

/**
 * @author Kohsuke Kawaguchi
 */
public class JiraScraper {
    private final JiraRestClient restClient;
    private final NullProgressMonitor pm = new NullProgressMonitor();

    public JiraScraper() throws IOException {
        JerseyJiraRestClientFactory factory = new JerseyJiraRestClientFactory();
        ConnectionInfo con = new ConnectionInfo();
        restClient = factory.create(URI.create("https://issues.jenkins-ci.org/"), new BasicHttpAuthenticationHandler(con.userName,con.password));
    }

    /**
     * JIRA doesn't have the SOAP API to create a component, so we need to do this via a HTTP POST and page scraping.
     */
    public void createComponent(String projectKey, String subcomponent, String owner, AssigneeType defaultAssignee) throws IOException {
        restClient.getComponentClient().createComponent(projectKey, new ComponentInput(
                subcomponent, subcomponent + " plugin", owner, defaultAssignee), pm);
    }

    public void setDefaultAssignee(String projectId, String component, AssigneeType assignee) throws IOException {
        for (BasicComponent c : restClient.getProjectClient().getProject(projectId,pm).getComponents()) {
            if (c.getName().equals(component)) {
                Component comp = restClient.getComponentClient().getComponent(c.getSelf(),pm);
                ComponentInput ci = new ComponentInput(component,c.getDescription(),comp.getLead().getName(),assignee);
                restClient.getComponentClient().updateComponent(c.getSelf(), ci, pm);
                return;
            }
        }

        throw new IOException("Unable to find component "+component+" in the issue tracker");
    }

    // test
    public static void main(String[] args) throws Exception {
        JiraScraper js = new JiraScraper();
        js.createComponent("JENKINS", "kohsuke-test", "kohsuke", AssigneeType.COMPONENT_LEAD);
        js.setDefaultAssignee("JENKINS","kohsuke-test",AssigneeType.PROJECT_LEAD);
    }
}
