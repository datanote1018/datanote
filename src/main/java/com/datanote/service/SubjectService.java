package com.datanote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.datanote.mapper.DnSubjectMapper;
import com.datanote.model.DnSubject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 主题域服务 — 树形结构构建与增删操作
 */
@Service
@RequiredArgsConstructor
public class SubjectService {

    private final DnSubjectMapper subjectMapper;

    /**
     * 获取所有主题域并构建父子树
     *
     * @return 树形结构列表 [{id, name, parentId, layer, sortOrder, children:[...]}]
     */
    public List<Map<String, Object>> listTree() {
        QueryWrapper<DnSubject> qw = new QueryWrapper<>();
        qw.orderByAsc("sort_order", "id");
        List<DnSubject> subjects = subjectMapper.selectList(qw);
        return buildTree(subjects, null);
    }

    /**
     * 创建主题域
     *
     * @param subject 主题域实体
     * @return 插入后的实体（含自增 ID）
     */
    public DnSubject create(DnSubject subject) {
        subject.setCreatedAt(LocalDateTime.now());
        subjectMapper.insert(subject);
        return subject;
    }

    /**
     * 删除主题域（级联删除子节点）
     *
     * @param id 主题域 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        // 删除子节点
        QueryWrapper<DnSubject> childQuery = new QueryWrapper<>();
        childQuery.eq("parent_id", id);
        subjectMapper.delete(childQuery);
        // 删除自身
        subjectMapper.deleteById(id);
    }

    /**
     * 递归构建树形结构
     */
    private List<Map<String, Object>> buildTree(List<DnSubject> subjects, Long parentId) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (DnSubject s : subjects) {
            boolean match = (parentId == null && s.getParentId() == null)
                    || (parentId != null && parentId.equals(s.getParentId()));
            if (match) {
                Map<String, Object> node = new HashMap<>();
                node.put("id", s.getId());
                node.put("name", s.getName());
                node.put("parentId", s.getParentId());
                node.put("layer", s.getLayer());
                node.put("sortOrder", s.getSortOrder());
                node.put("children", buildTree(subjects, s.getId()));
                nodes.add(node);
            }
        }
        return nodes;
    }
}
