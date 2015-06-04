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

import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.machine.server.MachineManager;
import org.eclipse.che.api.machine.server.exception.MachineException;
import org.eclipse.che.api.machine.server.impl.MachineImpl;
import org.eclipse.che.api.machine.shared.Server;
import org.eclipse.che.commons.lang.Pair;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.UriBuilder;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Alexander Garagatyi
 */
public class ProxyRequest {
    private static final Pattern EXTENSION_API_URI = Pattern.compile("/api/ext/(?<machineId>[^/]+)/.*");

    private final List<Pair<String, String>> headers;

    private InputStream inputStream;
    private String      endpointUrl;
    private String      method;

    public ProxyRequest(int extService, MachineManager machineManager, HttpServletRequest req) {
        this.headers = new ArrayList<>();

        String machineId = getMachineId(req);

        try {
            final MachineImpl machine = machineManager.getMachine(machineId);
            final Server server = machine.getMetadata().getServers().get(Integer.toString(extService));

            this.endpointUrl = "http://" + server.getAddress();
        } catch (NotFoundException | MachineException ignore) {
        }
    }

    private String getMachineId(HttpServletRequest req) {
        final Matcher matcher = EXTENSION_API_URI.matcher(req.getRequestURI());
        if (matcher.matches()) {
            return matcher.group("machineId");
        }
        return null;
    }

    public boolean isValid() {
        return endpointUrl != null;
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public List<Pair<String, String>> getHeaders() {
        return headers;
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public void setInputStream(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    public void addHeader(String headerName, String headerValue) {
        headers.add(Pair.of(headerName, headerValue));
    }

    public void setUri(String uri) {
        this.endpointUrl = UriBuilder.fromUri(endpointUrl + uri)
                                     .build()
                                     .toString();
    }
}
