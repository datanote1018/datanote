package com.datanote.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.datanote.mapper.DnColumnMetaMapper;
import com.datanote.mapper.DnTableMetaMapper;
import com.datanote.model.DnColumnMeta;
import com.datanote.model.DnTableMeta;
import com.datanote.model.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 元数据中心 Controller — 表/字段元数据管理
 */
@RestController
@RequestMapping("/api/metadata-center")
@Tag(name = "元数据中心", description = "表和字段级元数据管理、搜索、标签")
@RequiredArgsConstructor
public class MetadataCenterController {

    private final DnTableMetaMapper tableMetaMapper;
    private final DnColumnMetaMapper columnMetaMapper;

    /**
     * 搜索表元数据
     */
    @Operation(summary = "搜索表元数据")
    @GetMapping("/tables")
    public R<List<DnTableMeta>> searchTables(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String tag) {
        QueryWrapper<DnTableMeta> qw = new QueryWrapper<>();
        if (datasourceId != null) {
            qw.eq("datasource_id", datasourceId);
        }
        if (keyword != null && !keyword.isEmpty()) {
            qw.and(w -> w.like("table_name", keyword)
                    .or().like("table_comment", keyword)
                    .or().like("database_name", keyword));
        }
        if (tag != null && !tag.isEmpty()) {
            qw.like("tags", tag);
        }
        qw.orderByDesc("updated_at");
        qw.last("LIMIT 100");
        return R.ok(tableMetaMapper.selectList(qw));
    }

    /**
     * 获取表元数据详情
     */
    @Operation(summary = "表元数据详情")
    @GetMapping("/table/{id}")
    public R<Map<String, Object>> getTableDetail(@PathVariable Long id) {
        DnTableMeta meta = tableMetaMapper.selectById(id);
        if (meta == null) {
            return R.fail("表元数据不存在");
        }
        QueryWrapper<DnColumnMeta> colQw = new QueryWrapper<>();
        colQw.eq("table_meta_id", id).orderByAsc("id");
        List<DnColumnMeta> columns = columnMetaMapper.selectList(colQw);

        Map<String, Object> result = new HashMap<>();
        result.put("table", meta);
        result.put("columns", columns);
        return R.ok(result);
    }

    /**
     * 保存表元数据
     */
    @Operation(summary = "保存表元数据")
    @PostMapping("/table/save")
    public R<DnTableMeta> saveTable(@RequestBody DnTableMeta meta) {
        if (meta.getId() != null) {
            meta.setUpdatedAt(LocalDateTime.now());
            tableMetaMapper.updateById(meta);
        } else {
            meta.setCreatedAt(LocalDateTime.now());
            meta.setUpdatedAt(LocalDateTime.now());
            tableMetaMapper.insert(meta);
        }
        return R.ok(meta);
    }

    /**
     * 删除表元数据
     */
    @Transactional(rollbackFor = Exception.class)
    @Operation(summary = "删除表元数据")
    @DeleteMapping("/table/{id}")
    public R<String> deleteTable(@PathVariable Long id) {
        tableMetaMapper.deleteById(id);
        // 同时删除关联的字段元数据
        QueryWrapper<DnColumnMeta> colQw = new QueryWrapper<>();
        colQw.eq("table_meta_id", id);
        columnMetaMapper.delete(colQw);
        return R.ok("删除成功");
    }

    /**
     * 保存字段元数据
     */
    @Operation(summary = "保存字段元数据")
    @PostMapping("/column/save")
    public R<DnColumnMeta> saveColumn(@RequestBody DnColumnMeta meta) {
        if (meta.getId() != null) {
            meta.setUpdatedAt(LocalDateTime.now());
            columnMetaMapper.updateById(meta);
        } else {
            meta.setCreatedAt(LocalDateTime.now());
            meta.setUpdatedAt(LocalDateTime.now());
            columnMetaMapper.insert(meta);
        }
        return R.ok(meta);
    }

    /**
     * 批量保存字段元数据
     */
    @Transactional(rollbackFor = Exception.class)
    @Operation(summary = "批量保存字段元数据")
    @PostMapping("/columns/batch-save")
    public R<String> batchSaveColumns(@RequestBody List<DnColumnMeta> columns) {
        for (DnColumnMeta col : columns) {
            if (col.getId() != null) {
                col.setUpdatedAt(LocalDateTime.now());
                columnMetaMapper.updateById(col);
            } else {
                col.setCreatedAt(LocalDateTime.now());
                col.setUpdatedAt(LocalDateTime.now());
                columnMetaMapper.insert(col);
            }
        }
        return R.ok("保存成功");
    }

    /**
     * 元数据统计概览
     */
    @Operation(summary = "元数据统计")
    @GetMapping("/stats")
    public R<Map<String, Object>> stats() {
        Map<String, Object> data = new HashMap<>();
        data.put("tableCount", tableMetaMapper.selectCount(null));
        data.put("columnCount", columnMetaMapper.selectCount(null));

        QueryWrapper<DnTableMeta> coreQw = new QueryWrapper<>();
        coreQw.eq("importance", "core");
        data.put("coreTableCount", tableMetaMapper.selectCount(coreQw));

        return R.ok(data);
    }
}
