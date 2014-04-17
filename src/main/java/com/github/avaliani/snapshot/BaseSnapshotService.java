package com.github.avaliani.snapshot;

import static com.github.avaliani.snapshot.SeoFilter.LOG_LEVEL;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;

public abstract class BaseSnapshotService implements SnapshotService {

    final static java.util.logging.Logger log =
            java.util.logging.Logger.getLogger(BaseSnapshotService.class.getName());

    protected SnapshotServiceConfig config;

    @Override
    public final void init(SnapshotServiceConfig config) {
        this.config = config;
    }

    public final String getSnapshotServiceUrl() {
        String configUrl = config.getServiceUrl();
        String serviceUrl = (configUrl == null) ? getDefaultSnapshotServiceUrl() : configUrl;
        return config.getRequestScheme() + "://" + stripUrlScheme(serviceUrl);
    }

    private static String stripUrlScheme(String url) {
        if (url.toLowerCase().startsWith("http://")) {
            return url.substring("http://".length());
        } else if (url.toLowerCase().startsWith("https://")) {
            return url.substring("https://".length());
        } else {
            return url;
        }
    }

    public abstract Map<String, List<String>> getSnapshotRequestHeaders(String incomingRequestUrl);

    public abstract String getDefaultSnapshotServiceUrl();

       public abstract String getSnapshotRequestUrl(String requestUrl);

       @Override
    public final SnapshotResult snapshot(String urlToSnapshot, Map<String, List<String>> headers) throws IOException {
        final String apiUrl = getSnapshotRequestUrl(urlToSnapshot);
        log.log(LOG_LEVEL, String.format("Prerender proxy will send request to:%s", apiUrl));

        URL url = new URL(apiUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        copyRequestHeaders(connection, headers);
        copyRequestHeaders(connection, getSnapshotRequestHeaders(urlToSnapshot));
        connection.setReadTimeout(60 * 1000);

        log.log(LOG_LEVEL, "Pre-render service making request:\n");
        dump(connection);

        if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
            log.log(LOG_LEVEL, "SUCCESS: Pre-render service response:\n");
            dumpResponse(connection, false);

            return new SnapshotResult(getResponse(connection), getResponseHeaders(connection));
        } else {
            log.log(LOG_LEVEL, "ERROR: Pre-render service response:\n");
            dumpResponse(connection, true);
            return null;
        }

    }

    private static void copyRequestHeaders(HttpURLConnection connection, Map<String, List<String>> headers) {
        for (Map.Entry<String, List<String>> header : headers.entrySet()) {
            for (String headerValue : header.getValue()) {
                connection.addRequestProperty(header.getKey(), headerValue);
            }
        }
    }

    /**
     * Copy proxied response headers back to the servlet client.
     */
    private static Map<String, List<String>> getResponseHeaders(HttpURLConnection connection) {
        return connection.getHeaderFields();
    }

    /**
     * Copy response from the proxy to the servlet client.
     */
    private static String getResponse(HttpURLConnection connection) throws IOException {
        StringWriter respWriter = new StringWriter();
        IOUtils.copy(connection.getInputStream(), respWriter);
        return respWriter.toString();
    }


    private static void dump(HttpURLConnection connection) {
        StringBuilder output = new StringBuilder();
        output.append("  " + connection + "\n");
        Map<String,List<String>> headers = connection.getRequestProperties();
        for (Map.Entry<String, List<String>> header : headers.entrySet()) {
            output.append("    " + header.getKey() + " : " + mergeHeaderValues(header.getValue()) + "\n");
        }
        log.log(LOG_LEVEL, output.toString());
    }

    private static void dumpResponse(HttpURLConnection connection, boolean dumpContent) throws IOException {
        StringBuilder output = new StringBuilder();
        output.append("  " + connection.getResponseMessage() + "\n");
        Map<String,List<String>> headers = connection.getHeaderFields();
        for (Map.Entry<String, List<String>> header : headers.entrySet()) {
            output.append("    " + header.getKey() + " : " + mergeHeaderValues(header.getValue()) + "\n");
        }
        if (dumpContent) {
            StringWriter respWriter = new StringWriter();
            IOUtils.copy(connection.getInputStream(), respWriter);
            output.append(respWriter.toString());
        }
        log.log(LOG_LEVEL, output.toString());
    }

    private static String mergeHeaderValues(List<String> headerValues) {
        StringBuilder mergedValue = new StringBuilder();
        for (String headerValue : headerValues) {
            if (mergedValue.length() > 0) {
                mergedValue.append(",");
            }
            mergedValue.append(headerValue);
        }
        return mergedValue.toString();
    }

    // TODO(avaliani): move to a utility class
    public static String encodeURIComponent(String s) {
        String result;
        try {
            result = URLEncoder.encode(s, "UTF-8")
                    .replaceAll("\\+", "%20")
                    .replaceAll("\\%21", "!")
                    .replaceAll("\\%27", "'")
                    .replaceAll("\\%28", "(")
                    .replaceAll("\\%29", ")")
                    .replaceAll("\\%7E", "~");
        } catch (UnsupportedEncodingException e) {
            result = s;
        }
        return result;
    }
}