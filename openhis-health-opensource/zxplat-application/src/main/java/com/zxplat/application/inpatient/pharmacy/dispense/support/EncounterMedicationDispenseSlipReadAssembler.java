/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.pharmacy.dispense.support;

import com.zxplat.application.shared.advice.OrderCatalogDisplayAssembler;
import com.zxplat.application.shared.advice.TcmMethodCodeDisplayAssembler;
import com.zxplat.application.shared.pagination.PagedResultBuilder;
import com.zxplat.application.shared.product.MedicationPackageSplitQuantityDisplayAssembler;
import com.zxplat.application.inpatient.pharmacy.dispense.dto.EncounterMedicationDispenseSlipRowDto;
import com.zxplat.application.inpatient.pharmacy.dispense.dto.MedicationDispenseInventoryDetailDto;
import com.zxplat.application.inpatient.pharmacy.dispense.dto.MedicationDispenseSlipPageResult;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.domain.medication.medicationdispense.queries.model.MedicationDispenseSlipQueryModel;
import com.zxplat.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.zxplat.domain.share.constants.MedicationCommonConstants;
import com.zxplat.domain.share.enums.MedicationDispenseType;
import com.zxplat.domain.share.enums.TherapyType;
import com.zxplat.domain.share.utils.EnumUtil;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 就诊药品发药单 slip 行读模型装配（门诊 IF-OPP-03 / 住院 IF-IPP-03 共用）。
 *
 * <p>职责边界：无业务流程分支与不变量；null 安全回落为空集合或空分页壳（B.1 / A.4）。</p>
 */
@Component("inpatientEncounterMedicationDispenseSlipReadAssembler")
public class EncounterMedicationDispenseSlipReadAssembler {

    /**
     * 门诊发药单分页装配（含候选库存明细）。
     */
    @SuppressWarnings("unused")
    public MedicationDispenseSlipPageResult assembleOutpatientSlipPage(
        Page<MedicationDispenseSlipQueryModel> slipPage,
        Map<Long, MedicationRequestListQueryModel> reqByMedReqId,
        Map<Long, ProductDefinitionDisplayDto> productByMedicationId,
        Map<Long, List<MedicationDispenseInventoryDetailDto>> inventoryDetailListByDispenseId,
        Boolean tcmOnly,
        Boolean westernOnly,
        Map<Long, String> dispensingPractitionerNameByDispenseId,
        String encounterBusNo,
        Map<Long, String> orderingDepartmentNameByOrgId,
        Map<Long, String> orderingPractitionerNameById,
        Map<Long, String> dispenseLocationNameByLocationId,
        Map<Long, BigDecimal> chargeUnitPriceByDispenseId,
        Map<Long, BigDecimal> chargeTotalPriceByDispenseId,
        Map<Long, String> traceNoEffectiveByDispenseId,
        Map<String, String> rateCodeLabelMap,
        Map<String, String> methodCodeLabelMap,
        Map<String, String> tcmMethodCodeLabelMap,
        Map<String, String> doseUnitLabelMap,
        Map<String, String> businessUnitLabelMap,
        Map<String, String> rxFlagLabelMap,
        Map<String, String> partAttributeLabelMap,
        Map<String, String> dosageInstructionLabelMap) {
        SlipAssemblyContext context = SlipAssemblyContext.of(
            reqByMedReqId,
            productByMedicationId,
            dispensingPractitionerNameByDispenseId,
            encounterBusNo,
            orderingDepartmentNameByOrgId,
            orderingPractitionerNameById,
            dispenseLocationNameByLocationId,
            chargeUnitPriceByDispenseId,
            chargeTotalPriceByDispenseId,
            traceNoEffectiveByDispenseId,
            null,
            inventoryDetailListByDispenseId,
            rateCodeLabelMap,
            methodCodeLabelMap,
            tcmMethodCodeLabelMap,
            doseUnitLabelMap,
            businessUnitLabelMap,
            rxFlagLabelMap,
            partAttributeLabelMap,
            dosageInstructionLabelMap);
        return buildPageResult(slipPage, context, true, tcmOnly, westernOnly);
    }

