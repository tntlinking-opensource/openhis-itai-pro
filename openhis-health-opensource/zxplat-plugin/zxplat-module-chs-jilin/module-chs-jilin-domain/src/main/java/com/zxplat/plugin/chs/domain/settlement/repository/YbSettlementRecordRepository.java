/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.settlement.repository;

import com.zxplat.plugin.chs.domain.settlement.entity.YbSettlementRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 医保正式结算回执仓储（门诊/住院共用） */
public interface YbSettlementRecordRepository
    extends JpaRepository<YbSettlementRecord, Long>, JpaSpecificationExecutor<YbSettlementRecord> {

    /**
     * 按主键与租户查询未删除结算回执。
     *
     * @param id       结算回执主键
     * @param tenantId 租户 ID
     */
    Optional<YbSettlementRecord> findFirstByIdAndTenantIdAndIsDeletedFalse(Long id, Long tenantId);

    /**
     * 按支付单查询最新一笔未删除结算回执。
     *
     * @param paymentId 支付单 ID
     * @param tenantId  租户 ID
     */
    Optional<YbSettlementRecord> findFirstByPaymentIdAndTenantIdAndIsDeletedFalse(
        Long paymentId, Long tenantId);

    /**
     * 按支付单查询全部未删除结算回执，按创建时间升序。
     *
     * @param paymentId 支付单 ID
     * @param tenantId  租户 ID
     */
    List<YbSettlementRecord> findAllByPaymentIdAndTenantIdAndIsDeletedFalseOrderByCreatedAtAsc(
        Long paymentId, Long tenantId);

    /**
     * 按支付单集合批量查询未删除结算回执，按创建时间升序（日结险种回落）。
     *
     * @param paymentIds 支付单 ID 集合
     * @param tenantId   租户 ID
     */
    List<YbSettlementRecord> findAllByPaymentIdInAndTenantIdAndIsDeletedFalseOrderByCreatedAtAsc(
        Collection<Long> paymentIds, Long tenantId);

    /**
     * 按平台结算 ID 查询未删除结算回执。
     *
     * @param setlId   平台结算 ID
     * @param tenantId 租户 ID
     */
    Optional<YbSettlementRecord> findFirstBySetlIdAndTenantIdAndIsDeletedFalse(String setlId, Long tenantId);

    /**
     * 查询指定时间段内创建的未删除结算回执（左闭右开）。
     *
     * @param tenantId     租户 ID
     * @param beginAt      起始时间（含）
     * @param endExclusive 结束时间（不含）
     */
    List<YbSettlementRecord> findAllByTenantIdAndIsDeletedFalseAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
        Long tenantId,
        LocalDateTime beginAt,
        LocalDateTime endExclusive);

    /**
     * 按平台结算时间 {@code setl_time} 查询未删除结算回执（左闭右开）。
     *
     * @param tenantId     租户 ID
     * @param beginAt      起始时间（含）
     * @param endExclusive 结束时间（不含）
     */
    List<YbSettlementRecord> findAllByTenantIdAndIsDeletedFalseAndSetlTimeGreaterThanEqualAndSetlTimeLessThan(
        Long tenantId,
        LocalDateTime beginAt,
        LocalDateTime endExclusive);

    /**
     * {@code setl_time} 为空时回落创建时间窗（左闭右开），供对账日期口径补齐。
     *
     * @param tenantId     租户 ID
     * @param beginAt      起始时间（含）
     * @param endExclusive 结束时间（不含）
     */
    List<YbSettlementRecord>
        findAllByTenantIdAndIsDeletedFalseAndSetlTimeIsNullAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Long tenantId,
            LocalDateTime beginAt,
            LocalDateTime endExclusive);

    /**
     * 按合同 + 结算模式 + {@code setl_time} 查询（工伤对账日汇总，避免拉全量医保结算）。
     */
    List<YbSettlementRecord>
        findAllByTenantIdAndContractIdAndSettleModeAndIsDeletedFalseAndSetlTimeGreaterThanEqualAndSetlTimeLessThan(
            Long tenantId,
            Long contractId,
            String settleMode,
            LocalDateTime beginAt,
            LocalDateTime endExclusive);

    /**
     * 按合同 + 结算模式 + 空 {@code setl_time} 回落创建时间窗。
     */
    List<YbSettlementRecord>
        findAllByTenantIdAndContractIdAndSettleModeAndIsDeletedFalseAndSetlTimeIsNullAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Long tenantId,
            Long contractId,
            String settleMode,
            LocalDateTime beginAt,
            LocalDateTime endExclusive);

    /**
     * 按就诊查询最新一笔未删除结算回执。
     *
     * @param encounterId 就诊 ID
     * @param tenantId    租户 ID
     */
    Optional<YbSettlementRecord> findFirstByEncounterIdAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
        Long encounterId, Long tenantId);

    /**
     * 按就诊与就诊类别查询最新一笔未删除结算回执。
     *
     * @param encounterId    就诊 ID
     * @param encounterClass 就诊类别
     * @param tenantId       租户 ID
     */
    Optional<YbSettlementRecord> findFirstByEncounterIdAndEncounterClassAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
        Long encounterId, String encounterClass, Long tenantId);

    /**
     * 按预结算 ID 查询未删除结算回执。
     *
     * @param preSettleId 预结算 ID
     * @param tenantId    租户 ID
     */
    Optional<YbSettlementRecord> findFirstByPreSettleIdAndTenantIdAndIsDeletedFalse(
        Long preSettleId, Long tenantId);

    /**
     * 按主键集合与租户批量查询未删除结算回执。
     *
     * @param ids      结算回执主键集合
     * @param tenantId 租户 ID
     */
    List<YbSettlementRecord> findAllByIdInAndTenantIdAndIsDeletedFalse(Collection<Long> ids, Long tenantId);

    /**
     * 按平台结算号集合与租户批量查询未删除结算回执（3202 结果行补姓名/时间）。
     *
     * @param setlIds  平台 setl_id 集合
     * @param tenantId 租户 ID
     */
    List<YbSettlementRecord> findAllBySetlIdInAndTenantIdAndIsDeletedFalse(
        Collection<String> setlIds, Long tenantId);
}
