/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.practitionerworkload.assembler;

import com.openhis.health.application.report.practitionerworkload.dto.PractitionerWorkloadDetailLineDto;
import com.openhis.health.application.report.practitionerworkload.dto.PractitionerWorkloadFinTypeRowDto;
import com.openhis.health.application.report.practitionerworkload.dto.PractitionerWorkloadRegistrationRowDto;
import com.openhis.health.application.report.practitionerworkload.dto.PractitionerWorkloadSummaryTotalsDto;
import com.openhis.health.domain.administration.chargeitem.query.PractitionerWorkloadFinTypeOrgSummaryQueryModel;
import com.openhis.health.domain.administration.chargeitem.query.PractitionerWorkloadOverviewSummaryQueryModel;
import com.openhis.health.domain.administration.chargeitem.query.PractitionerWorkloadRegistrationOrgSummaryQueryModel;
import com.openhis.health.domain.administration.chargeitem.query.PractitionerWorkloadRequestDetailQueryModel;
import com.openhis.health.domain.administration.healthcareservice.entity.HealthcareService;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.PractitionerWorkloadConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 科室/医生工作量金额列与跨 org 合并装配（AS-RPT-001；DD-报表中心-进售价口径；金额刻度 **BR-MON-001**）。
 *
 * <p>只读展示计算；禁止承载筛选或持久化逻辑。售价=账单行加和；进价=批号包装锚加和；
 * 本轮不含应收/实收。行级金额 6 位，页脚合计 {@link MonetaryAmountSupport#scaleSettle} 到 2 位。</p>
 */
@Component
@RequiredArgsConstructor
public class PractitionerWorkloadAssembler {

    /**
     * 按 fin_type_code 跨 org 合并汇总行（BR-RPT-WKL-016）。
     *
     * @param orgSummaries               领域 org 粒度汇总
     * @param finTypeCodeByDefinition    definitionId → fin_type_code
     * @param purchaseByOrgDefinitionKey orgId:definitionId → 进价合计
     * @param finTypeDisplayByCode       fin_type 字典展示
     * @return 合并后的 fin_type 行
     */
    public List<PractitionerWorkloadFinTypeRowDto> mergeFinTypeRowsAcrossOrgs(
        List<PractitionerWorkloadFinTypeOrgSummaryQueryModel> orgSummaries,
        Map<Long, String> finTypeCodeByDefinition,
        Map<String, BigDecimal> purchaseByOrgDefinitionKey,
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
                row.setSaleAmount(BigDecimal.ZERO);
                row.setPurchaseAmount(BigDecimal.ZERO);
                merged.put(finTypeKey, row);
            }
            // Asm：售价=账单行 total_price 加和（与明细同口径，禁止另算）
            BigDecimal sale = this.resolveSaleFromOrgSummary(orgSummary);
            row.setSaleAmount(row.getSaleAmount().add(MonetaryAmountSupport.nullToZero(sale)));
            String purchaseKey =
                this.buildOrgDefinitionKey(orgSummary.getRequestingOrgId(), orgSummary.getDefinitionId());
            BigDecimal purchase = BigDecimal.ZERO;
            if (purchaseByOrgDefinitionKey != null && purchaseKey != null) {
                BigDecimal bucketPurchase = purchaseByOrgDefinitionKey.get(purchaseKey);
                if (bucketPurchase != null) {
                    purchase = bucketPurchase;
                }
            }
            row.setPurchaseAmount(row.getPurchaseAmount().add(MonetaryAmountSupport.nullToZero(purchase)));
        }
        // Asm：跨 org 合并后各行金额归一到 6 位计算刻度（BR-MON-001）
        for (PractitionerWorkloadFinTypeRowDto row : merged.values()) {
            row.setSaleAmount(MonetaryAmountSupport.scaleCalc(row.getSaleAmount()));
            row.setPurchaseAmount(MonetaryAmountSupport.scaleCalc(row.getPurchaseAmount()));
        }
        return new ArrayList<>(merged.values());
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
        totals.setSaleAmountTotal(BigDecimal.ZERO);
        totals.setPurchaseAmountTotal(BigDecimal.ZERO);
        totals.setRegistrationNetCountTotal(0);
        if (finTypeRows != null) {
            for (PractitionerWorkloadFinTypeRowDto row : finTypeRows) {
                if (row == null) {
                    continue;
                }
                totals.setSaleAmountTotal(
                    totals.getSaleAmountTotal().add(MonetaryAmountSupport.nullToZero(row.getSaleAmount())));
                totals.setPurchaseAmountTotal(
                    totals.getPurchaseAmountTotal().add(MonetaryAmountSupport.nullToZero(row.getPurchaseAmount())));
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
        // Asm：页脚合计先 6 位加总再 settle（BR-MON-001），与明细同筛选加和
        totals.setSaleAmountTotal(MonetaryAmountSupport.scaleSettle(totals.getSaleAmountTotal()));
        totals.setPurchaseAmountTotal(MonetaryAmountSupport.scaleSettle(totals.getPurchaseAmountTotal()));
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
     * 装配单条明细行售价/进价与目录类型（IF-RPT-WKL-03/04）。
     *
     * @param domainRow           领域链路明细行（金额已带正负）
     * @param purchaseAmount      本行带符号进价；诊疗无进价为 null
     * @param product             产品展示
     * @param catalogTypeDisplay  目录类型文案
     * @param requestingOrgName   开单科室名
     * @param practitionerName    开单医生名
     * @return 明细行 DTO
     */
    public PractitionerWorkloadDetailLineDto assembleDetailLineAmounts(
        PractitionerWorkloadRequestDetailQueryModel domainRow,
        BigDecimal purchaseAmount,
        ProductDefinitionDisplayDto product,
        String catalogTypeDisplay,
        String requestingOrgName,
        String practitionerName) {
        if (domainRow == null) {
            return null;
        }
        PractitionerWorkloadDetailLineDto line = new PractitionerWorkloadDetailLineDto();
        line.setChargeItemId(domainRow.getChargeItemId());
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
        // Asm：售价=账单 total_price（带符号）；禁止另算
        line.setSaleAmount(MonetaryAmountSupport.scaleCalc(domainRow.getLineAmountTotal()));
        // Asm：诊疗无进价留空，不编 0
        if (purchaseAmount != null) {
            line.setPurchaseAmount(MonetaryAmountSupport.scaleCalc(purchaseAmount));
        }
        return line;
    }

    /**
     * {@code service_table} → 目录类型展示（BR-RPT-WKL-006）。
     *
     * @param serviceTable 医嘱锚点表名
     * @return 诊疗/药品/耗材展示文案
     * @throws IllegalArgumentException 空或不支持的表名
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
     * 机构汇总块售价：优先行金额合计（账单 total_price），否则回落折后。
     *
     * @param orgSummary 机构维度汇总读模型
     * @return 售价展示额
     */
    private BigDecimal resolveSaleFromOrgSummary(PractitionerWorkloadFinTypeOrgSummaryQueryModel orgSummary) {
        BigDecimal lineAmount = orgSummary.getLineAmountTotal();
        if (lineAmount != null) {
            return lineAmount;
        }
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
