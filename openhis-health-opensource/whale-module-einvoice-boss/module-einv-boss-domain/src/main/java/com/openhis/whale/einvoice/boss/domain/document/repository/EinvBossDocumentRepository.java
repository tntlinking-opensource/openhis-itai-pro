/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.domain.document.repository;

import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 博思电子票据单据仓储（{@code einv_boss_document}）。
 *
 * <p>除 JPA 常规 CRUD 外，提供按 health 主键/支付/流水号查询及
 * {@link #claimWriteOffProcessing} 冲红原子抢占。</p>
 */
public interface EinvBossDocumentRepository extends JpaRepository<EinvBossDocument, Long>, JpaSpecificationExecutor<EinvBossDocument> {

    /**
     * 按 health 票据主键查询未删除单据。
     *
     * @param invoiceId 票据主键
     * @return 单据（若存在）
     */
    Optional<EinvBossDocument> findByInvoiceIdAndIsDeletedFalse(Long invoiceId);

    /**
     * 按支付主键查询单据。
     *
     * @param paymentId 支付主键
     * @return 单据
     */
    Optional<EinvBossDocument> findByPaymentIdAndIsDeletedFalse(Long paymentId);

    /**
     * 按业务流水号查询单据。
     *
     * @param busNo 业务流水号
     * @return 单据
     */
    Optional<EinvBossDocument> findByBusNoAndIsDeletedFalse(String busNo);

    /**
     * 原子抢占冲红（仅 NULL/FAILED 可进入 PROCESSING）。
     *
     * @param documentId 单据主键
     * @param processingStatus 处理中状态码
     * @param now 更新时间
     * @return 更新行数
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE EinvBossDocument d
            SET d.writeOffStatus = :processingStatus, d.updatedAt = :now
            WHERE d.id = :documentId
              AND d.isDeleted = false
              AND (d.writeOffStatus IS NULL OR d.writeOffStatus = 'FAILED')
            """)
    int claimWriteOffProcessing(
            @Param("documentId") Long documentId,
            @Param("processingStatus") String processingStatus,
            @Param("now") LocalDateTime now);
}
