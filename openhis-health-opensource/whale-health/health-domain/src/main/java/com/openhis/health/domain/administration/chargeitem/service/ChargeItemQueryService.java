/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.chargeitem.service;

import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.query.OutpatientChargeQueueEncounterQuery;
import com.openhis.health.domain.administration.chargeitem.query.OutpatientFeeDetailAmountSummaryQueryModel;
import com.openhis.health.domain.administration.chargeitem.query.OutpatientFeeDetailChargeRowQueryModel;
import com.openhis.health.domain.administration.chargeitem.query.OutpatientFeeDetailEncounterBlockQueryModel;
import com.openhis.health.domain.administration.chargeitem.query.OutpatientFeeDetailPageQuery;
import com.openhis.health.domain.administration.chargeitem.query.FeeDetailFinTypeSummaryQueryModel;
import com.openhis.health.domain.administration.chargeitem.query.PractitionerWorkloadChargeRowQueryModel;
import com.openhis.health.domain.administration.chargeitem.query.PractitionerWorkloadFinTypeOrgSummaryQueryModel;
import com.openhis.health.domain.administration.chargeitem.query.PractitionerWorkloadOverviewSummaryQueryModel;
import com.openhis.health.domain.administration.chargeitem.query.PractitionerWorkloadPageQuery;
import com.openhis.health.domain.administration.chargeitem.query.PractitionerWorkloadRegistrationOrgSummaryQueryModel;
import com.openhis.health.domain.administration.chargeitem.query.PractitionerWorkloadRequestDetailQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.PractitionerWorkloadEncounterContextQueryModel;
import com.openhis.health.domain.share.constants.PractitionerWorkloadConstants;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.OutpatientFeeDetailChargePaymentLinkQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.OutpatientFeeDetailPaymentContextQueryModel;
import com.openhis.health.domain.share.constants.OutpatientFeeDetailConstants;
import com.openhis.health.domain.administration.chargeitem.repository.ChargeItemRepository;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.EncounterStatus;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.utils.EncounterDiagnosisIdsPredicates;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 收费明细读侧查询服务（DM-ADM-011 §6.2）。
 *
 * <p>职责：提供多种维度的收费明细只读查询能力，用于写入前校验、UI 回显与费用汇总；
 * 含门诊费用明细（§6.2.25～26）与科室/医生工作量（§6.2.27～29，AS-RPT-001）。</p>
 *
 * <p>边界：只读，不写库；仅注入本聚合 {@link ChargeItemRepository}（B.6.4）；
 * payment / Encounter scope 预解析由 module-support 承担。</p>
 */
@Service
@RequiredArgsConstructor
public class ChargeItemQueryService {

    private final ChargeItemRepository repository;
    private final ChargeItemRegenerationChainSupport regenerationChainSupport;
    private final EntityManager entityManager;

    /**
     * 按主键集合批量读取收费明细（DM-ADM-011 §6.2.1）。
     *
     * @param cmd 查询命令（ids 必填）
     * @return 收费明细列表
     */
    public List<ChargeItem> loadChargeItemsByIds(ChargeItemQueryCommand cmd) {
        // 批量主键一次加载，供退费、回显等场景减少往返。
        return repository.findAllByIdIn(cmd.getIds());
    }

    /**
     * 按上游业务主键集合查询关联明细（DM-ADM-011 §6.2.2，service_id IN）。
     *
     * @param cmd 查询命令（serviceIds 必填；可选 {@link ChargeItemQueryCommand#getEncounterId()} 收敛就诊；
     *            可选 {@link ChargeItemQueryCommand#getExcludeSupersededSettledLines()} 排除已再生替代的源 SETTLED 行）
     * @return 收费明细列表
     */
    public List<ChargeItem> listChargeItemsByServiceIds(ChargeItemQueryCommand cmd) {
        if (cmd == null || cmd.getServiceIds() == null || cmd.getServiceIds().isEmpty()) {
            return new ArrayList<>();
        }
        List<ChargeItem> rows = repository.findAllByServiceIdIn(cmd.getServiceIds());
        if (cmd.getEncounterId() != null) {
            List<ChargeItem> scoped = new ArrayList<>();
            for (ChargeItem row : rows) {
                if (row != null && cmd.getEncounterId().equals(row.getEncounterId())) {
                    scoped.add(row);
                }
            }
            rows = scoped;
        }
        if (Boolean.TRUE.equals(cmd.getExcludeSupersededSettledLines())) {
            rows = this.filterOutSupersededSettledLinesGroupedByEncounter(rows);
        }
        return rows;
    }

    /**
     * 按就诊 + 上游表名 + 上游主键集合加载收费明细（仅本聚合 Repository）。
     *
     * <p>供进销存 3506 住院对单：同药嘱锚点下冲销/再生行经 {@code refund_id} 反查原账单。</p>
     *
     * @param encounterId  就诊主键
     * @param serviceTable 上游物理表名（药品为 {@link TableNameConstants#MED_MEDICATION_REQUEST}）
     * @param serviceIds   上游主键集合
     * @return 明细列表；入参不全或无有效主键时返回空表
     */
    public List<ChargeItem> listByEncounterAndServiceAnchors(
        Long encounterId,
        String serviceTable,
        Collection<Long> serviceIds
    ) {
        // Gate：就诊、表名、上游主键均须齐全，避免全表误扫
        if (encounterId == null || StringUtils.isEmpty(serviceTable) || serviceIds == null || serviceIds.isEmpty()) {
            return new ArrayList<>();
        }
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        for (Long serviceId : serviceIds) {
            if (serviceId != null) {
                distinct.add(serviceId);
            }
        }
        // 去重后无有效锚点则短路
        if (distinct.isEmpty()) {
            return new ArrayList<>();
        }
        return repository.findAllByEncounterIdAndServiceTableAndServiceIdIn(
            encounterId, serviceTable.trim(), distinct);
    }

    /**
     * 按收费状态与业务锚点表解析 {@code service_id} 集合（AS-OP-PHA-005 收费状态筛预解析，B.6.4）。
     *
     * @param statusEnum  {@link ChargeItemStatus} 持久化 code；空则返回空列表
     * @param serviceTable 业务锚点表名（药品申请为 {@link TableNameConstants#MED_MEDICATION_REQUEST}）
     * @return 未软删且状态/表名命中的去重 {@code service_id} 列表
     */
    public List<Long> listDistinctServiceIdsByStatusEnumAndServiceTable(String statusEnum, String serviceTable) {
        String normalizedStatus = StringUtils.trimToNull(statusEnum);
        String normalizedTable = StringUtils.trimToNull(serviceTable);
        if (normalizedStatus == null || normalizedTable == null) {
            return List.of();
        }
        Specification<ChargeItem> specification = (root, criteriaQuery, cb) -> cb.and(
            cb.isFalse(root.get(FieldNameConstants.IS_DELETED)),
            cb.equal(root.get(FieldNameConstants.STATUS_ENUM), normalizedStatus),
            cb.equal(root.get(FieldNameConstants.SERVICE_TABLE), normalizedTable),
            cb.isNotNull(root.get(FieldNameConstants.SERVICE_ID)));
        List<ChargeItem> rows = repository.findAll(specification);
        LinkedHashSet<Long> serviceIds = new LinkedHashSet<>();
        // Loop：收费明细 → 药品申请主键去重，供处方查询 id IN
        for (ChargeItem row : rows) {
            if (row != null && row.getServiceId() != null) {
                serviceIds.add(row.getServiceId());
            }
        }
        return new ArrayList<>(serviceIds);
    }

    /**
     * 按执行记录主键集合批量查询收费明细（取消执行 Gate 与回退定位，DM-ADM-011 §6.2.23）。
     *
     * <p>默认排除反结/半退再生链上已被替代的源 {@code SETTLED} 行，仅保留有效待结算再生行（BR-IP-RVS-011）。</p>
     *
     * @param procedureIds 执行记录主键集合
     * @return 收费明细列表；空入参返回空列表
     */
    public List<ChargeItem> listChargeItemsByProcedureIds(Collection<Long> procedureIds) {
        return this.listChargeItemsByProcedureIds(procedureIds, true);
    }

    /**
     * 按执行记录主键集合批量查询收费明细，可选排除已再生替代的源 {@code SETTLED} 行。
     *
     * @param procedureIds                  执行记录主键集合
     * @param excludeSupersededSettledLines 为 true 时排除 BR-REF-020 / BR-IP-RVS-011 源 SETTLED 行
     * @return 收费明细列表；空入参返回空列表
     */
    public List<ChargeItem> listChargeItemsByProcedureIds(
        Collection<Long> procedureIds,
        boolean excludeSupersededSettledLines) {
        if (procedureIds == null || procedureIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> distinctIds = new LinkedHashSet<>();
        for (Long procedureId : procedureIds) {
            if (procedureId != null) {
                distinctIds.add(procedureId);
            }
        }
        if (distinctIds.isEmpty()) {
            return List.of();
        }
        List<ChargeItem> rows = repository.findAllByProcedureIdIn(distinctIds);
        if (excludeSupersededSettledLines) {
            rows = this.filterOutSupersededSettledLinesGroupedByEncounter(rows);
        }
        return rows;
    }

    /**
     * 住院展示余额：单就诊待结算费用合计（库内余额 − 本值 = 展示可用余额）。
     *
     * @param encounterId 住院就诊主键
     * @return 待结算总价；无行时 0
     */
    public BigDecimal sumPendingSettlementTotalPriceByEncounterId(Long encounterId) {
        if (encounterId == null) {
            return BigDecimal.ZERO;
        }
        Map<Long, BigDecimal> totals = this.sumTotalPriceGroupedByEncounterIdAndStatusEnum(
            List.of(encounterId), ChargeItemStatus.PENDING_SETTLEMENT.getCode());
        BigDecimal sum = totals.get(encounterId);
        if (sum == null) {
            return BigDecimal.ZERO;
        }
        return sum;
    }

    /**
     * 住院展示余额：批量按就诊汇总待结算费用（护士站/预交金队列防 N+1）。
     *
     * @param encounterIds 住院就诊主键集合
     * @return encounterId → 待结算总价
     */
    public Map<Long, BigDecimal> mapPendingSettlementTotalPriceByEncounterIds(Collection<Long> encounterIds) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            return Collections.emptyMap();
        }
        LinkedHashSet<Long> distinctIds = new LinkedHashSet<>();
        for (Long id : encounterIds) {
            if (id != null) {
                distinctIds.add(id);
            }
        }
        if (distinctIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return this.sumTotalPriceGroupedByEncounterIdAndStatusEnum(
            distinctIds, ChargeItemStatus.PENDING_SETTLEMENT.getCode());
    }

    /**
     * 住院展示余额：单就诊已结算费用合计（结算写侧已从库内余额扣减；仅用于「已结清」文案）。
     *
     * @param encounterId 住院就诊主键
     * @return 已结算总价；无行时 0
     */
    public BigDecimal sumSettledTotalPriceByEncounterId(Long encounterId) {
        if (encounterId == null) {
            return BigDecimal.ZERO;
        }
        Map<Long, BigDecimal> totals = this.sumTotalPriceGroupedByEncounterIdAndStatusEnum(
            List.of(encounterId), ChargeItemStatus.SETTLED.getCode());
        BigDecimal sum = totals.get(encounterId);
        if (sum == null) {
            return BigDecimal.ZERO;
        }
        return sum;
    }

