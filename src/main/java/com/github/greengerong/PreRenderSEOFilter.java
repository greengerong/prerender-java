package com.github.greengerong;


import com.google.common.base.Predicate;
import org.apache.commons.lang.StringUtils;
import org.apache.http.*;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.HeaderGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Pattern;

import static com.google.common.collect.FluentIterable.from;

public class PreRenderSEOFilter implements Filter {

    private final static Logger log = LoggerFactory.getLogger(PreRenderSEOFilter.class);
    private CloseableHttpClient httpClient;
    private PreRenderEventHandler preRenderEventHandler;
    private PrerenderConfig prerenderConfig;
    public static final int HTTP_OK = 200;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        this.prerenderConfig = new PrerenderConfig(filterConfig);
        this.httpClient = getHttpClient();
        this.preRenderEventHandler = prerenderConfig.getEventHandler();
    }

    protected CloseableHttpClient getHttpClient() {
        return prerenderConfig.getHttpClient();
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {
        try {
            final HttpServletRequest request = (HttpServletRequest) servletRequest;
            final HttpServletResponse response = (HttpServletResponse) servletResponse;
            if (shouldShowPrerenderedPage(request)) {

                if (beforeRender(request, response) || proxyPrerenderedPageResponse(request, response)) {
                    return;
                }
            }
        } catch (Exception e) {
            log.error("Prerender service error", e);
        }
        filterChain.doFilter(servletRequest, servletResponse);
    }

    private boolean beforeRender(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (preRenderEventHandler != null) {
            final String html = preRenderEventHandler.beforeRender(request);
            if (StringUtils.isNotBlank(html)) {
                final PrintWriter writer = response.getWriter();
                writer.write(html);
                writer.flush();
                closeQuietly(writer);
                return true;
            }
        }
        return false;
    }

    private boolean proxyPrerenderedPageResponse(HttpServletRequest request, HttpServletResponse response) throws IOException, URISyntaxException {
        final String apiUrl = getApiUrl(getFullUrl(request));
        log.trace(String.format("Prerender proxy will send request to:%s", apiUrl));
        final HttpGet getMethod = getHttpGet(apiUrl);
        copyRequestHeaders(request, getMethod);
        withPrerenderToken(getMethod);
        CloseableHttpResponse proxyResponse = null;

        try {
            proxyResponse = httpClient.execute(getMethod);
            if (proxyResponse.getStatusLine().getStatusCode() == HTTP_OK) {
                afterRender(request, proxyResponse);
                copyResponseHeaders(proxyResponse, response);
                copyResponseEntity(proxyResponse, response);
                return true;
            }
        } finally {
            closeQuietly(proxyResponse);
        }
        return false;
    }

    protected HttpGet getHttpGet(String apiUrl) {
        return new HttpGet(apiUrl);
    }

    private void afterRender(HttpServletRequest request, CloseableHttpResponse proxyResponse) {
        if (preRenderEventHandler != null) {
            preRenderEventHandler.afterRender(request, proxyResponse);
        }
    }

    private void withPrerenderToken(HttpRequest proxyRequest) {
        final String token = prerenderConfig.getPrerenderToken();
        //for new version prerender with token.
        if (StringUtils.isNotBlank(token)) {
            proxyRequest.addHeader("X-Prerender-Token", token);
        }
    }

    /**
     * Copy proxied response headers back to the servlet client.
     */
    protected void copyResponseHeaders(HttpResponse proxyResponse, HttpServletResponse servletResponse) {
        for (Header header : proxyResponse.getAllHeaders()) {
            if (!hopByHopHeaders.containsHeader(header.getName())) {
                servletResponse.addHeader(header.getName(), header.getValue());
            }
        }
    }

    /**
     * Copy response body data (the entity) from the proxy to the servlet client.
     */
    protected void copyResponseEntity(HttpResponse proxyResponse, HttpServletResponse servletResponse) throws IOException {
        HttpEntity entity = proxyResponse.getEntity();
        if (entity != null) {
            OutputStream servletOutputStream = servletResponse.getOutputStream();
            try {
                entity.writeTo(servletOutputStream);
            } finally {
                closeQuietly(servletOutputStream);
            }
        }
    }

    protected void closeQuietly(Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (IOException e) {
            log.error("Close proxy error", e);
        }
    }

    /**
     * These are the "hop-by-hop" headers that should not be copied.
     * http://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html
     * I use an HttpClient HeaderGroup class instead of Set<String> because this
     * approach does case insensitive lookup faster.
     */
    protected static final HeaderGroup hopByHopHeaders;

    static {
        hopByHopHeaders = new HeaderGroup();
        String[] headers = new String[]{
                "Connection", "Keep-Alive", "Proxy-Authenticate", "Proxy-Authorization",
                "TE", "Trailers", "Transfer-Encoding", "Upgrade"};
        for (String header : headers) {
            hopByHopHeaders.addHeader(new BasicHeader(header, null));
        }
    }

    /**
     * Copy request headers from the servlet client to the proxy request.
     *
     * @throws URISyntaxException
     */
    protected void copyRequestHeaders(HttpServletRequest servletRequest, HttpRequest proxyRequest) throws URISyntaxException {
        // Get an Enumeration of all of the header names sent by the client
        Enumeration<?> enumerationOfHeaderNames = servletRequest.getHeaderNames();
        while (enumerationOfHeaderNames.hasMoreElements()) {
            String headerName = (String) enumerationOfHeaderNames.nextElement();
            //Instead the content-length is effectively set via InputStreamEntity
            if (!headerName.equalsIgnoreCase(HttpHeaders.CONTENT_LENGTH) && !hopByHopHeaders.containsHeader(headerName)) {
                Enumeration<?> headers = servletRequest.getHeaders(headerName);
                while (headers.hasMoreElements()) {//sometimes more than one value
                    String headerValue = (String) headers.nextElement();
                    // In case the proxy host is running multiple virtual servers,
                    // rewrite the Host header to ensure that we get content from
                    // the correct virtual server
                    if (headerName.equalsIgnoreCase(HttpHeaders.HOST)) {
                        HttpHost host = URIUtils.extractHost(new URI(prerenderConfig.getPrerenderServiceUrl()));
                        headerValue = host.getHostName();
                        if (host.getPort() != -1) {
                            headerValue += ":" + host.getPort();
                        }
                    }
                    proxyRequest.addHeader(headerName, headerValue);
                }
            }
        }
    }

    private String getFullUrl(HttpServletRequest request) {
        final StringBuffer url = request.getRequestURL();
        final String queryString = request.getQueryString();
        if (queryString != null) {
            url.append('?');
            url.append(queryString);
        }
        return url.toString();
    }

    @Override
    public void destroy() {
        prerenderConfig = null;
        closeQuietly(httpClient);
    }

    private boolean shouldShowPrerenderedPage(HttpServletRequest request) throws URISyntaxException {
        final String userAgent = request.getHeader("User-Agent");
        final String url = request.getRequestURL().toString();
        final String referer = request.getHeader("Referer");

        log.trace("checking request for " + url + " from User-Agent " + userAgent + " and referer " + referer);

        if (!HttpGet.METHOD_NAME.equals(request.getMethod())) {
            log.trace("Request is not HTTP GET; intercept: no");
            return false;
        }

        if (hasEscapedFragment(request)) {
            log.trace("Request Has _escaped_fragment_; intercept: yes");
            return true;
        }

        if (StringUtils.isBlank(userAgent)) {
            log.trace("Request has blank userAgent; intercept: no");
            return false;
        }

        if (!isInSearchUserAgent(userAgent)) {
            log.trace("Request User-Agent is not a search bot; intercept: no");
            return false;
        }

        if (isInResources(url)) {
            log.trace("request is for a (static) resource; intercept: no");
            return false;
        }

        final List<String> whiteList = prerenderConfig.getWhitelist();
        if (whiteList != null && !isInWhiteList(url, whiteList)) {
            log.trace("Whitelist is enabled, but this request is not listed; intercept: no");
            return false;
        }

        final List<String> blacklist = prerenderConfig.getBlacklist();
        if (blacklist != null && isInBlackList(url, referer, blacklist)) {
            log.trace("Blacklist is enabled, and this request is listed; intercept: no");
            return false;
        }

        log.trace("Defaulting to request intercept: yes");
        return true;
    }

    private boolean hasEscapedFragment(HttpServletRequest request) {
        return request.getParameterMap().containsKey("_escaped_fragment_");
    }

    private String getApiUrl(String url) {
        String prerenderServiceUrl = prerenderConfig.getPrerenderServiceUrl();
        if (!prerenderServiceUrl.endsWith("/")) {
            prerenderServiceUrl += "/";
        }
        return prerenderServiceUrl + url;
    }

    private boolean isInBlackList(final String url, final String referer, List<String> blacklist) {
        return from(blacklist).anyMatch(new Predicate<String>() {
            @Override
            public boolean apply(String regex) {
                final Pattern pattern = Pattern.compile(regex);
                return pattern.matcher(url).matches() ||
                        (!StringUtils.isBlank(referer) && pattern.matcher(referer).matches());
            }
        });
    }

    private boolean isInWhiteList(final String url, List<String> whitelist) {
        return from(whitelist).anyMatch(new Predicate<String>() {
            @Override
            public boolean apply(String regex) {
                return Pattern.compile(regex).matcher(url).matches();
            }
        });
    }

    private boolean isInResources(final String url) {
        return from(prerenderConfig.getExtensionsToIgnore()).anyMatch(new Predicate<String>() {
            @Override
            public boolean apply(String item) {
                return url.contains(item.toLowerCase());
            }
        });
    }

    private boolean isInSearchUserAgent(final String userAgent) {
        return from(prerenderConfig.getCrawlerUserAgents()).anyMatch(new Predicate<String>() {
            @Override
            public boolean apply(String item) {
                return userAgent.toLowerCase().indexOf(item.toLowerCase()) >= 0;
            }
        });
    }

}
