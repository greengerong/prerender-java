package com.github.avaliani.snapshot;

import java.util.Map;

import javax.annotation.Nullable;

/**
 * Provides configuration for the snapshot service.
 *
 * @author avaliani
 *
 */
public interface SnapshotServiceConfig {

    /**
     * @return the token used to identify the app with the snapshotting service
     *     or null to use no token.
     */
    @Nullable
    String getServiceToken();

    /**
     * @return the snapshot service URL or null to use the default
     *     URL for the service.
     */
    @Nullable
    String getServiceUrl();

    /**
     * @return the scheme used to connect to the snapshotting service:
     *     "http" or "https".
     */
    String getRequestScheme();

    /**
     * @return snapshot service specific options.
     */
    Map<String, String> getOptions();
}