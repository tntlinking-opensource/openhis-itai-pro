package com.openhis.whale.mrms.application.pdf;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.openhis.whale.mrms.application.dto.DiagnosisLineDto;
import com.openhis.whale.mrms.application.dto.HomepageDto;
import com.openhis.whale.mrms.application.dto.ProcedureLineDto;
import com.openhis.whale.mrms.domain.entity.CaseRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * 病案首页 Wave 1 简化国标表格式 OpenPDF 渲染。
 */
@Service
@RequiredArgsConstructor
public class HomepageOpenPdfRenderer {

    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final MrmsPdfFontFactory fontFactory;

    public record RenderResult(byte[] pdfBytes, int pageCount) {
    }

    public RenderResult render(CaseRecord caseRecord, HomepageDto homepage) {
        try {
            Document document = new Document(PageSize.A4, 36, 36, 42, 36);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            document.open();

            Font titleFont = fontFactory.bodyFont(16f, Font.BOLD);
            Font sectionFont = fontFactory.bodyFont(11f, Font.BOLD);
            Font bodyFont = fontFactory.bodyFont(9f, Font.NORMAL);
            Font headerFont = fontFactory.bodyFont(9f, Font.BOLD);

            Paragraph title = new Paragraph("住院病案首页", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(12f);
            document.add(title);

            document.add(sectionTitle("一、基本信息", sectionFont));
            document.add(kvTable(bodyFont, new String[][]{
                    {"病案号", text(caseRecord.getMedcasno())},
                    {"住院号", text(firstNonBlank(homepage.getIptNo(), caseRecord.getInpatientNo()))},
                    {"姓名", text(firstNonBlank(homepage.getPsnName(), caseRecord.getPatientName()))},
                    {"性别", text(firstNonBlank(homepage.getGendCode(), caseRecord.getGenderCode()))},
                    {"出生日期", formatDate(homepage.getBrdy())},
                    {"证件号", text(homepage.getCertno())},
                    {"国籍", text(homepage.getNtlyCode())},
                    {"民族", text(homepage.getNatyCode())},
                    {"职业", text(homepage.getPrfsCode())},
                    {"婚姻", text(homepage.getMrgStasCode())},
                    {"现住址", text(homepage.getCurrAddr())},
                    {"电话", text(homepage.getPsnTel())},
                    {"联系人", text(homepage.getConerName())},
                    {"联系人电话", text(homepage.getConerTel())},
                    {"联系人地址", text(homepage.getConerAddr())},
            }));

            document.add(sectionTitle("二、住院信息", sectionFont));
            document.add(kvTable(bodyFont, new String[][]{
                    {"入院途径", text(homepage.getAdmWayCode())},
                    {"入院科别", text(firstNonBlank(homepage.getAdmCatyName(), homepage.getAdmCatyCode()))},
                    {"出院科别", text(firstNonBlank(homepage.getDscgCatyName(), homepage.getDscgCatyCode()))},
                    {"入院时间", formatDateTime(homepage.getAdmTime(), caseRecord.getAdmissionAt())},
                    {"出院时间", formatDateTime(homepage.getDscgTime(), caseRecord.getDischargeAt())},
                    {"实际住院天数", homepage.getActIptDays() != null ? String.valueOf(homepage.getActIptDays()) : "—"},
                    {"离院方式", text(homepage.getDscgWayCode())},
                    {"转科摘要", text(homepage.getTransferDeptSummary())},
            }));

            document.add(sectionTitle("三、医务人员", sectionFont));
            document.add(kvTable(bodyFont, new String[][]{
                    {"科主任", text(homepage.getDeptDirectorName())},
                    {"主任(副主任)医师", text(homepage.getChfdrName())},
                    {"主治医师", text(firstNonBlank(homepage.getAtddrName(), caseRecord.getAttendingPractitionerName()))},
                    {"住院医师", text(homepage.getIptDrName())},
                    {"责任护士", text(homepage.getRespNursName())},
            }));

            document.add(sectionTitle("四、出院诊断", sectionFont));
            document.add(diagnosisTable(homepage.getDiagnoses(), headerFont, bodyFont));

            document.add(sectionTitle("五、手术及操作", sectionFont));
            document.add(procedureTable(homepage.getProcedures(), headerFont, bodyFont));

            document.close();
            return new RenderResult(baos.toByteArray(), writer.getPageNumber());
        } catch (Exception e) {
            throw new IllegalStateException("病案首页 PDF 渲染失败", e);
        }
    }

    private static Paragraph sectionTitle(String text, Font font) {
        Paragraph paragraph = new Paragraph(text, font);
        paragraph.setSpacingBefore(8f);
        paragraph.setSpacingAfter(4f);
        return paragraph;
    }

    private static PdfPTable kvTable(Font bodyFont, String[][] rows) throws Exception {
        PdfPTable table = new PdfPTable(new float[]{2.2f, 3.8f, 2.2f, 3.8f});
        table.setWidthPercentage(100f);
        table.setSpacingAfter(6f);
        for (int i = 0; i < rows.length; i += 2) {
            table.addCell(labelCell(rows[i][0], bodyFont));
            table.addCell(valueCell(rows[i][1], bodyFont));
            if (i + 1 < rows.length) {
                table.addCell(labelCell(rows[i + 1][0], bodyFont));
                table.addCell(valueCell(rows[i + 1][1], bodyFont));
            } else {
                table.addCell(valueCell("", bodyFont));
                table.addCell(valueCell("", bodyFont));
            }
        }
        return table;
    }

    private static PdfPTable diagnosisTable(List<DiagnosisLineDto> lines, Font headerFont, Font bodyFont)
            throws Exception {
        PdfPTable table = new PdfPTable(new float[]{1.2f, 0.8f, 1.6f, 4.4f, 1.2f});
        table.setWidthPercentage(100f);
        table.setSpacingAfter(6f);
        addHeaderRow(table, headerFont, "类型", "序号", "编码", "名称", "入院病情");
        if (lines == null || lines.isEmpty()) {
            addEmptyRow(table, bodyFont, 5);
            return table;
        }
        lines.stream()
                .sorted(Comparator.comparing(DiagnosisLineDto::getSortNo, Comparator.nullsLast(Integer::compareTo)))
                .forEach(line -> {
                    table.addCell(dataCell(text(line.getDiagTypeCode()), bodyFont));
                    table.addCell(dataCell(line.getSortNo() != null ? String.valueOf(line.getSortNo()) : "—", bodyFont));
                    table.addCell(dataCell(text(line.getDiagCode()), bodyFont));
                    table.addCell(dataCell(text(line.getDiagName()), bodyFont));
                    table.addCell(dataCell(text(line.getAdmCondCode()), bodyFont));
                });
        return table;
    }

    private static PdfPTable procedureTable(List<ProcedureLineDto> lines, Font headerFont, Font bodyFont)
            throws Exception {
        PdfPTable table = new PdfPTable(new float[]{0.8f, 1.6f, 4.0f, 1.2f, 1.0f, 1.0f});
        table.setWidthPercentage(100f);
        table.setSpacingAfter(6f);
        addHeaderRow(table, headerFont, "序号", "编码", "名称", "日期", "级别", "切口");
        if (lines == null || lines.isEmpty()) {
            addEmptyRow(table, bodyFont, 6);
            return table;
        }
        lines.stream()
                .sorted(Comparator.comparing(ProcedureLineDto::getSortNo, Comparator.nullsLast(Integer::compareTo)))
                .forEach(line -> {
                    table.addCell(dataCell(line.getSortNo() != null ? String.valueOf(line.getSortNo()) : "—", bodyFont));
                    table.addCell(dataCell(text(line.getOprnOprtCode()), bodyFont));
                    table.addCell(dataCell(text(line.getOprnOprtName()), bodyFont));
                    table.addCell(dataCell(formatDate(line.getOprnDate()), bodyFont));
                    table.addCell(dataCell(text(line.getOprnLevelCode()), bodyFont));
                    table.addCell(dataCell(text(line.getOprnIncisionCode()), bodyFont));
                });
        return table;
    }

    private static void addHeaderRow(PdfPTable table, Font font, String... headers) {
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, font));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPadding(4f);
            cell.setBackgroundColor(new java.awt.Color(240, 240, 240));
            table.addCell(cell);
        }
    }

    private static void addEmptyRow(PdfPTable table, Font font, int colspan) {
        PdfPCell cell = new PdfPCell(new Phrase("（无）", font));
        cell.setColspan(colspan);
        cell.setPadding(6f);
        table.addCell(cell);
    }

    private static PdfPCell labelCell(String label, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(label, font));
        cell.setPadding(4f);
        cell.setBackgroundColor(new java.awt.Color(248, 248, 248));
        return cell;
    }

    private static PdfPCell valueCell(String value, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(value, font));
        cell.setPadding(4f);
        return cell;
    }

    private static PdfPCell dataCell(String value, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(value, font));
        cell.setPadding(3f);
        return cell;
    }

    private static String text(String value) {
        return isNotBlank(value) ? value.trim() : "—";
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (isNotBlank(value)) {
                return value.trim();
            }
        }
        return "—";
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String formatDate(LocalDate date) {
        return date != null ? date.format(DATE_FMT) : "—";
    }

    private static String formatDateTime(LocalDateTime primary, LocalDateTime fallback) {
        LocalDateTime value = primary != null ? primary : fallback;
        return value != null ? value.format(DATE_TIME_FMT) : "—";
    }
}
