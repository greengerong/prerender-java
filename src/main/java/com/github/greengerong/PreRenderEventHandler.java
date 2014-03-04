package com.github.greengerong;


import org.apache.http.HttpResponse;

import javax.servlet.http.HttpServletRequest;

public interface PreRenderEventHandler {

    String beforeRender(HttpServletRequest clientRequest);

    void afterRender(HttpServletRequest clientRequest, HttpResponse prerenderResponse, String html);

    void destroy();
}
