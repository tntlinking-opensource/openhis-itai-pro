package com.openhis.whale.einvoice.boss.application.cashier;

import com.openhis.health.domain.share.enums.InvoiceStatus;
import com.openhis.whale.einvoice.boss.application.dto.BossCashierQrDto;
import com.openhis.whale.einvoice.boss.contracts.dto.BossIssueStatusDto;
import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossDocument;
import com.openhis.whale.einvoice.boss.domain.document.repository.EinvBossDocumentRepository;
import com.openhis.whale.einvoice.boss.application.query.EinvoiceBossQueryAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 收费窗电子票据读服务（P1）。
 */
@Service
@RequiredArgsConstructor
public class EinvoiceBossCashierAppService {

    private final EinvBossDocumentRepository documentRepository;
    private final EinvoiceBossQueryAppService queryAppService;

    /**
     * 按支付主键查询开票进度。
     *
     * @param paymentId 支付主键
     * @return 进度
     */
    public BossIssueStatusDto getIssueStatus(Long paymentId) {
        return queryAppService.getIssueStatus(paymentId);
    }

    /**
     * 按支付主键获取 QR（仅 ISSUED 后有值）。
     *
     * @param paymentId 支付主键
     * @return QR 信息或空对象
     */
    public BossCashierQrDto getQr(Long paymentId) {
        BossCashierQrDto dto = new BossCashierQrDto();
        Optional<EinvBossDocument> document = documentRepository.findByPaymentIdAndIsDeletedFalse(paymentId);
        if (document.isEmpty()) {
            return dto;
        }
        BossIssueStatusDto status = queryAppService.getIssueStatus(paymentId);
        if (!InvoiceStatus.ISSUED.getCode().equals(status.getInvoiceStatus())) {
            return dto;
        }
        EinvBossDocument doc = document.get();
        dto.setBillNo(doc.getBossBillNo());
        dto.setBillQrCode(doc.getBillQrCode());
        dto.setPictureUrl(doc.getPictureUrl());
        dto.setPictureNetUrl(doc.getPictureNetUrl());
        return dto;
    }
}
