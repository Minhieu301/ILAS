package com.C1SE10.backend.controller.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/admin/ai")
public class AdminAIController {

    @Value("${crawler.python.executable:python}")
    private String pythonExe;

    @Value("${crawler.python.workdir:../python}")
    private String pythonWorkDir;

    @Value("${crawler.python.timeoutSeconds:600}")
    private long timeoutSeconds;

    private static volatile Process chatbotProcess = null;

    private String resolvePythonExecutable(Path backendDir, Path workDirPath) {
        Path projectRoot = backendDir.getParent();

        Path[] candidates = new Path[] {
                projectRoot != null ? projectRoot.resolve(".venv").resolve("Scripts").resolve("python.exe") : null,
                projectRoot != null ? projectRoot.resolve(".venv").resolve("Scripts").resolve("python") : null,
                workDirPath.resolve(".venv").resolve("Scripts").resolve("python.exe"),
                workDirPath.resolve(".venv").resolve("Scripts").resolve("python"),
        };

        for (Path candidate : candidates) {
            if (candidate != null && candidate.toFile().exists()) {
                return candidate.toString();
            }
        }

        return pythonExe;
    }

    /**
     * Rebuild all AI indexes and vector stores
     */
    @PostMapping("/rebuild")
    public ResponseEntity<?> rebuildAI() {
        Process process = null;
        try {
            Path backendDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
            Path workDirPath = Paths.get(pythonWorkDir);
            if (!workDirPath.isAbsolute()) {
                workDirPath = backendDir.resolve(workDirPath);
            }
            workDirPath = workDirPath.normalize();

            // Find python executable
            String pythonCmd = resolvePythonExecutable(backendDir, workDirPath);

            Path rebuildScript = workDirPath.resolve("ai").resolve("rebuild_all.py");
            if (!rebuildScript.toFile().exists()) {
                return ResponseEntity.status(404).body(Map.of(
                        "message", "rebuild_all.py not found at: " + rebuildScript,
                        "status", "file_not_found",
                        "path", rebuildScript.toString()
                ));
            }

            String[] cmd = {pythonCmd, "-m", "ai.rebuild_all"};

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(workDirPath.toFile());
            pb.redirectErrorStream(true);

            process = pb.start();

            // Capture output
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

            if (!finished) {
                process.destroy();
                return ResponseEntity.status(408).body(Map.of(
                        "message", "Quá thời gian chờ rebuild AI",
                        "status", "timeout",
                        "timeoutSeconds", timeoutSeconds
                ));
            }

            int exitCode = process.exitValue();
            if (exitCode == 0) {
                return ResponseEntity.ok(Map.of(
                        "message", "✅ Rebuild AI thành công",
                        "status", "success",
                        "exitCode", exitCode,
                        "output", output.toString()
                ));
            } else {
                return ResponseEntity.status(500).body(Map.of(
                        "message", "❌ Rebuild AI thất bại",
                        "status", "error",
                        "exitCode", exitCode,
                        "output", output.toString()
                ));
            }

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                    "message", "Lỗi khi rebuild AI: " + e.getMessage(),
                    "status", "exception",
                    "error", e.getClass().getSimpleName(),
                    "stackTrace", e.toString()
            ));
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    /**
     * Start chatbot AI service
     */
    @PostMapping("/start")
    public ResponseEntity<?> startChatbot() {
        try {
            // Check if already running
            if (chatbotProcess != null && chatbotProcess.isAlive()) {
                return ResponseEntity.ok(Map.of(
                        "message", "Chatbot AI đang chạy",
                        "status", "already_running"
                ));
            }

            Path backendDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
            Path workDirPath = Paths.get(pythonWorkDir);
            if (!workDirPath.isAbsolute()) {
                workDirPath = backendDir.resolve(workDirPath);
            }
            workDirPath = workDirPath.normalize();

            // Find python executable
            String pythonCmd = resolvePythonExecutable(backendDir, workDirPath);

            String[] cmd = {pythonCmd, "-m", "ai.app"};

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(workDirPath.toFile());
            pb.redirectErrorStream(true);

            chatbotProcess = pb.start();

            // Give it a moment to start
            Thread.sleep(2000);

            if (chatbotProcess.isAlive()) {
                return ResponseEntity.ok(Map.of(
                        "message", "✅ Chatbot AI đã khởi động",
                        "status", "started"
                ));
            } else {
                int exitCode = chatbotProcess.exitValue();
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(chatbotProcess.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }

                return ResponseEntity.status(500).body(Map.of(
                        "message", "Chatbot AI không thể khởi động",
                        "status", "failed_to_start",
                        "exitCode", exitCode,
                        "output", output.toString()
                ));
            }

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "message", "Lỗi khi khởi động Chatbot AI: " + e.getMessage(),
                    "status", "exception",
                    "error", e.getClass().getSimpleName()
            ));
        }
    }

    /**
     * Stop chatbot AI service
     */
    @DeleteMapping("/stop")
    public ResponseEntity<?> stopChatbot() {
        try {
            if (chatbotProcess == null) {
                return ResponseEntity.ok(Map.of(
                        "message", "Chatbot AI không đang chạy",
                        "status", "not_running"
                ));
            }

            if (!chatbotProcess.isAlive()) {
                chatbotProcess = null;
                return ResponseEntity.ok(Map.of(
                        "message", "Chatbot AI đã dừng (process không còn hoạt động)",
                        "status", "already_stopped"
                ));
            }

            chatbotProcess.destroyForcibly();
            Thread.sleep(1000);

            if (chatbotProcess.isAlive()) {
                return ResponseEntity.status(500).body(Map.of(
                        "message", "Không thể dừng Chatbot AI",
                        "status", "failed_to_stop"
                ));
            }

            chatbotProcess = null;
            return ResponseEntity.ok(Map.of(
                    "message", "✅ Chatbot AI đã dừng",
                    "status", "stopped"
            ));

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "message", "Lỗi khi dừng Chatbot AI: " + e.getMessage(),
                    "status", "exception",
                    "error", e.getClass().getSimpleName()
            ));
        }
    }

    /**
     * Check chatbot status
     */
    @GetMapping("/status")
    public ResponseEntity<?> checkStatus() {
        boolean isRunning = chatbotProcess != null && chatbotProcess.isAlive();
        return ResponseEntity.ok(Map.of(
                "status", isRunning ? "running" : "stopped",
                "isRunning", isRunning
        ));
    }
}
