package com.C1SE10.backend.service.admin;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.pdf.BaseFont;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ReportExportService {

    private static final String DATE_FORMAT = "dd/MM/yyyy HH:mm";
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_FORMAT);

    /**
     * Xuất báo cáo dưới dạng Excel
     */
    public ByteArrayOutputStream exportToExcel(Map<String, Object> reportData) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        
        // Sheet 1: Thống kê tổng quan
        createSummarySheet(workbook, reportData);
        
        // Sheet 2: Dữ liệu tuần
        createWeeklyDataSheet(workbook, reportData);
        
        // Sheet 3: Top trang truy cập
        createTopContentSheet(workbook, reportData);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        workbook.write(baos);
        workbook.close();
        
        return baos;
    }

    /**
     * Xuất báo cáo dưới dạng PDF với bảng
     */
    public ByteArrayOutputStream exportToPdf(Map<String, Object> reportData) throws IOException, DocumentException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        Document document = new Document(PageSize.A4, 20, 20, 32, 32);
        PdfWriter.getInstance(document, baos);
        document.open();

        // Register system fonts so FontFactory can find fonts that support Vietnamese/Unicode
        // This avoids using the built-in 14 PDF fonts which don't support accented characters.
        try {
            FontFactory.registerDirectories();
        } catch (Exception ignored) {
            // If registration fails, we'll fall back to default fonts (may lose diacritics).
        }

        com.itextpdf.text.Font titleFont = FontFactory.getFont("Arial", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, 20, com.itextpdf.text.Font.BOLD, new BaseColor(15, 23, 42));
        com.itextpdf.text.Font subtitleFont = FontFactory.getFont("Arial", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, 10, com.itextpdf.text.Font.NORMAL, new BaseColor(71, 85, 105));
        com.itextpdf.text.Font sectionFont = FontFactory.getFont("Arial", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, 13, com.itextpdf.text.Font.BOLD, new BaseColor(15, 23, 42));
        com.itextpdf.text.Font headerFont = FontFactory.getFont("Arial", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, 10, com.itextpdf.text.Font.BOLD, BaseColor.WHITE);
        com.itextpdf.text.Font normalFont = FontFactory.getFont("Arial", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, 10, com.itextpdf.text.Font.NORMAL, new BaseColor(30, 41, 59));

        PdfPTable hero = new PdfPTable(1);
        hero.setWidthPercentage(100);
        PdfPCell heroCell = new PdfPCell();
        heroCell.setBackgroundColor(new BaseColor(239, 246, 255));
        heroCell.setBorderColor(new BaseColor(191, 219, 254));
        heroCell.setPadding(14f);
        Paragraph title = new Paragraph("BÁO CÁO THỐNG KÊ HỆ THỐNG ILAS", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(6f);
        heroCell.addElement(title);
        Paragraph period = new Paragraph("Khoảng thời gian: " + readPeriodLabel(reportData), subtitleFont);
        period.setAlignment(Element.ALIGN_CENTER);
        heroCell.addElement(period);
        Paragraph exportedAt = new Paragraph("Xuất lúc: " + LocalDateTime.now().format(formatter), subtitleFont);
        exportedAt.setAlignment(Element.ALIGN_CENTER);
        heroCell.addElement(exportedAt);
        hero.addCell(heroCell);
        document.add(hero);

        document.add(new Paragraph(" "));

        Paragraph section1 = new Paragraph("TỔNG QUAN NHANH", sectionFont);
        section1.setSpacingAfter(8f);
        document.add(section1);
        document.add(createPdfSummaryTable(reportData, headerFont, normalFont));

        document.add(new Paragraph(" "));

        Paragraph section2 = new Paragraph("TOP TRANG ĐƯỢC TRUY CẬP", sectionFont);
        section2.setSpacingAfter(8f);
        document.add(section2);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> topContents = (List<Map<String, Object>>) reportData.get("topContents");
        if (topContents != null && !topContents.isEmpty()) {
            document.add(createPdfTopTable(topContents, headerFont, normalFont));
        }

        document.close();
        return baos;
    }

    /**
     * Tạo sheet thống kê tổng quan
     */
    private void createSummarySheet(Workbook workbook, Map<String, Object> reportData) {
        Sheet sheet = workbook.createSheet("Tổng Quan");

        CellStyle titleStyle = createTitleStyle(workbook);
        CellStyle subtitleStyle = createSubtitleStyle(workbook);
        CellStyle sectionStyle = createSectionStyle(workbook);
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle labelStyle = createBodyStyle(workbook, HorizontalAlignment.LEFT);
        CellStyle valueStyle = createBodyStyle(workbook, HorizontalAlignment.CENTER);
        CellStyle percentStyle = createPercentStyle(workbook);

        sheet.createFreezePane(0, 5);
        sheet.setColumnWidth(0, 6200);
        sheet.setColumnWidth(1, 4200);
        sheet.setColumnWidth(2, 4200);
        sheet.setColumnWidth(3, 5200);

        Row titleRow = sheet.createRow(0);
        titleRow.createCell(0).setCellValue("BÁO CÁO THỐNG KÊ HỆ THỐNG ILAS");
        titleRow.getCell(0).setCellStyle(titleStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 3));

        Row subtitleRow = sheet.createRow(1);
        subtitleRow.createCell(0).setCellValue("Xuất lúc: " + LocalDateTime.now().format(formatter));
        subtitleRow.getCell(0).setCellStyle(subtitleStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(1, 1, 0, 3));

        Row periodRow = sheet.createRow(2);
        periodRow.createCell(0).setCellValue("Khoảng thời gian");
        periodRow.createCell(1).setCellValue(readPeriodLabel(reportData));
        periodRow.getCell(0).setCellStyle(sectionStyle);
        periodRow.getCell(1).setCellStyle(subtitleStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(2, 2, 1, 3));

        Row headerRow = sheet.createRow(4);
        headerRow.createCell(0).setCellValue("Chỉ tiêu");
        headerRow.createCell(1).setCellValue("Giá trị");
        headerRow.createCell(2).setCellValue("Thay đổi (%)");
        headerRow.createCell(3).setCellValue("Ghi chú");
        applyHeaderStyle(headerRow, headerStyle);

        @SuppressWarnings("unchecked")
        Map<String, Object> stats = (Map<String, Object>) reportData.get("stats");
        int rowNum = 5;

        String[][] data = {
            {"Tổng Người Dùng", "totalUsers", "usersChange", "Tổng số tài khoản"},
            {"Tổng Nội Dung", "totalContent", "contentChange", "Bài viết/nội dung đã xử lý"},
            {"Tổng Biểu Mẫu", "totalForms", "formsChange", "Biểu mẫu trong hệ thống"},
            {"Tổng Phản Hồi", "totalFeedback", "feedbackChange", "Lượt phản hồi của người dùng"}
        };

        for (String[] item : data) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(item[0]);
            row.createCell(1).setCellValue(stats != null && stats.get(item[1]) != null ? stats.get(item[1]).toString() : "0");
            row.createCell(2).setCellValue(stats != null && stats.get(item[2]) != null ? Double.parseDouble(stats.get(item[2]).toString()) : 0);
            row.createCell(3).setCellValue(item[3]);
            row.getCell(0).setCellStyle(labelStyle);
            row.getCell(1).setCellStyle(valueStyle);
            row.getCell(2).setCellStyle(percentStyle);
            row.getCell(3).setCellStyle(labelStyle);
        }
    }

    /**
     * Tạo sheet dữ liệu tuần
     */
    private void createWeeklyDataSheet(Workbook workbook, Map<String, Object> reportData) {
        Sheet sheet = workbook.createSheet("Dữ Liệu Ngày");
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle bodyLeftStyle = createBodyStyle(workbook, HorizontalAlignment.LEFT);
        CellStyle bodyCenterStyle = createBodyStyle(workbook, HorizontalAlignment.CENTER);
        
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Ngày");
        headerRow.createCell(1).setCellValue("Người dùng");
        headerRow.createCell(2).setCellValue("Nội dung");
        headerRow.createCell(3).setCellValue("Biểu mẫu");
        applyHeaderStyle(headerRow, headerStyle);
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> weeklyData = (List<Map<String, Object>>) reportData.get("weeklyData");
        if (weeklyData != null) {
            int rowNum = 1;
            for (Map<String, Object> item : weeklyData) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(item.get("label") != null ? item.get("label").toString() : "");
                row.createCell(1).setCellValue(item.get("users") != null ? Long.parseLong(item.get("users").toString()) : 0);
                row.createCell(2).setCellValue(item.get("content") != null ? Long.parseLong(item.get("content").toString()) : 0);
                row.createCell(3).setCellValue(item.get("forms") != null ? Long.parseLong(item.get("forms").toString()) : 0);
                row.getCell(0).setCellStyle(bodyLeftStyle);
                row.getCell(1).setCellStyle(bodyCenterStyle);
                row.getCell(2).setCellStyle(bodyCenterStyle);
                row.getCell(3).setCellStyle(bodyCenterStyle);
            }
        }
        
        sheet.setColumnWidth(0, 4000);
        sheet.setColumnWidth(1, 4000);
        sheet.setColumnWidth(2, 4000);
        sheet.setColumnWidth(3, 4000);
    }

    /**
     * Tạo sheet top trang truy cập
     */
    private void createTopContentSheet(Workbook workbook, Map<String, Object> reportData) {
        Sheet sheet = workbook.createSheet("Top Truy Cập");
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle bodyLeftStyle = createBodyStyle(workbook, HorizontalAlignment.LEFT);
        CellStyle bodyCenterStyle = createBodyStyle(workbook, HorizontalAlignment.CENTER);
        
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("STT");
        headerRow.createCell(1).setCellValue("Trang / Từ khóa");
        headerRow.createCell(2).setCellValue("Lượt xem");
        applyHeaderStyle(headerRow, headerStyle);
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> topContents = (List<Map<String, Object>>) reportData.get("topContents");
        if (topContents != null) {
            int rowNum = 1;
            int stt = 1;
            for (Map<String, Object> item : topContents) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(stt++);
                row.createCell(1).setCellValue(item.get("title") != null ? item.get("title").toString() : "");
                row.createCell(2).setCellValue(item.get("views") != null ? Long.parseLong(item.get("views").toString()) : 0);
                row.getCell(0).setCellStyle(bodyCenterStyle);
                row.getCell(1).setCellStyle(bodyLeftStyle);
                row.getCell(2).setCellStyle(bodyCenterStyle);
            }
        }
        
        sheet.setColumnWidth(0, 2200);
        sheet.setColumnWidth(1, 8500);
        sheet.setColumnWidth(2, 4200);
    }

    /**
     * Tạo style cho header
     */
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createTitleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 16);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createSubtitleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) 10);
        font.setColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle createSectionStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle createBodyStyle(Workbook workbook, HorizontalAlignment alignment) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(alignment);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createPercentStyle(Workbook workbook) {
        CellStyle style = createBodyStyle(workbook, HorizontalAlignment.CENTER);
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("0.0%"));
        return style;
    }

    private String readPeriodLabel(Map<String, Object> reportData) {
        Object period = reportData.get("period");
        if (period instanceof Map<?, ?> periodMap) {
            Object range = periodMap.get("range");
            if (range != null) {
                String key = range.toString();
                return switch (key) {
                    case "month" -> "Tháng này";
                    case "quarter" -> "Quý này";
                    case "year" -> "Năm nay";
                    default -> "Tuần này";
                };
            }
        }
        return "Tuần này";
    }

    private PdfPTable createPdfSummaryTable(Map<String, Object> reportData,
                                            com.itextpdf.text.Font headerFont,
                                            com.itextpdf.text.Font normalFont) throws DocumentException {
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1.5f, 1.5f, 1.5f, 1.5f});

        @SuppressWarnings("unchecked")
        Map<String, Object> stats = (Map<String, Object>) reportData.get("stats");

        addPdfMetricCell(table, "Tổng người dùng", safeStat(stats, "totalUsers"), normalFont, headerFont);
        addPdfMetricCell(table, "Tổng nội dung", safeStat(stats, "totalContent"), normalFont, headerFont);
        addPdfMetricCell(table, "Tổng biểu mẫu", safeStat(stats, "totalForms"), normalFont, headerFont);
        addPdfMetricCell(table, "Tổng phản hồi", safeStat(stats, "totalFeedback"), normalFont, headerFont);

        return table;
    }

    private PdfPTable createPdfTopTable(List<Map<String, Object>> topContents,
                                        com.itextpdf.text.Font headerFont,
                                        com.itextpdf.text.Font normalFont) throws DocumentException {
        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{0.8f, 4.8f, 1.2f});
        table.setSpacingBefore(4f);

        table.addCell(pdfHeaderCell("STT", headerFont));
        table.addCell(pdfHeaderCell("Trang / Từ khóa", headerFont));
        table.addCell(pdfHeaderCell("Lượt xem", headerFont));

        int index = 1;
        for (Map<String, Object> item : topContents) {
            table.addCell(pdfBodyCell(String.valueOf(index++), normalFont, Element.ALIGN_CENTER));
            table.addCell(pdfBodyCell(safeText(item.get("title")), normalFont, Element.ALIGN_LEFT));
            table.addCell(pdfBodyCell(safeText(item.get("views")), normalFont, Element.ALIGN_CENTER));
        }

        return table;
    }

    private void addPdfMetricCell(PdfPTable table,
                                  String label,
                                  String value,
                                  com.itextpdf.text.Font normalFont,
                                  com.itextpdf.text.Font headerFont) {
        PdfPCell labelCell = pdfBodyCell(label, normalFont, Element.ALIGN_LEFT);
        labelCell.setBackgroundColor(new BaseColor(248, 250, 252));
        PdfPCell valueCell = pdfBodyCell(value, headerFont, Element.ALIGN_CENTER);
        valueCell.setBackgroundColor(new BaseColor(239, 246, 255));
        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private PdfPCell pdfHeaderCell(String text, com.itextpdf.text.Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(new BaseColor(37, 99, 235));
        cell.setBorderColor(new BaseColor(191, 219, 254));
        cell.setPadding(8f);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        return cell;
    }

    private PdfPCell pdfBodyCell(String text, com.itextpdf.text.Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
        cell.setPadding(8f);
        cell.setBorderColor(new BaseColor(226, 232, 240));
        cell.setHorizontalAlignment(alignment);
        return cell;
    }

    private String safeStat(Map<String, Object> stats, String key) {
        if (stats == null || stats.get(key) == null) {
            return "0";
        }
        return stats.get(key).toString();
    }

    private String safeText(Object value) {
        return value == null ? "" : value.toString();
    }

    /**
     * Áp dụng header style cho row
     */
    private void applyHeaderStyle(Row row, CellStyle style) {
        for (int i = 0; i < row.getLastCellNum(); i++) {
            if (row.getCell(i) != null) {
                row.getCell(i).setCellStyle(style);
            }
        }
    }
}