    /**
     * 住院发药单分页装配（不含候选库存；支持批号有效值覆盖）。
     */
    @SuppressWarnings("unused")
    public MedicationDispenseSlipPageResult assembleInpatientSlipPage(
        Page<MedicationDispenseSlipQueryModel> slipPage,
        Map<Long, MedicationRequestListQueryModel> reqByMedReqId,
        Map<Long, ProductDefinitionDisplayDto> productByMedicationId,
        Boolean tcmOnly,
        Boolean westernOnly,
        Map<Long, String> dispensingPractitionerNameByDispenseId,
        String encounterBusNo,
        Map<Long, String> orderingDepartmentNameByOrgId,
        Map<Long, String> orderingPractitionerNameById,
        Map<Long, String> dispenseLocationNameByLocationId,
        Map<Long, String> traceNoEffectiveByDispenseId,
        Map<Long, String> lotNumberEffectiveByDispenseId,
        Map<Long, BigDecimal> chargeUnitPriceByDispenseId,
        Map<Long, BigDecimal> chargeTotalPriceByDispenseId,
        Map<String, String> rateCodeLabelMap,
        Map<String, String> methodCodeLabelMap,
        Map<String, String> doseUnitLabelMap,
        Map<String, String> businessUnitLabelMap,
        Map<String, String> rxFlagLabelMap,
        Map<String, String> partAttributeLabelMap,
        Map<String, String> dosageInstructionLabelMap) {
        return assembleInpatientSlipPage(
            slipPage,
            reqByMedReqId,
            productByMedicationId,
            tcmOnly,
            westernOnly,
            dispensingPractitionerNameByDispenseId,
            encounterBusNo,
            orderingDepartmentNameByOrgId,
            orderingPractitionerNameById,
            dispenseLocationNameByLocationId,
            traceNoEffectiveByDispenseId,
            lotNumberEffectiveByDispenseId,
            chargeUnitPriceByDispenseId,
            chargeTotalPriceByDispenseId,
            rateCodeLabelMap,
            methodCodeLabelMap,
            Map.of(),
            doseUnitLabelMap,
            businessUnitLabelMap,
            rxFlagLabelMap,
            partAttributeLabelMap,
            dosageInstructionLabelMap);
    }

    /**
     * 住院发药单分页装配（含中药用法字典；供门诊发药读侧复用，住院应用层不传入中药用法）。
     */
    @SuppressWarnings("unused")
    public MedicationDispenseSlipPageResult assembleInpatientSlipPage(
        Page<MedicationDispenseSlipQueryModel> slipPage,
        Map<Long, MedicationRequestListQueryModel> reqByMedReqId,
        Map<Long, ProductDefinitionDisplayDto> productByMedicationId,
        Boolean tcmOnly,
        Boolean westernOnly,
        Map<Long, String> dispensingPractitionerNameByDispenseId,
        String encounterBusNo,
        Map<Long, String> orderingDepartmentNameByOrgId,
        Map<Long, String> orderingPractitionerNameById,
        Map<Long, String> dispenseLocationNameByLocationId,
        Map<Long, String> traceNoEffectiveByDispenseId,
        Map<Long, String> lotNumberEffectiveByDispenseId,
        Map<Long, BigDecimal> chargeUnitPriceByDispenseId,
        Map<Long, BigDecimal> chargeTotalPriceByDispenseId,
        Map<String, String> rateCodeLabelMap,
        Map<String, String> methodCodeLabelMap,
        Map<String, String> tcmMethodCodeLabelMap,
        Map<String, String> doseUnitLabelMap,
        Map<String, String> businessUnitLabelMap,
        Map<String, String> rxFlagLabelMap,
        Map<String, String> partAttributeLabelMap,
        Map<String, String> dosageInstructionLabelMap) {
        SlipAssemblyContext context = SlipAssemblyContext.of(
            reqByMedReqId,
            productByMedicationId,
            dispensingPractitionerNameByDispenseId,
            encounterBusNo,
            orderingDepartmentNameByOrgId,
            orderingPractitionerNameById,
            dispenseLocationNameByLocationId,
            chargeUnitPriceByDispenseId,
            chargeTotalPriceByDispenseId,
            traceNoEffectiveByDispenseId,
            lotNumberEffectiveByDispenseId,
            null,
            rateCodeLabelMap,
            methodCodeLabelMap,
            tcmMethodCodeLabelMap,
            doseUnitLabelMap,
            businessUnitLabelMap,
            rxFlagLabelMap,
            partAttributeLabelMap,
            dosageInstructionLabelMap);
        return buildPageResult(slipPage, context, false, tcmOnly, westernOnly);
    }

