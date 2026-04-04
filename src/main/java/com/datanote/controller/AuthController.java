package com.datanote.controller;

import com.datanote.config.AuthProperties;
import com.datanote.model.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

/**
 * 认证控制器 — 登录、登出、状态查询
 */
@Tag(name = "认证管理")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final AuthProperties authProperties;

    /**
     * 登录
     */
    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public R<Map<String, Object>> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        if (!authProperties.isEnabled()) {
            return R.fail(R.CODE_BAD_REQUEST, "认证未启用");
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );

            // 认证成功，创建 Session
            SecurityContextHolder.getContext().setAuthentication(authentication);
            HttpSession session = httpRequest.getSession(true);
            session.setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());

            Map<String, Object> data = new HashMap<>();
            data.put("username", authentication.getName());
            return R.ok(data);
        } catch (AuthenticationException e) {
            return R.fail(R.CODE_UNAUTHORIZED, "用户名或密码错误");
        }
    }

    /**
     * 查询登录状态
     */
    @Operation(summary = "查询登录状态")
    @GetMapping("/status")
    public R<Map<String, Object>> status() {
        Map<String, Object> data = new HashMap<>();
        data.put("authEnabled", authProperties.isEnabled());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = authentication != null
                && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal());

        data.put("authenticated", authenticated);
        if (authenticated) {
            data.put("username", authentication.getName());
        }
        return R.ok(data);
    }

    /**
     * 注销
     */
    @Operation(summary = "用户注销")
    @PostMapping("/logout")
    public R<Void> logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return R.ok();
    }

    /**
     * 登录请求体
     */
    @Data
    public static class LoginRequest {
        private String username;
        private String password;
    }
}
