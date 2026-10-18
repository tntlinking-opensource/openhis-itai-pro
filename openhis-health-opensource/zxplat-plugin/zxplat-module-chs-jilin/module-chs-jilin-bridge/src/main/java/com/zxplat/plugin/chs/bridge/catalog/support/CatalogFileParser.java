/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.catalog.support;

import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;
import com.alibaba.fastjson2.JSON;
import com.zxplat.plugin.chs.domain.catalog.command.UpsertNationalCatalogRowCommand;
import com.zxplat.plugin.chs.domain.catalog.support.NationalCatalog1301FieldExtractor;
import com.zxplat.plugin.chs.domain.catalog.support.NationalCatalogPackFieldExtractor;
import com.zxplat.plugin.chs.domain.share.constants.ChsCatalogConstants;
import com.zxplat.plugin.chs.domain.share.constants.ChsErrorCodes;
import com.zxplat.plugin.chs.domain.share.constants.ChsInfNo;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.infrastructure.util.ChineseConvertUtils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 13xx 目录 ZIP/TXT（TAB 分隔）通用解析器。
 * <p>供 Phase1 元数据扫描与异步入库流式解析使用，按 {@link CatalogFileParserRegistry} 注册的列规格解析。</p>
 */
public final class CatalogFileParser {

    private CatalogFileParser() {
    }

    /** 从 9102 ZIP 字节流解压出首个 TXT 文件内容。 */
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

    /** 从 ZIP 中读取版本链下一 ver（按 infno 列规格）。 */
    public static String peekNextVersionFromZip(String infno, byte[] zipBytes) {
        return peekNextVersionFromTextBytes(infno, unzipToTextBytes(zipBytes));
    }

    /** 从 TXT 末行读取版本链下一 ver。 */
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

    /** 扫描 TXT 全包，取版本号列的最大值。 */
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

    /** 扫描 TXT 末行，取版本号列末值。 */
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

    /** 统计 TXT 非空数据行数。 */
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
     * 阶段一专用：仅按 infno 时从 ZIP 解压并单次扫描元数据 */
    public static TextPackageMetadata scanZipPackageMetadata(String infno, byte[] zipBytes) {
        return scanTextPackageMetadata(infno, unzipToTextBytes(zipBytes));
    }

    /**
     * TXT 包元数据扫描结果。
     *
     * @param dataLineCount   非空数据行数
     * @param txtVersionName  版本链末值（用于展示）
     * @param txtLastVerNo    版本号列末值
     * @param txtMaxVerNo     版本号列最大值
     * @param nextLocalVer    建议下一包 local_ver
     */
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

    /** 解压 ZIP 并全量解析为 upsert 命令列表。 */
    public static ParseResult parseZip(String infno, byte[] zipBytes) {
        return parseTextBytes(infno, unzipToTextBytes(zipBytes));
    }