    /**
     * 装配 slip 分页行，并按 {@code tcmOnly}/{@code westernOnly} 做中药维度过滤（与前端 tab 口径一致）。
     *
     * @param slipPage       发放单分页读模型
     * @param context        批量预热上下文
     * @param outpatientMode 是否门诊模式（术语回落口径）
     * @param tcmOnly        {@code true} 仅中药饮片
     * @param westernOnly    {@code true} 仅西药中成药
     * @return 过滤后的分页结果
     */
    private MedicationDispenseSlipPageResult buildPageResult(
        Page<MedicationDispenseSlipQueryModel> slipPage,
        SlipAssemblyContext context,
        boolean outpatientMode,
        Boolean tcmOnly,
        Boolean westernOnly) {
        List<EncounterMedicationDispenseSlipRowDto> content = new ArrayList<>();
        if (slipPage == null) {
            return PagedResultBuilder.build(new MedicationDispenseSlipPageResult(), content, null);
        }
        boolean filterByRxKind = Boolean.TRUE.equals(tcmOnly) || Boolean.TRUE.equals(westernOnly);
        for (MedicationDispenseSlipQueryModel slip : slipPage.getContent()) {
            if (slip == null) {
                continue;
            }
            EncounterMedicationDispenseSlipRowDto row = assembleRow(slip, context, outpatientMode);
            if (!filterByRxKind || matchesTcmWesternFilter(row, tcmOnly, westernOnly)) {
                content.add(row);
            }
        }
        if (filterByRxKind) {
            // 就诊维度单页装配后按中药标识收窄；totalCount 与当前 content 对齐
            return PagedResultBuilder.buildList(new MedicationDispenseSlipPageResult(), content);
        }
        return PagedResultBuilder.build(new MedicationDispenseSlipPageResult(), content, slipPage);
    }

    /**
     * 判定 slip 行是否属于中药饮片（与历史-pro-ui {@code isHerbalSlipRow} 同口径）。
     *
     * @param row 已装配发放行
     * @return {@code true} 视为中药饮片
     */
    private static boolean isHerbalSlipRow(EncounterMedicationDispenseSlipRowDto row) {
        if (row == null) {
            return false;
        }
        if (Boolean.TRUE.equals(row.getTcmFlag())) {
            return true;
        }
        if (Boolean.FALSE.equals(row.getTcmFlag())) {
            return false;
        }
        StringBuilder text = new StringBuilder();
        if (StringUtils.isNotEmpty(row.getTcmFlagText())) {
            text.append(row.getTcmFlagText());
        }
        if (StringUtils.isNotEmpty(row.getMedicationCategoryText())) {
            text.append(row.getMedicationCategoryText());
        }
        return text.indexOf(MedicationCommonConstants.TCM_CLASS_DISPLAY_TCM) >= 0;
    }

    /**
     * 中药 / 西药中成药 tab 过滤：二者均空则不过滤；{@code tcmOnly} 优先于 {@code westernOnly}。
     *
     * @param row         已装配发放行
     * @param tcmOnly     仅中药
     * @param westernOnly 仅非中药
     * @return 是否保留该行
     */
    private static boolean matchesTcmWesternFilter(
        EncounterMedicationDispenseSlipRowDto row,
        Boolean tcmOnly,
        Boolean westernOnly) {
        if (Boolean.TRUE.equals(tcmOnly)) {
            return isHerbalSlipRow(row);
        }
        if (Boolean.TRUE.equals(westernOnly)) {
            return !isHerbalSlipRow(row);
        }
        return true;
    }

