package com.github.greengerong;


import com.google.common.collect.Lists;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class PrerenderConfig {
    private final static Logger log = LoggerFactory.getLogger(PrerenderConfig.class);
    private Map<String, String> config;

    public PrerenderConfig(Map<String, String> config) {
        this.config = config;
    }

    public PreRenderEventHandler getEventHandler() {
        final String preRenderEventHandler = config.get("preRenderEventHandler");
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
        builder = configureProxy(builder);
        builder = configureTimeout(builder);
        builder = builder.setConnectionManager(new PoolingHttpClientConnectionManager());
        return builder.build();
    }

    private HttpClientBuilder configureProxy(HttpClientBuilder builder) {
        final String proxy = config.get("proxy");
        if (StringUtils.isNotBlank(proxy)) {
            final int proxyPort = Integer.parseInt(config.get("proxyPort"));
            DefaultProxyRoutePlanner routePlanner = new DefaultProxyRoutePlanner(new HttpHost(proxy, proxyPort));
            builder = builder.setRoutePlanner(routePlanner);
        }
        return builder;
    }

    private HttpClientBuilder configureTimeout(HttpClientBuilder builder) {
        final String socketTimeout = getSocketTimeout();
        if (socketTimeout != null) {
            RequestConfig config = RequestConfig.custom().setSocketTimeout(Integer.parseInt(socketTimeout)).build();
            builder = builder.setDefaultRequestConfig(config);
        }
        return builder;
    }

    public String getSocketTimeout() {
        return config.get("socketTimeout");
    }

    public String getPrerenderToken() {
        return config.get("prerenderToken");
    }

    public String getForwardedURLHeader() {
        return config.get("forwardedURLHeader");
    }

    public List<String> getCrawlerUserAgents() {
        List<String> crawlerUserAgents = Lists.newArrayList("googlebot", "yahoo", "bingbot", "baiduspider",
                "facebookexternalhit", "twitterbot", "rogerbot", "linkedinbot", "embedly");
        final String crawlerUserAgentsFromConfig = config.get("crawlerUserAgents");
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
        final String extensionsToIgnoreFromConfig = config.get("extensionsToIgnore");
        if (StringUtils.isNotBlank(extensionsToIgnoreFromConfig)) {
            extensionsToIgnore.addAll(Arrays.asList(extensionsToIgnoreFromConfig.trim().split(",")));
        }

        return extensionsToIgnore;
    }

    public List<String> getWhitelist() {
        final String whitelist = config.get("whitelist");
        if (StringUtils.isNotBlank(whitelist)) {
            return Arrays.asList(whitelist.trim().split(","));
        }
        return null;
    }

    public List<String> getBlacklist() {
        final String blacklist = config.get("blacklist");
        if (StringUtils.isNotBlank(blacklist)) {
            return Arrays.asList(blacklist.trim().split(","));
        }
        return null;
    }

    public String getPrerenderServiceUrl() {
        final String prerenderServiceUrl = config.get("prerenderServiceUrl");
        return StringUtils.isNotBlank(prerenderServiceUrl) ? prerenderServiceUrl : "http://service.prerender.io/";
    }
}
