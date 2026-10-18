/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.financial.paymentreconciliation.service;

import com.zxplat.domain.financial.paymentreconciliation.entity.PaymentRecDetail;
import com.zxplat.domain.financial.paymentreconciliation.entity.PaymentRecStatic;
import com.zxplat.domain.financial.paymentreconciliation.entity.PaymentReconciliation;
import com.zxplat.domain.financial.paymentreconciliation.entity.PaymentYbSettlement;
import com.zxplat.domain.financial.paymentreconciliation.queries.model.*;
import com.zxplat.domain.financial.paymentreconciliation.repository.PaymentRecDetailRepository;
import com.zxplat.domain.financial.paymentreconciliation.repository.PaymentRecStaticRepository;
import com.zxplat.domain.financial.paymentreconciliation.repository.PaymentReconciliationRepository;
import com.zxplat.domain.financial.paymentreconciliation.repository.PaymentYbSettlementRepository;
import com.zxplat.domain.financial.cashiersettlement.queries.PreviewCashierSettlementQuery;
import com.zxplat.domain.financial.cashiersettlement.queries.model.CashierSettlementMountedPaymentBundle;
import com.zxplat.domain.financial.cashiersettlement.queries.model.CashierSettlementSummaryPaymentBundle;
import com.zxplat.domain.financial.cashiersettlement.queries.model.PreviewCashierSettlementQueryModel;
import com.zxplat.domain.financial.cashiersettlement.queries.model.UnsettledPaymentPreviewItemQueryModel;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.financial.paymentreconciliation.support.PaymentYbSettlementSupport;
import com.zxplat.domain.administration.chargeitem.query.OutpatientFeeDetailPageQuery;
import com.zxplat.domain.administration.chargeitem.query.PractitionerWorkloadPageQuery;
import com.zxplat.domain.administration.encounter.entity.Encounter;
import com.zxplat.domain.share.constants.PractitionerWorkloadConstants;
import com.zxplat.domain.share.constants.OutpatientFeeDetailConstants;
import com.zxplat.domain.share.constants.FieldNameConstants;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.domain.share.enums.CashierSettlementType;
import com.zxplat.domain.share.enums.EncounterClass;
import com.zxplat.domain.share.enums.PaymentKind;
import com.zxplat.domain.share.enums.PaymentStatus;
import com.zxplat.domain.share.financial.DailySettlementRefundPaymentRules;
import com.zxplat.domain.share.utils.MonetaryAmountSupport;
import com.zxplat.domain.share.utils.IdInQueryChunkSupport;
import com.zxplat.framework.core.application.exception.BusinessException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import com.zxplat.framework.core.application.util.StringUtils;
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
     * 另含收费员交账未挂载 payment 候选预览/计数（{@code previewUnsettledPaymentsForCashierSettlement}，**BR-CS-001～006**，B.6.4）
     * 与日结展示汇总读路径（{@code previewPaymentsForCashierSettlementSummary}，含已交账）；不写库。</p>
 * <p>聚合边界：仅本聚合 {@link PaymentReconciliationRepository}、{@link PaymentRecDetailRepository}、{@link PaymentRecStaticRepository}、{@link PaymentYbSettlementRepository}。
 * 收款日结候选另以 Criteria EXISTS 关联 {@link Encounter} 排除体检就诊，不注入 Encounter 仓储。</p>
 */
@Service
@RequiredArgsConstructor
public class PaymentReconciliationQueryService {

    /** 历史预交金 {@code kind_enum} 落库码（与 {@link PaymentKind#HOSPITAL_DEPOSIT} 同义，见 PaymentKindConverter）。 */
    private static final String LEGACY_PRE_DEPOSIT_KIND_CODE = "PRE_DEPOSIT";
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
     * 按主键加载支付实体（adapter 读路径）；不存在或已软删时 empty。
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
     * 查询住院就诊下最新一条预交金草稿（门诊转住院意向 / 扫码预下单）。
     *
     * <p>多条 DRAFT 时取主键较大者，供登记弹窗回显医生意向总额。</p>
     *
     * @param encounterId 住院就诊主键
     * @return 最新 HOSPITAL_DEPOSIT + DRAFT；无则 empty
     */
    public Optional<PaymentReconciliationQueryModel> findLatestHospitalDepositDraftByEncounter(Long encounterId) {
        List<PaymentReconciliationQueryModel> drafts = this.listHospitalDepositDraftsByEncounter(encounterId);
        if (drafts.isEmpty()) {
            return Optional.empty();
        }
        PaymentReconciliationQueryModel latest = drafts.get(0);
        for (PaymentReconciliationQueryModel row : drafts) {
            // 主键更大者视为当前有效意向/预下单草稿，避免回显过期草稿金额
            if (row.getId() != null && latest.getId() != null && row.getId() > latest.getId()) {
                latest = row;
            }
        }
        return Optional.of(latest);
    }

    /**
     * 列出住院就诊下全部预交金草稿，供取消登记时批量作废。
     *
     * @param encounterId 住院就诊主键
     * @return HOSPITAL_DEPOSIT + DRAFT 列表；无则空列表
     */
    public List<PaymentReconciliationQueryModel> listHospitalDepositDraftsByEncounter(Long encounterId) {
        List<PaymentReconciliationQueryModel> result = new ArrayList<>();
        if (encounterId == null) {
            return result;
        }
        List<PaymentReconciliation> payments = paymentRepository.findByEncounterIdAndStatusEnumIn(
            encounterId,
            List.of(PaymentStatus.DRAFT));
        for (PaymentReconciliation payment : payments) {
            if (payment == null || Boolean.TRUE.equals(payment.getIsDeleted())) {
                continue;
            }
            // 仅住院预交金意向/预下单草稿；门诊挂号等其他 kind 的 DRAFT 不纳入
            if (payment.getKindEnum() != PaymentKind.HOSPITAL_DEPOSIT) {
                continue;
            }
            result.add(this.toQueryModel(payment));
        }
        return result;
    }

    /**
     * 按就诊主键集合批量列出支付流水（药房日结挂载净收入等；禁止逐就诊 {@link #findPaymentsByEncounter}）。
     *
     * <p>不挂载医保结算子表（调用方仅需 payment 头净额字段时更轻）。</p>
     *
     * @param encounterIds 就诊主键集合
     * @return 流水读模型列表；入参空时返回空列表
     */
    public List<PaymentReconciliationQueryModel> findPaymentsByEncounterIds(Collection<Long> encounterIds) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> distinctIds = new LinkedHashSet<>();
        for (Long encounterId : encounterIds) {
            if (encounterId != null) {
                distinctIds.add(encounterId);
            }
        }
        if (distinctIds.isEmpty()) {
            return List.of();
        }
        List<PaymentReconciliation> payments = this.paymentRepository.findByEncounterIdIn(distinctIds);
        List<PaymentReconciliationQueryModel> result = new ArrayList<>();
        for (PaymentReconciliation payment : payments) {
            if (payment == null || Boolean.TRUE.equals(payment.getIsDeleted())) {
                continue;
            }
            result.add(this.toQueryModel(payment));
        }
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
     * 按就诊批量查找：正向 SUCCESS 流水中 {@code charge_item_ids} 与给定挂号费收费项有交集的支付主键。
     *
     * <p>用于退号/补打挂号小票：锚定「挂载挂号费 chargeItem」的支付单，禁止用最近/最早启发式误绑门诊收费单。</p>
     *
     * @param registrationChargeItemIdsByEncounter 就诊 → 挂号费收费项主键集合
     * @return 就诊主键 → 命中的支付流水主键；无交集时不写入
     */
    public Map<Long, Long> findSuccessPaymentIdsOverlappingChargeItemsByEncounter(
        Map<Long, ? extends Collection<Long>> registrationChargeItemIdsByEncounter) {
        Map<Long, Long> result = new HashMap<>();
        if (registrationChargeItemIdsByEncounter == null || registrationChargeItemIdsByEncounter.isEmpty()) {
            return result;
        }
        List<Long> encounterIds = new ArrayList<>();
        for (Map.Entry<Long, ? extends Collection<Long>> entry : registrationChargeItemIdsByEncounter.entrySet()) {
            if (entry == null || entry.getKey() == null) {
                continue;
            }
            Collection<Long> chargeItemIds = entry.getValue();
            if (chargeItemIds == null || chargeItemIds.isEmpty()) {
                continue;
            }
            encounterIds.add(entry.getKey());
        }
        if (encounterIds.isEmpty()) {
            return result;
        }
        List<PaymentReconciliation> rows =
            paymentRepository.findByEncounterIdInAndStatusEnum(encounterIds, PaymentStatus.SUCCESS);
        for (PaymentReconciliation row : rows) {
            if (row == null || row.getRelationId() != null || row.getEncounterId() == null || row.getId() == null) {
                continue;
            }
            Collection<Long> wanted = registrationChargeItemIdsByEncounter.get(row.getEncounterId());
            if (wanted == null || wanted.isEmpty()) {
                continue;
            }
            Set<Long> wantedSet = new HashSet<>();
            for (Long chargeItemId : wanted) {
                if (chargeItemId != null) {
                    wantedSet.add(chargeItemId);
                }
            }
            if (wantedSet.isEmpty()) {
                continue;
            }
            boolean overlaps = false;
            for (Long boundId : this.parseIds(row.getChargeItemIds())) {
                if (wantedSet.contains(boundId)) {
                    overlaps = true;
                    break;
                }
            }
            if (!overlaps) {
                continue;
            }
            Long existing = result.get(row.getEncounterId());
            if (existing == null || row.getId() < existing) {
                result.put(row.getEncounterId(), row.getId());
            }
        }
        return result;
    }