    /**
     * 单条药品发放 slip 装配为工作站展示行：合并药嘱、产品、术语与门诊专用回落。
     *
     * @param slip            发放 slip 读模型
     * @param context         批量预加载的装配上下文
     * @param outpatientMode  是否门诊模式（影响途径/单位回落与库存明细）
     * @return 完整展示行 DTO
     */
    private EncounterMedicationDispenseSlipRowDto assembleRow(
        MedicationDispenseSlipQueryModel slip,
        SlipAssemblyContext context,
        boolean outpatientMode) {
        MedicationRequestListQueryModel req = context.reqMap().get(slip.getMedReqId());
        EncounterMedicationDispenseSlipRowDto row = new EncounterMedicationDispenseSlipRowDto();
        row.setEncounterId(slip.getEncounterId());
        row.setEncounterBusNo(context.encounterBusNo());
        row.setDispenseId(slip.getDispenseId());
        row.setMedReqId(slip.getMedReqId());
        // 住院日分单锚点：执行主键；recorded_time 由 Loader 批量回填
        row.setProcedureId(slip.getProcedureId());
        row.setMedicationId(slip.getMedicationId());
        row.setDispenseLocationId(slip.getLocationId());
        if (slip.getLocationId() != null) {
            row.setDispenseLocationName(context.locNames().get(slip.getLocationId()));
        }
        row.setLotNumber(slip.getLotNumber());
        if (context.lotMap() != null && slip.getDispenseId() != null) {
            String lotEffective = context.lotMap().get(slip.getDispenseId());
            if (StringUtils.isNotEmpty(lotEffective)) {
                row.setLotNumber(lotEffective);
            }
        }
        String traceEffective = null;
        if (slip.getDispenseId() != null) {
            traceEffective = context.traceMap().get(slip.getDispenseId());
        }
        if (StringUtils.isNotEmpty(traceEffective)) {
            row.setTraceNo(traceEffective);
        } else {
            row.setTraceNo(slip.getTraceNo());
        }
        String dispensingPractitionerName = null;
        if (slip.getDispenseId() != null) {
            dispensingPractitionerName = context.dispenserNames().get(slip.getDispenseId());
        }
        row.setDispensingPractitionerName(dispensingPractitionerName);
        if (slip.getStatusEnum() != null) {
            row.setStatusEnum(slip.getStatusEnum().getCode());
            row.setStatusEnumText(slip.getStatusEnum().getDisplay());
        }
        MedicationDispenseType dispenseKind = slip.getDispenseEnum();
        if (dispenseKind != null) {
            row.setDispenseEnum(dispenseKind.getCode());
            row.setDispenseEnumText(dispenseKind.getDisplay());
        }
        row.setQuantity(slip.getQuantity());
        row.setUnitCode(slip.getUnitCode());
        row.setBusNo(slip.getBusNo());
        // 分区块展示以药嘱 prescription_no 为准；发放 slip 读模型未 join 时从已批量加载的 req 回填
        String prescriptionNo = slip.getPrescriptionNo();
        if (StringUtils.isEmpty(prescriptionNo) && req != null) {
            prescriptionNo = req.getPrescriptionNo();
        }
        row.setPrescriptionNo(prescriptionNo);
        // 开单医生：slip 投影优先；住院发放行常无 medRequesterId，回落药嘱开立人
        Long orderingPractitionerId = slip.getMedRequesterId();
        if (orderingPractitionerId == null && req != null) {
            orderingPractitionerId = req.getRequesterId();
        }
        row.setOrderingPractitionerId(orderingPractitionerId);
        if (orderingPractitionerId != null) {
            row.setOrderingPractitionerName(context.orderingDrNames().get(orderingPractitionerId));
        }
        if (outpatientMode && context.invMap() != null && slip.getDispenseId() != null) {
            row.setInventoryDetailList(context.invMap().get(slip.getDispenseId()));
        }
        if (slip.getDispenseId() != null) {
            row.setUnitPrice(context.unitPriceMap().get(slip.getDispenseId()));
            row.setTotalPrice(context.totalPriceMap().get(slip.getDispenseId()));
        }

        ProductDefinitionDisplayDto product = context.prodMap().get(slip.getMedicationId());
        if (product != null) {
            row.setMedicationName(product.getProductName());
            row.setMerchandiseName(product.getMerchandiseName());
            row.setSpecificationText(product.getTotalVolume());
            row.setManufacturerName(product.getManufacturer());
            row.setProductTableName(product.getProductTableName());
            row.setMedicationCategoryCode(product.getCategoryCode());
            row.setMedicationCategoryText(product.getCategoryCodeDictText());
            row.setSplitRatio(product.getDoseUnitRatio());
            // 包装/拆零单位：住院发药应扫码数换算（与门诊同口径）
            row.setPackageUnitCode(product.getPackageUnitCode());
            row.setPackageUnitRatio(product.getPackageUnitRatio());
            row.setSplitUnitCode(product.getSplitUnitCode());
            // Asm：包装↔拆零比例文案（规格列旁展示；与库存明细 / 门诊发药同族）
            row.setPackageSplitRatioText(
                MedicationPackageSplitQuantityDisplayAssembler.buildInventoryPackageSplitRatioText(product));
            // 药典贴码单位/比例：主档无快照时回落；有主档时由 enricher 覆盖
            if (StringUtils.isNotEmpty(product.getTraceUnitCode())) {
                row.setTraceUnitCode(product.getTraceUnitCode());
                row.setTraceUnitRatio(product.getTraceUnitRatio());
                row.setTraceUnitCodeText(product.getTraceUnitCodeDictText());
            }
            if (StringUtils.isNotEmpty(product.getPartAttributeEnum())) {
                row.setPartAttributeCode(product.getPartAttributeEnum());
                row.setPartAttributeText(
                    OrderCatalogDisplayAssembler.resolveCategoryDisplay(
                        context.partLabels(), product.getPartAttributeEnum()));
            }
        }

        if (req != null) {
            row.setOrderingDepartmentId(req.getOrgId());
            if (req.getOrgId() != null) {
                row.setOrderingDepartmentName(context.deptNames().get(req.getOrgId()));
            }
            row.setRequestQuantity(req.getQuantity());
            // 中药付数：处方笺「N付」展示用；与 requestQuantity（请求总量）分列
            if (Boolean.TRUE.equals(req.getTcmFlag())) {
                row.setChineseHerbsDoseQuantity(req.getChineseHerbsDoseQuantity());
            }
            row.setFrequencyCode(req.getRateCode());
            row.setRouteCode(req.getMethodCode());
            row.setDoseValue(req.getDose());
            row.setDoseUnitCode(req.getDoseUnitCode());
            row.setDispensePerSupplyDays(req.getDispensePerDuration());
            row.setPrescriptionGroupId(req.getGroupId());
            LocalDateTime requestOpenedTime = req.getReqOpenedTime();
            // 发药 slip 展示主轴：开立时刻优先，空回落签发追溯列。
            if (requestOpenedTime == null) {
                requestOpenedTime = req.getReqAuthoredTime();
            }
            row.setRequestOpenedTime(requestOpenedTime);
            row.setEncounterMedicationTypeCode(req.getRxTypeCode());
            row.setEncounterMedicationTypeText(
                OrderCatalogDisplayAssembler.resolveCategoryDisplay(context.rxLabels(), req.getRxTypeCode()));
            row.setTherapyEnum(req.getTherapyEnum());
            TherapyType therapyType = EnumUtil.fromCode(TherapyType.class, req.getTherapyEnum());
            if (therapyType != null) {
                row.setTherapyEnumText(therapyType.getDisplay());
            }
            row.setFrequencyText(
                OrderCatalogDisplayAssembler.resolveCategoryDisplay(context.rateLabels(), req.getRateCode()));
            row.setDosageInstructionCode(req.getDosageInstruction());
            row.setDosageInstructionText(
                OrderCatalogDisplayAssembler.resolveCategoryDisplay(
                    context.dosageInstructionLabels(), req.getDosageInstruction()));
            if (outpatientMode) {
                row.setRouteText(TcmMethodCodeDisplayAssembler.resolveMethodDisplayName(
                    context.tcmMethodLabels(), context.methodLabels(), req.getTcmFlag(), req.getMethodCode()));
            } else {
                row.setRouteText(
                    OrderCatalogDisplayAssembler.resolveCategoryDisplay(context.methodLabels(), req.getMethodCode()));
            }
            if (outpatientMode) {
                applyOutpatientTerminologyFallbacks(row, req, product, slip, context);
            } else {
                row.setDoseUnitText(
                    OrderCatalogDisplayAssembler.resolveCategoryDisplay(context.doseLabels(), req.getDoseUnitCode()));
                row.setUnitText(
                    OrderCatalogDisplayAssembler.resolveCategoryDisplay(context.bizUnitLabels(), slip.getUnitCode()));
            }
            applyRequestBooleanFlags(row, req, outpatientMode);
        } else if (outpatientMode && product != null) {
            row.setTcmFlag(Boolean.FALSE);
            row.setTcmFlagText(MedicationCommonConstants.TCM_CLASS_DISPLAY_WESTERN_AND_PATENT);
        }
        return row;
    }

