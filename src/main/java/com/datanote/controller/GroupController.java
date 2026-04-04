package com.datanote.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.datanote.mapper.DnGroupMapper;
import com.datanote.mapper.DnGroupMemberMapper;
import com.datanote.model.DnGroup;
import com.datanote.model.DnGroupMember;
import com.datanote.model.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 分组管理 Controller
 */
@RestController
@RequestMapping("/api/group")
@RequiredArgsConstructor
@Tag(name = "分组管理", description = "分组与成员的增删改查")
public class GroupController {

    private final DnGroupMapper groupMapper;
    private final DnGroupMemberMapper memberMapper;

    /**
     * 获取所有分组
     */
    @Operation(summary = "获取分组列表")
    @GetMapping("/list")
    public R<List<DnGroup>> list() {
        return R.ok(groupMapper.selectList(null));
    }

    /**
     * 创建分组
     */
    @Operation(summary = "创建分组")
    @PostMapping
    public R<DnGroup> create(@RequestBody DnGroup group) {
        group.setCreatedAt(LocalDateTime.now());
        groupMapper.insert(group);
        return R.ok(group);
    }

    /**
     * 更新分组
     */
    @Operation(summary = "更新分组")
    @PutMapping("/{id}")
    public R<DnGroup> update(@PathVariable Long id, @RequestBody DnGroup group) {
        group.setId(id);
        groupMapper.updateById(group);
        return R.ok(group);
    }

    /**
     * 删除分组（级联删除成员）
     */
    @Transactional(rollbackFor = Exception.class)
    @Operation(summary = "删除分组")
    @DeleteMapping("/{id}")
    public R<String> delete(@PathVariable Long id) {
        QueryWrapper<DnGroupMember> memberQuery = new QueryWrapper<>();
        memberQuery.eq("group_id", id);
        memberMapper.delete(memberQuery);
        groupMapper.deleteById(id);
        return R.ok("删除成功");
    }

    /**
     * 获取分组成员列表
     */
    @Operation(summary = "获取分组成员列表")
    @GetMapping("/{id}/members")
    public R<List<DnGroupMember>> listMembers(@PathVariable Long id) {
        QueryWrapper<DnGroupMember> qw = new QueryWrapper<>();
        qw.eq("group_id", id);
        return R.ok(memberMapper.selectList(qw));
    }

    /**
     * 添加分组成员
     */
    @Operation(summary = "添加分组成员")
    @PostMapping("/{id}/members")
    public R<DnGroupMember> addMember(@PathVariable Long id, @RequestBody DnGroupMember member) {
        member.setGroupId(id);
        member.setCreatedAt(LocalDateTime.now());
        memberMapper.insert(member);
        return R.ok(member);
    }

    /**
     * 移除分组成员
     */
    @Operation(summary = "移除分组成员")
    @DeleteMapping("/{groupId}/members/{memberId}")
    public R<String> removeMember(@PathVariable Long groupId, @PathVariable Long memberId) {
        memberMapper.deleteById(memberId);
        return R.ok("删除成功");
    }
}