    /**
     * 列出就诊下全部 SUCCESS 支付（账单时间倒序，空 bill_date 回落主键倒序）。
     *
     * @param encounterId 就诊主键
     * @return 成功支付读模型列表；无则空列表
     */
    public List<PaymentReconciliationQueryModel> listSuccessPaymentsByEncounterId(Long encounterId) {
        if (encounterId == null) {
            return List.of();
        }
        List<PaymentReconciliation> rows = paymentRepository.findByEncounterIdAndStatusEnumIn(
            encounterId,
            List.of(PaymentStatus.SUCCESS));
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        rows.sort((left, right) -> {
            LocalDateTime leftDate = left.getBillDate();
            LocalDateTime rightDate = right.getBillDate();
            if (leftDate != null && rightDate != null) {
                int byDate = rightDate.compareTo(leftDate);
                if (byDate != 0) {
                    return byDate;
                }
            } else if (leftDate != null) {
                return -1;
            } else if (rightDate != null) {
                return 1;
            }
            Long leftId = left.getId();
            Long rightId = right.getId();
            if (leftId == null && rightId == null) {
                return 0;
            }
            if (leftId == null) {
                return 1;
            }
            if (rightId == null) {
                return -1;
            }
            return Long.compare(rightId, leftId);
        });
        List<PaymentReconciliationQueryModel> result = new ArrayList<>();
        for (PaymentReconciliation row : rows) {
            result.add(this.toQueryModel(row));
        }
        this.attachYbSettlements(result);
        return result;
    }

