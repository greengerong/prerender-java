package com.github.greengerong;

import com.google.common.collect.Maps;
import org.apache.http.Header;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Enumeration;
import java.util.HashMap;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class PreRenderSEOFilterTest {

    public static final int NOT_FOUND = 404;
    private PreRenderSEOFilter preRenderSEOFilter;

    @Mock
    private CloseableHttpClient httpClient;

    @Mock
    private FilterConfig filterConfig;

    @Mock
    private HttpServletRequest servletRequest;

    @Mock
    private HttpServletResponse servletResponse;

    @Mock
    private FilterChain filterChain;

    @Mock
    private HttpGet httpGet;

    @Before
    public void setUp() throws Exception {
        preRenderSEOFilter = new PreRenderSEOFilter() {
            @Override
            protected CloseableHttpClient getHttpClient() {
                return httpClient;
            }

            @Override
            protected HttpGet getHttpGet(String apiUrl) {
                return httpGet;
            }
        };
    }

    @Test
    public void should_not_handle_when_non_get_request() throws Exception {
        //given
        preRenderSEOFilter.init(filterConfig);
        when(servletRequest.getRequestURL()).thenReturn(new StringBuffer());
        when(servletRequest.getMethod()).thenReturn(HttpPost.METHOD_NAME);

        //when
        preRenderSEOFilter.doFilter(servletRequest, servletResponse, filterChain);

        //then
        verify(httpClient, never()).execute(httpGet);
        verify(filterChain).doFilter(servletRequest, servletResponse);
    }

    @Test
    public void should_handle_when_url_with_escaped_fragment_() throws Exception {
        //given
        preRenderSEOFilter.init(filterConfig);
        final CloseableHttpResponse httpResponse = mock(CloseableHttpResponse.class);
        final StatusLine statusLine = mock(StatusLine.class);

        when(servletRequest.getRequestURL()).thenReturn(new StringBuffer("http://localhost/test"));
        when(servletRequest.getMethod()).thenReturn(HttpGet.METHOD_NAME);
        when(servletRequest.getHeaderNames()).thenReturn(mock(Enumeration.class));
        when(httpClient.execute(httpGet)).thenReturn(httpResponse);
        when(httpResponse.getStatusLine()).thenReturn(statusLine);
        final HashMap<String, String> map = Maps.newHashMap();
        map.put("_escaped_fragment_", "");
        when(servletRequest.getParameterMap()).thenReturn(map);
        when(statusLine.getStatusCode()).thenReturn(PreRenderSEOFilter.HTTP_OK);
        when(httpResponse.getAllHeaders()).thenReturn(new Header[0]);
        //when
        preRenderSEOFilter.doFilter(servletRequest, servletResponse, filterChain);

        //then
        verify(httpClient).execute(httpGet);
        verify(filterChain, never()).doFilter(servletRequest, servletResponse);
    }

    @Test
    public void should_not_handle_when_user_agent_is_not_crawler() throws Exception {
        //given
        preRenderSEOFilter.init(filterConfig);

        when(servletRequest.getRequestURL()).thenReturn(new StringBuffer("http://localhost/test"));
        when(servletRequest.getMethod()).thenReturn(HttpGet.METHOD_NAME);
        when(servletRequest.getParameterMap()).thenReturn(Maps.<String, String>newHashMap());
        when(servletRequest.getHeader("User-Agent")).thenReturn("no");
        //when
        preRenderSEOFilter.doFilter(servletRequest, servletResponse, filterChain);

        //then
        verify(httpClient, never()).execute(httpGet);
        verify(filterChain).doFilter(servletRequest, servletResponse);
    }

    @Test
    public void should_not_handle_when_url_is_a_resource() throws Exception {
        //given
        when(filterConfig.getInitParameter("crawlerUserAgents")).thenReturn("crawler1,crawler2");
        preRenderSEOFilter.init(filterConfig);

        when(servletRequest.getRequestURL()).thenReturn(new StringBuffer("http://localhost/test.js"));
        when(servletRequest.getMethod()).thenReturn(HttpGet.METHOD_NAME);
        when(servletRequest.getParameterMap()).thenReturn(Maps.<String, String>newHashMap());
        when(servletRequest.getHeader("User-Agent")).thenReturn("crawler1");
        //when
        preRenderSEOFilter.doFilter(servletRequest, servletResponse, filterChain);

        //then
        verify(httpClient, never()).execute(httpGet);
        verify(filterChain).doFilter(servletRequest, servletResponse);
    }

    @Test
    public void should_not_handle_when_white_list_is_not_empty_and_url_is_not_in_white_list() throws Exception {
        //given
        when(filterConfig.getInitParameter("crawlerUserAgents")).thenReturn("crawler1,crawler2");
        when(filterConfig.getInitParameter("whitelist")).thenReturn("whitelist1,whitelist2");
        preRenderSEOFilter.init(filterConfig);

        when(servletRequest.getRequestURL()).thenReturn(new StringBuffer("http://localhost/test"));
        when(servletRequest.getMethod()).thenReturn(HttpGet.METHOD_NAME);
        when(servletRequest.getParameterMap()).thenReturn(Maps.<String, String>newHashMap());
        when(servletRequest.getHeader("User-Agent")).thenReturn("crawler1");
        //when
        preRenderSEOFilter.doFilter(servletRequest, servletResponse, filterChain);

        //then
        verify(httpClient, never()).execute(httpGet);
        verify(filterChain).doFilter(servletRequest, servletResponse);
    }

    @Test
    public void should_not_handle_when_black_list_is_not_empty_and_url_is_in_black_list() throws Exception {
        //given
        when(filterConfig.getInitParameter("crawlerUserAgents")).thenReturn("crawler1,crawler2");
        when(filterConfig.getInitParameter("blacklist")).thenReturn("blacklist1,http://localhost/test");
        preRenderSEOFilter.init(filterConfig);

        when(servletRequest.getRequestURL()).thenReturn(new StringBuffer("http://localhost/test"));
        when(servletRequest.getMethod()).thenReturn(HttpGet.METHOD_NAME);
        when(servletRequest.getParameterMap()).thenReturn(Maps.<String, String>newHashMap());
        when(servletRequest.getHeader("User-Agent")).thenReturn("crawler1");
        //when
        preRenderSEOFilter.doFilter(servletRequest, servletResponse, filterChain);

        //then
        verify(httpClient, never()).execute(httpGet);
        verify(filterChain).doFilter(servletRequest, servletResponse);
    }

    @Test
    public void should_handle_when_user_agent_is_crawler_and_url_is_not_resource_and_white_list_is_empty_and_black_list_is_empty() throws Exception {
        //given
        when(filterConfig.getInitParameter("crawlerUserAgents")).thenReturn("crawler1,crawler2");
        preRenderSEOFilter.init(filterConfig);

        final CloseableHttpResponse httpResponse = mock(CloseableHttpResponse.class);
        final StatusLine statusLine = mock(StatusLine.class);

        when(servletRequest.getRequestURL()).thenReturn(new StringBuffer("http://localhost/test"));
        when(servletRequest.getMethod()).thenReturn(HttpGet.METHOD_NAME);
        when(servletRequest.getHeader("User-Agent")).thenReturn("crawler1");

        when(servletRequest.getHeaderNames()).thenReturn(mock(Enumeration.class));
        when(httpClient.execute(httpGet)).thenReturn(httpResponse);
        when(httpResponse.getStatusLine()).thenReturn(statusLine);
        final HashMap<String, String> map = Maps.newHashMap();
        map.put("_escaped_fragment_", "");
        when(servletRequest.getParameterMap()).thenReturn(map);
        when(statusLine.getStatusCode()).thenReturn(PreRenderSEOFilter.HTTP_OK);
        when(httpResponse.getAllHeaders()).thenReturn(new Header[0]);

        //when
        preRenderSEOFilter.doFilter(servletRequest, servletResponse, filterChain);

        //then
        verify(httpClient).execute(httpGet);
        verify(filterChain, never()).doFilter(servletRequest, servletResponse);
    }

    @Test
    public void should_not_handle_when_every_thing_is_ok_but_prerender_server_response_is_not_200() throws Exception {
        //given
        when(filterConfig.getInitParameter("crawlerUserAgents")).thenReturn("crawler1,crawler2");
        preRenderSEOFilter.init(filterConfig);

        final CloseableHttpResponse httpResponse = mock(CloseableHttpResponse.class);
        final StatusLine statusLine = mock(StatusLine.class);

        when(servletRequest.getRequestURL()).thenReturn(new StringBuffer("http://localhost/test"));
        when(servletRequest.getMethod()).thenReturn(HttpGet.METHOD_NAME);
        when(servletRequest.getHeader("User-Agent")).thenReturn("crawler1");

        when(servletRequest.getHeaderNames()).thenReturn(mock(Enumeration.class));
        when(httpClient.execute(httpGet)).thenReturn(httpResponse);
        when(httpResponse.getStatusLine()).thenReturn(statusLine);
        final HashMap<String, String> map = Maps.newHashMap();
        map.put("_escaped_fragment_", "");
        when(servletRequest.getParameterMap()).thenReturn(map);
        when(statusLine.getStatusCode()).thenReturn(NOT_FOUND);

        //when
        preRenderSEOFilter.doFilter(servletRequest, servletResponse, filterChain);

        //then
        verify(httpClient).execute(httpGet);
        verify(filterChain).doFilter(servletRequest, servletResponse);
    }


    @Test
    public void should_handle_when_user_agent_is_crawler_and_url_is_not_resource_and_in_white_list_and_not_in_black_list() throws Exception {
        //given
        when(filterConfig.getInitParameter("crawlerUserAgents")).thenReturn("crawler1,crawler2");
        when(filterConfig.getInitParameter("whitelist")).thenReturn("whitelist1,http://localhost/test");
        when(filterConfig.getInitParameter("blacklist")).thenReturn("blacklist1,blacklist2");

        preRenderSEOFilter.init(filterConfig);

        final CloseableHttpResponse httpResponse = mock(CloseableHttpResponse.class);
        final StatusLine statusLine = mock(StatusLine.class);

        when(servletRequest.getRequestURL()).thenReturn(new StringBuffer("http://localhost/test"));
        when(servletRequest.getMethod()).thenReturn(HttpGet.METHOD_NAME);
        when(servletRequest.getHeader("User-Agent")).thenReturn("crawler1");

        when(servletRequest.getHeaderNames()).thenReturn(mock(Enumeration.class));
        when(httpClient.execute(httpGet)).thenReturn(httpResponse);
        when(httpResponse.getStatusLine()).thenReturn(statusLine);
        final HashMap<String, String> map = Maps.newHashMap();
        map.put("_escaped_fragment_", "");
        when(servletRequest.getParameterMap()).thenReturn(map);
        when(statusLine.getStatusCode()).thenReturn(PreRenderSEOFilter.HTTP_OK);
        when(httpResponse.getAllHeaders()).thenReturn(new Header[0]);

        //when
        preRenderSEOFilter.doFilter(servletRequest, servletResponse, filterChain);

        //then
        verify(httpClient).execute(httpGet);
        verify(filterChain, never()).doFilter(servletRequest, servletResponse);
    }

    @Test
    public void should_use_request_url_from_custom_header_if_available() throws Exception {
        //given
        when(filterConfig.getInitParameter("forwardedURLHeader")).thenReturn("X-Forwarded-URL");
        when(filterConfig.getInitParameter("whitelist")).thenReturn("http://my.public.domain.com/");
        when(filterConfig.getInitParameter("blacklist")).thenReturn("http://localhost/test");

        preRenderSEOFilter.init(filterConfig);

        final CloseableHttpResponse httpResponse = mock(CloseableHttpResponse.class);
        final StatusLine statusLine = mock(StatusLine.class);

        when(servletRequest.getRequestURL()).thenReturn(new StringBuffer("http://localhost/test"));
        when(servletRequest.getMethod()).thenReturn(HttpGet.METHOD_NAME);
        when(servletRequest.getHeader("X-Forwarded-URL")).thenReturn("http://my.public.domain.com/");

        when(servletRequest.getHeaderNames()).thenReturn(mock(Enumeration.class));
        when(httpClient.execute(httpGet)).thenReturn(httpResponse);
        when(httpResponse.getStatusLine()).thenReturn(statusLine);
        final HashMap<String, String> map = Maps.newHashMap();
        map.put("_escaped_fragment_", "");
        when(servletRequest.getParameterMap()).thenReturn(map);
        when(statusLine.getStatusCode()).thenReturn(PreRenderSEOFilter.HTTP_OK);
        when(httpResponse.getAllHeaders()).thenReturn(new Header[0]);

        //when
        preRenderSEOFilter.doFilter(servletRequest, servletResponse, filterChain);

        //then
        verify(httpClient).execute(httpGet);
        verify(filterChain, never()).doFilter(servletRequest, servletResponse);
    }
}
