/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.outpatient.service;

import com.openhis.chs.domain.outpatient.entity.YbClinicPreSettle;
import com.openhis.chs.domain.outpatient.repository.YbClinicPreSettleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** 门诊预结算快照只读查询 */
@Service
@RequiredArgsConstructor
public class YbClinicPreSettleQueryService {

    private final YbClinicPreSettleRepository clinicPreSettleRepository;

    /**
     * 列出指定支付草稿下所有 ACTIVE 预结算快照（按创建时间升序）。
     *
     * @param paymentDraftId 支付草稿 ID
     * @param tenantId       租户 ID
     * @return 预结算快照列表；参数无效时返回空列表
     */
    public List<YbClinicPreSettle> listActiveByPaymentDraftId(Long paymentDraftId, Long tenantId) {
        if (paymentDraftId == null || tenantId == null) {
            return Collections.emptyList();
        }
        return clinicPreSettleRepository.findAllByPaymentDraftIdAndStatusAndTenantIdAndIsDeletedFalseOrderByCreatedAtAsc(
            paymentDraftId, YbClinicPreSettle.STATUS_ACTIVE, tenantId);
    }

    /**
     * 按就诊 ID 集合批量查询预结算快照（按创建时间降序）。
     *
     * @param encounterIds 就诊 ID 集合
     * @param tenantId     租户 ID
     * @return 预结算快照列表；参数无效时返回空列表
     */
    public List<YbClinicPreSettle> listByEncounterIds(Collection<Long> encounterIds, Long tenantId) {
        if (encounterIds == null || encounterIds.isEmpty() || tenantId == null) {
            return Collections.emptyList();
        }
        return clinicPreSettleRepository.findAllByEncounterIdInAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
            encounterIds, tenantId);
    }

    /**
     * 列出指定就诊 + 契约下 ACTIVE 预结算快照（重试 2204 前须先 2205）。
     *
     * @param encounterId 就诊 ID
     * @param contractId  契约 ID
     * @param tenantId    租户 ID
     * @return ACTIVE 快照列表；参数无效时返回空列表
     */
    public List<YbClinicPreSettle> listActiveByEncounterIdAndContractId(
        Long encounterId, Long contractId, Long tenantId) {
        if (encounterId == null || contractId == null || tenantId == null) {
            return Collections.emptyList();
        }
        return clinicPreSettleRepository
            .findAllByEncounterIdAndContractIdAndStatusAndTenantIdAndIsDeletedFalseOrderByCreatedAtAsc(
                encounterId, contractId, YbClinicPreSettle.STATUS_ACTIVE, tenantId);
    }

    /**
     * 按支付草稿 ID 查找最新一条预结算快照。
     *
     * @param paymentDraftId 支付草稿 ID
     * @param tenantId       租户 ID
     * @return 最新快照；参数无效或未找到时返回空
     */
    public Optional<YbClinicPreSettle> findOptionalLatestByPaymentDraftId(Long paymentDraftId, Long tenantId) {
        if (paymentDraftId == null || tenantId == null) {
            return Optional.empty();
        }
        return clinicPreSettleRepository.findFirstByPaymentDraftIdAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
            paymentDraftId, tenantId);
    }

    /**
     * 按预结算快照主键查找契约 busNo。
     *
     * @param preSettleId 预结算快照主键
     * @return 契约 busNo；id 为空或未找到时返回空
     */
    public Optional<String> findOptionalContractBusNoById(Long preSettleId) {
        if (preSettleId == null) {
            return Optional.empty();
        }
        return clinicPreSettleRepository.findById(preSettleId).map(YbClinicPreSettle::getContractBusNo);
    }
}
