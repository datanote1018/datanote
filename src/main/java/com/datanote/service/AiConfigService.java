package com.datanote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.datanote.mapper.DnAiConfigMapper;
import com.datanote.model.DnAiConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 配置服务 — 多套 AI 模型配置的增删改查、默认切换、密钥加解密
 */
@Service
@RequiredArgsConstructor
public class AiConfigService {

    private final DnAiConfigMapper mapper;

    /** API Key 统一来自 ai-service/.env，本表不再存储密钥 */
    @Value("${datanote.ai.api-key:}")
    private String envApiKey;

    /** 列表（api_key 脱敏为掩码） */
    public List<DnAiConfig> list() {
        List<DnAiConfig> list = mapper.selectList(
                new QueryWrapper<DnAiConfig>().orderByDesc("is_default").orderByDesc("id"));
        for (DnAiConfig c : list) {
            c.setApiKey("");   // 密钥不在本表管理，也不下发到前端
        }
        return list;
    }

    /** 新建 */
    public DnAiConfig create(DnAiConfig c) {
        if (c.getName() == null || c.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("配置名称不能为空");
        }
        c.setId(null);
        c.setApiKey(null);   // 密钥不入库，统一在 ai-service/.env 配置
        c.setStatus(c.getStatus() == null ? 1 : c.getStatus());
        c.setIsDefault(0);
        c.setCreatedAt(LocalDateTime.now());
        mapper.insert(c);
        // 第一条自动设为默认
        Long count = mapper.selectCount(null);
        if (count != null && count == 1) {
            setDefault(c.getId());
        }
        return c;
    }

    /** 编辑（key 只有传了新明文才更新） */
    public void update(Long id, DnAiConfig c) {
        DnAiConfig ex = mapper.selectById(id);
        if (ex == null) {
            throw new IllegalArgumentException("配置不存在");
        }
        ex.setName(c.getName());
        ex.setProvider(c.getProvider());
        ex.setBaseUrl(c.getBaseUrl());
        ex.setModel(c.getModel());
        if (c.getStatus() != null) {
            ex.setStatus(c.getStatus());
        }
        ex.setApiKey(null);   // 密钥不入库，统一在 ai-service/.env 配置
        mapper.updateById(ex);
    }

    /** 删除 */
    public void delete(Long id) {
        mapper.deleteById(id);
    }

    /** 设为默认（其余取消默认） */
    public void setDefault(Long id) {
        DnAiConfig clear = new DnAiConfig();
        clear.setIsDefault(0);
        mapper.update(clear, new QueryWrapper<DnAiConfig>().isNotNull("id"));
        DnAiConfig target = mapper.selectById(id);
        if (target != null) {
            target.setIsDefault(1);
            target.setStatus(1);
            mapper.updateById(target);
        }
    }

    /** 测试连接用的 key —— 统一取 ai-service/.env 里配置的那把 */
    public String resolveTestKey(DnAiConfig c) {
        return envApiKey;
    }
}