    /** 全量解析 TXT 为 upsert 命令列表。 */
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
     * @return 本包建议的下一包 local_ver（与 {@link ParseResult#nextVersion()} 一致）
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

    /** 流式解析批次回调。 */
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
                if (isNotEmpty(versionName)) {
                    lastVersionName = versionName.trim();
                }
                String versionNo = column(cols, spec.idxVersionNo());
                if (isNotEmpty(versionNo)) {
                    String trimmed = versionNo.trim();
                    lastVersionNo = trimmed;
                    if (maxVersionNo == null || trimmed.compareTo(maxVersionNo) > 0) {
                        maxVersionNo = trimmed;
                    }
                }
                String chainValue = column(cols, versionChainCol);
                if (isNotEmpty(chainValue)) {
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
                if (isNotEmpty(lastVersionName)) {
                    yield lastVersionName.trim();
                }
                if (isNotEmpty(maxVersionNo)) {
                    yield maxVersionNo.trim();
                }
                yield null;
            }
            case VERSION_NUMBER_ONLY -> {
                if (isNotEmpty(lastVersionNo)) {
                    yield lastVersionNo.trim();
                }
                yield null;
            }
        };
    }

    private static int versionChainColumn(CatalogFileParserSpec spec) {
        return switch (spec.chainMode()) {
            case VERSION_NAME_THEN_NUMBER -> {
                if (spec.idxVersionName() >= 0) {
                    yield spec.idxVersionName();
                }
                yield spec.idxVersionNo();
            }
            case VERSION_NUMBER_ONLY -> spec.idxVersionNo();
        };
    }

    private static String resolveCursorVersionFromCols(
            CatalogFileParserSpec spec, String[] cols, String current) {
        return switch (spec.chainMode()) {
            case VERSION_NAME_THEN_NUMBER -> {
                String versionName = column(cols, spec.idxVersionName());
                if (isNotEmpty(versionName)) {
                    yield versionName.trim();
                }
                String versionNo = column(cols, spec.idxVersionNo());
                if (isNotEmpty(versionNo)) {
                    yield versionNo.trim();
                }
                yield current;
            }
            case VERSION_NUMBER_ONLY -> {
                String versionNo = column(cols, spec.idxVersionNo());
                if (isNotEmpty(versionNo)) {
                    yield versionNo.trim();
                }
                yield current;
            }
        };
    }

    private static UpsertNationalCatalogRowCommand toCommand(
            CatalogFileParserSpec spec, String[] cols, int lineNo) {
        String hilistCode = column(cols, spec.idxHilistCode());
        if (isEmpty(hilistCode)) {
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
        apply1301Fallbacks(spec, cols, cmd);
        fillPinyinIfMissing(cmd);
        cmd.setUpdtTime(ChsDateTimeParseUtil.parseLocalDateTime(column(cols, spec.idxUpdtTime())));
        cmd.setSourceInfno(spec.sourceInfno());
        cmd.setSourceLineNo(lineNo);
        cmd.setRawJson(JSON.toJSONString(cols));
        NationalCatalogPackFieldExtractor.PackFields pack =
            NationalCatalogPackFieldExtractor.fromColumns(cols);
        cmd.setMinPacCnt(pack.minPacCnt());
        cmd.setMinPacunt(pack.minPacunt());
        cmd.setMinPrepunt(pack.minPrepunt());
        cmd.setMinPrcunt(pack.minPrcunt());
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
                if (isNotEmpty(value)) {
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
                if (isEmpty(value)) {
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

    /** 1301：通用名为占位符时回退注册名/商品名；日期回退生效/失效时间。 */
    private static void apply1301Fallbacks(
            CatalogFileParserSpec spec, String[] cols, UpsertNationalCatalogRowCommand cmd) {
        if (!ChsInfNo.DRUG_CATALOG_DOWNLOAD.equals(spec.infno())) {
            return;
        }
        NationalCatalog1301FieldExtractor.DisplayFields extra =
            NationalCatalog1301FieldExtractor.fromColumns(cols);
        if (cmd.getHilistName() == null) {
            cmd.setHilistName(extra.hilistName());
        }
        if (cmd.getChrgitmLv() == null) {
            cmd.setChrgitmLv(extra.chrgitmLv());
        }
        if (cmd.getLmtUsedFlag() == null) {
            cmd.setLmtUsedFlag(extra.lmtUsedFlag());
        }
        if (cmd.getBegndate() == null) {
            cmd.setBegndate(parseDate(extra.begndate()));
        }
        if (cmd.getEnddate() == null) {
            cmd.setEnddate(parseDate(extra.enddate()));
        }
        if (cmd.getPinyin() == null) {
            cmd.setPinyin(extra.pinyin());
        }
    }

    /** 文件未给拼音时按名称生成首字母，避免页面出现字面量 null。 */
    private static void fillPinyinIfMissing(UpsertNationalCatalogRowCommand cmd) {
        if (cmd.getPinyin() != null || isEmpty(cmd.getHilistName())) {
            return;
        }
        String pinyin = ChineseConvertUtils.toPinyinFirstLetter(cmd.getHilistName());
        if (isNotEmpty(pinyin)) {
            cmd.setPinyin(pinyin);
        }
    }

    private static String column(String[] cols, int index) {
        if (cols == null || index < 0 || index >= cols.length) {
            return null;
        }
        return NationalCatalog1301FieldExtractor.clean(cols[index]);
    }

    private static LocalDate parseDate(String value) {
        if (isEmpty(value)) {
            return null;
        }
        String text;
        if (value.length() > 10) {
            text = value.substring(0, 10);
        } else {
            text = value;
        }
        try {
            LocalDate date = LocalDate.parse(text);
            if (date.getYear() <= 1) {
                return null;
            }
            return date;
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    /** 全量解析结果：行命令列表 + 版本链下一 ver。 */
    public record ParseResult(List<UpsertNationalCatalogRowCommand> rows, String nextVersion) {
    }
}
