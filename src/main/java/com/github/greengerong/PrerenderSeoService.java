package com.github.greengerong;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.*;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.HeaderGroup;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.Closeable;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static com.google.common.collect.FluentIterable.from;
import static org.apache.commons.lang.StringUtils.isNotBlank;
import static org.apache.http.HttpHeaders.CONTENT_LENGTH;
import static org.apache.http.HttpHeaders.HOST;

public class PrerenderSeoService {
    private final static Logger log = LoggerFactory.getLogger(PrerenderSeoService.class);
    /**
     * These are the "hop-by-hop" headers that should not be copied.
     * http://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html
     * I use an HttpClient HeaderGroup class instead of Set<String> because this
     * approach does case insensitive lookup faster.
     */
    private static final HeaderGroup hopByHopHeaders;
    private static final String ACCEPT_VALUE = "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8";
    private static final String ACCEPT_HEADER = "accept";
    public static final String ESCAPED_FRAGMENT_KEY = "_escaped_fragment_";
    private CloseableHttpClient httpClient;

    private PrerenderConfig prerenderConfig;
    private PreRenderEventHandler preRenderEventHandler;
    private static final String EMPTY_STRING = "";

    public PrerenderSeoService(Map<String, String> config,String token) {
        this.prerenderConfig = new PrerenderConfig(config);
        this.httpClient = getHttpClient();
    }

    static {
        hopByHopHeaders = new HeaderGroup();
        String[] headers = new String[]{
                "Connection", "Keep-Alive", "Proxy-Authenticate", "Proxy-Authorization",
                "TE", "Trailers", "Transfer-Encoding", "Upgrade"};
        for (String header : headers) {
            hopByHopHeaders.addHeader(new BasicHeader(header, null));
        }
    }

    public void destroy() {
        if (preRenderEventHandler != null) {
            preRenderEventHandler.destroy();
        }
        closeQuietly(httpClient);
    }

    public boolean prerenderIfEligible(HttpServletRequest servletRequest, HttpServletResponse servletResponse, String token) {
        try {
            if (handlePrerender(servletRequest, servletResponse,token)) {
                return true;
            }
        } catch (Exception e) {
            log.error("Prerender service error", e);
            System.out.println(String.format("token: %s  Exception: %s \n StackTrace: \n%s",token, e.toString(), ArrayUtils.toString(e.getStackTrace())));
        }
        return false;
    }

    private boolean handlePrerender(HttpServletRequest servletRequest, HttpServletResponse servletResponse,String token)
            throws URISyntaxException, IOException {
        if (shouldShowPrerenderedPage(token,servletRequest)) {
            System.out.println(String.format("token: %s Request is Prerendered",token));
            this.preRenderEventHandler = prerenderConfig.getEventHandler();
            if (beforeRender(servletRequest, servletResponse) || proxyPrerenderedPageResponse(token,servletRequest, servletResponse)) {
                return true;
            }
        }
        System.out.println(String.format("token: %s Request is not Prerendered",token));
        return false;
    }

    private boolean shouldShowPrerenderedPage(String token, HttpServletRequest request) throws URISyntaxException {

        final String userAgent = request.getHeader("User-Agent");
        final String url = getRequestURL(token, request);
        final String referer = request.getHeader("Referer");

        log.trace(String.format("token: %s checking request for %s from User-Agent %s and referer %s",token, url, userAgent, referer));
        System.out.println(String.format("token: %s checking request for %s from User-Agent %s and referer %s",token, url, userAgent, referer));

        if (!HttpGet.METHOD_NAME.equals(request.getMethod())) {
            log.trace(String.format("token: %s Request is not HTTP GET; intercept: no"),token);
            System.out.println(String.format("token: %s Request is not HTTP GET; intercept: no",token));
            return false;
        }

        if (isInResources(url)) {
            log.trace(String.format("token: %s request is for a (static) resource; intercept: no",token));
            System.out.println(String.format("token: %s request is for a (static) resource; intercept: no",token));
            return false;
        }
        
        String prerenderHeader = request.getHeader("X-Prerender");
        if (StringUtils.isNotEmpty(prerenderHeader)) {
            return false;
        }

        final List<String> whiteList = prerenderConfig.getWhitelist();
        if (whiteList != null && !isInWhiteList(url, whiteList)) {
            log.trace(String.format("token: %s Whitelist is enabled, but this request is not listed; intercept: no",token));
            System.out.println(String.format("token: %s Whitelist is enabled, but this request is not listed; intercept: no",token));
            return false;
        }

        final List<String> blacklist = prerenderConfig.getBlacklist();
        if (blacklist != null && isInBlackList(url, referer, blacklist)) {
            log.trace(String.format("token: %s Blacklist is enabled, and this request is listed; intercept: no",token));
            System.out.println(String.format("token: %s Blacklist is enabled, and this request is listed; intercept: no",token));
            return false;
        }

        if (hasEscapedFragment(request)) {
            log.trace(String.format("token: %s Request Has _escaped_fragment_; intercept: yes",token));
            System.out.println(String.format("token: %s Request Has _escaped_fragment_; intercept: yes",token));
            return true;
        }

        if (StringUtils.isBlank(userAgent)) {
            log.trace(String.format("token: %s Request has blank userAgent; intercept: no",token));
            System.out.println(String.format("token: %s Request has blank userAgent; intercept: no",token));
            return false;
        }

        if (!isInSearchUserAgent(userAgent)) {
            log.trace(String.format("token: %s Request User-Agent is not a search bot; intercept: no",token));
            System.out.println(String.format("token: %s Request User-Agent is not a search bot; intercept: no",token));
            return false;
        }

        log.trace(String.format("token: %s Defaulting to request intercept(user-agent=%s): yes",token, userAgent));
        System.out.println(String.format("token: %s Defaulting to request intercept(user-agent=%s): yes",token, userAgent));
        return true;
    }

