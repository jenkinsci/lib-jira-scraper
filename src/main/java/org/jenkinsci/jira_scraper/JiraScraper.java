package org.jenkinsci.jira_scraper;

import com.atlassian.jira.rest.client.JiraRestClient;
import com.atlassian.jira.rest.client.NullProgressMonitor;
import com.atlassian.jira.rest.client.auth.BasicHttpAuthenticationHandler;
import com.atlassian.jira.rest.client.domain.AssigneeType;
import com.atlassian.jira.rest.client.domain.BasicComponent;
import com.atlassian.jira.rest.client.domain.BasicUser;
import com.atlassian.jira.rest.client.domain.Component;
import com.atlassian.jira.rest.client.domain.Component.AssigneeInfo;
import com.atlassian.jira.rest.client.domain.input.ComponentInput;
import com.atlassian.jira.rest.client.internal.jersey.JerseyJiraRestClientFactory;

import java.io.IOException;
import java.net.URI;
import javax.annotation.Nonnull;

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

    /**
     * Deletes the specified component
     * @param projectKey Project Id
     * @param deletedComponentName Name of the component to be deleted
     * @param backupComponentName Existing issues will be moved to this component
     * @throws IOException Missing components
     * @since TODO: define the version
     */
    public void deleteComponent(String projectKey, String deletedComponentName, String backupComponentName)
        throws IOException {
        BasicComponent deletedComponent = getBasicComponent(projectKey, deletedComponentName);
        BasicComponent backupComponent = getBasicComponent(projectKey, backupComponentName);
        
        restClient.getComponentClient().removeComponent(deletedComponent.getSelf(), backupComponent.getSelf(), pm);
    }
    
    /**
     * Deletes the specified component
     * @param projectKey Project Id
     * @param oldName Name of the component to be renamed
     * @param newName New name to be set
     * @throws IOException Cannot find the initial component or the new name is exist 
     * @since TODO: define the version
     */
    public void renameComponent(String projectKey, String oldName, String newName) 
            throws IOException {     
        BasicComponent c = getBasicComponent(projectKey, oldName);
        Component comp = getComponent(c);
        
        // Check the existance of the new component
        BasicComponent newComponent = null;
        try {
            newComponent = getBasicComponent(projectKey, newName);
        } catch (IOException cannotFindTheComponent) {
            // All is OK
        }
        if (newComponent != null) {
            throw new IOException("Unable to rename component " + oldName + 
                    ". Component " + newName + " already exists");
        }
        
        AssigneeInfo info = comp.getAssigneeInfo();
        AssigneeType assigneeType = info != null ? info.getAssigneeType() : null;
        BasicUser leadUser = info != null ? info.getAssignee() : null;
      
        restClient.getComponentClient().updateComponent(c.getSelf(), 
                new ComponentInput(newName, c.getDescription(), 
                       leadUser != null ? leadUser.getName() : null, assigneeType), pm);
    }
    
    public void setDefaultAssignee(String projectId, String component, AssigneeType assignee) throws IOException {
        setDefaultAssignee(projectId, component, assignee, null);
    }

    public void setDefaultAssignee(String projectId, String component, AssigneeType assignee, String name) throws IOException {
        BasicComponent c = getBasicComponent(projectId, component);
        Component comp = getComponent(c);
        
        String componentLead = name == null ? comp.getLead().getName() : name;
        ComponentInput ci = new ComponentInput(component, c.getDescription(), componentLead, assignee);
        restClient.getComponentClient().updateComponent(c.getSelf(), ci, pm);
    }
    
    /**
     * Gets JIRA component by the specified name.
     * @param projectId Project Id (e.g. JENKINS)
     * @param component Component name
     * @return The requested component
     * @throws IOException Component cannot be found
     */
    private @Nonnull BasicComponent getBasicComponent (String projectId, String component) 
        throws IOException {
        for (BasicComponent c : restClient.getProjectClient().getProject(projectId, pm).getComponents()) {
            if (c.getName().equals(component)) {
                return c;
            }
        }
        
        throw new IOException("Unable to find component "+component+" in the "+ projectId +" issue tracker");
    }
    
    private @Nonnull Component getComponent (@Nonnull BasicComponent c) {
        return restClient.getComponentClient().getComponent(c.getSelf(),pm);
    }
    
    /**
     * Removes the default assignee (or project lead) from the specified component.
     * @since 1.4
     */
    public void removeDefaultAssignee(String projectId, String component, AssigneeType assignee) throws IOException {
        for (BasicComponent c : restClient.getProjectClient().getProject(projectId,pm).getComponents()) {
            if (c.getName().equals(component)) {
                Component comp = restClient.getComponentClient().getComponent(c.getSelf(),pm);
                ComponentInput ci = new ComponentInput(component,c.getDescription(),null,assignee);
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
