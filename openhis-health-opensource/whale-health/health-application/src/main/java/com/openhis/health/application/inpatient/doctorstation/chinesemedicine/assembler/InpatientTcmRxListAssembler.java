/*
 * Copyright © 2026 OpenHIS Contributors
 */
package com.openhis.health.application.inpatient.doctorstation.chinesemedicine.assembler;

import com.openhis.health.application.inpatient.doctorstation.chinesemedicine.dto.TcmRxHeaderDto;
import com.openhis.health.application.inpatient.doctorstation.chinesemedicine.dto.TcmRxHistoryRowDto;
import com.openhis.health.application.inpatient.doctorstation.chinesemedicine.dto.TcmRxLineDto;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.medication.medicationrequest.queries.model.TcmHerbalPrescriptionGroupQueryModel;
import com.openhis.health.domain.medication.medicationrequest.queries.model.TcmHerbalPrescriptionHistoryGroupQueryModel;
import com.openhis.health.domain.share.constants.ProductDefinitionCatalogUnitBandConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.module.common.assembler.advice.TcmMethodCodeDisplaySupport;
import com.openhis.health.module.common.assembler.option.FrequencyOptionLoader;
import com.openhis.health.module.common.assembler.option.TerminologyOptionLoader;
import com.openhis.health.module.common.assembler.order.OrderCatalogDisplayAssembler;
import com.openhis.health.module.common.assembler.product.ClinicalCatalogPriceResolveSupport;
import com.openhis.health.module.common.assembler.product.ClinicalCatalogUnitPriceCommand;
import com.openhis.health.module.common.assembler.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.module.common.dto.product.ProductDefinitionCatalogUnitBandDto;
import com.openhis.health.module.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import static com.openhis.whale.core.application.util.StringUtils.trimToEmpty;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * IF-IP-TCM-34/39：住院中药处方列表与历史 enrich（目录名、已落库账单单价优先）。
 *
 * <p>对齐门诊 {@code TcmRxListAssembler} 与 **BR-PRICE-SRC-001**：已挂费行优先 {@code ChargeItem.unit_price}，
 * 草稿态无账单时回落目录 DOSE 槽参考价。</p>
 */
@Component
@RequiredArgsConstructor
public class InpatientTcmRxListAssembler {

    private final MedicationDefinitionQueryService medicationDefinitionQueryService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final TerminologyOptionLoader terminologyOptionLoader;
    private final FrequencyOptionLoader frequencyOptionLoader;
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;
    private final ClinicalCatalogPriceResolveSupport clinicalCatalogPriceResolveSupport;

