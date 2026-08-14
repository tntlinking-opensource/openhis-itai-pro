/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.reconciliation.workflow;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.chs.bridge.reconciliation.invoker.Recon3202Invoker;
import com.openhis.chs.bridge.reconciliation.invoker.Recon9101Invoker;
import com.openhis.chs.bridge.reconciliation.support.ReconDetailFileSupport;
import com.openhis.chs.bridge.reconciliation.support.ReconPlatformSupport;
import com.openhis.chs.bridge.reconciliation.support.ReconPlatformSupport.ReconContractContext;
import com.openhis.chs.domain.catalog.access.Cat9102FileDownloadAccess;
import com.openhis.chs.domain.reconciliation.access.Recon3202DetailInvoker;
import com.openhis.chs.domain.reconciliation.command.ReconDetail3202Command;
import com.openhis.chs.domain.reconciliation.command.SummarizeClearAmountCommand;
import com.openhis.chs.domain.reconciliation.result.ReconDetail3202Result;
import com.openhis.chs.domain.reconciliation.result.ReconDetailResultRowResult;
import com.openhis.chs.domain.reconciliation.result.ReconDetailRowResult;
import com.openhis.chs.domain.reconciliation.service.YbReconciliationDomainService;
import com.openhis.chs.domain.reconciliation.service.YbReconciliationQueryService;
import com.openhis.chs.domain.settlement.YbSettlementVoucherStatus;
import com.openhis.chs.domain.settlement.entity.YbSettlementRecord;
import com.openhis.chs.domain.settlement.entity.YbSettlementReversal;
import com.openhis.chs.domain.settlement.service.YbSettlementRecordQueryService;
import com.openhis.chs.domain.settlement.service.YbSettlementReversalQueryService;
import com.openhis.chs.domain.share.constants.ChsErrorCodes;
import com.openhis.chs.gateway.client.InvokeContext;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.gateway.scheme.reconciliation.parser.ReconPlatformOutputParser;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 【3202】明细对账编排：本地汇总→按退费标志拆分 TAB/ZIP→9101→3202→9102→saveReconDetail。
 * <p>吉林 3202 入参 {@code refd_setl_flag} 必填：正向与反向须分文件、分次对账，头金额/笔数与该文件行合计一致。</p>
 */
@Component
@RequiredArgsConstructor
public class Recon3202Workflow implements Recon3202DetailInvoker {

    private static final DateTimeFormatter SETL_TIME_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Recon9101Invoker recon9101Invoker;
    private final Recon3202Invoker recon3202Invoker;
    private final Cat9102FileDownloadAccess cat9102FileDownloadAccess;
    private final ReconPlatformSupport reconPlatformSupport;
    private final YbReconciliationQueryService reconciliationQueryService;
    private final YbReconciliationDomainService reconciliationDomainService;
    private final YbSettlementRecordQueryService settlementRecordQueryService;
    private final YbSettlementReversalQueryService settlementReversalQueryService;

