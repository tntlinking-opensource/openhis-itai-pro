/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.chinesemedicine.assembler;

import static com.openhis.whale.core.application.util.StringUtils.trimToEmpty;

import com.openhis.health.application.outpatient.doctorstation.chinesemedicine.dto.TcmRxHeaderDto;
import com.openhis.health.application.outpatient.doctorstation.chinesemedicine.dto.TcmRxHistoryRowDto;
import com.openhis.health.application.outpatient.doctorstation.chinesemedicine.dto.TcmRxLineDto;
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
import com.openhis.health.application.shared.advice.AdviceChargeDisplayStatusAssembler;
import com.openhis.health.application.shared.advice.TcmMethodCodeDisplayAssembler;
import com.openhis.health.application.shared.option.FrequencyOptionLoader;
import com.openhis.health.application.shared.option.TerminologyOptionLoader;
import com.openhis.health.application.shared.advice.OrderCatalogDisplayAssembler;
import com.openhis.health.application.shared.product.ClinicalCatalogPriceResolver;
import com.openhis.health.application.shared.product.ClinicalCatalogUnitPriceQuery;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogUnitBandDto;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.share.utils.EnumUtil;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

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
 * IF-DOR-34/39：中药处方列表与历史读侧 enrich（目录名、费用态）。
 */
@Component
@RequiredArgsConstructor
public class TcmRxListAssembler {

    private final MedicationDefinitionQueryService medicationDefinitionQueryService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final TerminologyOptionLoader terminologyOptionLoader;
    private final FrequencyOptionLoader frequencyOptionLoader;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    private final ClinicalCatalogPriceResolver clinicalCatalogPriceResolver;

    /**
     * 将领域按方聚合读模型 enrich 为方头+行 DTO。
     */
    public List<TcmRxHeaderDto> toPrescriptionHeaders(List<TcmHerbalPrescriptionGroupQueryModel> groups) {
        return this.enrichPrescriptionGroups(groups);
    }