    /**
     * 批量读取支付流水经办人（{@code enterer_id}），供挂号列表回显收费人。
     *
     * <p>口径与结算写侧一致：正式收费成功后写入的经办人，与 {@code adm_charge_item.enterer_id} 同源（BR-OPC-016）。</p>
     *
     * @param paymentIds 支付流水主键集合；空集合时返回空 Map
     * @return 支付流水主键 → entererId；无经办人或主键为空时不写入该键
     */
    public Map<Long, Long> mapEntererIdByPaymentIds(Collection<Long> paymentIds) {
        Map<Long, Long> result = new HashMap<>();
        if (paymentIds == null || paymentIds.isEmpty()) {
            return result;
        }
        List<Long> idList = new ArrayList<>();
        for (Long paymentId : paymentIds) {
            if (paymentId != null) {
                idList.add(paymentId);
            }
        }
        if (idList.isEmpty()) {
            return result;
        }
        List<PaymentReconciliation> rows = paymentRepository.findByIdIn(idList);
        for (PaymentReconciliation row : rows) {
            if (row == null || row.getId() == null || row.getEntererId() == null) {
                continue;
            }
            result.put(row.getId(), row.getEntererId());
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
     * 按账单日左闭右开区间拉取流水（与日结交账预览 {@code bill_date} 窗口径一致）。
     *
     * @param windowStartInclusive 起始时刻（含）
     * @param windowEndExclusive   结束时刻（不含，通常为 {@code dateTo.plusDays(1).atStartOfDay()}）
     * @return 流水读模型列表
     */
    public List<PaymentReconciliationQueryModel> findPaymentByBillDateWindow(
        LocalDateTime windowStartInclusive,
        LocalDateTime windowEndExclusive) {
        Specification<PaymentReconciliation> specification = (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.greaterThanOrEqualTo(root.get(FieldNameConstants.BILL_DATE), windowStartInclusive));
            predicates.add(cb.lessThan(root.get(FieldNameConstants.BILL_DATE), windowEndExclusive));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<PaymentReconciliation> payments = this.paymentRepository.findAll(specification);
        List<PaymentReconciliationQueryModel> result = new ArrayList<>();
        for (PaymentReconciliation payment : payments) {
            result.add(this.toQueryModel(payment));
        }
        this.attachYbSettlements(result);
        return result;
    }

    /**
     * 预览收费员交账未挂载候选 payment（BR-CS-001～006；供日结 IF 与写侧复用口径）。
     *
     * @param query 交账类型、科室/location 范围、可选 enterer 软筛与业务日窗
     * @return 候选 items 与条数
     * @throws BusinessException 查询条件非法
     */
    public PreviewCashierSettlementQueryModel previewUnsettledPaymentsForCashierSettlement(PreviewCashierSettlementQuery query) {
        List<PaymentReconciliation> rows = this.findUnsettledPaymentsForCashierSettlement(query);
        PreviewCashierSettlementQueryModel model = new PreviewCashierSettlementQueryModel();
        List<UnsettledPaymentPreviewItemQueryModel> items = this.toUnsettledPreviewItems(rows);
        model.setItems(items);
        // paymentCount = 候选流水条数（含 0 元与收支抵净；BR-CS-011），与 items.size 及提交挂载条数一致
        model.setPaymentCount(items.size());
        return model;
    }

    /**
     * 统计收费员交账未挂载候选 payment 条数。
     *
     * @param query 交账类型、科室/location 范围、可选 enterer 软筛与业务日窗
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
        Specification<PaymentReconciliation> specification =
            this.buildCashierSettlementScopeSpecification(query, true);
        List<PaymentReconciliation> rows = this.paymentRepository.findAll(specification);
        this.sortByBillDateDesc(rows);
        return rows;
    }

    /**
     * 按 BR-CS-001、003～006 查询日结展示用 payment（含已交账与未交账，供 rows/summary 汇总）。
     *
     * @param query 交账类型、科室/location 范围、可选 enterer 软筛与业务日窗
     * @return 展示口径 payment 列表，按 billDate 降序
     * @throws BusinessException 查询条件非法
     */
    public List<PaymentReconciliation> findPaymentsForCashierSettlementSummary(PreviewCashierSettlementQuery query) {
        this.assertCashierSettlementPreviewQueryValid(query);
        Specification<PaymentReconciliation> specification =
            this.buildCashierSettlementScopeSpecification(query, false);
        List<PaymentReconciliation> rows = this.paymentRepository.findAll(specification);
        this.sortByBillDateDesc(rows);
        return rows;
    }

    /**
     * 日结展示用 payment 预览行（含已交账；与 {@link #previewUnsettledPaymentsForCashierSettlement} 共用 BR-CS-001、003～006）。
     *
     * @param query 交账类型、科室/location 范围、可选 enterer 软筛与业务日窗
     * @return 展示口径预览行列表
     * @throws BusinessException 查询条件非法
     */
    public List<UnsettledPaymentPreviewItemQueryModel> previewPaymentsForCashierSettlementSummary(
        PreviewCashierSettlementQuery query) {
        return this.loadPaymentsForCashierSettlementSummary(query).getPreviewItems();
    }

    /**
     * 日结展示用 payment 读模型（含医保关联 enrichment）。
     *
     * @param query 交账类型、科室/location 范围、可选 enterer 软筛与业务日窗
     * @return 展示口径读模型列表
     * @throws BusinessException 查询条件非法
     */
    public List<PaymentReconciliationQueryModel> findPaymentQueryModelsForCashierSettlementSummary(
        PreviewCashierSettlementQuery query) {
        return this.loadPaymentsForCashierSettlementSummary(query).getPaymentModels();
    }

    /**
     * 一次装载日结展示窗 payment（含已交账预览行 + 读模型），避免展示窗双扫。
     *
     * @param query 交账类型、科室/location 范围、可选 enterer 软筛与业务日窗
     * @return 预览行与读模型
     * @throws BusinessException 查询条件非法
     */
    public CashierSettlementSummaryPaymentBundle loadPaymentsForCashierSettlementSummary(
        PreviewCashierSettlementQuery query) {
        List<PaymentReconciliation> rows = this.findPaymentsForCashierSettlementSummary(query);
        CashierSettlementSummaryPaymentBundle bundle = new CashierSettlementSummaryPaymentBundle();
        bundle.setPreviewItems(this.toUnsettledPreviewItems(rows));
        List<PaymentReconciliationQueryModel> paymentModels = new ArrayList<>();
        for (PaymentReconciliation payment : rows) {
            paymentModels.add(this.toQueryModel(payment));
        }
        this.attachYbSettlements(paymentModels);
        bundle.setPaymentModels(paymentModels);
        return bundle;
    }

    /**
     * 按交账头挂载读取 payment 读模型（历史重打 / 审批详情现算；仅本聚合 Repository）。
     *
     * @param cashierSettlementId 交账头主键
     * @return 已挂载 payment 读模型；主键空时返回空列表
     */
    public List<PaymentReconciliationQueryModel> findPaymentQueryModelsByCashierSettlementId(
        Long cashierSettlementId) {
        return this.loadMountedPaymentsByCashierSettlementId(cashierSettlementId).getPaymentModels();
    }

    /**
     * 按交账头挂载组装日结预览行（与候选预览行字段同构，供 rows/summary 现算）。
     *
     * @param cashierSettlementId 交账头主键
     * @return 预览行；主键空时返回空列表
     */
    public List<UnsettledPaymentPreviewItemQueryModel> previewPaymentsByCashierSettlementId(
        Long cashierSettlementId) {
        return this.loadMountedPaymentsByCashierSettlementId(cashierSettlementId).getPreviewItems();
    }

    /**
     * 一次装载交账头挂载 payment（预览行 + 读模型），避免头表双扫。
     *
     * @param cashierSettlementId 交账头主键
     * @return 预览行与读模型；主键空时为空集合
     */
    public CashierSettlementMountedPaymentBundle loadMountedPaymentsByCashierSettlementId(
        Long cashierSettlementId) {
        CashierSettlementMountedPaymentBundle bundle = new CashierSettlementMountedPaymentBundle();
        if (cashierSettlementId == null) {
            return bundle;
        }
        List<PaymentReconciliation> rows =
            this.paymentRepository.findByCashierSettlementIdAndIsDeletedFalse(cashierSettlementId);
        bundle.setPreviewItems(this.toUnsettledPreviewItems(rows));
        List<PaymentReconciliationQueryModel> paymentModels = new ArrayList<>();
        for (PaymentReconciliation payment : rows) {
            // 跳过空行，避免 NPE
            if (payment == null) {
                continue;
            }
            paymentModels.add(this.toQueryModel(payment));
        }
        this.attachYbSettlements(paymentModels);
        bundle.setPaymentModels(paymentModels);
        return bundle;
    }

    /**
     * 交账业务日窗内出现过流水的 enterer_id 去重列表（不按收款人过滤；用于默认勾选候选）。
     *
     * @param query 交账类型、科室/location 范围与业务日窗
     * @return enterer_id 列表（已去重、去空）
     * @throws BusinessException 查询条件非法
     */
    public List<Long> listDistinctEntererIdsInCashierSettlementWindow(PreviewCashierSettlementQuery query) {
        this.assertCashierSettlementPreviewQueryValid(query);
        if (this.isLocationScopeExhausted(query.getLocationIds())) {
            return List.of();
        }
        Specification<PaymentReconciliation> specification =
            this.buildCashierSettlementScopeSpecificationWithoutEnterer(query, false);
        List<PaymentReconciliation> rows = this.paymentRepository.findAll(specification);
        LinkedHashSet<Long> entererIds = new LinkedHashSet<>();
        for (PaymentReconciliation row : rows) {
            if (row != null && row.getEntererId() != null) {
                entererIds.add(row.getEntererId());
            }
        }
        return new ArrayList<>(entererIds);
    }

    /**
     * 漏交硬栏：截止前（{@code billDate <= dateTo}）且早于当前窗起点（{@code billDate < dateFrom}）的最早未交账业务时刻。
     *
     * <p>口径与候选相同 kind/location 范围，<strong>不含</strong> enterer 软筛（DD §2.6.5）。</p>
     *
     * @param query 交账类型、org/location 范围与业务日窗
     * @return 最早 effective bill 时刻；无漏交时 {@code null}
     * @throws BusinessException 查询条件非法
     */
    public LocalDateTime findEarliestUnsettledPaymentBillTimeBeforeWindow(PreviewCashierSettlementQuery query) {
        this.assertCashierSettlementPreviewQueryValid(query);
        if (this.isLocationScopeExhausted(query.getLocationIds())) {
            return null;
        }
        CashierSettlementType settlementType = this.resolveCashierSettlementType(query.getTypeEnum());
        LocalDateTime dateFrom = query.getDateFrom();
        LocalDateTime dateTo = query.getDateTo();
        List<PaymentStatus> allowedStatuses = Arrays.asList(PaymentStatus.SUCCESS, PaymentStatus.REFUNDED);
        Specification<PaymentReconciliation> specification = (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.isNull(root.get(FieldNameConstants.CASHIER_SETTLEMENT_ID)));
            this.addCashierSettlementKindPredicate(predicates, cb, root, settlementType);
            this.addExcludePhysicalExamEncounterPredicate(predicates, cb, root, criteriaQuery, settlementType);
            predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(allowedStatuses));
            this.addLocationIdsPredicate(predicates, cb, root, query.getLocationIds());
            jakarta.persistence.criteria.Expression<LocalDateTime> effectiveBillDate =
                this.effectiveBillDateExpression(cb, root);
            predicates.add(cb.lessThan(effectiveBillDate, dateFrom));
            predicates.add(cb.lessThanOrEqualTo(effectiveBillDate, dateTo));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<PaymentReconciliation> rows = this.paymentRepository.findAll(specification);
        LocalDateTime earliest = null;
        for (PaymentReconciliation row : rows) {
            LocalDateTime billTime = this.resolveEffectiveBillDate(row);
            if (billTime == null) {
                continue;
            }
            if (earliest == null || billTime.isBefore(earliest)) {
                earliest = billTime;
            }
        }
        return earliest;
    }

    /**
     * 漏交提醒：业务时间晚于 {@code dateTo} 的未交账 payment 条数（同 kind/location 范围，不含 enterer 软筛）。
     *
     * @param query 交账类型、org/location 范围与业务日窗
     * @return 条数
     * @throws BusinessException 查询条件非法
     */
    public long countUnsettledPaymentsAfterWindow(PreviewCashierSettlementQuery query) {
        this.assertCashierSettlementPreviewQueryValid(query);
        if (this.isLocationScopeExhausted(query.getLocationIds())) {
            return 0L;
        }
        CashierSettlementType settlementType = this.resolveCashierSettlementType(query.getTypeEnum());
        LocalDateTime dateTo = query.getDateTo();
        List<PaymentStatus> allowedStatuses = Arrays.asList(PaymentStatus.SUCCESS, PaymentStatus.REFUNDED);
        Specification<PaymentReconciliation> specification = (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.isNull(root.get(FieldNameConstants.CASHIER_SETTLEMENT_ID)));
            this.addCashierSettlementKindPredicate(predicates, cb, root, settlementType);
            this.addExcludePhysicalExamEncounterPredicate(predicates, cb, root, criteriaQuery, settlementType);
            predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(allowedStatuses));
            this.addLocationIdsPredicate(predicates, cb, root, query.getLocationIds());
            jakarta.persistence.criteria.Expression<LocalDateTime> effectiveBillDate =
                this.effectiveBillDateExpression(cb, root);
            predicates.add(cb.greaterThan(effectiveBillDate, dateTo));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return this.paymentRepository.count(specification);
    }

