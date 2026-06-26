package com.openhis.whale.einvoice.boss.application.common;

import com.openhis.whale.einvoice.boss.contracts.dto.BossIssueSnapshot;
import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossDocument;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 通用票快照构造（不依赖 health adm_invoice）。
 */
@Component
public class BossCommonSnapshotSupport {

    /**
     * 由 boss 单据构造开具快照。
     *
     * @param document 通用票单据
     * @return 快照
     */
    public BossIssueSnapshot build(EinvBossDocument document) {
        BossIssueSnapshot snapshot = new BossIssueSnapshot();
        snapshot.setInvoiceId(document.getInvoiceId());
        snapshot.setPaymentId(document.getPaymentId());
        snapshot.setTenantId(document.getTenantId());
        snapshot.setBusNo(document.getBusNo());
        snapshot.setTotalAmt(document.getTotalAmt());
        snapshot.setPaymentAmt(document.getPaymentAmt());
        snapshot.setPatientName(document.getPayerName());
        snapshot.setPhone(document.getPayerPhone());
        snapshot.setIdCard(document.getPayerIdCard());
        snapshot.setGenderDisplay(document.getPayerSex());
        snapshot.setAge(document.getPayerAge());
        snapshot.setBillDateTime(LocalDateTime.now());
        snapshot.setInvoiceStatus("DRAFT");
        return snapshot;
    }
}
