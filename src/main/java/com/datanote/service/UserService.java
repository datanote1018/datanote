package com.datanote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.datanote.mapper.DnUserMapper;
import com.datanote.model.DnUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户服务 — 用户增删改查、登录校验、密码加密
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final DnUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 启动时若用户表为空，创建默认管理员 admin/admin123
     */
    @PostConstruct
    public void initDefaultAdmin() {
        Long count = userMapper.selectCount(null);
        if (count == null || count == 0) {
            DnUser admin = new DnUser();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setNickname("超级管理员");
            admin.setRole("ADMIN");
            admin.setStatus(1);
            admin.setCreatedAt(LocalDateTime.now());
            userMapper.insert(admin);
        }
    }

    /** 用户列表（密码脱敏） */
    public List<DnUser> list() {
        List<DnUser> users = userMapper.selectList(null);
        users.forEach(u -> u.setPassword(null));
        return users;
    }

    /** 按用户名查（含密码，供登录用） */
    public DnUser findByUsername(String username) {
        return userMapper.selectOne(new QueryWrapper<DnUser>().eq("username", username));
    }

    /**
     * 登录校验：成功返回脱敏用户，失败返回 null
     */
    public DnUser login(String username, String password) {
        DnUser u = findByUsername(username);
        if (u == null || u.getStatus() == null || u.getStatus() != 1) {
            return null;
        }
        if (!passwordEncoder.matches(password, u.getPassword())) {
            return null;
        }
        u.setPassword(null);
        return u;
    }

    /** 新建用户 */
    public DnUser create(String username, String password, String nickname, String role) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (findByUsername(username) != null) {
            throw new IllegalArgumentException("用户名已存在");
        }
        DnUser u = new DnUser();
        u.setUsername(username.trim());
        u.setPassword(passwordEncoder.encode(isBlank(password) ? "123456" : password));
        u.setNickname(nickname);
        u.setRole("ADMIN".equals(role) ? "ADMIN" : "USER");
        u.setStatus(1);
        u.setCreatedAt(LocalDateTime.now());
        userMapper.insert(u);
        u.setPassword(null);
        return u;
    }

    /** 编辑用户（昵称/角色/状态） */
    public void update(Long id, String nickname, String role, Integer status) {
        DnUser u = userMapper.selectById(id);
        if (u == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        if (nickname != null) u.setNickname(nickname);
        if (role != null) u.setRole("ADMIN".equals(role) ? "ADMIN" : "USER");
        if (status != null) u.setStatus(status);
        userMapper.updateById(u);
    }

    /** 重置密码 */
    public void resetPassword(Long id, String newPassword) {
        DnUser u = userMapper.selectById(id);
        if (u == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        u.setPassword(passwordEncoder.encode(isBlank(newPassword) ? "123456" : newPassword));
        userMapper.updateById(u);
    }

    /** 删除用户（默认 admin 不可删） */
    public void delete(Long id) {
        DnUser u = userMapper.selectById(id);
        if (u == null) {
            return;
        }
        if ("admin".equals(u.getUsername())) {
            throw new IllegalArgumentException("默认管理员不可删除");
        }
        userMapper.deleteById(id);
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
