/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.domain.repository;

import com.openhis.wi.domain.entity.YbWiPreSettle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** {@link YbWiPreSettle} 仓储。 */
public interface YbWiPreSettleRepository extends JpaRepository<YbWiPreSettle, Long> {

    /**
     * 按就诊 + 收银草稿查最新 ACTIVE 预结算快照（2207 取 mdtrt_id）。
     *
     * @param encounterId    就诊 id
     * @param paymentDraftId 收银草稿 id
     * @param status         预结算状态（ACTIVE）
     * @param tenantId       租户 id
     * @return 最新一行
     */
    Optional<YbWiPreSettle> findFirstByEncounterIdAndPaymentDraftIdAndStatusAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
            Long encounterId, Long paymentDraftId, String status, Long tenantId);

    /**
     * 按就诊查最新 ACTIVE 预结算快照。
     *
     * @param encounterId 就诊 id
     * @param status      预结算状态
     * @param tenantId    租户 id
     * @return 最新一行
     */
    Optional<YbWiPreSettle> findFirstByEncounterIdAndStatusAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
            Long encounterId, String status, Long tenantId);

    /**
     * 按收银草稿查最新预结算快照。
     *
     * @param paymentDraftId 收银草稿 id
     * @param status         预结算状态
     * @param tenantId       租户 id
     * @return 最新一行
     */
    Optional<YbWiPreSettle> findFirstByPaymentDraftIdAndStatusAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
            Long paymentDraftId, String status, Long tenantId);
}
