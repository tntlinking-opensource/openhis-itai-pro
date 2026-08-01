/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.feedetail.inpatient.assembler;

import com.openhis.health.application.outpatient.financial.feedetail.assembler.OutpatientFeeDetailAssembler;
import com.openhis.health.application.outpatient.financial.feedetail.dto.OutpatientFeeDetailAmountSummaryDto;
import com.openhis.health.application.outpatient.financial.feedetail.dto.OutpatientFeeDetailEncounterBlockDto;
import com.openhis.health.application.outpatient.financial.feedetail.dto.OutpatientFeeDetailLineDto;
import com.openhis.health.application.shared.financial.DailySettlementFinTypeAmountLoader;
import com.openhis.health.application.shared.option.TerminologyOptionLoader;
import com.openhis.health.application.shared.pricing.HistoricalChargeItemPurchasePriceSupport;
import com.openhis.health.application.shared.product.ChargeItemProductLookupHelper;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.application.shared.report.PatientSettlementContextRow;
import com.openhis.health.application.shared.report.ReportPatientSettlementContextAssembler;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.OutpatientFeeDetailChargeRowQueryModel;
import com.openhis.health.domain.administration.chargeitem.query.OutpatientFeeDetailEncounterBlockQueryModel;
import com.openhis.health.domain.administration.chargeitem.query.OutpatientFeeDetailPageQuery;
import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.healthcareservice.entity.HealthcareService;
import com.openhis.health.domain.administration.healthcareservice.service.HealthcareServiceQueryService;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.patient.entity.Patient;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.administration.product.support.ProductDefinitionDisplaySupport;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.OutpatientFeeDetailPaymentContextQueryModel;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.OutpatientFeeDetailConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 住院费用明细报表就诊块装配（AS-RPT-009；金额列委托 {@link OutpatientFeeDetailAssembler}）。
 */
@Component
@RequiredArgsConstructor
public class InpatientFeeDetailReportBlockAssembler {

    /** 就诊挂号科室、busNo 读侧。 */
    private final EncounterQueryService encounterQueryService;

    /** 患者姓名读侧。 */
    private final PatientQueryService patientQueryService;

    /** 科室名称读侧。 */
    private final OrganizationQueryService organizationQueryService;

    /** 开单医生/收费员读侧。 */
    private final PractitionerQueryService practitionerQueryService;

    /** 产品定义批量读侧。 */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    /** 号源类型单条回落读侧。 */
    private final HealthcareServiceQueryService healthcareServiceQueryService;

    /** 术语单位译文。 */
    private final TerminologyOptionLoader terminologyOptionLoader;

    /** 定价行 fin_type 解析。 */
    private final DailySettlementFinTypeAmountLoader dailySettlementFinTypeAmountLoader;

    /** 历史进价解析（BR-PRICE-SRC-001）。 */
    private final HistoricalChargeItemPurchasePriceSupport historicalChargeItemPurchasePriceSupport;

    /** 金额列装配（与门诊费用明细同构）。 */
    private final OutpatientFeeDetailAssembler outpatientFeeDetailAssembler;

    /** 费用性质 + medType 报表上下文装配。 */
    private final ReportPatientSettlementContextAssembler reportPatientSettlementContextAssembler;

