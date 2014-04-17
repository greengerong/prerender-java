package com.github.avaliani.snapshot;

import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Web page snapshotting service by https://ajaxsnapshots.com/
 *
 * @author avaliani
 *
 */
public class AjaxSnapshotsSnapshotService extends BaseSnapshotService {

    // TODO(avaliani): debugging therefore using http
    public static final String DEFAULT_SNAPSHOT_SERVICE_URL = "http://api.ajaxsnapshots.com/makeSnapshot";

    @Override
    public String getDefaultSnapshotServiceUrl() {
        return DEFAULT_SNAPSHOT_SERVICE_URL;
    }

    @Override
       public String getSnapshotRequestUrl(String requestUrl) {
        String baseUrl = getSnapshotServiceUrl();
        baseUrl += "?url=" + encodeURIComponent(requestUrl);
        return baseUrl;
       }

    @Override
    public Map<String, List<String>> getSnapshotRequestHeaders(String requestUrl) {
        Map<String, List<String>> headers = Maps.newHashMap();

        String serviceToken = config.getServiceToken();
        if (serviceToken != null) {
            headers.put("X-AJS-APIKEY", Lists.newArrayList(serviceToken));
        }

        // TODO(avaliani): file bug. This didn't work.
        // proxyRequest.addHeader("X-AJS-URL", incomingRequestUrl);

        // TODO(avaliani): make this a configurable option.
        headers.put("X-AJS-SNAP-TIME", Lists.newArrayList("5000"));

        return headers;
    }

    @Override
    public boolean isSnapshotRequest(HttpServletRequest request) {
        return request.getHeader("X-AJS-CALLTYPE") != null;
    }
}