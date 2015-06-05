/*******************************************************************************
 * Copyright (c) 2012-2015 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.api.machine.server.proxy;

import org.eclipse.che.api.machine.server.MachineManager;
import org.eclipse.che.api.machine.server.impl.MachineImpl;
import org.eclipse.che.api.machine.server.spi.InstanceMetadata;
import org.eclipse.che.api.machine.shared.Server;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.everrest.test.mock.MockHttpServletRequest;
import org.everrest.test.mock.MockHttpServletResponse;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

public class MachineExtensionProxyServletTest {
    private static final String MACHINE_ID              = "machine123";
    private static final int    EXTENSIONS_API_PORT     = 4301;
    private static final String PROXY_ENDPOINT          = "http://localhost:8080";
    private static final String DESTINATION_BASEPATH    = "/api/ext/" + MACHINE_ID;
    private static final String DEFAULT_RESPONSE_ENTITY = "Hello world!";

    private Map<String, Server> machineServers;

    private MachineManager machineManager;

    private MachineImpl machine;

    private InstanceMetadata instanceMetadata;

    private MachineExtensionProxyServlet proxyServlet;

    private org.eclipse.jetty.server.Server jettyServer;

    private Response extApiResponse;

    @BeforeClass
    public void setUpClass() throws Exception {
        jettyServer = new org.eclipse.jetty.server.Server(0);
        jettyServer.setHandler(new ExtensionApiHandler());
        jettyServer.start();

        String destinationAddress = jettyServer.getURI().getHost() + ":" + jettyServer.getURI().getPort();
        machineServers = Collections.<String, Server>singletonMap(String.valueOf(EXTENSIONS_API_PORT), new ServerImpl(destinationAddress));
    }

    @BeforeMethod
    public void setUpMethod() throws Exception {
        machineManager = mock(MachineManager.class);

        machine = mock(MachineImpl.class);

        instanceMetadata = mock(InstanceMetadata.class);

        extApiResponse = spy(new Response());

        proxyServlet = new MachineExtensionProxyServlet(4301, machineManager);
    }

    @AfterClass
    public void tearDown() throws Exception {
        jettyServer.stop();
    }

    @Test(dataProvider = "methodProvider")
    public void shouldBeAbleToProxyRequestWithDifferentMethod(String method) throws Exception {
        when(machineManager.getMachine(MACHINE_ID)).thenReturn(machine);
        when(machine.getMetadata()).thenReturn(instanceMetadata);
        when(instanceMetadata.getServers()).thenReturn(machineServers);

        MockHttpServletRequest mockRequest =
                new MockHttpServletRequest(PROXY_ENDPOINT + "/api/ext/" + MACHINE_ID + "/path/to/something",
                                           new ByteArrayInputStream(new byte[0]),
                                           0,
                                           method,
                                           Collections.<String, List<String>>emptyMap());

        MockHttpServletResponse mockResponse = new MockHttpServletResponse();

        proxyServlet.service(mockRequest, mockResponse);

        assertEquals(mockResponse.getStatus(), 200);
    }

    @DataProvider(name = "methodProvider")
    public String[][] methodProvider() {
        return new String[][]{{"GET"}, {"PUT"}, {"POST"}, {"DELETE"}, {"OPTIONS"}};
    }

    @Test
    public void shouldCopyEntityFromResponse() throws Exception {
        when(machineManager.getMachine(MACHINE_ID)).thenReturn(machine);
        when(machine.getMetadata()).thenReturn(instanceMetadata);
        when(instanceMetadata.getServers()).thenReturn(machineServers);

        MockHttpServletRequest mockRequest =
                new MockHttpServletRequest(PROXY_ENDPOINT + "/api/ext/" + MACHINE_ID + "/path/to/something",
                                           new ByteArrayInputStream(new byte[0]),
                                           0,
                                           "GET",
                                           Collections.<String, List<String>>emptyMap());

        MockHttpServletResponse mockResponse = new MockHttpServletResponse();

        proxyServlet.service(mockRequest, mockResponse);

        assertEquals(mockResponse.getStatus(), 200);
        assertEquals(mockResponse.getOutputContent(), DEFAULT_RESPONSE_ENTITY);
    }

    @Test
    public void shouldCopyHeadersFromResponse() throws Exception {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Accept-Ranges", Collections.singletonList("bytes"));
        headers.put("Allow", Collections.singletonList("GET, HEAD, PUT"));
        headers.put("ETag", Collections.singletonList("xyzzy"));
        headers.put("Expires", Collections.singletonList("Thu, 01 Dec 2020 16:00:00 GMT"));
        headers.put("Last-Modified", Collections.singletonList("Tue, 15 Nov 1994 12:45:26 GMT"));
        headers.put("Retry-After", Collections.singletonList("120"));
        headers.put("Upgrade", Collections.singletonList("HTTP/2.0"));

        when(machineManager.getMachine(MACHINE_ID)).thenReturn(machine);
        when(machine.getMetadata()).thenReturn(instanceMetadata);
        when(instanceMetadata.getServers()).thenReturn(machineServers);
        when(extApiResponse.getHeaders()).thenReturn(headers);

        MockHttpServletRequest mockRequest =
                new MockHttpServletRequest(PROXY_ENDPOINT + "/api/ext/" + MACHINE_ID + "/path/to/something",
                                           new ByteArrayInputStream(new byte[0]),
                                           0,
                                           "POST",
                                           Collections.<String, List<String>>emptyMap());

        MockHttpServletResponse mockResponse = new MockHttpServletResponse();

        proxyServlet.service(mockRequest, mockResponse);

        assertEquals(mockResponse.getStatus(), 200);
        for (Map.Entry<String, List<String>> expectedHeader : headers.entrySet()) {
            assertEquals(headers.get(expectedHeader.getKey()), mockResponse.getHeaders(expectedHeader.getKey()));
        }
    }

    @Test(enabled = false)
    public void shouldCopyHeadersFromRequest() throws Exception {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Content-Encoding", Collections.singletonList("gzip"));
        headers.put("Content-Language", Collections.singletonList("mi, en"));
        headers.put("Content-Length", Collections.singletonList("3495"));
        headers.put("Content-Type", Collections.singletonList("text/html; charset=ISO-8859-4"));
        headers.put("Transfer-Encoding", Collections.singletonList("chunked"));
        headers.put("Date", Collections.singletonList("Tue, 15 Nov 1994 08:12:31 GMT"));
        headers.put("From", Collections.singletonList("webmaster@w3.org"));
        headers.put("Accept", Collections.singletonList("*/*"));
        headers.put("Accept-Charset", Collections.singletonList("iso-8859-5, unicode-1-1;q=0.8"));
        headers.put("Accept-Encoding", Collections.singletonList("compress, gzip"));
        headers.put("Accept-Language", Collections.singletonList("da, en-gb;q=0.8, en;q=0.7"));
        headers.put("Referer", Collections.singletonList("http://www.w3.org/hypertext/DataSources/Overview.html"));
        headers.put("Max-Forwards", Collections.singletonList("5"));
        headers.put("If-Modified-Since", Collections.singletonList("Sat, 29 Oct 2016 19:43:31 GMT"));
        headers.put("If-Match", Collections.singletonList("xyzzy"));
        headers.put("Host", Collections.singletonList("www.w3.org"));

        when(machineManager.getMachine(MACHINE_ID)).thenReturn(machine);
        when(machine.getMetadata()).thenReturn(instanceMetadata);
        when(instanceMetadata.getServers()).thenReturn(machineServers);
        when(extApiResponse.getHeaders()).thenReturn(headers);

        MockHttpServletRequest mockRequest =
                new MockHttpServletRequest(PROXY_ENDPOINT + "/api/ext/" + MACHINE_ID + "/path/to/something",
                                           new ByteArrayInputStream(new byte[0]),
                                           0,
                                           "POST",
                                           Collections.<String, List<String>>emptyMap());

        MockHttpServletResponse mockResponse = new MockHttpServletResponse();

        proxyServlet.service(mockRequest, mockResponse);

        assertEquals(mockResponse.getStatus(), 200);
        //TODO verify headers of request
    }

    // Todo
    // redirect
    // do not proxy headers that are mentioned in connection header
    // send entity to destination
    // check headers send to destination
    // check used destination url
    // machine does not exist
    // request url does not contain machine id
    // no server on destination side
    // all type of response codes from destination side
    // https to http proxy
    // json object in response
    // json object in request
    // html in response
    // including cookies and http-only cookies
    // secure cookies for https
    // read entity from error stream of destination response

    // check that cookies are not saved
    //

    private class ExtensionApiHandler extends AbstractHandler {
        public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
                throws IOException, ServletException {
            if (!target.startsWith(DESTINATION_BASEPATH)) {
                response.sendError(404);
                return;
            }

            response.setStatus(extApiResponse.getStatus());
            for (Map.Entry<String, List<String>> header : extApiResponse.getHeaders().entrySet()) {
                for (String headerValue : header.getValue()) {
                    response.addHeader(header.getKey(), headerValue);
                }
            }
            response.getWriter().print(extApiResponse.getEntity());
            baseRequest.setHandled(true);
        }
    }

    private static class Response {
        int getStatus() {
            return 200;
        }

        public String getEntity() {
            return DEFAULT_RESPONSE_ENTITY;
        }

        Map<String, List<String>> getHeaders() {
            return Collections.singletonMap("Content-type", Collections.singletonList("text/html;charset=utf-8"));
        }
    }

    private static class ServerImpl implements Server {
        private String address;

        public ServerImpl(String address) {
            this.address = address;
        }

        @Override
        public String getAddress() {
            return address;
        }
    }
}