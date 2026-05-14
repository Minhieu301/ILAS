package com.C1SE10.backend.controller.admin;

import com.C1SE10.backend.dto.common.ApiResponse;
import com.C1SE10.backend.dto.response.admin.ReportResponse;
import com.C1SE10.backend.service.admin.ReportExportService;
import com.C1SE10.backend.service.admin.AdminReportService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/reports")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AdminReportController {

    private final AdminReportService adminReportService;
    private final ReportExportService reportExportService;
    private final ObjectMapper objectMapper;

    @GetMapping
    public ResponseEntity<ApiResponse<ReportResponse>> getReport(
            @RequestParam(defaultValue = "week") String range,
            @RequestParam(defaultValue = "all") String reportType
    ) {
        try {
            ReportResponse response = adminReportService.getReport(range, reportType);
            return ResponseEntity.ok(ApiResponse.success("Lấy báo cáo thành công", response));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Không thể tải báo cáo: " + e.getMessage()));
        }
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(defaultValue = "excel") String format,
            @RequestParam(defaultValue = "week") String range,
            @RequestParam(defaultValue = "all") String reportType
    ) {
        try {
            ReportResponse response = adminReportService.getReport(range, reportType);
            var reportMap = objectMapper.convertValue(response, new TypeReference<java.util.Map<String, Object>>() {});

            byte[] fileBytes;
            String fileName;
            MediaType mediaType;

            if ("pdf".equalsIgnoreCase(format)) {
                fileBytes = reportExportService.exportToPdf(reportMap).toByteArray();
                fileName = "report-ilas.pdf";
                mediaType = MediaType.APPLICATION_PDF;
            } else {
                fileBytes = reportExportService.exportToExcel(reportMap).toByteArray();
                fileName = "report-ilas.xlsx";
                mediaType = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            }

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .body(fileBytes);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(("Không thể xuất báo cáo: " + e.getMessage()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}


