package com.github.greengerong;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class PreRenderSEOFilter implements Filter {
    public static final List<String> PARAMETER_NAMES = Lists.newArrayList("preRenderEventHandler", "proxy", "proxyPort",
            "prerenderToken", "forwardedURLHeader", "crawlerUserAgents", "extensionsToIgnore", "whitelist",
            "blacklist", "prerenderServiceUrl", "protocol");
    private PrerenderSeoService prerenderSeoService;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        this.prerenderSeoService = new PrerenderSeoService(toMap(filterConfig));
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {
        boolean isPrerendered = prerenderSeoService.prerenderIfEligible(
                (HttpServletRequest) servletRequest, (HttpServletResponse) servletResponse);
        if (!isPrerendered) {
            filterChain.doFilter(servletRequest, servletResponse);
        }
    }

    @Override
    public void destroy() {
        prerenderSeoService.destroy();
    }

    protected void setPrerenderSeoService(PrerenderSeoService prerenderSeoService) {
        this.prerenderSeoService = prerenderSeoService;
    }

    protected Map<String, String> toMap(FilterConfig filterConfig) {
        Map<String, String> config = Maps.newHashMap();
        for (String parameterName : PARAMETER_NAMES) {
            config.put(parameterName, filterConfig.getInitParameter(parameterName));
        }
        return config;
    }
}

