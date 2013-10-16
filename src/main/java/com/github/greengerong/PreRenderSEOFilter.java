package com.github.greengerong;


import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.lang.StringUtils;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static com.google.common.collect.FluentIterable.from;

public class PreRenderSEOFilter implements Filter {

    private FilterConfig filterConfig;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        this.filterConfig = filterConfig;
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
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        filterChain.doFilter(servletRequest, servletResponse);

    }

    private ResponseResult getPrerenderedPageResponse(HttpServletRequest request) throws IOException {
        final String apiUrl = getApiUrl(request.getRequestURI());
        final HttpClient httpClient = new HttpClient();
        final GetMethod getMethod = new GetMethod(apiUrl);
        setConfig(httpClient);
        setHttpHeader(getMethod);
        final int code = httpClient.executeMethod(getMethod);
        String body = new String(getMethod.getResponseBodyAsString().getBytes("utf-8"));
        return new ResponseResult(code, body);
    }

    private void setHttpHeader(HttpMethod httpMethod) {
        httpMethod.setRequestHeader("Cache-Control", "no-cache");
        httpMethod.setRequestHeader("Content-Type", "text/html");
    }

    private void setConfig(HttpClient httpClient) {
        final String proxy = filterConfig.getInitParameter("proxy");
        if (StringUtils.isNotBlank(proxy)) {
            final int proxyPort = Integer.parseInt(filterConfig.getInitParameter("proxyPort"));
            httpClient.getHostConfiguration().setProxy(proxy, proxyPort);
        }
    }

    @Override
    public void destroy() {
        filterConfig = null;
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
        final String useAgent = request.getHeader("User-Agent");
        final String url = request.getRequestURI();
        final String referer = request.getHeader("Referer");

        if (StringUtils.isBlank(useAgent)) {
            return false;
        }

        if (hasEscapedFragment(request)) {
            return true;
        }
        if (!isInSearchUserAgent(useAgent)) {
            return false;
        }

        if (isInResources(url)) {
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
        return StringUtils.isBlank(request.getParameter("_escaped_fragment_"));
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

    private boolean isInSearchUserAgent(final String useAgent) {
        return from(getCrawlerUserAgents()).anyMatch(new Predicate<String>() {
            @Override
            public boolean apply(String item) {
                return item.equalsIgnoreCase(useAgent);
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
