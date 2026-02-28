package com.QuantPlatformApplication.QuantPlatformApplication.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT Authentication Filter — runs once per request, BEFORE Spring Security's
 * default UsernamePasswordAuthenticationFilter.
 *
 * Flow:
 * 1. Extract the "Authorization: Bearer <token>" header
 * 2. Validate the JWT via JwtTokenProvider
 * 3. Load the user from the database via CustomUserDetailsService
 * 4. Set a UsernamePasswordAuthenticationToken into the SecurityContext
 * so that downstream filters and controllers see the user as authenticated
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    // Don't need autowired here, implicit constructor injection and has been the
    // preferred style since Spring 4.3.
    private final JwtTokenProvider jwtTokenProvider;

    private final CustomUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider,
            CustomUserDetailsService userDetailsService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        // 1. Extract token from "Authorization: Bearer ey..."
        String token = extractTokenFromRequest(request);

        // 2. If token exists and is valid, authenticate the user
        if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {

            String email = jwtTokenProvider.getEmailFromToken(token);

            // 3. Load user details from DB
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);

            // 4. Create authentication token and set it into the security context
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null, // credentials not needed after authentication
                    userDetails.getAuthorities());
            authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        // Continue the filter chain (regardless of whether auth succeeded)
        filterChain.doFilter(request, response);
    }

    /**
     * Extract the Bearer token from the Authorization header.
     * Returns null if header is missing or doesn't start with "Bearer ".
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