    /**
     * 将领域按方聚合读模型 enrich 为方头+行 DTO。
     *
     * @param groups 领域层按方聚合结果
     * @return 方头列表
     */
    public List<TcmRxHeaderDto> enrichInpatientPrescriptionGroups(List<TcmHerbalPrescriptionGroupQueryModel> groups) {
        List<TcmRxHeaderDto> result = new ArrayList<>();
        if (groups == null || groups.isEmpty()) {
            return result;
        }
        Set<Long> medicationIds = new HashSet<>();
        Set<Long> requestIds = new HashSet<>();
        for (TcmHerbalPrescriptionGroupQueryModel group : groups) {
            if (group.getLines() == null) {
                continue;
            }
            for (MedicationRequestListQueryModel line : group.getLines()) {
                if (line.getMedicationId() != null) {
                    medicationIds.add(line.getMedicationId());
                }
                if (line.getId() != null) {
                    requestIds.add(line.getId());
                }
            }
        }
        Map<Long, String> medicationNameById = loadMedicationNames(medicationIds);
        // Pre：批量加载药嘱锚定的 ChargeItem，供已计费行展示账单单价（BR-PRICE-SRC-001）
        TcmLineChargeEnrich chargeEnrich = loadLineChargeEnrich(requestIds);
        Map<String, String> tcmMethodLabelByCode =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.TCM_METHOD_CODE);
        Map<String, String> westernMethodLabelByCode =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.METHOD_CODE);
        Map<String, String> rateLabelByCode = frequencyOptionLoader.buildRateLabelMap();
        Map<String, String> unitLabelByCode =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.UNIT_CODE);
        Map<String, String> doseUnitLabelByCode =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.DOSE_UNIT);
        Map<String, ProductDefinitionDisplayDto> productByKey = loadMedicationProducts(medicationIds);
        Map<String, List<ChargeItemDefinition>> priceByKey =
            clinicalCatalogPriceResolveSupport.loadPriceLinesGrouped(new ArrayList<>(productByKey.values()));
        for (TcmHerbalPrescriptionGroupQueryModel group : groups) {
            TcmRxHeaderDto header = new TcmRxHeaderDto();
            header.setPrescriptionGroupKey(group.getPrescriptionGroupKey());
            header.setPrescriptionNo(group.getPrescriptionNo());
            header.setTherapyEnum(group.getTherapyEnum());
            header.setTcmSyndromeGroup(group.getTcmSyndromeGroup());
            header.setChineseHerbsDoseQuantity(group.getChineseHerbsDoseQuantity());
            header.setSufferingFlag(group.getSufferingFlag());
            header.setMethodCode(group.getMethodCode());
            header.setMethodName(TcmMethodCodeDisplaySupport.resolveMethodDisplayName(
                tcmMethodLabelByCode, westernMethodLabelByCode, Boolean.TRUE, group.getMethodCode()));
            header.setRateCode(group.getRateCode());
            header.setRateName(OrderCatalogDisplayAssembler.resolveCategoryDisplay(
                rateLabelByCode, group.getRateCode()));
            header.setDays(group.getDispensePerDuration());
            header.setRemark(group.getRemark());
            header.setStatusEnum(group.getStatusEnum());
            MedicationRequestListQueryModel firstLine = null;
            if (group.getLines() != null && !group.getLines().isEmpty()) {
                firstLine = group.getLines().get(0);
            }
            if (firstLine != null) {
                header.setDecoctionMethodCode(TcmMethodCodeDisplaySupport.readContentJsonTextField(
                    firstLine.getContentJson(), "decoctionMethodCode"));
            }
            List<TcmRxLineDto> lineDtos = new ArrayList<>();
            if (group.getLines() != null) {
                for (MedicationRequestListQueryModel line : group.getLines()) {
                    TcmRxLineDto lineDto = new TcmRxLineDto();
                    lineDto.setMedicationRequestId(line.getId());
                    lineDto.setMedicationDefinitionId(line.getMedicationId());
                    lineDto.setMedicationName(medicationNameById.get(line.getMedicationId()));
                    lineDto.setDose(line.getDose());
                    lineDto.setDoseUnitCode(line.getDoseUnitCode());
                    ProductDefinitionDisplayDto medProduct = productByKey.get(
                        ProductDefinitionKeyUtil.buildCompositeKey(
                            TableNameConstants.MED_MEDICATION_DEFINITION, line.getMedicationId()));
                    lineDto.setDoseUnitName(ProductDefinitionCatalogUnitBandsAssembler.resolveDoseUnitDisplayName(
                        doseUnitLabelByCode, line.getDoseUnitCode(), medProduct));
                    lineDto.setQuantity(line.getQuantity());
                    lineDto.setUnitCode(line.getUnitCode());
                    lineDto.setUnitName(ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(
                        unitLabelByCode, line.getUnitCode(), medProduct));
                    lineDto.setRetailUnitPrice(resolveRetailUnitPrice(
                        line, group, medProduct, chargeEnrich, priceByKey));
                    lineDto.setSortNumber(line.getSortNumber());
                    lineDto.setLineStatusEnum(line.getStatusEnum());
                    lineDtos.add(lineDto);
                }
            }
            header.setLines(lineDtos);
            result.add(header);
        }
        return result;
    }

    /**
     * 将患者历史方读模型 enrich 为展示行，并按关键字过滤。
     *
     * @param groups  领域历史方聚合
     * @param keyword 可选关键字
     * @return 历史方摘要
     */
    public List<TcmRxHistoryRowDto> enrichHistoryGroups(
        List<TcmHerbalPrescriptionHistoryGroupQueryModel> groups,
        String keyword
    ) {
        List<TcmRxHistoryRowDto> result = new ArrayList<>();
        if (groups == null || groups.isEmpty()) {
            return result;
        }
        Set<Long> medicationIds = new LinkedHashSet<>();
        for (TcmHerbalPrescriptionHistoryGroupQueryModel group : groups) {
            if (group.getMedicationIds() != null) {
                medicationIds.addAll(group.getMedicationIds());
            }
        }
        Map<Long, String> medicationNameById = loadMedicationNames(medicationIds);
        String keywordTrimmed = trimToEmpty(keyword).toLowerCase();
        for (TcmHerbalPrescriptionHistoryGroupQueryModel group : groups) {
            List<String> medicationNames = new ArrayList<>();
            for (Long medicationId : group.getMedicationIds()) {
                String name = medicationNameById.get(medicationId);
                if (StringUtils.isNotBlank(name)) {
                    medicationNames.add(name);
                }
            }
            if (StringUtils.isNotBlank(keywordTrimmed)) {
                boolean matched = false;
                if (group.getPrescriptionNo() != null
                    && group.getPrescriptionNo().toLowerCase().contains(keywordTrimmed)) {
                    matched = true;
                }
                if (!matched) {
                    for (String medicationName : medicationNames) {
                        if (medicationName.toLowerCase().contains(keywordTrimmed)) {
                            matched = true;
                            break;
                        }
                    }
                }
                if (!matched) {
                    continue;
                }
            }
            TcmRxHistoryRowDto row = new TcmRxHistoryRowDto();
            row.setPrescriptionNo(group.getPrescriptionNo());
            row.setEncounterId(group.getEncounterId());
            row.setAuthoredAt(group.getReqAuthoredTime());
            row.setMedicationNames(medicationNames);
            int lineCount = 0;
            if (group.getLineCount() != null) {
                lineCount = group.getLineCount();
            }
            row.setSummaryText(lineCount + "味");
            result.add(row);
        }
        return result;
    }

    private Map<String, ProductDefinitionDisplayDto> loadMedicationProducts(Set<Long> medicationIds) {
        if (medicationIds == null || medicationIds.isEmpty()) {
            return new HashMap<>();
        }
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (Long medicationId : medicationIds) {
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
            lookup.setProductDefinitionId(medicationId);
            lookups.add(lookup);
        }
        return productDefinitionBatchLoader.loadAsMap(lookups);
    }

    private Map<Long, String> loadMedicationNames(Set<Long> medicationIds) {
        Map<Long, String> result = new HashMap<>();
        if (medicationIds == null || medicationIds.isEmpty()) {
            return result;
        }
        List<MedicationDefinition> definitions = medicationDefinitionQueryService.findAllByIds(medicationIds);
        for (MedicationDefinition definition : definitions) {
            if (definition != null && definition.getId() != null) {
                result.put(definition.getId(), definition.getName());
            }
        }
        return result;
    }

    /**
     * 解析饮片行单剂用量单位零售价：已挂费优先 {@code ChargeItem.unit_price}，否则目录 DOSE 槽单价。
     *
     * @param line         药品请求行
     * @param group        方头聚合（用法参与目录定价）
     * @param medProduct   饮片目录投影；可为 null
     * @param chargeEnrich 已落库账单单价
     * @param priceByKey   目录定价行（按复合键分组）
     * @return 元/剂量单位零售价；无法解析时 null
     */
    private BigDecimal resolveRetailUnitPrice(
        MedicationRequestListQueryModel line,
        TcmHerbalPrescriptionGroupQueryModel group,
        ProductDefinitionDisplayDto medProduct,
        TcmLineChargeEnrich chargeEnrich,
        Map<String, List<ChargeItemDefinition>> priceByKey
    ) {
        // 已落库 ChargeItem 单价为展示权威（BR-PRICE-SRC-001），避免调价后目录价覆盖历史账单
        if (line.getId() != null) {
            BigDecimal chargeUnitPrice = chargeEnrich.unitPriceByRequestId().get(line.getId());
            if (chargeUnitPrice != null) {
                return chargeUnitPrice;
            }
        }
        if (medProduct == null || line.getMedicationId() == null) {
            return null;
        }
        String compositeKey = ProductDefinitionKeyUtil.buildCompositeKey(
            TableNameConstants.MED_MEDICATION_DEFINITION, line.getMedicationId());
        List<ChargeItemDefinition> priceLines = priceByKey.getOrDefault(compositeKey, List.of());
        ClinicalCatalogUnitPriceCommand priceCmd = ClinicalCatalogUnitPriceCommand.builder()
            .productTableName(medProduct.getProductTableName())
            .productDefinitionId(medProduct.getProductDefinitionId())
            .categoryCode(medProduct.getCategoryCode())
            .methodCode(group.getMethodCode())
            .organizationId(line.getOrgId())
            .encounterId(line.getEncounterId())
            .clientResolvedDispenseLocationId(line.getPerformLocation())
            .priceLines(priceLines)
            .productRowForOccupancy(medProduct)
            .build();
        ChargeItemDefinition selected = clinicalCatalogPriceResolveSupport.resolveCatalogUnitPrice(priceCmd);
        if (selected == null || selected.getPriceValue() == null) {
            return null;
        }
        String pricingUnitCode = ProductDefinitionCatalogUnitBandsAssembler.resolvePackageAnchoredPricingUnitCode(
            medProduct, selected.getPriceTypeCode());
        List<ProductDefinitionCatalogUnitBandDto> bands = ProductDefinitionCatalogUnitBandsAssembler.buildDoctorStationBands(
            medProduct, pricingUnitCode, selected.getPriceValue());
        return resolveDoseUnitPriceFromBands(bands, line.getDoseUnitCode());
    }

    private static BigDecimal resolveDoseUnitPriceFromBands(
        List<ProductDefinitionCatalogUnitBandDto> bands,
        String doseUnitCode
    ) {
        if (bands == null || bands.isEmpty()) {
            return null;
        }
        for (ProductDefinitionCatalogUnitBandDto band : bands) {
            if (band == null) {
                continue;
            }
            if (ProductDefinitionCatalogUnitBandConstants.BAND_DOSE.equals(band.getBandKind())
                && band.getUnitPrice() != null) {
                return band.getUnitPrice();
            }
        }
        if (StringUtils.isBlank(doseUnitCode)) {
            return null;
        }
        String normalized = doseUnitCode.trim();
        for (ProductDefinitionCatalogUnitBandDto band : bands) {
            if (band == null || band.getUnitPrice() == null || StringUtils.isBlank(band.getUnitCode())) {
                continue;
            }
            if (normalized.equals(band.getUnitCode().trim())) {
                return band.getUnitPrice();
            }
        }
        return null;
    }

    /**
     * 批量加载中药行已落库账单单价（按 {@code med_medication_request.id} 归并多条 ChargeItem）。
     *
     * @param requestIds 药品请求主键集合
     * @return 请求 id → 账单单价；无 id 时返回空映射
     */
    private TcmLineChargeEnrich loadLineChargeEnrich(Set<Long> requestIds) {
        Map<Long, BigDecimal> unitPriceByRequestId = new HashMap<>();
        if (requestIds == null || requestIds.isEmpty()) {
            return new TcmLineChargeEnrich(unitPriceByRequestId);
        }
        ChargeItemQueryCommand cmd = new ChargeItemQueryCommand();
        cmd.setServiceIds(new ArrayList<>(requestIds));
        List<ChargeItem> chargeItems = chargeItemQueryService.listChargeItemsByServiceIds(cmd);
        Map<Long, TcmChargePrimaryRow> primaryByRequestId = new HashMap<>();
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null || chargeItem.getServiceId() == null) {
                continue;
            }
            if (!TableNameConstants.MED_MEDICATION_REQUEST.equals(chargeItem.getServiceTable())) {
                continue;
            }
            if (Boolean.TRUE.equals(chargeItem.getIsDeleted())) {
                continue;
            }
            Long requestId = chargeItem.getServiceId();
            TcmChargePrimaryRow primary = primaryByRequestId.get(requestId);
            if (primary == null) {
                primaryByRequestId.put(requestId, TcmChargePrimaryRow.from(chargeItem));
            } else {
                primary.consider(chargeItem);
            }
        }
        for (Map.Entry<Long, TcmChargePrimaryRow> entry : primaryByRequestId.entrySet()) {
            if (entry.getValue().unitPrice != null) {
                unitPriceByRequestId.put(entry.getKey(), entry.getValue().unitPrice);
            }
        }
        return new TcmLineChargeEnrich(unitPriceByRequestId);
    }

    /** 中药行账单单价 enrich 中间态。 */
    private record TcmLineChargeEnrich(Map<Long, BigDecimal> unitPriceByRequestId) {
    }

    /** 同一药品请求下代表 ChargeItem 行（按发生时间与主键择优）。 */
    private static final class TcmChargePrimaryRow {
        private Long chargeItemId;
        private LocalDateTime occurrenceTime;
        private BigDecimal unitPrice;

        private static TcmChargePrimaryRow from(ChargeItem item) {
            TcmChargePrimaryRow row = new TcmChargePrimaryRow();
            row.consider(item);
            return row;
        }

        private void consider(ChargeItem item) {
            LocalDateTime occ = item.getOccurrenceTime();
            if (occ == null) {
                occ = LocalDateTime.MIN;
            }
            if (chargeItemId == null) {
                chargeItemId = item.getId();
                occurrenceTime = occ;
                unitPrice = item.getUnitPrice();
                return;
            }
            if (occ.isAfter(occurrenceTime)) {
                chargeItemId = item.getId();
                occurrenceTime = occ;
                unitPrice = item.getUnitPrice();
                return;
            }
            if (occ.equals(occurrenceTime) && item.getId() != null && chargeItemId != null
                && item.getId() > chargeItemId) {
                chargeItemId = item.getId();
                unitPrice = item.getUnitPrice();
            }
        }
    }
}
