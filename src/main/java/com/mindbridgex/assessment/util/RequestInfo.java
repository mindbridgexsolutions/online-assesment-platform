package com.mindbridgex.assessment.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class RequestInfo {

    private RequestInfo() {
    }

    public static HttpServletRequest currentRequest() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        return attrs instanceof ServletRequestAttributes sra ? sra.getRequest() : null;
    }

    /** Client IP as resolved by Tomcat's RemoteIpValve (honours trusted X-Forwarded-For). */
    public static String clientIp(HttpServletRequest request) {
        return request == null ? null : request.getRemoteAddr();
    }

    public static String currentIp() {
        return clientIp(currentRequest());
    }

    public static String userAgent(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String ua = request.getHeader("User-Agent");
        return ua == null ? null : (ua.length() > 250 ? ua.substring(0, 250) : ua);
    }

    public static boolean isApi(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/api/");
    }
}
