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
import com.openhis.chs.domain.reconciliation.result.ClearAmountSummaryResult;
import com.openhis.chs.domain.reconciliation.result.ReconDetail3202Result;
import com.openhis.chs.domain.reconciliation.result.ReconDetailResultRowResult;
import com.openhis.chs.domain.reconciliation.result.ReconDetailRowResult;
import com.openhis.chs.domain.reconciliation.service.YbReconciliationDomainService;
import com.openhis.chs.domain.reconciliation.service.YbReconciliationQueryService;
import com.openhis.chs.domain.share.constants.ChsErrorCodes;
import com.openhis.chs.gateway.client.InvokeContext;
import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.gateway.scheme.reconciliation.parser.ReconPlatformOutputParser;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * 【3202】明细对账编排：本地汇总→TAB/ZIP→9101 上传→3202 提交→9102 下载解析→saveReconDetail 入库。
 * <p>实现 domain {@link Recon3202DetailInvoker}；低阶 9101/3202 gateway 步骤由同包 invoker 完成。</p>
 */
@Component
@RequiredArgsConstructor
public class Recon3202Workflow implements Recon3202DetailInvoker {

    private final Recon9101Invoker recon9101Invoker;
    private final Recon3202Invoker recon3202Invoker;
    private final Cat9102FileDownloadAccess cat9102FileDownloadAccess;
    private final ReconPlatformSupport reconPlatformSupport;
    private final YbReconciliationQueryService reconciliationQueryService;
    private final YbReconciliationDomainService reconciliationDomainService;

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
        // 结束早于开始则无法生成明细对账文件
        if (end.isBefore(begin)) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "结束日期不能早于开始日期");
        }
        // 结算经办机构为 3202 必填
        if (isEmpty(safe.getSetlOptins())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "结算经办机构不能为空");
        }

        Long tenantId = TenantUtils.getCurrentTenantId();
        SummarizeClearAmountCommand period = new SummarizeClearAmountCommand()
            .setContractBusNo(safe.getContractBusNo())
            .setContractId(safe.getContractId())
            .setBegndate(safe.getStmtBegndate())
            .setEnddate(safe.getStmtEnddate());
        List<ReconDetailRowResult> detailRows = reconciliationQueryService.loadDetailRows(period, tenantId);

        BigDecimal medfee = safe.getMedfeeSumamt();
        BigDecimal fund = safe.getFundPaySumamt();
        BigDecimal cash = safe.getCashPayamt();
        Integer setlCount = safe.getFixmedinsSetlCnt();
        // 金额/笔数缺省时：先按维度列表汇总，再折叠为区间合计填入 3202
        if (medfee == null || fund == null || cash == null || setlCount == null) {
            List<ClearAmountSummaryResult> amountRows =
                reconciliationQueryService.summarizeClearAmounts(period, tenantId);
            ClearAmountSummaryResult summary =
                reconciliationQueryService.mergeClearAmountSummaries(amountRows);
            if (medfee == null) {
                medfee = summary.getMedfeeSumamt();
            }
            if (fund == null) {
                fund = summary.getFundAppySum();
            }
            if (cash == null) {
                cash = summary.getCashPayamt();
            }
            if (setlCount == null) {
                setlCount = summary.getSetlCount();
            }
        }

        byte[] txtBytes = ReconDetailFileSupport.buildTabTxtBytes(detailRows);
        String zipFilename = ReconDetailFileSupport.buildZipFilename(ctx.fixmedinsCode());
        String txtEntryName = zipFilename.replace(".zip", "");
        byte[] zipBytes = ReconDetailFileSupport.zipTxt(txtBytes, txtEntryName);
        InvokeContext invokeContext = reconPlatformSupport.buildInvokeContext();

        var uploadResult = recon9101Invoker.upload(ctx.busNo(), zipBytes, zipFilename, invokeContext);
        ReconDetail3202Command platformCommand = new ReconDetail3202Command()
            .setSetlOptins(safe.getSetlOptins().trim())
            .setFileQuryNo(uploadResult.getFileQuryNo())
            .setStmtBegndate(safe.getStmtBegndate())
            .setStmtEnddate(safe.getStmtEnddate())
            .setMedfeeSumamt(medfee)
            .setFundPaySumamt(fund)
            .setCashPayamt(cash)
            .setFixmedinsSetlCnt(setlCount)
            .setClrType(safe.getClrType())
            .setRefdSetlFlag(StringUtils.defaultIfBlank(safe.getRefdSetlFlag(), "0"));

        ResultBody body3202 = recon3202Invoker.invoke(ctx.busNo(), platformCommand, invokeContext);
        JSONObject fileinfo = ReconPlatformOutputParser.parse3202FileInfo(body3202);
        String resultFileQuryNo = fileinfo.getString("file_qury_no");
        String resultFilename = fileinfo.getString("filename");
        String dldEndtime = fileinfo.getString("dld_endtime");

        byte[] resultZip = cat9102FileDownloadAccess.download(
            ctx.busNo(), resultFileQuryNo, resultFilename, ctx.fixmedinsCode());
        byte[] textBytes = ReconDetailFileSupport.unzipToTextBytes(resultZip);
        List<ReconDetailResultRowResult> resultRows = ReconPlatformOutputParser.parse3202ResultZipText(
            new String(textBytes, StandardCharsets.UTF_8));
        int mismatchCount = ReconPlatformOutputParser.countMismatch(resultRows);

        String uploadFilename;
        if (uploadResult.getFilename() != null) {
            uploadFilename = uploadResult.getFilename();
        } else {
            uploadFilename = zipFilename;
        }
        var detailRow = reconciliationDomainService.saveReconDetail(
            ctx.contract().id(),
            ctx.busNo(),
            safe.getSetlOptins().trim(),
            safe.getClrType(),
            platformCommand.getRefdSetlFlag(),
            begin,
            end,
            uploadResult.getFileQuryNo(),
            uploadFilename,
            resultFileQuryNo,
            resultFilename,
            dldEndtime,
            detailRows.size(),
            mismatchCount,
            JSON.toJSONString(resultRows));

        return new ReconDetail3202Result()
            .setLocalRecordId(detailRow.getId())
            .setUploadFileQuryNo(uploadResult.getFileQuryNo())
            .setUploadFilename(detailRow.getUploadFilename())
            .setResultFileQuryNo(resultFileQuryNo)
            .setResultFilename(resultFilename)
            .setDldEndtime(dldEndtime)
            .setRowCount(detailRows.size())
            .setMismatchCount(mismatchCount)
            .setResultRows(resultRows);
    }
}
