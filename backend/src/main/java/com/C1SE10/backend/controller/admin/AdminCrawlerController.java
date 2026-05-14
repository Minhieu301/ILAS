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
@RequestMapping("/api/admin/crawler")
public class AdminCrawlerController {

    @Value("${crawler.python.executable}")
    private String pythonExe;

    @Value("${crawler.python.workdir}")
    private String pythonWorkDir;

    @Value("${crawler.python.module}")
    private String pythonModule;

    @Value("${crawler.python.timeoutSeconds:600}")
    private long timeoutSeconds;

    @PostMapping("/laws")
    public ResponseEntity<?> crawlLaw(@RequestBody Map<String, String> body) {
        String url = body.get("url");

        if (url == null || url.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "URL không được để trống"));
        }

        // (tuỳ chọn) validate nhanh phía backend
        if (!url.startsWith("https://thuvienphapluat.vn/van-ban/")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "URL không hợp lệ (phải từ thuvienphapluat.vn/van-ban/)"));
        }

        Process process = null;
        try {
            Path backendDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();

            Path workDirPath = Paths.get(pythonWorkDir);
            if (!workDirPath.isAbsolute()) {
                workDirPath = backendDir.resolve(workDirPath).normalize();
            }

            if (!workDirPath.toFile().exists()) {
                return ResponseEntity.status(500).body(Map.of(
                        "message", "Không tìm thấy thư mục python workdir",
                        "workdir", workDirPath.toString()
                ));
            }

            String resolvedPythonExe = pythonExe;
            if ("python".equalsIgnoreCase((pythonExe == null ? "" : pythonExe).trim())) {
                Path venvPy = backendDir.resolve("../.venv/Scripts/python.exe").normalize();
                if (venvPy.toFile().exists()) {
                    resolvedPythonExe = venvPy.toString();
                }
            }

            ProcessBuilder pb = new ProcessBuilder(
                    resolvedPythonExe,
                    "-m",
                    pythonModule,
                    url.trim()
            );

            // đảm bảo chạy đúng thư mục python trong repo
            pb.directory(workDirPath.toFile());

            // ép UTF-8 để tránh lỗi tiếng Việt / emoji trên Windows
            pb.environment().put("PYTHONUTF8", "1");
            pb.environment().put("PYTHONIOENCODING", "utf-8");

            // gộp stderr vào stdout
            pb.redirectErrorStream(true);

            process = pb.start();

            // đọc logs (UTF-8)
            String logs = readAll(process.getInputStream());

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return ResponseEntity.status(504).body(Map.of(
                        "message", "Crawler chạy quá thời gian cho phép (timeout)",
                        "logs", logs
                ));
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                return ResponseEntity.status(500).body(Map.of(
                        "message", "Crawler lỗi",
                        "logs", logs,
                        "exitCode", exitCode,
                        "resolvedPythonExe", resolvedPythonExe,
                        "resolvedWorkDir", workDirPath.toString(),
                        "pythonModule", pythonModule
                ));
            }

            return ResponseEntity.ok(Map.of(
                    "message", "Cào luật thành công",
                    "logs", logs
            ));

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "message", "Lỗi khi chạy crawler",
                    "error", e.getMessage(),
                    "pythonExe", pythonExe,
                    "resolvedPythonExe", pythonExe,
                    "pythonWorkDir", pythonWorkDir,
                    "userDir", System.getProperty("user.dir")
            ));
        } finally {
            if (process != null) {
                try { process.getInputStream().close(); } catch (IOException ignored) {}
                try { process.getOutputStream().close(); } catch (IOException ignored) {}
                try { process.getErrorStream().close(); } catch (IOException ignored) {}
            }
        }
    }

    private String readAll(InputStream in) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        }
    }
}
