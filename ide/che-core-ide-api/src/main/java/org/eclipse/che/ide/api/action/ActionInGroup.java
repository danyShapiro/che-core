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
package org.eclipse.che.ide.api.action;

/**
 * @author <a href="mailto:evidolob@codenvy.com">Evgen Vidolob</a>
 * @version $Id:
 */
public class ActionInGroup {
    private final DefaultActionGroup myGroup;
    private final Action             myAction;

    ActionInGroup(DefaultActionGroup group, Action action) {
        myGroup = group;
        myAction = action;
    }

    public ActionInGroup setAsSecondary(boolean isSecondary) {
        myGroup.setAsPrimary(myAction, !isSecondary);
        return this;
    }

    public ActionGroup getGroup() {
        return myGroup;
    }
}
