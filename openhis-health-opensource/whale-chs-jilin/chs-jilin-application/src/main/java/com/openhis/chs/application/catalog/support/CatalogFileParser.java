/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.catalog.support;

import com.alibaba.fastjson2.JSON;
import com.openhis.chs.domain.catalog.command.UpsertNationalCatalogRowCommand;
import com.openhis.chs.domain.catalog.ChsCatalogConstants;
import com.openhis.chs.domain.ChsErrorCodes;
import com.openhis.chs.domain.support.ChsTextSupport;
import com.openhis.whale.core.application.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 13xx 目录 ZIP/TXT（TAB 分隔）通用解析器。
 */
public final class CatalogFileParser {

    private CatalogFileParser() {
    }

    public static byte[] unzipToTextBytes(byte[] zipBytes) {
        if (zipBytes == null || zipBytes.length == 0) {
            throw new BusinessException(ChsErrorCodes.FILE_PARSE_FAILED, "9102 文件为空");
        }
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                return zipInputStream.readAllBytes();
            }
            throw new BusinessException(ChsErrorCodes.FILE_PARSE_FAILED, "9102 ZIP 内无 TXT 文件");
        } catch (IOException ex) {
            throw new BusinessException(ChsErrorCodes.FILE_PARSE_FAILED, "9102 ZIP 解压失败: " + ex.getMessage());
        }
    }

    public static String peekNextVersionFromZip(String infno, byte[] zipBytes) {
        return peekNextVersionFromTextBytes(infno, unzipToTextBytes(zipBytes));
    }

    public static String peekNextVersionFromTextBytes(String infno, byte[] textBytes) {
        CatalogFileParserSpec spec = CatalogFileParserRegistry.require(infno);
        int col = versionChainColumn(spec);
        if (textBytes == null || textBytes.length == 0 || col < 0) {
            return null;
        }
        try {
            return peekLastColumn(new ByteArrayInputStream(textBytes), col);
        } catch (IOException ex) {
            throw new BusinessException(ChsErrorCodes.FILE_PARSE_FAILED, "TXT 读取失败: " + ex.getMessage());
        }
    }

    public static String peekMaxVersionNoFromTextBytes(String infno, byte[] textBytes) {
        CatalogFileParserSpec spec = CatalogFileParserRegistry.require(infno);
        if (spec.idxVersionNo() < 0 || textBytes == null || textBytes.length == 0) {
            return null;
        }
        try {
            return peekMaxColumn(new ByteArrayInputStream(textBytes), spec.idxVersionNo());
        } catch (IOException ex) {
            throw new BusinessException(ChsErrorCodes.FILE_PARSE_FAILED, "TXT 读取失败: " + ex.getMessage());
        }
    }

    public static String peekLastVersionNoFromTextBytes(String infno, byte[] textBytes) {
        CatalogFileParserSpec spec = CatalogFileParserRegistry.require(infno);
        if (spec.idxVersionNo() < 0 || textBytes == null || textBytes.length == 0) {
            return null;
        }
        try {
            return peekLastColumn(new ByteArrayInputStream(textBytes), spec.idxVersionNo());
        } catch (IOException ex) {
            throw new BusinessException(ChsErrorCodes.FILE_PARSE_FAILED, "TXT 读取失败: " + ex.getMessage());
        }
    }

    public static int countDataLines(byte[] textBytes) {
        if (textBytes == null || textBytes.length == 0) {
            return 0;
        }
        int count = 0;
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(new ByteArrayInputStream(textBytes), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    count++;
                }
            }
        } catch (IOException ex) {
            throw new BusinessException(ChsErrorCodes.FILE_PARSE_FAILED, "TXT 行数统计失败: " + ex.getMessage());
        }
        return count;
    }

    /**
     * 单次扫描 TXT 包元数据（行数、版本链），供阶段一使用，避免多次全量读盘。
     *
     * @param infno 13xx 接口号
     */
    public static TextPackageMetadata scanTextPackageMetadata(String infno, byte[] textBytes) {
        if (textBytes == null || textBytes.length == 0) {
            return TextPackageMetadata.empty();
        }
        CatalogFileParserSpec spec = CatalogFileParserRegistry.require(infno);
        try {
            return scanTextPackageMetadata(spec, new ByteArrayInputStream(textBytes));
        } catch (IOException ex) {
            throw new BusinessException(ChsErrorCodes.FILE_PARSE_FAILED, "TXT 扫描失败: " + ex.getMessage());
        }
    }

    /**
     * 阶段一专用：仅知 infno 时从 ZIP 解压并单次扫描元数据。
     */
    public static TextPackageMetadata scanZipPackageMetadata(String infno, byte[] zipBytes) {
        return scanTextPackageMetadata(infno, unzipToTextBytes(zipBytes));
    }

    public record TextPackageMetadata(
        int dataLineCount,
        String txtVersionName,
        String txtLastVerNo,
        String txtMaxVerNo,
        String nextLocalVer) {

        static TextPackageMetadata empty() {
            return new TextPackageMetadata(0, null, null, null, null);
        }
    }

    /**
     * 本包 TXT 建议的下一轮 {@code data.ver}（对齐 OpenHIS {@code ReadCsv} 返回值，可与当前 ver 相同或为空）。
     *
     * <p>阶段一在平台停链之外，若本包解析出的 ver 与当前请求 ver 相同（或无下一 ver）也会停链，
     * 避免 1307 等平台持续成功时的死循环。</p>
     */
    public static String peekNextLocalVerFromPackage(String infno, byte[] textBytes) {
        CatalogFileParserSpec spec = CatalogFileParserRegistry.require(infno);
        return ChsTextSupport.blankToNull(resolveNextFromText(spec, textBytes));
    }

    /**
     * @deprecated 使用 {@link #peekNextLocalVerFromPackage}；停链已改为平台驱动，不再在此与 currentVer 比较
     */
    @Deprecated
    public static String peekNextDownloadVer(String infno, String currentVer, byte[] textBytes) {
        return peekNextLocalVerFromPackage(infno, textBytes);
    }

    public static ParseResult parseZip(String infno, byte[] zipBytes) {
        return parseTextBytes(infno, unzipToTextBytes(zipBytes));
    }

    public static ParseResult parseTextBytes(String infno, byte[] textBytes) {
        if (textBytes == null || textBytes.length == 0) {
            return new ParseResult(List.of(), null);
        }
        CatalogFileParserSpec spec = CatalogFileParserRegistry.require(infno);
        try {
            return parseText(spec, new ByteArrayInputStream(textBytes));
        } catch (IOException ex) {
            throw new BusinessException(ChsErrorCodes.FILE_PARSE_FAILED, "TXT 解析失败: " + ex.getMessage());
        }
    }

    /**
     * 流式解析 TXT：攒满 {@code batchSize} 行回调一次，避免整包 List 占满内存。
     *
     * @return 本包建议的下一轮 local_ver（与 {@link ParseResult#nextVersion()} 一致）
     */
    public static String parseTextBytesStreaming(
            String infno,
            byte[] textBytes,
            int batchSize,
            CatalogRowBatchHandler handler) {
        if (textBytes == null || textBytes.length == 0) {
            return null;
        }
        if (batchSize <= 0) {
            batchSize = ChsCatalogConstants.CATALOG_UPSERT_BATCH_SIZE;
        }
        CatalogFileParserSpec spec = CatalogFileParserRegistry.require(infno);
        try {
            return parseTextStreaming(spec, new ByteArrayInputStream(textBytes), batchSize, handler);
        } catch (IOException ex) {
            throw new BusinessException(ChsErrorCodes.FILE_PARSE_FAILED, "TXT 解析失败: " + ex.getMessage());
        }
    }

    @FunctionalInterface
    public interface CatalogRowBatchHandler {
        void onBatch(List<UpsertNationalCatalogRowCommand> batch, String runningNextVersion);
    }

    static ParseResult parseText(CatalogFileParserSpec spec, InputStream inputStream) throws IOException {
        List<UpsertNationalCatalogRowCommand> rows = new ArrayList<>();
        String nextVersion = null;
        int lineNo = 0;
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                lineNo++;
                String[] cols = line.split("\t", -1);
                UpsertNationalCatalogRowCommand cmd = toCommand(spec, cols, lineNo);
                if (cmd == null) {
                    continue;
                }
                rows.add(cmd);
                nextVersion = resolveCursorVersionFromCols(spec, cols, nextVersion);
            }
        }
        return new ParseResult(rows, nextVersion);
    }

    static TextPackageMetadata scanTextPackageMetadata(
            CatalogFileParserSpec spec, InputStream inputStream) throws IOException {
        int dataLineCount = 0;
        String lastVersionName = null;
        String lastVersionNo = null;
        String maxVersionNo = null;
        String lastVersionChainValue = null;
        int versionChainCol = versionChainColumn(spec);
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                dataLineCount++;
                String[] cols = line.split("\t", -1);
                String versionName = column(cols, spec.idxVersionName());
                if (StringUtils.isNotBlank(versionName)) {
                    lastVersionName = versionName.trim();
                }
                String versionNo = column(cols, spec.idxVersionNo());
                if (StringUtils.isNotBlank(versionNo)) {
                    String trimmed = versionNo.trim();
                    lastVersionNo = trimmed;
                    if (maxVersionNo == null || trimmed.compareTo(maxVersionNo) > 0) {
                        maxVersionNo = trimmed;
                    }
                }
                String chainValue = column(cols, versionChainCol);
                if (StringUtils.isNotBlank(chainValue)) {
                    lastVersionChainValue = chainValue.trim();
                }
            }
        }
        String nextLocalVer = resolveNextLocalVerFromScan(
            spec, lastVersionName, lastVersionNo, maxVersionNo);
        return new TextPackageMetadata(
            dataLineCount,
            lastVersionChainValue,
            lastVersionNo,
            maxVersionNo,
            nextLocalVer);
    }

    static String parseTextStreaming(
            CatalogFileParserSpec spec,
            InputStream inputStream,
            int batchSize,
            CatalogRowBatchHandler handler) throws IOException {
        List<UpsertNationalCatalogRowCommand> batch = new ArrayList<>(batchSize);
        String nextVersion = null;
        int lineNo = 0;
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                lineNo++;
                String[] cols = line.split("\t", -1);
                UpsertNationalCatalogRowCommand cmd = toCommand(spec, cols, lineNo);
                if (cmd == null) {
                    continue;
                }
                batch.add(cmd);
                nextVersion = resolveCursorVersionFromCols(spec, cols, nextVersion);
                if (batch.size() >= batchSize) {
                    handler.onBatch(batch, nextVersion);
                    batch = new ArrayList<>(batchSize);
                }
            }
        }
        if (!batch.isEmpty()) {
            handler.onBatch(batch, nextVersion);
        }
        return nextVersion;
    }

    private static String resolveNextFromText(CatalogFileParserSpec spec, byte[] textBytes) {
        try {
            return scanTextPackageMetadata(spec, new ByteArrayInputStream(textBytes)).nextLocalVer();
        } catch (IOException ex) {
            throw new BusinessException(ChsErrorCodes.FILE_PARSE_FAILED, "TXT 扫描失败: " + ex.getMessage());
        }
    }

    private static String resolveNextLocalVerFromScan(
            CatalogFileParserSpec spec,
            String lastVersionName,
            String lastVersionNo,
            String maxVersionNo) {
        return switch (spec.chainMode()) {
            case VERSION_NAME_THEN_NUMBER -> {
                if (StringUtils.isNotBlank(lastVersionName)) {
                    yield lastVersionName.trim();
                }
                if (StringUtils.isNotBlank(maxVersionNo)) {
                    yield maxVersionNo.trim();
                }
                yield null;
            }
            case VERSION_NUMBER_ONLY -> {
                if (StringUtils.isNotBlank(lastVersionNo)) {
                    yield lastVersionNo.trim();
                }
                yield null;
            }
            case VERSION_FIELD -> {
                if (StringUtils.isNotBlank(lastVersionName)) {
                    yield lastVersionName.trim();
                }
                yield null;
            }
        };
    }

    private static int versionChainColumn(CatalogFileParserSpec spec) {
        return switch (spec.chainMode()) {
            case VERSION_NAME_THEN_NUMBER -> spec.idxVersionName() >= 0
                ? spec.idxVersionName()
                : spec.idxVersionNo();
            case VERSION_NUMBER_ONLY -> spec.idxVersionNo();
            case VERSION_FIELD -> spec.idxVersionName();
        };
    }

    private static String resolveCursorVersionFromCols(
            CatalogFileParserSpec spec, String[] cols, String current) {
        return switch (spec.chainMode()) {
            case VERSION_NAME_THEN_NUMBER -> {
                String versionName = column(cols, spec.idxVersionName());
                if (StringUtils.isNotBlank(versionName)) {
                    yield versionName.trim();
                }
                String versionNo = column(cols, spec.idxVersionNo());
                if (StringUtils.isNotBlank(versionNo)) {
                    yield versionNo.trim();
                }
                yield current;
            }
            case VERSION_NUMBER_ONLY -> {
                String versionNo = column(cols, spec.idxVersionNo());
                if (StringUtils.isNotBlank(versionNo)) {
                    yield versionNo.trim();
                }
                yield current;
            }
            case VERSION_FIELD -> {
                String version = column(cols, spec.idxVersionName());
                if (StringUtils.isNotBlank(version)) {
                    yield version.trim();
                }
                yield current;
            }
        };
    }

    private static UpsertNationalCatalogRowCommand toCommand(
            CatalogFileParserSpec spec, String[] cols, int lineNo) {
        String hilistCode = column(cols, spec.idxHilistCode());
        if (StringUtils.isBlank(hilistCode)) {
            return null;
        }
        UpsertNationalCatalogRowCommand cmd = new UpsertNationalCatalogRowCommand();
        cmd.setListType(spec.listType());
        cmd.setHilistCode(hilistCode.trim());
        cmd.setHilistName(column(cols, spec.idxHilistName()));
        cmd.setDosform(column(cols, spec.idxDosform()));
        cmd.setSpec(column(cols, spec.idxSpec()));
        cmd.setPinyin(column(cols, spec.idxPinyin()));
        cmd.setAprvno(column(cols, spec.idxAprvno()));
        cmd.setProdentpName(column(cols, spec.idxProdentpName()));
        cmd.setValiFlag(column(cols, spec.idxValiFlag()));
        cmd.setChrgitmLv(column(cols, spec.idxChrgitmLv()));
        cmd.setBegndate(parseDate(column(cols, spec.idxBegndate())));
        cmd.setEnddate(parseDate(column(cols, spec.idxEnddate())));
        cmd.setUpdtTime(ChsDateTimeParseUtil.parseLocalDateTime(column(cols, spec.idxUpdtTime())));
        cmd.setSourceInfno(spec.sourceInfno());
        cmd.setSourceLineNo(lineNo);
        cmd.setRawJson(JSON.toJSONString(cols));
        return cmd;
    }

    private static String peekLastColumn(InputStream inputStream, int columnIndex) throws IOException {
        String last = null;
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] cols = line.split("\t", -1);
                String value = column(cols, columnIndex);
                if (StringUtils.isNotBlank(value)) {
                    last = value.trim();
                }
            }
        }
        return last;
    }

    private static String peekMaxColumn(InputStream inputStream, int columnIndex) throws IOException {
        String max = null;
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] cols = line.split("\t", -1);
                String value = column(cols, columnIndex);
                if (StringUtils.isBlank(value)) {
                    continue;
                }
                String trimmed = value.trim();
                if (max == null || trimmed.compareTo(max) > 0) {
                    max = trimmed;
                }
            }
        }
        return max;
    }

    private static String column(String[] cols, int index) {
        if (cols == null || index < 0 || index >= cols.length) {
            return null;
        }
        String value = cols[index];
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static LocalDate parseDate(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        String text;
        if (value.length() > 10) {
            text = value.substring(0, 10);
        } else {
            text = value;
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    public record ParseResult(List<UpsertNationalCatalogRowCommand> rows, String nextVersion) {
    }
}