    protected HttpGet getHttpGet(String apiUrl) {
        return new HttpGet(apiUrl);
    }

    protected CloseableHttpClient getHttpClient() {
        return prerenderConfig.getHttpClient();
    }

    /**
     * Copy request headers from the servlet client to the proxy request.
     *
     * @throws java.net.URISyntaxException
     */
    private void copyRequestHeaders(String token, HttpServletRequest servletRequest, HttpRequest proxyRequest)
            throws URISyntaxException {
        // Get an Enumeration of all of the header names sent by the client
        Enumeration<?> enumerationOfHeaderNames = servletRequest.getHeaderNames();
        List<String> headersName;
        while (enumerationOfHeaderNames.hasMoreElements()) {
            String headerName = (String) enumerationOfHeaderNames.nextElement();
            //Instead the content-length is effectively set via InputStreamEntity
            if (!headerName.equalsIgnoreCase(CONTENT_LENGTH) && !hopByHopHeaders.containsHeader(headerName)) {
                Enumeration<?> headers = servletRequest.getHeaders(headerName);
                while (headers.hasMoreElements()) {//sometimes more than one value
                    String headerValue = (String) headers.nextElement();
                    // In case the proxy host is running multiple virtual servers,
                    // rewrite the Host header to ensure that we get content from
                    // the correct virtual server
                    if (headerName.equalsIgnoreCase(HOST)) {
                        HttpHost host = URIUtils.extractHost(new URI(prerenderConfig.getPrerenderServiceUrl()));
                        headerValue = host.getHostName();
                        if (host.getPort() != -1) {
                            headerValue += ":" + host.getPort();
                        }
                    }
                    // Added to avoid 301 loops due to edit-connect asking json
                    if (headerName.toLowerCase().equals(ACCEPT_HEADER)){
                        System.out.println(String.format("token %s Setting up the ACCEPT header",token));
                        headerValue = ACCEPT_VALUE;
                    }
                    proxyRequest.addHeader(headerName, headerValue);
                }
            }
        }
    }

    private String getRequestURL(String token, HttpServletRequest request) {
        if (StringUtils.isNotEmpty(prerenderConfig.getForwardedURLHeader())) {
            String url = request.getHeader(prerenderConfig.getForwardedURLHeader());
            if (StringUtils.isNotEmpty(url)) {
                return url;
            }
        }
        String url = "";
        if (StringUtils.isNotEmpty(prerenderConfig.getProtocol())) {
            url = request.getRequestURL().toString();
            url = url.replace(request.getScheme(), prerenderConfig.getProtocol());
        }
        if (prerenderConfig.getPathsToRemove() != null){
            url = StringUtils.isNotEmpty(url)?url:request.getRequestURL().toString();
            for(String path: prerenderConfig.getPathsToRemove()){
                url = url.replace(path,EMPTY_STRING);
            }
        }
        if(StringUtils.isNotBlank(url)){
            System.out.println(String.format("token: %s OriginalUrl %s / NewUrl: %s",token,request.getRequestURL().toString(),url));
            return url;
        }
        return request.getRequestURL().toString();
    }

    private String getApiUrl(String url) {
        String prerenderServiceUrl = prerenderConfig.getPrerenderServiceUrl();
        if (!prerenderServiceUrl.endsWith("/")) {
            prerenderServiceUrl += "/";
        }
        return prerenderServiceUrl + url;
    }