    /**
     * 将领域按方聚合读模型 enrich 为方头+行 DTO。
     *
     * @param groups 领域层按方聚合结果
     * @return 方头列表
     */
    public List<TcmRxHeaderDto> enrichPrescriptionGroups(List<TcmHerbalPrescriptionGroupQueryModel> groups) {
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
        Map<Long, String> medicationNameById = this.loadMedicationNames(medicationIds);
        TcmLineChargeEnrich chargeEnrich = this.loadLineChargeEnrich(requestIds);
        Long encounterId = this.resolveEncounterIdFromGroups(groups);
        Map<String, String> decoctionChargeByPrescriptionNo =
            this.loadDecoctionChargeStatusByPrescriptionNo(encounterId, groups);
        Map<String, String> tcmMethodLabelByCode =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.TCM_METHOD_CODE);
        Map<String, String> westernMethodLabelByCode =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.METHOD_CODE);
        // Pre：批量预取频次标签，供方头 rateName 展示（与 IF-DOR-18 下拉同源）
        Map<String, String> rateLabelByCode = frequencyOptionLoader.buildRateLabelMap();
        Map<String, String> dosageInstructionLabelByCode =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.DOSAGE_INSTRUCTION);
        // Pre：单位展示名预热，供行级 doseUnitName/unitName（BR-ORD-022 / BR-TCM-RX-012）
        Map<String, String> unitLabelByCode =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.UNIT_CODE);
        Map<String, String> doseUnitLabelByCode =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.DOSE_UNIT);
        // Pre：批量加载饮片目录投影与定价行，供零售价回落（无 ChargeItem 时走 DOSE 槽）
        Map<String, ProductDefinitionDisplayDto> productByKey = this.loadMedicationProducts(medicationIds);
        Map<String, List<ChargeItemDefinition>> priceByKey =
            clinicalCatalogPriceResolver.loadPriceLinesGrouped(new ArrayList<>(productByKey.values()));
        for (TcmHerbalPrescriptionGroupQueryModel group : groups) {
            TcmRxHeaderDto header = new TcmRxHeaderDto();
            header.setPrescriptionGroupKey(group.getPrescriptionGroupKey());
            header.setPrescriptionNo(group.getPrescriptionNo());
            header.setTcmSyndromeGroup(group.getTcmSyndromeGroup());
            header.setChineseHerbsDoseQuantity(group.getChineseHerbsDoseQuantity());
            header.setSufferingFlag(group.getSufferingFlag());
            header.setMethodCode(group.getMethodCode());
            header.setMethodName(TcmMethodCodeDisplayAssembler.resolveMethodDisplayName(
                tcmMethodLabelByCode, westernMethodLabelByCode, Boolean.TRUE, group.getMethodCode()));
            // Asm：方头用药频次编码与展示名，取领域聚合首行 rate_code
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
                header.setDecoctionMethodCode(TcmMethodCodeDisplayAssembler.readContentJsonTextField(
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
                    lineDto.setRetailUnitPrice(this.resolveRetailUnitPrice(
                        line, group, medProduct, chargeEnrich, priceByKey));
                    lineDto.setSortNumber(line.getSortNumber());
                    lineDto.setLineStatusEnum(line.getStatusEnum());
                    lineDto.setChargeStatusText(chargeEnrich.statusTextByRequestId().get(line.getId()));
                    lineDto.setDosageInstructionCode(line.getDosageInstruction());
                    lineDto.setDosageInstructionText(OrderCatalogDisplayAssembler.resolveCategoryDisplay(
                        dosageInstructionLabelByCode, line.getDosageInstruction()));
                    lineDtos.add(lineDto);
                }
            }
            header.setLines(lineDtos);
            this.applyHeaderDisplayStatus(header, group, chargeEnrich, decoctionChargeByPrescriptionNo);
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
        Map<Long, String> medicationNameById = this.loadMedicationNames(medicationIds);
        String keywordTrimmed = trimToEmpty(keyword).toLowerCase();
        for (TcmHerbalPrescriptionHistoryGroupQueryModel group : groups) {
            List<String> medicationNames = new ArrayList<>();
            for (Long medicationId : group.getMedicationIds()) {
                String name = medicationNameById.get(medicationId);
                if (StringUtils.isNotEmpty(name)) {
                    medicationNames.add(name);
                }
            }
            if (StringUtils.isNotEmpty(keywordTrimmed)) {
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

    /**
     * 批量加载饮片目录展示投影，键为 productTableName+definitionId 复合键。
     *
     * @param medicationIds 饮片定义主键集合
     * @return 复合键 → 目录展示 DTO；无 id 时返回空 Map
     */
    private Map<String, ProductDefinitionDisplayDto> loadMedicationProducts(Set<Long> medicationIds) {
        if (medicationIds == null || medicationIds.isEmpty()) {
            return new HashMap<>();
        }
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        // Loop：逐饮片 id 组装批量查询键，避免列表装配阶段逐行查库
        for (Long medicationId : medicationIds) {
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
            lookup.setProductDefinitionId(medicationId);
            lookups.add(lookup);
        }
        return productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
    }

    /**
     * 批量加载饮片定义 id → 名称（列表缺品名时的回落展示）。
     *
     * @param medicationIds 饮片定义主键集合
     * @return id → 名称；无 id 时空 Map
     */
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
     * 解析饮片行单剂用量单位零售价：已挂费优先取 {@code ChargeItem.unit_price}，否则目录 DOSE 槽单价。
     *
     * @param line         药品请求行
     * @param group        方头聚合（用法参与目录定价）
     * @param medProduct   饮片目录投影；可为 null
     * @param chargeEnrich 费用态与已落库单价
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
        // 已落库 ChargeItem 单价为展示权威，避免草稿态目录价覆盖已计费金额
        if (line.getId() != null) {
            BigDecimal chargeUnitPrice = chargeEnrich.unitPriceByRequestId().get(line.getId());
            if (chargeUnitPrice != null) {
                return chargeUnitPrice;
            }
        }
        // 无费用行时回落目录定价 + unitBands DOSE 槽（与 IF-DOR-11 开立目录口径一致）
        if (medProduct == null || line.getMedicationId() == null) {
            return null;
        }
        String compositeKey = ProductDefinitionKeyUtil.buildCompositeKey(
            TableNameConstants.MED_MEDICATION_DEFINITION, line.getMedicationId());
        List<ChargeItemDefinition> priceLines = priceByKey.getOrDefault(compositeKey, List.of());
        ClinicalCatalogUnitPriceQuery priceCmd = ClinicalCatalogUnitPriceQuery.builder()
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
        ChargeItemDefinition selected = clinicalCatalogPriceResolver.resolveCatalogUnitPrice(priceCmd);
        if (selected == null || selected.getPriceValue() == null) {
            return null;
        }
        String pricingUnitCode = ProductDefinitionCatalogUnitBandsAssembler.resolvePackageAnchoredPricingUnitCode(
            medProduct, selected.getPriceTypeCode());
        List<ProductDefinitionCatalogUnitBandDto> bands = ProductDefinitionCatalogUnitBandsAssembler.buildDoctorStationBands(
            medProduct, pricingUnitCode, selected.getPriceValue());
        return resolveDoseUnitPriceFromBands(bands, line.getDoseUnitCode());
    }

    /**
     * 从医生站单位槽列表取 DOSE 槽单价；无 DOSE 槽时按 doseUnitCode 精确匹配槽位。
     *
     * @param bands       目录单位槽
     * @param doseUnitCode 单剂用量单位编码
     * @return 元/剂量单位单价；无匹配槽时 null
     */
    private static BigDecimal resolveDoseUnitPriceFromBands(
        List<ProductDefinitionCatalogUnitBandDto> bands,
        String doseUnitCode
    ) {
        if (bands == null || bands.isEmpty()) {
            return null;
        }
        // 优先取标准 DOSE 槽单价（与开立目录 unitBands 语义一致）
        for (ProductDefinitionCatalogUnitBandDto band : bands) {
            if (band == null) {
                continue;
            }
            if (ProductDefinitionCatalogUnitBandConstants.BAND_DOSE.equals(band.getBandKind())
                && band.getUnitPrice() != null) {
                return band.getUnitPrice();
            }
        }
        if (StringUtils.isEmpty(doseUnitCode)) {
            return null;
        }
        String normalized = doseUnitCode.trim();
        // 回落：按行上 doseUnitCode 与槽位 unitCode 精确匹配
        for (ProductDefinitionCatalogUnitBandDto band : bands) {
            if (band == null || band.getUnitPrice() == null || StringUtils.isEmpty(band.getUnitCode())) {
                continue;
            }
            if (normalized.equals(band.getUnitCode().trim())) {
                return band.getUnitPrice();
            }
        }
        return null;
    }

    /**
     * 批量加载中药行费用态展示文案与已落库单价（按请求 id 归并多条 ChargeItem）。
     *
     * @param requestIds 药品请求主键集合
     * @return 费用态文案与单价映射；无 id 时返回空映射
     */
    private TcmLineChargeEnrich loadLineChargeEnrich(Set<Long> requestIds) {
        Map<Long, String> statusTextByRequestId = new HashMap<>();
        Map<Long, String> chargeStatusEnumByRequestId = new HashMap<>();
        Map<Long, BigDecimal> unitPriceByRequestId = new HashMap<>();
        if (requestIds == null || requestIds.isEmpty()) {
            return new TcmLineChargeEnrich(statusTextByRequestId, chargeStatusEnumByRequestId, unitPriceByRequestId);
        }
        ChargeItemQueryCommand cmd = new ChargeItemQueryCommand();
        cmd.setServiceIds(new ArrayList<>(requestIds));
        List<ChargeItem> chargeItems = chargeItemQueryService.listChargeItemsByServiceIds(cmd);
        Map<Long, String> rollupStatusByRequestId = new HashMap<>();
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
            String current = rollupStatusByRequestId.get(requestId);
            rollupStatusByRequestId.put(requestId, AdviceChargeDisplayStatusAssembler.mergeChargeStatusForRollup(
                current, chargeItem.getStatusEnum()));
            TcmChargePrimaryRow primary = primaryByRequestId.get(requestId);
            if (primary == null) {
                TcmChargePrimaryRow created = new TcmChargePrimaryRow();
                created.consider(chargeItem);
                primaryByRequestId.put(requestId, created);
            } else {
                // 同一请求多条费用时取 occurrence_time 最新（同秒取 id 较大）的 unit_price
                primary.consider(chargeItem);
            }
        }
        for (Map.Entry<Long, String> entry : rollupStatusByRequestId.entrySet()) {
            chargeStatusEnumByRequestId.put(entry.getKey(), entry.getValue());
            statusTextByRequestId.put(entry.getKey(),
                AdviceChargeDisplayStatusAssembler.resolveChargeStatusDisplayTextForAdviceList(entry.getValue()));
        }
        for (Map.Entry<Long, TcmChargePrimaryRow> entry : primaryByRequestId.entrySet()) {
            if (entry.getValue().unitPrice != null) {
                unitPriceByRequestId.put(entry.getKey(), entry.getValue().unitPrice);
            }
        }
        return new TcmLineChargeEnrich(statusTextByRequestId, chargeStatusEnumByRequestId, unitPriceByRequestId);
    }

    /**
     * 从方聚合读模型取就诊主键（同次列表查询下各方归属同一就诊）。
     */
    private Long resolveEncounterIdFromGroups(List<TcmHerbalPrescriptionGroupQueryModel> groups) {
        if (groups == null) {
            return null;
        }
        for (TcmHerbalPrescriptionGroupQueryModel group : groups) {
            if (group.getLines() == null) {
                continue;
            }
            for (MedicationRequestListQueryModel line : group.getLines()) {
                if (line != null && line.getEncounterId() != null) {
                    return line.getEncounterId();
                }
            }
        }
        return null;
    }

    /**
     * 按处方号归并同方代煎 companion 诊疗收费态（锚定 {@code wkf_service_request}）。
     */
    private Map<String, String> loadDecoctionChargeStatusByPrescriptionNo(
        Long encounterId,
        List<TcmHerbalPrescriptionGroupQueryModel> groups
    ) {
        Map<String, String> result = new HashMap<>();
        if (encounterId == null || groups == null || groups.isEmpty()) {
            return result;
        }
        Set<String> prescriptionNos = new HashSet<>();
        for (TcmHerbalPrescriptionGroupQueryModel group : groups) {
            if (!Boolean.TRUE.equals(group.getSufferingFlag())) {
                continue;
            }
            if (StringUtils.isEmpty(group.getPrescriptionNo())) {
                continue;
            }
            prescriptionNos.add(group.getPrescriptionNo().trim());
        }
        if (prescriptionNos.isEmpty()) {
            return result;
        }
        ChargeItemQueryCommand cmd = new ChargeItemQueryCommand();
        cmd.setEncounterId(encounterId);
        List<ChargeItem> chargeItems = chargeItemQueryService.listChargeItemsByEncounterId(cmd);
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null || Boolean.TRUE.equals(chargeItem.getIsDeleted())) {
                continue;
            }
            if (!TableNameConstants.WKF_SERVICE_REQUEST.equals(chargeItem.getServiceTable())) {
                continue;
            }
            if (StringUtils.isEmpty(chargeItem.getPrescriptionNo())) {
                continue;
            }
            String prescriptionNo = chargeItem.getPrescriptionNo().trim();
            if (!prescriptionNos.contains(prescriptionNo)) {
                continue;
            }
            String current = result.get(prescriptionNo);
            result.put(prescriptionNo, AdviceChargeDisplayStatusAssembler.mergeChargeStatusForRollup(
                current, chargeItem.getStatusEnum()));
        }
        return result;
    }

    /**
     * 方头「状态」列展示态：药材行与代煎 companion 收费退费态优先于请求态。
     */
    private void applyHeaderDisplayStatus(
        TcmRxHeaderDto header,
        TcmHerbalPrescriptionGroupQueryModel group,
        TcmLineChargeEnrich chargeEnrich,
        Map<String, String> decoctionChargeByPrescriptionNo
    ) {
        String headerChargeStatus = null;
        if (group.getLines() != null) {
            for (MedicationRequestListQueryModel line : group.getLines()) {
                if (line == null || line.getId() == null) {
                    continue;
                }
                headerChargeStatus = AdviceChargeDisplayStatusAssembler.mergeChargeStatusForRollup(
                    headerChargeStatus, chargeEnrich.chargeStatusEnumByRequestId().get(line.getId()));
            }
        }
        if (Boolean.TRUE.equals(group.getSufferingFlag()) && StringUtils.isNotEmpty(group.getPrescriptionNo())) {
            String decoctionCharge = decoctionChargeByPrescriptionNo.get(group.getPrescriptionNo().trim());
            headerChargeStatus = AdviceChargeDisplayStatusAssembler.mergeChargeStatusForRollup(
                headerChargeStatus, decoctionCharge);
        }
        String requestStatus = StringUtils.defaultIfBlank(header.getStatusEnum(), "");
        RequestStatus parsedRequestStatus = EnumUtil.fromCode(RequestStatus.class, requestStatus);
        String requestStatusDisplay;
        if (parsedRequestStatus != null) {
            requestStatusDisplay = parsedRequestStatus.getDisplay();
        } else {
            requestStatusDisplay = StringUtils.defaultIfBlank(requestStatus, "");
        }
        AdviceChargeDisplayStatusAssembler.ListDisplayStatus display =
            AdviceChargeDisplayStatusAssembler.resolveAdviceListDisplayStatus(
                requestStatus, requestStatusDisplay, headerChargeStatus);
        header.setDisplayStatusEnum(display.displayStatusEnum());
        header.setDisplayStatusEnumText(display.displayStatusEnumText());
    }

    /** 中药行费用 enrich 中间态：展示文案 + 收费态码 + 已落库单价。 */
    private record TcmLineChargeEnrich(
        Map<Long, String> statusTextByRequestId,
        Map<Long, String> chargeStatusEnumByRequestId,
        Map<Long, BigDecimal> unitPriceByRequestId
    ) {
    }

    /** 同一药品请求下代表 ChargeItem 行（按发生时间与主键择优）。 */
    private static final class TcmChargePrimaryRow {
        private Long chargeItemId;
        private LocalDateTime occurrenceTime;
        private BigDecimal unitPrice;

        /**
         * 择优合并同一请求下的 ChargeItem：发生时间较新优先，同秒取 id 较大者的单价。
         *
         * @param item 待合并费用行
         */
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
