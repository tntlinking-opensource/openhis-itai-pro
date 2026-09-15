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
import com.openhis.chs.domain.settlement.queries.InsuranceFeeDetailAmountSummary;
import com.openhis.chs.domain.settlement.queries.InsuranceFeeDetailPageCriteria;
import com.openhis.chs.domain.settlement.repository.YbSettlementRecordRepository;
import com.openhis.whale.core.application.exception.BusinessException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** 结算回执定位（混合结算下一 payment 可对应多笔）；仅本聚合 Repository + Criteria。 */
@Service
@RequiredArgsConstructor
public class YbSettlementRecordQueryService {

    private final YbSettlementRecordRepository settlementRecordRepository;

    private final YbClinicPreSettleRepository clinicPreSettleRepository;

    private final YbInpatientPreSettleRepository inpatientPreSettleRepository;

    /** Criteria 合计（SQL SUM）专用；不用于写路径。 */
    @PersistenceContext
    private EntityManager entityManager;

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
     * 按平台结算号批量装载未删除结算回执（3202 明细结果补姓名/结算时间）。
     *
     * @param setlIds  平台 setl_id 集合
     * @param tenantId 租户 ID
     * @return setlId → 回执；参数缺失或未命中时空 map
     */
    public Map<String, YbSettlementRecord> mapBySetlIds(Collection<String> setlIds, Long tenantId) {
        if (setlIds == null || setlIds.isEmpty() || tenantId == null) {
            return Collections.emptyMap();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String setlId : setlIds) {
            if (setlId != null && !setlId.isBlank()) {
                unique.add(setlId.trim());
            }
        }
        if (unique.isEmpty()) {
            return Collections.emptyMap();
        }
        List<YbSettlementRecord> rows =
            settlementRecordRepository.findAllBySetlIdInAndTenantIdAndIsDeletedFalse(unique, tenantId);
        Map<String, YbSettlementRecord> out = new HashMap<>(rows.size() * 2);
        for (YbSettlementRecord row : rows) {
            if (row == null || row.getSetlId() == null || row.getSetlId().isBlank()) {
                continue;
            }
            out.putIfAbsent(row.getSetlId().trim(), row);
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
     * 按医保结算时间查询结算回执（左闭右开；{@code setl_time} 为空回落 {@code created_at}）。
     * <p>供对账清算：3201/3202 日期口径对齐平台「以结算返回的结算时间为依据」。</p>
     *
     * @param tenantId     租户 ID
     * @param beginAt      起始时间（含）
     * @param endExclusive 结束时间（不含）
     * @return 结算回执列表；参数缺失时空列表
     */
    public List<YbSettlementRecord> listBySetlTimeBetween(
            Long tenantId, LocalDateTime beginAt, LocalDateTime endExclusive) {
        if (tenantId == null || beginAt == null || endExclusive == null) {
            return Collections.emptyList();
        }
        // Pre：有结算时间的按 setl_time 入窗
        List<YbSettlementRecord> bySetlTime =
            settlementRecordRepository
                .findAllByTenantIdAndIsDeletedFalseAndSetlTimeGreaterThanEqualAndSetlTimeLessThan(
                    tenantId, beginAt, endExclusive);
        // Pre：缺省结算时间的回落创建时间，避免历史空 setl_time 整日落空
        List<YbSettlementRecord> byCreatedFallback =
            settlementRecordRepository
                .findAllByTenantIdAndIsDeletedFalseAndSetlTimeIsNullAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                    tenantId, beginAt, endExclusive);
        return mergeById(bySetlTime, byCreatedFallback);
    }

    /**
     * 按合同 + 结算模式 + 结算时间窗查询（工伤对账专用，缩小结果集）。
     *
     * @param tenantId     租户 ID
     * @param contractId   合同 ID
     * @param settleMode   结算模式，如 WORK_INJURY
     * @param beginAt      起始时间（含）
     * @param endExclusive 结束时间（不含）
     * @return 结算回执列表
     */
    public List<YbSettlementRecord> listBySetlTimeBetweenAndContractAndSettleMode(
            Long tenantId,
            Long contractId,
            String settleMode,
            LocalDateTime beginAt,
            LocalDateTime endExclusive) {
        if (tenantId == null
                || contractId == null
                || isEmpty(settleMode)
                || beginAt == null
                || endExclusive == null) {
            return Collections.emptyList();
        }
        String mode = settleMode.trim();
        List<YbSettlementRecord> bySetlTime =
            settlementRecordRepository
                .findAllByTenantIdAndContractIdAndSettleModeAndIsDeletedFalseAndSetlTimeGreaterThanEqualAndSetlTimeLessThan(
                    tenantId, contractId, mode, beginAt, endExclusive);
        List<YbSettlementRecord> byCreatedFallback =
            settlementRecordRepository
                .findAllByTenantIdAndContractIdAndSettleModeAndIsDeletedFalseAndSetlTimeIsNullAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                    tenantId, contractId, mode, beginAt, endExclusive);
        return mergeById(bySetlTime, byCreatedFallback);
    }

