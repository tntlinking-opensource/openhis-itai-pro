/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.settlement.service;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

import com.openhis.chs.domain.inpatient.entity.YbInpatientPreSettle;
import com.openhis.chs.domain.inpatient.repository.YbInpatientPreSettleRepository;
import com.openhis.chs.domain.outpatient.entity.YbClinicPreSettle;
import com.openhis.chs.domain.outpatient.repository.YbClinicPreSettleRepository;
import com.openhis.chs.domain.share.constants.ChsErrorCodes;
import com.openhis.chs.domain.settlement.YbSettlementVoucherStatus;
import com.openhis.chs.domain.settlement.constants.ChsSettleEncounterClass;
import com.openhis.chs.domain.settlement.entity.YbSettlementRecord;
import com.openhis.chs.domain.settlement.repository.YbSettlementRecordRepository;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** 结算回执定位（混合结算下一 payment 可对应多笔） */
@Service
@RequiredArgsConstructor
public class YbSettlementRecordQueryService {

    private final YbSettlementRecordRepository settlementRecordRepository;

    private final YbClinicPreSettleRepository clinicPreSettleRepository;

    private final YbInpatientPreSettleRepository inpatientPreSettleRepository;

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
     * 按主键集合批量装载未删除结算回执（对账撤销行回查险种/清算类别）。
     *
     * @param ids      结算回执主键集合
     * @param tenantId 租户 ID
     * @return id → 回执；参数缺失或未命中时空 map
     */
    public Map<Long, YbSettlementRecord> mapByIds(Collection<Long> ids, Long tenantId) {
        if (ids == null || ids.isEmpty() || tenantId == null) {
            return Collections.emptyMap();
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id != null) {
                unique.add(id);
            }
        }
        if (unique.isEmpty()) {
            return Collections.emptyMap();
        }
        List<YbSettlementRecord> rows =
            settlementRecordRepository.findAllByIdInAndTenantIdAndIsDeletedFalse(unique, tenantId);
        Map<Long, YbSettlementRecord> out = new HashMap<>(rows.size() * 2);
        for (YbSettlementRecord row : rows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            out.put(row.getId(), row);
        }
        return out;
    }

    /**
     * 按主键集合批量解析险种类型（日结下半区 insutype 分桶）。
     *
     * <p>优先取结算回执自身 {@code insutype}；为空时回落关联预结算快照（历史 2207/2304 未回写场景）。</p>
     *
     * @param ids      结算回执主键集合
     * @param tenantId 租户 ID
     * @return id → insutype；参数缺失或未命中时空 map
     */
    public Map<Long, String> mapInsutypeByIds(Collection<Long> ids, Long tenantId) {
        if (ids == null || ids.isEmpty() || tenantId == null) {
            return Collections.emptyMap();
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id != null) {
                unique.add(id);
            }
        }
        if (unique.isEmpty()) {
            return Collections.emptyMap();
        }
        List<YbSettlementRecord> rows =
            settlementRecordRepository.findAllByIdInAndTenantIdAndIsDeletedFalse(unique, tenantId);
        Map<Long, String> out = new HashMap<>(rows.size() * 2);
        List<YbSettlementRecord> needPreFallback = new ArrayList<>();
        for (YbSettlementRecord row : rows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            if (isNotEmpty(row.getInsutype())) {
                out.put(row.getId(), row.getInsutype().trim());
                continue;
            }
            if (row.getPreSettleId() != null) {
                needPreFallback.add(row);
            }
        }
        if (needPreFallback.isEmpty()) {
            return out;
        }
        LinkedHashSet<Long> clinicPreIds = new LinkedHashSet<>();
        LinkedHashSet<Long> inpatientPreIds = new LinkedHashSet<>();
        for (YbSettlementRecord row : needPreFallback) {
            if (ChsSettleEncounterClass.IMP.equals(row.getEncounterClass())) {
                inpatientPreIds.add(row.getPreSettleId());
            } else {
                clinicPreIds.add(row.getPreSettleId());
            }
        }
        Map<Long, String> clinicInsutypeById = this.mapClinicPreInsutypeByIds(clinicPreIds, tenantId);
        Map<Long, String> inpatientInsutypeById = this.mapInpatientPreInsutypeByIds(inpatientPreIds, tenantId);
        for (YbSettlementRecord row : needPreFallback) {
            String insutype;
            if (ChsSettleEncounterClass.IMP.equals(row.getEncounterClass())) {
                insutype = inpatientInsutypeById.get(row.getPreSettleId());
            } else {
                insutype = clinicInsutypeById.get(row.getPreSettleId());
            }
            if (isNotEmpty(insutype)) {
                out.put(row.getId(), insutype.trim());
            }
        }
        return out;
    }

    /**
     * 门诊预结算 id → insutype。
     */
    private Map<Long, String> mapClinicPreInsutypeByIds(Collection<Long> preIds, Long tenantId) {
        if (preIds == null || preIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> out = new HashMap<>();
        for (YbClinicPreSettle pre : clinicPreSettleRepository.findAllById(preIds)) {
            if (pre == null || pre.getId() == null || Boolean.TRUE.equals(pre.getIsDeleted())) {
                continue;
            }
            if (tenantId != null && !tenantId.equals(pre.getTenantId())) {
                continue;
            }
            if (isEmpty(pre.getInsutype())) {
                continue;
            }
            out.put(pre.getId(), pre.getInsutype().trim());
        }
        return out;
    }

    /**
     * 住院预结算 id → insutype。
     */
    private Map<Long, String> mapInpatientPreInsutypeByIds(Collection<Long> preIds, Long tenantId) {
        if (preIds == null || preIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> out = new HashMap<>();
        for (YbInpatientPreSettle pre : inpatientPreSettleRepository.findAllById(preIds)) {
            if (pre == null || pre.getId() == null || Boolean.TRUE.equals(pre.getIsDeleted())) {
                continue;
            }
            if (tenantId != null && !tenantId.equals(pre.getTenantId())) {
                continue;
            }
            if (isEmpty(pre.getInsutype())) {
                continue;
            }
            out.put(pre.getId(), pre.getInsutype().trim());
        }
        return out;
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
     * 按支付单集合批量解析险种类型（日结：桥表缺 settlement_record_id 时回落）。
     *
     * <p>每个 payment 取创建最早且能解析出非空 {@code insutype} 的回执；口径同 {@link #mapInsutypeByIds}。</p>
     *
     * @param paymentIds 支付单主键集合
     * @param tenantId   租户 ID
     * @return paymentId → insutype；未命中不包含该 key
     */
    public Map<Long, String> mapInsutypeByPaymentIds(Collection<Long> paymentIds, Long tenantId) {
        if (paymentIds == null || paymentIds.isEmpty() || tenantId == null) {
            return Collections.emptyMap();
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long paymentId : paymentIds) {
            if (paymentId != null) {
                unique.add(paymentId);
            }
        }
        if (unique.isEmpty()) {
            return Collections.emptyMap();
        }
        List<YbSettlementRecord> rows =
            settlementRecordRepository.findAllByPaymentIdInAndTenantIdAndIsDeletedFalseOrderByCreatedAtAsc(
                unique, tenantId);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyMap();
        }
        LinkedHashSet<Long> recordIds = new LinkedHashSet<>();
        for (YbSettlementRecord row : rows) {
            if (row != null && row.getId() != null) {
                recordIds.add(row.getId());
            }
        }
        Map<Long, String> byRecordId = this.mapInsutypeByIds(recordIds, tenantId);
        Map<Long, String> out = new HashMap<>();
        for (YbSettlementRecord row : rows) {
            if (row == null || row.getPaymentId() == null || row.getId() == null) {
                continue;
            }
            // 已解析过该支付单：保留最早命中
            if (out.containsKey(row.getPaymentId())) {
                continue;
            }
            String insutype = byRecordId.get(row.getId());
            if (isNotEmpty(insutype)) {
                out.put(row.getPaymentId(), insutype.trim());
            }
        }
        return out;
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

    /**
     * 撤销场景定位结算回执：允许已 {@code REVOKED}，供 2208/2305 Invoker 幂等短路。
     *
     * <p>与 {@link #requireOne} 的差异：按 id/setlId 命中已撤销行时不抛错；按 paymentId
     * 无未撤销行时回退到最新已撤销行（全部已撤则幂等成功）。</p>
     *
     * @param settlementRecordId 结算回执主键（优先）
     * @param paymentId          支付单 ID（次选）
     * @param setlId             平台结算 ID（次选）
     * @param tenantId           租户 ID
     * @return 结算回执（可能已 REVOKED）
     */
    public YbSettlementRecord requireOneAllowRevoked(
        Long settlementRecordId,
        Long paymentId,
        String setlId,
        Long tenantId) {
        if (settlementRecordId != null) {
            return settlementRecordRepository
                .findFirstByIdAndTenantIdAndIsDeletedFalse(settlementRecordId, tenantId)
                .orElseThrow(() -> new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "未找到结算回执"));
        }
        if (isNotEmpty(setlId)) {
            return settlementRecordRepository
                .findFirstBySetlIdAndTenantIdAndIsDeletedFalse(setlId.trim(), tenantId)
                .orElseThrow(() -> new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "未找到结算回执"));
        }
        if (paymentId != null) {
            List<YbSettlementRecord> all = settlementRecordRepository
                .findAllByPaymentIdAndTenantIdAndIsDeletedFalseOrderByCreatedAtAsc(paymentId, tenantId);
            List<YbSettlementRecord> active = all.stream()
                .filter(row -> !YbSettlementVoucherStatus.REVOKED.equals(row.getStatus()))
                .collect(Collectors.toList());
            if (active.size() == 1) {
                return active.get(0);
            }
            if (active.size() > 1) {
                throw new BusinessException(
                    ChsErrorCodes.SETTLEMENT_AMBIGUOUS,
                    "同一 payment 存在多笔医保结算，请指定 settlementRecordId 或 setlId");
            }
            if (!all.isEmpty()) {
                return all.get(all.size() - 1);
            }
            throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "未找到结算回执");
        }
        throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "缺少 settlementRecordId/paymentId/setlId");
    }
}
