package com.opah;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OpahApplication {

    private static final int DEFAULT_PORT = 8787;
    private static final int MAX_PORT = 8796;

    /** 单实例锁引用，保持强引用直到 JVM 退出（进程结束由 OS 释放锁） */
    private static FileChannel lockChannel;
    private static FileLock instanceLock;

    public static void main(String[] args) {
        String dataDir = resolveDataDir(args);
        System.setProperty("opah.data-dir", dataDir);
        Path dir = Path.of(dataDir);
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            System.err.println("无法创建数据目录 " + dir + ": " + e.getMessage());
        }

        // 单实例锁：已有实例在运行 → 直接打开浏览器到其端口，不重复启动
        Path lockFile = dir.resolve("opah.lock");
        try {
            lockChannel = FileChannel.open(lockFile,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock lock = lockChannel.tryLock();
            if (lock == null) {
                int port = readPortFile(dir);
                System.out.println("Opah 已在运行（端口 " + port + "），直接打开浏览器。");
                openBrowserAsync(port);
                lockChannel.close();
                System.exit(0);   // 立即退出，不重复启动服务
            }
            instanceLock = lock;
        } catch (Exception e) {
            System.err.println("单实例锁获取失败: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }

        try {
            // 端口探测：默认 8787 起，被占用则递增（8788-8796）；命令行 --server.port 优先
            int port = resolvePort(args);
            System.setProperty("server.port", String.valueOf(port));
            SpringApplication app = new SpringApplication(OpahApplication.class);
            ConfigurableApplicationContext context = app.run(args);

            // 服务就绪后：记录端口 + 自动打开浏览器
            writePortFile(dir, port);
            System.out.println("Opah 已启动: http://127.0.0.1:" + port);
            openBrowserAsync(port);
            context.registerShutdownHook();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    /** 解析数据目录：--opah.data-dir=xxx > 系统属性 > jpackage 检测（exe 同级） > ./data */
    private static String resolveDataDir(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--opah.data-dir=")) {
                return arg.substring("--opah.data-dir=".length());
            }
        }
        String sys = System.getProperty("opah.data-dir");
        if (sys != null && !sys.isBlank()) {
            return sys;
        }
        // jpackage app-image：java.home = {image}/runtime，data 固定到 exe 同级 {image}/data，
        // 不依赖启动 cwd（重复双击 exe 才能命中同一把单实例锁）
        try {
            Path javaHome = Path.of(System.getProperty("java.home"));
            Path imageDir = javaHome.getParent();
            if (imageDir != null && Files.isDirectory(imageDir.resolve("app"))
                    && Files.exists(imageDir.resolve("opah.exe"))) {
                return imageDir.resolve("data").toString();
            }
        } catch (Exception ignored) {
            // fallthrough
        }
        return "./data";
    }

    /** 解析端口：命令行 --server.port 优先，否则从 8787 起探测空闲端口 */
    private static int resolvePort(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--server.port=")) {
                try {
                    return Integer.parseInt(arg.substring("--server.port=".length()));
                } catch (NumberFormatException ignored) {
                    // fallthrough to probing
                }
            }
        }
        for (int port = DEFAULT_PORT; port <= MAX_PORT; port++) {
            try (ServerSocket socket = new ServerSocket(port)) {
                return port;
            } catch (IOException ignored) {
                // 端口被占用，尝试下一个
            }
        }
        return DEFAULT_PORT;
    }

    /** 端口落盘，供"重复点击 → 直接打开浏览器"时读取 */
    private static void writePortFile(Path dir, int port) {
        try {
            Files.writeString(dir.resolve("opah.port"), String.valueOf(port), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("写入端口文件失败: " + e.getMessage());
        }
    }

    private static int readPortFile(Path dir) {
        try {
            Path f = dir.resolve("opah.port");
            if (Files.exists(f)) {
                return Integer.parseInt(Files.readString(f, StandardCharsets.UTF_8).trim());
            }
        } catch (Exception ignored) {
            // fallthrough to default
        }
        return DEFAULT_PORT;
    }

    /** 异步打开浏览器（独立 daemon 线程，绝不阻塞主流程/JVM 退出） */
    private static void openBrowserAsync(int port) {
        Thread t = new Thread(() -> openBrowser(port), "opah-browser-launcher");
        t.setDaemon(true);
        t.start();
    }

    /** 打开默认浏览器（优先 java.awt.Desktop，无外部子进程；跨平台 fallback） */
    private static void openBrowser(int port) {
        String url = "http://127.0.0.1:" + port;
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
                return;
            }
        } catch (Exception ignored) {
            // fallthrough to process-based fallback
        }
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                new ProcessBuilder("cmd", "/c", "start", "", url)
                        .redirectErrorStream(true).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", url).start();
            } else {
                new ProcessBuilder("xdg-open", url).start();
            }
        } catch (Exception e) {
            System.err.println("无法自动打开浏览器，请手动访问 " + url);
        }
    }
}
