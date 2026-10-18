/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.inpatient.service;

import com.zxplat.plugin.chs.domain.inpatient.entity.YbInpatientPreSettle;
import com.zxplat.plugin.chs.domain.inpatient.repository.YbInpatientPreSettleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** 住院预结算快照只读查询 */
@Service
@RequiredArgsConstructor
public class YbInpatientPreSettleQueryService {

    private final YbInpatientPreSettleRepository inpatientPreSettleRepository;

    /**
     * 查询指定时间段内已正式结算的住院预结算快照（左闭右开）。
     *
     * @param tenantId     租户 ID
     * @param beginAt      起始时间（含）
     * @param endExclusive 结束时间（不含）
     * @return 预结算快照列表；参数无效时返回空列表
     */
    public List<YbInpatientPreSettle> listSettledBetween(
            Long tenantId, LocalDateTime beginAt, LocalDateTime endExclusive) {
        if (tenantId == null || beginAt == null || endExclusive == null) {
            return Collections.emptyList();
        }
        return inpatientPreSettleRepository
            .findAllByTenantIdAndIsDeletedFalseAndStatusAndSetlIdIsNotNullAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                tenantId, YbInpatientPreSettle.STATUS_SETTLED, beginAt, endExclusive);
    }

    /**
     * 列出指定支付草稿下所有 ACTIVE 预结算快照（按创建时间升序）。
     *
     * @param paymentDraftId 支付草稿 ID
     * @param tenantId       租户 ID
     * @return 预结算快照列表；参数无效时返回空列表
     */
    public List<YbInpatientPreSettle> listActiveByPaymentDraftId(Long paymentDraftId, Long tenantId) {
        if (paymentDraftId == null || tenantId == null) {
            return Collections.emptyList();
        }
        return inpatientPreSettleRepository
            .findAllByPaymentDraftIdAndStatusAndTenantIdAndIsDeletedFalseOrderByCreatedAtAsc(
                paymentDraftId, YbInpatientPreSettle.STATUS_ACTIVE, tenantId);
    }

    /**
     * 按支付草稿 ID 查找最新一条 SETTLED 预结算快照。
     *
     * @param paymentDraftId 支付草稿 ID
     * @param tenantId       租户 ID
     * @return 已结算快照；参数无效或未找到时返回空
     */
    public Optional<YbInpatientPreSettle> findOptionalSettledByPaymentDraftId(Long paymentDraftId, Long tenantId) {
        if (paymentDraftId == null || tenantId == null) {
            return Optional.empty();
        }
        return inpatientPreSettleRepository
            .findFirstByPaymentDraftIdAndStatusAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
                paymentDraftId, YbInpatientPreSettle.STATUS_SETTLED, tenantId);
    }

    /**
     * 按就诊 ID 集合批量查询预结算快照（按创建时间降序）。
     *
     * @param encounterIds 就诊 ID 集合
     * @param tenantId     租户 ID
     * @return 预结算快照列表；参数无效时返回空列表
     */
    public List<YbInpatientPreSettle> listByEncounterIds(Collection<Long> encounterIds, Long tenantId) {
        if (encounterIds == null || encounterIds.isEmpty() || tenantId == null) {
            return Collections.emptyList();
        }
        return inpatientPreSettleRepository
            .findAllByEncounterIdInAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(encounterIds, tenantId);
    }
}
