package com.opah;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OpahApplication {

    public static void main(String[] args) {
        // 确保数据目录存在（SQLite 需要目录已建）；支持 --opah.data-dir=xxx 命令行覆盖
        String dataDir = resolveDataDir(args);
        System.setProperty("opah.data-dir", dataDir);
        try {
            java.nio.file.Files.createDirectories(java.nio.file.Path.of(dataDir));
        } catch (Exception e) {
            System.err.println("无法创建数据目录 " + dataDir + ": " + e.getMessage());
        }
        SpringApplication.run(OpahApplication.class, args);
    }

    private static String resolveDataDir(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--opah.data-dir=")) {
                return arg.substring("--opah.data-dir=".length());
            }
        }
        return System.getProperty("opah.data-dir", "./data");
    }
}

