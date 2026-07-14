/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.settlement.service;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

import com.openhis.chs.domain.share.constants.ChsErrorCodes;
import com.openhis.chs.domain.settlement.YbSettlementVoucherStatus;
import com.openhis.chs.domain.settlement.entity.YbSettlementRecord;
import com.openhis.chs.domain.settlement.repository.YbSettlementRecordRepository;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** 结算回执定位（混合结算下一 payment 可对应多笔） */
@Service
@RequiredArgsConstructor
public class YbSettlementRecordQueryService {

    private final YbSettlementRecordRepository settlementRecordRepository;

    /**
     * 按主键与租户查询未删除结算回执。
     *
     * @param id       结算回执主键
     * @param tenantId 租户 ID
     * @return 匹配行；不存在时 empty
     */
    public Optional<YbSettlementRecord> findOptionalByIdAndTenant(Long id, Long tenantId) {
        if (id == null || tenantId == null) {
            return Optional.empty();
        }
        return settlementRecordRepository.findFirstByIdAndTenantIdAndIsDeletedFalse(id, tenantId);
    }

    /**
     * 查询指定时间段内创建的结算回执（左闭右开）。
     *
     * @param tenantId     租户 ID
     * @param beginAt      起始时间（含）
     * @param endExclusive 结束时间（不含）
     * @return 结算回执列表；参数缺失时空列表
     */
    public List<YbSettlementRecord> listCreatedBetween(
            Long tenantId, LocalDateTime beginAt, LocalDateTime endExclusive) {
        if (tenantId == null || beginAt == null || endExclusive == null) {
            return Collections.emptyList();
        }
        return settlementRecordRepository
            .findAllByTenantIdAndIsDeletedFalseAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                tenantId, beginAt, endExclusive);
    }

    /**
     * 按支付单查询关联的全部结算回执（含混合结算多笔）。
     *
     * @param paymentId 支付单 ID
     * @param tenantId  租户 ID
     * @return 按创建时间升序的列表；参数缺失时空列表
     */
    public List<YbSettlementRecord> listByPaymentId(Long paymentId, Long tenantId) {
        if (paymentId == null || tenantId == null) {
            return Collections.emptyList();
        }
        return settlementRecordRepository.findAllByPaymentIdAndTenantIdAndIsDeletedFalseOrderByCreatedAtAsc(
            paymentId, tenantId);
    }

    /**
     * 按就诊与就诊类别查询最新一笔结算回执。
     *
     * @param encounterId    就诊 ID
     * @param encounterClass 就诊类别（AMB/IMP）
     * @param tenantId       租户 ID
     * @return 最新结算回执；参数缺失时 empty
     */
    public Optional<YbSettlementRecord> findOptionalLatestByEncounterAndClass(
            Long encounterId,
            String encounterClass,
            Long tenantId) {
        if (encounterId == null || tenantId == null || isEmpty(encounterClass)) {
            return Optional.empty();
        }
        return settlementRecordRepository
            .findFirstByEncounterIdAndEncounterClassAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
                encounterId, encounterClass.trim(), tenantId);
    }

    /**
     * 按 settlementRecordId、paymentId 或 setlId 唯一定位有效结算回执。
     *
     * @param settlementRecordId 结算回执主键（优先）
     * @param paymentId          支付单 ID（次选，须唯一）
     * @param setlId             平台结算 ID（次选）
     * @param tenantId           租户 ID
     * @return 未撤销的结算回执
     */
    public YbSettlementRecord requireOne(
        Long settlementRecordId,
        Long paymentId,
        String setlId,
        Long tenantId) {
        if (settlementRecordId != null) {
            YbSettlementRecord record = settlementRecordRepository
                .findFirstByIdAndTenantIdAndIsDeletedFalse(settlementRecordId, tenantId)
                .orElseThrow(() -> new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "未找到结算回执"));
            if (YbSettlementVoucherStatus.REVOKED.equals(record.getStatus())) {
                throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "结算回执已撤销");
            }
            return record;
        }
        if (isNotEmpty(setlId)) {
            YbSettlementRecord record = settlementRecordRepository
                .findFirstBySetlIdAndTenantIdAndIsDeletedFalse(setlId.trim(), tenantId)
                .orElseThrow(() -> new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "未找到结算回执"));
            if (YbSettlementVoucherStatus.REVOKED.equals(record.getStatus())) {
                throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "结算回执已撤销");
            }
            return record;
        }
        if (paymentId != null) {
            List<YbSettlementRecord> records = settlementRecordRepository
                .findAllByPaymentIdAndTenantIdAndIsDeletedFalseOrderByCreatedAtAsc(paymentId, tenantId)
                .stream()
                .filter(row -> !YbSettlementVoucherStatus.REVOKED.equals(row.getStatus()))
                .collect(Collectors.toList());
            if (records.isEmpty()) {
                throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "未找到结算回执");
            }
            if (records.size() > 1) {
                throw new BusinessException(
                    ChsErrorCodes.SETTLEMENT_AMBIGUOUS,
                    "同一 payment 存在多笔医保结算，请指定 settlementRecordId 或 setlId");
            }
            return records.get(0);
        }
        throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "缺少 settlementRecordId/paymentId/setlId");
    }
}
