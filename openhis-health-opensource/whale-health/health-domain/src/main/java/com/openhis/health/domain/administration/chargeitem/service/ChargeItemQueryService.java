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
import com.openhis.health.domain.administration.chargeitem.query.OutpatientFeeDetailAmountSummaryQueryModel;
import com.openhis.health.domain.administration.chargeitem.query.OutpatientFeeDetailChargeRowQueryModel;
import com.openhis.health.domain.administration.chargeitem.query.OutpatientFeeDetailEncounterBlockQueryModel;
import com.openhis.health.domain.administration.chargeitem.query.OutpatientFeeDetailPageQuery;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.OutpatientFeeDetailChargePaymentLinkQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.OutpatientFeeDetailPaymentContextQueryModel;
import com.openhis.health.domain.share.constants.OutpatientFeeDetailConstants;
import com.openhis.health.domain.administration.chargeitem.repository.ChargeItemRepository;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.GenerateSource;
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
        if (encounterId == null || StringUtils.isBlank(statusEnum)) {
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
     * 门诊费用明细：补全 payment {@code charge_item_ids} 快照截断漏挂的已结/已退行（DM-ADM-011 §6.2.25 扩展；AS-OP-FIN-005）。
     *
     * <p>仅读本聚合 {@code adm_charge_item}；收款人匹配 {@code performer_id}（与 BR-OPC-016 结算写入口径一致）。</p>
     *
     * @param encounterIds         bill_date 筛选命中 payment 涉及的就诊主键
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
                // 排除快照已解析主键，仅补全截断漏挂行
                predicates.add(cb.not(root.get(FieldNameConstants.ID).in(excludedChargeItemIds)));
            }
            if (entererMatchIds != null && !entererMatchIds.isEmpty()) {
                predicates.add(root.get(FieldNameConstants.PERFORMER_ID).in(entererMatchIds));
            } else if (cashierId != null) {
                predicates.add(cb.equal(root.get(FieldNameConstants.PERFORMER_ID), cashierId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return repository.findAll(specification);
    }

    /**
     * 门诊费用明细：按就诊块分页返回候选行（DM-ADM-011 §6.2.25；AS-OP-FIN-005 IF-OP-FDT-02）。
     *
     * <p>须先经 {@link com.openhis.health.module.querysupport.feedetail.OutpatientFeeDetailQuerySupport}
     * 写入 {@link OutpatientFeeDetailPageQuery#getResolvedChargeItemScope()} 与 payment 上下文。</p>
     *
     * @param query 须含 resolved scope 与 payment 上下文
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
        // 按就诊分组，块内/块间排序均依赖 payment bill_date（BR-OP-FDT-014）
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
     * <p>域层仅汇总账单事实列；退单负号与医保/自费分摊由应用层 Assembler 叠加（**BR-OP-FDT-010～012**）。</p>
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
                summary.setDiscountAmountTotal(summary.getDiscountAmountTotal().add(discountGap));
            }
        }
        return summary;
    }

    /**
     * 在 payment 预解析 scope 内加载并过滤门诊费用明细候选行（仅本聚合 Repository）。
     *
     * @param query 须含 {@link OutpatientFeeDetailPageQuery#getResolvedChargeItemScope()}
     * @return 过滤后的候选行；scope 为空时返回空列表
     */
    private List<OutpatientFeeDetailChargeRowQueryModel> listOutpatientFeeDetailChargeRows(
        OutpatientFeeDetailPageQuery query) {
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
        this.enrichOutpatientFeeDetailPaymentSnapshotDenominators(chargeItemById, paymentLinks);
        List<String> allowedStatuses = List.of(
            ChargeItemStatus.SETTLED.getCode(),
            ChargeItemStatus.REFUNDED.getCode());
        List<OutpatientFeeDetailChargeRowQueryModel> rows = new ArrayList<>();
        // 按 payment 快照关联逐条展开明细行，同 chargeItem 可收退各一行（BR-OP-FDT-012）
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
                // service_table 无法归类时在 resolve 阶段抛错（BR-OP-FDT-006）
                continue;
            }
            rows.add(new OutpatientFeeDetailChargeRowQueryModel(chargeItem, link.getPaymentContext()));
        }
        return rows;
    }

    /**
     * 按 payment 快照补全各关联行的 {@code paymentSnapshotAbsAmountTotal} 分母（医保占比用）。
     */
    private void enrichOutpatientFeeDetailPaymentSnapshotDenominators(
        Map<Long, ChargeItem> chargeItemById,
        List<OutpatientFeeDetailChargePaymentLinkQueryModel> paymentLinks) {
        Map<Long, BigDecimal> absTotalByPaymentId = new HashMap<>();
        // 第一遍：按 payment 聚合快照内 |total_price| 之和
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
        // 第二遍：写回各 payment 快照关联的分母
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

    /** 取就诊块内最新 payment {@code bill_date}，供块间倒序排序。 */
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

    /** 就诊 scope 收窄：{@code resolvedEncounterScope} 为空时不额外过滤。 */
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

    /** 费用大类多选（BR-OP-FDT-006）；未传大类时不过滤。 */
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
     * {@code service_table} → 费用大类 code（BR-OP-FDT-006）；无法识别时抛错。
     */
    private String resolveOutpatientFeeDetailFeeCategoryCode(String serviceTable) {
        if (StringUtils.isBlank(serviceTable)) {
            throw new IllegalArgumentException("charge item service_table is blank");
        }
        String normalized = serviceTable.trim();
        if (TableNameConstants.MED_MEDICATION_REQUEST.equals(normalized)
            || TableNameConstants.MED_MEDICATION_DISPENSE.equals(normalized)) {
            // 药品：医嘱/发药锚点
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
        // 未识别 service_table 整单失败（BR-OP-FDT-006 / EX-OP-FDT-103）
        throw new IllegalArgumentException("unsupported charge item service_table for outpatient fee detail: " + normalized);
    }

    /** 应收金额：优先 discount_amount，缺失回退 total_price（BR-CHG-QRY-003）。 */
    private BigDecimal resolveOutpatientFeeDetailReceivableAmount(ChargeItem chargeItem) {
        if (chargeItem.getDiscountAmount() != null) {
            return chargeItem.getDiscountAmount();
        }
        return this.nullToZero(chargeItem.getTotalPrice());
    }

    /** 折后金额：system_discount_price → discount_amount → total_price 回退链。 */
    private BigDecimal resolveOutpatientFeeDetailDiscountedAmount(ChargeItem chargeItem) {
        if (chargeItem.getSystemDiscountPrice() != null) {
            return chargeItem.getSystemDiscountPrice();
        }
        if (chargeItem.getDiscountAmount() != null) {
            return chargeItem.getDiscountAmount();
        }
        return this.nullToZero(chargeItem.getTotalPrice());
    }

    private BigDecimal nullToZero(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value;
    }
}
