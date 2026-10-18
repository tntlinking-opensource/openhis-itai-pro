/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.reconciliation.support;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.trimToEmpty;

import com.zxplat.plugin.chs.domain.reconciliation.result.ReconDetailRowResult;
import com.zxplat.plugin.chs.domain.share.constants.ChsErrorCodes;
import com.zxplat.framework.core.application.exception.BusinessException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 3202 明细对账文件 TAB 文本与 ZIP 打包工具。
 * <p>供 {@link com.zxplat.plugin.chs.bridge.reconciliation.workflow.Recon3202Workflow} 9101 上传前生成平台要求的明细文件。</p>
 */
public final class ReconDetailFileSupport {

    private static final DateTimeFormatter ZIP_NAME_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private ReconDetailFileSupport() {
    }

    /** 将明细行列表编码为 UTF-8 TAB 分隔文本（setl_id、mdtrt_id、psn_no、金额列…）。 */
    public static byte[] buildTabTxtBytes(List<ReconDetailRowResult> rows) {
        if (rows == null || rows.isEmpty()) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "对账明细为空，请先确认区间内有医保结算记录");
        }
        StringBuilder sb = new StringBuilder();
        for (ReconDetailRowResult row : rows) {
            sb.append(trimToEmpty(row.getSetlId())).append('\t')
                .append(trimToEmpty(row.getMdtrtId())).append('\t')
                .append(trimToEmpty(row.getPsnNo())).append('\t')
                .append(formatAmount(row.getMedfeeSumamt())).append('\t')
                .append(formatAmount(row.getFundPaySumamt())).append('\t')
                .append(formatAmount(row.getAcctPay())).append('\t')
                .append(trimToEmpty(row.getRefdSetlFlag())).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** 生成 9101 上传 ZIP 文件名（{@code {fixmedinsCode}_{yyyyMMddHHmmss}.txt.zip}）。 */
    public static String buildZipFilename(String fixmedinsCode) {
        String code = isEmpty(fixmedinsCode) ? "fixmedins" : fixmedinsCode.trim();
        return code + "_" + LocalDateTime.now().format(ZIP_NAME_FMT) + ".txt.zip";
    }

    /** 将 TAB 文本打包为单 entry ZIP（entry 名默认补 {@code .txt} 后缀）。 */
    public static byte[] zipTxt(byte[] txtBytes, String zipEntryName) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {
            String entryName = zipEntryName.endsWith(".txt") ? zipEntryName : zipEntryName + ".txt";
            ZipEntry entry = new ZipEntry(entryName);
            zos.putNextEntry(entry);
            zos.write(txtBytes);
            zos.closeEntry();
            zos.finish();
            return baos.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException(ChsErrorCodes.FILE_PARSE_FAILED, "3202 ZIP 打包失败: " + ex.getMessage());
        }
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

    private static String formatAmount(BigDecimal value) {
        if (value == null) {
            return "0.00";
        }
        // 平台数值型 16,2：固定两位小数，避免 stripTrailingZeros 产出 35.7 / 0
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
