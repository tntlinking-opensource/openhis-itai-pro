/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.invoke.repository;

import com.openhis.chs.domain.invoke.entity.YbInvokeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

/** {@link YbInvokeLog} 仓储 */
public interface YbInvokeLogRepository
    extends JpaRepository<YbInvokeLog, Long>, JpaSpecificationExecutor<YbInvokeLog> {

    /**
     * 按支付单查询 invoke 日志，按创建时间倒序。
     *
     * @param paymentDraftId 支付草稿/支付单 ID
     * @param tenantId       租户 ID
     */
    List<YbInvokeLog> findAllByPaymentDraftIdAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
        Long paymentDraftId, Long tenantId);

    /**
     * 按结算回执查询 invoke 日志，按创建时间倒序。
     *
     * @param settlementRecordId 结算回执主键
     * @param tenantId           租户 ID
     */
    List<YbInvokeLog> findAllBySettlementRecordIdAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
        Long settlementRecordId, Long tenantId);

    /**
     * 按就诊与交易号查询 invoke 日志，按创建时间倒序（用于悬挂 2204 孤儿批次回收）。
     *
     * @param encounterId 就诊 ID
     * @param infno       交易号（如 2204/2207）
     * @param tenantId    租户 ID
     * @return 调用日志列表
     */
    List<YbInvokeLog> findAllByEncounterIdAndInfnoAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
        Long encounterId, String infno, Long tenantId);
}
