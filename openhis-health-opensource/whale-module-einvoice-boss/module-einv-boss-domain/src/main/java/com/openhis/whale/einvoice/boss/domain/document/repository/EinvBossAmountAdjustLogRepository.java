/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.domain.document.repository;

import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossAmountAdjustLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 金额调额审计日志仓储（{@code einv_boss_amount_adjust_log}）。
 *
 * <p>人工调额后追加审计记录，按单据倒序查询用于运营追溯。</p>
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
