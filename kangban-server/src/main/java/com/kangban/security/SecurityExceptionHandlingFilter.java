package com.kangban.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kangban.common.Result;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 防止安全过滤器链中未被 ExceptionTranslationFilter 转换的异常冒泡到 DispatcherServlet。
 */
@Component
@RequiredArgsConstructor
public class SecurityExceptionHandlingFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return true;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } catch (AccessDeniedException e) {
            writeJson(response, HttpServletResponse.SC_FORBIDDEN, Result.forbidden("权限不足"));
        } catch (AuthenticationException e) {
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, Result.unauthorized("请先登录"));
        }
    }

    private void writeJson(HttpServletResponse response, int status, Result<Void> result)
            throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), result);
    }
}
