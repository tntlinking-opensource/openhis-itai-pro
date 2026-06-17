/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.administration.chargeitem.service;

import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.query.OutpatientChargeQueueEncounterQuery;
import com.openhis.health.domain.administration.chargeitem.repository.ChargeItemRepository;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 收费明细读侧查询服务（DM-ADM-011 §6.2）。
 *
 * <p>职责：提供多种维度的收费明细只读查询能力，用于写入前校验、UI 回显与费用汇总。</p>
 *
 * <p>边界：只读，不写库；复杂联表或大数据量场景优先走读模型（BR-CHG-QRY-002）。</p>
 */
@Service
@RequiredArgsConstructor
public class ChargeItemQueryService {

    private final ChargeItemRepository repository;
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
     * @param cmd 查询命令（serviceIds 必填）
     * @return 收费明细列表
     */
    public List<ChargeItem> listChargeItemsByServiceIds(ChargeItemQueryCommand cmd) {
        return repository.findAllByServiceIdIn(cmd.getServiceIds());
    }

    /**
     * 按执行记录主键集合批量查询收费明细（取消执行 Gate 与回退定位，DM-ADM-011 §6.2.23）。
     *
     * @param procedureIds 执行记录主键集合
     * @return 收费明细列表；空入参返回空列表
     */
    public List<ChargeItem> listChargeItemsByProcedureIds(Collection<Long> procedureIds) {
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
        return repository.findAllByProcedureIdIn(distinctIds);
    }

    /**
     * 住院展示余额：单就诊待结算费用合计（预交金净值 − 本值 = accountBalance）。
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
     * 住院展示余额：单就诊已结算费用合计（预交金占用额组成部分）。
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
     * 住院展示余额：批量按就诊汇总已结算费用（与待结算合计合并参与 accountBalance 计算）。
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
     * 住院展示余额：单就诊待结算 + 已结算费用占用合计。
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
        if (StringUtils.isBlank(statusEnum)) {
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
                cb.equal(root.get(FieldNameConstants.STATUS_ENUM), statusEnum)));
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
            if (StringUtils.isNotBlank(status)) {
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
                root.get(FieldNameConstants.STATUS_ENUM).in(distinctStatuses)));
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
        if (this.useEncounterScopedChargeItemSpecification(cmd)) {
            return repository.findAll(
                this.encounterScopedChargeItemSpecification(cmd),
                Sort.by(Sort.Order.desc(FieldNameConstants.OCCURRENCE_TIME)));
        }
        return repository.findAllByEncounterId(cmd.getEncounterId());
    }

    /**
     * 半退保留链：按源已结算行主键批量解析再生 {@code PLANNED} 行（BR-REF-020）。
     *
     * @param encounterId           就诊标识（收敛同就诊）
     * @param sourceChargeItemIds   源 {@code SETTLED} 行主键集合
     * @return 源行主键 → 再生行主键；无命中时返回空 Map
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
        List<ChargeItem> regenerationRows = repository.findAllByRefundIdIn(distinctSources);
        Map<Long, Long> sourceToNew = new HashMap<>();
        for (ChargeItem regenerationRow : regenerationRows) {
            if (!encounterId.equals(regenerationRow.getEncounterId())) {
                continue;
            }
            if (!ChargeItemStatus.PLANNED.getCode().equals(regenerationRow.getStatusEnum())) {
                continue;
            }
            if (regenerationRow.getRefundId() == null) {
                continue;
            }
            sourceToNew.put(regenerationRow.getRefundId(), regenerationRow.getId());
        }
        return sourceToNew;
    }

    /**
     * 读侧过滤：排除已被半退再生替代的源 {@code SETTLED} 行（IF-DRA-01 / 提交前校验）。
     *
     * @param encounterId 就诊标识
     * @param rows        已装载收费行
     * @return 过滤后的列表；入参为空时原样返回
     */
    public List<ChargeItem> filterOutSupersededSettledLines(Long encounterId, List<ChargeItem> rows) {
        if (rows == null || rows.isEmpty()) {
            return rows;
        }
        LinkedHashSet<Long> settledIds = new LinkedHashSet<>();
        for (ChargeItem row : rows) {
            if (ChargeItemStatus.SETTLED.getCode().equals(row.getStatusEnum())) {
                settledIds.add(row.getId());
            }
        }
        if (settledIds.isEmpty()) {
            return rows;
        }
        Map<Long, Long> supersededSources = this.mapPreservedNewChargeItemIdBySourceId(encounterId, settledIds);
        if (supersededSources.isEmpty()) {
            return rows;
        }
        List<ChargeItem> kept = new ArrayList<>();
        for (ChargeItem row : rows) {
            if (supersededSources.containsKey(row.getId())) {
                continue;
            }
            kept.add(row);
        }
        return kept;
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
        return repository.findAllByEncounterIdIn(encounterIds);
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
        if (this.useEncounterScopedChargeItemSpecification(cmd)) {
            return repository.findAll(
                this.encounterScopedChargeItemSpecification(cmd),
                Sort.by(Sort.Order.desc(FieldNameConstants.OCCURRENCE_TIME)));
        }
        return repository.findAllByEncounterId(cmd.getEncounterId());
    }

