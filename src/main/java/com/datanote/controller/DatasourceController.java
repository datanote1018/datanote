package com.datanote.controller;

import com.datanote.common.Constants;
import com.datanote.exception.ResourceNotFoundException;
import com.datanote.model.ColumnInfo;
import com.datanote.model.DnDatasource;
import com.datanote.model.R;
import com.datanote.mapper.DnDatasourceMapper;
import com.datanote.service.MetadataService;
import com.datanote.util.CryptoUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据源管理 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/datasource")
@Tag(name = "数据源管理", description = "数据源的增删改查、连接测试、元数据浏览")
@RequiredArgsConstructor
public class DatasourceController {

    private final DnDatasourceMapper datasourceMapper;
    private final MetadataService metadataService;

    @Value("${datanote.crypto.key}")
    private String cryptoKey;

    /**
     * 获取数据源列表（密码已脱敏）
     *
     * @return 数据源列表
     */
    @Operation(summary = "数据源列表")
    @GetMapping("/list")
    public R<List<DnDatasource>> list() {
        List<DnDatasource> result = datasourceMapper.selectList(null);
        result.forEach(ds -> ds.setPassword(Constants.PASSWORD_MASK));
        return R.ok(result);
    }

    /**
     * 查询数据源详情（密码已解密）
     *
     * @param id 数据源 ID
     * @return 数据源详情
     */
    @Operation(summary = "查询数据源详情")
    @GetMapping("/{id}")
    public R<DnDatasource> getById(@PathVariable Long id) {
        DnDatasource ds = datasourceMapper.selectById(id);
        if (ds == null) {
            throw new ResourceNotFoundException("数据源");
        }
        // 前端不需要看到真实密码，只需知道是否已设置
        ds.setPassword(Constants.PASSWORD_MASK);
        return R.ok(ds);
    }

    /**
     * 保存数据源（新增或更新），密码自动加密存储
     *
     * @param ds 数据源对象
     * @return 保存后的数据源（密码已脱敏）
     */
    @Operation(summary = "保存数据源")
    @PostMapping("/save")
    public R<DnDatasource> save(@RequestBody DnDatasource ds) {
        if (ds.getId() != null) {
            ds.setUpdatedAt(LocalDateTime.now());
            if (Constants.PASSWORD_MASK.equals(ds.getPassword())) {
                // 前端未修改密码，保留数据库中已加密的密码
                DnDatasource old = datasourceMapper.selectById(ds.getId());
                if (old != null) {
                    ds.setPassword(old.getPassword());
                }
            } else {
                // 前端提交了新密码，加密后存库
                ds.setPassword(CryptoUtil.encrypt(ds.getPassword(), cryptoKey));
            }
            datasourceMapper.updateById(ds);
        } else {
            ds.setCreatedAt(LocalDateTime.now());
            ds.setUpdatedAt(LocalDateTime.now());
            ds.setStatus(1);
            ds.setPassword(CryptoUtil.encrypt(ds.getPassword(), cryptoKey));
            datasourceMapper.insert(ds);
        }
        ds.setPassword(Constants.PASSWORD_MASK);
        return R.ok(ds);
    }

    /**
     * 删除数据源
     *
     * @param id 数据源 ID
     * @return 操作结果
     */
    @Operation(summary = "删除数据源")
    @DeleteMapping("/{id}")
    public R<String> delete(@PathVariable Long id) {
        datasourceMapper.deleteById(id);
        return R.ok("删除成功");
    }

    /**
     * 测试数据源连接
     */
    @Operation(summary = "测试数据源连接")
    @PostMapping("/test")
    public R<String> testConnection(@RequestBody DnDatasource ds) {
        try {
            String pwd = ds.getPassword();
            if (Constants.PASSWORD_MASK.equals(pwd) && ds.getId() != null) {
                // 前端未修改密码，从数据库取已加密密码并解密
                DnDatasource old = datasourceMapper.selectById(ds.getId());
                if (old != null) {
                    pwd = CryptoUtil.decryptSafe(old.getPassword(), cryptoKey);
                }
            }
            String url = "jdbc:mysql://" + ds.getHost() + ":" + ds.getPort()
                    + "/?useSSL=false&allowPublicKeyRetrieval=true&connectTimeout=3000";
            try (Connection conn = DriverManager.getConnection(url, ds.getUsername(), pwd)) {
                return R.ok("连接成功");
            }
        } catch (SQLException e) {
            log.error("测试数据源连接失败", e);
            return R.fail("连接失败，请检查数据源配置");
        }
    }

    /**
     * 获取指定数据源下的数据库列表
     */
    @Operation(summary = "获取数据库列表")
    @GetMapping("/{id}/databases")
    public R<List<String>> databases(@PathVariable Long id) {
        try {
            DnDatasource ds = requireDatasource(id);
            return R.ok(metadataService.getDatabasesByConnection(ds.getHost(), ds.getPort(), ds.getUsername(), ds.getPassword()));
        } catch (SQLException e) {
            log.error("获取数据库列表失败, datasourceId={}", id, e);
            return R.fail("获取数据库列表失败");
        }
    }

    /**
     * 获取指定数据源指定库的表列表
     */
    @Operation(summary = "获取表列表")
    @GetMapping("/{id}/tables")
    public R<List<String>> tables(@PathVariable Long id, @RequestParam String db) {
        try {
            DnDatasource ds = requireDatasource(id);
            return R.ok(metadataService.getTablesByConnection(ds.getHost(), ds.getPort(), ds.getUsername(), ds.getPassword(), db));
        } catch (SQLException e) {
            log.error("获取表列表失败, datasourceId={}, db={}", id, db, e);
            return R.fail("获取表列表失败");
        }
    }

    /**
     * 获取指定数据源指定库表的字段列表
     */
    @Operation(summary = "获取字段列表")
    @GetMapping("/{id}/columns")
    public R<List<ColumnInfo>> columns(@PathVariable Long id, @RequestParam String db, @RequestParam String table) {
        try {
            DnDatasource ds = requireDatasource(id);
            return R.ok(metadataService.getColumnsByConnection(ds.getHost(), ds.getPort(), ds.getUsername(), ds.getPassword(), db, table));
        } catch (SQLException e) {
            log.error("获取字段列表失败, datasourceId={}, db={}, table={}", id, db, table, e);
            return R.fail("获取字段列表失败");
        }
    }

    private DnDatasource requireDatasource(Long id) {
        DnDatasource ds = datasourceMapper.selectById(id);
        if (ds == null) {
            throw new ResourceNotFoundException("数据源");
        }
        ds.setPassword(CryptoUtil.decryptSafe(ds.getPassword(), cryptoKey));
        return ds;
    }

}
