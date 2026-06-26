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

package com.openhis.health.domain.financial.paymentreconciliation.service;

import com.openhis.health.domain.financial.paymentreconciliation.entity.PaymentRecDetail;
import com.openhis.health.domain.financial.paymentreconciliation.entity.PaymentRecStatic;
import com.openhis.health.domain.financial.paymentreconciliation.entity.PaymentReconciliation;
import com.openhis.health.domain.financial.paymentreconciliation.entity.PaymentYbSettlement;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.*;
import com.openhis.health.domain.financial.paymentreconciliation.repository.PaymentRecDetailRepository;
import com.openhis.health.domain.financial.paymentreconciliation.repository.PaymentRecStaticRepository;
import com.openhis.health.domain.financial.paymentreconciliation.repository.PaymentReconciliationRepository;
import com.openhis.health.domain.financial.paymentreconciliation.repository.PaymentYbSettlementRepository;
import com.openhis.health.domain.financial.cashiersettlement.queries.PreviewCashierSettlementQuery;
import com.openhis.health.domain.financial.cashiersettlement.queries.model.PreviewCashierSettlementQueryModel;
import com.openhis.health.domain.financial.cashiersettlement.queries.model.UnsettledPaymentPreviewItemQueryModel;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.financial.paymentreconciliation.support.PaymentYbSettlementSupport;
import com.openhis.health.domain.administration.chargeitem.query.OutpatientFeeDetailPageQuery;
import com.openhis.health.domain.share.constants.OutpatientFeeDetailConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.enums.CashierSettlementType;
import com.openhis.health.domain.share.enums.PaymentKind;
import com.openhis.health.domain.share.enums.PaymentStatus;
import com.openhis.health.domain.share.enums.PaymentType;
import com.openhis.whale.core.application.exception.BusinessException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 支付对账/支付流水聚合读侧查询服务。
 *
 * <p>职责：按就诊、患者、账单日、关联单等维度查询支付事实及明细，并承担退费存在性、冲销汇总等只读编排；
 * 另含收费员交账未挂载 payment 候选预览/计数（{@code previewUnsettledPaymentsForCashierSettlement}，**BR-CS-001～006**，B.6.4）；不写库。</p>
 */
@Service
@RequiredArgsConstructor
public class PaymentReconciliationQueryService {
    private final PaymentReconciliationRepository paymentRepository;
    private final PaymentRecDetailRepository detailRepository;
    private final PaymentRecStaticRepository recStaticRepository;
    private final PaymentYbSettlementRepository ybSettlementRepository;

