package com.opah.config;

import com.opah.security.TokenHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 令牌鉴权过滤器：请求头 X-Auth-Token（或 query ?token=）与进程内 token 比对。
 * 命中则注入 Authentication 使 /api/** 的 authenticated 规则通过。
 */
@Component
public class TokenAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = request.getHeader("X-Auth-Token");
        if (token == null || token.isBlank()) {
            token = request.getParameter("token");
        }
        if (token != null && token.equals(TokenHolder.get())) {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("admin", null, Collections.emptyList()));
        }
        chain.doFilter(request, response);
    }
}
