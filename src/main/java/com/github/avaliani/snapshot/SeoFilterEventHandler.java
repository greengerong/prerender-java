package com.github.avaliani.snapshot;


import javax.servlet.http.HttpServletRequest;

/**
 * An event handler to be invoked before and after the SEO filter encounters a URL that
 * requires snapshotting.
 *
 * @author avaliani
 *
 */
public interface SeoFilterEventHandler {

    /**
     * Invoked prior to the SEO filters snapshot of the clientRequest.
     *
     * @param clientRequest the request that will be snapshotted.
     * @return a snapshot result to use instead of taking a new snapshot. Or null
     *     to proceed with the snapshot.
     */
    SnapshotResult beforeSnapshot(HttpServletRequest clientRequest);

    /**
     * Invoked after the SEO filter takes a successful snapshot of the clientRequest.
     *
     * @param clientRequest the request that was snapshotted.
     * @param result the result of the snapshotting operation.
     */
    void afterSnapshot(HttpServletRequest clientRequest, SnapshotResult result);

    void destroy();
}
