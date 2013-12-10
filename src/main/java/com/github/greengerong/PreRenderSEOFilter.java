package com.github.greengerong;


import static com.google.common.collect.FluentIterable.from;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import com.google.common.base.Predicate;
import com.google.common.collect.Lists;

public class PreRenderSEOFilter implements Filter {

    private FilterConfig filterConfig;
    
    private CloseableHttpClient httpClient;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        this.filterConfig = filterConfig;
        
        HttpClientBuilder builder = HttpClients.custom();
        
        final String proxy = filterConfig.getInitParameter("proxy");
        if (StringUtils.isNotBlank(proxy)) {
            final int proxyPort = Integer.parseInt(filterConfig.getInitParameter("proxyPort"));
        	DefaultProxyRoutePlanner routePlanner = new DefaultProxyRoutePlanner(new HttpHost(proxy, proxyPort));
            builder = builder.setRoutePlanner(routePlanner);
        }
        
        builder = builder.setConnectionManager(new PoolingHttpClientConnectionManager());
        httpClient = builder.build();
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {
        try {
            final HttpServletRequest request = (HttpServletRequest) servletRequest;
            if (shouldShowPrerenderedPage(request)) {
                final ResponseResult result = getPrerenderedPageResponse(request);
                if (result.getStatusCode() == 200) {
                    final PrintWriter writer = servletResponse.getWriter();
                    writer.write(result.getResponseBody());
                    writer.flush();
                    return;
                }
            }
        } catch (Exception e) {
        }
        filterChain.doFilter(servletRequest, servletResponse);
    }

    private ResponseResult getPrerenderedPageResponse(HttpServletRequest request) throws IOException {
        final String apiUrl = getApiUrl(getFullUrl(request));
        final HttpGet getMethod = new HttpGet(apiUrl);
        setHttpHeader(getMethod);
        CloseableHttpResponse response = httpClient.execute(getMethod);
        try {
            final int code = response.getStatusLine().getStatusCode();
            String body = IOUtils.toString(response.getEntity().getContent(), "utf-8");
            return new ResponseResult(code, body);
        } finally {
        	response.close();
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

    private void setHttpHeader(HttpGet httpMethod) {
        httpMethod.setHeader("Cache-Control", "no-cache");
        httpMethod.setHeader("Content-Type", "text/html");
    }

    @Override
    public void destroy() {
        filterConfig = null;
        try {
			httpClient.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
    }

    private List<String> getCrawlerUserAgents() {
        List<String> crawlerUserAgents = Lists.newArrayList("googlebot", "yahoo", "bingbot", "baiduspider",
                "facebookexternalhit", "twitterbot");
        final String crawlerUserAgentsFromConfig = filterConfig.getInitParameter("crawlerUserAgents");
        if (StringUtils.isNotBlank(crawlerUserAgentsFromConfig)) {
            crawlerUserAgents.addAll(Arrays.asList(crawlerUserAgentsFromConfig.trim().split(",")));
        }

        return crawlerUserAgents;
    }

    private List<String> getExtensionsToIgnore() {
        List<String> extensionsToIgnore = Lists.newArrayList(".js", ".css", ".less", ".png", ".jpg", ".jpeg",
                ".gif", ".pdf", ".doc", ".txt", ".zip", ".mp3", ".rar", ".exe", ".wmv", ".doc", ".avi", ".ppt", ".mpg",
                ".mpeg", ".tif", ".wav", ".mov", ".psd", ".ai", ".xls", ".mp4", ".m4a", ".swf", ".dat", ".dmg",
                ".iso", ".flv", ".m4v", ".torrent");
        final String extensionsToIgnoreFromConfig = filterConfig.getInitParameter("extensionsToIgnore");
        if (StringUtils.isNotBlank(extensionsToIgnoreFromConfig)) {
            extensionsToIgnore.addAll(Arrays.asList(extensionsToIgnoreFromConfig.trim().split(",")));
        }

        return extensionsToIgnore;
    }

    private List<String> getWhitelist() {
        final String whitelist = filterConfig.getInitParameter("whitelist");
        if (StringUtils.isNotBlank(whitelist)) {
            return Arrays.asList(whitelist.trim().split(","));
        }
        return null;
    }

    private List<String> getBlacklist() {
        final String blacklist = filterConfig.getInitParameter("blacklist");
        if (StringUtils.isNotBlank(blacklist)) {
            return Arrays.asList(blacklist.trim().split(","));
        }
        return null;
    }

    private boolean shouldShowPrerenderedPage(HttpServletRequest request) throws URISyntaxException {
        final String userAgent = request.getHeader("User-Agent");
        final String url = request.getRequestURL().toString();
        final String referer = request.getHeader("Referer");

        if (!HttpGet.METHOD_NAME.equals(request.getMethod())) {
        	// only respond to GET requests
        	return false;
        }
        
        if (hasEscapedFragment(request)) {
        	// request has the escape fragment, as defined by google, intercept the request
            return true;
        }

        if (StringUtils.isBlank(userAgent)) {
        	// no User-Agent header, don't intercept
            return false;
        }

        if (!isInSearchUserAgent(userAgent)) {
        	// User-Agent is not a search bot, don't intercept
            return false;
        }

        if (isInResources(url)) {
        	// request is for a (static) resource, don't intercept
            return false;
        }

        final List<String> whiteList = getWhitelist();
        if (whiteList != null && !isInWhiteList(url, whiteList)) {
            return false;
        }

        final List<String> blacklist = getBlacklist();
        if (blacklist != null && isInBlackList(url, referer, blacklist)) {
            return false;
        }

        return true;
    }

    private boolean hasEscapedFragment(HttpServletRequest request) {
        return request.getParameterMap().containsKey("_escaped_fragment_");
    }

    private String getApiUrl(String url) {
        String prerenderServiceUrl = getPrerenderServiceUrl();
        if (!prerenderServiceUrl.endsWith("/")) {
            prerenderServiceUrl += "/";
        }
        return prerenderServiceUrl + url;
    }

    private String getPrerenderServiceUrl() {
        final String prerenderServiceUrl = filterConfig.getInitParameter("prerenderServiceUrl");
        return StringUtils.isNotBlank(prerenderServiceUrl) ? prerenderServiceUrl : "http://prerender.herokuapp.com/";
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
        return from(getExtensionsToIgnore()).anyMatch(new Predicate<String>() {
            @Override
            public boolean apply(String item) {
                return url.contains(item.toLowerCase());
            }
        });
    }

    private boolean isInSearchUserAgent(final String userAgent) {
        return from(getCrawlerUserAgents()).anyMatch(new Predicate<String>() {
            @Override
            public boolean apply(String item) {
                return userAgent.toLowerCase().indexOf(item.toLowerCase()) >= 0;
            }
        });
    }

    private class ResponseResult {
        private int statusCode;
        private String responseBody;

        public ResponseResult(int code, String body) {
            statusCode = code;
            responseBody = body;
        }

        private int getStatusCode() {
            return statusCode;
        }

        private String getResponseBody() {
            return responseBody;
        }
    }
}
