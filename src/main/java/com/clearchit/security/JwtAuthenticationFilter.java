package com.clearchit.security;

import com.clearchit.model.UserAccount;
import com.clearchit.repository.UserAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserAccountRepository userAccountRepository;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserAccountRepository userAccountRepository) {

        this.jwtService = jwtService;
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.equals("/")
                || path.equals("/index.html")
                || path.equals("/styles.css")
                || path.equals("/app.js")
                || path.equals("/favicon.ico")
                || path.equals("/api/health")
                || path.equals("/api/auth/register")
                || path.equals("/api/auth/login");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String authorizationHeader =
                request.getHeader("Authorization");

        if (authorizationHeader == null
                || !authorizationHeader.startsWith("Bearer ")) {

            filterChain.doFilter(request, response);
            return;
        }

        String token =
                authorizationHeader.substring(7);

        try {

            if (jwtService.isTokenValid(token)) {

                String email =
                        jwtService.extractEmail(token);

                UserAccount userAccount =
                        userAccountRepository
                                .findByEmail(email)
                                .orElse(null);

                if (userAccount != null
                        && SecurityContextHolder
                        .getContext()
                        .getAuthentication() == null) {

                    var authority =
                            new SimpleGrantedAuthority(
                                    "ROLE_" +
                                            userAccount
                                                    .getRole()
                                                    .name());

                    var authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userAccount.getEmail(),
                                    null,
                                    List.of(authority));

                    SecurityContextHolder
                            .getContext()
                            .setAuthentication(authentication);
                }
            }

        } catch (Exception exception) {
            // Invalid token → request remains unauthenticated.
        }

        filterChain.doFilter(request, response);
    }
}