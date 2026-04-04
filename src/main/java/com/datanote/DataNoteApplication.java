package com.datanote;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * DataNote 数据开发平台启动类
 */
@SpringBootApplication
@MapperScan("com.datanote.mapper")
@EnableScheduling
@EnableAsync
public class DataNoteApplication {

    /**
     * 应用启动入口
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(DataNoteApplication.class, args);
    }
}
