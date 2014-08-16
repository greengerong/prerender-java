package com.github.greengerong;


import org.apache.http.HttpResponse;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public interface PreRenderEventHandler {

    String beforeRender(HttpServletRequest clientRequest);

    void afterRender(HttpServletRequest clientRequest, HttpServletResponse clientResponse, HttpResponse prerenderResponse, String responseHtml);

    void destroy();
}
