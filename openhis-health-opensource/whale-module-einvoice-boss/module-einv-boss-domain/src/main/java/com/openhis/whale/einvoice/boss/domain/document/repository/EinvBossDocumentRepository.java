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
import java.util.List;
import java.util.Optional;

/**
 * 博思电子票据单据仓储（{@code einv_boss_document}）。
 *
 * <p>除 JPA 常规 CRUD 外，提供按 health 主键/支付/流水号查询及
 * {@link #claimWriteOffProcessing} 冲红原子抢占。拆票后续票与主票共享 invoiceId，
 * 单条查询一律用 {@code findFirst*OrderByIdAsc} 取主票（最早创建）。</p>
 */
public interface EinvBossDocumentRepository extends JpaRepository<EinvBossDocument, Long>, JpaSpecificationExecutor<EinvBossDocument> {

    /**
     * 按 health 票据主键取主单据（拆票时取最早一条=主票）。
     *
     * @param invoiceId 票据主键
     * @return 主单据（若存在）
     */
    Optional<EinvBossDocument> findFirstByInvoiceIdAndIsDeletedFalseOrderByIdAsc(Long invoiceId);

    /**
     * 按 health 票据主键查询全部未删除单据（含拆票续票）。
     *
     * @param invoiceId 票据主键
     * @return 按 id 升序
     */
    List<EinvBossDocument> findAllByInvoiceIdAndIsDeletedFalseOrderByIdAsc(Long invoiceId);

    /**
     * 按支付主键取主单据。
     *
     * @param paymentId 支付主键
     * @return 主单据
     */
    Optional<EinvBossDocument> findFirstByPaymentIdAndIsDeletedFalseOrderByIdAsc(Long paymentId);

    /**
     * 按支付主键取最新单据（冲销重开后新票 id 更大）。
     *
     * @param paymentId 支付主键
     * @return 最新单据
     */
    Optional<EinvBossDocument> findFirstByPaymentIdAndIsDeletedFalseOrderByIdDesc(Long paymentId);

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
