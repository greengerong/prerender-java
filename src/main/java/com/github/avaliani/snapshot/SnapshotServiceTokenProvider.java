package com.github.avaliani.snapshot;

import javax.annotation.Nullable;

/**
 * Provides the service token to use when making a snapshot request.
 *
 * @author avaliani
 *
 */
public interface SnapshotServiceTokenProvider {

    /**
     * @return the token to use when making snapshot requests or
     *     null to specify no token.
     */
    @Nullable
    String getServiceToken();

}
