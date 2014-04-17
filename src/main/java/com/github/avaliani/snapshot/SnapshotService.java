package com.github.avaliani.snapshot;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

/**
 * A service used to snapshot HTML pages after executing any javascript on the page.
 *
 * @author avaliani
 *
 */
public interface SnapshotService {

    /**
     * Initializes a newly constructed snapshot service. Must be called prior to using the service.
     */
    void init(SnapshotServiceConfig config);

    /**
     * Perform a snapshot.
     *
     * @param urlToSnapshot the url to snapshot.
     * @param headers the http headers to add to the snapshotting request.
     * @return the snapshot or null if the snapshot failed.
     * @throws IOException
     */
    public SnapshotResult snapshot(String urlToSnapshot, Map<String, List<String>> headers)
            throws IOException;

    /**
     * @return true if the incoming request is from the snapshotting service. false if it is not
     *     or if it is not possible to tell.
     */
    boolean isSnapshotRequest(HttpServletRequest request);
}