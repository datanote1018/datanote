package com.datanote.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.datanote.model.ColumnInfo;
import com.datanote.util.ProcessUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.util.List;

/**
 * DataX 同步服务 — 生成 DataX JSON 配置并执行同步任务
 */
@Service
public class DataxService {

    private static final Logger log = LoggerFactory.getLogger(DataxService.class);

    @Value("${datax.home}")
    private String dataxHome;

    @Value("${datax.jvm}")
    private String dataxJvm;

    @Value("${datax.job-dir}")
    private String jobDir;

    @Value("${datax.mode:local}")
    private String dataxMode;

    @Value("${hive.default-fs}")
    private String hiveDefaultFs;

    @Value("${hive.warehouse}")
    private String hiveWarehouse;

    /**
     * Docker 模式下，将 127.0.0.1/localhost 替换为 host.docker.internal
     * 使容器内的 DataX 能访问宿主机上的数据源
     */
    private String translateHost(String host) {
        if ("docker".equals(dataxMode)) {
            if ("127.0.0.1".equals(host) || "localhost".equals(host)) {
                return "host.docker.internal";
            }
        }
        return host;
    }

    /**
     * Docker 模式下翻译 HDFS 地址
     * hdfs://localhost:9000 → hdfs://namenode:8020（Docker 网络内 NameNode 的地址）
     * hdfs://127.0.0.1:9000 → hdfs://namenode:8020
     */
    private String translateHdfsUrl(String url) {
        if ("docker".equals(dataxMode) && url != null) {
            return url.replace("localhost", "namenode")
                      .replace("127.0.0.1", "namenode")
                      .replace(":9000", ":8020");
        }
        return url;
    }

    /**
     * 在内存中构建 DataX JSON 字符串（mysqlreader → hdfswriter），不写磁盘
     */
    public String generateJobJsonString(String mysqlHost, int mysqlPort, String mysqlUser, String mysqlPassword,
                                        String sourceDb, String sourceTable,
                                        String odsTable, List<ColumnInfo> columns) {
        String actualHost = translateHost(mysqlHost);

        JSONObject job = new JSONObject(true);
        JSONObject jobContent = new JSONObject(true);

        // === Reader: mysqlreader ===
        JSONObject reader = new JSONObject(true);
        reader.put("name", "mysqlreader");
        JSONObject readerParam = new JSONObject(true);
        readerParam.put("username", mysqlUser);
        readerParam.put("password", mysqlPassword);

        JSONArray columnArr = new JSONArray();
        for (ColumnInfo col : columns) {
            columnArr.add(col.getName());
        }
        readerParam.put("column", columnArr);

        JSONArray connArr = new JSONArray();
        JSONObject conn = new JSONObject(true);
        conn.put("jdbcUrl", new JSONArray() {{
            add("jdbc:mysql://" + actualHost + ":" + mysqlPort + "/" + sourceDb
                    + "?useUnicode=true&characterEncoding=UTF-8&useSSL=false");
        }});
        conn.put("table", new JSONArray() {{ add(sourceTable); }});
        connArr.add(conn);
        readerParam.put("connection", connArr);
        reader.put("parameter", readerParam);

        // === Writer: hdfswriter ===
        JSONObject writer = new JSONObject(true);
        writer.put("name", "hdfswriter");
        JSONObject writerParam = new JSONObject(true);

        String today = java.time.LocalDate.now().minusDays(1).toString();
        writerParam.put("defaultFS", translateHdfsUrl(hiveDefaultFs));
        writerParam.put("fileType", "orc");
        writerParam.put("path", hiveWarehouse + "/ods.db/" + odsTable + "/dt=" + today);
        writerParam.put("fileName", odsTable);
        writerParam.put("writeMode", "truncate");
        writerParam.put("fieldDelimiter", "\t");
        writerParam.put("compress", "SNAPPY");

        JSONArray writerColumns = new JSONArray();
        for (ColumnInfo col : columns) {
            JSONObject wc = new JSONObject(true);
            wc.put("name", col.getName().toLowerCase());
            wc.put("type", "string");
            writerColumns.add(wc);
        }
        writerParam.put("column", writerColumns);
        writer.put("parameter", writerParam);

        // === Assemble ===
        JSONArray contentArr = new JSONArray();
        JSONObject contentItem = new JSONObject(true);
        contentItem.put("reader", reader);
        contentItem.put("writer", writer);
        contentArr.add(contentItem);

        JSONObject setting = new JSONObject(true);
        JSONObject speed = new JSONObject(true);
        speed.put("channel", 3);
        setting.put("speed", speed);

        jobContent.put("content", contentArr);
        jobContent.put("setting", setting);
        job.put("job", jobContent);

        return JSON.toJSONString(job, true);
    }

    /**
     * 执行 DataX 同步任务（从内存中的 JSON 字符串）
     * docker 模式：通过 stdin 直接写入容器，不落宿主机磁盘
     * local  模式：写临时文件 → 执行 → 删除
     */
    public ProcessUtil.ExecResult runJobInMemory(String jsonContent, String taskName) throws Exception {
        if ("docker".equals(dataxMode)) {
            return runJobDockerInMemory(jsonContent, taskName);
        }
        return runJobLocalInMemory(jsonContent, taskName);
    }

    private ProcessUtil.ExecResult runJobDockerInMemory(String jsonContent, String taskName) throws Exception {
        String containerName = "datanote-datax";
        // 单次 exec：stdin 流入 → mktemp 写入容器 /tmp → datax 执行 → rm 删除
        // JSON 配置以 MySQL 为唯一持久化来源，容器内无残留文件
        String shell =
                "TMPJOB=$(mktemp /tmp/datax_XXXXXX.json); " +
                "cat > \"$TMPJOB\"; " +
                "python /opt/datax/bin/datax.py \"$TMPJOB\"; " +
                "EC=$?; rm -f \"$TMPJOB\"; exit $EC";
        String[] cmd = {"/usr/local/bin/docker", "exec", "-i", containerName, "bash", "-c", shell};
        log.info("执行 DataX (docker), taskName={}", taskName);
        return ProcessUtil.execWithStdin(cmd, jsonContent.getBytes(java.nio.charset.StandardCharsets.UTF_8), 600);
    }

    private ProcessUtil.ExecResult runJobLocalInMemory(String jsonContent, String taskName) throws Exception {
        new File(jobDir).mkdirs();
        String tmpFile = jobDir + "/" + taskName + "_" + System.currentTimeMillis() + ".json";
        try {
            try (FileWriter fw = new FileWriter(tmpFile)) {
                fw.write(jsonContent);
            }
            String classpath = dataxHome + "/lib/*";
            String[] cmd = {
                    "java", "-server", "-Xms1g", "-Xmx1g",
                    "-Ddatax.home=" + dataxHome, "-classpath", classpath,
                    "com.alibaba.datax.core.Engine",
                    "-mode", "standalone", "-jobid", "-1", "-job", tmpFile
            };
            log.info("执行 DataX (local): {}", tmpFile);
            return ProcessUtil.exec(cmd, 600);
        } finally {
            new File(tmpFile).delete();
        }
    }
}