    /**
     * 住院展示余额：批量按就诊汇总已结算费用（已结清文案等读侧使用）。
     *
     * @param encounterIds 住院就诊主键集合
     * @return encounterId → 已结算总价
     */
    public Map<Long, BigDecimal> mapSettledTotalPriceByEncounterIds(Collection<Long> encounterIds) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            return Collections.emptyMap();
        }
        LinkedHashSet<Long> distinctIds = new LinkedHashSet<>();
        for (Long id : encounterIds) {
            if (id != null) {
                distinctIds.add(id);
            }
        }
        if (distinctIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return this.sumTotalPriceGroupedByEncounterIdAndStatusEnum(
            distinctIds, ChargeItemStatus.SETTLED.getCode());
    }

    /**
     * 住院内部：单就诊待结算 + 已结算费用占用合计（遗留汇总；展示余额请用待结算 + 库内余额）。
     *
     * @param encounterId 住院就诊主键
     * @return 待结算与已结算 {@code total_price} 之和；无行时 0
     */
    public BigDecimal sumInpatientChargedOccupancyTotalPriceByEncounterId(Long encounterId) {
        if (encounterId == null) {
            return BigDecimal.ZERO;
        }
        Map<Long, BigDecimal> totals = this.mapInpatientChargedOccupancyTotalPriceByEncounterIds(
            List.of(encounterId));
        BigDecimal sum = totals.get(encounterId);
        if (sum == null) {
            return BigDecimal.ZERO;
        }
        return sum;
    }

    /**
     * 住院展示余额：批量按就诊汇总待结算 + 已结算费用占用（护士站/预交金/结算队列防 N+1）。
     *
     * @param encounterIds 住院就诊主键集合
     * @return encounterId → 待结算与已结算总价之和
     */
    public Map<Long, BigDecimal> mapInpatientChargedOccupancyTotalPriceByEncounterIds(
        Collection<Long> encounterIds) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            return Collections.emptyMap();
        }
        LinkedHashSet<Long> distinctIds = new LinkedHashSet<>();
        for (Long id : encounterIds) {
            if (id != null) {
                distinctIds.add(id);
            }
        }
        if (distinctIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> statusCodes = new ArrayList<>();
        statusCodes.add(ChargeItemStatus.PENDING_SETTLEMENT.getCode());
        statusCodes.add(ChargeItemStatus.SETTLED.getCode());
        return this.sumTotalPriceGroupedByEncounterIdAndStatusEnums(distinctIds, statusCodes);
    }

    /**
     * 按就诊与收费状态列出明细主键（住院反结回退 SETTLED 行等）。
     *
     * @param encounterId 就诊主键
     * @param statusEnum  收费状态持久化码
     * @return 主键列表；无匹配时空列表
     */
    public List<Long> listChargeItemIdsByEncounterIdAndStatusEnum(Long encounterId, String statusEnum) {
        if (encounterId == null || StringUtils.isEmpty(statusEnum)) {
            return Collections.emptyList();
        }
        List<ChargeItem> rows = repository.findAllByEncounterIdAndStatusEnum(
            encounterId, statusEnum.trim());
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> ids = new ArrayList<>();
        for (ChargeItem row : rows) {
            if (row != null && row.getId() != null && !Boolean.TRUE.equals(row.getIsDeleted())) {
                ids.add(row.getId());
            }
        }
        return ids;
    }

    /**
     * 在库内按就诊分组汇总 {@code total_price}（派生 Repository 无法表达 SUM + GROUP BY）。
     *
     * <p>相对 {@code findAllByEncounterIdInAndStatusEnumIn} 后内存累加：只返回分组聚合行，避免大批量明细行网络与堆占用。</p>
     *
     * @param encounterIds 就诊主键集合（非空）
     * @param statusEnum   收费状态持久化码
     * @return 有明细的就诊 → 总价合计；无匹配行的就诊不出现在 Map 中
     */
    private Map<Long, BigDecimal> sumTotalPriceGroupedByEncounterIdAndStatusEnum(
        Collection<Long> encounterIds, String statusEnum) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            return Collections.emptyMap();
        }
        if (StringUtils.isEmpty(statusEnum)) {
            return Collections.emptyMap();
        }
        LinkedHashSet<Long> distinctIds = new LinkedHashSet<>();
        for (Long id : encounterIds) {
            if (id != null) {
                distinctIds.add(id);
            }
        }
        if (distinctIds.isEmpty()) {
            return Collections.emptyMap();
        }
        CriteriaBuilder cb = this.entityManager.getCriteriaBuilder();
        CriteriaQuery<Object[]> cq = cb.createQuery(Object[].class);
        Root<ChargeItem> root = cq.from(ChargeItem.class);
        // 住院余额：仅统计指定就诊 + 待结算状态，在 SQL 层 SUM 而非拉全表明细
        cq.multiselect(
            root.get(FieldNameConstants.ENCOUNTER_ID),
            cb.sum(root.get(FieldNameConstants.TOTAL_PRICE)));
        cq.where(
            cb.and(
                root.get(FieldNameConstants.ENCOUNTER_ID).in(distinctIds),
                cb.equal(root.get(FieldNameConstants.STATUS_ENUM), statusEnum),
                cb.isFalse(root.get(FieldNameConstants.IS_DELETED))));
        cq.groupBy(root.get(FieldNameConstants.ENCOUNTER_ID));
        List<Object[]> rows = this.entityManager.createQuery(cq).getResultList();
        Map<Long, BigDecimal> totals = new HashMap<>();
        for (Object[] row : rows) {
            if (row == null || row.length < 2) {
                continue;
            }
            Long encounterId = (Long) row[0];
            BigDecimal sum = (BigDecimal) row[1];
            if (encounterId == null || sum == null) {
                continue;
            }
            totals.put(encounterId, sum);
        }
        return totals;
    }

    /**
     * 在库内按就诊分组汇总 {@code total_price}（多状态 IN 条件，供住院占用额批量统计）。
     *
     * @param encounterIds 就诊主键集合（非空）
     * @param statusEnums  收费状态持久化码集合（非空）
     * @return 有明细的就诊 → 总价合计；无匹配行的就诊不出现在 Map 中
     */
    private Map<Long, BigDecimal> sumTotalPriceGroupedByEncounterIdAndStatusEnums(
        Collection<Long> encounterIds, Collection<String> statusEnums) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            return Collections.emptyMap();
        }
        if (statusEnums == null || statusEnums.isEmpty()) {
            return Collections.emptyMap();
        }
        LinkedHashSet<Long> distinctIds = new LinkedHashSet<>();
        for (Long id : encounterIds) {
            if (id != null) {
                distinctIds.add(id);
            }
        }
        LinkedHashSet<String> distinctStatuses = new LinkedHashSet<>();
        for (String status : statusEnums) {
            if (StringUtils.isNotEmpty(status)) {
                distinctStatuses.add(status.trim());
            }
        }
        if (distinctIds.isEmpty() || distinctStatuses.isEmpty()) {
            return Collections.emptyMap();
        }
        CriteriaBuilder cb = this.entityManager.getCriteriaBuilder();
        CriteriaQuery<Object[]> cq = cb.createQuery(Object[].class);
        Root<ChargeItem> root = cq.from(ChargeItem.class);
        // 住院余额占用：待结算 + 已结算均在 SQL 层 SUM，避免结算后仅扣待结算导致余额虚高
        cq.multiselect(
            root.get(FieldNameConstants.ENCOUNTER_ID),
            cb.sum(root.get(FieldNameConstants.TOTAL_PRICE)));
        cq.where(
            cb.and(
                root.get(FieldNameConstants.ENCOUNTER_ID).in(distinctIds),
                root.get(FieldNameConstants.STATUS_ENUM).in(distinctStatuses),
                cb.isFalse(root.get(FieldNameConstants.IS_DELETED))));
        cq.groupBy(root.get(FieldNameConstants.ENCOUNTER_ID));
        List<Object[]> rows = this.entityManager.createQuery(cq).getResultList();
        Map<Long, BigDecimal> totals = new HashMap<>();
        for (Object[] row : rows) {
            if (row == null || row.length < 2) {
                continue;
            }
            Long encounterId = (Long) row[0];
            BigDecimal sum = (BigDecimal) row[1];
            if (encounterId == null || sum == null) {
                continue;
            }
            totals.put(encounterId, sum);
        }
        return totals;
    }

    /**
     * 门诊发药 IF-OPP-03：按就诊与药品发放聚合锚点批量加载收费明细，用于单价/总价回显（锚点：{@code dispense_table}+{@code dispense_id}）。
     *
     * <p>状态口径：草稿/待收费/已结算/退款中/已退款均纳入——签发前后迁移、医生站拟退（{@code REFUNDING}）、
     * 收费窗退费完成（{@code REFUNDED}）时均需回显金额（门诊退药明细、发药 slip 等同源）。</p>
     * <p>住院护士执行后汇总/发药定价由 {@code module.querysupport.pharmacy.InpatientMedicationDispenseChargePricingLoader}
     * 调用 {@link #listPendingSettlementChargeItemsForMedicationDispensePricing}（仅 {@code PENDING_SETTLEMENT}）。</p>
     *
     * @param encounterId 就诊标识
     * @param dispenseIds 发放单主键集合
     * @return 平铺明细；入参非法或集合为空时返回空表、不落库
     */
    public List<ChargeItem> listChargeItemsForMedicationDispensePricing(Long encounterId, Collection<Long> dispenseIds) {
        if (encounterId == null || dispenseIds == null || dispenseIds.isEmpty()) {
            return new ArrayList<>();
        }
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        for (Long dispenseId : dispenseIds) {
            if (dispenseId != null) {
                distinct.add(dispenseId);
            }
        }
        if (distinct.isEmpty()) {
            return new ArrayList<>();
        }
        LinkedHashSet<String> statuses = new LinkedHashSet<>();
        statuses.add(ChargeItemStatus.DRAFT.getCode());
        statuses.add(ChargeItemStatus.PLANNED.getCode());
        statuses.add(ChargeItemStatus.SETTLED.getCode());
        statuses.add(ChargeItemStatus.REFUNDING.getCode());
        statuses.add(ChargeItemStatus.REFUNDED.getCode());
        return repository.findAllByEncounterIdAndDispenseTableAndDispenseIdInAndStatusEnumIn(
            encounterId,
            TableNameConstants.MED_MEDICATION_DISPENSE,
            distinct,
            statuses);
    }

    /**
     * 住院汇总领药/住院发药：按就诊与药品发放锚点批量加载<strong>待结算</strong>收费明细（锚点：{@code dispense_table}+{@code dispense_id}）。
     *
     * <p>仅 {@code PENDING_SETTLEMENT}：护士执行入账后、日结/出院结算前的在院累计费用。</p>
     *
     * @param encounterId 就诊标识
     * @param dispenseIds 发放单主键集合
     * @return 平铺明细；入参非法或集合为空时返回空表、不落库
     */
    public List<ChargeItem> listPendingSettlementChargeItemsForMedicationDispensePricing(
        Long encounterId, Collection<Long> dispenseIds) {
        if (encounterId == null || dispenseIds == null || dispenseIds.isEmpty()) {
            return new ArrayList<>();
        }
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        for (Long dispenseId : dispenseIds) {
            if (dispenseId != null) {
                distinct.add(dispenseId);
            }
        }
        if (distinct.isEmpty()) {
            return new ArrayList<>();
        }
        return repository.findAllByEncounterIdAndDispenseTableAndDispenseIdInAndStatusEnumIn(
            encounterId,
            TableNameConstants.MED_MEDICATION_DISPENSE,
            distinct,
            List.of(ChargeItemStatus.PENDING_SETTLEMENT.getCode()));
    }

    /**
     * 按就诊标识查询收费明细（DM-ADM-011 §6.2.3）。
     *
     * @param cmd 查询命令（encounterId 必填；可选过滤见 {@link #encounterScopedChargeItemSpecification}）
     * @return 收费明细列表
     */
    public List<ChargeItem> listChargeItemsByEncounterId(ChargeItemQueryCommand cmd) {
        List<ChargeItem> rows;
        if (this.useEncounterScopedChargeItemSpecification(cmd)) {
            rows = repository.findAll(
                this.encounterScopedChargeItemSpecification(cmd),
                Sort.by(Sort.Order.desc(FieldNameConstants.OCCURRENCE_TIME)));
        } else {
            rows = repository.findAllByEncounterId(cmd.getEncounterId());
        }
        if (Boolean.TRUE.equals(cmd.getExcludeSupersededSettledLines())) {
            rows = this.filterOutSupersededSettledLinesGroupedByEncounter(rows);
        }
        return rows;
    }

    /**
     * 按就诊 + 产品锚点加载收费明细（门诊发药代煎费回显等，禁止为筛一项而拉全就诊费用）。
     *
     * <p>仅本聚合 {@link ChargeItemRepository}；产品表名/主键由调用方解析后传入（B.6.4）。</p>
     *
     * @param encounterId  就诊主键；空则返回空列表
     * @param productTable 产品物理表名；空则返回空列表
     * @param productId    产品主键；空则返回空列表
     * @return 匹配明细；无命中时为空列表
     */
    public List<ChargeItem> listChargeItemsByEncounterAndProduct(
        Long encounterId,
        String productTable,
        Long productId
    ) {
        // Gate：三要素缺一则无法下推产品锚点查询
        if (encounterId == null || productId == null || StringUtils.isEmpty(productTable)) {
            return new ArrayList<>();
        }
        List<ChargeItem> rows = repository.findAllByEncounterIdAndProductTableAndProductId(
            encounterId, productTable.trim(), productId);
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }
        return rows;
    }

    /**
     * 半退/住院反结保留链：按源已结算行主键批量解析链末端有效再生行（BR-REF-020 / BR-IP-RVS-011）。
     *
     * <p>支持多轮结算/反结：沿 {@code refund_id} 向上追溯，凡链上祖先存在有效再生叶则映射至该叶主键。</p>
     *
     * @param encounterId           就诊标识（收敛同就诊）
     * @param sourceChargeItemIds   源 {@code SETTLED} 行主键集合
     * @return 源行主键 → 链末端有效再生行主键；无命中时返回空 Map
     */
    public Map<Long, Long> mapPreservedNewChargeItemIdBySourceId(Long encounterId, Collection<Long> sourceChargeItemIds) {
        if (encounterId == null || sourceChargeItemIds == null || sourceChargeItemIds.isEmpty()) {
            return Collections.emptyMap();
        }
        LinkedHashSet<Long> distinctSources = new LinkedHashSet<>();
        for (Long sourceId : sourceChargeItemIds) {
            if (sourceId != null) {
                distinctSources.add(sourceId);
            }
        }
        if (distinctSources.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Long> ancestorToLeaf = regenerationChainSupport.mapSettledAncestorToActiveRegenerationLeafId(encounterId);
        Map<Long, Long> sourceToNew = new LinkedHashMap<>();
        for (Long sourceId : distinctSources) {
            Long leafId = ancestorToLeaf.get(sourceId);
            if (leafId != null) {
                sourceToNew.put(sourceId, leafId);
            }
        }
        return sourceToNew;
    }

    /**
     * 反结 Pre：源 {@code SETTLED} 行是否已存在有效再生行（禁止重复反结）。
     *
     * @param encounterId         就诊标识
     * @param sourceChargeItemIds 源已结算收费明细主键
     * @return 存在任一有效再生行时 true
     */
    public boolean existsActiveRegenerationForSourceChargeItemIds(Long encounterId, Collection<Long> sourceChargeItemIds) {
        Map<Long, Long> activeBySource = this.mapPreservedNewChargeItemIdBySourceId(encounterId, sourceChargeItemIds);
        return !activeBySource.isEmpty();
    }

    /**
     * 读侧过滤：排除已被半退/住院反结再生替代的源 {@code SETTLED} 行（IF-DRA-01 / BR-REF-020 / BR-IP-RVS-011）。
     *
     * @param encounterId 就诊标识
     * @param rows        已装载收费行
     * @return 过滤后的列表；入参为空时原样返回
     */
    public List<ChargeItem> filterOutSupersededSettledLines(Long encounterId, List<ChargeItem> rows) {
        if (rows == null || rows.isEmpty()) {
            return rows;
        }
        Set<Long> supersededAncestorIds = regenerationChainSupport.collectSupersededSettledAncestorIds(encounterId);
        if (supersededAncestorIds.isEmpty()) {
            return rows;
        }
        List<ChargeItem> kept = new ArrayList<>();
        for (ChargeItem row : rows) {
            if (row == null) {
                continue;
            }
            if (ChargeItemStatus.SETTLED.getCode().equals(row.getStatusEnum())
                && row.getId() != null
                && supersededAncestorIds.contains(row.getId())) {
                continue;
            }
            kept.add(row);
        }
        return kept;
    }

    /**
     * 读侧过滤：按就诊分组排除已被半退/住院反结再生替代的源 {@code SETTLED} 行（BR-REF-020 / BR-IP-RVS-011）。
     *
     * @param rows 已装载收费行（可跨就诊）
     * @return 过滤后的列表；入参为空时原样返回
     */
    public List<ChargeItem> filterOutSupersededSettledLinesGroupedByEncounter(List<ChargeItem> rows) {
        if (rows == null || rows.isEmpty()) {
            return rows;
        }
        Map<Long, List<ChargeItem>> byEncounterId = new LinkedHashMap<>();
        for (ChargeItem row : rows) {
            if (row == null || row.getEncounterId() == null) {
                continue;
            }
            byEncounterId.computeIfAbsent(row.getEncounterId(), key -> new ArrayList<>()).add(row);
        }
        List<ChargeItem> filtered = new ArrayList<>();
        for (Map.Entry<Long, List<ChargeItem>> entry : byEncounterId.entrySet()) {
            filtered.addAll(this.filterOutSupersededSettledLines(entry.getKey(), entry.getValue()));
        }
        return filtered;
    }

    /**
     * 门诊退费执行：解析 {@code completeOutpatientBillRefund} 应收口的收费明细主键（拟退行 + 被替代源行）。
     *
     * @param encounterId                    就诊标识
     * @param paymentSnapshotChargeItemIds   原支付挂载快照中的收费项主键
     * @return 去重后的主键列表（保持插入顺序）
     */
    public List<Long> resolveChargeItemIdsForOutpatientRefundBillCompletion(
        Long encounterId,
        Collection<Long> paymentSnapshotChargeItemIds
    ) {
        if (encounterId == null || paymentSnapshotChargeItemIds == null || paymentSnapshotChargeItemIds.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<Long> snapshotIds = new LinkedHashSet<>();
        for (Long id : paymentSnapshotChargeItemIds) {
            if (id != null) {
                snapshotIds.add(id);
            }
        }
        if (snapshotIds.isEmpty()) {
            return Collections.emptyList();
        }
        ChargeItemQueryCommand loadCmd = new ChargeItemQueryCommand();
        loadCmd.setIds(new ArrayList<>(snapshotIds));
        List<ChargeItem> snapshotRows = this.loadChargeItemsByIds(loadCmd);
        LinkedHashSet<Long> settledInSnapshot = new LinkedHashSet<>();
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        for (ChargeItem row : snapshotRows) {
            if (!encounterId.equals(row.getEncounterId())) {
                continue;
            }
            if (ChargeItemStatus.REFUNDING.getCode().equals(row.getStatusEnum())) {
                result.add(row.getId());
            }
            if (ChargeItemStatus.SETTLED.getCode().equals(row.getStatusEnum())) {
                settledInSnapshot.add(row.getId());
            }
        }
        Map<Long, Long> superseded = this.mapPreservedNewChargeItemIdBySourceId(encounterId, settledInSnapshot);
        for (Long settledId : settledInSnapshot) {
            if (superseded.containsKey(settledId)) {
                result.add(settledId);
            }
        }
        return new ArrayList<>(result);
    }

    /**
     * 按就诊主键集合批量读取收费明细（挂号列表等一页多就诊场景）。
     *
     * <p>一次 IN 查询整页涉及就诊下的全部明细，调用方在应用层按 service_table/service_id 锚点筛选。</p>
     *
     * @param cmd 查询命令（encounterIds 必填且非空）
     * @return 收费明细列表；入参为空集合时返回空列表、不落库
     */
    public List<ChargeItem> listChargeItemsByEncounterIds(ChargeItemQueryCommand cmd) {
        Collection<Long> encounterIds = cmd.getEncounterIds();
        if (encounterIds == null || encounterIds.isEmpty()) {
            return new ArrayList<>();
        }
        // 批量 IN，避免挂号列表对每行就诊逐条查询（A.9.1）。
        List<ChargeItem> rows = repository.findAllByEncounterIdIn(encounterIds);
        if (Boolean.TRUE.equals(cmd.getExcludeSupersededSettledLines())) {
            rows = this.filterOutSupersededSettledLinesGroupedByEncounter(rows);
        }
        return rows;
    }

    /**
     * 门诊退费队列：批量加载页内就诊的拟退/已退收费明细（排除挂号锚点，BR-REF-002）。
     *
     * @param encounterIds 就诊主键集合
     * @return 收费明细列表；入参为空时返回空列表
     */
    public List<ChargeItem> listRefundRelatedChargeItemsByEncounterIds(Collection<Long> encounterIds) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> statusCodes = new ArrayList<>(2);
        statusCodes.add(ChargeItemStatus.REFUNDING.getCode());
        statusCodes.add(ChargeItemStatus.REFUNDED.getCode());
        return repository.findAllByEncounterIdInAndStatusEnumInAndServiceTableNot(
            encounterIds,
            statusCodes,
            TableNameConstants.ADM_HEALTHCARE_SERVICE);
    }

    /**
     * 费用明细视图查询（DM-ADM-011 §6.2.4；门诊场景）。
     *
     * <p>当前唯一调用方为门诊收银 IF-OPC-03；与 {@link #listChargeItemsByEncounterId} 共用就诊维度规格过滤。</p>
     *
     * @param cmd 查询命令（encounterId 必填；可选过滤同就诊列表口径）
     * @return 收费明细列表
     */
    public List<ChargeItem> queryCostDetails(ChargeItemQueryCommand cmd) {
        List<ChargeItem> rows;
        if (this.useEncounterScopedChargeItemSpecification(cmd)) {
            rows = repository.findAll(
                this.encounterScopedChargeItemSpecification(cmd),
                Sort.by(Sort.Order.desc(FieldNameConstants.OCCURRENCE_TIME)));
        } else {
            rows = repository.findAllByEncounterId(cmd.getEncounterId());
        }
        if (Boolean.TRUE.equals(cmd.getExcludeSupersededSettledLines())) {
            rows = this.filterOutSupersededSettledLinesGroupedByEncounter(rows);
        }
        return rows;
    }

    /**
     * 住院费用明细视图查询（DM-ADM-011 §6.2.4；住院场景）。
     *
     * <p>与 {@link #queryCostDetails} 共用就诊维度规格过滤；{@code excludeSupersededSettledLines=true} 时
     * 排除反结/半退再生链上已被有效叶替代的祖先 {@code SETTLED} 行（BR-REF-020 / BR-IP-RVS-011）。</p>
     *
     * @param cmd 查询命令（encounterId 必填；contextEnum 建议传入住院上下文值）
     * @return 收费明细列表
     */
    public List<ChargeItem> queryInpatientCostDetails(ChargeItemQueryCommand cmd) {
        List<ChargeItem> rows;
        if (this.useEncounterScopedChargeItemSpecification(cmd)) {
            rows = repository.findAll(
                this.encounterScopedChargeItemSpecification(cmd),
                Sort.by(Sort.Order.desc(FieldNameConstants.OCCURRENCE_TIME)));
        } else if (cmd.getContextEnum() != null && StringUtils.isNotEmpty(cmd.getContextEnum().trim())) {
            rows = repository.findAllByEncounterIdAndContextEnum(cmd.getEncounterId(), cmd.getContextEnum().trim());
        } else {
            rows = repository.findAllByEncounterId(cmd.getEncounterId());
        }
        if (Boolean.TRUE.equals(cmd.getExcludeSupersededSettledLines())) {
            rows = this.filterOutSupersededSettledLinesGroupedByEncounter(rows);
        }
        return rows;
    }

    /**
     * 查询收费项及定价关联（DM-ADM-011 §6.2.6；含组套子项）。
     *
     * @param cmd 查询命令（encounterId 必填）
     * @return 收费明细列表（含父行与子行，应用层组装为组套树）
     */
    public List<ChargeItem> listChargeItemDefInfoWithChildren(ChargeItemQueryCommand cmd) {
        return repository.findAllByEncounterId(cmd.getEncounterId());
    }

    /**
     * 按结算单读取收费明细（DM-ADM-011 §6.2.7）。
     *
     * @param cmd 查询命令（encounterId 必填；accountId 可选作过渡过滤）
     * @return 收费明细列表
     */
    public List<ChargeItem> listChargeItemsByPaymentBill(ChargeItemQueryCommand cmd) {
        // 过渡方案：按账户过滤；精确实现需读模型（BR-CHG-QRY-002）。
        if (cmd.getAccountId() != null) {
            return repository.findAllByEncounterIdAndAccountId(cmd.getEncounterId(), cmd.getAccountId());
        }
        return repository.findAllByEncounterId(cmd.getEncounterId());
    }

    /**
     * 按开票场景读取收费明细（DM-ADM-011 §6.2.8）。
     *
     * @param cmd 查询命令（encounterId 必填；statusEnum 建议传入已结算状态值）
     * @return 收费明细列表
     */
    public List<ChargeItem> listChargeItemsForInvoice(ChargeItemQueryCommand cmd) {
        // 开票场景按状态过滤，调用方传入已结算状态码。
        if (cmd.getStatusEnum() != null) {
            return repository.findAllByEncounterIdAndStatusEnum(cmd.getEncounterId(), cmd.getStatusEnum());
        }
        return repository.findAllByEncounterId(cmd.getEncounterId());
    }

    /**
     * 按住院结算单读取收费明细（DM-ADM-011 §6.2.9）。
     *
     * @param cmd 查询命令（encounterId 必填；contextEnum 可选作住院上下文过渡过滤）
     * @return 收费明细列表
     */
    public List<ChargeItem> listChargeItemsByInpatientBill(ChargeItemQueryCommand cmd) {
        // 过渡方案：按住院上下文过滤；精确实现需读模型（BR-CHG-QRY-002）。
        if (cmd.getContextEnum() != null) {
            return repository.findAllByEncounterIdAndContextEnum(cmd.getEncounterId(), cmd.getContextEnum());
        }
        return repository.findAllByEncounterId(cmd.getEncounterId());
    }

    /**
     * 批量加载门诊收银队列行挂载所需的收费明细（仅 {@link ChargeItemStatus#PLANNED}/{@link ChargeItemStatus#SETTLED}；排除挂号锚点）。
     *
     * <p>与 IF-OPC-03 同口径叠加 {@code BR-REF-020}：排除半退再生链上已被替代的源 {@code SETTLED} 行，
     * 避免队列汇总误判为「待收费」而明细区已全部展示为已结算。</p>
     *
     * @param encounterIds         就诊主键集合
     * @param excludedServiceTable 排除表物理名（trim 后须非空）
     * @return 平铺明细列表
     */
    public List<ChargeItem> listOutpatientCashierChargeItemsForEncounters(
        Collection<Long> encounterIds,
        String excludedServiceTable) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            return new ArrayList<>();
        }
        if (excludedServiceTable == null || excludedServiceTable.trim().isEmpty()) {
            throw new IllegalArgumentException("excludedServiceTable required");
        }
        String excluded = excludedServiceTable.trim();
        Specification<ChargeItem> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(root.get(FieldNameConstants.ENCOUNTER_ID).in(encounterIds));
            predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(this.outpatientCashierBillableStatusCodes()));
            predicates.add(cb.notEqual(root.get(FieldNameConstants.SERVICE_TABLE), excluded));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return this.filterOutSupersededSettledLinesGroupedByEncounter(repository.findAll(spec));
    }

    /**
     * 门诊收银队列口径下的收费状态集合（{@link ChargeItemStatus#PLANNED} / {@link ChargeItemStatus#SETTLED}）。
     *
     * @return 持久化码列表（去重保序）
     */
    public List<String> outpatientCashierBillableStatusCodes() {
        LinkedHashSet<String> codes = new LinkedHashSet<>(4);
        codes.add(ChargeItemStatus.PLANNED.getCode());
        codes.add(ChargeItemStatus.SETTLED.getCode());
        return new ArrayList<>(codes);
    }

    /**
     * 就诊是否已结清（IF-PE-CHG-07 / REG-08 同族）：存在 {@link ChargeItemStatus#SETTLED} 且无 {@link ChargeItemStatus#PLANNED}。
     *
     * @param encounterId 就诊主键
     * @return 有收银口径事实且全部已结时为 {@code true}；无事实或有待收时为 {@code false}
     */
    public boolean isEncounterPaid(Long encounterId) {
        if (encounterId == null) {
            return false;
        }
        ChargeItemQueryCommand cmd = new ChargeItemQueryCommand();
        cmd.setEncounterId(encounterId);
        cmd.setStatusEnumIn(this.outpatientCashierBillableStatusCodes());
        List<ChargeItem> rows = this.listChargeItemsByEncounterId(cmd);
        if (rows.isEmpty()) {
            return false;
        }
        String settledCode = ChargeItemStatus.SETTLED.getCode();
        String plannedCode = ChargeItemStatus.PLANNED.getCode();
        boolean hasSettled = false;
        for (ChargeItem row : rows) {
            if (row == null) {
                continue;
            }
            String status = row.getStatusEnum();
            if (plannedCode.equals(status)) {
                return false;
            }
            if (settledCode.equals(status)) {
                hasSettled = true;
            }
        }
        return hasSettled;
    }

    /**
     * 门诊退费队列口径下的收费状态集合（{@link ChargeItemStatus#REFUNDING}）。
     *
     * @return 持久化码列表
     */
    public List<String> outpatientRefundQueueStatusCodes() {
        return List.of(ChargeItemStatus.REFUNDING.getCode());
    }

    /**
     * 就诊维度收费明细规格：合并排除锚点、状态多值/单值、收费上下文等过滤。
     *
     * @param cmd 查询命令
     * @return JPA Specification
     */
    private Specification<ChargeItem> encounterScopedChargeItemSpecification(ChargeItemQueryCommand cmd) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get(FieldNameConstants.ENCOUNTER_ID), cmd.getEncounterId()));
            String excludedTable = this.normalizedExcludedServiceTable(cmd);
            if (excludedTable != null) {
                predicates.add(cb.notEqual(root.get(FieldNameConstants.SERVICE_TABLE), excludedTable));
            }
            Collection<String> statusIn = this.normalizedStatusEnumIn(cmd);
            if (statusIn != null && !statusIn.isEmpty()) {
                predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(statusIn));
            } else {
                if (cmd.getStatusEnum() != null && StringUtils.isNotEmpty(cmd.getStatusEnum().trim())) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), cmd.getStatusEnum().trim()));
                }
            }
            if (cmd.getContextEnum() != null && StringUtils.isNotEmpty(cmd.getContextEnum().trim())) {
                predicates.add(cb.equal(root.get(FieldNameConstants.CONTEXT_ENUM), cmd.getContextEnum().trim()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 判断是否走就诊维度规格查询（含门诊收银状态/排除锚点/上下文过滤）。
     *
     * @param cmd 查询命令
     * @return true 时使用 {@link #encounterScopedChargeItemSpecification}
     */
    private boolean useEncounterScopedChargeItemSpecification(ChargeItemQueryCommand cmd) {
        if (this.normalizedExcludedServiceTable(cmd) != null) {
            return true;
        }
        if (this.normalizedStatusEnumIn(cmd) != null) {
            return true;
        }
        if (cmd.getContextEnum() != null && StringUtils.isNotEmpty(cmd.getContextEnum().trim())) {
            return true;
        }
        if (cmd.getStatusEnum() != null && StringUtils.isNotEmpty(cmd.getStatusEnum().trim())) {
            return true;
        }
        if (Boolean.TRUE.equals(cmd.getExcludeSupersededSettledLines())) {
            return true;
        }
        if (cmd.getBusinessDateFrom() != null || cmd.getBusinessDateTo() != null) {
            return true;
        }
        return false;
    }

    /**
     * 解析命令上的 {@code excludedServiceTable}：null 或空白视为「不排除」。
     *
     * @param cmd 查询命令
     * @return trim 后的排除表名；不需要排除时 {@code null}
     */
    private String normalizedExcludedServiceTable(ChargeItemQueryCommand cmd) {
        if (cmd.getExcludedServiceTable() == null) {
            return null;
        }
        String trimmed = cmd.getExcludedServiceTable().trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed;
    }

    /**
     * 解析 {@code statusEnumIn}：空集合视为未启用。
     *
     * @param cmd 查询命令
     * @return 非空持久化码集合；未启用时 {@code null}
     */
    private Collection<String> normalizedStatusEnumIn(ChargeItemQueryCommand cmd) {
        Collection<String> raw = cmd.getStatusEnumIn();
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String code : raw) {
            if (code != null && StringUtils.isNotEmpty(code.trim())) {
                out.add(code.trim());
            }
        }
        if (out.isEmpty()) {
            return null;
        }
        return out;
    }

    /**
     * 列出存在待结算收费行的住院就诊主键（AS-IP-FIN-003 结算队列「有待结算」筛选）。
     *
     * @return 去重后的 encounterId 列表；无匹配时空列表
     */
    public List<Long> listEncounterIdsWithPendingSettlementCharges() {
        CriteriaBuilder cb = this.entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<ChargeItem> root = cq.from(ChargeItem.class);
        cq.select(root.get(FieldNameConstants.ENCOUNTER_ID)).distinct(true);
        cq.where(
            cb.and(
                cb.equal(root.get(FieldNameConstants.STATUS_ENUM), ChargeItemStatus.PENDING_SETTLEMENT.getCode()),
                cb.isFalse(root.get(FieldNameConstants.IS_DELETED))));
        List<Long> rows = this.entityManager.createQuery(cq).getResultList();
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        for (Long encounterId : rows) {
            if (encounterId != null) {
                distinct.add(encounterId);
            }
        }
        return new ArrayList<>(distinct);
    }

    /**
     * 门诊收银/退费队列：分页返回存在指定状态收费事实（排除挂号锚点）的就诊主键。
     *
     * @param query                       查询入参（须含 {@code resolvedEncounterScope}，由应用层 Loader 预解析）
     * @param statusCodes                 收费状态持久化码子集
     * @return 就诊主键分页（按本就诊下命中收费行的最近 {@code occurrence_time} 倒序）
     */
    public Page<Long> pageEncounterIdsForOutpatientChargeQueue(
        OutpatientChargeQueueEncounterQuery query,
        List<String> statusCodes) {
        if (query == null || query.getExcludedServiceTable() == null || query.getExcludedServiceTable().trim().isEmpty()) {
            throw new IllegalArgumentException("outpatient encounter queue requires excludedServiceTable");
        }
        if (statusCodes == null || statusCodes.isEmpty()) {
            throw new IllegalArgumentException("outpatient encounter queue requires statusCodes");
        }
        int pageNo = query.springPageIndex();
        int pageSize;
        if (query.getPageSize() != null && query.getPageSize() > 0) {
            pageSize = query.getBoundedPageSize();
        } else {
            pageSize = GeneralSharedConstants.DEFAULT_QUERY_PAGE_SIZE;
        }
        CriteriaBuilder cb = this.entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> countCq = cb.createQuery(Long.class);
        Root<ChargeItem> countRoot = countCq.from(ChargeItem.class);
        Predicate chargeFacts = this.chargeItemMatchesOutpatientQueueFacts(countRoot, cb, query, statusCodes);
        List<Long> encounterScope = query.getResolvedEncounterScope();
        if (encounterScope != null && encounterScope.isEmpty()) {
            PageRequest pageable = PageRequest.of(pageNo, pageSize);
            return new PageImpl<>(Collections.emptyList(), pageable, 0L);
        }
        Predicate encounterScopePredicate = this.buildEncounterScopePredicate(countRoot, cb, encounterScope);
        countCq.select(cb.countDistinct(countRoot.get(FieldNameConstants.ENCOUNTER_ID)));
        countCq.where(cb.and(chargeFacts, encounterScopePredicate));
        Long totalRow = this.entityManager.createQuery(countCq).getSingleResult();
        long total = 0L;
        if (totalRow != null) {
            total = totalRow.longValue();
        }
        CriteriaQuery<Long> dataCq = cb.createQuery(Long.class);
        Root<ChargeItem> ci = dataCq.from(ChargeItem.class);
        Predicate dataChargeFacts = this.chargeItemMatchesOutpatientQueueFacts(ci, cb, query, statusCodes);
        Predicate dataEncounterScopePredicate = this.buildEncounterScopePredicate(ci, cb, encounterScope);
        dataCq.select(ci.get(FieldNameConstants.ENCOUNTER_ID));
        dataCq.where(cb.and(dataChargeFacts, dataEncounterScopePredicate));
        dataCq.groupBy(ci.get(FieldNameConstants.ENCOUNTER_ID));
        dataCq.orderBy(cb.desc(cb.max(ci.get(FieldNameConstants.OCCURRENCE_TIME))));
        TypedQuery<Long> typed = this.entityManager.createQuery(dataCq);
        typed.setFirstResult(pageNo * pageSize);
        typed.setMaxResults(pageSize);
        List<Long> encounterIds = typed.getResultList();
        PageRequest pageable = PageRequest.of(pageNo, pageSize);
        return new PageImpl<>(encounterIds, pageable, total);
    }

    /**
     * 门诊收费队列 Criteria：收费行须匹配给定状态且排除指定 service_table。
     *
     * @param ci          ChargeItem 根
     * @param cb          CriteriaBuilder
     * @param query       队列查询（含 excludedServiceTable）
     * @param statusCodes 允许的 charge_item.status_enum
     * @return AND 谓词
     */
    private Predicate chargeItemMatchesOutpatientQueueFacts(
        Root<ChargeItem> ci,
        CriteriaBuilder cb,
        OutpatientChargeQueueEncounterQuery query,
        List<String> statusCodes) {
        String excluded = query.getExcludedServiceTable().trim();
        return cb.and(
            ci.get(FieldNameConstants.STATUS_ENUM).in(statusCodes),
            cb.notEqual(ci.get(FieldNameConstants.SERVICE_TABLE), excluded)
        );
    }

    /**
     * 收费行 encounter 范围谓词：空范围时构造恒 false 避免全表扫描。
     *
     * @param ci                     ChargeItem 根
     * @param cb                     CriteriaBuilder
     * @param resolvedEncounterScope 已解析就诊 id 列表
     * @return encounter_id IN 或恒 false
     */
    private Predicate buildEncounterScopePredicate(
        Root<ChargeItem> ci,
        CriteriaBuilder cb,
        List<Long> resolvedEncounterScope) {
        if (resolvedEncounterScope == null || resolvedEncounterScope.isEmpty()) {
            return cb.equal(cb.literal(1), cb.literal(0));
        }
        return ci.get(FieldNameConstants.ENCOUNTER_ID).in(resolvedEncounterScope);
    }

    /**
     * 滚费幂等探测：同 billDate 是否已存在 auto-roll-fees 收费行（AS-IP-NUR-013 BR-IP-ARG-010；DM-ADM-011 §6.2.24）。
     *
     * <p>匹配 {@code encounterId + chargeDefinitionId + generate_source=auto-roll-fees}，且 {@code occurrence_time}
     * 落在 billDate 自然日（含边界）。已软删行不参与。</p>
     *
     * @param encounterId        就诊主键
     * @param chargeDefinitionId 定价定义主键
     * @param billDate           滚费业务日
     * @return 已存在时 {@code true}
     */
    public boolean existsAutoRollChargeItemForBillDate(
        Long encounterId,
        Long chargeDefinitionId,
        LocalDate billDate) {
        // 入参缺任一维度时视为未命中，避免误拦合法落账。
        if (encounterId == null || chargeDefinitionId == null || billDate == null) {
            return false;
        }
        LocalDateTime dayStart = billDate.atStartOfDay();
        LocalDateTime dayEnd = billDate.plusDays(1).atStartOfDay();
        Specification<ChargeItem> specification = (root, criteriaQuery, cb) -> cb.and(
            cb.isFalse(root.get(FieldNameConstants.IS_DELETED)),
            cb.equal(root.get(FieldNameConstants.ENCOUNTER_ID), encounterId),
            cb.equal(root.get(FieldNameConstants.DEFINITION_ID), chargeDefinitionId),
            cb.equal(root.get(FieldNameConstants.GENERATE_SOURCE_ENUM), GenerateSource.AUTO_ROLL_FEES.getCode()),
            cb.greaterThanOrEqualTo(root.get(FieldNameConstants.OCCURRENCE_TIME), dayStart),
            cb.lessThan(root.get(FieldNameConstants.OCCURRENCE_TIME), dayEnd));
        return repository.count(specification) > 0;
    }

    /**
     * 滚费幂等探测：同 billDate 是否已存在锚定指定诊疗申请的 order-execute 收费行（AS-IP-NUR-013 BR-IP-ARG-010）。
     *
     * <p>匹配 {@code serviceId + generate_source=order-execute}，且 {@code occurrence_time} 落在 billDate 自然日。</p>
     *
     * @param serviceRequestId 诊疗申请主键（{@code wkf_service_request.id}）
     * @param billDate           滚费业务日
     * @return 已存在时 {@code true}
     */
    public boolean existsOrderExecuteChargeForServiceRequestBillDate(
        Long serviceRequestId,
        LocalDate billDate) {
        if (serviceRequestId == null || billDate == null) {
            return false;
        }
        LocalDateTime dayStart = billDate.atStartOfDay();
        LocalDateTime dayEnd = billDate.plusDays(1).atStartOfDay();
        Specification<ChargeItem> specification = (root, criteriaQuery, cb) -> cb.and(
            cb.isFalse(root.get(FieldNameConstants.IS_DELETED)),
            cb.equal(root.get(FieldNameConstants.SERVICE_TABLE), TableNameConstants.WKF_SERVICE_REQUEST),
            cb.equal(root.get(FieldNameConstants.SERVICE_ID), serviceRequestId),
            cb.equal(root.get(FieldNameConstants.GENERATE_SOURCE_ENUM), GenerateSource.ORDER_EXECUTE.getCode()),
            cb.greaterThanOrEqualTo(root.get(FieldNameConstants.OCCURRENCE_TIME), dayStart),
            cb.lessThan(root.get(FieldNameConstants.OCCURRENCE_TIME), dayEnd));
        return repository.count(specification) > 0;
    }

    /**
     * 列出诊疗申请下医嘱执行来源收费明细（AS-IP-NUR-013 IF-IP-ARG-09 床位滚费展开）。
     *
     * <p>匹配 {@code service_table=wkf_service_request + serviceId + generate_source=order-execute}；
     * 可选按 occurrence 自然日闭区间过滤；按 occurrence 倒序。</p>
     *
     * @param serviceRequestId 诊疗申请主键
     * @param dateFrom         业务日起（含）；null 表示不限起
     * @param dateTo           业务日止（含）；null 表示不限止
     * @return 命中收费行；无则空列表
     */
    public List<ChargeItem> listOrderExecuteChargesByServiceRequestId(
        Long serviceRequestId,
        LocalDate dateFrom,
        LocalDate dateTo) {
        if (serviceRequestId == null) {
            return Collections.emptyList();
        }
        LocalDateTime rangeStart = null;
        if (dateFrom != null) {
            rangeStart = dateFrom.atStartOfDay();
        }
        LocalDateTime rangeEndExclusive = null;
        if (dateTo != null) {
            rangeEndExclusive = dateTo.plusDays(1).atStartOfDay();
        }
        LocalDateTime finalRangeStart = rangeStart;
        LocalDateTime finalRangeEndExclusive = rangeEndExclusive;
        Specification<ChargeItem> specification = (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.SERVICE_TABLE), TableNameConstants.WKF_SERVICE_REQUEST));
            predicates.add(cb.equal(root.get(FieldNameConstants.SERVICE_ID), serviceRequestId));
            predicates.add(cb.equal(
                root.get(FieldNameConstants.GENERATE_SOURCE_ENUM), GenerateSource.ORDER_EXECUTE.getCode()));
            // 可选业务日起止：occurrence 落在 [dateFrom, dateTo] 自然日
            if (finalRangeStart != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                    root.get(FieldNameConstants.OCCURRENCE_TIME), finalRangeStart));
            }
            if (finalRangeEndExclusive != null) {
                predicates.add(cb.lessThan(
                    root.get(FieldNameConstants.OCCURRENCE_TIME), finalRangeEndExclusive));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ChargeItem> rows = repository.findAll(specification);
        rows.sort(Comparator
            .comparing(ChargeItem::getOccurrenceTime, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(ChargeItem::getId, Comparator.nullsLast(Comparator.reverseOrder())));
        return rows;
    }

    // -------------------------------------------------------------------------
    // AS-OP-FIN-005 门诊费用明细读侧（DM-ADM-011 §6.2.25～6.2.26；仅本聚合 Repository）
    // -------------------------------------------------------------------------

    /**
     * 门诊费用明细：补全 payment {@code charge_item_ids} 快照截断漏挂的已结/已退行（DM-ADM-011 §6.2.25 扩展）。
     *
     * <p>当 payment 快照 JSON 截断时，Support 按就诊 + 状态 + 收款人 SQL 补全候选行（AS-OP-FIN-005 **BR-OP-FDT-009**）。
     * 收款人匹配 {@code performer_id}（与 **BR-OPC-016** 结算写入口径一致）。</p>
     *
     * @param encounterIds          bill_date 筛选命中 payment 涉及的就诊主键
     * @param excludedChargeItemIds 已由快照解析的主键；空集合表示不排除
     * @param allowedStatusCodes    收退 Tab 映射的状态白名单（SETTLED / REFUNDED）
     * @param entererMatchIds       收费人白名单；非空时优先于 {@code cashierId}
     * @param cashierId             单值收费人；{@code entererMatchIds} 为空时生效
     * @return 补全候选收费明细；无命中时空列表
     */
    public List<ChargeItem> listOutpatientFeeDetailAugmentChargeItems(
        Collection<Long> encounterIds,
        Collection<Long> excludedChargeItemIds,
        List<String> allowedStatusCodes,
        List<Long> entererMatchIds,
        Long cashierId) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            return Collections.emptyList();
        }
        if (allowedStatusCodes == null || allowedStatusCodes.isEmpty()) {
            return Collections.emptyList();
        }
        Specification<ChargeItem> specification = (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(root.get(FieldNameConstants.ENCOUNTER_ID).in(encounterIds));
            predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(allowedStatusCodes));
            if (excludedChargeItemIds != null && !excludedChargeItemIds.isEmpty()) {
                // 排除快照已解析主键，仅补全截断漏挂行（BR-OP-FDT-009）
                predicates.add(cb.not(root.get(FieldNameConstants.ID).in(excludedChargeItemIds)));
            }
            if (entererMatchIds != null && !entererMatchIds.isEmpty()) {
                // 多选收费人（含「全部」Tab 时不进入此分支）
                predicates.add(root.get(FieldNameConstants.PERFORMER_ID).in(entererMatchIds));
            } else if (cashierId != null) {
                predicates.add(cb.equal(root.get(FieldNameConstants.PERFORMER_ID), cashierId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return repository.findAll(specification);
    }

    /**
     * 门诊费用明细：按就诊块分页返回候选行（DM-ADM-011 §6.2.25；AS-OP-FIN-005 IF-OP-FDT-02/03）。
     *
     * <p>块内为同一就诊下全部命中 chargeItem 行；块间按块内最新 {@code bill_date} 倒序（**BR-OP-FDT-014**）。
     * 须先经 {@link com.openhis.health.application.outpatient.financial.feedetail.support.OutpatientFeeDetailLoader}
     * 写入 payment 快照与 {@link OutpatientFeeDetailPageQuery#getResolvedPaymentChargeItemLinks()}。</p>
     *
     * @param query 须含 resolved payment 上下文；分页缺省见 {@link OutpatientFeeDetailConstants}
     * @return 就诊块分页；scope 为空时返回空页（total=0）
     */
    public Page<OutpatientFeeDetailEncounterBlockQueryModel> pageOutpatientFeeDetailEncounterBlocks(
        OutpatientFeeDetailPageQuery query) {
        List<OutpatientFeeDetailChargeRowQueryModel> allRows = this.listOutpatientFeeDetailChargeRows(query);
        if (allRows.isEmpty()) {
            int pageNo = this.resolveOutpatientFeeDetailPageNo(query);
            int pageSize = this.resolveOutpatientFeeDetailPageSize(query);
            PageRequest pageable = PageRequest.of(pageNo, pageSize);
            return new PageImpl<>(Collections.emptyList(), pageable, 0L);
        }
        // 按 encounter_id 分组；块内/块间排序均依赖 payment bill_date（BR-OP-FDT-014）
        Map<Long, List<OutpatientFeeDetailChargeRowQueryModel>> rowsByEncounter = new LinkedHashMap<>();
        for (OutpatientFeeDetailChargeRowQueryModel row : allRows) {
            if (row == null || row.getChargeItem() == null) {
                continue;
            }
            Long encounterId = row.getChargeItem().getEncounterId();
            if (encounterId == null) {
                continue;
            }
            List<OutpatientFeeDetailChargeRowQueryModel> bucket = rowsByEncounter.get(encounterId);
            if (bucket == null) {
                bucket = new ArrayList<>();
                rowsByEncounter.put(encounterId, bucket);
            }
            bucket.add(row);
        }
        List<Long> encounterOrder = new ArrayList<>(rowsByEncounter.keySet());
        encounterOrder.sort((leftId, rightId) -> {
            LocalDateTime leftMax = this.maxBillDateForEncounterRows(rowsByEncounter.get(leftId));
            LocalDateTime rightMax = this.maxBillDateForEncounterRows(rowsByEncounter.get(rightId));
            // 块间倒序：无 bill_date 的块沉底
            if (leftMax == null && rightMax == null) {
                return 0;
            }
            if (leftMax == null) {
                return 1;
            }
            if (rightMax == null) {
                return -1;
            }
            return rightMax.compareTo(leftMax);
        });
        int pageNo = this.resolveOutpatientFeeDetailPageNo(query);
        int pageSize = this.resolveOutpatientFeeDetailPageSize(query);
        long total = encounterOrder.size();
        int fromIndex = pageNo * pageSize;
        if (fromIndex >= encounterOrder.size()) {
            // 页码超出末页时返回空 content、保留 total 供前端翻页控件
            PageRequest pageable = PageRequest.of(pageNo, pageSize);
            return new PageImpl<>(Collections.emptyList(), pageable, total);
        }
        int toIndex = Math.min(fromIndex + pageSize, encounterOrder.size());
        List<OutpatientFeeDetailEncounterBlockQueryModel> blocks = new ArrayList<>();
        for (int index = fromIndex; index < toIndex; index++) {
            Long encounterId = encounterOrder.get(index);
            List<OutpatientFeeDetailChargeRowQueryModel> lines = rowsByEncounter.get(encounterId);
            if (lines == null) {
                continue;
            }
            // 块内按 bill_date 倒序，与 AS 展示一致
            lines.sort(Comparator.comparing(
                row -> {
                    if (row.getPaymentContext() != null) {
                        return row.getPaymentContext().getBillDate();
                    }
                    return null;
                },
                Comparator.nullsLast(Comparator.reverseOrder())));
            OutpatientFeeDetailEncounterBlockQueryModel block = new OutpatientFeeDetailEncounterBlockQueryModel();
            block.setEncounterId(encounterId);
            if (!lines.isEmpty() && lines.get(0).getChargeItem() != null) {
                block.setPatientId(lines.get(0).getChargeItem().getPatientId());
            }
            block.setLines(lines);
            blocks.add(block);
        }
        PageRequest pageable = PageRequest.of(pageNo, pageSize);
        return new PageImpl<>(blocks, pageable, total);
    }

    /**
     * 门诊费用明细：全量命中集的账单金额代数和（DM-ADM-011 §6.2.26；IF-OP-FDT-02 summary）。
     *
     * <p>域层仅汇总账单事实列（原价/应收/折后/行金额/优惠差）；实收、医保/自费分摊、退单展示负号
     * 由应用层 Assembler 在相同 scope 上叠加（**BR-OP-FDT-010～013**）。与当前页 blocks 解耦。</p>
     *
     * @param query 与 {@link #pageOutpatientFeeDetailEncounterBlocks} 相同 scope（忽略分页键）
     * @return 合计读模型；无命中时各字段为 0
     */
    public OutpatientFeeDetailAmountSummaryQueryModel summarizeOutpatientFeeDetailAmounts(
        OutpatientFeeDetailPageQuery query) {
        List<OutpatientFeeDetailChargeRowQueryModel> rows = this.listOutpatientFeeDetailChargeRows(query);
        OutpatientFeeDetailAmountSummaryQueryModel summary = new OutpatientFeeDetailAmountSummaryQueryModel();
        // 全量命中集代数求和，与当前页 blocks 解耦（BR-OP-FDT-013）
        for (OutpatientFeeDetailChargeRowQueryModel row : rows) {
            if (row == null || row.getChargeItem() == null) {
                continue;
            }
            ChargeItem chargeItem = row.getChargeItem();
            BigDecimal baseAmount = this.nullToZero(chargeItem.getBaseAmount());
            BigDecimal receivable = this.resolveOutpatientFeeDetailReceivableAmount(chargeItem);
            BigDecimal discounted = this.resolveOutpatientFeeDetailDiscountedAmount(chargeItem);
            BigDecimal lineAmount = this.nullToZero(chargeItem.getTotalPrice());
            summary.setBaseAmountTotal(summary.getBaseAmountTotal().add(baseAmount));
            summary.setReceivableAmountTotal(summary.getReceivableAmountTotal().add(receivable));
            summary.setDiscountedAmountTotal(summary.getDiscountedAmountTotal().add(discounted));
            summary.setLineAmountTotal(summary.getLineAmountTotal().add(lineAmount));
            BigDecimal discountGap = baseAmount.subtract(receivable);
            if (discountGap.compareTo(BigDecimal.ZERO) > 0) {
                // 原价高于应收的部分计入优惠差（展示列「优惠」）
                summary.setDiscountAmountTotal(summary.getDiscountAmountTotal().add(discountGap));
            }
        }
        return summary;
    }

    /**
     * 门诊费用明细按定价 definition 净汇总（AS-RPT-008；DM-ADM-011 §6.2.32）。
     *
     * <p>筛选与 {@link #pageOutpatientFeeDetailEncounterBlocks} 同 scope；按 {@code definition_id}
     * 累加带 payment 符号的应收/折后/行金额。{@code fin_type_code} 由应用层 enrich。
     * 仅本聚合 Repository。</p>
     *
     * @param query 须含 resolved payment 快照
     * @return 汇总行；无命中为空列表
     */
    public List<FeeDetailFinTypeSummaryQueryModel> summarizeOutpatientFeeDetailByFinType(
        OutpatientFeeDetailPageQuery query) {
        // Pre：复用门诊费用明细候选行装载
        // Asm：按 definition 分桶（见 summarizeFeeDetailRowsByDefinition）
        return this.summarizeFeeDetailRowsByDefinition(this.listOutpatientFeeDetailChargeRows(query));
    }

    /**
     * 住院费用明细就诊块分页（AS-RPT-009；DM-ADM-011 §6.2.33）。
     *
     * <p>读侧规约与门诊费用明细同构：由 Support 写入 IMP 就诊 scope 与 payment 快照后调用。
     * 禁止用护士站单就诊 {@code queryInpatientCostDetails} 替代本报表口径。</p>
     *
     * @param query 与门诊费用明细 PageQuery 同结构（住院域预解析）
     * @return 就诊块分页
     */
    public Page<OutpatientFeeDetailEncounterBlockQueryModel> pageInpatientFeeDetailEncounterBlocks(
        OutpatientFeeDetailPageQuery query) {
        // Pre/Asm：委托同构门诊就诊块分页（scope 已由应用层区分 AMB/IMP）
        return this.pageOutpatientFeeDetailEncounterBlocks(query);
    }

    /**
     * 住院费用明细按定价 definition 净汇总（AS-RPT-009；DM-ADM-011 §6.2.34）。
     *
     * @param query 与住院明细分页同 scope
     * @return 汇总行
     */
    public List<FeeDetailFinTypeSummaryQueryModel> summarizeInpatientFeeDetailByFinType(
        OutpatientFeeDetailPageQuery query) {
        // Pre/Asm：与门诊 fin_type 汇总同实现，仅 scope 不同
        return this.summarizeFeeDetailRowsByDefinition(this.listOutpatientFeeDetailChargeRows(query));
    }

    /**
     * 费用明细候选行按 definition_id 分桶净汇总（门诊/住院共用）。
     *
     * @param rows 已过滤候选行
     * @return 汇总行
     */
    private List<FeeDetailFinTypeSummaryQueryModel> summarizeFeeDetailRowsByDefinition(
        List<OutpatientFeeDetailChargeRowQueryModel> rows) {
        Map<Long, FeeDetailFinTypeSummaryQueryModel> bucketByDefinition = new LinkedHashMap<>();
        for (OutpatientFeeDetailChargeRowQueryModel row : rows) {
            if (row == null || row.getChargeItem() == null) {
                continue;
            }
            ChargeItem chargeItem = row.getChargeItem();
            Long definitionId = chargeItem.getDefinitionId();
            // 无定价锚点无法映射 fin_type，跳过脏行
            if (definitionId == null) {
                continue;
            }
            FeeDetailFinTypeSummaryQueryModel summary = bucketByDefinition.get(definitionId);
            if (summary == null) {
                summary = new FeeDetailFinTypeSummaryQueryModel();
                summary.setDefinitionId(definitionId);
                bucketByDefinition.put(definitionId, summary);
            }
            BigDecimal receivable = this.applyPractitionerWorkloadPaymentSign(
                this.resolveOutpatientFeeDetailReceivableAmount(chargeItem),
                row.getPaymentContext());
            BigDecimal discounted = this.applyPractitionerWorkloadPaymentSign(
                this.resolveOutpatientFeeDetailDiscountedAmount(chargeItem),
                row.getPaymentContext());
            BigDecimal lineAmount = this.applyPractitionerWorkloadPaymentSign(
                this.nullToZero(chargeItem.getTotalPrice()),
                row.getPaymentContext());
            summary.setReceivableAmountTotal(summary.getReceivableAmountTotal().add(receivable));
            summary.setDiscountedAmountTotal(summary.getDiscountedAmountTotal().add(discounted));
            summary.setLineAmountTotal(summary.getLineAmountTotal().add(lineAmount));
            summary.setLineCount(summary.getLineCount() + 1L);
        }
        return new ArrayList<>(bucketByDefinition.values());
    }

    /**
     * 在 payment 预解析 scope 内加载并过滤门诊费用明细候选行（仅本聚合 Repository）。
     *
     * <p>筛选链：SETTLED/REFUNDED → 就诊 scope → 费用大类多选（**BR-OP-FDT-002/006**）。
     * 加载后补全 payment 快照医保占比分母。同请求优先复用 {@link OutpatientFeeDetailPageQuery#getCachedChargeRows()}
     * 与 Support 写入的 {@link OutpatientFeeDetailPageQuery#getPreloadedChargeItemsById()}，避免 page/summary
     * 重复 IN 全量 ChargeItem。</p>
     *
     * @param query 须含 {@link OutpatientFeeDetailPageQuery#getResolvedPaymentChargeItemLinks()}
     * @return 过滤后的候选行；scope 为空时返回空列表
     */
    public List<OutpatientFeeDetailChargeRowQueryModel> listOutpatientFeeDetailChargeRows(
        OutpatientFeeDetailPageQuery query) {
        if (query == null) {
            return Collections.emptyList();
        }
        // Pre：同请求已物化则直接返回，page/summary/导出翻页共用一份候选行
        if (query.getCachedChargeRows() != null) {
            return query.getCachedChargeRows();
        }
        List<OutpatientFeeDetailChargePaymentLinkQueryModel> paymentLinks = query.getResolvedPaymentChargeItemLinks();
        if (paymentLinks == null || paymentLinks.isEmpty()) {
            query.setCachedChargeRows(Collections.emptyList());
            return Collections.emptyList();
        }
        LinkedHashSet<Long> chargeItemIds = new LinkedHashSet<>();
        for (OutpatientFeeDetailChargePaymentLinkQueryModel link : paymentLinks) {
            if (link != null && link.getChargeItemId() != null) {
                chargeItemIds.add(link.getChargeItemId());
            }
        }
        if (chargeItemIds.isEmpty()) {
            query.setCachedChargeRows(Collections.emptyList());
            return Collections.emptyList();
        }
        Map<Long, ChargeItem> chargeItemById = this.resolveOutpatientFeeDetailChargeItemMap(query, chargeItemIds);
        this.enrichOutpatientFeeDetailPaymentSnapshotDenominators(chargeItemById, paymentLinks);
        List<String> allowedStatuses = List.of(
            ChargeItemStatus.SETTLED.getCode(),
            ChargeItemStatus.REFUNDED.getCode());
        List<OutpatientFeeDetailChargeRowQueryModel> rows = new ArrayList<>();
        // 按 payment 快照关联逐条展开；同 chargeItem 可收/退各一行（BR-OP-FDT-011/012）
        for (OutpatientFeeDetailChargePaymentLinkQueryModel link : paymentLinks) {
            if (link == null || link.getChargeItemId() == null || link.getPaymentContext() == null) {
                continue;
            }
            ChargeItem chargeItem = chargeItemById.get(link.getChargeItemId());
            if (chargeItem == null) {
                continue;
            }
            if (!allowedStatuses.contains(chargeItem.getStatusEnum())) {
                continue;
            }
            if (!this.matchesOutpatientFeeDetailEncounterScope(chargeItem, query.getResolvedEncounterScope())) {
                continue;
            }
            if (!this.matchesOutpatientFeeDetailFeeCategory(chargeItem, query.getFeeCategoryCodes())) {
                continue;
            }
            rows.add(new OutpatientFeeDetailChargeRowQueryModel(chargeItem, link.getPaymentContext()));
        }
        // Asm：写入请求级缓存，供同 Query 后续 page/summary 复用
        query.setCachedChargeRows(rows);
        return rows;
    }

    /**
     * 解析费用明细候选 ChargeItem Map：优先 Support 预加载，否则按 id 批量 IN（仅本聚合 Repository）。
     *
     * @param query         可含 {@code preloadedChargeItemsById}
     * @param chargeItemIds 待解析主键
     * @return id → ChargeItem
     */
    private Map<Long, ChargeItem> resolveOutpatientFeeDetailChargeItemMap(
        OutpatientFeeDetailPageQuery query,
        LinkedHashSet<Long> chargeItemIds) {
        Map<Long, ChargeItem> preloaded = query.getPreloadedChargeItemsById();
        if (preloaded != null && !preloaded.isEmpty()) {
            Map<Long, ChargeItem> reuse = new LinkedHashMap<>();
            for (Long chargeItemId : chargeItemIds) {
                if (chargeItemId == null) {
                    continue;
                }
                ChargeItem chargeItem = preloaded.get(chargeItemId);
                if (chargeItem != null) {
                    reuse.put(chargeItemId, chargeItem);
                }
            }
            // 预加载覆盖全部所需 id 时跳过二次 Repository 读
            if (reuse.size() == chargeItemIds.size()) {
                return reuse;
            }
        }
        ChargeItemQueryCommand loadCmd = new ChargeItemQueryCommand();
        loadCmd.setIds(new ArrayList<>(chargeItemIds));
        List<ChargeItem> chargeItems = this.loadChargeItemsByIds(loadCmd);
        Map<Long, ChargeItem> chargeItemById = new LinkedHashMap<>();
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem != null && chargeItem.getId() != null) {
                chargeItemById.put(chargeItem.getId(), chargeItem);
            }
        }
        return chargeItemById;
    }

    /**
     * 按 payment 快照补全各关联行的 {@code paymentSnapshotAbsAmountTotal} 分母（AS-OP-FIN-005 **BR-OP-FDT-011**）。
     *
     * <p>分母 = 同一 payment 快照内各 chargeItem {@code |total_price|} 之和，供应用层实收/医保占比分摊。</p>
     */
    private void enrichOutpatientFeeDetailPaymentSnapshotDenominators(
        Map<Long, ChargeItem> chargeItemById,
        List<OutpatientFeeDetailChargePaymentLinkQueryModel> paymentLinks) {
        Map<Long, BigDecimal> absTotalByPaymentId = new HashMap<>();
        // 第一遍：按 paymentId 聚合快照内 |total_price| 之和
        for (OutpatientFeeDetailChargePaymentLinkQueryModel link : paymentLinks) {
            if (link == null || link.getChargeItemId() == null || link.getPaymentContext() == null) {
                continue;
            }
            ChargeItem chargeItem = chargeItemById.get(link.getChargeItemId());
            if (chargeItem == null) {
                continue;
            }
            Long paymentId = link.getPaymentContext().getPaymentId();
            if (paymentId == null) {
                continue;
            }
            BigDecimal absAmount = this.nullToZero(chargeItem.getTotalPrice()).abs();
            BigDecimal accumulated = absTotalByPaymentId.get(paymentId);
            if (accumulated == null) {
                absTotalByPaymentId.put(paymentId, absAmount);
            } else {
                absTotalByPaymentId.put(paymentId, accumulated.add(absAmount));
            }
        }
        // 第二遍：写回各 link 的 payment 上下文分母
        for (OutpatientFeeDetailChargePaymentLinkQueryModel link : paymentLinks) {
            if (link == null || link.getPaymentContext() == null) {
                continue;
            }
            Long paymentId = link.getPaymentContext().getPaymentId();
            if (paymentId == null) {
                continue;
            }
            BigDecimal denominator = absTotalByPaymentId.get(paymentId);
            if (denominator == null) {
                link.getPaymentContext().setPaymentSnapshotAbsAmountTotal(BigDecimal.ZERO);
            } else {
                link.getPaymentContext().setPaymentSnapshotAbsAmountTotal(denominator);
            }
        }
    }

    /** 解析分页页码（1-based → Spring 0-based；缺省第 1 页）。 */
    private int resolveOutpatientFeeDetailPageNo(OutpatientFeeDetailPageQuery query) {
        if (query == null || query.getPageNo() == null || query.getPageNo() < 1) {
            return 0;
        }
        return query.springPageIndex();
    }

    /** 解析分页大小（缺省 {@link OutpatientFeeDetailConstants#DEFAULT_PAGE_SIZE} 就诊块/页）。 */
    private int resolveOutpatientFeeDetailPageSize(OutpatientFeeDetailPageQuery query) {
        if (query == null || query.getPageSize() == null || query.getPageSize() < 1) {
            return OutpatientFeeDetailConstants.DEFAULT_PAGE_SIZE;
        }
        return query.getBoundedPageSize();
    }

    /** 取就诊块内最新 payment {@code bill_date}，供块间倒序排序（BR-OP-FDT-014）。 */
    private LocalDateTime maxBillDateForEncounterRows(List<OutpatientFeeDetailChargeRowQueryModel> lines) {
        LocalDateTime max = null;
        if (lines == null) {
            return null;
        }
        for (OutpatientFeeDetailChargeRowQueryModel row : lines) {
            if (row == null || row.getPaymentContext() == null) {
                continue;
            }
            LocalDateTime billDate = row.getPaymentContext().getBillDate();
            if (billDate == null) {
                continue;
            }
            if (max == null || billDate.isAfter(max)) {
                max = billDate;
            }
        }
        return max;
    }

    /**
     * 就诊 scope 收窄：{@code resolvedEncounterScope} 非空时 chargeItem 须命中其一；
     * 为空时不额外过滤（Support 未写 encounter 集合时的兼容）。
     */
    private boolean matchesOutpatientFeeDetailEncounterScope(ChargeItem chargeItem, List<Long> resolvedEncounterScope) {
        if (resolvedEncounterScope == null || resolvedEncounterScope.isEmpty()) {
            return true;
        }
        if (chargeItem.getEncounterId() == null) {
            return false;
        }
        for (Long encounterId : resolvedEncounterScope) {
            if (encounterId != null && encounterId.equals(chargeItem.getEncounterId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 费用大类多选过滤（BR-OP-FDT-006）；未传大类时不过滤。
     *
     * @throws IllegalArgumentException {@link #resolveOutpatientFeeDetailFeeCategoryCode(String)} 无法识别 service_table 时
     */
    private boolean matchesOutpatientFeeDetailFeeCategory(ChargeItem chargeItem, List<String> feeCategoryCodes) {
        if (feeCategoryCodes == null || feeCategoryCodes.isEmpty()) {
            return true;
        }
        String categoryCode = this.resolveOutpatientFeeDetailFeeCategoryCode(chargeItem.getServiceTable());
        for (String selected : feeCategoryCodes) {
            if (selected != null && selected.equals(categoryCode)) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@code service_table} → 费用大类 code（BR-OP-FDT-006 / EX-OP-FDT-103）。
     *
     * @param serviceTable chargeItem 业务锚点表名
     * @return 大类 code（见 {@link OutpatientFeeDetailConstants}）
     * @throws IllegalArgumentException 空白或无法识别时整单失败
     */
    private String resolveOutpatientFeeDetailFeeCategoryCode(String serviceTable) {
        if (StringUtils.isEmpty(serviceTable)) {
            throw new IllegalArgumentException("charge item service_table is blank");
        }
        String normalized = serviceTable.trim();
        if (TableNameConstants.MED_MEDICATION_REQUEST.equals(normalized)
            || TableNameConstants.MED_MEDICATION_DISPENSE.equals(normalized)) {
            return OutpatientFeeDetailConstants.FEE_CATEGORY_MEDICATION;
        }
        if (TableNameConstants.WKF_SERVICE_REQUEST.equals(normalized)) {
            return OutpatientFeeDetailConstants.FEE_CATEGORY_ACTIVITY;
        }
        if (TableNameConstants.WKF_DEVICE_REQUEST.equals(normalized)
            || TableNameConstants.WKF_DEVICE_DISPENSE.equals(normalized)) {
            return OutpatientFeeDetailConstants.FEE_CATEGORY_DEVICE;
        }
        if (TableNameConstants.ADM_HEALTHCARE_SERVICE.equals(normalized)) {
            return OutpatientFeeDetailConstants.FEE_CATEGORY_REGISTER;
        }
        throw new IllegalArgumentException("unsupported charge item service_table for outpatient fee detail: " + normalized);
    }

    /**
     * 应收金额：优先 {@code discount_amount}，缺失回退 {@code total_price}（BR-CHG-QRY-003）。
     *
     * <p>门诊费用明细与工作量读路径共用；实收分摊在应用层。</p>
     */
    private BigDecimal resolveOutpatientFeeDetailReceivableAmount(ChargeItem chargeItem) {
        if (chargeItem.getDiscountAmount() != null
            && chargeItem.getDiscountAmount().compareTo(BigDecimal.ZERO) != 0) {
            return chargeItem.getDiscountAmount();
        }
        return this.nullToZero(chargeItem.getTotalPrice());
    }

    /**
     * 折后金额：{@code system_discount_price} → 非零 {@code discount_amount} → {@code total_price} 回退链。
     */
    private BigDecimal resolveOutpatientFeeDetailDiscountedAmount(ChargeItem chargeItem) {
        if (chargeItem.getSystemDiscountPrice() != null) {
            return chargeItem.getSystemDiscountPrice();
        }
        if (chargeItem.getDiscountAmount() != null
            && chargeItem.getDiscountAmount().compareTo(BigDecimal.ZERO) != 0) {
            return chargeItem.getDiscountAmount();
        }
        return this.nullToZero(chargeItem.getTotalPrice());
    }

    /** {@link BigDecimal} null 安全归零（汇总循环共用）。 */
    private BigDecimal nullToZero(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value;
    }

    // -------------------------------------------------------------------------
    // AS-RPT-001 科室/医生工作量读侧（DM-ADM-011 §6.2.27～6.2.29；仅本聚合 Repository）
    // -------------------------------------------------------------------------

    /**
     * 工作量 fin_type 汇总：按开单科室 + 定价 definition 聚合账单金额（DM-ADM-011 §6.2.27；IF-RPT-WKL-02）。
     *
     * <p>本方法输出 org + definition 粒度事实；fin_type_code、实收/成本/净收入由应用层 enrich。
     * 未选科室时应用层按 fin_type **跨 org 合并**为 {@code finTypeRows[]}（BR-RPT-WKL-016），
     * 汇总 Tab **禁止**按科室分块。</p>
     *
     * @param query 须经 {@link com.openhis.health.application.report.practitionerworkload.support.PractitionerWorkloadLoader}
     *              预解析 payment 快照、{@code resolvedOrganizationIds}、{@code resolvedEncounterScope}
     * @return 汇总行；无命中时返回空列表
     */
    public List<PractitionerWorkloadFinTypeOrgSummaryQueryModel> summarizePractitionerWorkloadByFinType(
        PractitionerWorkloadPageQuery query) {
        // 候选行含费用与挂号 chargeItem（挂号费映射 fin_type 1012）；明细 IF 另路径排除
        List<PractitionerWorkloadChargeRowQueryModel> rows = this.listPractitionerWorkloadChargeRows(query, false);
        Map<String, PractitionerWorkloadFinTypeOrgSummaryQueryModel> bucketByKey = new LinkedHashMap<>();
        // 按 requesting_org_id + definition_id 代数 SUM 应收/折后/行金额
        for (PractitionerWorkloadChargeRowQueryModel row : rows) {
            if (row == null || row.getChargeItem() == null) {
                continue;
            }
            ChargeItem chargeItem = row.getChargeItem();
            Long orgId = chargeItem.getRequestingOrgId();
            Long definitionId = chargeItem.getDefinitionId();
            // 缺开单科室或定价锚点时无法形成稳定聚合键，跳过以免脏桶
            if (orgId == null || definitionId == null) {
                continue;
            }
            String key = orgId + ":" + definitionId;
            PractitionerWorkloadFinTypeOrgSummaryQueryModel summary = bucketByKey.get(key);
            if (summary == null) {
                summary = new PractitionerWorkloadFinTypeOrgSummaryQueryModel();
                summary.setRequestingOrgId(orgId);
                summary.setDefinitionId(definitionId);
                bucketByKey.put(key, summary);
            }
            BigDecimal receivable = this.applyPractitionerWorkloadPaymentSign(
                this.resolveOutpatientFeeDetailReceivableAmount(chargeItem),
                row.getPaymentContext());
            BigDecimal discounted = this.applyPractitionerWorkloadPaymentSign(
                this.resolveOutpatientFeeDetailDiscountedAmount(chargeItem),
                row.getPaymentContext());
            BigDecimal lineAmount = this.applyPractitionerWorkloadPaymentSign(
                this.nullToZero(chargeItem.getTotalPrice()),
                row.getPaymentContext());
            summary.setReceivableAmountTotal(summary.getReceivableAmountTotal().add(receivable));
            summary.setDiscountedAmountTotal(summary.getDiscountedAmountTotal().add(discounted));
            summary.setLineAmountTotal(summary.getLineAmountTotal().add(lineAmount));
        }
        return new ArrayList<>(bucketByKey.values());
    }

    /**
     * 工作量挂号统计：按挂号科室 + 号源类型净笔数（DM-ADM-011 §6.2.29；IF-RPT-WKL-02 下半区）。
     *
     * <p>科室键为 {@code Encounter.organization_id}（方案 C）；未选科室时应用层按 serviceType
     * **跨 org 合并**为 {@code registrationRows[]}（BR-RPT-WKL-016）。</p>
     *
     * @param query 须含 {@link PractitionerWorkloadPageQuery#getEncounterContextByEncounterId()}（Support 预载）
     * @return 汇总行；无命中时返回空列表
     */
    public List<PractitionerWorkloadRegistrationOrgSummaryQueryModel> summarizeRegistrationChargesByServiceType(
        PractitionerWorkloadPageQuery query) {
        List<PractitionerWorkloadChargeRowQueryModel> rows = this.listPractitionerWorkloadChargeRows(query, true);
        Map<String, PractitionerWorkloadRegistrationOrgSummaryQueryModel> bucketByKey = new LinkedHashMap<>();
        for (PractitionerWorkloadChargeRowQueryModel row : rows) {
            if (row == null || row.getChargeItem() == null || row.getPaymentContext() == null) {
                continue;
            }
            ChargeItem chargeItem = row.getChargeItem();
            if (!this.isPractitionerWorkloadRegistrationChargeItem(chargeItem)) {
                continue;
            }
            PractitionerWorkloadEncounterContextQueryModel encounterContext =
                this.resolvePractitionerWorkloadEncounterContext(query, chargeItem.getEncounterId());
            if (encounterContext == null) {
                continue;
            }
            // 退号就诊不计入挂号净笔数（BR-RPT-WKL-009）
            if (EncounterStatus.CANCELLED.getCode().equals(encounterContext.getStatusEnum())) {
                continue;
            }
            // 选医生时仅 REGISTRATION_DOCTOR 命中就诊（BR-RPT-WKL-005）
            if (!this.matchesPractitionerWorkloadRegistrationDoctorScope(query, chargeItem.getEncounterId())) {
                continue;
            }
            Long organizationId = encounterContext.getOrganizationId();
            Long serviceTypeId = encounterContext.getServiceTypeId();
            if (organizationId == null) {
                continue;
            }
            String serviceTypeKey = "null";
            if (serviceTypeId != null) {
                serviceTypeKey = String.valueOf(serviceTypeId);
            }
            String key = organizationId + ":" + serviceTypeKey;
            PractitionerWorkloadRegistrationOrgSummaryQueryModel summary = bucketByKey.get(key);
            if (summary == null) {
                summary = new PractitionerWorkloadRegistrationOrgSummaryQueryModel();
                summary.setOrganizationId(organizationId);
                summary.setServiceTypeId(serviceTypeId);
                bucketByKey.put(key, summary);
            }
            int delta = 1;
            if (Boolean.TRUE.equals(row.getPaymentContext().getRefundPayment())) {
                // 退费 payment 冲减净笔数（BR-RPT-WKL-011）
                delta = -1;
            }
            summary.setNetChargeCount(summary.getNetChargeCount() + delta);
        }
        return new ArrayList<>(bucketByKey.values());
    }

    /**
     * 工作量明细分页：按 payment–chargeItem 链路展开（DM-ADM-011 §6.2.28；IF-RPT-WKL-03/04）。
     *
     * <p>一行 = 一条收费或退费 payment 快照内的 chargeItem（金额带正负，对齐门诊费用明细）；
     * 不含挂号 chargeItem；排序 {@code entered_date DESC}（BR-RPT-WKL-012）。</p>
     *
     * @param query 须含预解析 scope；分页键 {@code pageNo}/{@code pageSize} 缺省 1 / 20
     * @return 链路明细行分页
     */
    public Page<PractitionerWorkloadRequestDetailQueryModel> pagePractitionerWorkloadDetailByRequest(
        PractitionerWorkloadPageQuery query) {
        List<PractitionerWorkloadChargeRowQueryModel> rows = this.listPractitionerWorkloadChargeRows(query, false);
        return this.pagePractitionerWorkloadDetailFromChargeRows(rows, query);
    }

    /**
     * 在已加载 charge 行上分页明细（避免重复 loadChargeItemsByIds；供应用层复用 scope 行）。
     *
     * @param chargeRows 已按 scope 过滤的 charge 行
     * @param query      分页参数
     * @return 链路明细行分页
     */
    public Page<PractitionerWorkloadRequestDetailQueryModel> pagePractitionerWorkloadDetailFromChargeRows(
        List<PractitionerWorkloadChargeRowQueryModel> chargeRows,
        PractitionerWorkloadPageQuery query) {
        List<PractitionerWorkloadRequestDetailQueryModel> details =
            this.listPractitionerWorkloadDetailFromChargeRows(chargeRows);
        return this.pagePractitionerWorkloadDetailRows(details, query);
    }

    /**
     * 在已加载 charge 行上展开全部明细并排序（导出等全量路径复用）。
     *
     * @param chargeRows 已按 scope 过滤的 charge 行
     * @return 排序后的链路明细行
     */
    public List<PractitionerWorkloadRequestDetailQueryModel> listPractitionerWorkloadDetailFromChargeRows(
        List<PractitionerWorkloadChargeRowQueryModel> chargeRows) {
        List<PractitionerWorkloadRequestDetailQueryModel> details =
            this.buildPractitionerWorkloadDetailRowsFromChargeRows(chargeRows);
        details.sort(Comparator.comparing(
            PractitionerWorkloadRequestDetailQueryModel::getEnteredDate,
            Comparator.nullsLast(Comparator.reverseOrder())));
        return details;
    }

    /**
     * 将 charge 行展开为明细链路读模型（不含排序与分页）。
     */
    private List<PractitionerWorkloadRequestDetailQueryModel> buildPractitionerWorkloadDetailRowsFromChargeRows(
        List<PractitionerWorkloadChargeRowQueryModel> chargeRows) {
        List<PractitionerWorkloadRequestDetailQueryModel> details = new ArrayList<>();
        if (chargeRows == null || chargeRows.isEmpty()) {
            return details;
        }
        for (PractitionerWorkloadChargeRowQueryModel row : chargeRows) {
            if (row == null || row.getChargeItem() == null || row.getPaymentContext() == null) {
                continue;
            }
            ChargeItem chargeItem = row.getChargeItem();
            // 明细仅三类 request 表；未知表在应用层整单失败（BR-RPT-WKL-006）
            if (!this.isPractitionerWorkloadAdviceServiceTable(chargeItem.getServiceTable())) {
                continue;
            }
            PractitionerWorkloadRequestDetailQueryModel detail = new PractitionerWorkloadRequestDetailQueryModel();
            detail.setChargeItemId(chargeItem.getId());
            detail.setPaymentId(row.getPaymentContext().getPaymentId());
            detail.setServiceTable(chargeItem.getServiceTable());
            detail.setServiceId(chargeItem.getServiceId());
            detail.setRequestingOrgId(chargeItem.getRequestingOrgId());
            detail.setEntererId(chargeItem.getEntererId());
            detail.setEnteredDate(chargeItem.getEnteredDate());
            detail.setBillDate(row.getPaymentContext().getBillDate());
            detail.setReceivableAmountTotal(this.applyPractitionerWorkloadPaymentSign(
                this.resolveOutpatientFeeDetailReceivableAmount(chargeItem),
                row.getPaymentContext()));
            detail.setDiscountedAmountTotal(this.applyPractitionerWorkloadPaymentSign(
                this.resolveOutpatientFeeDetailDiscountedAmount(chargeItem),
                row.getPaymentContext()));
            detail.setLineAmountTotal(this.applyPractitionerWorkloadPaymentSign(
                this.nullToZero(chargeItem.getTotalPrice()),
                row.getPaymentContext()));
            details.add(detail);
        }
        return details;
    }

    /**
     * 对已排序明细行做内存分页。
     */
    private Page<PractitionerWorkloadRequestDetailQueryModel> pagePractitionerWorkloadDetailRows(
        List<PractitionerWorkloadRequestDetailQueryModel> details,
        PractitionerWorkloadPageQuery query) {
        if (details == null) {
            details = Collections.emptyList();
        }
        int pageNo = this.resolvePractitionerWorkloadPageNo(query);
        int pageSize = this.resolvePractitionerWorkloadPageSize(query);
        long total = details.size();
        int fromIndex = pageNo * pageSize;
        if (fromIndex >= details.size()) {
            return new PageImpl<>(Collections.emptyList(), PageRequest.of(pageNo, pageSize), total);
        }
        int toIndex = Math.min(fromIndex + pageSize, details.size());
        return new PageImpl<>(details.subList(fromIndex, toIndex), PageRequest.of(pageNo, pageSize), total);
    }

    /**
     * 在 payment 预解析 scope 内加载并过滤工作量候选 chargeItem 行（仅本聚合 Repository）。
     *
     * @param query                须含 {@link PractitionerWorkloadPageQuery#getResolvedPaymentChargeItemLinks()}
     * @param registrationOnlyMode {@code true} 挂号净笔数分支；{@code false} fin_type/明细分支
     * @return 过滤后的候选行；scope 为空时返回空列表
     */
    public List<PractitionerWorkloadChargeRowQueryModel> listPractitionerWorkloadScopeChargeRows(
        PractitionerWorkloadPageQuery query,
        boolean registrationOnlyMode) {
        return this.listPractitionerWorkloadChargeRows(query, registrationOnlyMode);
    }

    /**
     * 汇总工作量外显指标：接诊人数、处方/诊疗开立量与开单总金额。
     *
     * @param rows 已按 scope 过滤的候选 chargeItem 行
     * @return 外显汇总读模型
     */
    public PractitionerWorkloadOverviewSummaryQueryModel summarizePractitionerWorkloadOverview(
        List<PractitionerWorkloadChargeRowQueryModel> rows) {
        PractitionerWorkloadOverviewSummaryQueryModel summary = new PractitionerWorkloadOverviewSummaryQueryModel();
        if (rows == null || rows.isEmpty()) {
            return summary;
        }
        LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
        LinkedHashSet<String> prescriptionRequestKeys = new LinkedHashSet<>();
        LinkedHashSet<String> activityRequestKeys = new LinkedHashSet<>();
        for (PractitionerWorkloadChargeRowQueryModel row : rows) {
            if (row == null || row.getChargeItem() == null) {
                continue;
            }
            ChargeItem chargeItem = row.getChargeItem();
            if (chargeItem.getEncounterId() != null) {
                encounterIds.add(chargeItem.getEncounterId());
            }
            if (this.isPractitionerWorkloadRegistrationChargeItem(chargeItem)) {
                continue;
            }
            if (!this.isPractitionerWorkloadAdviceServiceTable(chargeItem.getServiceTable())) {
                continue;
            }
            String serviceTable = chargeItem.getServiceTable().trim();
            String requestKey = serviceTable + ":" + chargeItem.getServiceId();
            if (TableNameConstants.MED_MEDICATION_REQUEST.equals(serviceTable)) {
                prescriptionRequestKeys.add(requestKey);
            } else if (TableNameConstants.WKF_SERVICE_REQUEST.equals(serviceTable)
                || TableNameConstants.WKF_DEVICE_REQUEST.equals(serviceTable)) {
                activityRequestKeys.add(requestKey);
            }
        }
        summary.setEncounterCountTotal(encounterIds.size());
        summary.setPrescriptionCountTotal(prescriptionRequestKeys.size());
        summary.setActivityItemCountTotal(activityRequestKeys.size());
        return summary;
    }

    /**
     * 在 payment 预解析 scope 内加载并过滤工作量候选 chargeItem 行（仅本聚合 Repository）。
     *
     * @param query                须含 {@link PractitionerWorkloadPageQuery#getResolvedPaymentChargeItemLinks()}
     * @param registrationOnlyMode {@code true} 挂号净笔数分支；{@code false} fin_type/明细分支
     * @return 过滤后的候选行；scope 为空时返回空列表
     */
    private List<PractitionerWorkloadChargeRowQueryModel> listPractitionerWorkloadChargeRows(
        PractitionerWorkloadPageQuery query,
        boolean registrationOnlyMode) {
        if (query == null) {
            return Collections.emptyList();
        }
        List<OutpatientFeeDetailChargePaymentLinkQueryModel> paymentLinks = query.getResolvedPaymentChargeItemLinks();
        if (paymentLinks == null || paymentLinks.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<Long> chargeItemIds = new LinkedHashSet<>();
        for (OutpatientFeeDetailChargePaymentLinkQueryModel link : paymentLinks) {
            if (link != null && link.getChargeItemId() != null) {
                chargeItemIds.add(link.getChargeItemId());
            }
        }
        if (chargeItemIds.isEmpty()) {
            return Collections.emptyList();
        }
        ChargeItemQueryCommand loadCmd = new ChargeItemQueryCommand();
        loadCmd.setIds(new ArrayList<>(chargeItemIds));
        List<ChargeItem> chargeItems = this.loadChargeItemsByIds(loadCmd);
        Map<Long, ChargeItem> chargeItemById = new LinkedHashMap<>();
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem != null && chargeItem.getId() != null) {
                chargeItemById.put(chargeItem.getId(), chargeItem);
            }
        }
        List<String> allowedStatuses = List.of(
            ChargeItemStatus.SETTLED.getCode(),
            ChargeItemStatus.REFUNDED.getCode());
        List<PractitionerWorkloadChargeRowQueryModel> rows = new ArrayList<>();
        // 按 payment 快照关联逐条展开；同 chargeItem 可收退各一行（BR-RPT-WKL-002/011）
        for (OutpatientFeeDetailChargePaymentLinkQueryModel link : paymentLinks) {
            if (link == null || link.getChargeItemId() == null || link.getPaymentContext() == null) {
                continue;
            }
            ChargeItem chargeItem = chargeItemById.get(link.getChargeItemId());
            if (chargeItem == null) {
                continue;
            }
            if (!allowedStatuses.contains(chargeItem.getStatusEnum())) {
                continue;
            }
            if (!this.matchesPractitionerWorkloadEncounterScope(chargeItem, query.getResolvedEncounterScope())) {
                continue;
            }
            if (registrationOnlyMode) {
                // 挂号分支：科室收窄用 Encounter.organization_id（方案 C / BR-RPT-WKL-004）
                if (!this.matchesPractitionerWorkloadRegistrationEncounterOrgScope(query, chargeItem)) {
                    continue;
                }
            } else if (!this.matchesPractitionerWorkloadRequestingOrgScope(chargeItem, query.getResolvedOrganizationIds())) {
                // 费用/医嘱分支：开单科室 requesting_org_id ∈ resolvedOrganizationIds
                continue;
            }
            if (!this.matchesPractitionerWorkloadPractitionerScope(chargeItem, query)) {
                continue;
            }
            boolean registration = this.isPractitionerWorkloadRegistrationChargeItem(chargeItem);
            if (registrationOnlyMode && !registration) {
                continue;
            }
            rows.add(new PractitionerWorkloadChargeRowQueryModel(chargeItem, link.getPaymentContext()));
        }
        return rows;
    }

    /**
     * 就诊 scope 收窄：chargeItem.encounter_id 须在 Support 预解析的就诊集合内（BR-RPT-WKL-003）。
     */
    private boolean matchesPractitionerWorkloadEncounterScope(ChargeItem chargeItem, List<Long> resolvedEncounterScope) {
        if (resolvedEncounterScope == null || resolvedEncounterScope.isEmpty()) {
            return false;
        }
        if (chargeItem.getEncounterId() == null) {
            return false;
        }
        for (Long encounterId : resolvedEncounterScope) {
            if (encounterId != null && encounterId.equals(chargeItem.getEncounterId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 费用/医嘱科室收窄：{@code requesting_org_id ∈ resolvedOrganizationIds}（BR-RPT-WKL-004）。
     */
    private boolean matchesPractitionerWorkloadRequestingOrgScope(
        ChargeItem chargeItem,
        List<Long> resolvedOrganizationIds) {
        if (resolvedOrganizationIds == null || resolvedOrganizationIds.isEmpty()) {
            return false;
        }
        if (chargeItem.getRequestingOrgId() == null) {
            return false;
        }
        return this.matchesOrganizationIdInScope(chargeItem.getRequestingOrgId(), resolvedOrganizationIds);
    }

    /**
     * 挂号科室收窄：{@code Encounter.organization_id ∈ resolvedOrganizationIds}（方案 C）。
     */
    private boolean matchesPractitionerWorkloadRegistrationEncounterOrgScope(
        PractitionerWorkloadPageQuery query,
        ChargeItem chargeItem) {
        PractitionerWorkloadEncounterContextQueryModel encounterContext =
            this.resolvePractitionerWorkloadEncounterContext(query, chargeItem.getEncounterId());
        if (encounterContext == null || encounterContext.getOrganizationId() == null) {
            return false;
        }
        return this.matchesOrganizationIdInScope(encounterContext.getOrganizationId(), query.getResolvedOrganizationIds());
    }

    /** 判断 organizationId 是否落在已解析科室集合内。 */
    private boolean matchesOrganizationIdInScope(Long organizationId, List<Long> resolvedOrganizationIds) {
        if (organizationId == null || resolvedOrganizationIds == null || resolvedOrganizationIds.isEmpty()) {
            return false;
        }
        for (Long candidate : resolvedOrganizationIds) {
            if (organizationId.equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    /** 选医生时费用 chargeItem 须 enterer_id 命中（BR-RPT-WKL-005）。 */
    private boolean matchesPractitionerWorkloadEntererScope(ChargeItem chargeItem, Long practitionerId) {
        if (practitionerId == null) {
            return true;
        }
        return practitionerId.equals(chargeItem.getEntererId());
    }

    /**
     * 医生收窄总入口：未选医生放行；挂号走 REGISTRATION_DOCTOR，费用走 enterer_id（BR-RPT-WKL-005）。
     */
    private boolean matchesPractitionerWorkloadPractitionerScope(
        ChargeItem chargeItem,
        PractitionerWorkloadPageQuery query) {
        if (query.getPractitionerId() == null) {
            return true;
        }
        if (this.isPractitionerWorkloadRegistrationChargeItem(chargeItem)) {
            return this.matchesPractitionerWorkloadRegistrationDoctorScope(query, chargeItem.getEncounterId());
        }
        return this.matchesPractitionerWorkloadEntererScope(chargeItem, query.getPractitionerId());
    }

    /**
     * 挂号 chargeItem 选医生时：就诊须在 REGISTRATION_DOCTOR 预筛集合内。
     */
    private boolean matchesPractitionerWorkloadRegistrationDoctorScope(
        PractitionerWorkloadPageQuery query,
        Long encounterId) {
        if (query.getPractitionerId() == null) {
            return true;
        }
        Set<Long> registrationScope = query.getRegistrationDoctorEncounterScope();
        if (registrationScope == null || registrationScope.isEmpty()) {
            return false;
        }
        return registrationScope.contains(encounterId);
    }

    /** 从 Support 预载 map 取就诊上下文；未预载时返回 {@code null}。 */
    private PractitionerWorkloadEncounterContextQueryModel resolvePractitionerWorkloadEncounterContext(
        PractitionerWorkloadPageQuery query,
        Long encounterId) {
        if (query.getEncounterContextByEncounterId() == null || encounterId == null) {
            return null;
        }
        return query.getEncounterContextByEncounterId().get(encounterId);
    }

    /**
     * 识别挂号 chargeItem：{@code generate_source_enum = registration} 或 {@code context_enum = REGISTER}。
     */
    private boolean isPractitionerWorkloadRegistrationChargeItem(ChargeItem chargeItem) {
        if (GenerateSource.REGISTRATION_REGISTER.getCode().equals(chargeItem.getGenerateSourceEnum())) {
            return true;
        }
        return PractitionerWorkloadConstants.CHARGE_ITEM_CONTEXT_REGISTER.equals(chargeItem.getContextEnum());
    }

    /**
     * 明细 IF 允许的医嘱锚点表（BR-RPT-WKL-006）：诊疗 / 药品 / 耗材 request。
     */
    private boolean isPractitionerWorkloadAdviceServiceTable(String serviceTable) {
        if (StringUtils.isEmpty(serviceTable)) {
            return false;
        }
        String normalized = serviceTable.trim();
        return TableNameConstants.WKF_SERVICE_REQUEST.equals(normalized)
            || TableNameConstants.MED_MEDICATION_REQUEST.equals(normalized)
            || TableNameConstants.WKF_DEVICE_REQUEST.equals(normalized);
    }

    /** 解析分页页码（1-based → Spring 0-based；缺省第 1 页）。 */
    private int resolvePractitionerWorkloadPageNo(PractitionerWorkloadPageQuery query) {
        if (query == null || query.getPageNo() == null || query.getPageNo() < 1) {
            return 0;
        }
        return query.springPageIndex();
    }

    /** 解析分页大小（缺省 {@link PractitionerWorkloadConstants#DEFAULT_PAGE_SIZE}）。 */
    private int resolvePractitionerWorkloadPageSize(PractitionerWorkloadPageQuery query) {
        if (query == null || query.getPageSize() == null || query.getPageSize() < 1) {
            return PractitionerWorkloadConstants.DEFAULT_PAGE_SIZE;
        }
        return query.getBoundedPageSize();
    }

    /**
     * 退费 payment 链路金额取负（BR-RPT-WKL-011；对齐门诊费用明细 BR-OP-FDT-012）。
     *
     * @param amount          原始金额（chargeItem 侧恒为正）
     * @param paymentContext  payment 读上下文
     * @return 带退费符号的金额
     */
    private BigDecimal applyPractitionerWorkloadPaymentSign(
        BigDecimal amount,
        OutpatientFeeDetailPaymentContextQueryModel paymentContext) {
        BigDecimal normalized = this.nullToZero(amount);
        if (paymentContext != null && Boolean.TRUE.equals(paymentContext.getRefundPayment())) {
            return normalized.abs().negate();
        }
        return normalized;
    }

    /**
     * 统计未软删账单中 {@code encounter_diagnosis_ids} 引用指定就诊诊断行的条数。
     *
     * @param encounterDiagnosisId 就诊诊断行主键
     * @return 引用条数
     */
    public int countReferencingEncounterDiagnosisId(Long encounterDiagnosisId) {
        if (encounterDiagnosisId == null) {
            return 0;
        }
        Specification<ChargeItem> specification = (root, query, cb) -> cb.and(
            cb.isFalse(root.get(FieldNameConstants.IS_DELETED)),
            EncounterDiagnosisIdsPredicates.containsEncounterDiagnosisId(
                cb, root.get(FieldNameConstants.ENCOUNTER_DIAGNOSIS_IDS), encounterDiagnosisId)
        );
        long count = repository.count(specification);
        if (count > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) count;
    }
}
