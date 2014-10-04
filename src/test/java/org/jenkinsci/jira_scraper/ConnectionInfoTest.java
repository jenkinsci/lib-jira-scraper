package org.jenkinsci.jira_scraper;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link ConnectionInfo}.
 * @author Oleg Nenashev <o.v.nenashev@gmail.com>
 */
public class ConnectionInfoTest {
    
    private static final String CONNECTION_INFO_SRC = "testConnectionInfo.properties";
    
    @Test
    public void loadConnectionInfo() throws IOException {
        final URL resource = ConnectionInfoTest.class.getClassLoader().getResource(CONNECTION_INFO_SRC);
        if (resource == null) {
            throw new IOException("Cannot find resource " + CONNECTION_INFO_SRC);
        }
        ConnectionInfo info = new ConnectionInfo(new File(resource.getPath()));
        Assert.assertEquals("testUserName", info.userName);
        Assert.assertEquals("testPassword", info.password);
    }
}
