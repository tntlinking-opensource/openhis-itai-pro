/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.practitionerworkload.assembler;

import com.openhis.health.application.report.practitionerworkload.dto.PractitionerWorkloadDetailLineDto;
import com.openhis.health.application.report.practitionerworkload.dto.PractitionerWorkloadFinTypeRowDto;
import com.openhis.health.application.report.practitionerworkload.dto.PractitionerWorkloadRegistrationRowDto;
import com.openhis.health.application.report.practitionerworkload.dto.PractitionerWorkloadSummaryTotalsDto;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.PractitionerWorkloadChargeRowQueryModel;
import com.openhis.health.domain.administration.chargeitem.query.PractitionerWorkloadFinTypeOrgSummaryQueryModel;
import com.openhis.health.domain.administration.chargeitem.query.PractitionerWorkloadOverviewSummaryQueryModel;
import com.openhis.health.domain.administration.chargeitem.query.PractitionerWorkloadPageQuery;
import com.openhis.health.domain.administration.chargeitem.query.PractitionerWorkloadRegistrationOrgSummaryQueryModel;
import com.openhis.health.domain.administration.chargeitem.query.PractitionerWorkloadRequestDetailQueryModel;
import com.openhis.health.domain.administration.healthcareservice.entity.HealthcareService;
import com.openhis.health.domain.financial.paymentreconciliation.entity.PaymentReconciliation;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.OutpatientFeeDetailPaymentContextQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.PractitionerWorkloadConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.domain.share.enums.PaymentStatus;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 科室/医生工作量金额列与跨 org 合并装配（AS-RPT-001 §1.3；BR-RPT-WKL-016；金额刻度 **BR-MON-001**）。
 *
 * <p>只读展示计算；禁止承载筛选或持久化逻辑。行级金额 6 位，页脚合计
 * {@link MonetaryAmountSupport#scaleSettle} 到 2 位。</p>
 */
@Component
@RequiredArgsConstructor
public class PractitionerWorkloadAssembler {

    private final PaymentReconciliationQueryService paymentReconciliationQueryService;

    /**
     * 按 fin_type_code 跨 org 合并汇总行（BR-RPT-WKL-016）。
     *
     * @param orgSummaries            领域 org 粒度汇总
     * @param finTypeCodeByDefinition definitionId → fin_type_code
     * @param costByOrgDefinitionKey  orgId:definitionId → 成本合计
     * @param finTypeDisplayByCode    fin_type 字典展示
     * @return 合并后的 fin_type 行
     */
    public List<PractitionerWorkloadFinTypeRowDto> mergeFinTypeRowsAcrossOrgs(
        List<PractitionerWorkloadFinTypeOrgSummaryQueryModel> orgSummaries,
        Map<Long, String> finTypeCodeByDefinition,
        Map<String, BigDecimal> costByOrgDefinitionKey,
        Map<String, String> finTypeDisplayByCode) {
        Map<String, PractitionerWorkloadFinTypeRowDto> merged = new LinkedHashMap<>();
        if (orgSummaries == null || orgSummaries.isEmpty()) {
            return new ArrayList<>();
        }
        for (PractitionerWorkloadFinTypeOrgSummaryQueryModel orgSummary : orgSummaries) {
            if (orgSummary == null || orgSummary.getDefinitionId() == null) {
                continue;
            }
            String finTypeCode = finTypeCodeByDefinition.get(orgSummary.getDefinitionId());
            if (StringUtils.isEmpty(finTypeCode)) {
                continue;
            }
            String finTypeKey = finTypeCode.trim();
            PractitionerWorkloadFinTypeRowDto row = merged.get(finTypeKey);
            if (row == null) {
                row = new PractitionerWorkloadFinTypeRowDto();
                row.setFinTypeCode(finTypeKey);
                String display = null;
                if (finTypeDisplayByCode != null) {
                    display = finTypeDisplayByCode.get(finTypeKey);
                }
                if (StringUtils.isNotEmpty(display)) {
                    row.setFinTypeDisplay(display.trim());
                } else {
                    row.setFinTypeDisplay(finTypeKey);
                }
                row.setReceivableAmount(BigDecimal.ZERO);
                row.setActualReceivedAmount(BigDecimal.ZERO);
                row.setCostAmount(BigDecimal.ZERO);
                merged.put(finTypeKey, row);
            }
            BigDecimal receivable = this.resolveReceivableFromOrgSummary(orgSummary);
            row.setReceivableAmount(row.getReceivableAmount().add(MonetaryAmountSupport.nullToZero(receivable)));
            String costKey = this.buildOrgDefinitionKey(orgSummary.getRequestingOrgId(), orgSummary.getDefinitionId());
            BigDecimal cost = BigDecimal.ZERO;
            if (costByOrgDefinitionKey != null && costKey != null) {
                BigDecimal bucketCost = costByOrgDefinitionKey.get(costKey);
                if (bucketCost != null) {
                    cost = bucketCost;
                }
            }
            row.setCostAmount(row.getCostAmount().add(MonetaryAmountSupport.nullToZero(cost)));
        }
        // Asm：跨 org 合并后各行金额归一到 6 位计算刻度（BR-MON-001）
        for (PractitionerWorkloadFinTypeRowDto row : merged.values()) {
            row.setReceivableAmount(MonetaryAmountSupport.scaleCalc(row.getReceivableAmount()));
            row.setActualReceivedAmount(MonetaryAmountSupport.scaleCalc(row.getActualReceivedAmount()));
            row.setCostAmount(MonetaryAmountSupport.scaleCalc(row.getCostAmount()));
        }
        return new ArrayList<>(merged.values());
    }

    /**
     * 按 payment 快照占比分摊实收并回填 fin_type 行（对齐 AS-OP-FIN-005 §1.3.1）。
     *
     * @param query         含 payment 链接
     * @param chargeRows    候选 chargeItem 行
     * @param finTypeRows   待回填实收的 fin_type 行
     * @param finTypeCodeByDefinition definition → fin_type
     * @param paymentSnapshotAbsTotalById paymentId → 快照内 |total_price| 合计（分母）
     */
    public void allocateActualReceivedAmount(
        PractitionerWorkloadPageQuery query,
        List<PractitionerWorkloadChargeRowQueryModel> chargeRows,
        List<PractitionerWorkloadFinTypeRowDto> finTypeRows,
        Map<Long, String> finTypeCodeByDefinition,
        Map<Long, BigDecimal> paymentSnapshotAbsTotalById) {
        if (finTypeRows == null || finTypeRows.isEmpty() || chargeRows == null || chargeRows.isEmpty()) {
            return;
        }
        Map<String, BigDecimal> actualByFinType = new LinkedHashMap<>();
        Map<Long, BigDecimal> paymentNetTenderedById = this.loadPaymentNetTenderedById(query, chargeRows);
        Map<Long, BigDecimal> paymentAbsTotalById = paymentSnapshotAbsTotalById;
        if (paymentAbsTotalById == null) {
            paymentAbsTotalById = new LinkedHashMap<>();
        }
        for (PractitionerWorkloadChargeRowQueryModel row : chargeRows) {
            if (row == null || row.getChargeItem() == null) {
                continue;
            }
            ChargeItem chargeItem = row.getChargeItem();
            if (chargeItem.getDefinitionId() == null) {
                continue;
            }
            String finTypeCode = finTypeCodeByDefinition.get(chargeItem.getDefinitionId());
            if (StringUtils.isEmpty(finTypeCode)) {
                continue;
            }
            BigDecimal actual = this.allocateLineActualReceived(
                chargeItem,
                row.getPaymentContext(),
                paymentNetTenderedById,
                paymentAbsTotalById);
            String key = finTypeCode.trim();
            BigDecimal existing = actualByFinType.get(key);
            if (existing == null) {
                existing = BigDecimal.ZERO;
            }
            actualByFinType.put(key, existing.add(actual));
        }
        for (PractitionerWorkloadFinTypeRowDto finTypeRow : finTypeRows) {
            if (finTypeRow == null || StringUtils.isEmpty(finTypeRow.getFinTypeCode())) {
                continue;
            }
            BigDecimal actualTotal = actualByFinType.get(finTypeRow.getFinTypeCode().trim());
            if (actualTotal == null) {
                actualTotal = BigDecimal.ZERO;
            }
            finTypeRow.setActualReceivedAmount(MonetaryAmountSupport.scaleCalc(actualTotal));
        }
    }

    /**
     * 按 serviceTypeId 跨 org 合并挂号净笔数（BR-RPT-WKL-016）。
     *
     * @param orgSummaries       领域 org 粒度号源汇总
     * @param serviceTypeById    号源类型主数据
     * @return 合并后的号源行
     */
    public List<PractitionerWorkloadRegistrationRowDto> mergeRegistrationRowsAcrossOrgs(
        List<PractitionerWorkloadRegistrationOrgSummaryQueryModel> orgSummaries,
        Map<Long, HealthcareService> serviceTypeById) {
        Map<String, PractitionerWorkloadRegistrationRowDto> merged = new LinkedHashMap<>();
        if (orgSummaries == null || orgSummaries.isEmpty()) {
            return new ArrayList<>();
        }
        for (PractitionerWorkloadRegistrationOrgSummaryQueryModel orgSummary : orgSummaries) {
            if (orgSummary == null) {
                continue;
            }
            Long serviceTypeId = orgSummary.getServiceTypeId();
            String mergeKey = "null";
            if (serviceTypeId != null) {
                mergeKey = String.valueOf(serviceTypeId);
            }
            PractitionerWorkloadRegistrationRowDto row = merged.get(mergeKey);
            if (row == null) {
                row = new PractitionerWorkloadRegistrationRowDto();
                row.setServiceTypeId(serviceTypeId);
                String serviceTypeName = null;
                if (serviceTypeId != null && serviceTypeById != null) {
                    HealthcareService healthcareService = serviceTypeById.get(serviceTypeId);
                    if (healthcareService != null) {
                        serviceTypeName = healthcareService.getName();
                    }
                }
                if (StringUtils.isNotEmpty(serviceTypeName)) {
                    row.setServiceTypeName(serviceTypeName.trim());
                } else if (serviceTypeId != null) {
                    row.setServiceTypeName(String.valueOf(serviceTypeId));
                }
                row.setNetChargeCount(0);
                merged.put(mergeKey, row);
            }
            row.setNetChargeCount(row.getNetChargeCount() + orgSummary.getNetChargeCount());
        }
        return new ArrayList<>(merged.values());
    }

    /**
     * 汇总 fin_type 行与号源行 → 页脚合计（结算刻度 2 位，**BR-MON-001**）。
     *
     * @param finTypeRows       fin_type 行
     * @param registrationRows  号源行
     * @return 合计 DTO
     */
    public PractitionerWorkloadSummaryTotalsDto assembleSummaryTotals(
        List<PractitionerWorkloadFinTypeRowDto> finTypeRows,
        List<PractitionerWorkloadRegistrationRowDto> registrationRows) {
        PractitionerWorkloadSummaryTotalsDto totals = new PractitionerWorkloadSummaryTotalsDto();
        totals.setReceivableAmountTotal(BigDecimal.ZERO);
        totals.setActualReceivedAmountTotal(BigDecimal.ZERO);
        totals.setCostAmountTotal(BigDecimal.ZERO);
        totals.setRegistrationNetCountTotal(0);
        if (finTypeRows != null) {
            for (PractitionerWorkloadFinTypeRowDto row : finTypeRows) {
                if (row == null) {
                    continue;
                }
                totals.setReceivableAmountTotal(
                    totals.getReceivableAmountTotal().add(MonetaryAmountSupport.nullToZero(row.getReceivableAmount())));
                totals.setActualReceivedAmountTotal(
                    totals.getActualReceivedAmountTotal().add(MonetaryAmountSupport.nullToZero(row.getActualReceivedAmount())));
                totals.setCostAmountTotal(
                    totals.getCostAmountTotal().add(MonetaryAmountSupport.nullToZero(row.getCostAmount())));
            }
        }
        if (registrationRows != null) {
            for (PractitionerWorkloadRegistrationRowDto row : registrationRows) {
                if (row == null || row.getNetChargeCount() == null) {
                    continue;
                }
                totals.setRegistrationNetCountTotal(totals.getRegistrationNetCountTotal() + row.getNetChargeCount());
            }
        }
        // Asm：页脚合计先 6 位加总再 settle（BR-MON-001），与门诊费用明细块合计口径一致
        totals.setReceivableAmountTotal(MonetaryAmountSupport.scaleSettle(totals.getReceivableAmountTotal()));
        totals.setActualReceivedAmountTotal(MonetaryAmountSupport.scaleSettle(totals.getActualReceivedAmountTotal()));
        totals.setCostAmountTotal(MonetaryAmountSupport.scaleSettle(totals.getCostAmountTotal()));
        return totals;
    }

    /**
     * 回填外显汇总指标到页脚/卡片合计 DTO。
     *
     * @param totals   财务合计 DTO
     * @param overview 外显指标读模型
     */
    public void applyOverviewSummary(
        PractitionerWorkloadSummaryTotalsDto totals,
        PractitionerWorkloadOverviewSummaryQueryModel overview) {
        if (totals == null) {
            return;
        }
        totals.setEncounterCountTotal(0);
        totals.setPrescriptionCountTotal(0);
        totals.setActivityItemCountTotal(0);
        if (overview == null) {
            return;
        }
        if (overview.getEncounterCountTotal() != null) {
            totals.setEncounterCountTotal(overview.getEncounterCountTotal());
        }
        if (overview.getPrescriptionCountTotal() != null) {
            totals.setPrescriptionCountTotal(overview.getPrescriptionCountTotal());
        }
        if (overview.getActivityItemCountTotal() != null) {
            totals.setActivityItemCountTotal(overview.getActivityItemCountTotal());
        }
    }

    /**
     * 装配单条明细行应收/实收/成本与目录类型（IF-RPT-WKL-03/04）。
     *
     * @param domainRow           领域链路明细行（金额已带正负）
     * @param costAmount          本链路带符号行成本
     * @param product             产品展示
     * @param catalogTypeDisplay  目录类型文案
     * @param requestingOrgName   开单科室名
     * @param practitionerName    开单医生名
     * @return 明细行 DTO
     */
    public PractitionerWorkloadDetailLineDto assembleDetailLineAmounts(
        PractitionerWorkloadRequestDetailQueryModel domainRow,
        BigDecimal costAmount,
        ProductDefinitionDisplayDto product,
        String catalogTypeDisplay,
        String requestingOrgName,
        String practitionerName) {
        if (domainRow == null) {
            return null;
        }
        PractitionerWorkloadDetailLineDto line = new PractitionerWorkloadDetailLineDto();
        line.setChargeItemId(domainRow.getChargeItemId());
        line.setPaymentId(domainRow.getPaymentId());
        line.setRequestingOrgId(domainRow.getRequestingOrgId());
        line.setRequestingOrgName(requestingOrgName);
        line.setServiceTable(domainRow.getServiceTable());
        line.setServiceId(domainRow.getServiceId());
        line.setCatalogTypeDisplay(catalogTypeDisplay);
        if (product != null) {
            line.setProductName(product.getProductName());
            line.setProductCode(product.getProductBusNo());
            line.setSpecification(this.resolveSpecification(product));
        }
        line.setPractitionerId(domainRow.getEntererId());
        line.setPractitionerName(practitionerName);
        if (domainRow.getEnteredDate() != null) {
            line.setEnteredDate(domainRow.getEnteredDate().format(GeneralSharedConstants.DATE_TIME_FORMATTER));
        }
        BigDecimal receivable = MonetaryAmountSupport.scaleCalc(domainRow.getDiscountedAmountTotal());
        // 折后链为 0 但行金额非 0 时回落行金额（对齐门诊费用明细行金额列）
        if (MonetaryAmountSupport.equalsAtSettleScale(receivable, BigDecimal.ZERO)) {
            receivable = MonetaryAmountSupport.scaleCalc(domainRow.getLineAmountTotal());
        }
        line.setReceivableAmount(receivable);
        line.setCostAmount(MonetaryAmountSupport.scaleCalc(costAmount));
        // 明细实收：本链路带符号 total_price（对齐门诊费用明细 BR-OP-FDT-012）
        line.setActualReceivedAmount(MonetaryAmountSupport.scaleCalc(domainRow.getLineAmountTotal()));
        return line;
    }

    /**
     * {@code service_table} → 目录类型展示（BR-RPT-WKL-006）。
     *
     * @param serviceTable 医嘱锚点表
     * @return 诊疗/药品/耗材；未知表抛错
     */
    public String resolveCatalogTypeDisplay(String serviceTable) {
        if (StringUtils.isEmpty(serviceTable)) {
            throw new IllegalArgumentException("service_table is blank");
        }
        String normalized = serviceTable.trim();
        if (TableNameConstants.WKF_SERVICE_REQUEST.equals(normalized)) {
            return PractitionerWorkloadConstants.CATALOG_TYPE_ACTIVITY_LABEL;
        }
        if (TableNameConstants.MED_MEDICATION_REQUEST.equals(normalized)) {
            return PractitionerWorkloadConstants.CATALOG_TYPE_MEDICATION_LABEL;
        }
        if (TableNameConstants.WKF_DEVICE_REQUEST.equals(normalized)) {
            return PractitionerWorkloadConstants.CATALOG_TYPE_DEVICE_LABEL;
        }
        throw new IllegalArgumentException("unsupported service_table for practitioner workload: " + normalized);
    }

    /**
     * 按 charge 行汇总实收分摊金额（按 payment 净实收比例分配）。
     *
     * @param requestChargeRows           收费行读模型
     * @param paymentSnapshotAbsTotalById payment → 快照绝对金额合计
     * @return 实收合计（计算刻度）
     */
    private BigDecimal sumActualReceivedForRequestRows(
        List<PractitionerWorkloadChargeRowQueryModel> requestChargeRows,
        Map<Long, BigDecimal> paymentSnapshotAbsTotalById) {
        if (requestChargeRows == null || requestChargeRows.isEmpty()) {
            return BigDecimal.ZERO;
        }
        Map<Long, BigDecimal> paymentNetTenderedById = this.loadPaymentNetTenderedById(null, requestChargeRows);
        Map<Long, BigDecimal> paymentAbsTotalById = paymentSnapshotAbsTotalById;
        if (paymentAbsTotalById == null) {
            paymentAbsTotalById = new LinkedHashMap<>();
        }
        BigDecimal actualTotal = BigDecimal.ZERO;
        for (PractitionerWorkloadChargeRowQueryModel row : requestChargeRows) {
            if (row == null || row.getChargeItem() == null) {
                continue;
            }
            BigDecimal lineActual = this.allocateLineActualReceived(
                row.getChargeItem(),
                row.getPaymentContext(),
                paymentNetTenderedById,
                paymentAbsTotalById);
            actualTotal = actualTotal.add(lineActual);
        }
        return actualTotal;
    }

    /**
     * 从 charge 行收集 payment id 并批量解析净实收（tendered − returned，退费取负）。
     *
     * @param query      分页查询（本路径未用，保留签名）
     * @param chargeRows 收费行读模型
     * @return paymentId → 净实收
     */
    private Map<Long, BigDecimal> loadPaymentNetTenderedById(
        PractitionerWorkloadPageQuery query,
        List<PractitionerWorkloadChargeRowQueryModel> chargeRows) {
        Map<Long, BigDecimal> netByPaymentId = new LinkedHashMap<>();
        Set<Long> paymentIds = new LinkedHashSet<>();
        for (PractitionerWorkloadChargeRowQueryModel row : chargeRows) {
            if (row == null || row.getPaymentContext() == null) {
                continue;
            }
            Long paymentId = row.getPaymentContext().getPaymentId();
            if (paymentId != null) {
                paymentIds.add(paymentId);
            }
        }
        for (Long paymentId : paymentIds) {
            netByPaymentId.put(paymentId, this.resolvePaymentNetTendered(paymentId));
        }
        return netByPaymentId;
    }

    /**
     * 单条 payment 净实收：实收减退回，已退费状态取负。
     *
     * @param paymentId payment 主键
     * @return 净实收（计算刻度）；缺失按 ZERO
     */
    private BigDecimal resolvePaymentNetTendered(Long paymentId) {
        if (paymentId == null) {
            return BigDecimal.ZERO;
        }
        PaymentReconciliation payment =
            this.paymentReconciliationQueryService.findOptionalPaymentEntityById(paymentId).orElse(null);
        if (payment == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal tendered = payment.getTenderedAmount();
        if (tendered == null) {
            tendered = BigDecimal.ZERO;
        }
        BigDecimal returned = payment.getReturnedAmount();
        if (returned == null) {
            returned = BigDecimal.ZERO;
        }
        BigDecimal net = tendered.subtract(returned);
        if (PaymentStatus.REFUNDED.equals(payment.getStatusEnum())) {
            net = net.negate();
        }
        return MonetaryAmountSupport.scaleCalc(net);
    }

    /**
     * 按 payment 净实收与快照分母将单行 charge 分摊为实收额。
     *
     * @param chargeItem              收费明细
     * @param paymentContext          payment 读上下文
     * @param paymentNetTenderedById  payment → 净实收
     * @param paymentAbsTotalById     payment → 快照绝对金额合计
     * @return 行实收分摊额
     */
    private BigDecimal allocateLineActualReceived(
        ChargeItem chargeItem,
        OutpatientFeeDetailPaymentContextQueryModel paymentContext,
        Map<Long, BigDecimal> paymentNetTenderedById,
        Map<Long, BigDecimal> paymentAbsTotalById) {
        if (chargeItem == null || paymentContext == null || paymentContext.getPaymentId() == null) {
            return BigDecimal.ZERO;
        }
        Long paymentId = paymentContext.getPaymentId();
        BigDecimal netTendered = paymentNetTenderedById.get(paymentId);
        if (netTendered == null) {
            netTendered = BigDecimal.ZERO;
        }
        BigDecimal denominator = paymentAbsTotalById.get(paymentId);
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) <= 0) {
            BigDecimal lineAmount = MonetaryAmountSupport.nullToZero(chargeItem.getTotalPrice());
            return this.applyRefundSign(lineAmount, paymentContext);
        }
        BigDecimal lineAbs = MonetaryAmountSupport.nullToZero(chargeItem.getTotalPrice()).abs();
        BigDecimal allocated = MonetaryAmountSupport.proportionAtCalcScale(
            lineAbs, netTendered.abs(), denominator);
        if (netTendered.compareTo(BigDecimal.ZERO) < 0) {
            allocated = allocated.negate();
        }
        return this.applyRefundSign(allocated, paymentContext);
    }

    /**
     * 退费支付上下文下将金额取绝对值后取负。
     *
     * @param amount          分摊金额
     * @param paymentContext  payment 读上下文
     * @return 带退费符号的金额
     */
    private BigDecimal applyRefundSign(
        BigDecimal amount,
        OutpatientFeeDetailPaymentContextQueryModel paymentContext) {
        if (paymentContext != null && Boolean.TRUE.equals(paymentContext.getRefundPayment())) {
            return amount.abs().negate();
        }
        return amount;
    }

    /**
     * 机构汇总块应收：优先折后合计，否则回落应收合计。
     *
     * @param orgSummary 机构维度汇总读模型
     * @return 应收展示额
     */
    private BigDecimal resolveReceivableFromOrgSummary(PractitionerWorkloadFinTypeOrgSummaryQueryModel orgSummary) {
        BigDecimal discounted = orgSummary.getDiscountedAmountTotal();
        if (discounted != null) {
            return discounted;
        }
        return MonetaryAmountSupport.nullToZero(orgSummary.getReceivableAmountTotal());
    }

    /**
     * 机构 id 与收费项目定义 id 组成 Map 键。
     *
     * @param orgId        机构主键
     * @param definitionId 收费项目定义主键
     * @return orgId:definitionId；任一为空则 null
     */
    private String buildOrgDefinitionKey(Long orgId, Long definitionId) {
        if (orgId == null || definitionId == null) {
            return null;
        }
        return orgId + ":" + definitionId;
    }

    /**
     * 产品规格展示：优先 totalVolume，否则剂型文案。
     *
     * @param product 产品展示 DTO
     * @return 规格文案；无则 null
     */
    private String resolveSpecification(ProductDefinitionDisplayDto product) {
        if (product == null) {
            return null;
        }
        if (StringUtils.isNotEmpty(product.getTotalVolume())) {
            return product.getTotalVolume();
        }
        if (StringUtils.isNotEmpty(product.getDoseFormCodeDictText())) {
            return product.getDoseFormCodeDictText();
        }
        return null;
    }
}
