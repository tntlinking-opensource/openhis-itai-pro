/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.inpatient.repository;

import com.zxplat.plugin.chs.domain.inpatient.entity.YbInpatientPreSettle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 住院医保预结算快照仓储 */
public interface YbInpatientPreSettleRepository extends JpaRepository<YbInpatientPreSettle, Long> {

    /**
     * 按主键与租户查找预结算快照。
     *
     * @param id       预结算快照主键
     * @param tenantId 租户 ID
     * @return 预结算快照
     */
    Optional<YbInpatientPreSettle> findFirstByIdAndTenantIdAndIsDeletedFalse(Long id, Long tenantId);

    /**
     * 按支付草稿 ID 与状态查找最新预结算快照。
     *
     * @param paymentDraftId 支付草稿 ID
     * @param status         快照状态
     * @param tenantId       租户 ID
     * @return 最新预结算快照
     */
    Optional<YbInpatientPreSettle> findFirstByPaymentDraftIdAndStatusAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
        Long paymentDraftId, String status, Long tenantId);

    /**
     * 列出指定支付草稿与状态下的预结算快照（按创建时间升序）。
     *
     * @param paymentDraftId 支付草稿 ID
     * @param status         快照状态
     * @param tenantId       租户 ID
     * @return 预结算快照列表
     */
    List<YbInpatientPreSettle> findAllByPaymentDraftIdAndStatusAndTenantIdAndIsDeletedFalseOrderByCreatedAtAsc(
        Long paymentDraftId, String status, Long tenantId);

    /**
     * 查询指定时间段内已正式结算的住院预结算快照（左闭右开）。
     *
     * @param tenantId     租户 ID
     * @param status       快照状态
     * @param beginAt      起始时间（含）
     * @param endExclusive 结束时间（不含）
     * @return 预结算快照列表
     */
    List<YbInpatientPreSettle> findAllByTenantIdAndIsDeletedFalseAndStatusAndSetlIdIsNotNullAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
        Long tenantId,
        String status,
        LocalDateTime beginAt,
        LocalDateTime endExclusive);

    /**
     * 按就诊 ID 集合批量查询预结算快照（按创建时间降序）。
     *
     * @param encounterIds 就诊 ID 集合
     * @param tenantId     租户 ID
     * @return 预结算快照列表
     */
    List<YbInpatientPreSettle> findAllByEncounterIdInAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
        Collection<Long> encounterIds,
        Long tenantId);
}
