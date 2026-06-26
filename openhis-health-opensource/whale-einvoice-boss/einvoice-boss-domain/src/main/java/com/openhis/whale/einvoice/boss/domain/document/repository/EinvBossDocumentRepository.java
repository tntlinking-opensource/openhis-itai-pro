package com.openhis.whale.einvoice.boss.domain.document.repository;

import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * 博思电子票据单据仓储。
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
}
