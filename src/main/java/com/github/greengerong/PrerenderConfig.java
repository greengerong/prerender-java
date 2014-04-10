package com.github.greengerong;


import com.google.common.collect.Lists;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.FilterConfig;
import java.util.Arrays;
import java.util.List;

public class PrerenderConfig {
    private final static Logger log = LoggerFactory.getLogger(PrerenderConfig.class);
    private FilterConfig filterConfig;

    public PrerenderConfig(FilterConfig filterConfig) {
        this.filterConfig = filterConfig;
    }

    public PreRenderEventHandler getEventHandler() {
        final String preRenderEventHandler = filterConfig.getInitParameter("preRenderEventHandler");
        if (StringUtils.isNotBlank(preRenderEventHandler)) {
            try {
                return (PreRenderEventHandler) Class.forName(preRenderEventHandler).newInstance();
            } catch (Exception e) {
                log.error("PreRenderEventHandler class not find or can not new a instance", e);
            }
        }
        return null;
    }

    public CloseableHttpClient getHttpClient() {
        HttpClientBuilder builder = HttpClients.custom();
        final String proxy = filterConfig.getInitParameter("proxy");
        if (StringUtils.isNotBlank(proxy)) {
            final int proxyPort = Integer.parseInt(filterConfig.getInitParameter("proxyPort"));
            DefaultProxyRoutePlanner routePlanner = new DefaultProxyRoutePlanner(new HttpHost(proxy, proxyPort));
            builder = builder.setRoutePlanner(routePlanner);
        }

        builder = builder.setConnectionManager(new PoolingHttpClientConnectionManager());
        return builder.build();
    }

    public String getPrerenderToken() {
        return filterConfig.getInitParameter("prerenderToken");
    }

    public String getForwardedURLHeader() {
        return filterConfig.getInitParameter("forwardedURLHeader");
    }

    public List<String> getCrawlerUserAgents() {
        List<String> crawlerUserAgents = Lists.newArrayList("googlebot", "yahoo", "bingbot", "baiduspider",
                "facebookexternalhit", "twitterbot", "rogerbot", "linkedinbot", "embedly");
        final String crawlerUserAgentsFromConfig = filterConfig.getInitParameter("crawlerUserAgents");
        if (StringUtils.isNotBlank(crawlerUserAgentsFromConfig)) {
            crawlerUserAgents.addAll(Arrays.asList(crawlerUserAgentsFromConfig.trim().split(",")));
        }

        return crawlerUserAgents;
    }

    public List<String> getExtensionsToIgnore() {
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

    public List<String> getWhitelist() {
        final String whitelist = filterConfig.getInitParameter("whitelist");
        if (StringUtils.isNotBlank(whitelist)) {
            return Arrays.asList(whitelist.trim().split(","));
        }
        return null;
    }

    public List<String> getBlacklist() {
        final String blacklist = filterConfig.getInitParameter("blacklist");
        if (StringUtils.isNotBlank(blacklist)) {
            return Arrays.asList(blacklist.trim().split(","));
        }
        return null;
    }

    public String getPrerenderServiceUrl() {
        final String prerenderServiceUrl = filterConfig.getInitParameter("prerenderServiceUrl");
        return StringUtils.isNotBlank(prerenderServiceUrl) ? prerenderServiceUrl : "http://service.prerender.io/";
    }
}
