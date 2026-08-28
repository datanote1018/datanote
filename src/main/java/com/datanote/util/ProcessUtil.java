package com.datanote.util;

import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

/**
 * 外部进程执行工具 — 封装 ProcessBuilder 调用及超时控制
 */
public class ProcessUtil {

    private static final Logger log = LoggerFactory.getLogger(ProcessUtil.class);

    /**
     * 命令执行结果
     */
    @Data
    public static class ExecResult {
        private int exitCode;
        private String output;
        private long durationMs;
    }

    /**
     * 执行外部命令，返回结果
     *
     * @param cmd        命令数组
     * @param timeoutSec 超时时间（秒）
     */
    public static ExecResult exec(String[] cmd, int timeoutSec) throws Exception {
        long start = System.currentTimeMillis();

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        enrichPath(pb);
        Process process = pb.start();

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }

        boolean finished = process.waitFor(timeoutSec, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            log.warn("命令执行超时（{}秒），已强制终止", timeoutSec);
        }

        ExecResult result = new ExecResult();
        result.setExitCode(finished ? process.exitValue() : 143);
        result.setOutput(sb.toString());
        result.setDurationMs(System.currentTimeMillis() - start);
        return result;
    }

    /**
     * 执行外部命令并通过 stdin 传入数据（用于 docker exec -i 写文件场景）
     */
    public static ExecResult execWithStdin(String[] cmd, byte[] stdinData, int timeoutSec) throws Exception {
        long start = System.currentTimeMillis();

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        enrichPath(pb);
        Process process = pb.start();

        try (OutputStream os = process.getOutputStream()) {
            os.write(stdinData);
        }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }

        boolean finished = process.waitFor(timeoutSec, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            log.warn("命令执行超时（{}秒），已强制终止", timeoutSec);
        }

        ExecResult result = new ExecResult();
        result.setExitCode(finished ? process.exitValue() : 143);
        result.setOutput(sb.toString());
        result.setDurationMs(System.currentTimeMillis() - start);
        return result;
    }

    /** 把 /usr/local/bin 追加到子进程 PATH，让 ProcessBuilder 能找到 docker 等工具 */
    private static void enrichPath(ProcessBuilder pb) {
        String current = pb.environment().getOrDefault("PATH", "");
        if (!current.contains("/usr/local/bin")) {
            pb.environment().put("PATH", current + ":/usr/local/bin");
        }
    }
}
