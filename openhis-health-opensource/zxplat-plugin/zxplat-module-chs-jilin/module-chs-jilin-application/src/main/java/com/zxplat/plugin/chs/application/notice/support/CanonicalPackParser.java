/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.notice.support;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.zxplat.plugin.chs.domain.notice.command.PersistNoticeLineCommand;
import com.zxplat.plugin.chs.domain.notice.support.CatalogNoticeCanonicalSemantics;
import com.zxplat.plugin.chs.domain.share.constants.ChsErrorCodes;
import com.zxplat.framework.core.application.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 标准落地包解析器：目录内 {@code notice.meta.json}+{@code notice.lines.csv}，或等价 zip。
 * <p>目录扫描走 {@link #parseDirectory}；组装确定性 STORED zip 以保证同目录 hash 稳定。</p>
 */
@Component
public class CanonicalPackParser {

    private static final String META_NAME = "notice.meta.json";
    private static final String LINES_NAME = "notice.lines.csv";
    private static final byte[] ZIP_MAGIC = new byte[] {0x50, 0x4b};
    /** 固定时间戳，避免 ZipEntry 默认写当前时刻导致 hash 漂移。 */
    private static final long DETERMINISTIC_ENTRY_TIME_MS = 0L;

    /**
     * 解析标准落地包子目录（含 meta + csv），并组装确定性 zip 作为 packBytes。
     *
     * @param dir 子目录（目录名通常为原文件 stem）
     * @return 解析结果
     */
    public CanonicalPackParseResult parseDirectory(Path dir) {
        return parseDirectory(dir, true);
    }

    /**
     * 解析标准落地包子目录。
     *
     * @param dir              子目录（目录名通常为原文件 stem）
     * @param includePackBytes {@code true} 时组装确定性 zip；扫描预览可传 {@code false} 以省时省内存
     * @return 解析结果
     */
    public CanonicalPackParseResult parseDirectory(Path dir, boolean includePackBytes) {
        if (dir == null || !Files.isDirectory(dir)) {
            throw new BusinessException(
                ChsErrorCodes.NOTICE_FILE_UNSUPPORTED,
                "标准落地包子目录不存在：" + dir);
        }
        Path metaPath = dir.resolve(META_NAME);
        Path linesPath = dir.resolve(LINES_NAME);
        if (!Files.isRegularFile(metaPath) || !Files.isRegularFile(linesPath)) {
            throw new BusinessException(
                ChsErrorCodes.NOTICE_FILE_UNSUPPORTED,
                "标准落地包缺少 notice.meta.json 或 notice.lines.csv：" + dir.getFileName());
        }
        byte[] metaBytes;
        byte[] linesBytes;
        try {
            metaBytes = Files.readAllBytes(metaPath);
            linesBytes = Files.readAllBytes(linesPath);
        } catch (IOException ex) {
            throw new BusinessException(
                ChsErrorCodes.NOTICE_PARSE_FAILED,
                "读取标准落地包失败：" + ex.getMessage(),
                ex);
        }

        CanonicalPackParseResult result = new CanonicalPackParseResult();
        parseMeta(metaBytes, result);
        parseLines(linesBytes, result);
        // 内容 hash：meta + NUL + csv，与是否打 zip 无关，扫描/入库去重口径一致
        result.setContentHash(sha256OfParts(metaBytes, linesBytes));
        if (includePackBytes) {
            // 确定性 zip：固定 entry 名/顺序/时间戳/STORED
            result.setPackBytes(buildDeterministicZip(metaBytes, linesBytes));
        }
        String stem = dir.getFileName().toString();
        result.setPackFileName(resolvePackFileName(stem));
        if (result.getSourceFilename() == null || result.getSourceFilename().isBlank()) {
            result.setSourceFilename(stem);
        }
        return result;
    }

    /**
     * 解析标准落地包 zip 字节（回下/兼容历史包）。
     *
     * @param fileName 原始文件名（用于扩展名判断）
     * @param bytes    文件内容
     * @return 解析结果
     */
    public CanonicalPackParseResult parse(String fileName, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException(
                ChsErrorCodes.NOTICE_FILE_UNSUPPORTED,
                "请院外转为标准落地包（zip）后再导入");
        }
        String lowerName = "";
        if (fileName != null) {
            lowerName = fileName.toLowerCase(Locale.ROOT);
        }
        // xlsx 一期不做辅解析，直接拒收并提示院外洗包
        if (lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls") || lowerName.endsWith(".et")) {
            throw new BusinessException(
                ChsErrorCodes.NOTICE_FILE_UNSUPPORTED,
                "当前仅支持标准落地包 zip；xlsx/et 请院外洗成 notice.meta.json + notice.lines.csv 后再导入");
        }
        if (!looksLikeZip(bytes) && !lowerName.endsWith(".zip")) {
            throw new BusinessException(
                ChsErrorCodes.NOTICE_FILE_UNSUPPORTED,
                "请院外转为标准落地包（zip，含 notice.meta.json / notice.lines.csv）后再导入");
        }

        Map<String, byte[]> entries = readZipEntries(bytes);
        byte[] metaBytes = findEntry(entries, META_NAME);
        byte[] linesBytes = findEntry(entries, LINES_NAME);
        if (metaBytes == null || linesBytes == null) {
            throw new BusinessException(
                ChsErrorCodes.NOTICE_FILE_UNSUPPORTED,
                "标准包缺少 notice.meta.json 或 notice.lines.csv，请院外洗包后重试");
        }

        CanonicalPackParseResult result = new CanonicalPackParseResult();
        // 规范化为确定性 zip，避免历史上传 zip 时间戳导致 hash 与目录扫描不一致
        result.setPackBytes(buildDeterministicZip(metaBytes, linesBytes));
        result.setPackFileName(resolvePackFileName(fileName));
        parseMeta(metaBytes, result);
        parseLines(linesBytes, result);
        return result;
    }

    /**
     * 组装确定性 STORED zip（entry 顺序：meta → lines）。
     *
     * @param metaBytes  meta 文件字节
     * @param linesBytes csv 文件字节
     * @return zip 字节
     */
    byte[] buildDeterministicZip(byte[] metaBytes, byte[] linesBytes) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            putStoredEntry(zos, META_NAME, metaBytes);
            putStoredEntry(zos, LINES_NAME, linesBytes);
            zos.finish();
        } catch (IOException ex) {
            throw new BusinessException(
                ChsErrorCodes.NOTICE_PARSE_FAILED,
                "组装确定性标准包失败：" + ex.getMessage(),
                ex);
        }
        return baos.toByteArray();
    }

    private void putStoredEntry(ZipOutputStream zos, String entryName, byte[] data) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        entry.setMethod(ZipEntry.STORED);
        entry.setTime(DETERMINISTIC_ENTRY_TIME_MS);
        entry.setSize(data.length);
        entry.setCompressedSize(data.length);
        CRC32 crc = new CRC32();
        crc.update(data);
        entry.setCrc(crc.getValue());
        zos.putNextEntry(entry);
        zos.write(data);
        zos.closeEntry();
    }

    private boolean looksLikeZip(byte[] bytes) {
        if (bytes.length < 2) {
            return false;
        }
        return bytes[0] == ZIP_MAGIC[0] && bytes[1] == ZIP_MAGIC[1];
    }

    private String resolvePackFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "canonical-pack.zip";
        }
        if (fileName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            return fileName;
        }
        return fileName + ".zip";
    }

    private Map<String, byte[]> readZipEntries(byte[] bytes) {
        Map<String, byte[]> map = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                int slash = name.lastIndexOf('/');
                if (slash >= 0) {
                    name = name.substring(slash + 1);
                }
                int bslash = name.lastIndexOf('\\');
                if (bslash >= 0) {
                    name = name.substring(bslash + 1);
                }
                map.put(name.toLowerCase(Locale.ROOT), zis.readAllBytes());
            }
        } catch (IOException ex) {
            throw new BusinessException(
                ChsErrorCodes.NOTICE_FILE_UNSUPPORTED,
                "zip 损坏或无法读取，请院外重新打包后再导入",
                ex);
        }
        return map;
    }

    private byte[] findEntry(Map<String, byte[]> entries, String expected) {
        return entries.get(expected.toLowerCase(Locale.ROOT));
    }

    void parseMeta(byte[] metaBytes, CanonicalPackParseResult result) {
        try {
            String text = new String(metaBytes, StandardCharsets.UTF_8).trim();
            // 去掉可能的 UTF-8 BOM
            if (text.startsWith("\uFEFF")) {
                text = text.substring(1);
            }
            JSONObject meta = JSON.parseObject(text);
            if (meta == null) {
                throw new BusinessException(ChsErrorCodes.NOTICE_PARSE_FAILED, "notice.meta.json 为空或非法");
            }
            String docKind = meta.getString("doc_kind");
            String category = meta.getString("category");
            if (docKind == null || docKind.isBlank() || category == null || category.isBlank()) {
                throw new BusinessException(
                    ChsErrorCodes.NOTICE_PARSE_FAILED,
                    "notice.meta.json 缺少必填字段 doc_kind/category");
            }
            result.setDocKind(docKind.trim());
            result.setCategory(category.trim());
            result.setTitle(meta.getString("title"));
            result.setSourceFilename(meta.getString("source_filename"));
            result.setNoticeDate(parseDate(meta.getString("notice_date"), result.getWarnings(), "meta.notice_date"));
            result.setEffectiveDate(
                parseDate(meta.getString("effective_date"), result.getWarnings(), "meta.effective_date"));
            if (!CatalogNoticeCanonicalSemantics.hasBusinessDate(result.getNoticeDate(), result.getEffectiveDate())) {
                addWarning(result.getWarnings(), "缺业务日：notice_date 与 effective_date 均为空，禁止应用（可入库）");
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(
                ChsErrorCodes.NOTICE_PARSE_FAILED,
                "notice.meta.json 解析失败：" + ex.getMessage(),
                ex);
        }
    }

    void parseLines(byte[] linesBytes, CanonicalPackParseResult result) {
        String csvText = new String(linesBytes, StandardCharsets.UTF_8);
        if (csvText.startsWith("\uFEFF")) {
            csvText = csvText.substring(1);
        }
        List<String> warnings = result.getWarnings();
        List<PersistNoticeLineCommand> lines = new ArrayList<>();
        int addCount = 0;
        int stopCount = 0;
        int modifyCount = 0;
        int setPayClassCount = 0;
        Map<String, Integer> sheetCounts = new LinkedHashMap<>();

        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(new ByteArrayInputStream(csvText.getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8))) {
            String headerLine = readCsvRecord(reader);
            if (headerLine == null || headerLine.isBlank()) {
                throw new BusinessException(ChsErrorCodes.NOTICE_PARSE_FAILED, "notice.lines.csv 无表头");
            }
            String[] headers = splitCsvLine(headerLine);
            Map<String, Integer> colIndex = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                colIndex.put(headers[i].trim().toLowerCase(Locale.ROOT), i);
            }
            if (!colIndex.containsKey("action")) {
                throw new BusinessException(ChsErrorCodes.NOTICE_PARSE_FAILED, "notice.lines.csv 缺少 action 列");
            }

            String recordText;
            // 表头占用的物理行数（通常 1）
            int physicalRow = countNewlines(headerLine) + 1;
            while ((recordText = readCsvRecord(reader)) != null) {
                int recordStartRow = physicalRow + 1;
                physicalRow = recordStartRow + countNewlines(recordText);
                if (recordText.isBlank()) {
                    continue;
                }
                String[] cols = splitCsvLine(recordText);
                PersistNoticeLineCommand line = mapRow(cols, colIndex, recordStartRow, warnings);
                if (line.getAction() == null || line.getAction().isBlank()) {
                    addWarning(warnings, "第 " + recordStartRow + " 行缺少 action，已跳过");
                    continue;
                }
                if (CatalogNoticeCanonicalSemantics.shouldSkipLine(line)) {
                    addWarning(warnings, "第 " + recordStartRow + " 行脏码或无主键，已跳过");
                    continue;
                }
                String action = line.getAction().trim().toUpperCase(Locale.ROOT);
                line.setAction(action);
                // 统计动作计数供预览展示
                if ("ADD".equals(action)) {
                    addCount++;
                } else if ("STOP".equals(action)) {
                    stopCount++;
                } else if ("MODIFY".equals(action) || "PROVINCE_MODIFY".equals(action)) {
                    modifyCount++;
                } else if ("SET_PAY_CLASS".equals(action)) {
                    setPayClassCount++;
                }
                String sheet = line.getSheetName();
                if (sheet == null || sheet.isBlank()) {
                    sheet = "(blank)";
                }
                Integer prev = sheetCounts.get(sheet);
                if (prev == null) {
                    sheetCounts.put(sheet, 1);
                } else {
                    sheetCounts.put(sheet, prev + 1);
                }
                lines.add(line);
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new BusinessException(
                ChsErrorCodes.NOTICE_PARSE_FAILED,
                "notice.lines.csv 读取失败：" + ex.getMessage(),
                ex);
        }

        if (lines.isEmpty()) {
            throw new BusinessException(ChsErrorCodes.NOTICE_PARSE_FAILED, "notice.lines.csv 无有效明细行");
        }

        result.setLines(lines);
        result.setAddCount(addCount);
        result.setStopCount(stopCount);
        result.setModifyCount(modifyCount);
        result.setSetPayClassCount(setPayClassCount);
        result.setSheetSummaryJson(JSON.toJSONString(sheetCounts));
        if (warnings.isEmpty()) {
            result.setParseStatus("PARSED");
        } else {
            result.setParseStatus("PARTIAL");
        }
    }

    /**
     * 将 CSV 逻辑行映射为入库命令，并做名称提升与物价码挪列。
     *
     * @param cols         拆分后的列
     * @param colIndex     表头下标
     * @param physicalRow  原文件物理行号
     * @param warnings     解析警告
     * @return 明细命令
     */
    private PersistNoticeLineCommand mapRow(
            String[] cols,
            Map<String, Integer> colIndex,
            int physicalRow,
            List<String> warnings) {
        PersistNoticeLineCommand line = new PersistNoticeLineCommand();
        line.setAction(cell(cols, colIndex, "action"));
        line.setHilistCode(emptyToNull(cell(cols, colIndex, "hilist_code")));
        line.setProjectCode(emptyToNull(cell(cols, colIndex, "project_code")));
        line.setHilistName(emptyToNull(cell(cols, colIndex, "hilist_name")));
        line.setBeginDate(parseDate(cell(cols, colIndex, "begin_date"), warnings, "row " + physicalRow + " begin_date"));
        line.setEndDate(parseDate(cell(cols, colIndex, "end_date"), warnings, "row " + physicalRow + " end_date"));
        line.setChangeFields(emptyToNull(cell(cols, colIndex, "change_fields")));
        line.setChrgitmLv(emptyToNull(cell(cols, colIndex, "chrgitm_lv")));
        line.setListType(emptyToNull(cell(cols, colIndex, "list_type")));
        line.setSheetName(emptyToNull(cell(cols, colIndex, "source_sheet")));
        line.setRowNo(parseInt(cell(cols, colIndex, "source_row"), physicalRow));
        String extJson = emptyToNull(cell(cols, colIndex, "ext_json"));
        fillExtFields(line, extJson);
        CatalogNoticeCanonicalSemantics.sanitizeLine(line);

        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("action", line.getAction());
        payload.put("hilist_code", line.getHilistCode());
        payload.put("project_code", line.getProjectCode());
        payload.put("hilist_name", line.getHilistName());
        payload.put("ext_json", extJson);
        line.setPayloadJson(JSON.toJSONString(payload));
        return line;
    }

    /**
     * 把 ext 中限价/限制/名称提升到行字段（键名固定，不扩 CSV 列）。
     *
     * @param line    明细命令
     * @param extJson 扩展 JSON
     */
    private void fillExtFields(PersistNoticeLineCommand line, String extJson) {
        if (extJson == null) {
            return;
        }
        try {
            JSONObject ext = JSON.parseObject(extJson);
            if (ext == null) {
                return;
            }
            String price = ext.getString("医保目录定价上限金额");
            if (price != null && !price.isBlank()) {
                line.setPriceLimitAmt(new BigDecimal(price.trim()));
            }
            String overlmt = ext.getString("医保目录超限处理方式");
            if (overlmt != null && !overlmt.isBlank()) {
                line.setOverlmtDspoWay(overlmt.trim());
            }
            String lmtFlag = ext.getString("限制使用标志");
            if (lmtFlag != null && !lmtFlag.isBlank()) {
                line.setLmtUsedFlag(lmtFlag.trim());
            }
            String lmtScope = ext.getString("限制使用范围");
            if (lmtScope != null && !lmtScope.isBlank()) {
                line.setLmtScope(lmtScope.trim());
            }
            String extName = ext.getString("医疗服务项目名称");
            if ((line.getHilistName() == null || line.getHilistName().isBlank())
                && extName != null && !extName.isBlank()) {
                // 名称必须落到 hilist_name；ext 仅作来源
                line.setHilistName(extName.trim());
            }
        } catch (RuntimeException ignored) {
            // ext_json 非法不影响主列入库，原串仍进 payload
        }
    }

    /**
     * 解析警告封顶，避免大包把会话打满。
     *
     * @param warnings 警告列表
     * @param message  新增一句
     */
    private void addWarning(List<String> warnings, String message) {
        if (warnings == null || message == null || message.isBlank()) {
            return;
        }
        if (warnings.size() >= 80) {
            return;
        }
        warnings.add(message);
    }

    private String cell(String[] cols, Map<String, Integer> colIndex, String name) {
        Integer idx = colIndex.get(name.toLowerCase(Locale.ROOT));
        if (idx == null || idx < 0 || idx >= cols.length) {
            return null;
        }
        return cols[idx];
    }

    private String emptyToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed;
    }

    private LocalDate parseDate(String raw, List<String> warnings, String fieldLabel) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException ex) {
            warnings.add(fieldLabel + " 日期非法：" + raw);
            return null;
        }
    }

    private Integer parseInt(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    /**
     * 对 meta + NUL + csv 做 SHA-256，供扫描去重；与是否打 zip 无关。
     *
     * @param metaBytes  meta 字节
     * @param linesBytes csv 字节
     * @return hex 摘要
     */
    private String sha256OfParts(byte[] metaBytes, byte[] linesBytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            if (metaBytes != null) {
                digest.update(metaBytes);
            }
            digest.update((byte) 0);
            if (linesBytes != null) {
                digest.update(linesBytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new BusinessException(ChsErrorCodes.NOTICE_PARSE_FAILED, "无法计算 SHA-256", ex);
        }
    }

    /**
     * 读取一条逻辑 CSV 记录（RFC4180：引号字段内允许换行）。
     * <p>旧实现按 {@code readLine} 拆行，名称里含换行时会裂成脏行并多计入库。</p>
     */
    private String readCsvRecord(BufferedReader reader) throws IOException {
        StringBuilder record = new StringBuilder();
        String line = reader.readLine();
        if (line == null) {
            return null;
        }
        record.append(line);
        while (csvQuotesUnbalanced(record)) {
            String cont = reader.readLine();
            if (cont == null) {
                break;
            }
            record.append('\n').append(cont);
        }
        return record.toString();
    }

    /** 未闭合引号（已处理 {@code ""} 转义）时返回 true。 */
    private boolean csvQuotesUnbalanced(CharSequence text) {
        boolean inQuotes = false;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    i += 2;
                    continue;
                }
                inQuotes = !inQuotes;
            }
            i++;
        }
        return inQuotes;
    }

    private int countNewlines(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                n++;
            }
        }
        return n;
    }

    /**
     * 简易 CSV 拆分（支持双引号转义；调用方须保证传入完整逻辑行）。
     */
    private String[] splitCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    // 转义双引号 ""
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i += 2;
                        continue;
                    }
                    inQuotes = false;
                    i++;
                    continue;
                }
                current.append(c);
                i++;
                continue;
            }
            if (c == '"') {
                inQuotes = true;
                i++;
                continue;
            }
            if (c == ',') {
                fields.add(current.toString());
                current.setLength(0);
                i++;
                continue;
            }
            current.append(c);
            i++;
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }

    /**
     * 解析结果载体。
     */
    public static class CanonicalPackParseResult {
        private byte[] packBytes;
        /** meta+csv 内容 SHA-256（hex）。 */
        private String contentHash;
        private String packFileName;
        private String docKind;
        private String category;
        private String title;
        private String sourceFilename;
        private LocalDate noticeDate;
        private LocalDate effectiveDate;
        private String parseStatus;
        private String sheetSummaryJson;
        private final List<String> warnings = new ArrayList<>();
        private int addCount;
        private int stopCount;
        private int modifyCount;
        private int setPayClassCount;
        private List<PersistNoticeLineCommand> lines = new ArrayList<>();

        public byte[] getPackBytes() {
            return packBytes;
        }

        public void setPackBytes(byte[] packBytes) {
            this.packBytes = packBytes;
        }

        public String getContentHash() {
            return contentHash;
        }

        public void setContentHash(String contentHash) {
            this.contentHash = contentHash;
        }

        public String getPackFileName() {
            return packFileName;
        }

        public void setPackFileName(String packFileName) {
            this.packFileName = packFileName;
        }

        public String getDocKind() {
            return docKind;
        }

        public void setDocKind(String docKind) {
            this.docKind = docKind;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getSourceFilename() {
            return sourceFilename;
        }

        public void setSourceFilename(String sourceFilename) {
            this.sourceFilename = sourceFilename;
        }

        public LocalDate getNoticeDate() {
            return noticeDate;
        }

        public void setNoticeDate(LocalDate noticeDate) {
            this.noticeDate = noticeDate;
        }

        public LocalDate getEffectiveDate() {
            return effectiveDate;
        }

        public void setEffectiveDate(LocalDate effectiveDate) {
            this.effectiveDate = effectiveDate;
        }

        public String getParseStatus() {
            return parseStatus;
        }

        public void setParseStatus(String parseStatus) {
            this.parseStatus = parseStatus;
        }

        public String getSheetSummaryJson() {
            return sheetSummaryJson;
        }

        public void setSheetSummaryJson(String sheetSummaryJson) {
            this.sheetSummaryJson = sheetSummaryJson;
        }

        public List<String> getWarnings() {
            return warnings;
        }

        public int getAddCount() {
            return addCount;
        }

        public void setAddCount(int addCount) {
            this.addCount = addCount;
        }

        public int getStopCount() {
            return stopCount;
        }

        public void setStopCount(int stopCount) {
            this.stopCount = stopCount;
        }

        public int getModifyCount() {
            return modifyCount;
        }

        public void setModifyCount(int modifyCount) {
            this.modifyCount = modifyCount;
        }

        public int getSetPayClassCount() {
            return setPayClassCount;
        }

        public void setSetPayClassCount(int setPayClassCount) {
            this.setPayClassCount = setPayClassCount;
        }

        public List<PersistNoticeLineCommand> getLines() {
            return lines;
        }

        public void setLines(List<PersistNoticeLineCommand> lines) {
            this.lines = lines;
        }
    }
}