    /**
     * 按主键强制加载支付流水；不存在则阻断后续退费或打印等动作。
     *
     * @param paymentId 支付流水主键
     * @return 读模型
     * @throws BusinessException 流水不存在
     */
    public PaymentReconciliationQueryModel mustGetPayment(Long paymentId) {
        PaymentReconciliation payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.PAYMENT_RECONCILIATION_NOT_FOUND, "支付流水不存在"));
        List<PaymentYbSettlement> ybRows = ybSettlementRepository
            .findAllByReconciliationIdAndIsDeletedFalseOrderByCreatedAtAsc(paymentId);
        return toQueryModel(payment, ybRows);
    }

    /**
     * 按主键加载支付实体（Port/adapter 读路径）；不存在或已软删时 empty。
     */
    public Optional<PaymentReconciliation> findOptionalPaymentEntityById(Long paymentId) {
        if (paymentId == null) {
            return Optional.empty();
        }
        return paymentRepository.findById(paymentId)
            .filter(row -> !Boolean.TRUE.equals(row.getIsDeleted()));
    }

    /**
     * 按账单日闭区间加载支付实体（adapter 对账读路径）。
     */
    public List<PaymentReconciliation> listPaymentEntitiesByBillDateBetween(LocalDateTime from, LocalDateTime to) {
        return paymentRepository.findByBillDateBetween(from, to);
    }

    /**
     * 列出某次就诊下全部支付流水，用于费用一览或结账前核对。
     *
     * @param encounterId 就诊标识
     * @return 流水读模型列表
     */
    public List<PaymentReconciliationQueryModel> findPaymentsByEncounter(Long encounterId) {
        List<PaymentReconciliation> payments = paymentRepository.findByEncounterId(encounterId);
        List<PaymentReconciliationQueryModel> result = new ArrayList<>();
        for (PaymentReconciliation payment : payments) {
            result.add(toQueryModel(payment));
        }
        this.attachYbSettlements(result);
        return result;
    }

    /**
     * 查询就诊下已成功或退款处理中的流水，用于判断仍存在未完成资金态的订单。
     *
     * @param encounterId 就诊标识
     * @return 过滤后的流水列表
     */
    public List<PaymentReconciliationQueryModel> findPaySuccessOrRefundingByEncounter(Long encounterId) {
        List<PaymentReconciliation> payments = paymentRepository.findByEncounterIdAndStatusEnumIn(
            encounterId,
            List.of(PaymentStatus.SUCCESS, PaymentStatus.REFUND_PROCESSING));
        List<PaymentReconciliationQueryModel> result = new ArrayList<>();
        for (PaymentReconciliation payment : payments) {
            // 仅正向收费单参与「可退/防重」口径；逆向单（relation_id 非空）由 relation 链单独表达
            if (payment.getRelationId() != null) {
                continue;
            }
            result.add(toQueryModel(payment));
        }
        this.attachYbSettlements(result);
        return result;
    }

    /**
     * 查询指定就诊下已成功或退款处理中、且流水挂载的收费项与给定集合存在交集的支付流水。
     *
     * <p>用于 IF-OPC-07 与 {@code BR-OPC-005}：允许同一就诊多轮结算未出现在历史成功链路中的收费项；
     * 仅当正向 {@code SUCCESS}/{@code REFUND_PROCESSING} 流水上的 {@code charge_item_ids} 与本次拟收 ID 有交集时认定防重命中；
     * 逆向单（{@code relation_id} 非空）不参与防重，避免退费受理中的逆向单阻塞半退再结算。</p>
     *
     * @param encounterId   就诊标识
     * @param chargeItemIds 本次正式结算拟收的收费项主键集合；null 或空时返回空列表（调用方应已做非空门禁）
     * @return 命中流水读模型列表；无交集时为空
     */
    public List<PaymentReconciliationQueryModel> findPaySuccessOrRefundingOverlappingChargeItems(
        Long encounterId,
        Collection<Long> chargeItemIds) {
        List<PaymentReconciliationQueryModel> result = new ArrayList<>();
        if (encounterId == null || chargeItemIds == null || chargeItemIds.isEmpty()) {
            return result;
        }
        // 将拟收 ID 压入 HashSet，便于与每条流水上的挂载串做 O(1) 包含判断。
        Set<Long> wanted = new HashSet<>();
        for (Long id : chargeItemIds) {
            if (id != null) {
                wanted.add(id);
            }
        }
        if (wanted.isEmpty()) {
            return result;
        }
        List<PaymentReconciliation> payments = paymentRepository.findByEncounterIdAndStatusEnumIn(
            encounterId,
            List.of(PaymentStatus.SUCCESS, PaymentStatus.REFUND_PROCESSING));
        for (PaymentReconciliation payment : payments) {
            if (payment.getRelationId() != null) {
                continue;
            }
            List<Long> boundIds = this.parseIds(payment.getChargeItemIds());
            boolean overlaps = false;
            for (Long boundId : boundIds) {
                if (wanted.contains(boundId)) {
                    overlaps = true;
                    break;
                }
            }
            if (overlaps) {
                result.add(this.toQueryModel(payment));
            }
        }
        this.attachYbSettlements(result);
        return result;
    }

    /**
     * 批量解析就诊下仍处于草稿态的支付流水主键，供挂号列表「待挂号」行发起结算。
     *
     * <p>同一就诊若存在多条 DRAFT（异常数据），取主键较大者作为当前可结算草稿，避免前端拿到过期草稿。</p>
     *
     * @param encounterIds 就诊主键集合；空集合时返回空 Map
     * @return 就诊主键 → 支付草稿（流水）主键
     */
    public Map<Long, Long> findDraftPaymentIdsByEncounterIds(Collection<Long> encounterIds) {
        Map<Long, Long> result = new HashMap<>();
        if (encounterIds == null || encounterIds.isEmpty()) {
            return result;
        }
        List<Long> idList = new ArrayList<>();
        for (Long eid : encounterIds) {
            if (eid != null) {
                idList.add(eid);
            }
        }
        if (idList.isEmpty()) {
            return result;
        }
        List<PaymentReconciliation> rows = paymentRepository.findByEncounterIdInAndStatusEnum(idList, PaymentStatus.DRAFT);
        for (PaymentReconciliation row : rows) {
            Long eid = row.getEncounterId();
            if (eid == null) {
                continue;
            }
            Long pid = row.getId();
            if (pid == null) {
                continue;
            }
            Long existing = result.get(eid);
            if (existing == null) {
                result.put(eid, pid);
                continue;
            }
            if (pid > existing) {
                result.put(eid, pid);
            }
        }
        return result;
    }

    /**
     * 批量解析就诊下最近一次已成功支付的流水主键，供挂号列表补打小票。
     *
     * <p>同一就诊若存在多条 SUCCESS，取主键较大者作为最近一次成功支付。</p>
     *
     * @param encounterIds 就诊主键集合；空集合时返回空 Map
     * @return 就诊主键 → 支付流水主键
     */
    public Map<Long, Long> findLatestSuccessPaymentIdsByEncounterIds(Collection<Long> encounterIds) {
        Map<Long, Long> result = new HashMap<>();
        if (encounterIds == null || encounterIds.isEmpty()) {
            return result;
        }
        List<Long> idList = new ArrayList<>();
        for (Long eid : encounterIds) {
            if (eid != null) {
                idList.add(eid);
            }
        }
        if (idList.isEmpty()) {
            return result;
        }
        List<PaymentReconciliation> rows =
                paymentRepository.findByEncounterIdInAndStatusEnum(idList, PaymentStatus.SUCCESS);
        for (PaymentReconciliation row : rows) {
            Long eid = row.getEncounterId();
            if (eid == null) {
                continue;
            }
            Long pid = row.getId();
            if (pid == null) {
                continue;
            }
            Long existing = result.get(eid);
            if (existing == null || pid > existing) {
                result.put(eid, pid);
            }
        }
        return result;
    }

    /**
     * 判断关联原单是否已产生退款流水，并回填首条匹配流水的标识与状态。
     *
     * @param relationId 业务关联单标识（与原支付关联字段一致）
     * @return 是否存在及命中的退款流水摘要
     */
    public RefundExistenceCheckResult hasRefundByRelationId(Long relationId) {
        RefundExistenceCheckResult result = new RefundExistenceCheckResult();
        result.setExists(Boolean.FALSE);
        List<PaymentReconciliation> payments = paymentRepository.findByRelationId(relationId);
        for (PaymentReconciliation item : payments) {
            // 与入参关联键一致则认定已挂退款侧流水，取首条即可
            if (relationId.equals(item.getRelationId())) {
                result.setExists(Boolean.TRUE);
                result.setRefundPaymentId(item.getId());
                result.setRefundStatusEnum(item.getStatusEnum());
                break;
            }
        }
        return result;
    }

    /**
     * 按关联单查找对应的退款/回冲支付流水读模型；无命中返回 null。
     *
     * @param relationId 业务关联单标识
     * @return 流水读模型或 null
     */
    public PaymentReconciliationQueryModel findReturnPayment(Long relationId) {
        List<PaymentReconciliation> payments = paymentRepository.findByRelationId(relationId);
        for (PaymentReconciliation item : payments) {
            if (relationId.equals(item.getRelationId())) {
                PaymentReconciliationQueryModel model = toQueryModel(item);
                this.attachYbSettlements(List.of(model));
                return model;
            }
        }
        return null;
    }

    /**
     * 按账单日闭区间拉取流水，用于日结或报表汇总。
     *
     * @param from 起始时刻（含）
     * @param to 结束时刻（含）
     * @return 流水列表
     */
    public List<PaymentReconciliationQueryModel> findPaymentByBillDateRange(LocalDateTime from, LocalDateTime to) {
        List<PaymentReconciliation> payments = paymentRepository.findByBillDateBetween(from, to);
        List<PaymentReconciliationQueryModel> result = new ArrayList<>();
        for (PaymentReconciliation payment : payments) {
            result.add(toQueryModel(payment));
        }
        this.attachYbSettlements(result);
        return result;
    }

    /**
     * 预览收费员交账未挂载候选 payment（BR-CS-001～006；供日结 IF 与写侧复用口径）。
     *
     * @param query 交账类型、经办人与业务日窗
     * @return 候选 items 与条数
     * @throws BusinessException 查询条件非法
     */
    public PreviewCashierSettlementQueryModel previewUnsettledPaymentsForCashierSettlement(PreviewCashierSettlementQuery query) {
        List<PaymentReconciliation> rows = this.findUnsettledPaymentsForCashierSettlement(query);
        PreviewCashierSettlementQueryModel model = new PreviewCashierSettlementQueryModel();
        List<UnsettledPaymentPreviewItemQueryModel> items = new ArrayList<>();
        // 逐条映射为交账预览行（渠道/病区汇总在应用层）
        for (PaymentReconciliation row : rows) {
            items.add(this.toUnsettledPreviewItem(row));
        }
        model.setItems(items);
        // paymentCount = 候选流水条数（含 0 元与收支抵净；BR-CS-011），与 items.size 及提交挂载条数一致
        model.setPaymentCount(items.size());
        return model;
    }

    /**
     * 统计收费员交账未挂载候选 payment 条数。
     *
     * @param query 交账类型、经办人与业务日窗
     * @return 条数
     * @throws BusinessException 查询条件非法
     */
    public Long countUnsettledPaymentsForCashierSettlement(PreviewCashierSettlementQuery query) {
        return (long) this.findUnsettledPaymentsForCashierSettlement(query).size();
    }

    /**
     * 按 BR-CS-001～006 查询未交账 payment 实体（供交账写侧挂载复用口径）。
     *
     * @param query 查询条件
     * @return 未交账 payment 列表，按 billDate 降序
     * @throws BusinessException 查询条件非法
     */
    public List<PaymentReconciliation> findUnsettledPaymentsForCashierSettlement(PreviewCashierSettlementQuery query) {
        this.assertCashierSettlementPreviewQueryValid(query);
        Specification<PaymentReconciliation> specification = this.buildUnsettledForCashierSettlementSpecification(query);
        List<PaymentReconciliation> rows = this.paymentRepository.findAll(specification);
        this.sortByBillDateDesc(rows);
        return rows;
    }

    /**
     * 查询患者名下仍处于草稿态的支付流水，供收银台续办或清理。
     *
     * @param patientId 患者标识
     * @return 草稿流水列表
     */
    public List<PaymentReconciliationQueryModel> findDraftPaymentByPatient(Long patientId) {
        List<PaymentReconciliation> payments = paymentRepository.findByPatientIdAndStatusEnum(
            patientId,
            PaymentStatus.DRAFT);
        List<PaymentReconciliationQueryModel> result = new ArrayList<>();
        for (PaymentReconciliation payment : payments) {
            result.add(toQueryModel(payment));
        }
        this.attachYbSettlements(result);
        return result;
    }

    /**
     * 批量解析各支付单首条明细的渠道码（预交金流水 {@code payMethodText} 展示来源）。
     *
     * @param paymentIds 支付头主键集合
     * @return paymentId → 首条明细 {@code pay_enum}
     */
    public Map<Long, String> mapFirstPayEnumByPaymentIds(Collection<Long> paymentIds) {
        Map<Long, String> payEnumByPaymentId = new HashMap<>();
        if (paymentIds == null || paymentIds.isEmpty()) {
            return payEnumByPaymentId;
        }
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        for (Long paymentId : paymentIds) {
            if (paymentId != null) {
                distinct.add(paymentId);
            }
        }
        if (distinct.isEmpty()) {
            return payEnumByPaymentId;
        }
        List<PaymentRecDetail> details = detailRepository.findAllByReconciliationIdIn(distinct);
        for (PaymentRecDetail detail : details) {
            if (detail == null || detail.getReconciliationId() == null) {
                continue;
            }
            if (payEnumByPaymentId.containsKey(detail.getReconciliationId())) {
                continue;
            }
            if (StringUtils.isNotBlank(detail.getPayEnum())) {
                payEnumByPaymentId.put(detail.getReconciliationId(), detail.getPayEnum().trim());
            }
        }
        return payEnumByPaymentId;
    }

    /**
     * 批量解析各支付单明细上的去重渠道码（退费前判定是否可能银联被扫）。
     *
     * @param paymentIds 支付头主键集合
     * @return paymentId → 去重后的 {@code pay_enum} 列表（保持插入顺序）
     */
    public Map<Long, List<String>> mapDistinctPayEnumCodesByPaymentIds(Collection<Long> paymentIds) {
        Map<Long, LinkedHashSet<String>> codesByPaymentId = new LinkedHashMap<>();
        if (paymentIds == null || paymentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        for (Long paymentId : paymentIds) {
            if (paymentId != null) {
                distinct.add(paymentId);
            }
        }
        if (distinct.isEmpty()) {
            return Collections.emptyMap();
        }
        List<PaymentRecDetail> details = detailRepository.findAllByReconciliationIdIn(distinct);
        for (PaymentRecDetail detail : details) {
            if (detail == null || detail.getReconciliationId() == null) {
                continue;
            }
            if (StringUtils.isBlank(detail.getPayEnum())) {
                continue;
            }
            codesByPaymentId
                .computeIfAbsent(detail.getReconciliationId(), ignored -> new LinkedHashSet<>())
                .add(detail.getPayEnum().trim());
        }
        Map<Long, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<Long, LinkedHashSet<String>> entry : codesByPaymentId.entrySet()) {
            result.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return result;
    }

    /**
     * 列出指定支付流水下的渠道明细（多账户、多支付介质时逐条展开）。
     *
     * @param paymentId 支付流水主键
     * @return 明细读模型列表
     */
    public List<PaymentRecDetailQueryModel> listPaymentRecDetailsByPaymentId(Long paymentId) {
        List<PaymentRecDetail> details = detailRepository.findByReconciliationId(paymentId);
        List<PaymentRecDetailQueryModel> result = new ArrayList<>();
        for (PaymentRecDetail detail : details) {
            PaymentRecDetailQueryModel item = new PaymentRecDetailQueryModel();
            item.setId(detail.getId());
            item.setReconciliationId(detail.getReconciliationId());
            item.setPredecessorId(detail.getPredecessorId());
            item.setTargetEnum(detail.getTargetEnum());
            item.setAccountId(detail.getAccountId());
            item.setAccountCode(detail.getAccountCode());
            item.setPayEnum(detail.getPayEnum());
            item.setPayLevelEnum(detail.getPayLevelEnum());
            item.setAmount(detail.getAmount());
            item.setReturnAmount(detail.getReturnAmount());
            item.setChargeAmount(detail.getChargeAmount());
            item.setPayTransNo(detail.getPayTransNo());
            item.setPayTransText(detail.getPayTransText());
            item.setPayTransDate(detail.getPayTransDate());
            item.setBeforeBalance(detail.getBeforeBalance());
            item.setAfterBalance(detail.getAfterBalance());
            item.setResultEnum(detail.getResultEnum());
            result.add(item);
        }
        return result;
    }

    /**
     * 按支付流水主键批量读取 fin_type 统计快照（DM-FIN-002 §3.3 fin_payment_rec_static）。
     *
     * @param paymentIds 支付流水主键集合
     * @return paymentId → 快照行列表；无数据时返回空 map
     */
    public Map<Long, List<PaymentStaticSnapshotQueryModel>> mapPaymentRecStaticsByReconciliationIds(
        Collection<Long> paymentIds) {
        Map<Long, List<PaymentStaticSnapshotQueryModel>> result = new LinkedHashMap<>();
        if (paymentIds == null || paymentIds.isEmpty()) {
            return result;
        }
        List<PaymentRecStatic> staticRows = this.recStaticRepository.findAllByReconciliationIdInAndIsDeletedFalse(paymentIds);
        for (PaymentRecStatic staticRow : staticRows) {
            if (staticRow == null || staticRow.getReconciliationId() == null) {
                continue;
            }
            PaymentStaticSnapshotQueryModel model = new PaymentStaticSnapshotQueryModel();
            model.setId(staticRow.getId());
            model.setReconciliationId(staticRow.getReconciliationId());
            model.setTypeCode(staticRow.getTypeCode());
            model.setTypeName(staticRow.getTypeName());
            model.setAmount(staticRow.getAmount());
            model.setChargeItemIds(staticRow.getChargeItemIds());
            List<PaymentStaticSnapshotQueryModel> bucket = result.get(staticRow.getReconciliationId());
            if (bucket == null) {
                bucket = new ArrayList<>();
                result.put(staticRow.getReconciliationId(), bucket);
            }
            bucket.add(model);
        }
        return result;
    }

    /**
     * 批量解析支付流水上挂载的费用项主键串，供退费或医保冲账按项处理。
     *
     * @param paymentIds 支付流水主键集合
     * @return 每条流水对应的解析结果（含解析时刻与费用项 id 列表）
     */
    public List<ChargeItemIdsCollectionQueryModel> getChargeItemIdsByPayments(List<Long> paymentIds) {
        List<ChargeItemIdsCollectionQueryModel> result = new ArrayList<>();
        List<PaymentReconciliation> payments = paymentRepository.findByIdIn(paymentIds);
        for (PaymentReconciliation payment : payments) {
            ChargeItemIdsCollectionQueryModel model = new ChargeItemIdsCollectionQueryModel();
            model.setPaymentId(payment.getId());
            model.setParsedAt(LocalDateTime.now());
            model.setChargeItemIds(this.parseIds(payment.getChargeItemIds()));
            result.add(model);
        }
        return result;
    }

    /**
     * 按账单日区间拉取流水并展开为「操作员 + 费用性质」维度的粗粒度汇总行（每支付一行，未做 SQL 聚合）。
     *
     * @param from 起始账单日（含）
     * @param to 结束账单日（含）
     * @return 汇总行列表
     */
    public List<PaymentSummaryQueryModel> queryPaymentAccountContract(LocalDateTime from, LocalDateTime to) {
        List<PaymentReconciliation> payments = paymentRepository.findByBillDateBetween(from, to);
        List<PaymentSummaryQueryModel> result = new ArrayList<>();
        for (PaymentReconciliation payment : payments) {
            PaymentSummaryQueryModel model = new PaymentSummaryQueryModel();
            model.setEntererId(payment.getEntererId());
            model.setContractNo(payment.getContractNo());
            model.setTotalAmount(payment.getTenderedAmount());
            model.setPaymentCount(1L);
            // 成功态计入成功笔数，其它状态记 0，便于下游透视
            if (PaymentStatus.SUCCESS.equals(payment.getStatusEnum())) {
                model.setSuccessCount(1L);
            } else {
                model.setSuccessCount(0L);
            }
            // 全额或部分退款态计入退款笔数
            if (PaymentStatus.REFUNDED.equals(payment.getStatusEnum())
                || PaymentStatus.PARTIAL_REFUNDED.equals(payment.getStatusEnum())) {
                model.setRefundCount(1L);
            } else {
                model.setRefundCount(0L);
            }
            model.setBillDateFrom(from);
            model.setBillDateTo(to);
            model.setGroupByField("entererId,contractNo");
            result.add(model);
        }
        return result;
    }

    /**
     * 筛选处于「反结后待重新结算」的就诊（FIN-004；展示态 {@code REVERSED_PENDING_RESETTLE}）。
     *
     * <p>口径：存在已 {@code REFUNDED} 的逆向 {@code INPATIENT_CLINIC} 反结单（{@code relation_id} 非空），
     * 且 {@code SUCCESS} 正向出院结算条数不多于逆向条数（尚未再次结算成功；原正向单保持 {@code SUCCESS}）。</p>
     *
     * @param encounterIds 就诊主键集合
     * @return 命中展示态的 encounterId 集合；无匹配时返回空集
     */
    public Set<Long> filterReversedPendingResettleEncounterIds(Collection<Long> encounterIds) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            return Collections.emptySet();
        }
        LinkedHashSet<Long> distinctIds = new LinkedHashSet<>();
        for (Long id : encounterIds) {
            if (id != null) {
                distinctIds.add(id);
            }
        }
        if (distinctIds.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Long> matched = new LinkedHashSet<>();
        for (Long encounterId : distinctIds) {
            if (this.isReversedPendingResettleEncounter(encounterId)) {
                matched.add(encounterId);
            }
        }
        return matched;
    }

    /**
     * 单就诊是否处于反结后待重新结算展示态。
     *
     * @param encounterId 就诊主键
     * @return true 表示存在已收口逆向反结单且正向 SUCCESS 条数未超过逆向条数
     */
    private boolean isReversedPendingResettleEncounter(Long encounterId) {
        if (encounterId == null) {
            return false;
        }
        List<PaymentReconciliationQueryModel> payments = this.findPaymentsByEncounter(encounterId);
        int forwardSuccessCount = 0;
        int reverseRefundedCount = 0;
        for (PaymentReconciliationQueryModel payment : payments) {
            if (payment == null || !PaymentKind.INPATIENT_CLINIC.equals(payment.getKindEnum())) {
                continue;
            }
            if (payment.getRelationId() != null) {
                if (PaymentStatus.REFUNDED.equals(payment.getStatusEnum())) {
                    reverseRefundedCount = reverseRefundedCount + 1;
                }
                continue;
            }
            if (PaymentStatus.SUCCESS.equals(payment.getStatusEnum())) {
                forwardSuccessCount = forwardSuccessCount + 1;
            }
        }
        if (reverseRefundedCount <= 0) {
            return false;
        }
        // 再次结算成功会新增 SUCCESS 正向单，条数须严格大于逆向条数才脱离待重结态
        return forwardSuccessCount <= reverseRefundedCount;
    }

    /**
     * 支付聚合根到读模型全量字段拷贝。
     *
     * @param payment 实体
     * @return 读模型
     */
    private PaymentReconciliationQueryModel toQueryModel(PaymentReconciliation payment) {
        return toQueryModel(payment, List.of());
    }

    private PaymentReconciliationQueryModel toQueryModel(
        PaymentReconciliation payment,
        List<PaymentYbSettlement> ybRows) {
        PaymentReconciliationQueryModel model = new PaymentReconciliationQueryModel();
        model.setId(payment.getId());
        model.setStatusEnum(payment.getStatusEnum());
        model.setRelationId(payment.getRelationId());
        model.setPaymentNo(payment.getPaymentNo());
        model.setPaymentEnum(payment.getPaymentEnum());
        model.setKindEnum(payment.getKindEnum());
        model.setEntererId(payment.getEntererId());
        model.setPatientId(payment.getPatientId());
        model.setPractitionerId(payment.getPractitionerId());
        model.setOutcomeEnum(payment.getOutcomeEnum());
        model.setLocationId(payment.getLocationId());
        model.setTenderedAmount(payment.getTenderedAmount());
        model.setReturnedAmount(payment.getReturnedAmount());
        model.setDisplayAmount(payment.getDisplayAmount());
        model.setContractNo(payment.getContractNo());
        model.setChargeItemIds(payment.getChargeItemIds());
        model.setEncounterId(payment.getEncounterId());
        model.setBillDate(payment.getBillDate());
        model.setInvoiceId(payment.getInvoiceId());
        model.setAccountId(payment.getAccountId());
        model.setYbSettlements(toYbSettlementQueryModels(ybRows));
        model.setYbClearFlag(payment.getYbClearFlag());
        model.setPrintCount(payment.getPrintCount());
        return model;
    }

    private List<PaymentYbSettlementQueryModel> toYbSettlementQueryModels(List<PaymentYbSettlement> rows) {
        List<PaymentYbSettlementQueryModel> result = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return result;
        }
        for (PaymentYbSettlement row : rows) {
            if (row == null) {
                continue;
            }
            PaymentYbSettlementQueryModel item = new PaymentYbSettlementQueryModel();
            item.setId(row.getId());
            item.setReconciliationId(row.getReconciliationId());
            item.setSetlId(row.getSetlId());
            item.setChrgBchno(row.getChrgBchno());
            item.setMdtrtId(row.getMdtrtId());
            item.setSettlementRecordId(row.getSettlementRecordId());
            item.setAccountId(row.getAccountId());
            item.setStatus(row.getStatus());
            result.add(item);
        }
        return result;
    }

    private void attachYbSettlements(List<PaymentReconciliationQueryModel> models) {
        if (models == null || models.isEmpty()) {
            return;
        }
        LinkedHashSet<Long> paymentIds = new LinkedHashSet<>();
        for (PaymentReconciliationQueryModel model : models) {
            if (model != null && model.getId() != null) {
                paymentIds.add(model.getId());
            }
        }
        if (paymentIds.isEmpty()) {
            return;
        }
        List<PaymentYbSettlement> rows = ybSettlementRepository
            .findAllByReconciliationIdInAndIsDeletedFalseOrderByReconciliationIdAscCreatedAtAsc(paymentIds);
        Map<Long, List<PaymentYbSettlement>> grouped = new HashMap<>();
        for (PaymentYbSettlement row : rows) {
            if (row == null || row.getReconciliationId() == null) {
                continue;
            }
            grouped.computeIfAbsent(row.getReconciliationId(), key -> new ArrayList<>()).add(row);
        }
        for (PaymentReconciliationQueryModel model : models) {
            if (model == null || model.getId() == null) {
                continue;
            }
            List<PaymentYbSettlement> ybRows = grouped.get(model.getId());
            if (ybRows == null) {
                model.setYbSettlements(List.of());
            } else {
                model.setYbSettlements(toYbSettlementQueryModels(ybRows));
            }
        }
    }

    private void sortByBillDateDesc(List<PaymentReconciliation> rows) {
        rows.sort((left, right) -> {
            LocalDateTime leftDate = left.getBillDate();
            LocalDateTime rightDate = right.getBillDate();
            // 无 bill_date 的行排到末尾，避免干扰日结预览默认排序
            if (leftDate == null && rightDate == null) {
                return 0;
            }
            if (leftDate == null) {
                return 1;
            }
            if (rightDate == null) {
                return -1;
            }
            return rightDate.compareTo(leftDate);
        });
    }

    private UnsettledPaymentPreviewItemQueryModel toUnsettledPreviewItem(PaymentReconciliation row) {
        UnsettledPaymentPreviewItemQueryModel item = new UnsettledPaymentPreviewItemQueryModel();
        item.setPaymentId(row.getId());
        item.setPaymentNo(row.getPaymentNo());
        if (row.getKindEnum() != null) {
            item.setKindEnum(row.getKindEnum().getCode());
        }
        if (row.getStatusEnum() != null) {
            item.setStatusEnum(row.getStatusEnum().getCode());
        }
        item.setEntererId(row.getEntererId());
        item.setBillDate(row.getBillDate());
        item.setPaymentEnum(row.getPaymentEnum());
        item.setRelationId(row.getRelationId());
        item.setTenderedAmount(row.getTenderedAmount());
        item.setReturnedAmount(row.getReturnedAmount());
        item.setNetAmount(this.computeUnsettledPreviewNetAmount(row));
        item.setContractNo(row.getContractNo());
        return item;
    }

    private BigDecimal computeUnsettledPreviewNetAmount(PaymentReconciliation row) {
        if (this.isReverseRefundPayment(row)) {
            BigDecimal amount = row.getDisplayAmount();
            if (amount == null) {
                amount = row.getTenderedAmount();
            }
            if (amount == null) {
                amount = BigDecimal.ZERO;
            }
            return amount.abs().negate();
        }
        BigDecimal tendered = row.getTenderedAmount();
        if (tendered == null) {
            tendered = BigDecimal.ZERO;
        }
        BigDecimal returned = row.getReturnedAmount();
        if (returned == null) {
            returned = BigDecimal.ZERO;
        }
        return tendered.subtract(returned);
    }

    private boolean isReverseRefundPayment(PaymentReconciliation row) {
        if (row == null) {
            return false;
        }
        if (row.getRelationId() != null) {
            return true;
        }
        return PaymentType.UN_PAY.getCode().equals(row.getPaymentEnum());
    }

    private void assertCashierSettlementPreviewQueryValid(PreviewCashierSettlementQuery query) {
        if (query == null) {
            throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT, "交账预览条件不能为空");
        }
        if (query.getCashierId() == null) {
            throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT, "交账经办人不能为空");
        }
        LocalDate dateFrom = query.getDateFrom();
        LocalDate dateTo = query.getDateTo();
        if (dateFrom == null || dateTo == null) {
            throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT, "交账业务日范围不能为空");
        }
        if (dateTo.isBefore(dateFrom)) {
            throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT, "交账业务日范围不合法");
        }
        this.resolveCashierSettlementType(query.getTypeEnum());
    }

    private Specification<PaymentReconciliation> buildUnsettledForCashierSettlementSpecification(
        PreviewCashierSettlementQuery query) {
        CashierSettlementType settlementType = this.resolveCashierSettlementType(query.getTypeEnum());
        List<PaymentKind> kinds = this.resolveCashierSettlementPaymentKinds(settlementType);
        LocalDateTime windowStart = query.getDateFrom().atStartOfDay();
        LocalDateTime windowEndExclusive = query.getDateTo().plusDays(1).atStartOfDay();
        List<PaymentStatus> allowedStatuses = Arrays.asList(PaymentStatus.SUCCESS, PaymentStatus.REFUNDED);
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.isNull(root.get(FieldNameConstants.CASHIER_SETTLEMENT_ID)));
            predicates.add(root.get(FieldNameConstants.KIND_ENUM).in(kinds));
            predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(allowedStatuses));
            this.addEntererMatchPredicate(predicates, cb, root, query.getEntererMatchIds(), query.getCashierId());
            predicates.add(cb.greaterThanOrEqualTo(root.get(FieldNameConstants.BILL_DATE), windowStart));
            predicates.add(cb.lessThan(root.get(FieldNameConstants.BILL_DATE), windowEndExclusive));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private CashierSettlementType resolveCashierSettlementType(String typeEnum) {
        if (typeEnum == null) {
            throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT, "交账类型不能为空");
        }
        for (CashierSettlementType candidate : CashierSettlementType.values()) {
            if (candidate.getCode().equals(typeEnum)) {
                return candidate;
            }
        }
        throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT, "交账类型不合法");
    }

    private List<PaymentKind> resolveCashierSettlementPaymentKinds(CashierSettlementType settlementType) {
        // 门诊交账仅纳入 OUTPATIENT_CLINIC（BR-CS-004）
        if (CashierSettlementType.OUTPATIENT.equals(settlementType)) {
            return Collections.singletonList(PaymentKind.OUTPATIENT_CLINIC);
        }
        // 住院交账纳入结算与预交金（BR-CS-005）
        return Arrays.asList(PaymentKind.INPATIENT_CLINIC, PaymentKind.HOSPITAL_DEPOSIT);
    }

    /**
     * 门诊费用明细：按 payment {@code bill_date}/收费人/收退 Tab 解析候选 chargeItem 主键（DM-FIN-002 §6.2.17）。
     *
     * <p>仅读本聚合 {@code fin_payment_reconciliation}；同步写入
     * 同步写入 {@link OutpatientFeeDetailPageQuery#setPaymentChargeItemLinks(java.util.List)}、
     * {@link OutpatientFeeDetailPageQuery#setPaymentContextByChargeItemId(java.util.Map)}，
     * 供 {@link #mapOutpatientFeeDetailPaymentContextByChargeItemIds} 与 ChargeItem 读侧复用。</p>
     *
     * @param query 须含 {@code billDateFrom}/{@code billDateTo}；可选 {@code cashierId}、{@code tradeDirection}
     * @return 去重后的 chargeItem 主键；无命中时空列表
     * @throws IllegalArgumentException 日期缺失或 {@code billDateFrom > billDateTo}
     */
    public List<Long> listOutpatientFeeDetailChargeItemIdsByPaymentFilter(OutpatientFeeDetailPageQuery query) {
        if (query == null || query.getBillDateFrom() == null || query.getBillDateTo() == null) {
            throw new IllegalArgumentException("outpatient fee detail payment filter requires billDateFrom and billDateTo");
        }
        if (query.getBillDateFrom().isAfter(query.getBillDateTo())) {
            throw new IllegalArgumentException("billDateFrom must not be after billDateTo");
        }
        Specification<PaymentReconciliation> specification = this.buildOutpatientFeeDetailPaymentSpecification(query);
        List<PaymentReconciliation> payments = paymentRepository.findAll(specification);
        if (payments.isEmpty()) {
            query.setPaymentContextByChargeItemId(Collections.emptyMap());
            query.setPaymentChargeItemLinks(Collections.emptyList());
            query.setPaymentsInScope(Collections.emptyList());
            return Collections.emptyList();
        }
        this.sortByBillDateDesc(payments);
        Map<Long, OutpatientFeeDetailPaymentContextQueryModel> contextByChargeItemId = new LinkedHashMap<>();
        LinkedHashSet<Long> chargeItemIds = new LinkedHashSet<>();
        List<OutpatientFeeDetailPaymentScopeQueryModel> paymentsInScope = new ArrayList<>();
        List<OutpatientFeeDetailChargePaymentLinkQueryModel> paymentChargeItemLinks = new ArrayList<>();
        List<PaymentReconciliationQueryModel> models = new ArrayList<>();
        for (PaymentReconciliation payment : payments) {
            if (payment == null) {
                continue;
            }
            models.add(this.toQueryModel(payment));
        }
        // 挂载已确认医保关联，供 ybFundAndAcctTotal 计算（BR-OP-FDT-011）
        this.attachYbSettlements(models);
        Map<Long, PaymentReconciliationQueryModel> modelByPaymentId = new HashMap<>();
        for (PaymentReconciliationQueryModel model : models) {
            if (model != null && model.getId() != null) {
                modelByPaymentId.put(model.getId(), model);
            }
        }
        // 按 bill_date 降序遍历，解析 charge_item_ids 快照并写入读上下文
        for (PaymentReconciliation payment : payments) {
            if (payment == null || payment.getId() == null) {
                continue;
            }
            PaymentReconciliationQueryModel paymentModel = modelByPaymentId.get(payment.getId());
            BigDecimal ybFundAndAcctTotal = this.resolveOutpatientFeeDetailYbFundAndAcctTotal(payment, paymentModel);
            OutpatientFeeDetailPaymentScopeQueryModel paymentScope = new OutpatientFeeDetailPaymentScopeQueryModel();
            paymentScope.setPaymentId(payment.getId());
            paymentScope.setEncounterId(payment.getEncounterId());
            paymentScope.setBillDate(payment.getBillDate());
            paymentScope.setCashierId(payment.getEntererId());
            paymentScope.setRefundPayment(PaymentStatus.REFUNDED.equals(payment.getStatusEnum()));
            paymentScope.setChargeItemIdsSnapshot(payment.getChargeItemIds());
            paymentScope.setYbFundAndAcctTotal(ybFundAndAcctTotal);
            paymentsInScope.add(paymentScope);
            List<Long> snapshotIds = this.parseIds(payment.getChargeItemIds());
            for (Long chargeItemId : snapshotIds) {
                if (chargeItemId == null) {
                    continue;
                }
                chargeItemIds.add(chargeItemId);
                OutpatientFeeDetailPaymentContextQueryModel context = new OutpatientFeeDetailPaymentContextQueryModel();
                context.setChargeItemId(chargeItemId);
                context.setPaymentId(payment.getId());
                context.setEncounterId(payment.getEncounterId());
                context.setBillDate(payment.getBillDate());
                context.setCashierId(payment.getEntererId());
                context.setRefundPayment(PaymentStatus.REFUNDED.equals(payment.getStatusEnum()));
                context.setYbFundAndAcctTotal(ybFundAndAcctTotal);
                // 兼容快照截断补全：保留 chargeItemId 最近一条上下文，明细行以 links 列表为准
                contextByChargeItemId.put(chargeItemId, context);
                OutpatientFeeDetailChargePaymentLinkQueryModel link = new OutpatientFeeDetailChargePaymentLinkQueryModel();
                link.setChargeItemId(chargeItemId);
                link.setPaymentContext(context);
                paymentChargeItemLinks.add(link);
            }
        }
        query.setPaymentContextByChargeItemId(contextByChargeItemId);
        query.setPaymentChargeItemLinks(paymentChargeItemLinks);
        query.setPaymentsInScope(paymentsInScope);
        return new ArrayList<>(chargeItemIds);
    }

    /**
     * 门诊费用明细：按 chargeItem 主键批量返回 payment 读上下文（DM-FIN-002 §6.2.18）。
     *
     * <p>须与 {@link #listOutpatientFeeDetailChargeItemIdsByPaymentFilter} 使用相同 {@code billDateFrom/To} 预载
     * {@link OutpatientFeeDetailPageQuery#getPaymentContextByChargeItemId()}；
     * {@code paymentSnapshotAbsAmountTotal} 由 ChargeItem 读侧在加载 scope 后补全。</p>
     *
     * @param query         同批 payment 筛选条件；可已含 {@code paymentContextByChargeItemId}
     * @param chargeItemIds 待 enrichment 的明细主键
     * @return chargeItemId → 上下文；未命中 id 不包含在 map 中
     */
    public Map<Long, OutpatientFeeDetailPaymentContextQueryModel> mapOutpatientFeeDetailPaymentContextByChargeItemIds(
        OutpatientFeeDetailPageQuery query,
        Collection<Long> chargeItemIds) {
        if (chargeItemIds == null || chargeItemIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, OutpatientFeeDetailPaymentContextQueryModel> loadedContext = query.getPaymentContextByChargeItemId();
        if (loadedContext == null || loadedContext.isEmpty()) {
            // 调用方未预载时，按相同 bill_date 条件补调 list 方法
            this.listOutpatientFeeDetailChargeItemIdsByPaymentFilter(query);
            loadedContext = query.getPaymentContextByChargeItemId();
        }
        if (loadedContext == null || loadedContext.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, OutpatientFeeDetailPaymentContextQueryModel> result = new LinkedHashMap<>();
        for (Long chargeItemId : chargeItemIds) {
            if (chargeItemId == null) {
                continue;
            }
            OutpatientFeeDetailPaymentContextQueryModel context = loadedContext.get(chargeItemId);
            if (context != null) {
                result.put(chargeItemId, context);
            }
        }
        return result;
    }

    /**
     * 组装门诊费用明细 payment 筛选 Specification（bill_date 左闭右开日窗 + 门诊 kind + 收退 Tab）。
     */
    private Specification<PaymentReconciliation> buildOutpatientFeeDetailPaymentSpecification(
        OutpatientFeeDetailPageQuery query) {
        LocalDateTime windowStart = query.getBillDateFrom().atStartOfDay();
        LocalDateTime windowEndExclusive = query.getBillDateTo().plusDays(1).atStartOfDay();
        List<PaymentStatus> allowedStatuses = this.resolveOutpatientFeeDetailPaymentStatuses(query.getTradeDirection());
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.KIND_ENUM), PaymentKind.OUTPATIENT_CLINIC));
            predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(allowedStatuses));
            predicates.add(cb.greaterThanOrEqualTo(root.get(FieldNameConstants.BILL_DATE), windowStart));
            predicates.add(cb.lessThan(root.get(FieldNameConstants.BILL_DATE), windowEndExclusive));
            this.addEntererMatchPredicate(predicates, cb, root, query.getEntererMatchIds(), query.getCashierId());
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 收退 Tab 映射为 payment 状态白名单（BR-OP-FDT-009）。
     */
    private List<PaymentStatus> resolveOutpatientFeeDetailPaymentStatuses(String tradeDirection) {
        if (OutpatientFeeDetailConstants.TRADE_DIRECTION_CHARGE.equals(tradeDirection)) {
            return Collections.singletonList(PaymentStatus.SUCCESS);
        }
        if (OutpatientFeeDetailConstants.TRADE_DIRECTION_REFUND.equals(tradeDirection)) {
            return Collections.singletonList(PaymentStatus.REFUNDED);
        }
        // ALL 或未识别：收费 SUCCESS 与退费 REFUNDED 并集
        return Arrays.asList(PaymentStatus.SUCCESS, PaymentStatus.REFUNDED);
    }

    /**
     * 计算 payment 级医保基金+个账合计：有已确认医保关联时 {@code tendered − 自付子明细合计}，否则 0。
     *
     * <p>禁止从 {@code PaymentRecDetail.pay_enum} 推断医保（BR-OP-FDT-011）。</p>
     */
    private BigDecimal resolveOutpatientFeeDetailYbFundAndAcctTotal(
        PaymentReconciliation payment,
        PaymentReconciliationQueryModel paymentModel) {
        if (payment == null || paymentModel == null) {
            return BigDecimal.ZERO;
        }
        if (!PaymentYbSettlementSupport.hasConfirmed(paymentModel.getYbSettlements())) {
            // 纯自费 payment 不参与医保分摊
            return BigDecimal.ZERO;
        }
        BigDecimal tendered = payment.getTenderedAmount();
        if (tendered == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal selfPayTotal = BigDecimal.ZERO;
        List<PaymentRecDetail> details = detailRepository.findByReconciliationId(payment.getId());
        for (PaymentRecDetail detail : details) {
            if (detail == null || detail.getAmount() == null) {
                continue;
            }
            selfPayTotal = selfPayTotal.add(detail.getAmount());
        }
        BigDecimal ybTotal = tendered.subtract(selfPayTotal);
        if (ybTotal.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return ybTotal;
    }

    /**
     * 将逗号分隔的费用项 id 串解析为 Long 列表，忽略空段。
     *
     * @param ids 原始串，可为 null
     * @return 非 null 的 id 列表
     */
    private List<Long> parseIds(String ids) {
        List<Long> result = new ArrayList<>();
        if (ids == null || ids.isBlank()) {
            return result;
        }
        String[] values = ids.split(",");
        for (String value : values) {
            if (!value.isBlank()) {
                result.add(Long.parseLong(value.trim()));
            }
        }
        return result;
    }

    /**
     * 经办人/收费人筛选：优先 {@code entererMatchIds} 白名单，否则回落单值 {@code cashierId}。
     */
    private void addEntererMatchPredicate(
        List<Predicate> predicates,
        CriteriaBuilder cb,
        jakarta.persistence.criteria.Root<PaymentReconciliation> root,
        List<Long> entererMatchIds,
        Long cashierId) {
        if (entererMatchIds != null && !entererMatchIds.isEmpty()) {
            predicates.add(root.get(FieldNameConstants.ENTERER_ID).in(entererMatchIds));
            return;
        }
        if (cashierId != null) {
            predicates.add(cb.equal(root.get(FieldNameConstants.ENTERER_ID), cashierId));
        }
    }
}

