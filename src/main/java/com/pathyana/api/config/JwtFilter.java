package com.pathyana.api.config;

import com.pathyana.api.service.JwtService;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Inspects the Authorization header on every request. If a valid JWT is present,
 * we populate Spring's SecurityContext with the user's identity so downstream
 * controllers can call SecurityContextHolder.getContext().getAuthentication()
 * to know who's calling.
 *
 * Important details:
 *  - OncePerRequestFilter ensures this runs at most once per request, even if
 *    forwards/includes happen internally.
 *  - We do NOT reject the request if no token is present. We let the request
 *    proceed and let Spring Security's authorization layer decide (some endpoints
 *    like /api/auth/signup are public).
 *  - We DO reject if a token is present but invalid (expired, tampered, wrong
 *    issuer). That's a 401, because the client clearly *meant* to authenticate
 *    and failed.
 *
 * The "principal" we set is the userId as a string. Controllers can extract it via
 * @AuthenticationPrincipal or by parsing Authentication.getName().
 */
@Component
public class JwtFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // No header or wrong scheme → just pass through. Authorization layer will gate.
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();

        try {
            Claims claims = jwtService.parseToken(token);
            String userId = claims.getSubject();
            String email = claims.get("email", String.class);

            // Build an Authentication object that Spring Security will recognize.
            // We use ROLE_USER as a default; later we can add ROLE_ADMIN, etc.
            UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                    userId,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_USER"))
                );
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(auth);
            log.debug("Authenticated request from userId={} email={}", userId, email);

        } catch (JwtException e) {
            // Token was present but invalid. Reject with 401 so the client knows
            // to re-login rather than retry the same broken token.
            log.warn("Rejected invalid JWT: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write(
                "{\"error\":\"Invalid or expired token\",\"detail\":\"" + e.getMessage() + "\"}");
            return;
        }

        chain.doFilter(request, response);
    }
}