    /**
     * 门诊发放行剂量/业务单位术语回落：术语未命中时按产品定义单位码匹配展示名。
     *
     * @param row     待回填行（就地修改）
     * @param req     关联药嘱
     * @param product 药品产品定义展示
     * @param slip    发放 slip（取业务单位码）
     * @param context 术语标签映射
     */
    private static void applyOutpatientTerminologyFallbacks(
        EncounterMedicationDispenseSlipRowDto row,
        MedicationRequestListQueryModel req,
        ProductDefinitionDisplayDto product,
        MedicationDispenseSlipQueryModel slip,
        SlipAssemblyContext context) {
        String doseUnitCodeForLabel = req.getDoseUnitCode();
        String doseText = OrderCatalogDisplayAssembler.resolveCategoryDisplay(context.doseLabels(), doseUnitCodeForLabel);
        if (StringUtils.isEmpty(doseText) && product != null) {
            if (Objects.equals(doseUnitCodeForLabel, product.getDoseUnitCode())) {
                doseText = product.getDoseUnitCodeDictText();
            }
        }
        row.setDoseUnitText(doseText);
        String slipUnit = slip.getUnitCode();
        String unitTextResolved = OrderCatalogDisplayAssembler.resolveCategoryDisplay(context.bizUnitLabels(), slipUnit);
        if (StringUtils.isEmpty(unitTextResolved) && product != null) {
            if (Objects.equals(slipUnit, product.getPurchaseUnitCode())) {
                unitTextResolved = product.getPurchaseUnitCodeDictText();
            }
            if (StringUtils.isEmpty(unitTextResolved) && Objects.equals(slipUnit, product.getStockUnitCode())) {
                unitTextResolved = product.getStockUnitCodeDictText();
            }
            if (StringUtils.isEmpty(unitTextResolved) && Objects.equals(slipUnit, product.getSplitUnitCode())) {
                unitTextResolved = product.getSplitUnitCodeDictText();
            }
            if (StringUtils.isEmpty(unitTextResolved) && Objects.equals(slipUnit, product.getPackageUnitCode())) {
                unitTextResolved = product.getPackageUnitCodeDictText();
            }
            if (StringUtils.isEmpty(unitTextResolved) && Objects.equals(slipUnit, product.getDefEncounterUnitCode())) {
                unitTextResolved = product.getDefEncounterUnitCodeDictText();
            }
        }
        row.setUnitText(unitTextResolved);
    }

