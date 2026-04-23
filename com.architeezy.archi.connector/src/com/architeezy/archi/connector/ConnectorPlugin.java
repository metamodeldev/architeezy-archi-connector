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

import org.eclipse.core.runtime.Platform;
import org.eclipse.equinox.security.storage.SecurePreferencesFactory;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

/**
 * OSGi bundle activator for the Architeezy connector plugin.
 *
 * Builds the {@link ConnectorServices} composition root in {@link #start} and
 * exposes it via {@link #services()} for UI code (handlers, wizards, navigator)
 * that needs to look up services. Services themselves never go through the
 * plugin - they receive their collaborators via constructor injection.
 */
public class ConnectorPlugin extends AbstractUIPlugin {

    /** Bundle symbolic name / plugin identifier. */
    public static final String PLUGIN_ID = "com.architeezy.archi.connector"; //$NON-NLS-1$

    private static ConnectorPlugin instance;

    private ConnectorServices services;

    /** Creates the plugin instance. */
    public ConnectorPlugin() {
        // Instance is set in start() to avoid leaking 'this' from the constructor.
    }

    /**
     * Returns the shared plugin instance.
     *
     * @return the shared plugin instance
     */
    public static ConnectorPlugin getInstance() {
        return instance;
    }

    /**
     * Returns the plugin's service composition root.
     *
     * @return the service graph
     */
    public ConnectorServices services() {
        return services;
    }

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        setInstance(this);
        services = new ConnectorServices(
                () -> Platform.getStateLocation(context.getBundle()).toFile().toPath(),
                getPreferenceStore(),
                SecurePreferencesFactory::getDefault);
        services.updateCheckService().start();
        services.localChangeService().start();
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        if (services != null) {
            services.modelTreeDecorator().uninstall();
            services.updateCheckService().stop();
            services.localChangeService().stop();
            services = null;
        }
        setInstance(null);
        super.stop(context);
    }

    private static void setInstance(ConnectorPlugin plugin) {
        instance = plugin;
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
