/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.reconciliation;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.chs.api.ChsInfNo;
import com.openhis.chs.api.InsuranceGatewayClient;
import com.openhis.chs.api.InvokeContext;
import com.openhis.chs.api.ResultBody;
import com.openhis.chs.application.config.ContractConfig;
import com.openhis.chs.application.config.ContractConfigLoader;
import com.openhis.chs.application.gateway.session.ChsSignCredentialResolver;
import com.openhis.chs.application.gateway.support.ChsCurrentOperatorResolver;
import com.openhis.chs.application.outpatient.support.ChsPlatformResultSupport;
import com.openhis.chs.application.catalog.assembler.Catalog9102InputAssembler;
import com.openhis.chs.application.reconciliation.assembler.ChsReconciliationPlatformAssembler;
import com.openhis.chs.application.reconciliation.support.ReconDetailFileSupport;
import com.openhis.chs.domain.ChsErrorCodes;
import com.openhis.chs.domain.catalog.port.ChsContractSnapshot;
import com.openhis.chs.domain.catalog.port.ChsInsuranceContractPort;
import com.openhis.chs.domain.inpatient.entity.YbInpatientPreSettle;
import com.openhis.chs.domain.inpatient.repository.YbInpatientPreSettleRepository;
import com.openhis.chs.domain.settlement.entity.YbSettlementRecord;
import com.openhis.chs.domain.settlement.repository.YbSettlementRecordRepository;
import com.openhis.chs.domain.reconciliation.dto.request.ApplyJilinClear3203ARequest;
import com.openhis.chs.domain.reconciliation.dto.request.QueryClearOrg3206ARequest;
import com.openhis.chs.domain.reconciliation.dto.request.QueryClearStatus3205ARequest;
import com.openhis.chs.domain.reconciliation.dto.request.ReconDaily3201Request;
import com.openhis.chs.domain.reconciliation.dto.request.ReconDetail3202Request;
import com.openhis.chs.domain.reconciliation.dto.request.ListLocalReconRecordsRequest;
import com.openhis.chs.domain.reconciliation.dto.request.QueryCrossProvinceRecon3209ARequest;
import com.openhis.chs.domain.reconciliation.dto.request.RevokeJilinClear3204ARequest;
import com.openhis.chs.domain.reconciliation.dto.request.SummarizeClearAmountRequest;
import com.openhis.chs.domain.reconciliation.dto.response.ApplyJilinClear3203AResult;
import com.openhis.chs.domain.reconciliation.dto.response.ClearAmountSummaryDto;
import com.openhis.chs.domain.reconciliation.dto.response.ClearOrg3206AResult;
import com.openhis.chs.domain.reconciliation.dto.response.ClearStatus3205ARowDto;
import com.openhis.chs.domain.reconciliation.dto.response.CrossProvinceReconRowDto;
import com.openhis.chs.domain.reconciliation.dto.response.LocalClearApplyDto;
import com.openhis.chs.domain.reconciliation.dto.response.LocalReconDailyDto;
import com.openhis.chs.domain.reconciliation.dto.response.ReconDaily3201Result;
import com.openhis.chs.domain.reconciliation.dto.response.LocalReconDetailDto;
import com.openhis.chs.domain.reconciliation.dto.response.ReconDetail3202Result;
import com.openhis.chs.domain.reconciliation.dto.response.ReconDetailResultRowDto;
import com.openhis.chs.domain.reconciliation.dto.response.ReconDetailRowDto;
import com.openhis.chs.domain.reconciliation.entity.YbClearApply;
import com.openhis.chs.domain.reconciliation.entity.YbReconDaily;
import com.openhis.chs.domain.reconciliation.entity.YbReconDetail;
import com.openhis.chs.domain.reconciliation.repository.YbReconDetailRepository;
import com.openhis.chs.domain.reconciliation.facade.ChsReconciliationOperations;
import com.openhis.chs.domain.reconciliation.repository.YbClearApplyRepository;
import com.openhis.chs.domain.reconciliation.repository.YbReconDailyRepository;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** 吉林对账清算：3206A→3203A→3205A→3204A + 3201 日对账 + 3202 明细对账 + 3209A 跨省三方对账。 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class ChsReconciliationAppService implements ChsReconciliationOperations {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final InsuranceGatewayClient gatewayClient;
    private final ChsInsuranceContractPort contractPort;
    private final ChsCurrentOperatorResolver currentOperatorResolver;
    private final ChsSignCredentialResolver signCredentialResolver;
    private final YbSettlementRecordRepository settlementRecordRepository;
    private final YbInpatientPreSettleRepository inpatientPreSettleRepository;
    private final YbClearApplyRepository clearApplyRepository;
    private final YbReconDailyRepository reconDailyRepository;
    private final YbReconDetailRepository reconDetailRepository;
    private final Catalog9102InputAssembler catalog9102InputAssembler;

    /**
     * 【3206A】查询清算机构列表；分页返回 clr_optins 供前端选择清算经办机构。
     *
     * @param request 契约标识与分页参数（page_num、page_size 等）
     * @return 清算机构分页结果，含 clr_optins 数组
     * @throws BusinessException 契约无效或平台返回失败时
     */
    @Override
    public ClearOrg3206AResult queryClearOrganizations(QueryClearOrg3206ARequest request) {
        // Pre：解析契约与报文组装上下文
        ContractContext ctx = resolveContract(request.getContractBusNo(), request.getContractId());
        JSONObject input = ChsReconciliationPlatformAssembler.assemble3206(request, ctx.config());
        // Cmd：调网关 3206A 查询清算机构
        ResultBody body = gatewayClient.invoke(
            ctx.busNo(), ChsInfNo.CLEAR_ORG_QUERY_JILIN, input, buildInvokeContext());
        ChsPlatformResultSupport.requireSuccess(body, ChsInfNo.CLEAR_ORG_QUERY_JILIN);
        JSONObject output = ChsPlatformResultSupport.parseOutputObject(body);
        // Asm：解析分页字段与 clr_optins 数组
        ClearOrg3206AResult result = new ClearOrg3206AResult();
        result.setPages(output.getInteger("pages"));
        result.setPageNum(output.getInteger("page_num"));
        result.setPageSize(output.getInteger("page_size"));
        result.setClrOptins(parseClrOptinsArray(output.get("clr_optins")));
        return result;
    }

    /**
     * 汇总清算金额；按日期区间统计本院门诊结算与住院已结算记录的金额与笔数（不上平台）。
     *
     * @param request 契约标识与 begndate/enddate（yyyy-MM-dd）
     * @return 医疗费、基金、个账、现金合计及结算笔数
     * @throws BusinessException 日期为空、格式错误或结束日期早于开始日期时
     */
    @Override
    public ClearAmountSummaryDto summarizeClearAmounts(SummarizeClearAmountRequest request) {
        LocalDate begin = parseDate(request.getBegndate(), "开始日期");
        LocalDate end = parseDate(request.getEnddate(), "结束日期");
        // Gate：结束日期不能早于开始日期
        if (end.isBefore(begin)) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "结束日期不能早于开始日期");
        }
        // Pre：租户与时间窗口 [begin 00:00, end+1 00:00)
        Long tenantId = TenantUtils.getCurrentTenantId();
        LocalDateTime beginAt = begin.atStartOfDay();
        LocalDateTime endExclusive = end.plusDays(1).atStartOfDay();
        BigDecimal medfee = BigDecimal.ZERO;
        BigDecimal fund = BigDecimal.ZERO;
        BigDecimal acct = BigDecimal.ZERO;
        BigDecimal cash = BigDecimal.ZERO;
        int count = 0;
        // Cmd：累加门诊结算记录
        List<YbSettlementRecord> opRows =
            settlementRecordRepository.findAllByTenantIdAndIsDeletedFalseAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                tenantId, beginAt, endExclusive);
        for (YbSettlementRecord row : opRows) {
            medfee = medfee.add(nullToZero(row.getMedfeeSumamt()));
            fund = fund.add(nullToZero(row.getFundPaySumamt()));
            acct = acct.add(nullToZero(row.getAcctPay()));
            cash = cash.add(nullToZero(row.getPsnCashPay()));
            count += 1;
        }
        // Cmd：累加住院已结算且 setl_id 非空记录
        List<YbInpatientPreSettle> ipRows = inpatientPreSettleRepository
            .findAllByTenantIdAndIsDeletedFalseAndStatusAndSetlIdIsNotNullAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            tenantId, YbInpatientPreSettle.STATUS_SETTLED, beginAt, endExclusive);
        for (YbInpatientPreSettle row : ipRows) {
            medfee = medfee.add(nullToZero(row.getMedfeeSumamt()));
            fund = fund.add(nullToZero(row.getFundPaySumamt()));
            acct = acct.add(nullToZero(row.getAcctPay()));
            cash = cash.add(nullToZero(row.getPsnCashPay()));
            count += 1;
        }
        // Asm：组装汇总 DTO
        ClearAmountSummaryDto summary = new ClearAmountSummaryDto();
        summary.setMedfeeSumamt(medfee);
        summary.setMedSumfee(medfee);
        summary.setFundAppySum(fund);
        summary.setAcctPay(acct);
        summary.setCashPayamt(cash);
        summary.setSetlCount(count);
        return summary;
    }

    /**
     * 【3203A】吉林清算申请；平台受理后落库 yb_clear_apply 并返回 clr_appy_evt_id。
     *
     * @param request 清算类型、机构、日期区间及申报金额
     * @return 平台事件 ID 与本地记录主键
     * @throws BusinessException 契约无效、日期无效或平台返回失败时
     */
    @Override
    public ApplyJilinClear3203AResult applyJilinClear(ApplyJilinClear3203ARequest request) {
        // Pre：解析契约并组装 3203A 入参
        ContractContext ctx = resolveContract(request.getContractBusNo(), request.getContractId());
        JSONObject input = ChsReconciliationPlatformAssembler.assemble3203(request);
        // Cmd：调网关提交清算申请
        ResultBody body = gatewayClient.invoke(
            ctx.busNo(), ChsInfNo.CLEAR_APPLY_JILIN, input, buildInvokeContext());
        ChsPlatformResultSupport.requireSuccess(body, ChsInfNo.CLEAR_APPLY_JILIN);
        JSONObject output = ChsPlatformResultSupport.parseOutputObject(body);
        String evtId = output.getString("clr_appy_evt_id");
        // Asm：写入本地清算申请记录（状态=已申请，clr_stas=10）
        YbClearApply row = new YbClearApply();
        row.setContractId(ctx.contract().id());
        row.setContractBusNo(ctx.busNo());
        row.setClrType(request.getClrType());
        row.setClrOptins(request.getClrOptins());
        row.setBegndate(parseDate(request.getBegndate(), "开始日期"));
        row.setEnddate(parseDate(request.getEnddate(), "结束日期"));
        row.setMedfeeSumamt(request.getMedfeeSumamt());
        row.setMedSumfee(request.getMedSumfee());
        row.setFundAppySum(request.getFundAppySum());
        row.setCashPayamt(request.getCashPayamt());
        row.setAcctPay(request.getAcctPay());
        row.setClrAppyEvtId(evtId);
        row.setClrStas("10");
        row.setStatus(YbClearApply.STATUS_APPLIED);
        clearApplyRepository.saveAndFlush(row);
        return new ApplyJilinClear3203AResult().setClrAppyEvtId(evtId).setLocalRecordId(row.getId());
    }

    /**
     * 【3205A】查询清算申请状态；返回平台 clr_appys 列表供前端展示进度。
     *
     * @param request 契约标识与查询条件（clr_appy_evt_id 等）
     * @return 清算申请状态行列表
     * @throws BusinessException 契约无效或平台返回失败时
     */
    @Override
    public List<ClearStatus3205ARowDto> queryClearApplyStatus(QueryClearStatus3205ARequest request) {
        // Pre：解析契约并组装 3205A 入参
        ContractContext ctx = resolveContract(request.getContractBusNo(), request.getContractId());
        JSONObject input = ChsReconciliationPlatformAssembler.assemble3205(request);
        // Cmd：调网关查询清算状态
        ResultBody body = gatewayClient.invoke(
            ctx.busNo(), ChsInfNo.CLEAR_STATUS_QUERY_JILIN, input, buildInvokeContext());
        ChsPlatformResultSupport.requireSuccess(body, ChsInfNo.CLEAR_STATUS_QUERY_JILIN);
        JSONObject output = ChsPlatformResultSupport.parseOutputObject(body);
        // Asm：解析 clr_appys 为 DTO 列表
        return parseClearStatusRows(output.get("clr_appys"));
    }

    /**
     * 【3204A】撤销吉林清算申请；平台成功后同步更新本地 yb_clear_apply 状态为已撤销。
     *
     * @param request 契约标识与 clr_appy_evt_id
     * @throws BusinessException 契约无效或平台返回失败时
     */
    @Override
    public void revokeJilinClear(RevokeJilinClear3204ARequest request) {
        // Pre：解析契约并组装 3204A 入参
        ContractContext ctx = resolveContract(request.getContractBusNo(), request.getContractId());
        JSONObject input = ChsReconciliationPlatformAssembler.assemble3204(request);
        // Cmd：调网关撤销清算申请
        ResultBody body = gatewayClient.invoke(
            ctx.busNo(), ChsInfNo.CLEAR_APPLY_REVOKE_JILIN, input, buildInvokeContext());
        ChsPlatformResultSupport.requireSuccess(body, ChsInfNo.CLEAR_APPLY_REVOKE_JILIN);
        // Asm：按 evt_id 更新本地记录；未找到则忽略（平台已撤销即可）
        Long tenantId = TenantUtils.getCurrentTenantId();
        clearApplyRepository.findFirstByClrAppyEvtIdAndTenantIdAndIsDeletedFalse(
                request.getClrAppyEvtId(), tenantId)
            .ifPresent(row -> {
                row.setStatus(YbClearApply.STATUS_REVOKED);
                row.setClrStas("00");
                clearApplyRepository.save(row);
            });
    }

    /**
     * 【3201】日对账；提交本院汇总与平台核对，结果落库 yb_recon_daily。
     *
     * @param request 险种、清算类型、经办机构、日期区间及汇总金额
     * @return 平台对账结果（stmt_rslt）与本地记录 ID
     * @throws BusinessException 契约无效、日期无效或平台返回失败时
     */
    @Override
    public ReconDaily3201Result reconcileDailyLedger(ReconDaily3201Request request) {
        // Pre：解析契约并组装 3201 入参
        ContractContext ctx = resolveContract(request.getContractBusNo(), request.getContractId());
        JSONObject input = ChsReconciliationPlatformAssembler.assemble3201(request);
        // Cmd：调网关执行日对账
        ResultBody body = gatewayClient.invoke(
            ctx.busNo(), ChsInfNo.RECON_DAILY_LEDGER, input, buildInvokeContext());
        ChsPlatformResultSupport.requireSuccess(body, ChsInfNo.RECON_DAILY_LEDGER);
        JSONObject output = ChsPlatformResultSupport.parseOutputObject(body);
        // 分支业务：部分报文 stmtinfo 嵌套在 output 内，缺失时直接使用 output 根节点
        JSONObject stmtinfo = output.getJSONObject("stmtinfo");
        if (stmtinfo == null) {
            stmtinfo = output;
        }
        // Asm：落库日对账记录并返回结果摘要
        YbReconDaily row = new YbReconDaily();
        row.setContractId(ctx.contract().id());
        row.setContractBusNo(ctx.busNo());
        row.setInsutype(request.getInsutype());
        row.setClrType(request.getClrType());
        row.setSetlOptins(request.getSetlOptins());
        row.setStmtBegndate(parseDate(request.getStmtBegndate(), "对账开始日期"));
        row.setStmtEnddate(parseDate(request.getStmtEnddate(), "对账结束日期"));
        row.setMedfeeSumamt(request.getMedfeeSumamt());
        row.setFundPaySumamt(request.getFundPaySumamt());
        row.setAcctPay(request.getAcctPay());
        row.setFixmedinsSetlCnt(request.getFixmedinsSetlCnt());
        row.setStmtRslt(stmtinfo.getString("stmt_rslt"));
        row.setStmtRsltDscr(stmtinfo.getString("stmt_rslt_dscr"));
        reconDailyRepository.saveAndFlush(row);
        return new ReconDaily3201Result()
            .setSetlOptins(stmtinfo.getString("setl_optins"))
            .setStmtRslt(row.getStmtRslt())
            .setStmtRsltDscr(row.getStmtRsltDscr())
            .setLocalRecordId(row.getId());
    }

    /**
     * 预览明细对账行；按日期区间加载本院门诊+住院结算明细，供 3202 上传前核对。
     *
     * @param request 契约标识与 begndate/enddate
     * @return 明细对账 TXT 行列表（未调平台）
     * @throws BusinessException 日期无效或结束日期早于开始日期时
     */
    @Override
    public List<ReconDetailRowDto> previewReconDetailRows(SummarizeClearAmountRequest request) {
        // Cmd：复用 loadDetailRows 加载本地明细
        return loadDetailRows(request);
    }

    /**
     * 【3202】明细对账；压缩上传明细 TXT、调平台对账、9102 下载结果 ZIP 并落库 yb_recon_detail。
     *
     * @param request 经办机构、日期区间、汇总金额及 clr_type 等
     * @return 上传/结果文件号、不一致笔数及逐行对账结果
     * @throws BusinessException 日期无效、经办机构为空、契约无效或平台/文件处理失败时
     */
    @Override
    public ReconDetail3202Result reconcileDetailLedger(ReconDetail3202Request request) {
        // Pre：解析契约与对账日期
        ContractContext ctx = resolveContract(request.getContractBusNo(), request.getContractId());
        LocalDate begin = parseDate(request.getStmtBegndate(), "对账开始日期");
        LocalDate end = parseDate(request.getStmtEnddate(), "对账结束日期");
        // Gate：日期区间与结算经办机构必填
        if (end.isBefore(begin)) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "结束日期不能早于开始日期");
        }
        if (StringUtils.isBlank(request.getSetlOptins())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "结算经办机构不能为空");
        }
        SummarizeClearAmountRequest period = new SummarizeClearAmountRequest()
            .setContractBusNo(request.getContractBusNo())
            .setContractId(request.getContractId())
            .setBegndate(request.getStmtBegndate())
            .setEnddate(request.getStmtEnddate());
        List<ReconDetailRowDto> detailRows = loadDetailRows(period);

        BigDecimal medfee = request.getMedfeeSumamt();
        BigDecimal fund = request.getFundPaySumamt();
        BigDecimal cash = request.getCashPayamt();
        Integer setlCount = request.getFixmedinsSetlCnt();
        // 分支业务：汇总金额或笔数未传时，自动从本地结算记录汇总
        if (medfee == null || fund == null || cash == null || setlCount == null) {
            ClearAmountSummaryDto summary = summarizeClearAmounts(period);
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

        // Cmd：构建明细 ZIP 并上传至平台
        byte[] txtBytes = ReconDetailFileSupport.buildTabTxtBytes(detailRows);
        String zipFilename = ReconDetailFileSupport.buildZipFilename(ctx.config().getFixmedinsCode());
        String txtEntryName = zipFilename.replace(".zip", "");
        byte[] zipBytes = ReconDetailFileSupport.zipTxt(txtBytes, txtEntryName);
        InvokeContext invokeContext = buildInvokeContext();

        var uploadResult = gatewayClient.uploadFile(ctx.busNo(), zipBytes, zipFilename, invokeContext);
        ReconDetail3202Request platformRequest = new ReconDetail3202Request()
            .setSetlOptins(request.getSetlOptins().trim())
            .setFileQuryNo(uploadResult.getFileQuryNo())
            .setStmtBegndate(request.getStmtBegndate())
            .setStmtEnddate(request.getStmtEnddate())
            .setMedfeeSumamt(medfee)
            .setFundPaySumamt(fund)
            .setCashPayamt(cash)
            .setFixmedinsSetlCnt(setlCount)
            .setClrType(request.getClrType())
            .setRefdSetlFlag(StringUtils.defaultIfBlank(request.getRefdSetlFlag(), "0"));
        JSONObject input3202 = ChsReconciliationPlatformAssembler.assemble3202(platformRequest);
        // Cmd：调网关 3202 提交明细对账
        ResultBody body3202 = gatewayClient.invoke(
            ctx.busNo(), ChsInfNo.RECON_DETAIL_LEDGER, input3202, invokeContext);
        ChsPlatformResultSupport.requireSuccess(body3202, ChsInfNo.RECON_DETAIL_LEDGER);
        JSONObject output3202 = ChsPlatformResultSupport.parseOutputObject(body3202);
        // 分支业务：fileinfo 缺失时直接使用 output 根节点
        JSONObject fileinfo = output3202.getJSONObject("fileinfo");
        if (fileinfo == null) {
            fileinfo = output3202;
        }
        String resultFileQuryNo = fileinfo.getString("file_qury_no");
        String resultFilename = fileinfo.getString("filename");
        String dldEndtime = fileinfo.getString("dld_endtime");

        // Cmd：9102 下载对账结果 ZIP 并解析不一致行
        JSONObject input9102 = catalog9102InputAssembler.assemble(
            resultFileQuryNo, resultFilename, ctx.config().getFixmedinsCode());
        byte[] resultZip = gatewayClient.downloadFile(ctx.busNo(), input9102, invokeContext);
        List<ReconDetailResultRowDto> resultRows = ReconDetailFileSupport.parseResultZip(resultZip);
        int mismatchCount = ReconDetailFileSupport.countMismatch(resultRows);

        // Asm：落库明细对账记录
        YbReconDetail detailRow = new YbReconDetail();
        detailRow.setContractId(ctx.contract().id());
        detailRow.setContractBusNo(ctx.busNo());
        detailRow.setSetlOptins(request.getSetlOptins().trim());
        detailRow.setClrType(request.getClrType());
        detailRow.setRefdSetlFlag(platformRequest.getRefdSetlFlag());
        detailRow.setStmtBegndate(begin);
        detailRow.setStmtEnddate(end);
        detailRow.setUploadFileQuryNo(uploadResult.getFileQuryNo());
        String uploadFilename;
        if (uploadResult.getFilename() != null) {
            uploadFilename = uploadResult.getFilename();
        } else {
            uploadFilename = zipFilename;
        }
        detailRow.setUploadFilename(uploadFilename);
        detailRow.setResultFileQuryNo(resultFileQuryNo);
        detailRow.setResultFilename(resultFilename);
        detailRow.setDldEndtime(dldEndtime);
        detailRow.setRowCount(detailRows.size());
        detailRow.setMismatchCount(mismatchCount);
        detailRow.setResultJson(JSON.toJSONString(resultRows));
        reconDetailRepository.saveAndFlush(detailRow);

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

    /**
     * 查询本地明细对账记录；按契约 bus_no 倒序，可选 monthFilter（yyyyMM）过滤对账开始月。
     *
     * @param request 契约 bus_no 与可选 monthFilter
     * @return 本地 yb_recon_detail 摘要列表
     */
    @Override
    public List<LocalReconDetailDto> listLocalReconDetailRecords(ListLocalReconRecordsRequest request) {
        // Pre：当前租户
        Long tenantId = TenantUtils.getCurrentTenantId();
        // Cmd：按契约查询本地明细对账记录
        List<YbReconDetail> rows = reconDetailRepository
            .findAllByContractBusNoAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
                request.getContractBusNo(), tenantId);
        List<LocalReconDetailDto> result = new ArrayList<>();
        for (YbReconDetail row : rows) {
            // 分支业务：monthFilter 非空时仅保留对账开始日期所在月份匹配的记录
            if (StringUtils.isNotBlank(request.getMonthFilter()) && row.getStmtBegndate() != null) {
                String ym = row.getStmtBegndate().format(DateTimeFormatter.ofPattern("yyyyMM"));
                if (!request.getMonthFilter().trim().equals(ym)) {
                    continue;
                }
            }
            result.add(toLocalReconDetailDto(row));
        }
        // Asm：返回 DTO 列表
        return result;
    }

    /**
     * 查询本地清算申请记录；按契约 bus_no 倒序，可选 monthFilter（yyyyMM）过滤开始日期所在月。
     *
     * @param request 契约 bus_no 与可选 monthFilter
     * @return 本地 yb_clear_apply 摘要列表
     */
    @Override
    public List<LocalClearApplyDto> listLocalClearApplies(ListLocalReconRecordsRequest request) {
        // Pre：当前租户
        Long tenantId = TenantUtils.getCurrentTenantId();
        // Cmd：按契约查询本地清算申请
        List<YbClearApply> rows = clearApplyRepository
            .findAllByContractBusNoAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
                request.getContractBusNo(), tenantId);
        List<LocalClearApplyDto> result = new ArrayList<>();
        for (YbClearApply row : rows) {
            // 分支业务：monthFilter 非空时仅保留开始日期所在月份匹配的记录
            if (StringUtils.isNotBlank(request.getMonthFilter()) && row.getBegndate() != null) {
                String ym = row.getBegndate().format(DateTimeFormatter.ofPattern("yyyyMM"));
                if (!request.getMonthFilter().trim().equals(ym)) {
                    continue;
                }
            }
            result.add(toLocalClearApplyDto(row));
        }
        // Asm：返回 DTO 列表
        return result;
    }

    /**
     * 查询本地日对账记录；按契约 bus_no 倒序，可选 monthFilter（yyyyMM）过滤对账开始月。
     *
     * @param request 契约 bus_no 与可选 monthFilter
     * @return 本地 yb_recon_daily 摘要列表
     */
    @Override
    public List<LocalReconDailyDto> listLocalReconDailyRecords(ListLocalReconRecordsRequest request) {
        // Pre：当前租户
        Long tenantId = TenantUtils.getCurrentTenantId();
        // Cmd：按契约查询本地日对账记录
        List<YbReconDaily> rows = reconDailyRepository
            .findAllByContractBusNoAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
                request.getContractBusNo(), tenantId);
        List<LocalReconDailyDto> result = new ArrayList<>();
        for (YbReconDaily row : rows) {
            // 分支业务：monthFilter 非空时仅保留对账开始日期所在月份匹配的记录
            if (StringUtils.isNotBlank(request.getMonthFilter()) && row.getStmtBegndate() != null) {
                String ym = row.getStmtBegndate().format(DateTimeFormatter.ofPattern("yyyyMM"));
                if (!request.getMonthFilter().trim().equals(ym)) {
                    continue;
                }
            }
            result.add(toLocalReconDailyDto(row));
        }
        // Asm：返回 DTO 列表
        return result;
    }

    /**
     * 【3209A】跨省三方对账失败查询；返回 selt_list 中未对账成功的结算 ID 列表。
     *
     * @param request 契约标识、clr_optins 及查询条件
     * @return 跨省对账失败行（setlId、insuAdmdvs）
     * @throws BusinessException 清算机构为空、契约无效或平台返回失败时
     */
    @Override
    public List<CrossProvinceReconRowDto> queryCrossProvinceReconFailures(
            QueryCrossProvinceRecon3209ARequest request) {
        // Gate：清算机构必填
        if (StringUtils.isBlank(request.getClrOptins())) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "清算机构不能为空");
        }
        // Pre：解析契约并组装 3209A 入参
        ContractContext ctx = resolveContract(request.getContractBusNo(), request.getContractId());
        JSONObject input = ChsReconciliationPlatformAssembler.assemble3209A(request);
        // Cmd：调网关查询跨省对账失败明细
        ResultBody body = gatewayClient.invoke(
            ctx.busNo(), ChsInfNo.CLEAR_CROSS_PROVINCE_RECON_JILIN, input, buildInvokeContext());
        ChsPlatformResultSupport.requireSuccess(body, ChsInfNo.CLEAR_CROSS_PROVINCE_RECON_JILIN);
        JSONObject output = ChsPlatformResultSupport.parseOutputObject(body);
        // Asm：解析 selt_list 为 DTO 列表
        return parseCrossProvinceRows(output.get("selt_list"));
    }

    private List<CrossProvinceReconRowDto> parseCrossProvinceRows(Object raw) {
        if (raw == null) {
            return Collections.emptyList();
        }
        List<JSONObject> objects = new ArrayList<>();
        if (raw instanceof JSONArray array) {
            for (int i = 0; i < array.size(); i++) {
                Object item = array.get(i);
                if (item instanceof JSONObject jo) {
                    objects.add(jo);
                }
            }
        } else if (raw instanceof JSONObject jo) {
            objects.add(jo);
        } else if (raw instanceof String text && StringUtils.isNotBlank(text)) {
            try {
                return parseCrossProvinceRows(JSON.parse(text));
            } catch (RuntimeException ignored) {
                return Collections.emptyList();
            }
        }
        List<CrossProvinceReconRowDto> rows = new ArrayList<>();
        for (JSONObject jo : objects) {
            CrossProvinceReconRowDto row = new CrossProvinceReconRowDto();
            row.setSetlId(firstNonBlank(jo, "setlId", "setl_id"));
            row.setInsuAdmdvs(firstNonBlank(jo, "insuAdmdvs", "insu_admdvs"));
            rows.add(row);
        }
        return rows;
    }

    private String firstNonBlank(JSONObject jo, String... keys) {
        for (String key : keys) {
            String value = jo.getString(key);
            if (StringUtils.isNotBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private List<ReconDetailRowDto> loadDetailRows(SummarizeClearAmountRequest request) {
        LocalDate begin = parseDate(request.getBegndate(), "开始日期");
        LocalDate end = parseDate(request.getEnddate(), "结束日期");
        if (end.isBefore(begin)) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "结束日期不能早于开始日期");
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        LocalDateTime beginAt = begin.atStartOfDay();
        LocalDateTime endExclusive = end.plusDays(1).atStartOfDay();
        List<ReconDetailRowDto> rows = new ArrayList<>();
        rows.addAll(ReconDetailFileSupport.fromOutpatientRows(
            settlementRecordRepository.findAllByTenantIdAndIsDeletedFalseAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                tenantId, beginAt, endExclusive)));
        rows.addAll(ReconDetailFileSupport.fromInpatientRows(
            inpatientPreSettleRepository
                .findAllByTenantIdAndIsDeletedFalseAndStatusAndSetlIdIsNotNullAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                tenantId, YbInpatientPreSettle.STATUS_SETTLED, beginAt, endExclusive)));
        return rows;
    }

    private LocalReconDetailDto toLocalReconDetailDto(YbReconDetail row) {
        LocalReconDetailDto dto = new LocalReconDetailDto();
        dto.setId(row.getId());
        dto.setContractBusNo(row.getContractBusNo());
        dto.setSetlOptins(row.getSetlOptins());
        dto.setClrType(row.getClrType());
        dto.setStmtBegndate(formatDateOrNull(row.getStmtBegndate()));
        dto.setStmtEnddate(formatDateOrNull(row.getStmtEnddate()));
        dto.setRowCount(row.getRowCount());
        dto.setMismatchCount(row.getMismatchCount());
        dto.setUploadFileQuryNo(row.getUploadFileQuryNo());
        dto.setResultFileQuryNo(row.getResultFileQuryNo());
        dto.setCreatedAt(formatDateTimeOrNull(row.getCreatedAt()));
        return dto;
    }

    private List<Map<String, Object>> parseClrOptinsArray(Object raw) {
        if (raw == null) {
            return Collections.emptyList();
        }
        if (raw instanceof JSONArray array) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (int i = 0; i < array.size(); i++) {
                Object item = array.get(i);
                if (item instanceof JSONObject jo) {
                    rows.add(jo);
                } else if (item instanceof Map<?, ?> map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> cast = (Map<String, Object>) map;
                    rows.add(cast);
                }
            }
            return rows;
        }
        if (raw instanceof String text && StringUtils.isNotBlank(text)) {
            try {
                return parseClrOptinsArray(JSON.parseArray(text));
            } catch (RuntimeException ignored) {
                return Collections.emptyList();
            }
        }
        return Collections.emptyList();
    }

    private List<ClearStatus3205ARowDto> parseClearStatusRows(Object raw) {
        if (raw == null) {
            return Collections.emptyList();
        }
        List<JSONObject> objects = new ArrayList<>();
        if (raw instanceof JSONArray array) {
            for (int i = 0; i < array.size(); i++) {
                Object item = array.get(i);
                if (item instanceof JSONObject jo) {
                    objects.add(jo);
                }
            }
        } else if (raw instanceof JSONObject jo) {
            objects.add(jo);
        }
        List<ClearStatus3205ARowDto> rows = new ArrayList<>();
        for (JSONObject jo : objects) {
            ClearStatus3205ARowDto row = new ClearStatus3205ARowDto();
            row.setFixmedinsName(jo.getString("fixmedins_name"));
            row.setFixmedinsCode(jo.getString("fixmedins_code"));
            row.setClrStas(jo.getString("clr_stas"));
            row.setOptTime(jo.getString("opt_time"));
            row.setMedfeeSumamt(jo.getBigDecimal("Medfee_Sumamt"));
            if (row.getMedfeeSumamt() == null) {
                row.setMedfeeSumamt(jo.getBigDecimal("medfee_sumamt"));
            }
            row.setHiAgreSumfee(jo.getBigDecimal("hi_agre_sumfee"));
            row.setFundAppySum(jo.getBigDecimal("fund_appy_sum"));
            row.setCashPayamt(jo.getBigDecimal("cash_payamt"));
            row.setAcctPay(jo.getBigDecimal("acct_pay"));
            row.setBegndate(jo.getString("begndate"));
            row.setEnddate(jo.getString("enddate"));
            row.setClrYm(jo.getString("clr_ym"));
            row.setClrOptins(jo.getString("clr_optins"));
            row.setOpterId(jo.getString("opter_id"));
            row.setOpterName(jo.getString("opter_name"));
            row.setOptinsNo(jo.getString("optins_no"));
            row.setClrAppyEvtId(jo.getString("clr_appy_evt_id"));
            rows.add(row);
        }
        return rows;
    }

    private LocalClearApplyDto toLocalClearApplyDto(YbClearApply row) {
        LocalClearApplyDto dto = new LocalClearApplyDto();
        dto.setId(row.getId());
        dto.setContractBusNo(row.getContractBusNo());
        dto.setClrType(row.getClrType());
        dto.setClrOptins(row.getClrOptins());
        dto.setBegndate(formatDateOrNull(row.getBegndate()));
        dto.setEnddate(formatDateOrNull(row.getEnddate()));
        dto.setMedfeeSumamt(row.getMedfeeSumamt());
        dto.setFundAppySum(row.getFundAppySum());
        dto.setAcctPay(row.getAcctPay());
        dto.setCashPayamt(row.getCashPayamt());
        dto.setClrAppyEvtId(row.getClrAppyEvtId());
        dto.setClrStas(row.getClrStas());
        dto.setStatus(row.getStatus());
        dto.setCreatedAt(formatDateTimeOrNull(row.getCreatedAt()));
        return dto;
    }

    private LocalReconDailyDto toLocalReconDailyDto(YbReconDaily row) {
        LocalReconDailyDto dto = new LocalReconDailyDto();
        dto.setId(row.getId());
        dto.setContractBusNo(row.getContractBusNo());
        dto.setInsutype(row.getInsutype());
        dto.setClrType(row.getClrType());
        dto.setSetlOptins(row.getSetlOptins());
        dto.setStmtBegndate(formatDateOrNull(row.getStmtBegndate()));
        dto.setStmtEnddate(formatDateOrNull(row.getStmtEnddate()));
        dto.setMedfeeSumamt(row.getMedfeeSumamt());
        dto.setFundPaySumamt(row.getFundPaySumamt());
        dto.setAcctPay(row.getAcctPay());
        dto.setFixmedinsSetlCnt(row.getFixmedinsSetlCnt());
        dto.setStmtRslt(row.getStmtRslt());
        dto.setStmtRsltDscr(row.getStmtRsltDscr());
        dto.setCreatedAt(formatDateTimeOrNull(row.getCreatedAt()));
        return dto;
    }

    private String formatDateOrNull(LocalDate date) {
        if (date == null) {
            return null;
        }
        return date.format(DATE_FMT);
    }

    private String formatDateTimeOrNull(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.toString();
    }

    private ContractContext resolveContract(String contractBusNo, Long contractId) {
        ChsContractSnapshot contract = contractPort.requireContract(contractBusNo, contractId);
        String busNo = contractPort.requireContractBusNo(contract);
        ContractConfig config = ContractConfigLoader.getByBusNo(busNo);
        return new ContractContext(contract, busNo, config);
    }

    private InvokeContext buildInvokeContext() {
        Long operatorId = currentOperatorResolver.requireOperatorId("请先登录");
        InvokeContext partial = InvokeContext.builder()
            .operatorId(String.valueOf(operatorId))
            .build();
        return InvokeContext.builder()
            .operatorId(signCredentialResolver.resolveOperatorId(partial))
            .operatorName(signCredentialResolver.resolveOperatorName(partial))
            .build();
    }

    private LocalDate parseDate(String text, String label) {
        if (StringUtils.isBlank(text)) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, label + "不能为空");
        }
        return LocalDate.parse(text.trim(), DATE_FMT);
    }

    private BigDecimal nullToZero(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value;
    }

    private record ContractContext(ChsContractSnapshot contract, String busNo, ContractConfig config) {
    }
}
