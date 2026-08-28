package com.datanote.controller;

import com.datanote.config.AuthProperties;
import com.datanote.model.DnUser;
import com.datanote.model.R;
import com.datanote.service.UserService;
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

    private final AuthProperties authProperties;
    private final UserService userService;

    /**
     * 登录 — 校验数据库用户，成功返回 userId（AI 会话隔离用）
     */
    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public R<Map<String, Object>> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        DnUser user = userService.login(request.getUsername(), request.getPassword());
        if (user == null) {
            return R.fail(R.CODE_UNAUTHORIZED, "用户名或密码错误");
        }

        // 记录到 Session（后端可据此识别当前用户）
        HttpSession session = httpRequest.getSession(true);
        session.setAttribute("USER_ID", user.getId());
        session.setAttribute("USERNAME", user.getUsername());

        Map<String, Object> data = new HashMap<>();
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        data.put("nickname", user.getNickname());
        data.put("role", user.getRole());
        return R.ok(data);
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