    @Override
    public ReconDetail3202Result invoke(ReconDetail3202Command command) {
        ReconDetail3202Command safe;
        if (command == null) {
            safe = new ReconDetail3202Command();
        } else {
            safe = command;
        }
        ReconContractContext ctx = reconPlatformSupport.resolveContract(safe.getContractBusNo(), safe.getContractId());
        LocalDate begin = reconPlatformSupport.parseDate(safe.getStmtBegndate(), "对账开始日期");
        LocalDate end = reconPlatformSupport.parseDate(safe.getStmtEnddate(), "对账结束日期");
        if (end.isBefore(begin)) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "结束日期不能早于开始日期");
        }
        if (isEmpty(safe.getSetlOptins())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "结算经办机构不能为空");
        }

        Long tenantId = TenantUtils.getCurrentTenantId();
        SummarizeClearAmountCommand period = new SummarizeClearAmountCommand()
            .setContractBusNo(safe.getContractBusNo())
            .setContractId(safe.getContractId())
            .setBegndate(safe.getStmtBegndate())
            .setEnddate(safe.getStmtEnddate())
            .setInsutype(safe.getInsutype())
            .setClrType(safe.getClrType());
        List<ReconDetailRowResult> allRows = reconciliationQueryService.loadDetailRows(period, tenantId);
        InvokeContext invokeContext = reconPlatformSupport.buildInvokeContext();
        BatchRunResult run = runDetailBatches(ctx, safe, allRows, invokeContext, "");
        if (run.mergedResults.isEmpty()) {
            throw new BusinessException(
                ChsErrorCodes.CONTRACT_INVALID,
                "3202 明细对账失败: " + String.join("; ", run.batchErrors));
        }
        List<ReconDetailResultRowResult> mergedResults = run.mergedResults;
        // Asm：按 setl_id 补本地姓名/结算时间/状态，便于前端排查
        enrichResultRowsFromLocal(mergedResults, tenantId);
        int totalUploaded = run.totalUploaded;
        String lastUploadQuryNo = run.lastUploadQuryNo;
        String lastUploadFilename = run.lastUploadFilename;
        String lastResultQuryNo = run.lastResultQuryNo;
        String lastResultFilename = run.lastResultFilename;
        String lastDldEndtime = run.lastDldEndtime;

        int mismatchCount = ReconPlatformOutputParser.countMismatch(mergedResults);

        // 落库退费标志：多批时记 0（列长 3，避免 "0,1"）
        String onlyFlag = trimFlag(safe.getRefdSetlFlag());
        String saveRefdFlag = ("0".equals(onlyFlag) || "1".equals(onlyFlag)) ? onlyFlag : "0";
        var detailRow = reconciliationDomainService.saveReconDetail(
            ctx.contract().id(),
            ctx.busNo(),
            safe.getSetlOptins().trim(),
            safe.getClrType(),
            saveRefdFlag,
            begin,
            end,
            lastUploadQuryNo,
            lastUploadFilename,
            lastResultQuryNo,
            lastResultFilename,
            lastDldEndtime,
            totalUploaded,
            mismatchCount,
            JSON.toJSONString(mergedResults));

        return new ReconDetail3202Result()
            .setLocalRecordId(detailRow.getId())
            .setUploadFileQuryNo(lastUploadQuryNo)
            .setUploadFilename(detailRow.getUploadFilename())
            .setResultFileQuryNo(lastResultQuryNo)
            .setResultFilename(lastResultFilename)
            .setDldEndtime(lastDldEndtime)
            .setRowCount(totalUploaded)
            .setMismatchCount(mismatchCount)
            .setResultRows(mergedResults);
    }

    private BatchRunResult runDetailBatches(
            ReconContractContext ctx,
            ReconDetail3202Command safe,
            List<ReconDetailRowResult> allRows,
            InvokeContext invokeContext,
            String logPrefix) {
        List<ReconDetailRowResult> refd0 = new ArrayList<>();
        List<ReconDetailRowResult> refd1 = new ArrayList<>();
        for (ReconDetailRowResult row : allRows) {
            if (row == null) {
                continue;
            }
            if ("1".equals(trimFlag(row.getRefdSetlFlag()))) {
                refd1.add(row);
            } else {
                refd0.add(row);
            }
        }
        String onlyFlag = trimFlag(safe.getRefdSetlFlag());
        List<String> batchFlags = new ArrayList<>();
        if ("0".equals(onlyFlag) || "1".equals(onlyFlag)) {
            batchFlags.add(onlyFlag);
        } else {
            if (!refd0.isEmpty()) {
                batchFlags.add("0");
            }
            if (!refd1.isEmpty()) {
                batchFlags.add("1");
            }
        }
        BatchRunResult run = new BatchRunResult();
        for (String flag : batchFlags) {
            List<ReconDetailRowResult> batch = "1".equals(flag) ? refd1 : refd0;
            if (batch.isEmpty()) {
                continue;
            }
            BatchTotals totals = sumBatch(batch);
            try {
                byte[] txtBytes = ReconDetailFileSupport.buildTabTxtBytes(batch);
                String zipFilename = ReconDetailFileSupport.buildZipFilename(ctx.fixmedinsCode());
                String txtEntryName = zipFilename.replace(".zip", "");
                byte[] zipBytes = ReconDetailFileSupport.zipTxt(txtBytes, txtEntryName);
                var uploadResult = recon9101Invoker.upload(ctx.busNo(), zipBytes, zipFilename, invokeContext);
                ReconDetail3202Command platformCommand = new ReconDetail3202Command()
                    .setSetlOptins(safe.getSetlOptins().trim())
                    .setFileQuryNo(uploadResult.getFileQuryNo())
                    .setStmtBegndate(safe.getStmtBegndate())
                    .setStmtEnddate(safe.getStmtEnddate())
                    .setMedfeeSumamt(totals.medfee)
                    .setFundPaySumamt(totals.fund)
                    .setCashPayamt(totals.cash)
                    .setFixmedinsSetlCnt(batch.size())
                    .setClrType(safe.getClrType())
                    .setRefdSetlFlag(flag);
                ResultBody body3202 = recon3202Invoker.invoke(ctx.busNo(), platformCommand, invokeContext);
                JSONObject fileinfo = ReconPlatformOutputParser.parse3202FileInfo(body3202);
                String resultFileQuryNo = fileinfo.getString("file_qury_no");
                String resultFilename = fileinfo.getString("filename");
                String dldEndtime = fileinfo.getString("dld_endtime");
                byte[] resultZip = cat9102FileDownloadAccess.download(
                    ctx.busNo(), resultFileQuryNo, resultFilename, ctx.fixmedinsCode());
                byte[] textBytes = ReconDetailFileSupport.unzipToTextBytes(resultZip);
                List<ReconDetailResultRowResult> batchResults = ReconPlatformOutputParser.parse3202ResultZipText(
                    new String(textBytes, StandardCharsets.UTF_8));
                run.mergedResults.addAll(batchResults);
                run.totalUploaded += batch.size();
                run.lastUploadQuryNo = uploadResult.getFileQuryNo();
                run.lastUploadFilename =
                    uploadResult.getFilename() != null ? uploadResult.getFilename() : zipFilename;
                run.lastResultQuryNo = resultFileQuryNo;
                run.lastResultFilename = resultFilename;
                run.lastDldEndtime = dldEndtime;
            } catch (Exception ex) {
                run.batchErrors.add("refd=" + logPrefix + flag + ":" + ex.getMessage());
            }
        }
        return run;
    }

    /**
     * 用本地结算回执补齐 3202 结果行（姓名/时间/状态/三金额），供前端本院|中心对照。
     * <p>退费行 setl_id 是 2208 的 {@code revoke_setl_id}，落在 {@code yb_settlement_reversal}，
     * 不在 {@code yb_settlement_record.setl_id}；仅查结算表会误报「本院无此结算」。</p>
     *
     * @param rows     9102 解析结果
     * @param tenantId 租户
     */
    private void enrichResultRowsFromLocal(List<ReconDetailResultRowResult> rows, Long tenantId) {
        if (rows == null || rows.isEmpty() || tenantId == null) {
            return;
        }
        Set<String> setlIds = new HashSet<>();
        for (ReconDetailResultRowResult row : rows) {
            if (row == null || isEmpty(row.getSetlId())) {
                continue;
            }
            setlIds.add(row.getSetlId().trim());
        }
        if (setlIds.isEmpty()) {
            return;
        }
        Map<String, YbSettlementRecord> bySetlId =
            settlementRecordQueryService.mapBySetlIds(setlIds, tenantId);
        Map<String, YbSettlementReversal> byRevokeSetlId =
            settlementReversalQueryService.mapByRevokeSetlIds(setlIds, tenantId);
        Set<Long> originIds = new HashSet<>();
        for (YbSettlementReversal reversal : byRevokeSetlId.values()) {
            if (reversal != null && reversal.getSettlementRecordId() != null) {
                originIds.add(reversal.getSettlementRecordId());
            }
        }
        Map<Long, YbSettlementRecord> originById =
            originIds.isEmpty()
                ? Map.of()
                : settlementRecordQueryService.mapByIds(originIds, tenantId);
        for (ReconDetailResultRowResult row : rows) {
            if (row == null || isEmpty(row.getSetlId())) {
                continue;
            }
            String setlId = row.getSetlId().trim();
            YbSettlementRecord local = bySetlId.get(setlId);
            if (local != null) {
                applyLocalSettlement(row, local);
                continue;
            }
            YbSettlementReversal reversal = byRevokeSetlId.get(setlId);
            if (reversal == null) {
                row.setLocalExists(Boolean.FALSE);
                continue;
            }
            applyLocalReversal(row, reversal, originById.get(reversal.getSettlementRecordId()));
        }
    }

    /** 正向/原结算回执补齐本院侧字段。 */
    private void applyLocalSettlement(ReconDetailResultRowResult row, YbSettlementRecord local) {
        row.setLocalExists(Boolean.TRUE);
        if (!isEmpty(local.getPsnName())) {
            row.setPsnName(local.getPsnName().trim());
        }
        row.setLocalStatus(local.getStatus());
        row.setLocalMedfeeSumamt(local.getMedfeeSumamt());
        row.setLocalFundPaySumamt(local.getFundPaySumamt());
        row.setLocalAcctPay(local.getAcctPay());
        LocalDateTime setlTime = local.getSetlTime();
        if (setlTime == null) {
            setlTime = local.getCreatedAt();
        }
        if (setlTime != null) {
            row.setSetlTime(setlTime.format(SETL_TIME_FMT));
        }
    }

    /**
     * 退费行按撤销凭证补齐：状态视为已撤销，金额取凭证（通常为负），姓名取原结算。
     */
    private void applyLocalReversal(
            ReconDetailResultRowResult row,
            YbSettlementReversal reversal,
            YbSettlementRecord origin) {
        row.setLocalExists(Boolean.TRUE);
        row.setLocalStatus(YbSettlementVoucherStatus.REVOKED);
        row.setLocalMedfeeSumamt(reversal.getMedfeeSumamt());
        row.setLocalFundPaySumamt(reversal.getFundPaySumamt());
        row.setLocalAcctPay(reversal.getAcctPay());
        if (origin != null && !isEmpty(origin.getPsnName())) {
            row.setPsnName(origin.getPsnName().trim());
        }
        LocalDateTime setlTime = reversal.getReversedAt();
        if (setlTime == null) {
            setlTime = reversal.getCreatedAt();
        }
        if (setlTime != null) {
            row.setSetlTime(setlTime.format(SETL_TIME_FMT));
        }
    }

    private static BatchTotals sumBatch(List<ReconDetailRowResult> rows) {
        BigDecimal medfee = BigDecimal.ZERO;
        BigDecimal fund = BigDecimal.ZERO;
        BigDecimal cash = BigDecimal.ZERO;
        for (ReconDetailRowResult row : rows) {
            medfee = medfee.add(nullToZero(row.getMedfeeSumamt()));
            fund = fund.add(nullToZero(row.getFundPaySumamt()));
            cash = cash.add(nullToZero(row.getPsnCashPay()));
        }
        return new BatchTotals(medfee, fund, cash);
    }

    private static final class BatchRunResult {
        private final List<ReconDetailResultRowResult> mergedResults = new ArrayList<>();
        private final List<String> batchErrors = new ArrayList<>();
        private int totalUploaded;
        private String lastUploadQuryNo;
        private String lastUploadFilename;
        private String lastResultQuryNo;
        private String lastResultFilename;
        private String lastDldEndtime;
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value;
    }

    private static String trimFlag(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    private record BatchTotals(BigDecimal medfee, BigDecimal fund, BigDecimal cash) {
    }
}
