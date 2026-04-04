package com.datanote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.datanote.mapper.DnGroupMapper;
import com.datanote.mapper.DnGroupMemberMapper;
import com.datanote.model.DnGroup;
import com.datanote.model.DnGroupMember;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 分组服务 — 管理分组与成员的增删改查
 */
@Service
@RequiredArgsConstructor
public class GroupService {

    private final DnGroupMapper groupMapper;
    private final DnGroupMemberMapper memberMapper;

    /**
     * 获取所有分组
     *
     * @return 分组列表
     */
    public List<DnGroup> list() {
        return groupMapper.selectList(null);
    }

    /**
     * 创建分组
     *
     * @param group 分组实体
     * @return 插入后的实体（含自增 ID）
     */
    public DnGroup create(DnGroup group) {
        group.setCreatedAt(LocalDateTime.now());
        groupMapper.insert(group);
        return group;
    }

    /**
     * 更新分组
     *
     * @param group 分组实体
     * @return 更新后的实体
     */
    public DnGroup update(DnGroup group) {
        groupMapper.updateById(group);
        return group;
    }

    /**
     * 删除分组（级联删除所有成员）
     *
     * @param id 分组 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        QueryWrapper<DnGroupMember> memberQuery = new QueryWrapper<>();
        memberQuery.eq("group_id", id);
        memberMapper.delete(memberQuery);
        groupMapper.deleteById(id);
    }

    /**
     * 获取指定分组的成员列表
     *
     * @param groupId 分组 ID
     * @return 成员列表
     */
    public List<DnGroupMember> listMembers(Long groupId) {
        QueryWrapper<DnGroupMember> qw = new QueryWrapper<>();
        qw.eq("group_id", groupId);
        return memberMapper.selectList(qw);
    }

    /**
     * 添加分组成员
     *
     * @param member 成员实体
     * @return 插入后的实体（含自增 ID）
     */
    public DnGroupMember addMember(DnGroupMember member) {
        member.setCreatedAt(LocalDateTime.now());
        memberMapper.insert(member);
        return member;
    }

    /**
     * 移除分组成员
     *
     * @param memberId 成员 ID
     */
    public void removeMember(Long memberId) {
        memberMapper.deleteById(memberId);
    }
}
