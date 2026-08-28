package com.datanote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.datanote.mapper.DnRequirementLogMapper;
import com.datanote.mapper.DnRequirementMapper;
import com.datanote.model.DnRequirement;
import com.datanote.model.DnRequirementLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 取数需求服务 — 需求工作项的增删改查、状态流转、口径/SQL/结果的保存，并留流转记录
 */
@Service
@RequiredArgsConstructor
public class RequirementService {

    private final DnRequirementMapper mapper;
    private final DnRequirementLogMapper logMapper;

    /** 列表（按更新时间倒序） */
    public List<DnRequirement> list() {
        return mapper.selectList(new QueryWrapper<DnRequirement>().orderByDesc("updated_at"));
    }

    public DnRequirement get(Long id) {
        return mapper.selectById(id);
    }

    /** 某条需求的流转记录（按时间正序） */
    public List<DnRequirementLog> logs(Long reqId) {
        return logMapper.selectList(
                new QueryWrapper<DnRequirementLog>().eq("req_id", reqId).orderByAsc("created_at", "id"));
    }

    /** 新建需求：初始状态 clarifying，并把 session_id 绑成 req-{id} */
    public DnRequirement create(DnRequirement r) {
        if (r.getTitle() == null || r.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("需求标题不能为空");
        }
        r.setId(null);
        r.setStatus("clarifying");
        r.setPriority(r.getPriority() == null ? "中" : r.getPriority());
        r.setCreatedAt(LocalDateTime.now());
        // 若前端已在「聊天式新建」里用草稿会话聊过，保留该 session_id，让澄清记忆延续到详情页；
        // 否则给一个独立会话 req-{id}
        String draftSession = r.getSessionId();
        r.setSessionId(null);
        mapper.insert(r);
        r.setSessionId((draftSession == null || draftSession.trim().isEmpty()) ? "req-" + r.getId() : draftSession);
        mapper.updateById(r);
        writeLog(r.getId(), null, "clarifying", r.getSubmitterName(), "创建需求");
        return r;
    }

    /** 状态流转（默认无操作人/备注） */
    public void updateStatus(Long id, String status) {
        updateStatus(id, status, null, null);
    }

    /** 状态流转 + 记流转日志 */
    public void updateStatus(Long id, String status, String operator, String comment) {
        DnRequirement r = require(id);
        String from = r.getStatus();
        r.setStatus(status);
        mapper.updateById(r);
        if (from == null || !from.equals(status)) {
            writeLog(id, from, status, operator, comment);
        }
    }

    /** 保存加工口径+建模建议，并推进到「待评审」 */
    public void saveSpec(Long id, String spec) {
        DnRequirement r = require(id);
        r.setSpec(spec);
        if ("clarifying".equals(r.getStatus())) {
            String from = r.getStatus();
            r.setStatus("reviewing");
            mapper.updateById(r);
            writeLog(id, from, "reviewing", r.getAssigneeName(), "生成加工口径，进入评审");
            return;
        }
        mapper.updateById(r);
    }

    /** 写一条流转记录 */
    private void writeLog(Long reqId, String from, String to, String operator, String comment) {
        DnRequirementLog log = new DnRequirementLog();
        log.setReqId(reqId);
        log.setFromStatus(from);
        log.setToStatus(to);
        log.setOperator(operator == null || operator.trim().isEmpty() ? "系统" : operator);
        log.setComment(comment);
        log.setCreatedAt(LocalDateTime.now());
        logMapper.insert(log);
    }

    /** 保存出数结果（SQL + 结果摘要） */
    public void saveQuery(Long id, String sqlText, String resultSummary) {
        DnRequirement r = require(id);
        r.setSqlText(sqlText);
        r.setResultSummary(resultSummary);
        mapper.updateById(r);
    }

    /** 保存核对结论 */
    public void saveCheck(Long id, String checkResult) {
        DnRequirement r = require(id);
        r.setCheckResult(checkResult);
        mapper.updateById(r);
    }

    public void delete(Long id) {
        mapper.deleteById(id);
    }

    private DnRequirement require(Long id) {
        DnRequirement r = mapper.selectById(id);
        if (r == null) {
            throw new IllegalArgumentException("需求不存在");
        }
        return r;
    }
}