    /**
     * 交账日窗筛选（不含收款人 enterer 条件）。
     */
    private Specification<PaymentReconciliation> buildCashierSettlementScopeSpecificationWithoutEnterer(
        PreviewCashierSettlementQuery query,
        boolean unsettledOnly) {
        CashierSettlementType settlementType = this.resolveCashierSettlementType(query.getTypeEnum());
        LocalDateTime windowStart = query.getDateFrom();
        LocalDateTime windowEndExclusive = query.getDateTo().plusSeconds(1);
        List<PaymentStatus> allowedStatuses = Arrays.asList(PaymentStatus.SUCCESS, PaymentStatus.REFUNDED);
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            if (unsettledOnly) {
                predicates.add(cb.isNull(root.get(FieldNameConstants.CASHIER_SETTLEMENT_ID)));
            }
            this.addCashierSettlementKindPredicate(predicates, cb, root, settlementType);
            this.addExcludePhysicalExamEncounterPredicate(predicates, cb, root, criteriaQuery, settlementType);
            predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(allowedStatuses));
            this.addLocationIdsPredicate(predicates, cb, root, query.getLocationIds());
            this.addBillDateWindowPredicate(predicates, cb, root, windowStart, windowEndExclusive);
            return cb.and(predicates.toArray(new Predicate[0]));
        };
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
        // Pre：复用分批明细加载，禁止超长 reconciliationId IN
        Map<Long, List<PaymentRecDetail>> detailsByPaymentId =
            this.loadPaymentRecDetailsGroupedByPaymentId(paymentIds);
        for (Map.Entry<Long, List<PaymentRecDetail>> entry : detailsByPaymentId.entrySet()) {
            List<PaymentRecDetail> details = entry.getValue();
            if (details == null || details.isEmpty()) {
                continue;
            }
            for (PaymentRecDetail detail : details) {
                if (detail == null || StringUtils.isEmpty(detail.getPayEnum())) {
                    continue;
                }
                payEnumByPaymentId.put(entry.getKey(), detail.getPayEnum().trim());
                break;
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
        // Pre：复用分批明细加载，禁止超长 reconciliationId IN
        Map<Long, List<PaymentRecDetail>> detailsByPaymentId =
            this.loadPaymentRecDetailsGroupedByPaymentId(paymentIds);
        for (Map.Entry<Long, List<PaymentRecDetail>> entry : detailsByPaymentId.entrySet()) {
            List<PaymentRecDetail> details = entry.getValue();
            if (details == null) {
                continue;
            }
            for (PaymentRecDetail detail : details) {
                if (detail == null || StringUtils.isEmpty(detail.getPayEnum())) {
                    continue;
                }
                codesByPaymentId
                    .computeIfAbsent(entry.getKey(), ignored -> new LinkedHashSet<>())
                    .add(detail.getPayEnum().trim());
            }
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
        if (paymentId == null) {
            return List.of();
        }
        Map<Long, List<PaymentRecDetailQueryModel>> grouped =
            this.mapPaymentRecDetailsByPaymentIds(List.of(paymentId));
        List<PaymentRecDetailQueryModel> details = grouped.get(paymentId);
        return details == null ? List.of() : details;
    }

    /**
     * 按 payment 主键集合批量加载渠道明细读模型（日结/交账现算；分批 IN，避免 N+1）。
     *
     * @param paymentIds 支付头主键集合
     * @return paymentId → 明细列表（无明细的 id 映射空列表）
     */
    public Map<Long, List<PaymentRecDetailQueryModel>> mapPaymentRecDetailsByPaymentIds(
        Collection<Long> paymentIds) {
        Map<Long, List<PaymentRecDetailQueryModel>> result = new LinkedHashMap<>();
        if (paymentIds == null || paymentIds.isEmpty()) {
            return result;
        }
        Set<Long> distinctIds = new LinkedHashSet<>();
        for (Long paymentId : paymentIds) {
            if (paymentId != null) {
                distinctIds.add(paymentId);
                result.put(paymentId, new ArrayList<>());
            }
        }
        if (distinctIds.isEmpty()) {
            return result;
        }
        Map<Long, List<PaymentRecDetail>> entityGrouped =
            this.loadPaymentRecDetailsGroupedByPaymentId(distinctIds);
        for (Map.Entry<Long, List<PaymentRecDetail>> entry : entityGrouped.entrySet()) {
            List<PaymentRecDetailQueryModel> models = new ArrayList<>();
            if (entry.getValue() != null) {
                for (PaymentRecDetail detail : entry.getValue()) {
                    PaymentRecDetailQueryModel item = this.toPaymentRecDetailQueryModel(detail);
                    if (item != null) {
                        models.add(item);
                    }
                }
            }
            result.put(entry.getKey(), models);
        }
        return result;
    }

    /**
     * 将支付明细实体转为读模型。
     *
     * @param detail 明细实体
     * @return 读模型；实体空时 null
     */
    private PaymentRecDetailQueryModel toPaymentRecDetailQueryModel(PaymentRecDetail detail) {
        if (detail == null) {
            return null;
        }
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
        return item;
    }

    /**
     * 按支付主键批量加载读模型（日结提交头表金额快照等）。
     *
     * <p>仅本聚合 {@code PaymentReconciliationRepository}。</p>
     *
     * @param paymentIds 支付主键；可空
     * @return 读模型列表（保序近似按入参去重后的库序）；空入参返回空列表
     */
    public List<PaymentReconciliationQueryModel> findPaymentQueryModelsByIds(Collection<Long> paymentIds) {
        List<PaymentReconciliationQueryModel> result = new ArrayList<>();
        if (paymentIds == null || paymentIds.isEmpty()) {
            return result;
        }
        LinkedHashSet<Long> idSet = new LinkedHashSet<>();
        for (Long paymentId : paymentIds) {
            if (paymentId != null) {
                idSet.add(paymentId);
            }
        }
        if (idSet.isEmpty()) {
            return result;
        }
        List<PaymentReconciliation> rows = this.paymentRepository.findByIdIn(idSet);
        for (PaymentReconciliation payment : rows) {
            if (payment == null || payment.getId() == null) {
                continue;
            }
            result.add(this.toQueryModel(payment));
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
        // Pre：fin_payment_rec_static reconciliationId IN 分批
        List<PaymentRecStatic> staticRows = IdInQueryChunkSupport.loadByIdInChunks(
            paymentIds,
            chunkIds -> this.recStaticRepository.findAllByReconciliationIdInAndIsDeletedFalse(chunkIds));
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
        // Pre：一次批量拉取各就诊支付头，内存分组判定，避免逐就诊 findPaymentsByEncounter N+1
        List<PaymentReconciliation> paymentRows = paymentRepository.findByEncounterIdIn(distinctIds);
        Map<Long, List<PaymentReconciliationQueryModel>> paymentsByEncounterId = new HashMap<>();
        for (PaymentReconciliation payment : paymentRows) {
            if (payment == null || payment.getEncounterId() == null) {
                continue;
            }
            if (Boolean.TRUE.equals(payment.getIsDeleted())) {
                continue;
            }
            paymentsByEncounterId
                .computeIfAbsent(payment.getEncounterId(), ignored -> new ArrayList<>())
                .add(this.toQueryModel(payment));
        }
        Set<Long> matched = new LinkedHashSet<>();
        for (Long encounterId : distinctIds) {
            List<PaymentReconciliationQueryModel> payments = paymentsByEncounterId.get(encounterId);
            if (this.isReversedPendingResettleEncounter(payments)) {
                matched.add(encounterId);
            }
        }
        return matched;
    }

    /**
     * 按已加载的支付流水判定是否处于反结后待重新结算展示态。
     *
     * @param payments 该就诊支付头读模型；null 或空视为非待重结
     * @return true 表示存在已收口逆向反结单且正向 SUCCESS 条数未超过逆向条数
     */
    private boolean isReversedPendingResettleEncounter(List<PaymentReconciliationQueryModel> payments) {
        if (payments == null || payments.isEmpty()) {
            return false;
        }
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

    /**
     * 支付聚合根到读模型全量字段拷贝（含已预载医保结算子表）。
     *
     * @param payment 实体
     * @param ybRows  已关联的医保结算行；可空
     * @return 读模型
     */
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

    /**
     * 医保结算子表实体列表 → 读模型列表。
     *
     * @param rows 子表实体；空则返回空列表
     * @return 医保结算读模型列表
     */
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

    /**
     * 批量挂载支付读模型上的医保结算子表（按 reconciliationId 分组）。
     *
     * @param models 待 enrich 的支付读模型列表
     */
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
        List<Long> idList = new ArrayList<>(paymentIds);
        int chunkSize = GeneralSharedConstants.ID_IN_QUERY_CHUNK_SIZE;
        List<PaymentYbSettlement> rows = new ArrayList<>();
        // Pre：payment 量大时分批 IN，避免单次超长
        for (int fromIndex = 0; fromIndex < idList.size(); fromIndex += chunkSize) {
            int toIndex = Math.min(fromIndex + chunkSize, idList.size());
            rows.addAll(
                ybSettlementRepository
                    .findAllByReconciliationIdInAndIsDeletedFalseOrderByReconciliationIdAscCreatedAtAsc(
                        idList.subList(fromIndex, toIndex)));
        }
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

    /**
     * 支付实体列表按 bill_date 降序排序（空 bill_date 排末尾）。
     *
     * @param rows 待排序列表（原地修改）
     */
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

    /**
     * 未交账支付实体列表 → 日结/交账预览行（批量解析逆向单原单金额，避免 N+1）。
     *
     * @param rows 支付实体列表
     * @return 预览行列表
     */
    private List<UnsettledPaymentPreviewItemQueryModel> toUnsettledPreviewItems(List<PaymentReconciliation> rows) {
        List<UnsettledPaymentPreviewItemQueryModel> items = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return items;
        }
        Map<Long, PaymentReconciliation> originalById = this.loadOriginalPaymentsForReverseNet(rows);
        for (PaymentReconciliation row : rows) {
            items.add(this.toUnsettledPreviewItem(row, originalById));
        }
        return items;
    }

    /**
     * 批量加载逆向退费单关联的原支付，供净额与原单患者应付对齐（BR-OP-DCL-011）。
     *
     * @param rows 候选支付
     * @return relationId → 原支付实体
     */
    private Map<Long, PaymentReconciliation> loadOriginalPaymentsForReverseNet(List<PaymentReconciliation> rows) {
        Set<Long> relationIds = new LinkedHashSet<>();
        for (PaymentReconciliation row : rows) {
            if (row == null || row.getRelationId() == null) {
                continue;
            }
            if (this.isReverseRefundPayment(row)) {
                relationIds.add(row.getRelationId());
            }
        }
        if (relationIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, PaymentReconciliation> originalById = new HashMap<>();
        for (PaymentReconciliation original : this.paymentRepository.findByIdIn(relationIds)) {
            if (original != null && original.getId() != null) {
                originalById.put(original.getId(), original);
            }
        }
        return originalById;
    }

    /**
     * 未交账支付实体 → 日结/交账预览行读模型。
     *
     * @param row 支付实体
     * @param originalById 逆向单 relationId → 原支付（可空 map）
     * @return 预览行
     */
    private UnsettledPaymentPreviewItemQueryModel toUnsettledPreviewItem(
        PaymentReconciliation row,
        Map<Long, PaymentReconciliation> originalById) {
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
        item.setNetAmount(this.computeUnsettledPreviewNetAmount(row, originalById));
        item.setContractNo(row.getContractNo());
        return item;
    }

    /**
     * 计算未交账预览净额：正向为实收减退回；逆向取负时与原单患者应付（display/tendered）对齐，避免医保全报
     * 原单 0 元、逆向误写渠道合计导致日结净额成对抵不平（BR-OP-DCL-011）。
     *
     * @param row 支付实体
     * @param originalById 逆向单 relationId → 原支付（可空）
     * @return 预览净额
     */
    private BigDecimal computeUnsettledPreviewNetAmount(
        PaymentReconciliation row,
        Map<Long, PaymentReconciliation> originalById) {
        if (this.isReverseRefundPayment(row)) {
            BigDecimal amount = this.resolveReversePreviewAmountBasis(row, originalById);
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

    /**
     * 逆向退费净额基数：优先原单 display/tendered（与正向日结口径一致），否则回落本单 display/tendered。
     *
     * @param reverse 逆向支付
     * @param originalById relationId → 原支付
     * @return 金额基数；可能为 null
     */
    private BigDecimal resolveReversePreviewAmountBasis(
        PaymentReconciliation reverse,
        Map<Long, PaymentReconciliation> originalById) {
        if (reverse != null && reverse.getRelationId() != null && originalById != null) {
            PaymentReconciliation original = originalById.get(reverse.getRelationId());
            if (original != null) {
                BigDecimal originalAmount = original.getDisplayAmount();
                if (originalAmount == null) {
                    originalAmount = original.getTenderedAmount();
                }
                if (originalAmount != null) {
                    return originalAmount;
                }
            }
        }
        if (reverse == null) {
            return null;
        }
        BigDecimal amount = reverse.getDisplayAmount();
        if (amount == null) {
            amount = reverse.getTenderedAmount();
        }
        return amount;
    }

    /**
     * 是否冲正退费支付（日结净额口径须单独处理）。
     *
     * @param row 支付实体
     * @return 冲正退费时 true
     */
    private boolean isReverseRefundPayment(PaymentReconciliation row) {
        if (row == null) {
            return false;
        }
        return DailySettlementRefundPaymentRules.isRefundPaymentForSettlement(
            row.getPaymentEnum(),
            row.getRelationId(),
            row.getStatusEnum());
    }

    /**
     * 交账预览入参 Gate：收款 org、业务日窗与交账类型合法性；enterer 为可选软筛。
     *
     * @param query 交账预览查询
     * @throws BusinessException 参数缺失或不合法时
     */
    private void assertCashierSettlementPreviewQueryValid(PreviewCashierSettlementQuery query) {
        if (query == null) {
            throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT, "交账预览条件不能为空");
        }
        this.resolveCashierSettlementType(query.getTypeEnum());
        if (CashierSettlementType.isPaymentType(query.getTypeEnum()) && query.getOrgId() == null) {
            throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT, "收款交账须指定科室");
        }
        LocalDateTime dateFrom = query.getDateFrom();
        LocalDateTime dateTo = query.getDateTo();
        if (dateFrom == null || dateTo == null) {
            throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT, "交账业务日范围不能为空");
        }
        if (dateTo.isBefore(dateFrom)) {
            throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT, "交账业务日范围不合法");
        }
    }

    /**
     * 交账候选/日结展示共用筛选：BR-CS-001、003～006；{@code unsettledOnly=true} 时追加 BR-CS-002。
     *
     * @param query         交账类型、科室/location 范围、可选 enterer 软筛与业务日窗
     * @param unsettledOnly 是否仅未交账（提交挂载与 paymentCount 须为 true）
     * @return JPA Specification
     */
    private Specification<PaymentReconciliation> buildCashierSettlementScopeSpecification(
        PreviewCashierSettlementQuery query,
        boolean unsettledOnly) {
        CashierSettlementType settlementType = this.resolveCashierSettlementType(query.getTypeEnum());
        // 日结时间止按秒闭区间：endExclusive = dateTo + 1s
        LocalDateTime windowStart = query.getDateFrom();
        LocalDateTime windowEndExclusive = query.getDateTo().plusSeconds(1);
        List<PaymentStatus> allowedStatuses = Arrays.asList(PaymentStatus.SUCCESS, PaymentStatus.REFUNDED);
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            if (unsettledOnly) {
                // BR-CS-002：仅未交账流水进入提交挂载与 paymentCount
                predicates.add(cb.isNull(root.get(FieldNameConstants.CASHIER_SETTLEMENT_ID)));
            }
            this.addCashierSettlementKindPredicate(predicates, cb, root, settlementType);
            this.addExcludePhysicalExamEncounterPredicate(predicates, cb, root, criteriaQuery, settlementType);
            predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(allowedStatuses));
            this.addLocationIdsPredicate(predicates, cb, root, query.getLocationIds());
            this.addEntererMatchPredicate(predicates, root, query.getEntererMatchIds());
            this.addBillDateWindowPredicate(predicates, cb, root, windowStart, windowEndExclusive);
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 交账候选 bill_date 窗口：优先 bill_date，空值回落 created_at（存量预交金可能未写 bill_date）。
     *
     * @param predicates           条件列表（追加）
     * @param cb                   CriteriaBuilder
     * @param root                 payment 根
     * @param windowStartInclusive 起始时刻（含）
     * @param windowEndExclusive   结束时刻（不含）
     */
    private void addBillDateWindowPredicate(
        List<Predicate> predicates,
        CriteriaBuilder cb,
        jakarta.persistence.criteria.Root<PaymentReconciliation> root,
        LocalDateTime windowStartInclusive,
        LocalDateTime windowEndExclusive) {
        jakarta.persistence.criteria.Expression<LocalDateTime> effectiveBillDate = cb.coalesce(
            root.get(FieldNameConstants.BILL_DATE),
            root.get(FieldNameConstants.CREATED_AT));
        predicates.add(cb.greaterThanOrEqualTo(effectiveBillDate, windowStartInclusive));
        predicates.add(cb.lessThan(effectiveBillDate, windowEndExclusive));
    }

    /**
     * 交账候选 {@code kind_enum} 过滤：住院须含 {@link PaymentKind#HOSPITAL_DEPOSIT} 与历史 {@code PRE_DEPOSIT} 落库码。
     *
     * <p>须逐条 {@code cb.equal} 匹配 {@code @Convert} 枚举字段；禁止整段 {@code as(String).in(...)}，
     * 否则 Hibernate 可能无法正确下推 SQL，导致预交金候选漏查。</p>
     *
     * @param predicates     条件列表（追加）
     * @param cb             CriteriaBuilder
     * @param root           payment 根
     * @param settlementType 交账类型
     */
    private void addCashierSettlementKindPredicate(
        List<Predicate> predicates,
        CriteriaBuilder cb,
        jakarta.persistence.criteria.Root<PaymentReconciliation> root,
        CashierSettlementType settlementType) {
        jakarta.persistence.criteria.Path<PaymentKind> kindPath = root.get(FieldNameConstants.KIND_ENUM);
        if (CashierSettlementType.OP_PAYMENT.equals(settlementType)) {
            predicates.add(cb.equal(kindPath, PaymentKind.OUTPATIENT_CLINIC));
            return;
        }
        if (CashierSettlementType.IP_PAYMENT.equals(settlementType)) {
            Predicate inpatientClinic = cb.equal(kindPath, PaymentKind.INPATIENT_CLINIC);
            Predicate hospitalDeposit = cb.equal(kindPath, PaymentKind.HOSPITAL_DEPOSIT);
            Predicate legacyPreDeposit = cb.equal(
                kindPath.as(String.class),
                LEGACY_PRE_DEPOSIT_KIND_CODE);
            predicates.add(cb.or(inpatientClinic, hospitalDeposit, legacyPreDeposit));
            return;
        }
        throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT, "交账类型不适用于支付流水候选");
    }

    /**
     * 门/住院收款日结排除体检就诊：个检 payment 的 {@code kind_enum} 落门诊收费，
     * 仅按 kind 过滤会把体检费用算进日结；无 encounter 的预交金保留。
     *
     * @param predicates     条件列表（追加）
     * @param cb             CriteriaBuilder
     * @param root           payment 根
     * @param criteriaQuery  当前查询（供 EXISTS 子查询）
     * @param settlementType 交账类型
     */
    private void addExcludePhysicalExamEncounterPredicate(
        List<Predicate> predicates,
        CriteriaBuilder cb,
        jakarta.persistence.criteria.Root<PaymentReconciliation> root,
        jakarta.persistence.criteria.CriteriaQuery<?> criteriaQuery,
        CashierSettlementType settlementType) {
        if (criteriaQuery == null) {
            return;
        }
        if (!CashierSettlementType.OP_PAYMENT.equals(settlementType)
            && !CashierSettlementType.IP_PAYMENT.equals(settlementType)) {
            return;
        }
        jakarta.persistence.criteria.Subquery<Long> peEncounter = criteriaQuery.subquery(Long.class);
        jakarta.persistence.criteria.Root<Encounter> encounterRoot = peEncounter.from(Encounter.class);
        peEncounter.select(encounterRoot.get(FieldNameConstants.ID));
        peEncounter.where(
            cb.equal(encounterRoot.get(FieldNameConstants.ID), root.get(FieldNameConstants.ENCOUNTER_ID)),
            cb.equal(encounterRoot.get(FieldNameConstants.CLASS_ENUM), EncounterClass.PE.getCode()));
        predicates.add(cb.not(cb.exists(peEncounter)));
    }

    /**
     * 交账类型 code → 枚举；非法或空则抛业务异常。
     *
     * @param typeEnum 交账类型 code
     * @return 交账类型枚举
     * @throws BusinessException 类型为空或不合法时
     */
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
        // Pre：门诊费用明细仅纳入 OUTPATIENT_CLINIC 收退成功单
        return this.listFeeDetailChargeItemIdsByPaymentFilter(query, PaymentKind.OUTPATIENT_CLINIC);
    }

    /**
     * 住院费用明细：按 payment {@code bill_date}/收费人/收退 Tab 解析候选 chargeItem 主键（AS-RPT-009）。
     *
     * <p>与 {@link #listOutpatientFeeDetailChargeItemIdsByPaymentFilter} 同构，kind 收窄为
     * {@link PaymentKind#INPATIENT_CLINIC}；同步写入 query 内 payment 读上下文供 ChargeItem 报表读侧复用。</p>
     *
     * @param query 须含 {@code billDateFrom}/{@code billDateTo}；可选 {@code cashierId}、{@code tradeDirection}
     * @return 去重后的 chargeItem 主键；无命中时空列表
     * @throws IllegalArgumentException 日期缺失或 {@code billDateFrom > billDateTo}
     */
    public List<Long> listInpatientFeeDetailChargeItemIdsByPaymentFilter(OutpatientFeeDetailPageQuery query) {
        // Pre：住院费用明细仅纳入 INPATIENT_CLINIC 出院结算收退单（排除预交金）
        return this.listFeeDetailChargeItemIdsByPaymentFilter(query, PaymentKind.INPATIENT_CLINIC);
    }

    /**
     * 门/住院费用明细共用：按 payment kind + bill_date 窗解析 chargeItem 快照并写入读上下文。
     *
     * @param query      须含日期窗
     * @param paymentKind 门诊 {@link PaymentKind#OUTPATIENT_CLINIC} 或住院 {@link PaymentKind#INPATIENT_CLINIC}
     * @return 去重 chargeItem 主键
     */
    private List<Long> listFeeDetailChargeItemIdsByPaymentFilter(
        OutpatientFeeDetailPageQuery query,
        PaymentKind paymentKind) {
        if (query == null || query.getBillDateFrom() == null || query.getBillDateTo() == null) {
            throw new IllegalArgumentException("fee detail payment filter requires billDateFrom and billDateTo");
        }
        if (query.getBillDateFrom().isAfter(query.getBillDateTo())) {
            throw new IllegalArgumentException("billDateFrom must not be after billDateTo");
        }
        if (paymentKind == null) {
            throw new IllegalArgumentException("paymentKind is required");
        }
        Specification<PaymentReconciliation> specification =
            this.buildFeeDetailPaymentSpecification(query, paymentKind);
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
        boolean skipYbFund = Boolean.TRUE.equals(query.getSkipYbFundAmountAttach());
        Map<Long, List<PaymentRecDetail>> detailsByPaymentId = Map.of();
        // Pre：汇总不读医保分摊列时跳过 YB/明细挂载；明细页批量预取 PaymentRecDetail，禁止按 payment N+1
        if (!skipYbFund) {
            this.attachYbSettlements(models);
            LinkedHashSet<Long> paymentIdsForDetails = new LinkedHashSet<>();
            for (PaymentReconciliation payment : payments) {
                if (payment != null && payment.getId() != null) {
                    paymentIdsForDetails.add(payment.getId());
                }
            }
            detailsByPaymentId = this.loadPaymentRecDetailsGroupedByPaymentId(paymentIdsForDetails);
        }
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
            BigDecimal ybFundAndAcctTotal = BigDecimal.ZERO;
            if (!skipYbFund) {
                ybFundAndAcctTotal = this.resolveOutpatientFeeDetailYbFundAndAcctTotal(
                    payment, paymentModel, detailsByPaymentId.get(payment.getId()));
            }
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
     * 科室/医生工作量：按 payment {@code bill_date} 与门诊/住院 kind 解析候选 chargeItem（DM-FIN-002 §6.2.19；AS-RPT-001）。
     *
     * @param query 须含 {@code billDateFrom}/{@code billDateTo}、{@code encounterScope}
     * @return 去重 chargeItem 主键；无命中时空列表；并写入 query payment 读上下文
     * @throws IllegalArgumentException 日期窗非法
     */
    public List<Long> listPractitionerWorkloadChargeItemIdsByPaymentFilter(PractitionerWorkloadPageQuery query) {
        if (query == null || query.getBillDateFrom() == null || query.getBillDateTo() == null) {
            throw new IllegalArgumentException("practitioner workload payment filter requires billDateFrom and billDateTo");
        }
        if (query.getBillDateFrom().isAfter(query.getBillDateTo())) {
            throw new IllegalArgumentException("billDateFrom must not be after billDateTo");
        }
        Specification<PaymentReconciliation> specification = this.buildPractitionerWorkloadPaymentSpecification(query);
        List<PaymentReconciliation> payments = paymentRepository.findAll(specification);
        if (payments.isEmpty()) {
            query.setPaymentContextByChargeItemId(Collections.emptyMap());
            query.setPaymentChargeItemLinks(Collections.emptyList());
            query.setResolvedPaymentChargeItemLinks(Collections.emptyList());
            query.setPaymentsInScope(Collections.emptyList());
            return Collections.emptyList();
        }
        this.sortByBillDateDesc(payments);
        Map<Long, OutpatientFeeDetailPaymentContextQueryModel> contextByChargeItemId = new LinkedHashMap<>();
        LinkedHashSet<Long> chargeItemIds = new LinkedHashSet<>();
        List<OutpatientFeeDetailPaymentScopeQueryModel> paymentsInScope = new ArrayList<>();
        List<OutpatientFeeDetailChargePaymentLinkQueryModel> paymentChargeItemLinks = new ArrayList<>();
        for (PaymentReconciliation payment : payments) {
            if (payment == null || payment.getId() == null) {
                continue;
            }
            OutpatientFeeDetailPaymentScopeQueryModel paymentScope = new OutpatientFeeDetailPaymentScopeQueryModel();
            paymentScope.setPaymentId(payment.getId());
            paymentScope.setEncounterId(payment.getEncounterId());
            paymentScope.setBillDate(payment.getBillDate());
            paymentScope.setCashierId(payment.getEntererId());
            paymentScope.setRefundPayment(PaymentStatus.REFUNDED.equals(payment.getStatusEnum()));
            paymentScope.setChargeItemIdsSnapshot(payment.getChargeItemIds());
            paymentsInScope.add(paymentScope);
            // 展开 charge_item_ids 快照；与门诊费用明细读侧同构
            for (Long chargeItemId : this.parseIds(payment.getChargeItemIds())) {
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
                contextByChargeItemId.put(chargeItemId, context);
                OutpatientFeeDetailChargePaymentLinkQueryModel link = new OutpatientFeeDetailChargePaymentLinkQueryModel();
                link.setChargeItemId(chargeItemId);
                link.setPaymentContext(context);
                paymentChargeItemLinks.add(link);
            }
        }
        query.setPaymentContextByChargeItemId(contextByChargeItemId);
        query.setPaymentChargeItemLinks(paymentChargeItemLinks);
        query.setResolvedPaymentChargeItemLinks(paymentChargeItemLinks);
        query.setPaymentsInScope(paymentsInScope);
        return new ArrayList<>(chargeItemIds);
    }

    /**
     * 工作量 payment 筛选规格：bill_date 闭区间 + kind + SUCCESS/REFUNDED（BR-RPT-WKL-002）。
     *
     * <p>若 query 已写入 {@code resolvedEncounterScope}，叠加 {@code encounter_id IN}，
     * 与内存二次收窄同口径，减少日窗内无关流水加载。</p>
     */
    private Specification<PaymentReconciliation> buildPractitionerWorkloadPaymentSpecification(
        PractitionerWorkloadPageQuery query) {
        LocalDateTime windowStart = query.getBillDateFrom().atStartOfDay();
        LocalDateTime windowEndExclusive = query.getBillDateTo().plusDays(1).atStartOfDay();
        List<PaymentKind> allowedKinds = this.resolvePractitionerWorkloadPaymentKinds(query.getEncounterScope());
        List<PaymentStatus> allowedStatuses = Arrays.asList(PaymentStatus.SUCCESS, PaymentStatus.REFUNDED);
        List<Long> resolvedEncounterScope = query.getResolvedEncounterScope();
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(root.get(FieldNameConstants.KIND_ENUM).in(allowedKinds));
            predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(allowedStatuses));
            predicates.add(cb.greaterThanOrEqualTo(root.get(FieldNameConstants.BILL_DATE), windowStart));
            predicates.add(cb.lessThan(root.get(FieldNameConstants.BILL_DATE), windowEndExclusive));
            // Pre：就诊 scope 已解析时下推 SQL，与 BR-RPT-WKL-003 内存过滤一致
            if (resolvedEncounterScope != null && !resolvedEncounterScope.isEmpty()) {
                predicates.add(root.get(FieldNameConstants.ENCOUNTER_ID).in(resolvedEncounterScope));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 医师工作量报表：按就诊范围解析纳入统计的 payment kind 集合。
     *
     * @param encounterScope 就诊范围（门诊/住院）
     * @return 允许的 PaymentKind 列表
     */
    private List<PaymentKind> resolvePractitionerWorkloadPaymentKinds(String encounterScope) {
        if (PractitionerWorkloadConstants.ENCOUNTER_SCOPE_INPATIENT.equals(encounterScope)) {
            return Arrays.asList(PaymentKind.INPATIENT_CLINIC, PaymentKind.HOSPITAL_DEPOSIT);
        }
        return Collections.singletonList(PaymentKind.OUTPATIENT_CLINIC);
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
     * 组装费用明细 payment 筛选 Specification（bill_date 左闭右开日窗 + kind + 收退 Tab）。
     *
     * @param query       日期/收费人/收退 Tab
     * @param paymentKind 门诊或住院 clinic kind
     * @return Specification
     */
    private Specification<PaymentReconciliation> buildFeeDetailPaymentSpecification(
        OutpatientFeeDetailPageQuery query,
        PaymentKind paymentKind) {
        // 应用层已把日/时刻规范为闭区间；开区间上界 +1 秒与全日窗次日 00:00:00 同口径
        LocalDateTime windowStart = query.getBillDateFrom();
        LocalDateTime windowEndExclusive = query.getBillDateTo().plusSeconds(1);
        List<PaymentStatus> allowedStatuses = this.resolveOutpatientFeeDetailPaymentStatuses(query.getTradeDirection());
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.KIND_ENUM), paymentKind));
            predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(allowedStatuses));
            predicates.add(cb.greaterThanOrEqualTo(root.get(FieldNameConstants.BILL_DATE), windowStart));
            predicates.add(cb.lessThan(root.get(FieldNameConstants.BILL_DATE), windowEndExclusive));
            this.addEntererMatchPredicate(predicates, root, query.getEntererMatchIds());
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
     * <p>禁止从 {@code PaymentRecDetail.pay_enum} 推断医保（BR-OP-FDT-011）。
     * 自付明细须由调用方批量预取后传入，禁止本方法内按 payment 打库。</p>
     *
     * @param payment              支付头
     * @param paymentModel         已挂载 YB 的读模型
     * @param preloadedRecDetails  本 payment 的 PaymentRecDetail；可空
     * @return 医保基金+个账合计
     */
    private BigDecimal resolveOutpatientFeeDetailYbFundAndAcctTotal(
        PaymentReconciliation payment,
        PaymentReconciliationQueryModel paymentModel,
        List<PaymentRecDetail> preloadedRecDetails) {
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
        if (preloadedRecDetails != null) {
            for (PaymentRecDetail detail : preloadedRecDetails) {
                if (detail == null || detail.getAmount() == null) {
                    continue;
                }
                selfPayTotal = selfPayTotal.add(detail.getAmount());
            }
        }
        BigDecimal ybTotal = tendered.subtract(selfPayTotal);
        if (ybTotal.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return ybTotal;
    }

    /**
     * 按 payment 主键集合批量加载 PaymentRecDetail 并按 reconciliationId 分组（费用明细医保分摊）。
     *
     * @param paymentIds 支付头主键
     * @return paymentId → 明细行；无命中不含键
     */
    private Map<Long, List<PaymentRecDetail>> loadPaymentRecDetailsGroupedByPaymentId(
        Collection<Long> paymentIds) {
        Map<Long, List<PaymentRecDetail>> detailsByPaymentId = new HashMap<>();
        if (paymentIds == null || paymentIds.isEmpty()) {
            return detailsByPaymentId;
        }
        // Pre：reconciliationId IN 分批，避免费用明细大窗超时
        List<PaymentRecDetail> allDetails = IdInQueryChunkSupport.loadByIdInChunks(
            paymentIds,
            chunkIds -> detailRepository.findAllByReconciliationIdIn(chunkIds));
        for (PaymentRecDetail detail : allDetails) {
            if (detail == null || detail.getReconciliationId() == null) {
                continue;
            }
            detailsByPaymentId
                .computeIfAbsent(detail.getReconciliationId(), ignored -> new ArrayList<>())
                .add(detail);
        }
        return detailsByPaymentId;
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
     * 经办人/收费人软筛：{@code entererMatchIds} 非空时 IN 白名单；空则不加谓词。
     *
     * @param predicates       谓词列表
     * @param root             Payment 根
     * @param entererMatchIds  enterer_id 白名单；空表示不按人过滤
     */
    private void addEntererMatchPredicate(
        List<Predicate> predicates,
        jakarta.persistence.criteria.Root<PaymentReconciliation> root,
        List<Long> entererMatchIds) {
        if (entererMatchIds != null && !entererMatchIds.isEmpty()) {
            predicates.add(root.get(FieldNameConstants.ENTERER_ID).in(entererMatchIds));
        }
    }

    /**
     * 可选 location 白名单：{@code null} 不下推（收款日结口径）；空列表表示无地点、谓词恒 false。
     */
    private void addLocationIdsPredicate(
        List<Predicate> predicates,
        CriteriaBuilder cb,
        jakarta.persistence.criteria.Root<PaymentReconciliation> root,
        List<Long> locationIds) {
        if (locationIds == null) {
            return;
        }
        if (locationIds.isEmpty()) {
            // org 下无 location 时不应命中任何 payment
            predicates.add(cb.disjunction());
            return;
        }
        predicates.add(root.get(FieldNameConstants.LOCATION_ID).in(locationIds));
    }

    /**
     * locationIds 已解析但为空列表时，读侧可直接短路为空结果。
     */
    private boolean isLocationScopeExhausted(List<Long> locationIds) {
        return locationIds != null && locationIds.isEmpty();
    }

    /**
     * effective bill 时刻：优先 {@code bill_date}，空值回落 {@code created_at}（与窗谓词一致）。
     */
    private jakarta.persistence.criteria.Expression<LocalDateTime> effectiveBillDateExpression(
        CriteriaBuilder cb,
        jakarta.persistence.criteria.Root<PaymentReconciliation> root) {
        return cb.coalesce(
            root.get(FieldNameConstants.BILL_DATE),
            root.get(FieldNameConstants.CREATED_AT));
    }

    /**
     * 实体 effective bill 时刻（内存侧与 {@link #effectiveBillDateExpression} 同口径）。
     */
    private LocalDateTime resolveEffectiveBillDate(PaymentReconciliation row) {
        if (row == null) {
            return null;
        }
        LocalDateTime billDate = row.getBillDate();
        if (billDate != null) {
            return billDate;
        }
        return row.getCreatedAt();
    }

    /**
     * 按就诊批量映射住院出院结算净额（DM-FIN-002 §6.2.20；AS-RPT-010）。
     *
     * <p>仅 {@link PaymentKind#INPATIENT_CLINIC}：正向 {@code SUCCESS} 计入实收（tendered−returned）；
     * 逆向 {@code REFUNDED}（{@code relation_id} 非空）按绝对值取负冲减。净额为 0 的就诊不入 Map
     * （应用层展示为空，禁止写 0）。待重结判定见 {@link #filterReversedPendingResettleEncounterIds}。</p>
     *
     * @param encounterIds 就诊主键集合
     * @return encounterId → 结算刻度净额；无有效净额则缺省键
     */
    public Map<Long, BigDecimal> mapInpatientClinicSettledNetAmountByEncounterIds(
        Collection<Long> encounterIds) {
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        if (encounterIds == null || encounterIds.isEmpty()) {
            return result;
        }
        LinkedHashSet<Long> distinctIds = new LinkedHashSet<>();
        for (Long id : encounterIds) {
            if (id != null) {
                distinctIds.add(id);
            }
        }
        if (distinctIds.isEmpty()) {
            return result;
        }
        // Pre：一次批量拉支付头，按就诊累加净额（B.6.4 仅本聚合仓储）
        List<PaymentReconciliation> paymentRows = paymentRepository.findByEncounterIdIn(distinctIds);
        Map<Long, BigDecimal> sumByEncounter = new HashMap<>();
        for (PaymentReconciliation payment : paymentRows) {
            if (payment == null || payment.getEncounterId() == null) {
                continue;
            }
            if (Boolean.TRUE.equals(payment.getIsDeleted())) {
                continue;
            }
            if (!PaymentKind.INPATIENT_CLINIC.equals(payment.getKindEnum())) {
                continue;
            }
            BigDecimal delta = this.resolveInpatientClinicSettledNetDelta(payment);
            if (delta == null) {
                continue;
            }
            BigDecimal previous = sumByEncounter.get(payment.getEncounterId());
            if (previous == null) {
                previous = BigDecimal.ZERO;
            }
            sumByEncounter.put(payment.getEncounterId(), previous.add(delta));
        }
        for (Map.Entry<Long, BigDecimal> entry : sumByEncounter.entrySet()) {
            BigDecimal scaled = MonetaryAmountSupport.scaleSettle(entry.getValue());
            // 净 0 不入 Map，供应用层落空字段
            if (scaled.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            result.put(entry.getKey(), scaled);
        }
        return result;
    }

    /**
     * 单笔住院出院结算对入出明细净额的增量：正向加、逆向减；其它状态忽略。
     *
     * @param payment 支付头
     * @return 增量；不参与则 null
     */
    private BigDecimal resolveInpatientClinicSettledNetDelta(PaymentReconciliation payment) {
        if (payment.getRelationId() != null) {
            // 反结/退费逆向单：收口 REFUNDED 后冲减
            if (!PaymentStatus.REFUNDED.equals(payment.getStatusEnum())) {
                return null;
            }
            BigDecimal amount = payment.getTenderedAmount();
            if (amount == null) {
                amount = payment.getDisplayAmount();
            }
            if (amount == null) {
                return BigDecimal.ZERO;
            }
            return amount.abs().negate();
        }
        if (!PaymentStatus.SUCCESS.equals(payment.getStatusEnum())) {
            return null;
        }
        BigDecimal tendered = payment.getTenderedAmount();
        if (tendered == null) {
            tendered = BigDecimal.ZERO;
        }
        BigDecimal returned = payment.getReturnedAmount();
        if (returned == null) {
            returned = BigDecimal.ZERO;
        }
        return tendered.subtract(returned);
    }
}
