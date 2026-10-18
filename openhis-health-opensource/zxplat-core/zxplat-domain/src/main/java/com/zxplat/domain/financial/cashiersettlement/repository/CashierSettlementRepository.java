/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.financial.cashiersettlement.repository;

import com.zxplat.domain.financial.cashiersettlement.entity.CashierSettlement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 收费员交账头仓储。
 */
public interface CashierSettlementRepository
    extends JpaRepository<CashierSettlement, Long>, JpaSpecificationExecutor<CashierSettlement> {

    /**
     * 按类型集合列出未软删交账头（药房售价合计回刷等一次性批处理）。
     *
     * @param typeEnums 类型 code 集合（如 OP_PHARMACY / IP_PHARMACY）
     * @return 交账头列表；无匹配时空列表
     */
    List<CashierSettlement> findByTypeEnumInAndIsDeletedFalse(Collection<String> typeEnums);

    /**
     * 判断租户下交账单号是否已存在。
     *
     * @param busNo    交账单号
     * @param tenantId 租户标识
     * @return 是否存在
     */
    boolean existsByBusNoAndTenantId(String busNo, Long tenantId);

    /**
     * 交账历史分页：按类型 + 提交时刻窗。
     *
     * @param typeEnum      OP_PAYMENT / IP_PAYMENT / OP_PHARMACY / IP_PHARMACY
     * @param submittedFrom 提交时刻起（含）
     * @param submittedTo   提交时刻止（含）
     * @param pageable      分页
     * @return 历史页
     */
    Page<CashierSettlement> findByTypeEnumAndSubmittedAtBetweenAndIsDeletedFalseOrderBySubmittedAtDesc(
        String typeEnum,
        LocalDateTime submittedFrom,
        LocalDateTime submittedTo,
        Pageable pageable);
}
