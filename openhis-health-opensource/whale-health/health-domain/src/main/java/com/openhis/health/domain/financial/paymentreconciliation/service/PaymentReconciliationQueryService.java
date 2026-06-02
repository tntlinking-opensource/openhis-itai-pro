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
import com.openhis.health.domain.financial.paymentreconciliation.entity.PaymentReconciliation;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.*;
import com.openhis.health.domain.financial.paymentreconciliation.repository.PaymentRecDetailRepository;
import com.openhis.health.domain.financial.paymentreconciliation.repository.PaymentReconciliationRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.PaymentStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 支付对账/支付流水聚合读侧查询服务。
 *
 * <p>职责：按就诊、患者、账单日、关联单等维度查询支付事实及明细，并承担退费存在性、冲销汇总等只读编排；不写库。</p>
 */
@Service
@RequiredArgsConstructor
public class PaymentReconciliationQueryService {
    private final PaymentReconciliationRepository paymentRepository;
    private final PaymentRecDetailRepository detailRepository;

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
        return toQueryModel(payment);
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
            result.add(toQueryModel(payment));
        }
        return result;
    }

    /**
     * 查询指定就诊下已成功或退款处理中、且流水挂载的收费项与给定集合存在交集的支付流水。
     *
     * <p>用于 IF-OPC-07 与 {@code BR-OPC-005}：允许同一就诊多轮结算未出现在历史成功链路中的收费项；
     * 仅当 {@code SUCCESS}/{@code REFUND_PROCESSING} 流水上的 {@code charge_item_ids} 与本次拟收 ID 有交集时认定防重命中。</p>
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
                return toQueryModel(item);
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
        return result;
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
        return result;
    }

    /**
     * 列出指定支付流水下的渠道明细（多账户、多支付介质时逐条展开）。
     *
     * @param paymentId 支付流水主键
     * @return 明细读模型列表
     */
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
     * 支付聚合根到读模型全量字段拷贝。
     *
     * @param payment 实体
     * @return 读模型
     */
    private PaymentReconciliationQueryModel toQueryModel(PaymentReconciliation payment) {
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
        model.setYbSettleIds(payment.getYbSettleIds());
        model.setYbClearFlag(payment.getYbClearFlag());
        model.setPrintCount(payment.getPrintCount());
        return model;
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
}