    /**
     * Copy proxied response headers back to the servlet client.
     */
    private void copyResponseHeaders(HttpResponse proxyResponse, final HttpServletResponse servletResponse) {
        servletResponse.setCharacterEncoding(getContentCharSet(proxyResponse.getEntity()));
        from(Arrays.asList(proxyResponse.getAllHeaders())).filter(new Predicate<Header>() {
            @Override
            public boolean apply(Header header) {
                return !hopByHopHeaders.containsHeader(header.getName());
            }
        }).transform(new Function<Header, Boolean>() {
            @Override
            public Boolean apply(Header header) {
                servletResponse.addHeader(header.getName(), header.getValue());
                return true;
            }
        }).toList();
    }
    
    /**
     * Get the charset used to encode the http entity.
     */
    private String getContentCharSet(final HttpEntity entity) throws ParseException {
        if (entity == null) {
            return null;
        }
        String charset = null;
        if (entity.getContentType() != null) {
            HeaderElement values[] = entity.getContentType().getElements();
            if (values.length > 0) {
                NameValuePair param = values[0].getParameterByName("charset");
                if (param != null) {
                    charset = param.getValue();
                }
            }
        }        
        return charset;
    }

    private String getResponseHtml(HttpResponse proxyResponse)
            throws IOException {
        HttpEntity entity = proxyResponse.getEntity();
        return entity != null ? EntityUtils.toString(entity) : "";
    }

    /**
     * Copy response body data (the entity) from the proxy to the servlet client.
     */
    private void responseEntity(String html, HttpServletResponse servletResponse)
            throws IOException {
        PrintWriter printWriter = servletResponse.getWriter();
        try {
            printWriter.write(html);
            printWriter.flush();
        } finally {
            closeQuietly(printWriter);
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

    private boolean hasEscapedFragment(HttpServletRequest request) {
        return request.getParameterMap().containsKey(ESCAPED_FRAGMENT_KEY);
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

    private boolean isInSearchUserAgent(final String userAgent) {
        return from(prerenderConfig.getCrawlerUserAgents()).anyMatch(new Predicate<String>() {
            @Override
            public boolean apply(String item) {
                return userAgent.toLowerCase().contains(item.toLowerCase());
            }
        });
    }


    private boolean isInResources(final String url) {
        return from(prerenderConfig.getExtensionsToIgnore()).anyMatch(new Predicate<String>() {
            @Override
            public boolean apply(String item) {
                return (url.indexOf('?') >= 0 ? url.substring(0, url.indexOf('?')) : url)
                        .toLowerCase().endsWith(item);
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

    private boolean beforeRender(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (preRenderEventHandler != null) {
            final String html = preRenderEventHandler.beforeRender(request);
            if (isNotBlank(html)) {
                final PrintWriter writer = response.getWriter();
                writer.write(html);
                writer.flush();
                closeQuietly(writer);
                return true;
            }
        }
        return false;
    }

    private boolean proxyPrerenderedPageResponse(String token, HttpServletRequest request, HttpServletResponse response)
            throws IOException, URISyntaxException {
        final String apiUrl = getApiUrl(getFullUrl(token, request));
        log.trace(String.format("token %s Prerender proxy will send request to:%s",token, apiUrl));
        System.out.println(String.format("token %s Prerender proxy will send request to:%s", token, apiUrl));
        final HttpGet getMethod = getHttpGet(apiUrl);
        copyRequestHeaders(token,request, getMethod);
        withPrerenderToken(getMethod);
        CloseableHttpResponse prerenderServerResponse = null;

        try {
            prerenderServerResponse = httpClient.execute(getMethod);
            response.setStatus(prerenderServerResponse.getStatusLine().getStatusCode());
            copyResponseHeaders(prerenderServerResponse, response);
            String html = getResponseHtml(prerenderServerResponse);
            html = afterRender(request, response, prerenderServerResponse, html);
            responseEntity(html, response);
            return true;
        } finally {
            closeQuietly(prerenderServerResponse);
        }
    }

    private String afterRender(HttpServletRequest clientRequest, HttpServletResponse clientResponse, CloseableHttpResponse prerenderServerResponse, String responseHtml) {
        if (preRenderEventHandler != null) {
            return preRenderEventHandler.afterRender(clientRequest, clientResponse, prerenderServerResponse, responseHtml);
        }
        return responseHtml;
    }

    private void withPrerenderToken(HttpRequest proxyRequest) {
        final String token = prerenderConfig.getPrerenderToken();
        //for new version prerender with token.
        if (isNotBlank(token)) {
            proxyRequest.addHeader("X-Prerender-Token", token);
        }
    }

    private String getFullUrl(String token, HttpServletRequest request) {
        final String url = getRequestURL(token, request);
        final String queryString = request.getQueryString();
        return isNotBlank(queryString) ? String.format("%s?%s", url, queryString) : url;
    }
}
