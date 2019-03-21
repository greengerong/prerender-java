package com.github.greengerong;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.lang.ArrayUtils;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class PreRenderSEOFilter implements Filter {
    public static final List<String> PARAMETER_NAMES = Lists.newArrayList("preRenderEventHandler", "proxy", "proxyPort",
            "prerenderToken", "forwardedURLHeader", "crawlerUserAgents", "extensionsToIgnore", "whitelist",
            "blacklist", "prerenderServiceUrl", "protocol","pathsToIgnore","qsappend","requestTimeOut");
    private PrerenderSeoService prerenderSeoService;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        this.prerenderSeoService = new PrerenderSeoService(toMap(filterConfig),java.util.UUID.randomUUID().toString());
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {
        String token = java.util.UUID.randomUUID().toString();

        try {
            boolean isPrerendered = prerenderSeoService.prerenderIfEligible(
                    (HttpServletRequest) servletRequest, (HttpServletResponse) servletResponse,token );
            if (!isPrerendered) {
                filterChain.doFilter(servletRequest, servletResponse);
            }
        }catch (Exception e){
            e.printStackTrace();
            System.out.println(String.format("Token: %s Exception: %s \n StackTrace: \n%s",token, e.toString(), ArrayUtils.toString(e.getStackTrace())));
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

