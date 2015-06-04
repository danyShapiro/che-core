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

import com.google.common.io.ByteStreams;

import org.eclipse.che.api.machine.server.MachineManager;
import org.eclipse.che.commons.lang.Pair;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;

/**
 * Routes requests to extension API hosted in machine
 *
 * @author Alexander Garagatyi
 */
@Singleton
public class MachineExtensionProxyServlet extends HttpServlet {
    private final int            extServicesPort;
    private final MachineManager machineManager;

    @Inject
    public MachineExtensionProxyServlet(@Named("machine.extension.api_port") int extServicesPort, MachineManager machineManager) {
        this.extServicesPort = extServicesPort;
        this.machineManager = machineManager;
    }

    @Override
    public void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        final ProxyRequest proxyRequest = new ProxyRequest(extServicesPort, machineManager, req);

        // fixme secure request to another's machine

        // todo handle https to http

        // fixme if machine does not exist or start or other machine error occurs respond with another message

        // todo remove headers if it's name is in connection headers

        // fixme proxy should ensure that http 1.1 request contains hosts header

        if (proxyRequest.isValid()) {

            proxyRequest.setMethod(req.getMethod());
            proxyRequest.setUri(req.getQueryString() == null ? req.getRequestURI() : req.getRequestURI() + "?" + req.getQueryString());
            copyHeadersToProxyRequest(proxyRequest, req);

            if ("POST".equals(req.getMethod()) || "PUT".equals(req.getMethod()) || "DELETE".equals(req.getMethod())) {
                proxyRequest.setInputStream(req.getInputStream());
            }

            executeProxyRequest(proxyRequest, resp);
        } else {
            resp.sendError(SC_NOT_FOUND, "Request can't be forwarded to machine. No machine id is found in " + req.getRequestURI());
        }
    }

    private void copyHeadersToProxyRequest(ProxyRequest proxyRequest, HttpServletRequest request) {
        final Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            final String headerName = headerNames.nextElement();

            final Enumeration<String> headerValues = request.getHeaders(headerName);
            while (headerValues.hasMoreElements()) {
                proxyRequest.addHeader(headerName, headerValues.nextElement());
            }
        }
    }

    private void executeProxyRequest(ProxyRequest proxyRequest, HttpServletResponse resp) throws IOException {
        final HttpURLConnection conn = (HttpURLConnection)new URL(proxyRequest.getEndpointUrl()).openConnection();

        conn.setRequestMethod(proxyRequest.getMethod());

        for (Pair<String, String> header : proxyRequest.getHeaders()) {
            conn.setRequestProperty(header.first, header.second);
        }

        if (proxyRequest.getInputStream() != null) {
            conn.setDoOutput(true);

            try(InputStream is = proxyRequest.getInputStream()) {
                ByteStreams.copy(is, conn.getOutputStream());
            }
        }

        conn.connect();

        resp.setStatus(conn.getResponseCode());

        InputStream responseStream = conn.getErrorStream();

        if (responseStream == null) {
            responseStream = conn.getInputStream();
        }

        // copy headers from proxy response to origin response
        for (Map.Entry<String, List<String>> header : conn.getHeaderFields().entrySet()) {
            for (String headerValue : header.getValue()) {
                resp.addHeader(header.getKey(), headerValue);
            }
        }

        // copy content of input or error stream from destination response to output stream of origin response
        try (OutputStream os = resp.getOutputStream()) {
            ByteStreams.copy(responseStream, os);
        }
    }
}
