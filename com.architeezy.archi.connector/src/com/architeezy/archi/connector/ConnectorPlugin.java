/*
 * Copyright (c) 2026 Denis Nikiforov
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.architeezy.archi.connector;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import com.architeezy.archi.connector.navigator.ModelTreeDecorator;
import com.architeezy.archi.connector.service.UpdateCheckService;

/**
 * OSGi bundle activator for the Architeezy connector plugin.
 */
public class ConnectorPlugin extends AbstractUIPlugin {

    /** Bundle symbolic name / plugin identifier. */
    public static final String PLUGIN_ID = "com.architeezy.archi.connector"; //$NON-NLS-1$

    private static ConnectorPlugin instance;

    /** Creates the plugin instance. */
    public ConnectorPlugin() {
        instance = this;
    }

    /**
     * Returns the shared plugin instance.
     *
     * @return the shared plugin instance
     */
    public static ConnectorPlugin getInstance() {
        return instance;
    }

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        UpdateCheckService.INSTANCE.start();
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        ModelTreeDecorator.INSTANCE.uninstall();
        UpdateCheckService.INSTANCE.stop();
        super.stop(context);
    }

    /**
     * Returns an image descriptor for the image at the given plugin-relative path.
     *
     * @param path plugin-relative path to the image
     * @return image descriptor for the given path
     */
    public static ImageDescriptor getImageDescriptor(String path) {
        return imageDescriptorFromPlugin(PLUGIN_ID, path);
    }

}
