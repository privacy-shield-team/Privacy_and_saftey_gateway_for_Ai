package com.aiprivacy.shield.config;

import com.aiprivacy.shield.entity.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter that enforces session authentication on protected routes.
 *
 * Protected paths: / (the main app), /api/** endpoints
 * Public paths: /login.html, /auth/**, /styles.css, /app.js, static assets
 *
 * Unauthenticated access to protected paths:
 * - Browser navigation (Accept: text/html) → redirect to /login.html
 * - API calls (Accept: application/json) → 401 JSON response
 */
public class SessionAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Allow public paths without session check
        if (isPublicPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Check session
        HttpSession session = request.getSession(false);
        User user = (session != null) ? (User) session.getAttribute("authenticatedUser") : null;

        if (user == null) {
            String accept = request.getHeader("Accept");
            if (accept != null && accept.contains("application/json")) {
                // API client — return 401 JSON
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Not authenticated\",\"redirect\":\"/login.html\"}");
            } else {
                // Browser — redirect to login page
                response.sendRedirect("/login.html?redirect=" + encodeRedirectPath(path));
            }
            return;
        }

        // Authenticated — continue
        filterChain.doFilter(request, response);
    }

    private boolean isPublicPath(String path) {
        return path.equals("/login.html")
                || path.startsWith("/auth/")
                || path.equals("/api/health")
                || path.equals("/styles.css")
                || path.equals("/app.js")
                || path.equals("/favicon.ico")
                || path.startsWith("/h2-console")
                || path.startsWith("/protecto-original.css")
                || path.endsWith(".css")
                || path.endsWith(".js")
                || path.endsWith(".png")
                || path.endsWith(".svg")
                || path.endsWith(".ico")
                || path.endsWith(".woff2")
                || path.endsWith(".woff");
    }

    private String encodeRedirectPath(String path) {
        try {
            return java.net.URLEncoder.encode(path, "UTF-8");
        } catch (Exception e) {
            return "/";
        }
    }
}