    /**
     * 将域层就诊块批量 enrichment 并装配为 DTO。
     *
     * @param domainBlocks 域层就诊块
     * @param query        同批查询（含 payment 上下文）
     * @return 就诊块 DTO 列表
     */
    public List<OutpatientFeeDetailEncounterBlockDto> assembleEncounterBlockDtos(
        List<OutpatientFeeDetailEncounterBlockQueryModel> domainBlocks,
        OutpatientFeeDetailPageQuery query) {
        if (domainBlocks == null || domainBlocks.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> encounterIdsForContext = new LinkedHashSet<>();
        for (OutpatientFeeDetailEncounterBlockQueryModel domainBlock : domainBlocks) {
            if (domainBlock != null && domainBlock.getEncounterId() != null) {
                encounterIdsForContext.add(domainBlock.getEncounterId());
            }
        }
        Map<Long, PatientSettlementContextRow> contextByEncounterId =
            this.reportPatientSettlementContextAssembler.mapContextsByEncounterIds(encounterIdsForContext);
        FeeDetailEnrichmentBundle bundle = this.loadEnrichmentBundle(domainBlocks);
        Map<String, String> unitLabelByCode =
            this.terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.UNIT_CODE);
        List<OutpatientFeeDetailEncounterBlockDto> result = new ArrayList<>(domainBlocks.size());
        for (OutpatientFeeDetailEncounterBlockQueryModel domainBlock : domainBlocks) {
            if (domainBlock == null || domainBlock.getEncounterId() == null) {
                continue;
            }
            OutpatientFeeDetailEncounterBlockDto blockDto = new OutpatientFeeDetailEncounterBlockDto();
            blockDto.setEncounterId(domainBlock.getEncounterId());
            Encounter encounter = bundle.encounterById.get(domainBlock.getEncounterId());
            if (encounter != null) {
                blockDto.setBusNo(encounter.getBusNo());
                if (encounter.getStartTime() != null) {
                    blockDto.setEncounterDate(
                        encounter.getStartTime().toLocalDate().format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER));
                }
                if (encounter.getOrganizationId() != null) {
                    blockDto.setRegistrationDepartmentName(
                        bundle.organizationNameById.get(encounter.getOrganizationId()));
                }
            }
            Long patientId = domainBlock.getPatientId();
            if (patientId == null && encounter != null) {
                patientId = encounter.getPatientId();
            }
            if (patientId != null) {
                Patient patient = bundle.patientById.get(patientId);
                if (patient != null) {
                    blockDto.setPatientName(patient.getName());
                }
            }
            PatientSettlementContextRow settlementContext = contextByEncounterId.get(domainBlock.getEncounterId());
            if (settlementContext != null) {
                blockDto.setFeeNatureContractNo(settlementContext.getFeeNatureContractNo());
                blockDto.setFeeNatureDisplay(settlementContext.getFeeNatureDisplay());
                blockDto.setMedType(settlementContext.getMedType());
                blockDto.setMedTypeDisplay(settlementContext.getMedTypeDisplay());
            }
            List<OutpatientFeeDetailLineDto> lineDtos = new ArrayList<>();
            if (domainBlock.getLines() != null) {
                for (OutpatientFeeDetailChargeRowQueryModel row : domainBlock.getLines()) {
                    OutpatientFeeDetailLineDto lineDto = this.assembleSingleLine(row, bundle, unitLabelByCode);
                    if (lineDto != null) {
                        lineDtos.add(lineDto);
                    }
                }
            }
            blockDto.setLines(lineDtos);
            blockDto.setSubtotal(this.outpatientFeeDetailAssembler.assembleEncounterSubtotal(lineDtos));
            result.add(blockDto);
        }
        return result;
    }

    /**
     * 全量命中行合计（Assembler 权威）。
     *
     * @param allLines 全量明细行
     * @return 金额 summary
     */
    public OutpatientFeeDetailAmountSummaryDto assembleGlobalSummary(List<OutpatientFeeDetailLineDto> allLines) {
        return this.outpatientFeeDetailAssembler.assembleEncounterSubtotal(allLines);
    }

    /**
     * 由域层候选行轻量装配全量金额 summary，禁止为页脚全量 enrichment。
     *
     * <p>明细分页页脚不跑全量历史进价（与门诊 {@code OutpatientFeeDetailAppService#buildGlobalSummary} 同构）；
     * {@code purchaseAmountTotal} 置空；行上进价仍由当前页 enrichment 解析。</p>
     *
     * @param rows 全量命中候选行
     * @return 合计 DTO（结算刻度 2 位；进价合计为 {@code null}）
     */
    public OutpatientFeeDetailAmountSummaryDto assembleGlobalSummaryFromChargeRows(
        List<OutpatientFeeDetailChargeRowQueryModel> rows) {
        // Asm：售价/实收/医保轻量加总；进价 Map 空，避免全量历史进价 IO
        OutpatientFeeDetailAmountSummaryDto summary =
            this.outpatientFeeDetailAssembler.assembleGlobalSummaryFromChargeRows(rows, Map.of());
        // 显式置空：与「未算」区分，禁止落成 0.00 误导页脚
        summary.setPurchaseAmountTotal(null);
        return summary;
    }

    /**
     * 装配单条明细行。
     *
     * @param row             域层候选行
     * @param bundle          enrichment 上下文
     * @param unitLabelByCode 单位码 → 展示名
     * @return 明细行 DTO；缺 chargeItem 时 null
     */
    private OutpatientFeeDetailLineDto assembleSingleLine(
        OutpatientFeeDetailChargeRowQueryModel row,
        FeeDetailEnrichmentBundle bundle,
        Map<String, String> unitLabelByCode) {
        if (row == null || row.getChargeItem() == null) {
            return null;
        }
        Long chargeItemId = row.getChargeItem().getId();
        OutpatientFeeDetailPaymentContextQueryModel paymentCtx = row.getPaymentContext();
        ProductDefinitionDisplayDto product = this.resolveProduct(row, bundle);
        if (product == null) {
            throw new IllegalArgumentException("missing product definition for charge item " + chargeItemId);
        }
        String finTypeCode = null;
        String finTypeDisplay = null;
        if (chargeItemId != null && bundle.finTypeCodeByChargeItemId != null) {
            finTypeCode = bundle.finTypeCodeByChargeItemId.get(chargeItemId);
        }
        if (StringUtils.isNotEmpty(finTypeCode) && bundle.finTypeLabelByCode != null) {
            finTypeDisplay = bundle.finTypeLabelByCode.get(finTypeCode.trim());
        }
        if (StringUtils.isEmpty(finTypeDisplay) && StringUtils.isNotEmpty(finTypeCode)) {
            finTypeDisplay = finTypeCode.trim();
        }
        OutpatientFeeDetailLineDto lineDto =
            this.outpatientFeeDetailAssembler.assembleLineAmounts(
                row, paymentCtx, product, finTypeCode, finTypeDisplay);
        if (lineDto == null) {
            return null;
        }
        String unitCode = row.getChargeItem().getQuantityUnit();
        if (StringUtils.isNotEmpty(unitCode)) {
            String unitDisplay = unitLabelByCode.get(unitCode.trim());
            if (StringUtils.isNotEmpty(unitDisplay)) {
                lineDto.setQuantityUnitDisplay(unitDisplay);
            } else {
                lineDto.setQuantityUnitDisplay(unitCode.trim());
            }
        }
        Long orderingDoctorId = row.getChargeItem().getEntererId();
        if (orderingDoctorId != null) {
            lineDto.setOrderingDoctorName(bundle.practitionerNameById.get(orderingDoctorId));
        }
        if (lineDto.getCashierId() != null) {
            lineDto.setCashierName(bundle.practitionerNameById.get(lineDto.getCashierId()));
        }
        // Asm：回填历史进价（与售价同号；缺锚点置 0）
        this.applyPurchasePrices(lineDto, chargeItemId, bundle);
        return lineDto;
    }

    /**
     * 将批量进价回填到明细行（退费行与售价同号）。
     *
     * @param lineDto      已装配售价/实收的明细行
     * @param chargeItemId 收费明细主键
     * @param bundle       enrichment 上下文
     */
    private void applyPurchasePrices(
        OutpatientFeeDetailLineDto lineDto,
        Long chargeItemId,
        FeeDetailEnrichmentBundle bundle) {
        BigDecimal signMultiplier = BigDecimal.ONE;
        if (OutpatientFeeDetailConstants.TRADE_DIRECTION_REFUND.equals(lineDto.getTradeDirection())) {
            signMultiplier = new BigDecimal("-1");
        }
        BigDecimal purchaseUnit = null;
        BigDecimal purchaseAmount = null;
        if (chargeItemId != null) {
            purchaseUnit = bundle.purchaseUnitByChargeItemId.get(chargeItemId);
            purchaseAmount = bundle.purchaseAmountByChargeItemId.get(chargeItemId);
        }
        if (purchaseUnit == null) {
            purchaseUnit = BigDecimal.ZERO;
        }
        if (purchaseAmount == null) {
            purchaseAmount = BigDecimal.ZERO;
        }
        lineDto.setPurchaseUnitPrice(
            MonetaryAmountSupport.applySignAtCalcScale(purchaseUnit, signMultiplier));
        lineDto.setPurchaseAmount(
            MonetaryAmountSupport.applySignAtCalcScale(purchaseAmount, signMultiplier));
    }

    /**
     * 批量加载就诊块 enrichment 上下文。
     *
     * @param domainBlocks 当前批域层就诊块
     * @return enrichment 上下文
     */
    private FeeDetailEnrichmentBundle loadEnrichmentBundle(
        List<OutpatientFeeDetailEncounterBlockQueryModel> domainBlocks) {
        FeeDetailEnrichmentBundle bundle = new FeeDetailEnrichmentBundle();
        List<ChargeItem> chargeItemsForFinType = new ArrayList<>();
        LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
        LinkedHashSet<Long> patientIds = new LinkedHashSet<>();
        LinkedHashSet<Long> practitionerIds = new LinkedHashSet<>();
        LinkedHashSet<ProductDefinitionBatchLookup> productLookups = new LinkedHashSet<>();
        for (OutpatientFeeDetailEncounterBlockQueryModel block : domainBlocks) {
            if (block == null) {
                continue;
            }
            if (block.getEncounterId() != null) {
                encounterIds.add(block.getEncounterId());
            }
            if (block.getPatientId() != null) {
                patientIds.add(block.getPatientId());
            }
            if (block.getLines() == null) {
                continue;
            }
            for (OutpatientFeeDetailChargeRowQueryModel row : block.getLines()) {
                if (row == null || row.getChargeItem() == null) {
                    continue;
                }
                chargeItemsForFinType.add(row.getChargeItem());
                if (row.getChargeItem().getPatientId() != null) {
                    patientIds.add(row.getChargeItem().getPatientId());
                }
                if (row.getChargeItem().getEntererId() != null) {
                    practitionerIds.add(row.getChargeItem().getEntererId());
                }
                if (row.getPaymentContext() != null && row.getPaymentContext().getCashierId() != null) {
                    practitionerIds.add(row.getPaymentContext().getCashierId());
                }
                ProductDefinitionBatchLookup lookup =
                    ChargeItemProductLookupHelper.buildLookupFromChargeItem(row.getChargeItem());
                if (lookup != null) {
                    productLookups.add(lookup);
                }
            }
        }
        List<Encounter> encounters = this.encounterQueryService.listEncounterEntitiesByIds(encounterIds);
        for (Encounter encounter : encounters) {
            if (encounter != null && encounter.getId() != null) {
                bundle.encounterById.put(encounter.getId(), encounter);
                if (encounter.getPatientId() != null) {
                    patientIds.add(encounter.getPatientId());
                }
                if (encounter.getOrganizationId() != null) {
                    bundle.organizationIds.add(encounter.getOrganizationId());
                }
            }
        }
        bundle.patientById = this.patientQueryService.loadPatientEntityMapByIds(patientIds);
        bundle.organizationNameById =
            this.organizationQueryService.findOrganizationNamesByIds(bundle.organizationIds);
        bundle.practitionerNameById = this.loadPractitionerNameMap(practitionerIds);
        List<ProductDefinitionBatchLookup> lookupList = new ArrayList<>(productLookups);
        bundle.productByCompositeKey = this.productDefinitionDisplayTerminologyAssembler.loadAsMap(lookupList);
        bundle.finTypeCodeByChargeItemId =
            this.dailySettlementFinTypeAmountLoader.mapFinTypeCodeByChargeItemId(chargeItemsForFinType);
        bundle.finTypeLabelByCode =
            this.terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.FIN_TYPE_CODE);
        // Pre：历史进价批量解析（禁当前 ACTIVE 目录价）；单价一次、行额由单价推导，禁止双倍定价 IO
        bundle.purchaseUnitByChargeItemId =
            this.historicalChargeItemPurchasePriceSupport.computePurchaseUnitPriceByChargeItemId(
                chargeItemsForFinType);
        bundle.purchaseAmountByChargeItemId =
            this.historicalChargeItemPurchasePriceSupport.computePurchaseAmountFromUnitPrices(
                chargeItemsForFinType, bundle.purchaseUnitByChargeItemId);
        return bundle;
    }

    /**
     * 解析单行产品定义。
     *
     * @param row    域层候选行
     * @param bundle enrichment 上下文
     * @return 产品展示 DTO
     */
    private ProductDefinitionDisplayDto resolveProduct(
        OutpatientFeeDetailChargeRowQueryModel row,
        FeeDetailEnrichmentBundle bundle) {
        if (row == null || row.getChargeItem() == null) {
            return null;
        }
        ProductDefinitionBatchLookup lookup =
            ChargeItemProductLookupHelper.buildLookupFromChargeItem(row.getChargeItem());
        if (lookup == null) {
            return null;
        }
        String compositeKey = ProductDefinitionKeyUtil.buildCompositeKey(
            lookup.getProductTableName(),
            lookup.getProductDefinitionId());
        if (compositeKey == null) {
            return null;
        }
        ProductDefinitionDisplayDto product = bundle.productByCompositeKey.get(compositeKey);
        if (product != null) {
            return product;
        }
        if (TableNameConstants.ADM_HEALTHCARE_SERVICE.equals(lookup.getProductTableName())
            && lookup.getProductDefinitionId() != null) {
            Map<Long, HealthcareService> healthcareById = this.healthcareServiceQueryService.loadHealthcareServiceMapByIds(
                List.of(lookup.getProductDefinitionId()));
            HealthcareService healthcareService = healthcareById.get(lookup.getProductDefinitionId());
            return ProductDefinitionDisplaySupport.fromHealthcareService(healthcareService);
        }
        return null;
    }

    /**
     * 批量解析医务人员展示名。
     *
     * @param practitionerIds 医务人员/enterer 主键集合
     * @return id → 姓名
     */
    private Map<Long, String> loadPractitionerNameMap(Set<Long> practitionerIds) {
        Map<Long, String> nameById = new HashMap<>();
        if (practitionerIds == null || practitionerIds.isEmpty()) {
            return nameById;
        }
        List<Practitioner> practitioners = this.practitionerQueryService.findAllByIds(practitionerIds);
        for (Practitioner practitioner : practitioners) {
            if (practitioner != null && practitioner.getId() != null && StringUtils.isNotEmpty(practitioner.getName())) {
                nameById.put(practitioner.getId(), practitioner.getName());
            }
        }
        Map<Long, String> entererNames = this.practitionerQueryService.mapDisplayNameByEntererIds(practitionerIds);
        for (Map.Entry<Long, String> entry : entererNames.entrySet()) {
            if (entry.getKey() != null && StringUtils.isNotEmpty(entry.getValue())) {
                nameById.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
        return nameById;
    }

    /**
     * 单次查询批量 enrichment 上下文容器。
     */
    private static final class FeeDetailEnrichmentBundle {

        /** encounterId → 就诊实体。 */
        private Map<Long, Encounter> encounterById = new HashMap<>();

        /** patientId → 患者实体。 */
        private Map<Long, Patient> patientById = Map.of();

        /** 待解析名称的 organizationId 集合。 */
        private Set<Long> organizationIds = new HashSet<>();

        /** organizationId → 科室名称。 */
        private Map<Long, String> organizationNameById = Map.of();

        /** practitionerId/entererId → 姓名。 */
        private Map<Long, String> practitionerNameById = Map.of();

        /** 产品复合键 → 展示 DTO。 */
        private Map<String, ProductDefinitionDisplayDto> productByCompositeKey = Map.of();

        /** chargeItemId → fin_type_code。 */
        private Map<Long, String> finTypeCodeByChargeItemId = Map.of();

        /** fin_type_code → 展示名。 */
        private Map<String, String> finTypeLabelByCode = Map.of();

        /** chargeItemId → 进价单价（未带收退符号）。 */
        private Map<Long, BigDecimal> purchaseUnitByChargeItemId = Map.of();

        /** chargeItemId → 进价总价（未带收退符号）。 */
        private Map<Long, BigDecimal> purchaseAmountByChargeItemId = Map.of();
    }
}