    private static List<YbSettlementRecord> mergeById(
            List<YbSettlementRecord> bySetlTime, List<YbSettlementRecord> byCreatedFallback) {
        if (byCreatedFallback == null || byCreatedFallback.isEmpty()) {
            if (bySetlTime == null) {
                return Collections.emptyList();
            }
            return bySetlTime;
        }
        if (bySetlTime == null || bySetlTime.isEmpty()) {
            return byCreatedFallback;
        }
        Map<Long, YbSettlementRecord> merged = new LinkedHashMap<>();
        for (YbSettlementRecord row : bySetlTime) {
            if (row != null && row.getId() != null) {
                merged.put(row.getId(), row);
            }
        }
        for (YbSettlementRecord row : byCreatedFallback) {
            if (row != null && row.getId() != null) {
                merged.putIfAbsent(row.getId(), row);
            }
        }
        return new ArrayList<>(merged.values());
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
     * 按支付单取最早一笔 CONFIRMED 结算回执（博思基金分项 / 票面金额）。
     *
     * @param paymentId 支付单 ID
     * @param tenantId  租户 ID
     * @return 有效回执；无则 empty
     */
    public Optional<YbSettlementRecord> findFirstConfirmedByPaymentId(Long paymentId, Long tenantId) {
        for (YbSettlementRecord row : this.listByPaymentId(paymentId, tenantId)) {
            if (row != null && YbSettlementVoucherStatus.CONFIRMED.equals(row.getStatus())) {
                return Optional.of(row);
            }
        }
        return Optional.empty();
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
     * 批量解析结算回执「零售−上传」分差（收费日结勾稽）。
     *
     * @param ids      结算回执主键集合
     * @param tenantId 租户 ID
     * @return id → retail_upload_diff_amt；未命中或不含分差字段不入表
     */
    public Map<Long, BigDecimal> mapRetailUploadDiffByIds(Collection<Long> ids, Long tenantId) {
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
        Map<Long, BigDecimal> out = new HashMap<>(rows.size() * 2);
        for (YbSettlementRecord row : rows) {
            if (row == null || row.getId() == null || row.getRetailUploadDiffAmt() == null) {
                continue;
            }
            out.put(row.getId(), row.getRetailUploadDiffAmt());
        }
        return out;
    }

    /**
     * 按支付单批量解析「零售−上传」分差（桥表缺 settlement_record_id 时日结回落）。
     *
     * <p>每个 payment 对其回执分差做代数和（与桥表多回执口径一致，**BR-OP-DCL-028**）。</p>
     *
     * @param paymentIds 支付单主键集合
     * @param tenantId   租户 ID
     * @return paymentId → retail_upload_diff_amt 代数和
     */
    public Map<Long, BigDecimal> mapRetailUploadDiffByPaymentIds(Collection<Long> paymentIds, Long tenantId) {
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
        Map<Long, BigDecimal> out = new HashMap<>();
        for (YbSettlementRecord row : rows) {
            if (row == null || row.getPaymentId() == null || row.getRetailUploadDiffAmt() == null) {
                continue;
            }
            out.merge(row.getPaymentId(), row.getRetailUploadDiffAmt(), BigDecimal::add);
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
            if (all == null || all.isEmpty()) {
                throw new BusinessException(ChsErrorCodes.PLATFORM_FAILED, "未找到结算回执");
            }
            List<YbSettlementRecord> active = all.stream()
                .filter(row -> !YbSettlementVoucherStatus.REVOKED.equals(row.getStatus()))
                .collect(Collectors.toList());
            if (active.isEmpty()) {
                // Pre：仅剩已撤销回执 → 交由 Invoker 幂等返回
                return all.get(all.size() - 1);
            }
            if (active.size() > 1) {
                throw new BusinessException(
                    ChsErrorCodes.SETTLEMENT_AMBIGUOUS,
                    "同一 payment 存在多笔医保结算，请指定 settlementRecordId 或 setlId");
            }
            return active.get(0);
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

    /**
     * 医保费用明细：CONFIRMED 净行 DB 分页（AS-RPT-012/013；禁止全窗物化）。
     *
     * <p>聚合边界：仅本聚合 {@link YbSettlementRecordRepository}。</p>
     *
     * @param criteria 时间窗、就诊类、合同/工伤、险种、就诊白名单与编号模糊
     * @return Spring Page；参数非法或预解析空 scope 时为空页
     */
    public Page<YbSettlementRecord> pageConfirmedInsuranceFeeDetail(InsuranceFeeDetailPageCriteria criteria) {
        if (criteria == null || criteria.getTenantId() == null) {
            return Page.empty();
        }
        // Gate：科室/业务号/姓名预解析无命中 → 直接空页
        if (Boolean.TRUE.equals(criteria.getEmptyEncounterScope())) {
            return Page.empty(this.toPageable(criteria));
        }
        if (criteria.getSetlTimeFromInclusive() == null || criteria.getSetlTimeToExclusive() == null) {
            return Page.empty();
        }
        if (isEmpty(criteria.getEncounterClass())) {
            return Page.empty();
        }
        Specification<YbSettlementRecord> specification = this.buildConfirmedFeeDetailSpecification(criteria);
        return this.settlementRecordRepository.findAll(specification, this.toPageable(criteria));
    }

    /**
     * 医保费用明细：同筛选四列金额 SQL SUM（禁止回刷明细实体再加总）。
     *
     * @param criteria 与 {@link #pageConfirmedInsuranceFeeDetail} 同筛选（分页字段忽略）
     * @return 笔数与四列合计；空 scope / 参数缺失时零合计
     */
    public InsuranceFeeDetailAmountSummary summarizeConfirmedInsuranceFeeDetail(
            InsuranceFeeDetailPageCriteria criteria) {
        InsuranceFeeDetailAmountSummary empty = new InsuranceFeeDetailAmountSummary();
        if (criteria == null || criteria.getTenantId() == null) {
            return empty;
        }
        if (Boolean.TRUE.equals(criteria.getEmptyEncounterScope())) {
            return empty;
        }
        if (criteria.getSetlTimeFromInclusive() == null || criteria.getSetlTimeToExclusive() == null) {
            return empty;
        }
        if (isEmpty(criteria.getEncounterClass())) {
            return empty;
        }
        CriteriaBuilder cb = this.entityManager.getCriteriaBuilder();
        CriteriaQuery<Object[]> cq = cb.createQuery(Object[].class);
        Root<YbSettlementRecord> root = cq.from(YbSettlementRecord.class);
        List<Predicate> predicates = this.collectConfirmedFeeDetailPredicates(root, cb, criteria);
        cq.multiselect(
            cb.count(root.get("id")),
            cb.sum(this.coalesceAmount(cb, root.get("medfeeSumamt"))),
            cb.sum(this.coalesceAmount(cb, root.get("fundPaySumamt"))),
            cb.sum(this.coalesceAmount(cb, root.get("acctPay"))),
            cb.sum(this.coalesceAmount(cb, root.get("psnCashPay"))));
        cq.where(predicates.toArray(new Predicate[0]));
        TypedQuery<Object[]> typedQuery = this.entityManager.createQuery(cq);
        List<Object[]> rows = typedQuery.getResultList();
        if (rows == null || rows.isEmpty() || rows.get(0) == null) {
            return empty;
        }
        Object[] tuple = rows.get(0);
        InsuranceFeeDetailAmountSummary summary = new InsuranceFeeDetailAmountSummary();
        summary.setLineCount(this.toLong(tuple[0]));
        summary.setMedfeeSumamtTotal(this.toBigDecimal(tuple[1]));
        summary.setFundPaySumamtTotal(this.toBigDecimal(tuple[2]));
        summary.setAcctPayTotal(this.toBigDecimal(tuple[3]));
        summary.setPsnCashPayTotal(this.toBigDecimal(tuple[4]));
        return summary;
    }

    /**
     * 将筛选条件转为 CONFIRMED 净行 Specification（分页与 count 共用）。
     *
     * @param criteria 已 Gate 的筛选
     * @return JPA Specification
     */
    private Specification<YbSettlementRecord> buildConfirmedFeeDetailSpecification(
            InsuranceFeeDetailPageCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = this.collectConfirmedFeeDetailPredicates(root, cb, criteria);
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 组装 CONFIRMED + 时间窗 + 类别/工伤 + 险种 + 就诊白名单 + 编号模糊谓词。
     *
     * @param root     实体根
     * @param cb       CriteriaBuilder
     * @param criteria 筛选
     * @return 谓词列表（非空）
     */
    private List<Predicate> collectConfirmedFeeDetailPredicates(
            Root<YbSettlementRecord> root,
            CriteriaBuilder cb,
            InsuranceFeeDetailPageCriteria criteria) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(root.get("tenantId"), criteria.getTenantId()));
        predicates.add(cb.isFalse(root.get("isDeleted")));
        predicates.add(cb.equal(root.get("status"), YbSettlementVoucherStatus.CONFIRMED));
        predicates.add(cb.equal(root.get("encounterClass"), criteria.getEncounterClass().trim()));
        // 结算时间窗：有 setl_time 按 setl_time；空则回落 created_at（与 listBySetlTimeBetween 同口径）
        Predicate bySetlTime = cb.and(
            cb.isNotNull(root.get("setlTime")),
            cb.greaterThanOrEqualTo(root.get("setlTime"), criteria.getSetlTimeFromInclusive()),
            cb.lessThan(root.get("setlTime"), criteria.getSetlTimeToExclusive()));
        Predicate byCreatedFallback = cb.and(
            cb.isNull(root.get("setlTime")),
            cb.greaterThanOrEqualTo(root.get("createdAt"), criteria.getSetlTimeFromInclusive()),
            cb.lessThan(root.get("createdAt"), criteria.getSetlTimeToExclusive()));
        predicates.add(cb.or(bySetlTime, byCreatedFallback));
        // 工伤独立：仅 WORK_INJURY；否则排除工伤并按合同白名单
        if (Boolean.TRUE.equals(criteria.getWorkInjuryOnly())) {
            String mode = criteria.getWorkInjurySettleMode();
            if (isEmpty(mode)) {
                // 缺 settle_mode 字面量时用恒假谓词，避免误放行全表
                predicates.add(cb.equal(cb.literal(1), cb.literal(0)));
            } else {
                predicates.add(cb.equal(root.get("settleMode"), mode.trim()));
            }
        } else {
            String mode = criteria.getWorkInjurySettleMode();
            if (isNotEmpty(mode)) {
                predicates.add(cb.or(
                    cb.isNull(root.get("settleMode")),
                    cb.notEqual(root.get("settleMode"), mode.trim())));
            }
            List<Long> contractIds = criteria.getContractIds();
            if (contractIds == null || contractIds.isEmpty()) {
                // 非工伤且无合同：无行可命中
                predicates.add(cb.equal(cb.literal(1), cb.literal(0)));
            } else {
                predicates.add(root.get("contractId").in(contractIds));
            }
        }
        List<String> insutypes = criteria.getInsutypes();
        if (insutypes != null && !insutypes.isEmpty()) {
            predicates.add(root.get("insutype").in(insutypes));
        }
        List<Long> encounterIds = criteria.getEncounterIds();
        if (encounterIds != null && !encounterIds.isEmpty()) {
            predicates.add(root.get("encounterId").in(encounterIds));
        }
        if (isNotEmpty(criteria.getSetlIdKeyword())) {
            String like = "%" + criteria.getSetlIdKeyword().trim().toLowerCase() + "%";
            predicates.add(cb.like(cb.lower(root.get("setlId")), like));
        }
        if (isNotEmpty(criteria.getMdtrtIdKeyword())) {
            String like = "%" + criteria.getMdtrtIdKeyword().trim().toLowerCase() + "%";
            predicates.add(cb.like(cb.lower(root.get("mdtrtId")), like));
        }
        // 姓名：与列表展示列同源，按平台回执 psn_name 包含匹配
        if (isNotEmpty(criteria.getPatientNameKeyword())) {
            String like = "%" + criteria.getPatientNameKeyword().trim().toLowerCase() + "%";
            predicates.add(cb.like(cb.lower(root.get("psnName")), like));
        }
        return predicates;
    }

    /**
     * 金额列 NULL 按 0 参与 SUM，避免合计被整列置空。
     *
     * @param cb   CriteriaBuilder
     * @param path 金额路径
     * @return coalesce 表达式
     */
    private Expression<BigDecimal> coalesceAmount(CriteriaBuilder cb, Expression<BigDecimal> path) {
        return cb.coalesce(path, BigDecimal.ZERO);
    }

    /**
     * 筛选 → Pageable（结算时间倒序，空 setl_time 靠后；同刻按 id 倒序）。
     *
     * @param criteria 含 pageNo/pageSize
     * @return Pageable
     */
    private Pageable toPageable(InsuranceFeeDetailPageCriteria criteria) {
        int pageNo = 1;
        if (criteria.getPageNo() != null && criteria.getPageNo() > 0) {
            pageNo = criteria.getPageNo();
        }
        int pageSize = 20;
        if (criteria.getPageSize() != null && criteria.getPageSize() > 0) {
            pageSize = criteria.getPageSize();
        }
        Sort sort = Sort.by(Sort.Order.desc("setlTime"), Sort.Order.desc("id"));
        return PageRequest.of(pageNo - 1, pageSize, sort);
    }

    /**
     * Object 计数 → long；null 按 0。
     *
     * @param value 查询结果
     * @return 长整型
     */
    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }

    /**
     * Object 金额 → BigDecimal；null 按 ZERO。
     *
     * @param value 查询结果
     * @return 金额
     */
    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        // JPA SUM 通常直接返回 BigDecimal
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        // 其它 Number：经字符串转入，避免 double 中间精度损失
        if (value instanceof Number) {
            return new BigDecimal(value.toString());
        }
        return BigDecimal.ZERO;
    }
}
