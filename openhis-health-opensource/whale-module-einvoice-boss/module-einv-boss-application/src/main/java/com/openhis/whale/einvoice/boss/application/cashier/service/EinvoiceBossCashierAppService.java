/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.application.cashier.service;

import com.openhis.health.domain.share.enums.InvoiceStatus;
import com.openhis.whale.einvoice.boss.application.cashier.response.BossCashierQr;
import com.openhis.whale.einvoice.boss.domain.issue.result.BossIssueStatus;
import com.openhis.whale.einvoice.boss.domain.issue.loader.EinvoiceIssueSnapshotContextLoader;
import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossDocument;
import com.openhis.whale.einvoice.boss.domain.document.repository.EinvBossDocumentRepository;
import com.openhis.whale.einvoice.boss.domain.issue.repository.EinvBossIssueTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 收费窗电子票据组件（HTTP：{@code /api/app/einvoiceBossCashier/*}）�?
 *
 * <p>�?paymentId 查询开票进度与二维码，供收银台嵌入展示�?/p>
 */
@Service
@RequiredArgsConstructor
public class EinvoiceBossCashierAppService {

    private final EinvBossDocumentRepository documentRepository;
    private final EinvBossIssueTaskRepository taskRepository;
    private final EinvoiceIssueSnapshotContextLoader issueSnapshotContextLoader;

    /**
     * 按支付主键查询开票进度�?
     *
     * @param paymentId 支付主键
     * @return 开票进�?
     */
    public BossIssueStatus getIssueStatus(Long paymentId) {
        BossIssueStatus dto = new BossIssueStatus();
        Optional<EinvBossDocument> document = documentRepository.findFirstByPaymentIdAndIsDeletedFalseOrderByIdDesc(paymentId);
        if (document.isEmpty()) {
            return dto;
        }
        EinvBossDocument doc = document.get();
        var snapshot = issueSnapshotContextLoader.loadForInvoice(doc.getInvoiceId());
        dto.setInvoiceStatus(snapshot.getInvoiceStatus());
        dto.setAmountStatus(doc.getAmountStatus());
        dto.setBillNo(doc.getBossBillNo());
        dto.setQrReady(doc.getBillQrCode() != null);
        taskRepository.findByInvoiceIdAndIsDeletedFalse(doc.getInvoiceId()).ifPresent(task -> {
            dto.setTaskStatus(task.getStatus());
        });
        return dto;
    }

    /**
     * 按支付主键获�?QR（仅 ISSUED 后有值）�?
     *
     * @param paymentId 支付主键
     * @return QR 信息或空对象
     */
    public BossCashierQr getQr(Long paymentId) {
        BossCashierQr dto = new BossCashierQr();
        Optional<EinvBossDocument> document = documentRepository.findFirstByPaymentIdAndIsDeletedFalseOrderByIdDesc(paymentId);
        if (document.isEmpty()) {
            return dto;
        }
        BossIssueStatus status = getIssueStatus(paymentId);
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
