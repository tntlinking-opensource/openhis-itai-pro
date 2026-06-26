/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.reconciliation.support;

import static com.openhis.whale.core.application.util.StringUtils.trimToEmpty;

import com.openhis.chs.application.catalog.support.CatalogFileParser;
import com.openhis.chs.domain.ChsErrorCodes;
import com.openhis.chs.domain.inpatient.entity.YbInpatientPreSettle;
import com.openhis.chs.domain.settlement.entity.YbSettlementRecord;
import com.openhis.chs.domain.reconciliation.dto.response.ReconDetailResultRowDto;
import com.openhis.chs.domain.reconciliation.dto.response.ReconDetailRowDto;
import com.openhis.whale.core.application.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** 3202 明细文件 TAB/ZIP 编解码。 */
public final class ReconDetailFileSupport {

    private static final DateTimeFormatter ZIP_NAME_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String REFD_SETL_FLAG_NORMAL = "0";

    private ReconDetailFileSupport() {
    }

    public static List<ReconDetailRowDto> fromOutpatientRows(List<YbSettlementRecord> rows) {
        List<ReconDetailRowDto> result = new ArrayList<>();
        for (YbSettlementRecord row : rows) {
            if (StringUtils.isBlank(row.getSetlId()) || StringUtils.isBlank(row.getMdtrtId())) {
                continue;
            }
            ReconDetailRowDto dto = new ReconDetailRowDto();
            dto.setSetlId(row.getSetlId().trim());
            dto.setMdtrtId(row.getMdtrtId().trim());
            dto.setPsnNo(row.getPsnNo());
            dto.setMedfeeSumamt(row.getMedfeeSumamt());
            dto.setFundPaySumamt(row.getFundPaySumamt());
            dto.setAcctPay(row.getAcctPay());
            dto.setRefdSetlFlag(REFD_SETL_FLAG_NORMAL);
            dto.setSource("OP");
            result.add(dto);
        }
        return result;
    }

    public static List<ReconDetailRowDto> fromInpatientRows(List<YbInpatientPreSettle> rows) {
        List<ReconDetailRowDto> result = new ArrayList<>();
        for (YbInpatientPreSettle row : rows) {
            if (StringUtils.isBlank(row.getSetlId()) || StringUtils.isBlank(row.getMdtrtId())) {
                continue;
            }
            ReconDetailRowDto dto = new ReconDetailRowDto();
            dto.setSetlId(row.getSetlId().trim());
            dto.setMdtrtId(row.getMdtrtId().trim());
            dto.setPsnNo(row.getPsnNo());
            dto.setMedfeeSumamt(row.getMedfeeSumamt());
            dto.setFundPaySumamt(row.getFundPaySumamt());
            dto.setAcctPay(row.getAcctPay());
            dto.setRefdSetlFlag(REFD_SETL_FLAG_NORMAL);
            dto.setSource("IP");
            result.add(dto);
        }
        return result;
    }

    public static byte[] buildTabTxtBytes(List<ReconDetailRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "对账明细为空，请先确认区间内有医保结算记录");
        }
        StringBuilder sb = new StringBuilder();
        for (ReconDetailRowDto row : rows) {
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

    public static String buildZipFilename(String fixmedinsCode) {
        String code = StringUtils.defaultIfBlank(fixmedinsCode, "fixmedins");
        return code + "_" + LocalDateTime.now().format(ZIP_NAME_FMT) + ".txt.zip";
    }

    public static byte[] zipTxt(byte[] txtBytes, String zipEntryName) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {
            String entryName;
            if (zipEntryName.endsWith(".txt")) {
                entryName = zipEntryName;
            } else {
                entryName = zipEntryName + ".txt";
            }
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

    public static List<ReconDetailResultRowDto> parseResultZip(byte[] zipBytes) {
        byte[] textBytes = CatalogFileParser.unzipToTextBytes(zipBytes);
        String text = new String(textBytes, StandardCharsets.UTF_8);
        List<ReconDetailResultRowDto> rows = new ArrayList<>();
        for (String line : text.split("\\R")) {
            if (StringUtils.isBlank(line)) {
                continue;
            }
            String[] cols = line.split("\t", -1);
            if (cols.length < 5) {
                continue;
            }
            ReconDetailResultRowDto row = new ReconDetailResultRowDto();
            row.setPsnNo(cols[0]);
            if (cols.length > 1) {
                row.setMdtrtId(cols[1]);
            } else {
                row.setMdtrtId(null);
            }
            if (cols.length > 2) {
                row.setSetlId(cols[2]);
            } else {
                row.setSetlId(null);
            }
            if (cols.length > 3) {
                row.setMsgid(cols[3]);
            } else {
                row.setMsgid(null);
            }
            if (cols.length > 4) {
                row.setStmtRslt(cols[4]);
            } else {
                row.setStmtRslt(null);
            }
            if (cols.length > 5) {
                row.setRefdSetlFlag(cols[5]);
            } else {
                row.setRefdSetlFlag(null);
            }
            if (cols.length > 6) {
                row.setMemo(cols[6]);
            } else {
                row.setMemo(null);
            }
            row.setMedfeeSumamt(parseDecimal(cols, 7));
            row.setFundPaySumamt(parseDecimal(cols, 8));
            row.setAcctPay(parseDecimal(cols, 9));
            rows.add(row);
        }
        return rows;
    }

    public static int countMismatch(List<ReconDetailResultRowDto> rows) {
        int count = 0;
        for (ReconDetailResultRowDto row : rows) {
            if (!"0".equals(trimToEmpty(row.getStmtRslt()))) {
                count += 1;
            }
        }
        return count;
    }

    private static BigDecimal parseDecimal(String[] cols, int index) {
        if (cols.length <= index || StringUtils.isBlank(cols[index])) {
            return null;
        }
        try {
            return new BigDecimal(cols[index].trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String formatAmount(BigDecimal value) {
        if (value == null) {
            return "0.00";
        }
        return value.stripTrailingZeros().toPlainString();
    }
}