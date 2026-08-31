/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.chargeitem.service;

import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.query.OutpatientChargeQueueEncounterQuery;
import com.openhis.health.domain.administration.chargeitem.query.FeeDetailChargeItemFilterProjection;
import com.openhis.health.domain.administration.chargeitem.query.OutpatientFeeDetailAmountSummaryQueryModel;
import com.openhis.health.domain.administration.chargeitem.query.OutpatientFeeDetailChargeRowQueryModel;
import com.openhis.health.domain.administration.chargeitem.query.OutpatientFeeDetailEncounterBlockQueryModel;
import com.openhis.health.domain.administration.chargeitem.query.OutpatientFeeDetailPageQuery;
import com.openhis.health.domain.administration.chargeitem.query.FeeDetailFinTypeSummaryQueryModel;
import com.openhis.health.domain.administration.chargeitem.query.KeyMedicationUsageAggQueryModel;
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
import com.openhis.health.domain.share.utils.IdInQueryChunkSupport;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
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
import java.util.HashSet;
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
     * <p>主键过多时按 {@link GeneralSharedConstants#ID_IN_QUERY_CHUNK_SIZE} 分批 IN，避免单次超长导致库超时。</p>
     *
     * @param cmd 查询命令（ids 必填）
     * @return 收费明细列表
     */
    public List<ChargeItem> loadChargeItemsByIds(ChargeItemQueryCommand cmd) {
        if (cmd == null || cmd.getIds() == null || cmd.getIds().isEmpty()) {
            return new ArrayList<>();
        }
        // Pre：分批 IN，避免日期窗大 scope 单次超长参数导致库超时
        return new ArrayList<>(IdInQueryChunkSupport.loadByIdInChunks(
            cmd.getIds(),
            chunkIds -> repository.findAllByIdIn(chunkIds)));
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
        // Pre：service_id IN 分批，避免退药补价等大集合单次超长
        List<ChargeItem> rows = IdInQueryChunkSupport.loadByIdInChunks(
            cmd.getServiceIds(),
            chunkIds -> repository.findAllByServiceIdIn(chunkIds));
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
     * 按收费状态 + service_table + service_id 范围解析去重 service_id（窗口化，避免租户全量状态扫描）。
     *
     * <p>主键过多时按 {@link GeneralSharedConstants#ID_IN_QUERY_CHUNK_SIZE} 分批 IN。</p>
     *
     * @param statusEnum     收费状态 code
     * @param serviceTable   服务表名
     * @param serviceIdScope 服务主键范围（药品申请 id）；空则空列表
     * @return 去重 service_id
     */
    public List<Long> listDistinctServiceIdsByStatusEnumAndServiceTableAndServiceIdIn(
        String statusEnum,
        String serviceTable,
        Collection<Long> serviceIdScope) {
        String normalizedStatus = StringUtils.trimToNull(statusEnum);
        String normalizedTable = StringUtils.trimToNull(serviceTable);
        if (normalizedStatus == null || normalizedTable == null) {
            return List.of();
        }
        List<Long> scopeIds = IdInQueryChunkSupport.distinctNonNullIds(serviceIdScope);
        if (scopeIds.isEmpty()) {
            return List.of();
        }
        List<ChargeItem> rows = IdInQueryChunkSupport.loadByIdInChunks(
            scopeIds,
            chunkIds -> {
                Specification<ChargeItem> specification = (root, criteriaQuery, cb) -> cb.and(
                    cb.isFalse(root.get(FieldNameConstants.IS_DELETED)),
                    cb.equal(root.get(FieldNameConstants.STATUS_ENUM), normalizedStatus),
                    cb.equal(root.get(FieldNameConstants.SERVICE_TABLE), normalizedTable),
                    cb.isNotNull(root.get(FieldNameConstants.SERVICE_ID)),
                    root.get(FieldNameConstants.SERVICE_ID).in(chunkIds));
                return repository.findAll(specification);
            });
        LinkedHashSet<Long> serviceIds = new LinkedHashSet<>();
        // Loop：窗口内收费行 → service_id 去重
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
     * 按上游表名 + 上游主键集合拉取待结算收费行（仅本聚合 Repository）。
     *
     * <p>供住院费用明细报表按 Procedure.recorded / 耗材发放展示轴反查待结账单。</p>
     *
     * @param serviceTable 上游表名
     * @param serviceIds   上游主键集合
     * @return 未软删且状态为 PENDING_SETTLEMENT 的行；入参不全时返回空列表
     */
    public List<ChargeItem> listPendingSettlementChargeItemsByServiceTableAndServiceIds(
        String serviceTable,
        Collection<Long> serviceIds) {
        String normalizedTable = StringUtils.trimToNull(serviceTable);
        if (normalizedTable == null || serviceIds == null || serviceIds.isEmpty()) {
            return new ArrayList<>();
        }
        LinkedHashSet<Long> distinctIds = new LinkedHashSet<>();
        for (Long serviceId : serviceIds) {
            if (serviceId != null) {
                distinctIds.add(serviceId);
            }
        }
        if (distinctIds.isEmpty()) {
            return new ArrayList<>();
        }
        String pendingCode = ChargeItemStatus.PENDING_SETTLEMENT.getCode();
        Specification<ChargeItem> specification = (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), pendingCode));
            predicates.add(cb.equal(root.get(FieldNameConstants.SERVICE_TABLE), normalizedTable));
            predicates.add(root.get(FieldNameConstants.SERVICE_ID).in(distinctIds));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return repository.findAll(specification);
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
     * 按发放主键集合跨就诊批量加载耗材发放定价锚点收费明细（报表汇总/明细；禁止按就诊全量扫费用）。
     *
     * <p>状态口径与 {@link #listChargeItemsForMedicationDispensePricingByDispenseIds} 一致。</p>
     *
     * @param dispenseIds              耗材发放主键集合
     * @param includePendingSettlement 是否包含 {@code PENDING_SETTLEMENT}
     * @return 平铺明细；集合为空时返回空表
     */
    public List<ChargeItem> listChargeItemsForDeviceDispensePricingByDispenseIds(
        Collection<Long> dispenseIds,
        boolean includePendingSettlement) {
        if (dispenseIds == null || dispenseIds.isEmpty()) {
            return new ArrayList<>();
        }
        LinkedHashSet<String> statuses = new LinkedHashSet<>();
        statuses.add(ChargeItemStatus.DRAFT.getCode());
        statuses.add(ChargeItemStatus.PLANNED.getCode());
        statuses.add(ChargeItemStatus.SETTLED.getCode());
        statuses.add(ChargeItemStatus.REFUNDING.getCode());
        statuses.add(ChargeItemStatus.REFUNDED.getCode());
        if (includePendingSettlement) {
            statuses.add(ChargeItemStatus.PENDING_SETTLEMENT.getCode());
        }
        // Pre：dispense_id IN 分批（报表大窗）
        return new ArrayList<>(IdInQueryChunkSupport.loadByIdInChunks(
            dispenseIds,
            chunkIds -> repository.findAllByDispenseTableAndDispenseIdInAndStatusEnumIn(
                TableNameConstants.WKF_DEVICE_DISPENSE,
                new LinkedHashSet<>(chunkIds),
                statuses)));
    }

    /**
     * 按发放主键集合跨就诊批量加载发药定价锚点收费明细（药房日结挂载净收入；禁止按就诊循环）。
     *
     * <p>状态口径与 {@link #listChargeItemsForMedicationDispensePricing} 一致；
     * {@code includePendingSettlement=true} 时追加住院待结算态。</p>
     *
     * @param dispenseIds              发放主键集合
     * @param includePendingSettlement 是否包含 {@code PENDING_SETTLEMENT}
     * @return 平铺明细；集合为空时返回空表
     */
    public List<ChargeItem> listChargeItemsForMedicationDispensePricingByDispenseIds(
        Collection<Long> dispenseIds,
        boolean includePendingSettlement) {
        if (dispenseIds == null || dispenseIds.isEmpty()) {
            return new ArrayList<>();
        }
        LinkedHashSet<String> statuses = new LinkedHashSet<>();
        statuses.add(ChargeItemStatus.DRAFT.getCode());
        statuses.add(ChargeItemStatus.PLANNED.getCode());
        statuses.add(ChargeItemStatus.SETTLED.getCode());
        statuses.add(ChargeItemStatus.REFUNDING.getCode());
        statuses.add(ChargeItemStatus.REFUNDED.getCode());
        if (includePendingSettlement) {
            statuses.add(ChargeItemStatus.PENDING_SETTLEMENT.getCode());
        }
        // Pre：dispense_id IN 分批（发药报表/日结大窗）
        return new ArrayList<>(IdInQueryChunkSupport.loadByIdInChunks(
            dispenseIds,
            chunkIds -> repository.findAllByDispenseTableAndDispenseIdInAndStatusEnumIn(
                TableNameConstants.MED_MEDICATION_DISPENSE,
                new LinkedHashSet<>(chunkIds),
                statuses)));
    }

    /**
     * 按耗材发放主键批量加载收费明细（护士工作量金额 enrich；BR-RPT-NWL-021 dispense_table 锚点）。
     *
     * <p>覆盖 {@code dispense_table=wkf_device_dispense + dispense_id}；与 {@code service_id} 锚点路径互补。
     * 禁止按就诊全量扫 ChargeItem。</p>
     *
     * @param dispenseIds 耗材发放主键集合
     * @return 收费明细；空入参返回空列表
     */
    public List<ChargeItem> listChargeItemsByDeviceDispenseIds(Collection<Long> dispenseIds) {
        if (dispenseIds == null || dispenseIds.isEmpty()) {
            return new ArrayList<>();
        }
        LinkedHashSet<String> statuses = new LinkedHashSet<>();
        statuses.add(ChargeItemStatus.DRAFT.getCode());
        statuses.add(ChargeItemStatus.PLANNED.getCode());
        statuses.add(ChargeItemStatus.SETTLED.getCode());
        statuses.add(ChargeItemStatus.REFUNDING.getCode());
        statuses.add(ChargeItemStatus.REFUNDED.getCode());
        statuses.add(ChargeItemStatus.PENDING_SETTLEMENT.getCode());
        // Pre：dispense_id IN 分批（护士工作量等）
        return new ArrayList<>(IdInQueryChunkSupport.loadByIdInChunks(
            dispenseIds,
            chunkIds -> repository.findAllByDispenseTableAndDispenseIdInAndStatusEnumIn(
                TableNameConstants.WKF_DEVICE_DISPENSE,
                new LinkedHashSet<>(chunkIds),
                statuses)));
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
     * 按 {@code refund_id} 批量加载冲销/再生行（费用清单打印成对剔除：源行在日期窗内、冲销行可在窗外）。
     *
     * @param refundIds 被冲销的源收费明细主键集合（对应 {@code adm_charge_item.refund_id}）
     * @return 冲销/再生行列表；空入参返回空列表
     */
    public List<ChargeItem> listChargeItemsByRefundIds(Collection<Long> refundIds) {
        if (refundIds == null || refundIds.isEmpty()) {
            return List.of();
        }
        return repository.findAllByRefundIdIn(refundIds);
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
     * 门诊费用明细：补全 payment {@code charge_item_ids} 快照截断漏挂的已结/退费中/已退行（DM-ADM-011 §6.2.25 扩展）。
     *
     * <p>当 payment 快照 JSON 截断时，Support 按就诊 + 状态 + 收费人 SQL 补全候选行（AS-OP-FIN-005 **BR-OP-FDT-009**）。
     * 收费人匹配 {@code enterer_id}（与 **BR-OPC-016** 结算写入口径一致）。</p>
     *
     * @param encounterIds          bill_date 筛选命中 payment 涉及的就诊主键
     * @param excludedChargeItemIds 已由快照解析的主键；空集合表示不排除
     * @param allowedStatusCodes    收退 Tab 映射的状态白名单（SETTLED / REFUNDING / REFUNDED）
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
                // 多选收费人：匹配结算写入的 enterer_id（BR-OPC-016）
                predicates.add(root.get(FieldNameConstants.ENTERER_ID).in(entererMatchIds));
            } else if (cashierId != null) {
                predicates.add(cb.equal(root.get(FieldNameConstants.ENTERER_ID), cashierId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return repository.findAll(specification);
    }

    /**
     * 门诊费用明细：按就诊块分页返回候选行（DM-ADM-011 §6.2.25；AS-OP-FIN-005 IF-OP-FDT-02/03）。
     *
     * <p>块内为同一就诊下全部命中 chargeItem 行；块间按块内最新 {@code bill_date} 倒序（**BR-OP-FDT-014**）。
     * 须先经 Support 写入 {@link OutpatientFeeDetailPageQuery#getResolvedPaymentChargeItemLinks()}
     * 与可选 {@link OutpatientFeeDetailPageQuery#getPreloadedChargeItemsById()}。</p>
     *
     * <p><strong>仅当前页装载行</strong>：先在 links+ChargeItem Map 上轻量得到就诊有序全集与 total，
     * 再只对本页 encounter 组装 {@link OutpatientFeeDetailChargeRowQueryModel}。
     * <strong>禁止</strong>为本方法调用 {@link #listOutpatientFeeDetailChargeRows} 做全窗 RowQueryModel 物化后再
     * {@code subList}（SKILL §2.1 #9）。汇总/导出全量路径仍走 {@link #listOutpatientFeeDetailChargeRows}。</p>
     *
     * @param query 须含 resolved payment 上下文；分页缺省见 {@link OutpatientFeeDetailConstants}
     * @return 就诊块分页；scope 为空时返回空页（total=0）
     */
    public Page<OutpatientFeeDetailEncounterBlockQueryModel> pageOutpatientFeeDetailEncounterBlocks(
        OutpatientFeeDetailPageQuery query) {
        int pageNo = this.resolveOutpatientFeeDetailPageNo(query);
        int pageSize = this.resolveOutpatientFeeDetailPageSize(query);
        PageRequest pageable = PageRequest.of(pageNo, pageSize);
        if (query == null) {
            return new PageImpl<>(Collections.emptyList(), pageable, 0L);
        }
        List<OutpatientFeeDetailChargePaymentLinkQueryModel> paymentLinks =
            query.getResolvedPaymentChargeItemLinks();
        // Gate：无 payment 链路则无就诊块
        if (paymentLinks == null || paymentLinks.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, 0L);
        }
        LinkedHashSet<Long> chargeItemIds = new LinkedHashSet<>();
        for (OutpatientFeeDetailChargePaymentLinkQueryModel link : paymentLinks) {
            if (link != null && link.getChargeItemId() != null) {
                chargeItemIds.add(link.getChargeItemId());
            }
        }
        // Gate：链路缺 chargeItemId 时无法形成就诊块
        if (chargeItemIds.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, 0L);
        }
        // Pre：优先 Support 预加载；缺失时按 id 分批 IN（ID_IN_QUERY_CHUNK_SIZE）
        Map<Long, ChargeItem> chargeItemById =
            this.resolveOutpatientFeeDetailChargeItemMap(query, chargeItemIds);
        // Pre：分母写回 payment 上下文，供本页行实收分摊（与 list 路径同口径）
        this.enrichOutpatientFeeDetailPaymentSnapshotDenominators(chargeItemById, paymentLinks);
        Set<String> allowedStatusCodes = this.resolveOutpatientFeeDetailAllowedStatusCodes(query);
        // pageScopeOnly：links/preloaded 已是本页；total 用 DB 分页结果，禁止再 collect 全日窗 encounter 序
        if (Boolean.TRUE.equals(query.getPageScopeOnly())) {
            long pageScopeTotal = 0L;
            if (query.getResolvedEncounterPageTotal() != null) {
                pageScopeTotal = query.getResolvedEncounterPageTotal().longValue();
            }
            List<Long> pageEncounterOrder = this.collectOrderedEncounterIdsForOutpatientFeeDetail(
                paymentLinks, chargeItemById, query, allowedStatusCodes);
            List<OutpatientFeeDetailEncounterBlockQueryModel> pageScopeBlocks =
                this.buildOutpatientFeeDetailEncounterBlocksForEncounters(
                    paymentLinks, chargeItemById, query, allowedStatusCodes, pageEncounterOrder);
            return new PageImpl<>(pageScopeBlocks, pageable, pageScopeTotal);
        }
        // Pre：轻量 encounter 排序键（max billDate），禁止先物化全窗 RowQueryModel
        List<Long> encounterOrder = this.collectOrderedEncounterIdsForOutpatientFeeDetail(
            paymentLinks, chargeItemById, query, allowedStatusCodes);
        long total = encounterOrder.size();
        // 页码越界时钳回末页，避免前端停在空页（分页 bug）
        if (total > 0 && pageSize > 0) {
            int maxPageIndex = (int) ((total - 1) / pageSize);
            if (pageNo > maxPageIndex) {
                pageNo = maxPageIndex;
            }
        }
        pageable = PageRequest.of(pageNo, pageSize);
        int fromIndex = pageNo * pageSize;
        if (fromIndex >= encounterOrder.size()) {
            // 无命中块时返回空 content、保留 total
            return new PageImpl<>(Collections.emptyList(), pageable, total);
        }
        int toIndex = Math.min(fromIndex + pageSize, encounterOrder.size());
        List<Long> pageEncounterIds = new ArrayList<>(encounterOrder.subList(fromIndex, toIndex));
        // Asm：仅本页 encounter 组装行与就诊块
        List<OutpatientFeeDetailEncounterBlockQueryModel> blocks =
            this.buildOutpatientFeeDetailEncounterBlocksForEncounters(
                paymentLinks, chargeItemById, query, allowedStatusCodes, pageEncounterIds);
        return new PageImpl<>(blocks, pageable, total);
    }

    /**
     * 从 payment 链路 + ChargeItem Map 轻量收集就诊有序列表（块间按 max billDate 倒序）。
     *
     * <p>仅读取状态/就诊 scope/费用大类过滤所需字段与 {@code paymentContext.billDate}，
     * 不构建 {@link OutpatientFeeDetailChargeRowQueryModel}。供
     * {@link #pageOutpatientFeeDetailEncounterBlocks} 算 total 与本页 encounter 切片。</p>
     *
     * @param paymentLinks       resolved payment–chargeItem 链路
     * @param chargeItemById     id → ChargeItem（预加载或分批 IN）
     * @param query              含就诊 scope、费用大类
     * @param allowedStatusCodes 状态白名单
     * @return 按块间 billDate 倒序的 encounterId 列表；无命中为空列表
     */
    private List<Long> collectOrderedEncounterIdsForOutpatientFeeDetail(
        List<OutpatientFeeDetailChargePaymentLinkQueryModel> paymentLinks,
        Map<Long, ChargeItem> chargeItemById,
        OutpatientFeeDetailPageQuery query,
        Set<String> allowedStatusCodes) {
        Map<Long, LocalDateTime> maxBillDateByEncounter = new HashMap<>();
        for (OutpatientFeeDetailChargePaymentLinkQueryModel link : paymentLinks) {
            if (link == null || link.getChargeItemId() == null || link.getPaymentContext() == null) {
                continue;
            }
            ChargeItem chargeItem = chargeItemById.get(link.getChargeItemId());
            if (!this.matchesOutpatientFeeDetailRowFilters(chargeItem, query, allowedStatusCodes)) {
                continue;
            }
            Long encounterId = chargeItem.getEncounterId();
            if (encounterId == null) {
                continue;
            }
            LocalDateTime billDate = link.getPaymentContext().getBillDate();
            LocalDateTime currentMax = maxBillDateByEncounter.get(encounterId);
            if (currentMax == null) {
                maxBillDateByEncounter.put(encounterId, billDate);
            } else if (billDate != null && billDate.isAfter(currentMax)) {
                // 同就诊取最新 billDate 作为块间排序键（BR-OP-FDT-014）
                maxBillDateByEncounter.put(encounterId, billDate);
            }
        }
        List<Long> encounterOrder = new ArrayList<>(maxBillDateByEncounter.keySet());
        encounterOrder.sort((leftId, rightId) -> {
            LocalDateTime leftMax = maxBillDateByEncounter.get(leftId);
            LocalDateTime rightMax = maxBillDateByEncounter.get(rightId);
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
        return encounterOrder;
    }

    /**
     * 按指定就诊有序集合组装费用明细就诊块（仅这些 encounter 的命中行）。
     *
     * <p>过滤口径与 {@link #listOutpatientFeeDetailChargeRows} 一致；块内按 billDate 倒序。
     * 调用方须已写入 payment 快照分母（见 {@link #enrichOutpatientFeeDetailPaymentSnapshotDenominators}）。</p>
     *
     * @param paymentLinks       resolved payment–chargeItem 链路
     * @param chargeItemById     id → ChargeItem
     * @param query              含就诊 scope、费用大类
     * @param allowedStatusCodes 状态白名单
     * @param orderedEncounterIds 本页（或导出切片）就诊主键，顺序即块间顺序
     * @return 就诊块列表；无命中为空列表
     */
    private List<OutpatientFeeDetailEncounterBlockQueryModel> buildOutpatientFeeDetailEncounterBlocksForEncounters(
        List<OutpatientFeeDetailChargePaymentLinkQueryModel> paymentLinks,
        Map<Long, ChargeItem> chargeItemById,
        OutpatientFeeDetailPageQuery query,
        Set<String> allowedStatusCodes,
        List<Long> orderedEncounterIds) {
        if (orderedEncounterIds == null || orderedEncounterIds.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> pageEncounterIdSet = new HashSet<>(orderedEncounterIds);
        Map<Long, List<OutpatientFeeDetailChargeRowQueryModel>> rowsByEncounter = new LinkedHashMap<>();
        for (Long encounterId : orderedEncounterIds) {
            if (encounterId != null) {
                rowsByEncounter.put(encounterId, new ArrayList<>());
            }
        }
        for (OutpatientFeeDetailChargePaymentLinkQueryModel link : paymentLinks) {
            if (link == null || link.getChargeItemId() == null || link.getPaymentContext() == null) {
                continue;
            }
            ChargeItem chargeItem = chargeItemById.get(link.getChargeItemId());
            if (!this.matchesOutpatientFeeDetailRowFilters(chargeItem, query, allowedStatusCodes)) {
                continue;
            }
            Long encounterId = chargeItem.getEncounterId();
            // 非本页就诊跳过，避免全窗 RowQueryModel 物化
            if (encounterId == null || !pageEncounterIdSet.contains(encounterId)) {
                continue;
            }
            List<OutpatientFeeDetailChargeRowQueryModel> bucket = rowsByEncounter.get(encounterId);
            if (bucket == null) {
                continue;
            }
            bucket.add(new OutpatientFeeDetailChargeRowQueryModel(chargeItem, link.getPaymentContext()));
        }
        List<OutpatientFeeDetailEncounterBlockQueryModel> blocks = new ArrayList<>();
        for (Long encounterId : orderedEncounterIds) {
            if (encounterId == null) {
                continue;
            }
            List<OutpatientFeeDetailChargeRowQueryModel> lines = rowsByEncounter.get(encounterId);
            if (lines == null || lines.isEmpty()) {
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
            if (lines.get(0).getChargeItem() != null) {
                block.setPatientId(lines.get(0).getChargeItem().getPatientId());
            }
            block.setLines(lines);
            blocks.add(block);
        }
        return blocks;
    }

    /**
     * 费用明细候选行过滤：状态白名单 + 就诊 scope + 费用大类（与 list 路径同口径）。
     *
     * @param chargeItem         收费行；空则不命中
     * @param query              含 scope / 大类
     * @param allowedStatusCodes 状态白名单
     * @return true 表示纳入就诊块/汇总候选
     */
    private boolean matchesOutpatientFeeDetailRowFilters(
        ChargeItem chargeItem,
        OutpatientFeeDetailPageQuery query,
        Set<String> allowedStatusCodes) {
        if (chargeItem == null) {
            return false;
        }
        if (chargeItem.getStatusEnum() == null || !allowedStatusCodes.contains(chargeItem.getStatusEnum())) {
            return false;
        }
        if (!this.matchesOutpatientFeeDetailEncounterScope(chargeItem, query.getResolvedEncounterScope())) {
            return false;
        }
        if (!this.matchesOutpatientFeeDetailFeeCategory(chargeItem, query.getFeeCategoryCodes())) {
            return false;
        }
        return true;
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
     * 按收费时间窗拉取已结/已退账单行（仅本聚合 Repository）。
     *
     * <p>时间轴为 {@code entered_date}（与 payment {@code bill_date} 同源，**BR-OPC-016**）。
     * 供医生工作量等仍需「收+退」双态的读路径；门/住院费用明细净口径请用
     * {@link #listSettledChargeItemsByEnteredDateWindow}（仅 SETTLED，退费不入统计）。
     * 方法名保留 Occurrence 后缀以兼容既有调用方。</p>
     *
     * @param enteredFromInclusive 收费时间起（含）
     * @param enteredToExclusive   收费时间止（不含）
     * @return 未软删且状态为 SETTLED/REFUNDED 的收费行；窗非法时返回空列表
     */
    public List<ChargeItem> listSettledOrRefundedChargeItemsByOccurrenceWindow(
        LocalDateTime enteredFromInclusive,
        LocalDateTime enteredToExclusive) {
        return this.listSettledOrRefundedChargeItemsByEnteredDateWindow(
            enteredFromInclusive, enteredToExclusive, null);
    }

    /**
     * 按收费时间窗 + 可选收费人白名单拉取已结/已退账单行（仅本聚合 Repository）。
     *
     * @param enteredFromInclusive 收费时间起（含）
     * @param enteredToExclusive   收费时间止（不含）
     * @param entererMatchIds      收费人 practitionerId 白名单；空则不按人过滤
     * @return 命中行；窗非法时返回空列表
     */
    public List<ChargeItem> listSettledOrRefundedChargeItemsByEnteredDateWindow(
        LocalDateTime enteredFromInclusive,
        LocalDateTime enteredToExclusive,
        Collection<Long> entererMatchIds) {
        return this.listChargeItemsByEnteredDateWindow(
            enteredFromInclusive,
            enteredToExclusive,
            entererMatchIds,
            List.of(
                ChargeItemStatus.SETTLED.getCode(),
                ChargeItemStatus.REFUNDED.getCode()));
    }

    /**
     * 门/住院费用明细净口径：按收费时间窗拉取<strong>仅已结</strong>账单行（仅本聚合 Repository）。
     *
     * <p>时间轴 {@code entered_date}；<strong>不</strong>纳入 {@code REFUNDED}——退费前必有收费，
     * 退费后账单退出统计，净额=仍有效收费（不改落库，仅统计口径）。可选收费人匹配
     * {@code enterer_id}（与结算写入口径一致）。</p>
     *
     * @param enteredFromInclusive 收费时间起（含）
     * @param enteredToExclusive   收费时间止（不含）
     * @param entererMatchIds      收费人 practitionerId 白名单；空则不按人过滤
     * @return 未软删且状态为 SETTLED 的收费行；窗非法时返回空列表
     */
    public List<ChargeItem> listSettledChargeItemsByEnteredDateWindow(
        LocalDateTime enteredFromInclusive,
        LocalDateTime enteredToExclusive,
        Collection<Long> entererMatchIds) {
        return this.listChargeItemsByEnteredDateWindow(
            enteredFromInclusive,
            enteredToExclusive,
            entererMatchIds,
            List.of(ChargeItemStatus.SETTLED.getCode()));
    }

    /**
     * 费用明细：日窗内 distinct {@code encounter_id}（仅选 Long，禁止装实体；仅本聚合 Criteria）。
     *
     * <p>谓词：{@code entered_date} 左闭右开、状态白名单、未软删、可选收费人 IN。
     * 供应用层再筛 AMB/IMP 与患者关键字，避免一个月全日窗实体预载。</p>
     *
     * @param enteredFromInclusive 收费时间起（含）
     * @param enteredToExclusive   收费时间止（不含）
     * @param statusCodes          状态码白名单；空则返回空列表
     * @param entererMatchIds      收费人白名单；空则不过滤
     * @return 去重就诊主键；窗非法时为空列表
     */
    public List<Long> listDistinctEncounterIdsByEnteredDateWindow(
        LocalDateTime enteredFromInclusive,
        LocalDateTime enteredToExclusive,
        Collection<String> statusCodes,
        Collection<Long> entererMatchIds) {
        if (enteredFromInclusive == null || enteredToExclusive == null) {
            return new ArrayList<>();
        }
        if (!enteredFromInclusive.isBefore(enteredToExclusive)) {
            return new ArrayList<>();
        }
        if (statusCodes == null || statusCodes.isEmpty()) {
            return new ArrayList<>();
        }
        CriteriaBuilder cb = this.entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<ChargeItem> root = cq.from(ChargeItem.class);
        Predicate windowPredicate = this.buildFeeDetailEnteredDateWindowPredicate(
            root, cb, enteredFromInclusive, enteredToExclusive, statusCodes, entererMatchIds, null, null, null);
        cq.select(root.get(FieldNameConstants.ENCOUNTER_ID)).distinct(true);
        cq.where(windowPredicate);
        return this.entityManager.createQuery(cq).getResultList();
    }

    /**
     * 费用明细明细分页：按 {@code GROUP BY encounter_id ORDER BY MAX(entered_date) DESC} DB 分页就诊主键
     * （金标准对齐 {@link #pageEncounterIdsForOutpatientChargeQueue}；仅本聚合 Criteria）。
     *
     * <p>谓词：日窗、状态白名单、未软删、可选 enterer / requestingOrg / encounter 白名单 /
     * service_table IN（费用大类）。页码越界时钳回末页。</p>
     *
     * @param enteredFromInclusive   收费时间起（含）
     * @param enteredToExclusive     收费时间止（不含）
     * @param statusCodes            状态码白名单
     * @param entererMatchIds        收费人白名单；空则不过滤
     * @param requestingOrgId        开立科室；空则不过滤
     * @param encounterIdWhitelist   就诊白名单；null 表示不按就诊收窄；空集合表示无命中
     * @param serviceTables          费用大类映射的 service_table 集合；空则不过滤
     * @param pageNoZeroBased        Spring 0-based 页码
     * @param pageSize               页大小
     * @return 就诊主键分页；total 为 distinct encounter 数
     */
    public Page<Long> pageFeeDetailEncounterIdsByEnteredDateWindow(
        LocalDateTime enteredFromInclusive,
        LocalDateTime enteredToExclusive,
        Collection<String> statusCodes,
        Collection<Long> entererMatchIds,
        Long requestingOrgId,
        Collection<Long> encounterIdWhitelist,
        Collection<String> serviceTables,
        int pageNoZeroBased,
        int pageSize) {
        int safePageSize = pageSize;
        if (safePageSize <= 0) {
            safePageSize = OutpatientFeeDetailConstants.DEFAULT_PAGE_SIZE;
        }
        int pageNo = pageNoZeroBased;
        if (pageNo < 0) {
            pageNo = 0;
        }
        PageRequest pageable = PageRequest.of(pageNo, safePageSize);
        if (enteredFromInclusive == null || enteredToExclusive == null
            || !enteredFromInclusive.isBefore(enteredToExclusive)) {
            return new PageImpl<>(Collections.emptyList(), pageable, 0L);
        }
        if (statusCodes == null || statusCodes.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, 0L);
        }
        // Gate：显式空白名单 = 无命中，避免误扫全表
        if (encounterIdWhitelist != null && encounterIdWhitelist.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, 0L);
        }
        CriteriaBuilder cb = this.entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> countCq = cb.createQuery(Long.class);
        Root<ChargeItem> countRoot = countCq.from(ChargeItem.class);
        Predicate countPredicate = this.buildFeeDetailEnteredDateWindowPredicate(
            countRoot,
            cb,
            enteredFromInclusive,
            enteredToExclusive,
            statusCodes,
            entererMatchIds,
            requestingOrgId,
            encounterIdWhitelist,
            serviceTables);
        countCq.select(cb.countDistinct(countRoot.get(FieldNameConstants.ENCOUNTER_ID)));
        countCq.where(countPredicate);
        Long totalRow = this.entityManager.createQuery(countCq).getSingleResult();
        long total = 0L;
        if (totalRow != null) {
            total = totalRow.longValue();
        }
        // 页码越界钳回末页，避免前端停在空页
        if (total > 0L && safePageSize > 0) {
            int maxPageIndex = (int) ((total - 1L) / safePageSize);
            if (pageNo > maxPageIndex) {
                pageNo = maxPageIndex;
            }
        }
        pageable = PageRequest.of(pageNo, safePageSize);
        CriteriaQuery<Long> dataCq = cb.createQuery(Long.class);
        Root<ChargeItem> dataRoot = dataCq.from(ChargeItem.class);
        Predicate dataPredicate = this.buildFeeDetailEnteredDateWindowPredicate(
            dataRoot,
            cb,
            enteredFromInclusive,
            enteredToExclusive,
            statusCodes,
            entererMatchIds,
            requestingOrgId,
            encounterIdWhitelist,
            serviceTables);
        dataCq.select(dataRoot.get(FieldNameConstants.ENCOUNTER_ID));
        dataCq.where(dataPredicate);
        dataCq.groupBy(dataRoot.get(FieldNameConstants.ENCOUNTER_ID));
        dataCq.orderBy(cb.desc(cb.max(dataRoot.get(FieldNameConstants.ENTERED_DATE))));
        TypedQuery<Long> typed = this.entityManager.createQuery(dataCq);
        typed.setFirstResult(pageNo * safePageSize);
        typed.setMaxResults(safePageSize);
        List<Long> encounterIds = typed.getResultList();
        return new PageImpl<>(encounterIds, pageable, total);
    }

    /**
     * 费用明细：按本页就诊 IN + 日窗装载 ChargeItem 实体（IN 超限按 {@link GeneralSharedConstants#ID_IN_QUERY_CHUNK_SIZE} 分批）。
     *
     * <p>仅本聚合 Repository/Criteria；供明细分页在 DB 分页出 encounter 后再装本页实体。</p>
     *
     * @param encounterIds         本页就诊主键；空则返回空列表
     * @param enteredFromInclusive 收费时间起（含）
     * @param enteredToExclusive   收费时间止（不含）
     * @param statusCodes          状态白名单
     * @param entererMatchIds      收费人白名单；空则不过滤
     * @param requestingOrgId      开立科室；空则不过滤
     * @return 命中实体；无则空列表
     */
    public List<ChargeItem> listChargeItemsByEncounterIdsInEnteredDateWindow(
        Collection<Long> encounterIds,
        LocalDateTime enteredFromInclusive,
        LocalDateTime enteredToExclusive,
        Collection<String> statusCodes,
        Collection<Long> entererMatchIds,
        Long requestingOrgId) {
        List<Long> distinctEncounterIds = IdInQueryChunkSupport.distinctNonNullIds(encounterIds);
        if (distinctEncounterIds.isEmpty()) {
            return new ArrayList<>();
        }
        if (enteredFromInclusive == null || enteredToExclusive == null
            || !enteredFromInclusive.isBefore(enteredToExclusive)) {
            return new ArrayList<>();
        }
        if (statusCodes == null || statusCodes.isEmpty()) {
            return new ArrayList<>();
        }
        List<ChargeItem> merged = new ArrayList<>();
        // Pre：就诊 IN 分批，避免单次超长参数
        List<ChargeItem> chunkRows = IdInQueryChunkSupport.loadByIdInChunks(
            distinctEncounterIds,
            chunkIds -> this.listChargeItemsByEncounterIdsInEnteredDateWindowChunk(
                chunkIds,
                enteredFromInclusive,
                enteredToExclusive,
                statusCodes,
                entererMatchIds,
                requestingOrgId));
        merged.addAll(chunkRows);
        return merged;
    }

    /**
     * 费用明细页脚售价：同筛选谓词对 {@code total_price} 做 SQL SUM（禁止为页脚装全日窗实体）。
     *
     * <p>门诊净口径仅 SETTLED、无真实退费负号时直接 SUM；encounter 白名单过大时按
     * {@link GeneralSharedConstants#ID_IN_QUERY_CHUNK_SIZE} 分批 SUM 再相加。</p>
     *
     * @param enteredFromInclusive   收费时间起（含）
     * @param enteredToExclusive     收费时间止（不含）
     * @param statusCodes            状态白名单
     * @param entererMatchIds        收费人白名单；空则不过滤
     * @param requestingOrgId        开立科室；空则不过滤
     * @param encounterIdWhitelist   命中就诊全集；null 表示不按就诊收窄；空集合返回 0
     * @param serviceTables          service_table IN；空则不过滤
     * @return 结算刻度售价合计
     */
    public BigDecimal sumFeeDetailSaleTotalPriceByEnteredDateWindow(
        LocalDateTime enteredFromInclusive,
        LocalDateTime enteredToExclusive,
        Collection<String> statusCodes,
        Collection<Long> entererMatchIds,
        Long requestingOrgId,
        Collection<Long> encounterIdWhitelist,
        Collection<String> serviceTables) {
        if (enteredFromInclusive == null || enteredToExclusive == null
            || !enteredFromInclusive.isBefore(enteredToExclusive)) {
            return MonetaryAmountSupport.scaleSettle(BigDecimal.ZERO);
        }
        if (statusCodes == null || statusCodes.isEmpty()) {
            return MonetaryAmountSupport.scaleSettle(BigDecimal.ZERO);
        }
        if (encounterIdWhitelist != null && encounterIdWhitelist.isEmpty()) {
            return MonetaryAmountSupport.scaleSettle(BigDecimal.ZERO);
        }
        if (encounterIdWhitelist == null) {
            BigDecimal single = this.sumFeeDetailSaleTotalPriceByEnteredDateWindowChunk(
                enteredFromInclusive,
                enteredToExclusive,
                statusCodes,
                entererMatchIds,
                requestingOrgId,
                null,
                serviceTables);
            return MonetaryAmountSupport.scaleSettle(this.nullToZero(single));
        }
        List<Long> distinctEncounters = IdInQueryChunkSupport.distinctNonNullIds(encounterIdWhitelist);
        if (distinctEncounters.isEmpty()) {
            return MonetaryAmountSupport.scaleSettle(BigDecimal.ZERO);
        }
        BigDecimal sum = BigDecimal.ZERO;
        int chunkSize = GeneralSharedConstants.ID_IN_QUERY_CHUNK_SIZE;
        int from = 0;
        while (from < distinctEncounters.size()) {
            int to = Math.min(from + chunkSize, distinctEncounters.size());
            List<Long> chunk = distinctEncounters.subList(from, to);
            BigDecimal chunkSum = this.sumFeeDetailSaleTotalPriceByEnteredDateWindowChunk(
                enteredFromInclusive,
                enteredToExclusive,
                statusCodes,
                entererMatchIds,
                requestingOrgId,
                chunk,
                serviceTables);
            sum = sum.add(this.nullToZero(chunkSum));
            from = to;
        }
        return MonetaryAmountSupport.scaleSettle(sum);
    }

    /**
     * 费用明细硬筛选：日窗内轻量投影（id/encounter/product/account/definition/generateSource/serviceTable/total_price）。
     *
     * <p>禁止把结果当作业务实体预载进 {@code preloadedChargeItemsById}；仅供应用层过滤就诊白名单。
     * <strong>不选</strong> {@code unit_price}：细分类 fin_type 仅锚 definition_id（+ 挂号识别字段）。</p>
     *
     * @param enteredFromInclusive   收费时间起（含）
     * @param enteredToExclusive     收费时间止（不含）
     * @param statusCodes            状态白名单
     * @param entererMatchIds        收费人白名单；空则不过滤
     * @param requestingOrgId        开立科室；空则不过滤
     * @param encounterIdWhitelist   就诊白名单；null 不收窄；空集合返回空
     * @param serviceTables          service_table IN；空则不过滤
     * @return 投影行；无命中为空列表
     */
    public List<FeeDetailChargeItemFilterProjection> listFeeDetailChargeItemFilterProjections(
        LocalDateTime enteredFromInclusive,
        LocalDateTime enteredToExclusive,
        Collection<String> statusCodes,
        Collection<Long> entererMatchIds,
        Long requestingOrgId,
        Collection<Long> encounterIdWhitelist,
        Collection<String> serviceTables) {
        if (enteredFromInclusive == null || enteredToExclusive == null
            || !enteredFromInclusive.isBefore(enteredToExclusive)) {
            return new ArrayList<>();
        }
        if (statusCodes == null || statusCodes.isEmpty()) {
            return new ArrayList<>();
        }
        if (encounterIdWhitelist != null && encounterIdWhitelist.isEmpty()) {
            return new ArrayList<>();
        }
        if (encounterIdWhitelist == null) {
            return this.listFeeDetailChargeItemFilterProjectionsChunk(
                enteredFromInclusive,
                enteredToExclusive,
                statusCodes,
                entererMatchIds,
                requestingOrgId,
                null,
                serviceTables);
        }
        List<Long> distinctEncounters = IdInQueryChunkSupport.distinctNonNullIds(encounterIdWhitelist);
        if (distinctEncounters.isEmpty()) {
            return new ArrayList<>();
        }
        List<FeeDetailChargeItemFilterProjection> merged = new ArrayList<>();
        int chunkSize = GeneralSharedConstants.ID_IN_QUERY_CHUNK_SIZE;
        int from = 0;
        while (from < distinctEncounters.size()) {
            int to = Math.min(from + chunkSize, distinctEncounters.size());
            List<Long> chunk = distinctEncounters.subList(from, to);
            merged.addAll(this.listFeeDetailChargeItemFilterProjectionsChunk(
                enteredFromInclusive,
                enteredToExclusive,
                statusCodes,
                entererMatchIds,
                requestingOrgId,
                chunk,
                serviceTables));
            from = to;
        }
        return merged;
    }

    /**
     * 费用大类 code 列表 → {@code service_table} 集合（反向映射 {@link #resolveOutpatientFeeDetailFeeCategoryCode}）。
     *
     * @param feeCategoryCodes 大类多选；空则返回空列表（调用方视为不过滤）
     * @return 去重表名；未知大类忽略
     */
    public List<String> resolveServiceTablesForFeeCategories(List<String> feeCategoryCodes) {
        if (feeCategoryCodes == null || feeCategoryCodes.isEmpty()) {
            return new ArrayList<>();
        }
        LinkedHashSet<String> tables = new LinkedHashSet<>();
        for (String categoryCode : feeCategoryCodes) {
            if (StringUtils.isEmpty(categoryCode)) {
                continue;
            }
            String normalized = categoryCode.trim();
            if (OutpatientFeeDetailConstants.FEE_CATEGORY_MEDICATION.equals(normalized)) {
                tables.add(TableNameConstants.MED_MEDICATION_REQUEST);
                tables.add(TableNameConstants.MED_MEDICATION_DISPENSE);
            } else if (OutpatientFeeDetailConstants.FEE_CATEGORY_ACTIVITY.equals(normalized)) {
                tables.add(TableNameConstants.WKF_SERVICE_REQUEST);
            } else if (OutpatientFeeDetailConstants.FEE_CATEGORY_DEVICE.equals(normalized)) {
                tables.add(TableNameConstants.WKF_DEVICE_REQUEST);
                tables.add(TableNameConstants.WKF_DEVICE_DISPENSE);
            } else if (OutpatientFeeDetailConstants.FEE_CATEGORY_REGISTER.equals(normalized)) {
                tables.add(TableNameConstants.ADM_HEALTHCARE_SERVICE);
            }
        }
        return new ArrayList<>(tables);
    }

    /**
     * 单批就诊 IN：日窗内装载 ChargeItem 实体。
     *
     * @param encounterIds         本批就诊主键
     * @param enteredFromInclusive 收费时间起（含）
     * @param enteredToExclusive   收费时间止（不含）
     * @param statusCodes          状态白名单
     * @param entererMatchIds      收费人白名单
     * @param requestingOrgId      开立科室
     * @return 本批命中实体
     */
    private List<ChargeItem> listChargeItemsByEncounterIdsInEnteredDateWindowChunk(
        Collection<Long> encounterIds,
        LocalDateTime enteredFromInclusive,
        LocalDateTime enteredToExclusive,
        Collection<String> statusCodes,
        Collection<Long> entererMatchIds,
        Long requestingOrgId) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            return new ArrayList<>();
        }
        Specification<ChargeItem> specification = (root, criteriaQuery, cb) -> this.buildFeeDetailEnteredDateWindowPredicate(
            root,
            cb,
            enteredFromInclusive,
            enteredToExclusive,
            statusCodes,
            entererMatchIds,
            requestingOrgId,
            encounterIds,
            null);
        return repository.findAll(specification);
    }

    /**
     * 单批（或无就诊 IN）页脚 {@code SUM(total_price)}。
     *
     * @param enteredFromInclusive   收费时间起（含）
     * @param enteredToExclusive     收费时间止（不含）
     * @param statusCodes            状态白名单
     * @param entererMatchIds        收费人白名单
     * @param requestingOrgId        开立科室
     * @param encounterIdWhitelist   本批就诊；null 表示不按就诊收窄
     * @param serviceTables          service_table IN
     * @return 未刻度原始合计；无行时 0
     */
    private BigDecimal sumFeeDetailSaleTotalPriceByEnteredDateWindowChunk(
        LocalDateTime enteredFromInclusive,
        LocalDateTime enteredToExclusive,
        Collection<String> statusCodes,
        Collection<Long> entererMatchIds,
        Long requestingOrgId,
        Collection<Long> encounterIdWhitelist,
        Collection<String> serviceTables) {
        CriteriaBuilder cb = this.entityManager.getCriteriaBuilder();
        CriteriaQuery<BigDecimal> cq = cb.createQuery(BigDecimal.class);
        Root<ChargeItem> root = cq.from(ChargeItem.class);
        Predicate predicate = this.buildFeeDetailEnteredDateWindowPredicate(
            root,
            cb,
            enteredFromInclusive,
            enteredToExclusive,
            statusCodes,
            entererMatchIds,
            requestingOrgId,
            encounterIdWhitelist,
            serviceTables);
        cq.select(cb.sum(root.get(FieldNameConstants.TOTAL_PRICE)));
        cq.where(predicate);
        BigDecimal sum = this.entityManager.createQuery(cq).getSingleResult();
        if (sum == null) {
            return BigDecimal.ZERO;
        }
        return sum;
    }

    /**
     * 单批轻量投影查询（含 fin_type 锚点与挂号识别字段；不含 unit_price）。
     *
     * @param enteredFromInclusive   收费时间起（含）
     * @param enteredToExclusive     收费时间止（不含）
     * @param statusCodes            状态白名单
     * @param entererMatchIds        收费人白名单
     * @param requestingOrgId        开立科室
     * @param encounterIdWhitelist   本批就诊；null 不收窄
     * @param serviceTables          service_table IN
     * @return 投影行
     */
    private List<FeeDetailChargeItemFilterProjection> listFeeDetailChargeItemFilterProjectionsChunk(
        LocalDateTime enteredFromInclusive,
        LocalDateTime enteredToExclusive,
        Collection<String> statusCodes,
        Collection<Long> entererMatchIds,
        Long requestingOrgId,
        Collection<Long> encounterIdWhitelist,
        Collection<String> serviceTables) {
        CriteriaBuilder cb = this.entityManager.getCriteriaBuilder();
        CriteriaQuery<Object[]> cq = cb.createQuery(Object[].class);
        Root<ChargeItem> root = cq.from(ChargeItem.class);
        Predicate predicate = this.buildFeeDetailEnteredDateWindowPredicate(
            root,
            cb,
            enteredFromInclusive,
            enteredToExclusive,
            statusCodes,
            entererMatchIds,
            requestingOrgId,
            encounterIdWhitelist,
            serviceTables);
        // Pre：细分类硬筛需 definition_id + 挂号识别字段；故意不选 unit_price（性能与口径，见 BR-OP-FDT-017）
        cq.multiselect(
            root.get(FieldNameConstants.ID),
            root.get(FieldNameConstants.ENCOUNTER_ID),
            root.get(FieldNameConstants.PRODUCT_TABLE),
            root.get(FieldNameConstants.PRODUCT_ID),
            root.get(FieldNameConstants.ACCOUNT_ID),
            root.get(FieldNameConstants.DEFINITION_ID),
            root.get(FieldNameConstants.GENERATE_SOURCE_ENUM),
            root.get(FieldNameConstants.SERVICE_TABLE),
            root.get(FieldNameConstants.TOTAL_PRICE));
        cq.where(predicate);
        List<Object[]> rows = this.entityManager.createQuery(cq).getResultList();
        List<FeeDetailChargeItemFilterProjection> projections = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            if (row == null || row.length < 9) {
                continue;
            }
            FeeDetailChargeItemFilterProjection projection = new FeeDetailChargeItemFilterProjection();
            if (row[0] instanceof Long) {
                projection.setId((Long) row[0]);
            }
            if (row[1] instanceof Long) {
                projection.setEncounterId((Long) row[1]);
            }
            if (row[2] instanceof String) {
                projection.setProductTable((String) row[2]);
            }
            if (row[3] instanceof Long) {
                projection.setProductId((Long) row[3]);
            }
            if (row[4] instanceof Long) {
                projection.setAccountId((Long) row[4]);
            }
            if (row[5] instanceof Long) {
                projection.setDefinitionId((Long) row[5]);
            }
            if (row[6] instanceof String) {
                projection.setGenerateSourceEnum((String) row[6]);
            }
            if (row[7] instanceof String) {
                projection.setServiceTable((String) row[7]);
            }
            if (row[8] instanceof BigDecimal) {
                projection.setTotalPrice((BigDecimal) row[8]);
            }
            projections.add(projection);
        }
        return projections;
    }

    /**
     * 费用明细日窗公共谓词：未软删 + 状态 + entered_date 窗 + 可选 enterer/requestingOrg/encounter/service_table。
     *
     * @param root                   ChargeItem 根
     * @param cb                     CriteriaBuilder
     * @param enteredFromInclusive   收费时间起（含）
     * @param enteredToExclusive     收费时间止（不含）
     * @param statusCodes            状态白名单
     * @param entererMatchIds        收费人白名单；空则不过滤
     * @param requestingOrgId        开立科室；空则不过滤
     * @param encounterIdWhitelist   就诊白名单；null 不过滤；空集合由调用方短路
     * @param serviceTables          service_table IN；空则不过滤
     * @return AND 谓词
     */
    private Predicate buildFeeDetailEnteredDateWindowPredicate(
        Root<ChargeItem> root,
        CriteriaBuilder cb,
        LocalDateTime enteredFromInclusive,
        LocalDateTime enteredToExclusive,
        Collection<String> statusCodes,
        Collection<Long> entererMatchIds,
        Long requestingOrgId,
        Collection<Long> encounterIdWhitelist,
        Collection<String> serviceTables) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
        predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(statusCodes));
        predicates.add(cb.greaterThanOrEqualTo(root.get(FieldNameConstants.ENTERED_DATE), enteredFromInclusive));
        predicates.add(cb.lessThan(root.get(FieldNameConstants.ENTERED_DATE), enteredToExclusive));
        if (entererMatchIds != null && !entererMatchIds.isEmpty()) {
            predicates.add(root.get(FieldNameConstants.ENTERER_ID).in(entererMatchIds));
        }
        if (requestingOrgId != null) {
            predicates.add(cb.equal(root.get(FieldNameConstants.REQUESTING_ORG_ID), requestingOrgId));
        }
        if (encounterIdWhitelist != null) {
            predicates.add(root.get(FieldNameConstants.ENCOUNTER_ID).in(encounterIdWhitelist));
        }
        if (serviceTables != null && !serviceTables.isEmpty()) {
            predicates.add(root.get(FieldNameConstants.SERVICE_TABLE).in(serviceTables));
        }
        return cb.and(predicates.toArray(new Predicate[0]));
    }

    /**
     * 按 {@code entered_date} 左闭右开窗 + 状态白名单批量读收费行（仅本聚合 Repository）。
     *
     * @param enteredFromInclusive 收费时间起（含）
     * @param enteredToExclusive   收费时间止（不含）
     * @param entererMatchIds      收费人白名单；空则不过滤
     * @param statusCodes          状态码白名单；空则返回空列表
     * @return 命中行
     */
    public List<ChargeItem> listChargeItemsByEnteredDateWindow(
        LocalDateTime enteredFromInclusive,
        LocalDateTime enteredToExclusive,
        Collection<Long> entererMatchIds,
        Collection<String> statusCodes) {
        return this.listChargeItemsByTimeFieldWindow(
            FieldNameConstants.ENTERED_DATE,
            enteredFromInclusive,
            enteredToExclusive,
            entererMatchIds,
            statusCodes);
    }

    /**
     * 按 {@code occurrence_time} 左闭右开窗 + 状态白名单批量读收费行（仅本聚合 Repository）。
     *
     * <p>待结算行创建时<strong>不写</strong> {@code entered_date}（结算才落库），住院费用明细查待结须走本方法。</p>
     *
     * @param occurrenceFromInclusive 发生时间起（含）
     * @param occurrenceToExclusive   发生时间止（不含）
     * @param entererMatchIds         收费人白名单；空则不过滤（待结通常无 enterer）
     * @param statusCodes             状态码白名单；空则返回空列表
     * @return 命中行
     */
    public List<ChargeItem> listChargeItemsByOccurrenceTimeWindow(
        LocalDateTime occurrenceFromInclusive,
        LocalDateTime occurrenceToExclusive,
        Collection<Long> entererMatchIds,
        Collection<String> statusCodes) {
        return this.listChargeItemsByTimeFieldWindow(
            FieldNameConstants.OCCURRENCE_TIME,
            occurrenceFromInclusive,
            occurrenceToExclusive,
            entererMatchIds,
            statusCodes);
    }

    /**
     * 按指定时间字段左闭右开窗 + 状态白名单批量读收费行（仅本聚合 Repository）。
     *
     * @param timeField            实体时间字段名（{@code enteredDate}/{@code occurrenceTime}）
     * @param fromInclusive        时间起（含）
     * @param toExclusive          时间止（不含）
     * @param entererMatchIds      收费人白名单；空则不过滤
     * @param statusCodes          状态码白名单；空则返回空列表
     * @return 命中行
     */
    private List<ChargeItem> listChargeItemsByTimeFieldWindow(
        String timeField,
        LocalDateTime fromInclusive,
        LocalDateTime toExclusive,
        Collection<Long> entererMatchIds,
        Collection<String> statusCodes) {
        if (StringUtils.isEmpty(timeField)) {
            return new ArrayList<>();
        }
        if (fromInclusive == null || toExclusive == null) {
            return new ArrayList<>();
        }
        if (!fromInclusive.isBefore(toExclusive)) {
            return new ArrayList<>();
        }
        if (statusCodes == null || statusCodes.isEmpty()) {
            return new ArrayList<>();
        }
        Specification<ChargeItem> specification = (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(statusCodes));
            predicates.add(cb.greaterThanOrEqualTo(root.get(timeField), fromInclusive));
            predicates.add(cb.lessThan(root.get(timeField), toExclusive));
            if (entererMatchIds != null && !entererMatchIds.isEmpty()) {
                predicates.add(root.get(FieldNameConstants.ENTERER_ID).in(entererMatchIds));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return repository.findAll(specification);
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
        // Pre：按 payment 链路轻量分桶，禁止默认 FIN_TYPE 路径物化 cachedChargeRows
        return this.summarizeFeeDetailRowsByDefinitionFromPaymentLinks(query);
    }

    /**
     * 住院费用明细就诊块分页（AS-RPT-009；DM-ADM-011 §6.2.33）。
     *
     * <p>读侧规约与门诊费用明细同构：由 Support 写入 IMP 就诊 scope 与 payment 快照后调用。
     * 委托 {@link #pageOutpatientFeeDetailEncounterBlocks}：仅当前页装载行，禁止全窗 RowQueryModel 物化。
     * 禁止用护士站单就诊 {@code queryInpatientCostDetails} 替代本报表口径。</p>
     *
     * @param query 与门诊费用明细 PageQuery 同结构（住院域预解析）
     * @return 就诊块分页
     */
    public Page<OutpatientFeeDetailEncounterBlockQueryModel> pageInpatientFeeDetailEncounterBlocks(
        OutpatientFeeDetailPageQuery query) {
        // Pre/Asm：委托同构门诊就诊块分页（scope 已由应用层区分 AMB/IMP；仅当前页装载）
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
        // Pre/Asm：与门诊同轻量链路分桶，仅 scope 不同
        return this.summarizeFeeDetailRowsByDefinitionFromPaymentLinks(query);
    }

    /**
     * 重点药品使用统计：费用明细 scope 内按药典 productId 汇总净人次与售价净额（DM-ADM-011 §6.2.35；AS-RPT-011）。
     *
     * <p>仅保留 {@code productTable = med_medication_definition} 且 {@code productId} 落在入参集合的行；
     * 售价带收退符号代数和；净人次 = 按就诊汇总净售价 &gt; 0 的 distinct encounter。药典主键须由应用层预解析，
     * 本方法禁止 JOIN 药典表。{@code productDefinitionIds} 空则直接返回 0。</p>
     *
     * @param query                 已写入费用明细 payment/账单 scope；可复用 {@code cachedChargeRows}
     * @param productDefinitionIds  重点药类型对应的药典主键集合
     * @return 汇总读模型；无命中时人次 0、金额 0
     */
    public KeyMedicationUsageAggQueryModel summarizeKeyMedicationUsage(
        OutpatientFeeDetailPageQuery query,
        Collection<Long> productDefinitionIds) {
        KeyMedicationUsageAggQueryModel result = new KeyMedicationUsageAggQueryModel();
        result.setEncounterNetCount(0L);
        result.setSaleAmountTotal(MonetaryAmountSupport.scaleSettle(BigDecimal.ZERO));
        // Gate：无 query 或无药典主键 → 全 0，禁止为 0 结果再物化候选行
        if (query == null || productDefinitionIds == null || productDefinitionIds.isEmpty()) {
            return result;
        }
        Set<Long> productIdSet = new HashSet<>();
        // Loop：去空主键，供 O(1) 命中判定
        for (Long productDefinitionId : productDefinitionIds) {
            if (productDefinitionId != null) {
                productIdSet.add(productDefinitionId);
            }
        }
        if (productIdSet.isEmpty()) {
            // Gate：去空后仍无主键 → 全 0
            return result;
        }
        // Pre：复用费用明细候选行（同就诊类型多次重点药类型可共享缓存）
        List<OutpatientFeeDetailChargeRowQueryModel> rows = this.listOutpatientFeeDetailChargeRows(query);
        Map<Long, BigDecimal> netSaleByEncounterId = new LinkedHashMap<>();
        BigDecimal saleSum = BigDecimal.ZERO;
        // Loop：筛药典产品行并按就诊累计带符号售价
        for (OutpatientFeeDetailChargeRowQueryModel row : rows) {
            // 脏行跳过
            if (row == null || row.getChargeItem() == null) {
                continue;
            }
            ChargeItem chargeItem = row.getChargeItem();
            // 仅药典产品行（BR-RPT-KDU-005）
            if (!TableNameConstants.MED_MEDICATION_DEFINITION.equals(chargeItem.getProductTable())) {
                continue;
            }
            Long productId = chargeItem.getProductId();
            // 非本重点药类型目录主键跳过
            if (productId == null || !productIdSet.contains(productId)) {
                continue;
            }
            BigDecimal signedSale = this.applyPractitionerWorkloadPaymentSign(
                this.nullToZero(chargeItem.getTotalPrice()),
                row.getPaymentContext());
            saleSum = saleSum.add(signedSale);
            Long encounterId = chargeItem.getEncounterId();
            // 无就诊锚点的脏行仍计入金额，但不参与人次
            if (encounterId == null) {
                continue;
            }
            BigDecimal encounterNet = netSaleByEncounterId.get(encounterId);
            // 首次命中该就诊时从 0 起累加
            if (encounterNet == null) {
                encounterNet = BigDecimal.ZERO;
            }
            netSaleByEncounterId.put(encounterId, encounterNet.add(signedSale));
        }
        long encounterNetCount = 0L;
        // Loop：净售价 > 0 才计 1 人次（BR-RPT-KDU-003 方案 A）
        for (BigDecimal encounterNet : netSaleByEncounterId.values()) {
            // 全退或净额为 0 的就诊不计人次
            if (encounterNet != null && encounterNet.compareTo(BigDecimal.ZERO) > 0) {
                encounterNetCount = encounterNetCount + 1L;
            }
        }
        // Asm：结算刻度售价合计
        result.setEncounterNetCount(encounterNetCount);
        result.setSaleAmountTotal(MonetaryAmountSupport.scaleSettle(saleSum));
        return result;
    }

    /**
     * 重点药品矩阵：一次遍历费用明细候选行，按多套 productId 集合同时分桶（AS-RPT-011）。
     *
     * <p>同一就诊类型下多重点药类型禁止重复调用 {@link #listOutpatientFeeDetailChargeRows}；
     * 净人次/售价口径与 {@link #summarizeKeyMedicationUsage} 一致。</p>
     *
     * @param query                 已写入费用明细 scope
     * @param productIdsByBucketKey 分桶键（重点药类型 code）→ 药典主键集合
     * @return 分桶键 → 汇总读模型；缺失键补 0
     */
    public Map<String, KeyMedicationUsageAggQueryModel> summarizeKeyMedicationUsageMatrix(
        OutpatientFeeDetailPageQuery query,
        Map<String, ? extends Collection<Long>> productIdsByBucketKey) {
        Map<String, KeyMedicationUsageAggQueryModel> resultByKey = new LinkedHashMap<>();
        if (productIdsByBucketKey == null || productIdsByBucketKey.isEmpty()) {
            return resultByKey;
        }
        Map<String, Set<Long>> productIdSetByKey = new LinkedHashMap<>();
        Map<String, Map<Long, BigDecimal>> netSaleByEncounterByKey = new LinkedHashMap<>();
        Map<String, BigDecimal> saleSumByKey = new LinkedHashMap<>();
        // Pre：为每个分桶初始化累加器与 O(1) 产品命中集合
        for (Map.Entry<String, ? extends Collection<Long>> entry : productIdsByBucketKey.entrySet()) {
            String bucketKey = entry.getKey();
            if (StringUtils.isEmpty(bucketKey)) {
                continue;
            }
            resultByKey.put(bucketKey, this.emptyKeyMedicationUsageAgg());
            Set<Long> productIdSet = new HashSet<>();
            Collection<Long> productIds = entry.getValue();
            if (productIds != null) {
                for (Long productId : productIds) {
                    if (productId != null) {
                        productIdSet.add(productId);
                    }
                }
            }
            productIdSetByKey.put(bucketKey, productIdSet);
            netSaleByEncounterByKey.put(bucketKey, new LinkedHashMap<>());
            saleSumByKey.put(bucketKey, BigDecimal.ZERO);
        }
        if (resultByKey.isEmpty()) {
            return resultByKey;
        }
        // Gate：无 query 时各桶保持 0
        if (query == null) {
            return resultByKey;
        }
        // Pre：候选行只物化一次
        List<OutpatientFeeDetailChargeRowQueryModel> rows = this.listOutpatientFeeDetailChargeRows(query);
        // Loop：药典产品行按桶累加带符号售价与就诊净额
        for (OutpatientFeeDetailChargeRowQueryModel row : rows) {
            if (row == null || row.getChargeItem() == null) {
                continue;
            }
            ChargeItem chargeItem = row.getChargeItem();
            if (!TableNameConstants.MED_MEDICATION_DEFINITION.equals(chargeItem.getProductTable())) {
                continue;
            }
            Long productId = chargeItem.getProductId();
            if (productId == null) {
                continue;
            }
            BigDecimal signedSale = this.applyPractitionerWorkloadPaymentSign(
                this.nullToZero(chargeItem.getTotalPrice()),
                row.getPaymentContext());
            Long encounterId = chargeItem.getEncounterId();
            for (Map.Entry<String, Set<Long>> bucketEntry : productIdSetByKey.entrySet()) {
                String bucketKey = bucketEntry.getKey();
                Set<Long> allowedProductIds = bucketEntry.getValue();
                if (allowedProductIds == null || !allowedProductIds.contains(productId)) {
                    continue;
                }
                saleSumByKey.put(bucketKey, saleSumByKey.get(bucketKey).add(signedSale));
                if (encounterId == null) {
                    continue;
                }
                Map<Long, BigDecimal> netSaleByEncounter = netSaleByEncounterByKey.get(bucketKey);
                BigDecimal encounterNet = netSaleByEncounter.get(encounterId);
                if (encounterNet == null) {
                    encounterNet = BigDecimal.ZERO;
                }
                netSaleByEncounter.put(encounterId, encounterNet.add(signedSale));
            }
        }
        // Asm：各桶结算刻度售价 + 净人次（净售价 &gt; 0）
        for (String bucketKey : resultByKey.keySet()) {
            KeyMedicationUsageAggQueryModel agg = resultByKey.get(bucketKey);
            long encounterNetCount = 0L;
            Map<Long, BigDecimal> netSaleByEncounter = netSaleByEncounterByKey.get(bucketKey);
            if (netSaleByEncounter != null) {
                for (BigDecimal encounterNet : netSaleByEncounter.values()) {
                    if (encounterNet != null && encounterNet.compareTo(BigDecimal.ZERO) > 0) {
                        encounterNetCount = encounterNetCount + 1L;
                    }
                }
            }
            agg.setEncounterNetCount(encounterNetCount);
            agg.setSaleAmountTotal(MonetaryAmountSupport.scaleSettle(saleSumByKey.get(bucketKey)));
        }
        return resultByKey;
    }

    /**
     * 构造全 0 的重点药品汇总读模型。
     *
     * @return 人次 0、金额结算刻度 0
     */
    private KeyMedicationUsageAggQueryModel emptyKeyMedicationUsageAgg() {
        KeyMedicationUsageAggQueryModel agg = new KeyMedicationUsageAggQueryModel();
        agg.setEncounterNetCount(0L);
        agg.setSaleAmountTotal(MonetaryAmountSupport.scaleSettle(BigDecimal.ZERO));
        return agg;
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
            this.accumulateFeeDetailDefinitionSummaryFromRow(summary, chargeItem, row.getPaymentContext());
        }
        return new ArrayList<>(bucketByDefinition.values());
    }

    /**
     * 按 payment 链路轻量分桶 definition 净汇总（AS-RPT-008/009 汇总 Tab 默认路径）。
     *
     * <p>禁止写入 {@code cachedChargeRows}；非 FIN_TYPE 维由应用层按需再物化候选行。</p>
     *
     * @param query 须含 resolved payment 快照
     * @return 汇总行；无命中为空列表
     */
    private List<FeeDetailFinTypeSummaryQueryModel> summarizeFeeDetailRowsByDefinitionFromPaymentLinks(
        OutpatientFeeDetailPageQuery query) {
        if (query == null) {
            return Collections.emptyList();
        }
        List<OutpatientFeeDetailChargePaymentLinkQueryModel> paymentLinks =
            query.getResolvedPaymentChargeItemLinks();
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
        Map<Long, ChargeItem> chargeItemById =
            this.resolveOutpatientFeeDetailChargeItemMap(query, chargeItemIds);
        this.enrichOutpatientFeeDetailPaymentSnapshotDenominators(chargeItemById, paymentLinks);
        Set<String> allowedStatusCodes = this.resolveOutpatientFeeDetailAllowedStatusCodes(query);
        Map<Long, FeeDetailFinTypeSummaryQueryModel> bucketByDefinition = new LinkedHashMap<>();
        for (OutpatientFeeDetailChargePaymentLinkQueryModel link : paymentLinks) {
            if (link == null || link.getChargeItemId() == null || link.getPaymentContext() == null) {
                continue;
            }
            ChargeItem chargeItem = chargeItemById.get(link.getChargeItemId());
            if (!this.matchesOutpatientFeeDetailRowFilters(chargeItem, query, allowedStatusCodes)) {
                continue;
            }
            Long definitionId = chargeItem.getDefinitionId();
            if (definitionId == null) {
                continue;
            }
            FeeDetailFinTypeSummaryQueryModel summary = bucketByDefinition.get(definitionId);
            if (summary == null) {
                summary = new FeeDetailFinTypeSummaryQueryModel();
                summary.setDefinitionId(definitionId);
                bucketByDefinition.put(definitionId, summary);
            }
            this.accumulateFeeDetailDefinitionSummaryFromRow(
                summary, chargeItem, link.getPaymentContext());
        }
        return new ArrayList<>(bucketByDefinition.values());
    }

    /**
     * 将单条 charge 行带符号金额/数量累加到 definition 汇总桶。
     *
     * @param summary        目标桶
     * @param chargeItem     收费明细
     * @param paymentContext 账单上下文（收退符号）
     */
    private void accumulateFeeDetailDefinitionSummaryFromRow(
        FeeDetailFinTypeSummaryQueryModel summary,
        ChargeItem chargeItem,
        OutpatientFeeDetailPaymentContextQueryModel paymentContext) {
        BigDecimal receivable = this.applyPractitionerWorkloadPaymentSign(
            this.resolveOutpatientFeeDetailReceivableAmount(chargeItem),
            paymentContext);
        BigDecimal discounted = this.applyPractitionerWorkloadPaymentSign(
            this.resolveOutpatientFeeDetailDiscountedAmount(chargeItem),
            paymentContext);
        BigDecimal lineAmount = this.applyPractitionerWorkloadPaymentSign(
            this.nullToZero(chargeItem.getTotalPrice()),
            paymentContext);
        BigDecimal signedQuantity = this.applyPractitionerWorkloadPaymentSign(
            this.nullToZero(chargeItem.getQuantityValue()),
            paymentContext);
        summary.setReceivableAmountTotal(summary.getReceivableAmountTotal().add(receivable));
        summary.setDiscountedAmountTotal(summary.getDiscountedAmountTotal().add(discounted));
        summary.setLineAmountTotal(summary.getLineAmountTotal().add(lineAmount));
        summary.setLineCount(summary.getLineCount() + 1L);
        summary.setQuantityTotal(summary.getQuantityTotal().add(signedQuantity));
    }

    /**
     * 明细分页页脚售价合计：只扫 {@code resolvedPaymentChargeItemLinks} + 预载/Map，
     * <strong>禁止</strong>物化 {@link OutpatientFeeDetailChargeRowQueryModel} 列表、禁止写
     * {@code cachedChargeRows}、禁止 payment 快照分母 enrich（SKILL §2.1 #9；AS-RPT-008/009 §7）。
     *
     * <p>口径与应用层 Assembler 售价列一致：{@code |total_price|} + 退费 payment 取负（BR-OP-FDT-012）；
     * 行过滤与 {@link #listOutpatientFeeDetailChargeRows} 同 scope。进价合计不在此计算。</p>
     *
     * @param query 须已预解析 links；优先复用 {@code preloadedChargeItemsById}
     * @return 结算刻度（2 位）售价代数和；无命中为 0
     */
    public BigDecimal summarizeOutpatientFeeDetailSaleAmountFromPreloadedScope(
        OutpatientFeeDetailPageQuery query) {
        if (query == null) {
            return MonetaryAmountSupport.scaleSettle(BigDecimal.ZERO);
        }
        List<OutpatientFeeDetailChargePaymentLinkQueryModel> paymentLinks =
            query.getResolvedPaymentChargeItemLinks();
        // Gate：无 payment 链路则页脚售价为 0，禁止为合计再 IN 全窗 ChargeItem
        if (paymentLinks == null || paymentLinks.isEmpty()) {
            return MonetaryAmountSupport.scaleSettle(BigDecimal.ZERO);
        }
        LinkedHashSet<Long> chargeItemIds = new LinkedHashSet<>();
        // Pre：从 links 收集主键，供预载 Map 命中
        for (OutpatientFeeDetailChargePaymentLinkQueryModel link : paymentLinks) {
            if (link != null && link.getChargeItemId() != null) {
                chargeItemIds.add(link.getChargeItemId());
            }
        }
        if (chargeItemIds.isEmpty()) {
            return MonetaryAmountSupport.scaleSettle(BigDecimal.ZERO);
        }
        Map<Long, ChargeItem> chargeItemById =
            this.resolveOutpatientFeeDetailChargeItemMap(query, chargeItemIds);
        Set<String> allowedStatusCodes = this.resolveOutpatientFeeDetailAllowedStatusCodes(query);
        BigDecimal saleSum = BigDecimal.ZERO;
        // Asm：逐链路轻量加总售价；不建 RowQueryModel、不写缓存
        for (OutpatientFeeDetailChargePaymentLinkQueryModel link : paymentLinks) {
            if (link == null || link.getChargeItemId() == null || link.getPaymentContext() == null) {
                continue;
            }
            ChargeItem chargeItem = chargeItemById.get(link.getChargeItemId());
            if (!this.matchesOutpatientFeeDetailRowFilters(chargeItem, query, allowedStatusCodes)) {
                continue;
            }
            BigDecimal lineAbsAmount = this.nullToZero(chargeItem.getTotalPrice()).abs();
            BigDecimal signMultiplier = BigDecimal.ONE;
            // 退费 payment 链路售价取负（与 Assembler BR-OP-FDT-012 同构）
            if (Boolean.TRUE.equals(link.getPaymentContext().getRefundPayment())) {
                signMultiplier = new BigDecimal("-1");
            }
            saleSum = saleSum.add(
                MonetaryAmountSupport.applySignAtCalcScale(lineAbsAmount, signMultiplier));
        }
        return MonetaryAmountSupport.scaleSettle(saleSum);
    }

    /**
     * 在 payment 预解析 scope 内加载并过滤门诊费用明细候选行（仅本聚合 Repository）。
     *
     * <p>筛选链：状态白名单（缺省仅 {@code SETTLED}；门诊费用明细由 Loader 写入
     * {@code SETTLED}∪{@code REFUNDING}∪{@code REFUNDED}；住院报表可含 {@code PENDING_SETTLEMENT}）
     * → 就诊 scope → 费用大类多选。
     * 加载后可补全读上下文金额分母。同请求优先复用 {@link OutpatientFeeDetailPageQuery#getCachedChargeRows()}
     * 与 Support 写入的 {@link OutpatientFeeDetailPageQuery#getPreloadedChargeItemsById()}，避免 page/summary
     * 重复 IN 全量 ChargeItem。</p>
     *
     * <p>供<strong>汇总 Tab / 导出</strong>全量路径；明细分页页脚须改走
     * {@link #summarizeOutpatientFeeDetailSaleAmountFromPreloadedScope}，禁止为本方法仅为页脚再全窗物化。</p>
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
        // 净口径：缺省仅已结；住院可扩展待结算（REFUNDED 仍退出合计）
        Set<String> allowedStatusCodes = this.resolveOutpatientFeeDetailAllowedStatusCodes(query);
        List<OutpatientFeeDetailChargeRowQueryModel> rows = new ArrayList<>();
        // 按账单合成/真实链路逐条展开；费用明细路径一账单一行
        for (OutpatientFeeDetailChargePaymentLinkQueryModel link : paymentLinks) {
            if (link == null || link.getChargeItemId() == null || link.getPaymentContext() == null) {
                continue;
            }
            ChargeItem chargeItem = chargeItemById.get(link.getChargeItemId());
            if (!this.matchesOutpatientFeeDetailRowFilters(chargeItem, query, allowedStatusCodes)) {
                continue;
            }
            rows.add(new OutpatientFeeDetailChargeRowQueryModel(chargeItem, link.getPaymentContext()));
        }
        // Asm：写入请求级缓存，供同 Query 后续 page/summary 复用
        query.setCachedChargeRows(rows);
        return rows;
    }

    /**
     * 解析费用明细候选行状态白名单：空则仅已结（门诊默认口径）。
     *
     * @param query 可含 {@code allowedChargeItemStatusCodes}
     * @return 非空状态码集合
     */
    private Set<String> resolveOutpatientFeeDetailAllowedStatusCodes(OutpatientFeeDetailPageQuery query) {
        List<String> configured = query.getAllowedChargeItemStatusCodes();
        if (configured == null || configured.isEmpty()) {
            return Set.of(ChargeItemStatus.SETTLED.getCode());
        }
        LinkedHashSet<String> allowed = new LinkedHashSet<>();
        for (String statusCode : configured) {
            if (StringUtils.isNotEmpty(statusCode)) {
                allowed.add(statusCode.trim());
            }
        }
        if (allowed.isEmpty()) {
            return Set.of(ChargeItemStatus.SETTLED.getCode());
        }
        return allowed;
    }

    /**
     * 解析费用明细候选 ChargeItem Map：优先 Support 预加载，否则按 id 批量 IN（仅本聚合 Repository）。
     *
     * <p>同请求写回 {@link OutpatientFeeDetailPageQuery#setPreloadedChargeItemsById}，
     * 供明细分页页脚进价 / 售价 / 汇总二次读取复用，禁止同窗重复全量 IN。</p>
     *
     * @param query         可含 {@code preloadedChargeItemsById}
     * @param chargeItemIds 待解析主键
     * @return id → ChargeItem
     */
    private Map<Long, ChargeItem> resolveOutpatientFeeDetailChargeItemMap(
        OutpatientFeeDetailPageQuery query,
        LinkedHashSet<Long> chargeItemIds) {
        Map<Long, ChargeItem> result = new LinkedHashMap<>();
        if (chargeItemIds == null || chargeItemIds.isEmpty()) {
            return result;
        }
        Map<Long, ChargeItem> preloaded = null;
        if (query != null) {
            preloaded = query.getPreloadedChargeItemsById();
        }
        LinkedHashSet<Long> missingIds = new LinkedHashSet<>();
        // Pre：先吃预载，仅收集缺口 id
        for (Long chargeItemId : chargeItemIds) {
            if (chargeItemId == null) {
                continue;
            }
            ChargeItem fromPreload = null;
            if (preloaded != null) {
                fromPreload = preloaded.get(chargeItemId);
            }
            if (fromPreload != null) {
                result.put(chargeItemId, fromPreload);
            } else {
                missingIds.add(chargeItemId);
            }
        }
        // Pre：缺口分批 IN，禁止预载已覆盖时仍整窗重载
        if (!missingIds.isEmpty()) {
            ChargeItemQueryCommand loadCmd = new ChargeItemQueryCommand();
            loadCmd.setIds(new ArrayList<>(missingIds));
            List<ChargeItem> loaded = this.loadChargeItemsByIds(loadCmd);
            for (ChargeItem chargeItem : loaded) {
                if (chargeItem != null && chargeItem.getId() != null) {
                    result.put(chargeItem.getId(), chargeItem);
                }
            }
        }
        // Asm：合并写回预载，供同 Query 页脚进价 / 汇总复用
        if (query != null && !result.isEmpty()) {
            Map<Long, ChargeItem> mergedPreload = new LinkedHashMap<>();
            if (preloaded != null && !preloaded.isEmpty()) {
                mergedPreload.putAll(preloaded);
            }
            mergedPreload.putAll(result);
            query.setPreloadedChargeItemsById(mergedPreload);
        }
        return result;
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
     * @param query 须经 {@link com.openhis.health.application.report.practitionerworkload.support}
     *              预解析 payment 快照、{@code resolvedOrganizationIds}、{@code resolvedEncounterScope}
     * @return 汇总行；无命中时返回空列表
     */
    public List<PractitionerWorkloadFinTypeOrgSummaryQueryModel> summarizePractitionerWorkloadByFinType(
        PractitionerWorkloadPageQuery query) {
        // 候选行含费用与挂号 chargeItem（挂号费映射 fin_type 1012）；明细 IF 另路径排除
        return this.summarizePractitionerWorkloadByFinType(
            this.listPractitionerWorkloadChargeRows(query, false, null));
    }

    /**
     * 工作量 fin_type 汇总（复用已加载 scope 行，避免 IF-02 重复 loadChargeItemsByIds）。
     *
     * @param rows 已按费用/医嘱 scope 过滤的候选行
     * @return 汇总行；无命中时返回空列表
     */
    public List<PractitionerWorkloadFinTypeOrgSummaryQueryModel> summarizePractitionerWorkloadByFinType(
        List<PractitionerWorkloadChargeRowQueryModel> rows) {
        Map<String, PractitionerWorkloadFinTypeOrgSummaryQueryModel> bucketByKey = new LinkedHashMap<>();
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }
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
     * 工作量挂号统计：按挂号科室 + 号源类型净人次（DM-ADM-011 §6.2.29；历史收费链路口径）。
     *
     * <p><b>IF-RPT-WKL-02 已改走</b>
     * {@link com.openhis.health.domain.administration.encounter.service.EncounterQueryService#summarizePractitionerWorkloadRegistrationByOrgAndServiceType}
     * （对齐 AS-RPT-007）；本方法保留供收费链路排查，新业务禁止再接入。</p>
     *
     * @param query 须含 {@link PractitionerWorkloadPageQuery#getEncounterContextByEncounterId()}（Support 预载）
     * @return 汇总行；无命中时返回空列表
     */
    public List<PractitionerWorkloadRegistrationOrgSummaryQueryModel> summarizeRegistrationChargesByServiceType(
        PractitionerWorkloadPageQuery query) {
        return this.summarizeRegistrationChargesByServiceType(
            query,
            this.listPractitionerWorkloadChargeRows(query, true, null));
    }

    /**
     * 工作量挂号统计（复用已加载挂号 scope 行，避免与 fin_type 路径重复加载 chargeItem）。
     *
     * @param query 须含 Encounter 上下文（选医生时还须 registrationDoctorEncounterScope）
     * @param rows  已按挂号 scope 过滤的候选行
     * @return 汇总行；无命中时返回空列表
     */
    public List<PractitionerWorkloadRegistrationOrgSummaryQueryModel> summarizeRegistrationChargesByServiceType(
        PractitionerWorkloadPageQuery query,
        List<PractitionerWorkloadChargeRowQueryModel> rows) {
        Map<String, PractitionerWorkloadRegistrationOrgSummaryQueryModel> bucketByKey = new LinkedHashMap<>();
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }
        // 同一就诊只计一次：对齐 AS-RPT-007 挂号明细「一人次一就诊」，禁止按收费/退费链路重复计次
        LinkedHashSet<Long> countedEncounterIds = new LinkedHashSet<>();
        for (PractitionerWorkloadChargeRowQueryModel row : rows) {
            if (row == null || row.getChargeItem() == null || row.getPaymentContext() == null) {
                continue;
            }
            ChargeItem chargeItem = row.getChargeItem();
            if (!this.isPractitionerWorkloadRegistrationChargeItem(chargeItem)) {
                continue;
            }
            Long encounterId = chargeItem.getEncounterId();
            if (encounterId == null) {
                continue;
            }
            // 已处理过的就诊跳过（含退号态），避免多条挂号费/收退链路抬高人次
            if (countedEncounterIds.contains(encounterId)) {
                continue;
            }
            PractitionerWorkloadEncounterContextQueryModel encounterContext =
                this.resolvePractitionerWorkloadEncounterContext(query, encounterId);
            if (encounterContext == null) {
                continue;
            }
            // 选医生时仅 REGISTRATION_DOCTOR 命中就诊（BR-RPT-WKL-005）
            if (!this.matchesPractitionerWorkloadRegistrationDoctorScope(query, encounterId)) {
                continue;
            }
            countedEncounterIds.add(encounterId);
            // 净人次：有效挂号 +1；退号/待挂/错误态不计入（与 AS-RPT-007 resolveOutpatientRegistrationNetDelta 同口径）
            int delta = this.resolvePractitionerWorkloadRegistrationNetDelta(encounterContext.getStatusEnum());
            if (delta == 0) {
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
            summary.setNetChargeCount(summary.getNetChargeCount() + delta);
        }
        return new ArrayList<>(bucketByKey.values());
    }

    /**
     * 工作量明细分页：按 payment–chargeItem 链路展开（DM-ADM-011 §6.2.28；IF-RPT-WKL-03/04）。
     *
     * <p>一行 = 一条收费或退费 payment 快照内的 chargeItem（金额带正负，对齐门诊费用明细）；
     * 不含挂号 chargeItem。领域侧将读模型 {@code entererId}/{@code enteredDate} 置空。
     * 本方法仅轻量展开后切片，<strong>不</strong>做 Request enrich；应用层须先回填
     * {@code req_opened_time} 并排序后再调用 {@link #slicePractitionerWorkloadDetailRows}
     *（SKILL §2.1 #9；BR-RPT-WKL-012）。</p>
     *
     * @param query 须含预解析 scope；分页键 {@code pageNo}/{@code pageSize} 缺省 1 / 20
     * @return 链路明细行分页
     */
    public Page<PractitionerWorkloadRequestDetailQueryModel> pagePractitionerWorkloadDetailByRequest(
        PractitionerWorkloadPageQuery query) {
        List<PractitionerWorkloadChargeRowQueryModel> rows = this.listPractitionerWorkloadChargeRows(query, false, null);
        return this.pagePractitionerWorkloadDetailFromChargeRows(rows, query);
    }

    /**
     * 在已加载 charge 行上分页明细（避免重复 loadChargeItemsByIds；供应用层复用 scope 行）。
     *
     * <p><strong>注意</strong>：本方法仅轻量展开后内存切片，<strong>不</strong>回填
     * {@code req_opened_time}/开单医生。明细分页须由 AppService：展开 → 仅开立时间 enrich → 排序 →
     * {@link #slicePractitionerWorkloadDetailRows} → 当前页展示 enrich。</p>
     *
     * @param chargeRows 已按 scope 过滤的 charge 行
     * @param query      分页参数
     * @return 链路明细行分页（未按开立时间排序时顺序不稳定）
     */
    public Page<PractitionerWorkloadRequestDetailQueryModel> pagePractitionerWorkloadDetailFromChargeRows(
        List<PractitionerWorkloadChargeRowQueryModel> chargeRows,
        PractitionerWorkloadPageQuery query) {
        List<PractitionerWorkloadRequestDetailQueryModel> details =
            this.buildPractitionerWorkloadDetailRowsFromChargeRows(chargeRows);
        return this.slicePractitionerWorkloadDetailRows(details, query);
    }

    /**
     * 在已加载 charge 行上展开全部明细读模型（轻量；不含 Request 开立时间/开单医生，不含排序）。
     *
     * <p>明细分页：AppService 回填开立时间并排序后再 {@link #slicePractitionerWorkloadDetailRows}；
     * 导出：AppService 全量 enrich 开单医生+开立时间后再排序装配。</p>
     *
     * @param chargeRows 已按 scope 过滤的 charge 行
     * @return 未排序的链路明细行
     */
    public List<PractitionerWorkloadRequestDetailQueryModel> listPractitionerWorkloadDetailFromChargeRows(
        List<PractitionerWorkloadChargeRowQueryModel> chargeRows) {
        return this.buildPractitionerWorkloadDetailRowsFromChargeRows(chargeRows);
    }

    /**
     * 将 charge 行展开为明细链路读模型（不含排序与分页；不含 Request enrich）。
     *
     * @param chargeRows 已按 scope 过滤的 charge 行
     * @return 轻量明细读模型列表
     */
    public List<PractitionerWorkloadRequestDetailQueryModel> buildPractitionerWorkloadDetailRowsFromChargeRows(
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
            // 开单医生/开立时间由应用层自 Request.requester_id / req_opened_time 回填（enterer_* 已改语义为收费人）
            detail.setEntererId(null);
            detail.setEnteredDate(null);
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
     * 对<strong>已排序</strong>明细行做内存分页切片（AppService 主导排序键后调用）。
     *
     * @param details 已按开立时间 DESC 排好序的明细
     * @param query   分页参数
     * @return 当前页切片；total 为全窗明细条数
     */
    public Page<PractitionerWorkloadRequestDetailQueryModel> slicePractitionerWorkloadDetailRows(
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
     * 过滤工作量明细分支候选 charge 行（仅诊疗/药品/耗材三类 request 表；不含挂号费）。
     *
     * @param chargeRows 已按 scope 过滤的 charge 行
     * @return 可展开为明细链路的行；无命中时空列表
     */
    public List<PractitionerWorkloadChargeRowQueryModel> filterPractitionerWorkloadAdviceChargeRows(
        List<PractitionerWorkloadChargeRowQueryModel> chargeRows) {
        List<PractitionerWorkloadChargeRowQueryModel> filtered = new ArrayList<>();
        if (chargeRows == null || chargeRows.isEmpty()) {
            return filtered;
        }
        for (PractitionerWorkloadChargeRowQueryModel row : chargeRows) {
            if (row == null || row.getChargeItem() == null || row.getPaymentContext() == null) {
                continue;
            }
            if (!this.isPractitionerWorkloadAdviceServiceTable(row.getChargeItem().getServiceTable())) {
                continue;
            }
            filtered.add(row);
        }
        return filtered;
    }

    /**
     * 对已排序的 charge 行做内存分页切片（明细分页：先排序 charge 行再展开当前页，禁止全窗明细物化）。
     *
     * @param sortedRows 已按开立时间 DESC 排好序的 charge 行
     * @param query      分页参数
     * @return 当前页 charge 行切片；total 为全窗条数
     */
    public Page<PractitionerWorkloadChargeRowQueryModel> slicePractitionerWorkloadChargeRows(
        List<PractitionerWorkloadChargeRowQueryModel> sortedRows,
        PractitionerWorkloadPageQuery query) {
        if (sortedRows == null) {
            sortedRows = Collections.emptyList();
        }
        int pageNo = this.resolvePractitionerWorkloadPageNo(query);
        int pageSize = this.resolvePractitionerWorkloadPageSize(query);
        long total = sortedRows.size();
        int fromIndex = pageNo * pageSize;
        if (fromIndex >= sortedRows.size()) {
            return new PageImpl<>(Collections.emptyList(), PageRequest.of(pageNo, pageSize), total);
        }
        int toIndex = Math.min(fromIndex + pageSize, sortedRows.size());
        return new PageImpl<>(sortedRows.subList(fromIndex, toIndex), PageRequest.of(pageNo, pageSize), total);
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
        return this.listPractitionerWorkloadChargeRows(query, registrationOnlyMode, null);
    }

    /**
     * 在已预加载的 chargeItem 映射上过滤工作量候选行（与 {@link #listPractitionerWorkloadScopeChargeRows} 同口径）。
     *
     * @param query                须含 payment 链接与 scope
     * @param registrationOnlyMode {@code true} 挂号净笔数分支；{@code false} fin_type/明细分支
     * @param chargeItemById       payment 快照内已加载的 chargeItem；null 时本方法自行加载
     * @return 过滤后的候选行
     */
    public List<PractitionerWorkloadChargeRowQueryModel> listPractitionerWorkloadScopeChargeRows(
        PractitionerWorkloadPageQuery query,
        boolean registrationOnlyMode,
        Map<Long, ChargeItem> chargeItemById) {
        return this.listPractitionerWorkloadChargeRows(query, registrationOnlyMode, chargeItemById);
    }

    /**
     * 按 payment 快照主键批量加载 chargeItem（IF-02 汇总路径一次加载、多处复用）。
     *
     * @param query 须含 {@link PractitionerWorkloadPageQuery#getResolvedPaymentChargeItemLinks()}
     * @return chargeItemId → 实体；无命中时返回空 map
     */
    public Map<Long, ChargeItem> loadPractitionerWorkloadChargeItemMap(PractitionerWorkloadPageQuery query) {
        if (query == null) {
            return Collections.emptyMap();
        }
        List<OutpatientFeeDetailChargePaymentLinkQueryModel> paymentLinks = query.getResolvedPaymentChargeItemLinks();
        if (paymentLinks == null || paymentLinks.isEmpty()) {
            return Collections.emptyMap();
        }
        LinkedHashSet<Long> chargeItemIds = new LinkedHashSet<>();
        for (OutpatientFeeDetailChargePaymentLinkQueryModel link : paymentLinks) {
            if (link != null && link.getChargeItemId() != null) {
                chargeItemIds.add(link.getChargeItemId());
            }
        }
        if (chargeItemIds.isEmpty()) {
            return Collections.emptyMap();
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
     * 汇总工作量外显指标：接诊人数、处方/诊疗开立量。
     *
     * <p>处方数量仅统计药品请求锚点行，按 {@code ChargeItem.prescription_no} 去重（同方多药多行计 1 张）。
     * 调用方须先将缺号收费行回落 {@code MedicationRequest.prescription_no}（应用层 enrich）；
     * 仍无号时才回落 {@code service_id}，避免漏计未分方行（BR-RPT-WKL-019）。</p>
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
        LinkedHashSet<String> prescriptionKeys = new LinkedHashSet<>();
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
            if (TableNameConstants.MED_MEDICATION_REQUEST.equals(serviceTable)) {
                // 一张处方号 = 一张处方；多味药/多行共享同一 prescription_no
                String prescriptionKey = this.resolvePractitionerWorkloadPrescriptionKey(chargeItem);
                if (prescriptionKey != null) {
                    prescriptionKeys.add(prescriptionKey);
                }
            } else if (TableNameConstants.WKF_SERVICE_REQUEST.equals(serviceTable)
                || TableNameConstants.WKF_DEVICE_REQUEST.equals(serviceTable)) {
                String requestKey = serviceTable + ":" + chargeItem.getServiceId();
                activityRequestKeys.add(requestKey);
            }
        }
        summary.setEncounterCountTotal(encounterIds.size());
        summary.setPrescriptionCountTotal(prescriptionKeys.size());
        summary.setActivityItemCountTotal(activityRequestKeys.size());
        return summary;
    }

    /**
     * 在 payment 预解析 scope 内加载并过滤工作量候选 chargeItem 行（仅本聚合 Repository）。
     *
     * @param query                须含 {@link PractitionerWorkloadPageQuery#getResolvedPaymentChargeItemLinks()}
     * @param registrationOnlyMode {@code true} 挂号净笔数分支；{@code false} fin_type/明细分支
     * @param preloadedChargeItemById 已加载的 chargeItem；null 时本方法自行按 payment 快照加载
     * @return 过滤后的候选行；scope 为空时返回空列表
     */
    private List<PractitionerWorkloadChargeRowQueryModel> listPractitionerWorkloadChargeRows(
        PractitionerWorkloadPageQuery query,
        boolean registrationOnlyMode,
        Map<Long, ChargeItem> preloadedChargeItemById) {
        if (query == null) {
            return Collections.emptyList();
        }
        List<OutpatientFeeDetailChargePaymentLinkQueryModel> paymentLinks = query.getResolvedPaymentChargeItemLinks();
        if (paymentLinks == null || paymentLinks.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, ChargeItem> chargeItemById = preloadedChargeItemById;
        // Pre：调用方未预载时按 payment 快照主键一次加载（禁止在汇总路径重复打库）
        if (chargeItemById == null) {
            chargeItemById = this.loadPractitionerWorkloadChargeItemMap(query);
        }
        if (chargeItemById.isEmpty()) {
            return Collections.emptyList();
        }
        // Pre：HashSet 收窄，避免对每条 payment 链路线性扫就诊/科室列表
        Set<Long> encounterScopeSet = this.toIdSet(query.getResolvedEncounterScope());
        Set<Long> organizationIdSet = this.toIdSet(query.getResolvedOrganizationIds());
        // Pre：仅 SETTLED（DD/费用明细净口径；退费不入统计，禁止再扫 REFUNDED）
        String settledStatus = ChargeItemStatus.SETTLED.getCode();
        List<PractitionerWorkloadChargeRowQueryModel> rows = new ArrayList<>();
        // 按合成链路展开；一行一已结账单（与费用明细同构）
        for (OutpatientFeeDetailChargePaymentLinkQueryModel link : paymentLinks) {
            if (link == null || link.getChargeItemId() == null || link.getPaymentContext() == null) {
                continue;
            }
            ChargeItem chargeItem = chargeItemById.get(link.getChargeItemId());
            if (chargeItem == null) {
                continue;
            }
            // Pre：非已结行跳过（退费态不入工作量）
            if (!settledStatus.equals(chargeItem.getStatusEnum())) {
                continue;
            }
            if (!this.matchesPractitionerWorkloadEncounterScope(chargeItem, encounterScopeSet)) {
                continue;
            }
            if (registrationOnlyMode) {
                // 挂号分支：科室收窄用 Encounter.organization_id（方案 C / BR-RPT-WKL-004）
                if (!this.matchesPractitionerWorkloadRegistrationEncounterOrgScope(query, chargeItem, organizationIdSet)) {
                    continue;
                }
            } else if (!this.matchesPractitionerWorkloadRequestingOrgScope(chargeItem, organizationIdSet)) {
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
     * 将 id 列表转为 HashSet；空或 null 返回空集合。
     *
     * @param ids 原始 id 列表
     * @return 去重后的 id 集合
     */
    private Set<Long> toIdSet(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptySet();
        }
        LinkedHashSet<Long> idSet = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id != null) {
                idSet.add(id);
            }
        }
        return idSet;
    }

    /**
     * 就诊 scope 收窄：chargeItem.encounter_id 须在 Support 预解析的就诊集合内（BR-RPT-WKL-003）。
     */
    private boolean matchesPractitionerWorkloadEncounterScope(ChargeItem chargeItem, Set<Long> resolvedEncounterScope) {
        if (resolvedEncounterScope == null || resolvedEncounterScope.isEmpty()) {
            return false;
        }
        if (chargeItem.getEncounterId() == null) {
            return false;
        }
        return resolvedEncounterScope.contains(chargeItem.getEncounterId());
    }

    /**
     * 费用/医嘱科室收窄：{@code requesting_org_id ∈ resolvedOrganizationIds}（BR-RPT-WKL-004）。
     */
    private boolean matchesPractitionerWorkloadRequestingOrgScope(
        ChargeItem chargeItem,
        Set<Long> resolvedOrganizationIds) {
        if (resolvedOrganizationIds == null || resolvedOrganizationIds.isEmpty()) {
            return false;
        }
        if (chargeItem.getRequestingOrgId() == null) {
            return false;
        }
        return resolvedOrganizationIds.contains(chargeItem.getRequestingOrgId());
    }

    /**
     * 挂号科室收窄：{@code Encounter.organization_id ∈ resolvedOrganizationIds}（方案 C）。
     */
    private boolean matchesPractitionerWorkloadRegistrationEncounterOrgScope(
        PractitionerWorkloadPageQuery query,
        ChargeItem chargeItem,
        Set<Long> resolvedOrganizationIds) {
        PractitionerWorkloadEncounterContextQueryModel encounterContext =
            this.resolvePractitionerWorkloadEncounterContext(query, chargeItem.getEncounterId());
        if (encounterContext == null || encounterContext.getOrganizationId() == null) {
            return false;
        }
        if (resolvedOrganizationIds == null || resolvedOrganizationIds.isEmpty()) {
            return false;
        }
        return resolvedOrganizationIds.contains(encounterContext.getOrganizationId());
    }

    /**
     * 选医生时费用 chargeItem 须落在应用层预解析的 Request.requester_id 命中集合（BR-RPT-WKL-005）。
     *
     * @param chargeItem 费用行
     * @param query      须已写入 {@link PractitionerWorkloadPageQuery#getOrderingDoctorChargeItemScope()}
     * @return 是否命中开单医生 scope
     */
    private boolean matchesPractitionerWorkloadOrderingDoctorScope(
        ChargeItem chargeItem,
        PractitionerWorkloadPageQuery query) {
        Set<Long> orderingScope = query.getOrderingDoctorChargeItemScope();
        if (orderingScope == null || orderingScope.isEmpty()) {
            return false;
        }
        if (chargeItem.getId() == null) {
            return false;
        }
        return orderingScope.contains(chargeItem.getId());
    }

    /**
     * 医生收窄总入口：未选医生放行；挂号走 REGISTRATION_DOCTOR，费用走 Request.requester_id 预筛集合（BR-RPT-WKL-005）。
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
        return this.matchesPractitionerWorkloadOrderingDoctorScope(chargeItem, query);
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
     * 挂号净人次符号：与 AS-RPT-007 {@code resolveOutpatientRegistrationNetDelta} 同口径。
     *
     * <p>有效挂号 +1；{@code CANCELLED}/{@code PLANNED}/{@code ENTERED_IN_ERROR} 不参与净汇总（退号从挂出总量中自然冲减）。</p>
     *
     * @param statusEnum 就诊状态存储码
     * @return +1 计入净人次；0 不计入
     */
    private int resolvePractitionerWorkloadRegistrationNetDelta(String statusEnum) {
        if (StringUtils.isEmpty(statusEnum)) {
            return 0;
        }
        if (EncounterStatus.CANCELLED.getCode().equals(statusEnum)
            || EncounterStatus.PLANNED.getCode().equals(statusEnum)
            || EncounterStatus.ENTERED_IN_ERROR.getCode().equals(statusEnum)) {
            return 0;
        }
        return 1;
    }

    /**
     * 解析处方去重键：优先非空 {@code ChargeItem.prescription_no}（通常已由应用层从药品请求回填）；
     * 否则回落药品申请主键（仅未分方极端行）。
     *
     * @param chargeItem 药品收费行
     * @return 去重键；无法识别时 {@code null}
     */
    private String resolvePractitionerWorkloadPrescriptionKey(ChargeItem chargeItem) {
        if (chargeItem == null) {
            return null;
        }
        if (StringUtils.isNotEmpty(chargeItem.getPrescriptionNo())) {
            return "RX:" + chargeItem.getPrescriptionNo().trim();
        }
        if (chargeItem.getServiceId() != null) {
            return "REQ:" + chargeItem.getServiceId();
        }
        return null;
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

    /**
     * 判断未软删收费行是否曾锚定给定定价定义主键集合（任意状态含已退费；BR-WHS-IN-018）。
     *
     * <p>定义主键过多时按 {@link GeneralSharedConstants#ID_IN_QUERY_CHUNK_SIZE} 分批 exists，命中即短路。</p>
     *
     * @param definitionIds 定价定义主键集合
     * @return 存在任一未软删收费行则为 true
     */
    public boolean existsByDefinitionIdIn(Collection<Long> definitionIds) {
        List<Long> idList = IdInQueryChunkSupport.distinctNonNullIds(definitionIds);
        // Gate：无定义主键视为从未挂费
        if (idList.isEmpty()) {
            return false;
        }
        int chunkSize = GeneralSharedConstants.ID_IN_QUERY_CHUNK_SIZE;
        for (int fromIndex = 0; fromIndex < idList.size(); fromIndex += chunkSize) {
            int toIndex = Math.min(fromIndex + chunkSize, idList.size());
            List<Long> chunk = idList.subList(fromIndex, toIndex);
            // 任一批命中即拒绝同批号改价
            if (repository.existsByDefinitionIdInAndIsDeletedFalse(chunk)) {
                return true;
            }
        }
        return false;
    }
}
