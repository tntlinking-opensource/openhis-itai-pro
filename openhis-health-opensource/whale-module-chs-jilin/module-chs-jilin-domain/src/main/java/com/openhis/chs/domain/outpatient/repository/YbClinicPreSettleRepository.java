/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.outpatient.repository;

import com.openhis.chs.domain.outpatient.entity.YbClinicPreSettle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 门诊预结算快照 {@code yb_clinic_pre_settle} 仓储 */
public interface YbClinicPreSettleRepository extends JpaRepository<YbClinicPreSettle, Long> {

    /**
     * 按收费批次号查找预结算快照。
     *
     * @param chrgBchno 收费批次号
     * @param tenantId  租户 ID
     * @return 预结算快照
     */
    Optional<YbClinicPreSettle> findFirstByChrgBchnoAndTenantIdAndIsDeletedFalse(String chrgBchno, Long tenantId);

    /**
     * 按支付草稿 ID 查找最新预结算快照。
     *
     * @param paymentDraftId 支付草稿 ID
     * @param tenantId       租户 ID
     * @return 最新预结算快照
     */
    Optional<YbClinicPreSettle> findFirstByPaymentDraftIdAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
        Long paymentDraftId, Long tenantId);

    /**
     * 列出指定支付草稿与状态下的预结算快照（按创建时间升序）。
     *
     * @param paymentDraftId 支付草稿 ID
     * @param status         快照状态
     * @param tenantId       租户 ID
     * @return 预结算快照列表
     */
    List<YbClinicPreSettle> findAllByPaymentDraftIdAndStatusAndTenantIdAndIsDeletedFalseOrderByCreatedAtAsc(
        Long paymentDraftId, String status, Long tenantId);

    /**
     * 按就诊 ID 集合批量查询预结算快照（按创建时间降序）。
     *
     * @param encounterIds 就诊 ID 集合
     * @param tenantId     租户 ID
     * @return 预结算快照列表
     */
    List<YbClinicPreSettle> findAllByEncounterIdInAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
        Collection<Long> encounterIds, Long tenantId);

    /**
     * 列出指定就诊、契约与状态下的预结算快照（按创建时间升序）。
     *
     * @param encounterId 就诊 ID
     * @param contractId  契约 ID
     * @param status      快照状态
     * @param tenantId    租户 ID
     * @return 预结算快照列表
     */
    List<YbClinicPreSettle> findAllByEncounterIdAndContractIdAndStatusAndTenantIdAndIsDeletedFalseOrderByCreatedAtAsc(
        Long encounterId, Long contractId, String status, Long tenantId);
}
