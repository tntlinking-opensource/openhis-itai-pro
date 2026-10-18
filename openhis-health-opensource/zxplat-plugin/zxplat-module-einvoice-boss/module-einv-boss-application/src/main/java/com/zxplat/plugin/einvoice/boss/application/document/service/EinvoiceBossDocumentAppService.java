/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.application.document.service;

import com.alibaba.fastjson2.JSON;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.session.CurrentOperatorResolver;
import com.zxplat.plugin.einvoice.boss.application.document.response.BossAdjustLog;
import com.zxplat.plugin.einvoice.boss.application.document.response.BossAmountValidateResult;
import com.zxplat.plugin.einvoice.boss.application.document.response.BossDocumentBrief;
import com.zxplat.plugin.einvoice.boss.application.document.response.BossDocumentDetail;
import com.zxplat.plugin.einvoice.boss.application.document.response.BossTaskSummary;
import com.zxplat.plugin.einvoice.boss.application.document.request.SaveDocumentAdjustmentRequest;
import com.zxplat.plugin.einvoice.boss.application.internal.issue.BossWriteOffStatusSupport;
import com.zxplat.plugin.einvoice.boss.domain.share.EinvBossErrorCodes;
import com.zxplat.plugin.einvoice.boss.domain.issue.result.BossIssueSnapshot;
import com.zxplat.plugin.einvoice.boss.domain.issue.result.BossLineResult;
import com.zxplat.plugin.einvoice.boss.domain.enums.AmountStatus;
import com.zxplat.plugin.einvoice.boss.domain.enums.IssueKind;
import com.zxplat.plugin.einvoice.boss.domain.issue.loader.EinvoiceIssueSnapshotContextLoader;
import com.zxplat.plugin.einvoice.boss.domain.document.entity.EinvBossAmountAdjustLog;
import com.zxplat.plugin.einvoice.boss.domain.document.entity.EinvBossDocument;
import com.zxplat.plugin.einvoice.boss.domain.document.entity.EinvBossLine;
import com.zxplat.plugin.einvoice.boss.domain.document.repository.EinvBossAmountAdjustLogRepository;
import com.zxplat.plugin.einvoice.boss.domain.document.repository.EinvBossDocumentRepository;
import com.zxplat.plugin.einvoice.boss.domain.document.repository.EinvBossLineRepository;
import com.zxplat.plugin.einvoice.boss.domain.document.service.BossInvoiceAmountValidator;
import com.zxplat.plugin.einvoice.boss.domain.issue.entity.EinvBossIssueTask;
import com.zxplat.plugin.einvoice.boss.domain.issue.repository.EinvBossIssueTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 博思电子票据单据页应用服务（HTTP：{@code /api/app/einvoiceBossDocument/*}）�?
 *
 * <p>职责：单据详情、金额预校验、人工调额（P1）、调额审计日志。不直接调用 GatewayClient�?
 * 调额后由 Worker 重新开具时尊重 {@code amountStatus=ADJUSTED}�?/p>
 */
@Service
@RequiredArgsConstructor
public class EinvoiceBossDocumentAppService {

    /** 调额补差行的默认 chargeCode�?*/
    private static final String BALANCE_CHARGE_CODE = "BALANCE_ADJ";

    private final EinvBossDocumentRepository documentRepository;
    private final EinvBossLineRepository lineRepository;
    private final EinvBossAmountAdjustLogRepository adjustLogRepository;
    private final EinvBossIssueTaskRepository taskRepository;
    private final EinvoiceIssueSnapshotContextLoader issueSnapshotContextLoader;
    private final BossInvoiceAmountValidator amountValidator;
    /** 写路径操作人门控（B.6.5），禁止 AppService 直读 ICurrentUser�?*/
    private final CurrentOperatorResolver currentOperatorResolver;
    private final BossWriteOffStatusSupport writeOffStatusSupport;

    /**
     * 按支付主键查询单据摘要（账单 Banner 跳转）�?
     *
     * @param paymentId 支付主键
     * @return 摘要（无单据时字段为空）
     */
    public BossDocumentBrief getBriefByPaymentId(Long paymentId) {
        BossDocumentBrief dto = new BossDocumentBrief();
        if (paymentId == null) {
            return dto;
        }
        documentRepository.findFirstByPaymentIdAndIsDeletedFalseOrderByIdDesc(paymentId).ifPresent(document -> {
            dto.setDocumentId(document.getId());
            dto.setInvoiceId(document.getInvoiceId());
        });
        return dto;
    }

    /**
     * 按单据主键查询详情�?
     *
     * @param documentId 单据主键
     * @return 详情
     */
    public BossDocumentDetail getByDocumentId(Long documentId) {
        EinvBossDocument document = requireDocument(documentId);
        return buildDetail(document);
    }

    /**
     * 按票据主键查询详情�?
     *
     * @param invoiceId 票据主键
     * @return 详情
     */
    public BossDocumentDetail getByInvoiceId(Long invoiceId) {
        EinvBossDocument document = documentRepository.findFirstByInvoiceIdAndIsDeletedFalseOrderByIdAsc(invoiceId)
                .orElseThrow(() -> new BusinessException(EinvBossErrorCodes.EINV_DOCUMENT_NOT_FOUND, "无电子票据单据"));
        return buildDetail(document);
    }

    /**
     * 预校验金额�?
     *
     * @param documentId 单据主键
     * @return 校验结果
     */
    public BossAmountValidateResult validateAmount(Long documentId) {
        EinvBossDocument document = requireDocument(documentId);
        assertAdjustable(document);
        List<EinvBossLine> lines = lineRepository.findByDocumentIdAndIsDeletedFalseOrderByLineNoAsc(document.getId());
        return computeValidateResult(document.getTotalAmt(), lines);
    }

    /**
     * 保存金额调整�?
     *
     * @param request 调整请求
     * @return 更新后详�?
     */
    public BossDocumentDetail saveAdjustment(SaveDocumentAdjustmentRequest request) {
        // Gate: 单据存在 + 草稿态可调额
        if (request == null || request.getDocumentId() == null) {
            throw new BusinessException(EinvBossErrorCodes.EINV_DOCUMENT_NOT_FOUND, "单据主键不能为空");
        }
        String reason = request.getReason() == null ? "" : request.getReason().trim();
        if (reason.length() < 5) {
            throw new BusinessException(EinvBossErrorCodes.EINV_AMOUNT_MISMATCH, "调整原因至少 5 个字");
        }
        EinvBossDocument document = requireDocument(request.getDocumentId());
        assertAdjustable(document);
        BigDecimal beforeTotal = document.getTotalAmt();
        BigDecimal targetTotal = request.getTotalAmt() == null ? beforeTotal : request.getTotalAmt();

        List<EinvBossLine> existingLines = lineRepository.findByDocumentIdAndIsDeletedFalseOrderByLineNoAsc(document.getId());
        Map<Long, EinvBossLine> lineMap = existingLines.stream()
                .collect(Collectors.toMap(EinvBossLine::getId, line -> line));

        List<EinvBossLine> workingLines = new ArrayList<>();
        int maxLineNo = existingLines.stream().map(EinvBossLine::getLineNo).max(Integer::compareTo).orElse(0);
        for (SaveDocumentAdjustmentRequest.LineItem item : request.getLines()) {
            if (item.getLineId() != null && lineMap.containsKey(item.getLineId())) {
                EinvBossLine line = lineMap.get(item.getLineId());
                line.setAmount(scale2(item.getAmount()));
                line.setAdjusted(true);
                line.setAdjustReason(reason);
                workingLines.add(line);
            } else if (item.isNewLine() || item.getLineId() == null) {
                EinvBossLine line = new EinvBossLine();
                line.setTenantId(document.getTenantId());
                line.setDocumentId(document.getId());
                line.setLineType("CHARGE");
                line.setLineNo(++maxLineNo);
                line.setChargeCode(StringUtils.hasText(item.getChargeCode()) ? item.getChargeCode() : BALANCE_CHARGE_CODE);
                line.setChargeName(StringUtils.hasText(item.getChargeName()) ? item.getChargeName() : "金额调平");
                line.setItemName(line.getChargeName());
                line.setStdAmount(scale2(item.getStdAmount() != null ? item.getStdAmount() : item.getAmount()));
                line.setAmount(scale2(item.getAmount()));
                line.setQuantity(item.getQuantity() != null ? item.getQuantity() : BigDecimal.ONE);
                line.setUnit(item.getUnit() != null ? item.getUnit() : "次");
                line.setAdjusted(true);
                line.setAdjustReason(reason);
                line.setIsDeleted(false);
                workingLines.add(line);
            }
        }
        if (workingLines.isEmpty()) {
            workingLines.addAll(existingLines);
        }

        List<BigDecimal> amounts = workingLines.stream().map(EinvBossLine::getAmount).collect(Collectors.toList());
        amountValidator.validate(scale2(targetTotal), amounts);

        for (EinvBossLine line : workingLines) {
            lineRepository.save(line);
        }
        document.setTotalAmt(scale2(targetTotal));
        document.setAmountStatus(AmountStatus.ADJUSTED.getCode());
        documentRepository.save(document);

        EinvBossAmountAdjustLog log = new EinvBossAmountAdjustLog();
        log.setTenantId(document.getTenantId());
        log.setDocumentId(document.getId());
        log.setOperatorId(requireOperatorId());
        log.setBeforeTotal(scale2(beforeTotal));
        log.setAfterTotal(scale2(targetTotal));
        log.setReason(reason);
        log.setDiffJson(JSON.toJSONString(buildDiffJson(existingLines, workingLines)));
        log.setCreatedAt(LocalDateTime.now());
        adjustLogRepository.save(log);

        // Cmd: 支付额不一致时返回警告（不阻断保存�?
        BossDocumentDetail detail = buildDetail(document);
        if (document.getPaymentAmt() != null
                && scale2(targetTotal).compareTo(scale2(document.getPaymentAmt())) != 0) {
            detail.setWarningCode(EinvBossErrorCodes.EINV_PAYMENT_AMOUNT_MISMATCH);
            detail.setWarningMessage("开票总额与支付金额不一致，请核对后再重新开票");
        }
        return detail;
    }

    /**
     * 组装单据详情 DTO：票面、明细、任务、调额日志、冲红展示�?
     *
     * @param document boss 单据
     * @return 前端详情契约
     */
    private BossDocumentDetail buildDetail(EinvBossDocument document) {
        BossDocumentDetail dto = new BossDocumentDetail();
        dto.setDocumentId(document.getId());
        dto.setInvoiceId(document.getInvoiceId());
        dto.setPaymentId(document.getPaymentId());
        dto.setIssueKind(document.getIssueKind());
        dto.setIssueKindLabel(resolveIssueKindLabel(document.getIssueKind()));
        dto.setBusNo(document.getBusNo());
        dto.setTotalAmt(document.getTotalAmt());
        dto.setPaymentAmt(document.getPaymentAmt());
        dto.setAmountStatus(document.getAmountStatus());
        dto.setBossBillNo(document.getBossBillNo());
        dto.setBossBatchCode(document.getBossBatchCode());
        dto.setPictureUrl(document.getPictureUrl());
        dto.setPictureNetUrl(document.getPictureNetUrl());
        dto.setBillQrCode(document.getBillQrCode());
        if (IssueKind.COM.getCode().equals(document.getIssueKind())) {
            dto.setPatientName(document.getPayerName());
            dto.setGenderDisplay(document.getPayerSex());
            dto.setAge(document.getPayerAge());
            dto.setInvoiceStatus(StringUtils.hasText(document.getBossBillNo()) ? "ISSUED" : "DRAFT");
        } else {
            BossIssueSnapshot snapshot = issueSnapshotContextLoader.loadForInvoice(document.getInvoiceId());
            dto.setPatientName(snapshot.getPatientName());
            dto.setGenderDisplay(snapshot.getGenderDisplay());
            dto.setAge(snapshot.getAge());
            dto.setEncounterBusNo(snapshot.getEncounterBusNo());
            dto.setInvoiceStatus(snapshot.getInvoiceStatus());
            dto.setIssuedAt(snapshot.getIssuedAt());
        }

        List<EinvBossLine> lines = lineRepository.findByDocumentIdAndIsDeletedFalseOrderByLineNoAsc(document.getId());
        dto.setLines(lines.stream().map(this::toLineDto).collect(Collectors.toList()));

        taskRepository.findByInvoiceIdAndIsDeletedFalse(document.getInvoiceId()).ifPresent(task -> dto.setTask(toTaskDto(task)));

        dto.setAdjustLogs(adjustLogRepository.findByDocumentIdOrderByCreatedAtDesc(document.getId()).stream()
                .map(this::toAdjustLogDto).collect(Collectors.toList()));

        String invoiceStatus = dto.getInvoiceStatus();
        BossWriteOffStatusSupport.WriteOffDisplay writeOff =
                writeOffStatusSupport.resolve(document, invoiceStatus);
        dto.setWriteOffStatus(writeOff.getWriteOffStatus());
        dto.setWriteOffStatusLabel(writeOff.getWriteOffStatusLabel());
        dto.setWriteOffBillNo(writeOff.getWriteOffBillNo() != null
                ? writeOff.getWriteOffBillNo()
                : document.getWriteOffBillNo());
        dto.setWriteOffAt(writeOff.getWriteOffAt() != null ? writeOff.getWriteOffAt() : document.getWriteOffAt());
        dto.setWriteOffMessage(writeOff.getWriteOffMessage() != null
                ? writeOff.getWriteOffMessage()
                : document.getWriteOffMessage());
        dto.setWriteOffBatchCode(document.getWriteOffBatchCode());
        return dto;
    }

    /**
     * 计算明细合计与开票总额的差额（调额页预校验）�?
     *
     * @param totalAmt 开票总额
     * @param lines 明细�?
     * @return �?lineSum、diff、valid 的校验结�?
     */
    private BossAmountValidateResult computeValidateResult(BigDecimal totalAmt, List<EinvBossLine> lines) {
        BigDecimal sum = BigDecimal.ZERO;
        for (EinvBossLine line : lines) {
            if (line.getAmount() != null) {
                sum = sum.add(line.getAmount());
            }
        }
        BigDecimal normalizedSum = scale2(sum);
        BigDecimal normalizedTotal = scale2(totalAmt);
        BossAmountValidateResult result = new BossAmountValidateResult();
        result.setLineSum(normalizedSum);
        result.setTotalAmt(normalizedTotal);
        result.setDiff(normalizedSum.subtract(normalizedTotal));
        result.setValid(normalizedSum.compareTo(normalizedTotal) == 0);
        return result;
    }

    /**
     * 加载未删除单据，不存在时�?{@link EinvBossErrorCodes#EINV_DOCUMENT_NOT_FOUND}�?
     *
     * @param documentId 单据主键
     * @return 单据实体
     */
    private EinvBossDocument requireDocument(Long documentId) {
        return documentRepository.findById(documentId)
                .filter(doc -> !Boolean.TRUE.equals(doc.getIsDeleted()))
                .orElseThrow(() -> new BusinessException(EinvBossErrorCodes.EINV_DOCUMENT_NOT_FOUND, "无电子票据单据"));
    }

    /** 仅草稿（未成功开出）票据允许调额�?*/
    private void assertAdjustable(EinvBossDocument document) {
        if (IssueKind.COM.getCode().equals(document.getIssueKind())) {
            if (StringUtils.hasText(document.getBossBillNo())) {
                throw new BusinessException(EinvBossErrorCodes.EINV_ADJUST_NOT_ALLOWED, "仅草稿状态票据允许调额");
            }
            return;
        }
        BossIssueSnapshot snapshot = issueSnapshotContextLoader.loadForInvoice(document.getInvoiceId());
        if (!"DRAFT".equals(snapshot.getInvoiceStatus())) {
            throw new BusinessException(EinvBossErrorCodes.EINV_ADJUST_NOT_ALLOWED, "仅草稿状态票据允许调额");
        }
    }

    private Long requireOperatorId() {
        return currentOperatorResolver.requireOperatorId("AUTH-001", "当前登录用户不能为空");
    }

    /** 明细实体 �?列表�?DTO�?*/
    private BossLineResult toLineDto(EinvBossLine line) {
        BossLineResult dto = new BossLineResult();
        dto.setLineId(line.getId());
        dto.setLineNo(line.getLineNo());
        dto.setChargeCode(line.getChargeCode());
        dto.setChargeName(line.getChargeName());
        // 调额页展示具体项目名与甲乙丙；出站仍�?chargeName 作统计大�?
        dto.setItemName(line.getItemName());
        dto.setListRemark(line.getListRemark());
        dto.setListMedicalCareType(line.getListMedicalCareType());
        dto.setUnit(line.getUnit());
        dto.setStdAmount(line.getStdAmount());
        dto.setAmount(line.getAmount());
        dto.setAdjusted(Boolean.TRUE.equals(line.getAdjusted()));
        return dto;
    }

    /** 任务实体 �?摘要 DTO（状态、重试、最近错误）�?*/
    private BossTaskSummary toTaskDto(EinvBossIssueTask task) {
        BossTaskSummary dto = new BossTaskSummary();
        dto.setTaskId(task.getId());
        dto.setStatus(task.getStatus());
        dto.setAttemptCount(task.getAttemptCount());
        dto.setLastErrorCode(task.getLastErrorCode());
        dto.setLastErrorMsg(task.getLastErrorMsg());
        dto.setNextRetryAt(task.getNextRetryAt());
        return dto;
    }

    /** 调额审计实体 �?前端 DTO�?*/
    private BossAdjustLog toAdjustLogDto(EinvBossAmountAdjustLog log) {
        BossAdjustLog dto = new BossAdjustLog();
        dto.setId(log.getId());
        dto.setBeforeTotal(log.getBeforeTotal());
        dto.setAfterTotal(log.getAfterTotal());
        dto.setReason(log.getReason());
        dto.setCreatedAt(log.getCreatedAt());
        return dto;
    }

    /**
     * 构建调额前后明细差异 JSON（写�?{@code einv_boss_amount_adjust_log.diff_json}）�?
     *
     * @param before 调额前明�?
     * @param after 调额后明�?
     * @return 可序列化的差异结�?
     */
    private Map<String, Object> buildDiffJson(List<EinvBossLine> before, List<EinvBossLine> after) {
        Map<Long, EinvBossLine> beforeById = before.stream()
                .filter(line -> line.getId() != null)
                .collect(Collectors.toMap(EinvBossLine::getId, line -> line, (a, b) -> a));
        List<Map<String, Object>> lineDiffs = new ArrayList<>();
        for (EinvBossLine afterLine : after) {
            Map<String, Object> row = new HashMap<>();
            row.put("lineId", afterLine.getId());
            row.put("chargeCode", afterLine.getChargeCode());
            row.put("afterAmount", afterLine.getAmount());
            EinvBossLine beforeLine = afterLine.getId() == null ? null : beforeById.get(afterLine.getId());
            row.put("beforeAmount", beforeLine == null ? null : beforeLine.getAmount());
            lineDiffs.add(row);
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("beforeLineCount", before.size());
        payload.put("afterLineCount", after.size());
        payload.put("lines", lineDiffs);
        return payload;
    }

    /** issueKind �?�?中文业务线标签�?*/
    private String resolveIssueKindLabel(String issueKind) {
        if (IssueKind.REG.getCode().equals(issueKind)) {
            return "挂号";
        }
        if (IssueKind.IMP.getCode().equals(issueKind)) {
            return "住院";
        }
        if (IssueKind.COM.getCode().equals(issueKind)) {
            return "通用票";
        }
        return "门诊";
    }

    /** 金额统一保留两位小数；null 视为 0.00�?*/
    private BigDecimal scale2(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
