/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.chinesemedicine.assembler;

import com.zxplat.application.inpatient.doctorstation.chinesemedicine.dto.TcmRxHeaderDto;
import com.zxplat.application.inpatient.doctorstation.chinesemedicine.dto.TcmRxHistoryRowDto;
import com.zxplat.application.inpatient.doctorstation.chinesemedicine.dto.TcmRxLineDto;
import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.zxplat.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.zxplat.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.zxplat.domain.administration.practitioner.entity.Practitioner;
import com.zxplat.domain.administration.practitioner.service.PractitionerQueryService;
import com.zxplat.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.zxplat.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.zxplat.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.zxplat.domain.medication.medicationrequest.queries.model.TcmHerbalPrescriptionGroupQueryModel;
import com.zxplat.domain.medication.medicationrequest.queries.model.TcmHerbalPrescriptionHistoryGroupQueryModel;
import com.zxplat.domain.share.constants.ProductDefinitionCatalogUnitBandConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.zxplat.domain.share.query.ProductDefinitionBatchLookup;
import com.zxplat.domain.share.utils.ProductDefinitionKeyUtil;
import com.zxplat.application.shared.advice.TcmMethodCodeDisplayAssembler;
import com.zxplat.application.shared.financial.ContractExpenseNatureAssembler;
import com.zxplat.application.shared.option.FrequencyOptionLoader;
import com.zxplat.application.shared.option.TerminologyOptionLoader;
import com.zxplat.application.shared.advice.OrderCatalogDisplayAssembler;
import com.zxplat.application.shared.product.ClinicalCatalogPriceResolver;
import com.zxplat.application.shared.product.ClinicalCatalogUnitPriceQuery;
import com.zxplat.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.zxplat.domain.share.product.dto.ProductDefinitionCatalogUnitBandDto;
import com.zxplat.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import lombok.RequiredArgsConstructor;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import static com.zxplat.framework.core.application.util.StringUtils.trimToEmpty;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
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
    private final PractitionerQueryService practitionerQueryService;
    private final TerminologyOptionLoader terminologyOptionLoader;
    private final FrequencyOptionLoader frequencyOptionLoader;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    private final ClinicalCatalogPriceResolver clinicalCatalogPriceResolver;
    /** 合同费用性质分类文案（行级 contract_id → 自费/医保等）。 */
    private final ContractExpenseNatureAssembler contractExpenseNatureAssembler;

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
        // 方列表按开立时间（reqOpenedTime）倒序，与住院医嘱统一列表口径一致。
        List<TcmHerbalPrescriptionGroupQueryModel> orderedGroups = new ArrayList<>(groups);
        orderedGroups.sort(Comparator
            .comparing(TcmHerbalPrescriptionGroupQueryModel::getReqOpenedTime,
                Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(TcmHerbalPrescriptionGroupQueryModel::getPrescriptionNo,
                Comparator.nullsLast(Comparator.naturalOrder())));
        Set<Long> medicationIds = new HashSet<>();
        Set<Long> requestIds = new HashSet<>();
        Set<Long> contractIds = new HashSet<>();
        for (TcmHerbalPrescriptionGroupQueryModel group : orderedGroups) {
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
                if (line.getContractId() != null) {
                    contractIds.add(line.getContractId());
                }
            }
        }
        Map<Long, String> medicationNameById = loadMedicationNames(medicationIds);
        // Pre：行级费用性质文案（contract_id → 自费/公费/省医保等）
        Map<Long, String> feeNatureTextByContractId =
            this.contractExpenseNatureAssembler.mapCategoryTextByContractIdIn(contractIds);
        // Pre：批量加载药嘱锚定的 ChargeItem，供已计费行展示账单单价（BR-PRICE-SRC-001）
        TcmLineChargeEnrich chargeEnrich = loadLineChargeEnrich(requestIds);
        Map<String, String> tcmMethodLabelByCode =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.TCM_METHOD_CODE);
        Map<String, String> westernMethodLabelByCode =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.METHOD_CODE);
        Map<String, String> rateLabelByCode = frequencyOptionLoader.buildRateLabelMap();
        Map<String, String> dosageInstructionLabelByCode =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.DOSAGE_INSTRUCTION);
        Map<String, String> unitLabelByCode =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.UNIT_CODE);
        Map<String, String> doseUnitLabelByCode =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.DOSE_UNIT);
        Map<String, ProductDefinitionDisplayDto> productByKey = loadMedicationProducts(medicationIds);
        Map<String, List<ChargeItemDefinition>> priceByKey =
            clinicalCatalogPriceResolver.loadPriceLinesGrouped(new ArrayList<>(productByKey.values()));
        for (TcmHerbalPrescriptionGroupQueryModel group : orderedGroups) {
            TcmRxHeaderDto header = new TcmRxHeaderDto();
            header.setPrescriptionGroupKey(group.getPrescriptionGroupKey());
            header.setPrescriptionNo(group.getPrescriptionNo());
            header.setTherapyEnum(group.getTherapyEnum());
            header.setTcmSyndromeGroup(group.getTcmSyndromeGroup());
            header.setChineseHerbsDoseQuantity(group.getChineseHerbsDoseQuantity());
            header.setSufferingFlag(group.getSufferingFlag());
            header.setMethodCode(group.getMethodCode());
            header.setMethodName(TcmMethodCodeDisplayAssembler.resolveMethodDisplayName(
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
                header.setDecoctionMethodCode(TcmMethodCodeDisplayAssembler.readContentJsonTextField(
                    firstLine.getContentJson(), "decoctionMethodCode"));
                // 方头诊断多选回显：取组内首行已落库的就诊诊断 ids 串
                header.setEncounterDiagnosisIds(firstLine.getEncounterDiagnosisIds());
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
                    lineDto.setDosageInstructionCode(line.getDosageInstruction());
                    lineDto.setDosageInstructionText(OrderCatalogDisplayAssembler.resolveCategoryDisplay(
                        dosageInstructionLabelByCode, line.getDosageInstruction()));
                    // Asm：行级费用性质（以行为主）
                    lineDto.setContractId(line.getContractId());
                    if (line.getContractId() != null) {
                        lineDto.setFeeNatureText(feeNatureTextByContractId.get(line.getContractId()));
                    }
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
        Set<Long> requesterIds = new LinkedHashSet<>();
        // 先收集饮片与开立人主键，批量查名后再组装行，避免 N+1
        for (TcmHerbalPrescriptionHistoryGroupQueryModel group : groups) {
            if (group.getMedicationIds() != null) {
                medicationIds.addAll(group.getMedicationIds());
            }
            // 方级开立人：有 requesterId 才参与批量姓名加载
            if (group.getRequesterId() != null) {
                requesterIds.add(group.getRequesterId());
            }
        }
        Map<Long, String> medicationNameById = loadMedicationNames(medicationIds);
        Map<Long, String> practitionerNameById = loadPractitionerNames(requesterIds);
        String keywordTrimmed = trimToEmpty(keyword).toLowerCase();
        for (TcmHerbalPrescriptionHistoryGroupQueryModel group : groups) {
            List<String> medicationNames = new ArrayList<>();
            for (Long medicationId : group.getMedicationIds()) {
                String name = medicationNameById.get(medicationId);
                if (StringUtils.isNotEmpty(name)) {
                    medicationNames.add(name);
                }
            }
            String authoredByName = null;
            // 有开立人主键则回填姓名，供列表展示与关键字过滤
            if (group.getRequesterId() != null) {
                authoredByName = practitionerNameById.get(group.getRequesterId());
            }
            if (StringUtils.isNotEmpty(keywordTrimmed)) {
                boolean matched = false;
                if (group.getPrescriptionNo() != null
                    && group.getPrescriptionNo().toLowerCase().contains(keywordTrimmed)) {
                    matched = true;
                }
                // 关键字命中开立人姓名时保留该历史方
                if (!matched && StringUtils.isNotEmpty(authoredByName)
                    && authoredByName.toLowerCase().contains(keywordTrimmed)) {
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
                // 处方号/开立人/药名均未命中：过滤掉该方
                if (!matched) {
                    continue;
                }
            }
            TcmRxHistoryRowDto row = new TcmRxHistoryRowDto();
            row.setPrescriptionNo(group.getPrescriptionNo());
            row.setEncounterId(group.getEncounterId());
            row.setReqOpenedTime(group.getReqOpenedTime());
            row.setAuthoredByName(authoredByName);
            row.setMedicationNames(medicationNames);
            int lineCount = 0;
            if (group.getLineCount() != null) {
                lineCount = group.getLineCount();
            }
            row.setSummaryText(lineCount + "味");
            result.add(row);
        }
        // 历史方按开立时间倒序，与住院医嘱统一列表口径一致。
        result.sort(Comparator
            .comparing(TcmRxHistoryRowDto::getReqOpenedTime, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(TcmRxHistoryRowDto::getPrescriptionNo, Comparator.nullsLast(Comparator.naturalOrder())));
        return result;
    }

    /**
     * 批量加载开立人姓名。
     *
     * @param ids 医务人员主键集合
     * @return id → 姓名
     */
    private Map<Long, String> loadPractitionerNames(Set<Long> ids) {
        Map<Long, String> map = new HashMap<>();
        // 无开立人主键：跳过跨聚合读，避免空集打 PractitionerQueryService
        if (ids == null || ids.isEmpty()) {
            return map;
        }
        List<Practitioner> list = practitionerQueryService.findAllByIds(ids);
        for (Practitioner practitioner : list) {
            // 脏数据或缺主键行：跳过，不污染 id→姓名 Map
            if (practitioner == null || practitioner.getId() == null) {
                continue;
            }
            map.put(practitioner.getId(), practitioner.getName());
        }
        return map;
    }

    /**
     * 批量加载中药饮片产品目录投影（处方列表药名/规格 enrich）。
     *
     * @param medicationIds 药品定义 ID 集合
     * @return 复合键 → 产品展示 DTO
     */
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
        return productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
    }

    /**
     * 批量加载药品定义名称（按主键索引，读药品主表 name）。
     *
     * @param medicationIds 药品定义 ID 集合
     * @return 药品 ID → 名称
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
     * 从目录单位带解析饮片剂量单位零售价：优先 DOSE 槽，再按 doseUnitCode 匹配。
     *
     * @param bands        单位带列表
     * @param doseUnitCode 行上剂量单位 code
     * @return 元/剂量单位；无法解析时 null
     */
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
        if (StringUtils.isEmpty(doseUnitCode)) {
            return null;
        }
        String normalized = doseUnitCode.trim();
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
        cmd.setExcludeSupersededSettledLines(true);
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

        /**
         * 由单条 ChargeItem 初始化代表行。
         *
         * @param item 账单单价来源行
         * @return 代表行中间态
         */
        private static TcmChargePrimaryRow from(ChargeItem item) {
            TcmChargePrimaryRow row = new TcmChargePrimaryRow();
            row.consider(item);
            return row;
        }

        /**
         * 择优合并同一药品请求下多条 ChargeItem：发生时间最新，同时间取主键较大者。
         *
         * @param item 候选账单行
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
