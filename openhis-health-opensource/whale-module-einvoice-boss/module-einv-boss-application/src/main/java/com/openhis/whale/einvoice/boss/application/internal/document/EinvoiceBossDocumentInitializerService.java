/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.application.internal.document;

import com.openhis.whale.einvoice.boss.domain.issue.access.BossPayloadMetadataAccess;
import com.openhis.whale.einvoice.boss.gateway.config.EinvoiceBossGatewayProperties;
import com.openhis.whale.einvoice.boss.domain.issue.result.BossChargeLineSnapshot;
import com.openhis.whale.einvoice.boss.domain.issue.result.BossIssueSnapshot;
import com.openhis.whale.einvoice.boss.domain.enums.AmountStatus;
import com.openhis.whale.einvoice.boss.domain.enums.IssueKind;
import com.openhis.whale.einvoice.boss.domain.issue.loader.EinvoiceIssueSnapshotContextLoader;
import com.openhis.whale.einvoice.boss.domain.contrast.entity.EinvChargeContrast;
import com.openhis.whale.einvoice.boss.domain.contrast.repository.EinvChargeContrastRepository;
import com.openhis.whale.einvoice.boss.domain.contrast.service.BossLegacyStatisticCode1Mapping;
import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossDocument;
import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossLine;
import com.openhis.whale.einvoice.boss.domain.document.repository.EinvBossDocumentRepository;
import com.openhis.whale.einvoice.boss.domain.document.repository.EinvBossLineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 从 health 快照同步 {@code einv_boss_document} 与 {@code einv_boss_line}。
 *
 * <p>入队（{@link com.openhis.whale.einvoice.boss.application.internal.issue.BossIssueTaskEnqueueService}）
 * 与开票前刷新均经本服务；{@code amountStatus=ADJUSTED} 时保留人工调额结果。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EinvoiceBossDocumentInitializerService {

    private final EinvoiceIssueSnapshotContextLoader issueSnapshotContextLoader;
    private final EinvBossDocumentRepository documentRepository;
    private final EinvBossLineRepository lineRepository;
    private final EinvChargeContrastRepository contrastRepository;
    private final BossPayloadMetadataAccess payloadMetadataAccess;
    private final EinvoiceBossGatewayProperties properties;

    /**
     * 全量初始化或刷新（首入队；非 ADJUSTED 时重建明细）。
     *
     * @param invoiceId 票据主键
     * @return 单据实体
     */
    public EinvBossDocument initialize(Long invoiceId) {
        return syncFromSnapshot(invoiceId, true);
    }

    /**
     * 开票前刷新：已人工调额（ADJUSTED）时保留明细与总额，仅同步非金额头字段。
     *
     * @param invoiceId 票据主键
     * @return 单据实体
     */
    public EinvBossDocument refreshForIssue(Long invoiceId) {
        return syncFromSnapshot(invoiceId, false);
    }

    /**
     * 从快照同步单据头与可选明细重建。
     *
     * @param invoiceId 票据主键
     * @param allowLineRebuild true 时非 ADJUSTED 单据会软删旧明细并重建
     * @return 持久化后的单据
     */
    private EinvBossDocument syncFromSnapshot(Long invoiceId, boolean allowLineRebuild) {
        BossIssueSnapshot snapshot = issueSnapshotContextLoader.loadForInvoice(invoiceId);
        Optional<EinvBossDocument> existing = documentRepository.findByInvoiceIdAndIsDeletedFalse(invoiceId);
        EinvBossDocument document;
        if (existing.isPresent()) {
            document = existing.get();
        } else {
            document = new EinvBossDocument();
            document.setInvoiceId(invoiceId);
            document.setIsDeleted(false);
        }

        boolean adjusted = AmountStatus.ADJUSTED.getCode().equals(document.getAmountStatus());
        boolean rebuildLines = allowLineRebuild && !adjusted;

        String issueKind = payloadMetadataAccess.resolveIssueKind(snapshot.getInvoiceTypeCode());
        document.setTenantId(snapshot.getTenantId());
        document.setPaymentId(snapshot.getPaymentId());
        document.setIssueKind(issueKind);
        document.setBusNo(snapshot.getBusNo());
        document.setBusType(payloadMetadataAccess.resolveBusType(issueKind));
        document.setPlatformProfile(properties.getPlatform().getProfile());
        // 分支：已调额单据保留 total/payment/amountStatus，避免 Worker 覆盖人工修正
        if (!adjusted) {
            document.setTotalAmt(snapshot.getTotalAmt());
            document.setPaymentAmt(snapshot.getPaymentAmt());
            document.setAmountStatus(AmountStatus.MATCH.getCode());
        }
        document = documentRepository.saveAndFlush(document);

        if (rebuildLines) {
            List<EinvBossLine> persistedLines = rebuildLines(document, snapshot);
            lineRepository.saveAllAndFlush(persistedLines);
        }
        return document;
    }

    /**
     * 软删旧明细并按快照 chargeLines 重建；挂号无明细时补 REG_FEE 单行。
     *
     * @param document 所属单据
     * @param snapshot health 收费明细快照
     * @return 待持久化的新明细列表
     */
    private List<EinvBossLine> rebuildLines(EinvBossDocument document, BossIssueSnapshot snapshot) {
        List<EinvBossLine> existing = lineRepository.findByDocumentIdAndIsDeletedFalseOrderByLineNoAsc(document.getId());
        for (EinvBossLine row : existing) {
            row.setIsDeleted(true);
        }
        lineRepository.saveAll(existing);

        List<EinvBossLine> lines = new ArrayList<>();
        int lineNo = 0;
        for (BossChargeLineSnapshot chargeLine : snapshot.getChargeLines()) {
            lineNo = lineNo + 1;
            EinvBossLine line = new EinvBossLine();
            line.setTenantId(document.getTenantId());
            line.setDocumentId(document.getId());
            line.setLineType("CHG");
            line.setLineNo(lineNo);
            resolveBossCharge(document.getTenantId(), document.getIssueKind(), chargeLine, line);
            applyLineAmounts(chargeLine, line);
            line.setUnit(chargeLine.getUnit());
            // listDetail.name：具体项目名；空则出站回落 chargeName
            line.setItemName(blankToNull(chargeLine.getItemName()));
            line.setSourceChargeItemId(chargeLine.getChargeItemId());
            line.setAdjusted(false);
            line.setIsDeleted(false);
            lines.add(line);
        }
        if (lines.isEmpty() && IssueKind.REG.getCode().equals(document.getIssueKind())) {
            EinvBossLine fallback = new EinvBossLine();
            fallback.setTenantId(document.getTenantId());
            fallback.setDocumentId(document.getId());
            fallback.setLineType("CHG");
            fallback.setLineNo(1);
            BossChargeLineSnapshot regFee = new BossChargeLineSnapshot();
            regFee.setInternalCode("REG_FEE");
            resolveBossCharge(document.getTenantId(), document.getIssueKind(), regFee, fallback);
            fallback.setStdAmount(document.getTotalAmt());
            fallback.setAmount(document.getTotalAmt());
            fallback.setQuantity(BigDecimal.ONE);
            fallback.setItemName(fallback.getChargeName());
            fallback.setAdjusted(false);
            fallback.setIsDeleted(false);
            lines.add(fallback);
        }
        return lines;
    }

    /**
     * 写入明细标准价/数量/金额，满足博思 {@code std * number = amt}。
     *
     * <p>优先用收费项单价作 {@code std}；无单价时退化为 {@code std=金额、number=1}，
     * 避免把行金额误写入标准价导致拒收（如中药饮片按克计价）。</p>
     *
     * @param chargeLine health 收费快照行
     * @param line 待填充的 boss 明细
     */
    private void applyLineAmounts(BossChargeLineSnapshot chargeLine, EinvBossLine line) {
        BigDecimal amount = scale2(chargeLine.getAmount());
        BigDecimal quantity = chargeLine.getQuantity() != null ? chargeLine.getQuantity() : BigDecimal.ONE;
        BigDecimal stdAmount;
        if (chargeLine.getUnitPrice() != null) {
            stdAmount = scale2(chargeLine.getUnitPrice());
        } else {
            stdAmount = amount;
            quantity = BigDecimal.ONE;
        }
        line.setStdAmount(stdAmount);
        line.setAmount(amount);
        line.setQuantity(quantity);
    }

    private static BigDecimal scale2(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /**
     * 经 {@code einv_charge_contrast} 将 health internalCode 映射为博思 chargeCode/chargeName。
     *
     * <p>对照表存住院口径统计项 CODE（YLZY*）；门诊/挂号再换算为老系统 CODE1（YLMZ*），
     * 对齐 areahis 门诊 {@code chargeDetail.chargeCode}。</p>
     *
     * @param tenantId 租户（对照表维度）
     * @param issueKind 票种 REG/AMB/IMP
     * @param chargeLine 快照行
     * @param line 待填充的 boss 明细实体
     */
    private void resolveBossCharge(
            Long tenantId, String issueKind, BossChargeLineSnapshot chargeLine, EinvBossLine line) {
        String internalCode = chargeLine.getInternalCode();
        if (tenantId == null || internalCode == null || internalCode.isBlank()) {
            String fallback = internalCode != null ? internalCode : "UNKNOWN";
            line.setChargeCode(BossLegacyStatisticCode1Mapping.toOutboundChargeCode(issueKind, fallback));
            line.setChargeName(line.getChargeCode());
            return;
        }
        try {
            Specification<EinvChargeContrast> spec = (root, query, cb) -> cb.and(
                    cb.equal(root.get("tenantId"), tenantId),
                    cb.equal(root.get("internalCode"), internalCode),
                    cb.equal(root.get("isDeleted"), Boolean.FALSE));
            Optional<EinvChargeContrast> contrast = contrastRepository.findOne(spec);
            if (contrast.isPresent()) {
                line.setChargeCode(BossLegacyStatisticCode1Mapping.toOutboundChargeCode(
                        issueKind, contrast.get().getBossChargeCode()));
                line.setChargeName(contrast.get().getBossChargeName());
                return;
            }
        } catch (Exception ex) {
            log.warn("Resolve boss charge contrast failed, tenantId={}, internalCode={}", tenantId, internalCode, ex);
        }
        line.setChargeCode(BossLegacyStatisticCode1Mapping.toOutboundChargeCode(issueKind, internalCode));
        String displayName = chargeLine.getItemName();
        line.setChargeName(displayName != null && !displayName.isBlank() ? displayName : internalCode);
    }
}
