package com.example.interview.config;

import java.io.IOException;
import java.net.URI;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 다른 사이트가 브라우저를 통해 이 앱으로 보내는 요청(API 키 바꾸기, 종료, 삭제 등)을 막는다.
 * 브라우저는 다른 사이트에서 보낸 POST·PUT·PATCH·DELETE에 Origin을 붙이므로, 그 호스트가 요청받은 호스트와 다르면 거절한다.
 */
@Component
public class SameOriginFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        if (!"GET".equals(request.getMethod()) && !"HEAD".equals(request.getMethod())
                && origin != null && !sameHost(origin, request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "다른 사이트에서 보낸 요청입니다.");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean sameHost(String origin, HttpServletRequest request) {
        try {
            URI o = URI.create(origin);
            int port = o.getPort() == -1 ? ("https".equals(o.getScheme()) ? 443 : 80) : o.getPort();
            return request.getServerName().equalsIgnoreCase(o.getHost()) && request.getServerPort() == port;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