    /**
     * 将药嘱中药/皮试/输液标记及对应工作站展示文案回填到发放行。
     *
     * @param row            待回填行（就地修改）
     * @param req            关联药嘱
     * @param outpatientMode 门诊模式下 null 标记展示为 null 文案
     */
    private static void applyRequestBooleanFlags(
        EncounterMedicationDispenseSlipRowDto row,
        MedicationRequestListQueryModel req,
        boolean outpatientMode) {
        row.setTcmFlag(req.getTcmFlag());
        if (Boolean.TRUE.equals(req.getTcmFlag())) {
            row.setTcmFlagText(MedicationCommonConstants.TCM_CLASS_DISPLAY_TCM);
        } else if (outpatientMode) {
            if (req.getTcmFlag() == null) {
                row.setTcmFlagText(null);
            } else {
                row.setTcmFlagText(MedicationCommonConstants.TCM_CLASS_DISPLAY_WESTERN_AND_PATENT);
            }
        } else if (req.getTcmFlag() != null) {
            row.setTcmFlagText(MedicationCommonConstants.TCM_CLASS_DISPLAY_WESTERN_AND_PATENT);
        }
        row.setSkinTestFlag(req.getSkinTestFlag());
        if (Boolean.TRUE.equals(req.getSkinTestFlag())) {
            row.setSkinTestFlagText(MedicationCommonConstants.WORKSTATION_BOOLEAN_DISPLAY_YES);
        } else if (outpatientMode) {
            if (req.getSkinTestFlag() == null) {
                row.setSkinTestFlagText(null);
            } else {
                row.setSkinTestFlagText(MedicationCommonConstants.WORKSTATION_BOOLEAN_DISPLAY_NO);
            }
        } else if (req.getSkinTestFlag() != null) {
            row.setSkinTestFlagText(MedicationCommonConstants.WORKSTATION_BOOLEAN_DISPLAY_NO);
        }
        row.setInfusionFlag(req.getInfusionFlag());
        if (Boolean.TRUE.equals(req.getInfusionFlag())) {
            row.setInfusionFlagText(MedicationCommonConstants.WORKSTATION_BOOLEAN_DISPLAY_YES);
        } else if (outpatientMode) {
            if (req.getInfusionFlag() == null) {
                row.setInfusionFlagText(null);
            } else {
                row.setInfusionFlagText(MedicationCommonConstants.WORKSTATION_BOOLEAN_DISPLAY_NO);
            }
        } else if (req.getInfusionFlag() != null) {
            row.setInfusionFlagText(MedicationCommonConstants.WORKSTATION_BOOLEAN_DISPLAY_NO);
        }
    }