    /**
     * 住院费用明细视图查询（DM-ADM-011 §6.2.4；住院场景）。
     *
     * @param cmd 查询命令（encounterId 必填；contextEnum 建议传入住院上下文值）
     * @return 收费明细列表
     */
    public List<ChargeItem> queryInpatientCostDetails(ChargeItemQueryCommand cmd) {
        // 住院费用视图：建议传入住院上下文枚举值以收敛结果；未传则退化为就诊全量明细。
        if (cmd.getContextEnum() != null) {
            return repository.findAllByEncounterIdAndContextEnum(cmd.getEncounterId(), cmd.getContextEnum());
        }
        return repository.findAllByEncounterId(cmd.getEncounterId());
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
            predicates.add(this.notSupersededSettledChargeItemPredicate(root, query, cb));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return repository.findAll(spec);
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
                if (cmd.getStatusEnum() != null && StringUtils.isNotBlank(cmd.getStatusEnum().trim())) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), cmd.getStatusEnum().trim()));
                }
            }
            if (cmd.getContextEnum() != null && StringUtils.isNotBlank(cmd.getContextEnum().trim())) {
                predicates.add(cb.equal(root.get(FieldNameConstants.CONTEXT_ENUM), cmd.getContextEnum().trim()));
            }
            if (Boolean.TRUE.equals(cmd.getExcludeSupersededSettledLines())) {
                predicates.add(this.notSupersededSettledChargeItemPredicate(root, query, cb));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 排除「同就诊已存在 PLANNED 再生行且 refund_id 指向本行」的源 SETTLED 行（BR-REF-020）。
     *
     * @param root  收费明细根
     * @param query 外层查询
     * @param cb    条件构造器
     * @return 谓词：非被替代 SETTLED，或非 SETTLED 行均保留
     */
    private Predicate notSupersededSettledChargeItemPredicate(
        Root<ChargeItem> root,
        CriteriaQuery<?> query,
        CriteriaBuilder cb
    ) {
        Subquery<Integer> replacementExists = query.subquery(Integer.class);
        Root<ChargeItem> regenerationRow = replacementExists.from(ChargeItem.class);
        replacementExists.select(cb.literal(1));
        replacementExists.where(
            cb.and(
                cb.equal(regenerationRow.get(FieldNameConstants.ENCOUNTER_ID), root.get(FieldNameConstants.ENCOUNTER_ID)),
                cb.equal(regenerationRow.get(FieldNameConstants.STATUS_ENUM), ChargeItemStatus.PLANNED.getCode()),
                cb.equal(regenerationRow.get(FieldNameConstants.REFUND_ID), root.get(FieldNameConstants.ID))
            )
        );
        Predicate isSettled = cb.equal(root.get(FieldNameConstants.STATUS_ENUM), ChargeItemStatus.SETTLED.getCode());
        Predicate isSupersededSettled = cb.and(isSettled, cb.exists(replacementExists));
        return cb.not(isSupersededSettled);
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
        if (cmd.getContextEnum() != null && StringUtils.isNotBlank(cmd.getContextEnum().trim())) {
            return true;
        }
        if (cmd.getStatusEnum() != null && StringUtils.isNotBlank(cmd.getStatusEnum().trim())) {
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
            if (code != null && StringUtils.isNotBlank(code.trim())) {
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

    private Predicate buildEncounterScopePredicate(
        Root<ChargeItem> ci,
        CriteriaBuilder cb,
        List<Long> resolvedEncounterScope) {
        if (resolvedEncounterScope == null || resolvedEncounterScope.isEmpty()) {
            return cb.equal(cb.literal(1), cb.literal(0));
        }
        return ci.get(FieldNameConstants.ENCOUNTER_ID).in(resolvedEncounterScope);
    }
}
