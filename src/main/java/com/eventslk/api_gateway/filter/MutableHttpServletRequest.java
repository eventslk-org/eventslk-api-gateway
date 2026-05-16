package com.eventslk.api_gateway.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.util.*;

/**
 * Wraps an HttpServletRequest so that extra headers can be injected before
 * the request is forwarded to a downstream service.
 *
 * HttpServletRequest headers are read-only by the Servlet spec, so a wrapper
 * is the standard way to add headers mid-filter-chain.
 *
 * Usage in JwtAuthenticationFilter:
 *   MutableHttpServletRequest mutable = new MutableHttpServletRequest(request);
 *   mutable.addHeader("X-User-Email", email);
 *   chain.doFilter(mutable, response);
 */
public class MutableHttpServletRequest extends HttpServletRequestWrapper {

    private final Map<String, String> extraHeaders = new HashMap<>();

    public MutableHttpServletRequest(HttpServletRequest request) {
        super(request);
    }

    public void addHeader(String name, String value) {
        if (value != null) {
            extraHeaders.put(name, value);
        }
    }

    @Override
    public String getHeader(String name) {
        if (extraHeaders.containsKey(name)) {
            return extraHeaders.get(name);
        }
        return super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        if (extraHeaders.containsKey(name)) {
            return Collections.enumeration(List.of(extraHeaders.get(name)));
        }
        return super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        List<String> names = Collections.list(super.getHeaderNames());
        names.addAll(extraHeaders.keySet());
        return Collections.enumeration(names);
    }
}
