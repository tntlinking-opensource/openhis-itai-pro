package com.openhis.whale.einvoice.boss.domain.document.repository;

import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossAmountAdjustLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 金额调额审计日志仓储。
 */
public interface EinvBossAmountAdjustLogRepository extends JpaRepository<EinvBossAmountAdjustLog, Long> {

    /**
     * 按单据查询调额记录（时间倒序）。
     *
     * @param documentId 单据主键
     * @return 日志列表
     */
    List<EinvBossAmountAdjustLog> findByDocumentIdOrderByCreatedAtDesc(Long documentId);
}
