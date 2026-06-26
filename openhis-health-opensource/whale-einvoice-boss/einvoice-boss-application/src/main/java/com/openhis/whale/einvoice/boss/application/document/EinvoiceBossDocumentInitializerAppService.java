package com.openhis.whale.einvoice.boss.application.document;

import com.openhis.whale.einvoice.boss.application.boss.BossPayloadBuilder;
import com.openhis.whale.einvoice.boss.application.config.EinvoiceBossProperties;
import com.openhis.whale.einvoice.boss.contracts.dto.BossChargeLineSnapshot;
import com.openhis.whale.einvoice.boss.contracts.dto.BossIssueSnapshot;
import com.openhis.whale.einvoice.boss.contracts.enums.AmountStatus;
import com.openhis.whale.einvoice.boss.contracts.reader.HealthInvoiceSnapshotReader;
import com.openhis.whale.einvoice.boss.domain.contrast.entity.EinvChargeContrast;
import com.openhis.whale.einvoice.boss.domain.contrast.repository.EinvChargeContrastRepository;
import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossDocument;
import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossLine;
import com.openhis.whale.einvoice.boss.domain.document.repository.EinvBossDocumentRepository;
import com.openhis.whale.einvoice.boss.domain.document.repository.EinvBossLineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 从 health 快照初始化 einv_boss_document 与 einv_boss_line。
 * <p>由 {@link com.openhis.whale.core.infrastructure.transaction.AppServiceAspect} 管理 UoW。</p>
 */
@Service
@RequiredArgsConstructor
public class EinvoiceBossDocumentInitializerAppService {

    private final HealthInvoiceSnapshotReader healthInvoiceSnapshotReader;
    private final EinvBossDocumentRepository documentRepository;
    private final EinvBossLineRepository lineRepository;
    private final EinvChargeContrastRepository contrastRepository;
    private final BossPayloadBuilder payloadBuilder;
    private final EinvoiceBossProperties properties;

    /**
     * 初始化或刷新单据与明细。
     *
     * @param invoiceId 票据主键
     * @return 单据实体
     */
    public EinvBossDocument initialize(Long invoiceId) {
        BossIssueSnapshot snapshot = healthInvoiceSnapshotReader.loadForInvoice(invoiceId);
        Optional<EinvBossDocument> existing = documentRepository.findByInvoiceIdAndIsDeletedFalse(invoiceId);
        EinvBossDocument document;
        if (existing.isPresent()) {
            document = existing.get();
        } else {
            document = new EinvBossDocument();
            document.setInvoiceId(invoiceId);
            document.setIsDeleted(false);
        }
        String issueKind = payloadBuilder.resolveIssueKind(snapshot.getInvoiceTypeCode());
        document.setTenantId(snapshot.getTenantId());
        document.setPaymentId(snapshot.getPaymentId());
        document.setIssueKind(issueKind);
        document.setBusNo(snapshot.getBusNo());
        document.setBusType(payloadBuilder.resolveBusType(issueKind));
        document.setTotalAmt(snapshot.getTotalAmt());
        document.setPaymentAmt(snapshot.getPaymentAmt());
        document.setAmountStatus(AmountStatus.MATCH.getCode());
        document.setPlatformProfile(properties.getPlatform().getProfile());
        document = documentRepository.save(document);

        List<EinvBossLine> persistedLines = rebuildLines(document, snapshot);
        lineRepository.saveAll(persistedLines);
        return document;
    }

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
            resolveBossCharge(document.getTenantId(), chargeLine, line);
            line.setStdAmount(chargeLine.getAmount());
            line.setAmount(chargeLine.getAmount());
            line.setQuantity(chargeLine.getQuantity() != null ? chargeLine.getQuantity() : BigDecimal.ONE);
            line.setUnit(chargeLine.getUnit());
            line.setSourceChargeItemId(chargeLine.getChargeItemId());
            line.setAdjusted(false);
            line.setIsDeleted(false);
            lines.add(line);
        }
        if (lines.isEmpty()) {
            EinvBossLine fallback = new EinvBossLine();
            fallback.setTenantId(document.getTenantId());
            fallback.setDocumentId(document.getId());
            fallback.setLineType("CHG");
            fallback.setLineNo(1);
            fallback.setChargeCode("REG_FEE");
            fallback.setChargeName("挂号费");
            fallback.setStdAmount(document.getTotalAmt());
            fallback.setAmount(document.getTotalAmt());
            fallback.setQuantity(BigDecimal.ONE);
            fallback.setAdjusted(false);
            fallback.setIsDeleted(false);
            lines.add(fallback);
        }
        return lines;
    }

    private void resolveBossCharge(Long tenantId, BossChargeLineSnapshot chargeLine, EinvBossLine line) {
        String internalCode = chargeLine.getInternalCode();
        Optional<EinvChargeContrast> contrast = contrastRepository
                .findByTenantIdAndInternalCodeAndIsDeletedFalse(tenantId, internalCode);
        if (contrast.isPresent()) {
            line.setChargeCode(contrast.get().getBossChargeCode());
            line.setChargeName(contrast.get().getBossChargeName());
        } else {
            line.setChargeCode(internalCode);
            line.setChargeName(internalCode);
        }
    }
}