    /**
     * 药品发放 slip 批量装配上下文：药嘱、产品、人员、术语与价格等预加载索引。
     */
    private record SlipAssemblyContext(
        Map<Long, MedicationRequestListQueryModel> reqMap,
        Map<Long, ProductDefinitionDisplayDto> prodMap,
        Map<Long, String> dispenserNames,
        String encounterBusNo,
        Map<Long, String> deptNames,
        Map<Long, String> orderingDrNames,
        Map<Long, String> locNames,
        Map<Long, BigDecimal> unitPriceMap,
        Map<Long, BigDecimal> totalPriceMap,
        Map<Long, String> traceMap,
        Map<Long, String> lotMap,
        Map<Long, List<MedicationDispenseInventoryDetailDto>> invMap,
        Map<String, String> rateLabels,
        Map<String, String> methodLabels,
        Map<String, String> tcmMethodLabels,
        Map<String, String> doseLabels,
        Map<String, String> bizUnitLabels,
        Map<String, String> rxLabels,
        Map<String, String> partLabels,
        Map<String, String> dosageInstructionLabels) {

        static SlipAssemblyContext of(
            Map<Long, MedicationRequestListQueryModel> reqByMedReqId,
            Map<Long, ProductDefinitionDisplayDto> productByMedicationId,
            Map<Long, String> dispensingPractitionerNameByDispenseId,
            String encounterBusNo,
            Map<Long, String> orderingDepartmentNameByOrgId,
            Map<Long, String> orderingPractitionerNameById,
            Map<Long, String> dispenseLocationNameByLocationId,
            Map<Long, BigDecimal> chargeUnitPriceByDispenseId,
            Map<Long, BigDecimal> chargeTotalPriceByDispenseId,
            Map<Long, String> traceNoEffectiveByDispenseId,
            Map<Long, String> lotNumberEffectiveByDispenseId,
            Map<Long, List<MedicationDispenseInventoryDetailDto>> inventoryDetailListByDispenseId,
            Map<String, String> rateCodeLabelMap,
            Map<String, String> methodCodeLabelMap,
            Map<String, String> tcmMethodCodeLabelMap,
            Map<String, String> doseUnitLabelMap,
            Map<String, String> businessUnitLabelMap,
            Map<String, String> rxFlagLabelMap,
            Map<String, String> partAttributeLabelMap,
            Map<String, String> dosageInstructionLabelMap) {
            return new SlipAssemblyContext(
                Objects.requireNonNullElse(reqByMedReqId, Map.of()),
                Objects.requireNonNullElse(productByMedicationId, Map.of()),
                Objects.requireNonNullElse(dispensingPractitionerNameByDispenseId, Map.of()),
                encounterBusNo,
                Objects.requireNonNullElse(orderingDepartmentNameByOrgId, Map.of()),
                Objects.requireNonNullElse(orderingPractitionerNameById, Map.of()),
                Objects.requireNonNullElse(dispenseLocationNameByLocationId, Map.of()),
                Objects.requireNonNullElse(chargeUnitPriceByDispenseId, Map.of()),
                Objects.requireNonNullElse(chargeTotalPriceByDispenseId, Map.of()),
                Objects.requireNonNullElse(traceNoEffectiveByDispenseId, Map.of()),
                lotNumberEffectiveByDispenseId,
                Objects.requireNonNullElse(inventoryDetailListByDispenseId, Map.of()),
                Objects.requireNonNullElse(rateCodeLabelMap, Map.of()),
                Objects.requireNonNullElse(methodCodeLabelMap, Map.of()),
                Objects.requireNonNullElse(tcmMethodCodeLabelMap, Map.of()),
                Objects.requireNonNullElse(doseUnitLabelMap, Map.of()),
                Objects.requireNonNullElse(businessUnitLabelMap, Map.of()),
                Objects.requireNonNullElse(rxFlagLabelMap, Map.of()),
                Objects.requireNonNullElse(partAttributeLabelMap, Map.of()),
                Objects.requireNonNullElse(dosageInstructionLabelMap, Map.of()));
        }
    }
}
