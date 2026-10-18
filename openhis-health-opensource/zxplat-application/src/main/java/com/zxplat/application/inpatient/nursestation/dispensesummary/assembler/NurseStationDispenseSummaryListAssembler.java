/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.dispensesummary.assembler;

import static com.zxplat.framework.core.application.util.StringUtils.trimToEmpty;

import com.zxplat.application.shared.product.MedicationPackageSplitQuantityDisplayAssembler;
import com.zxplat.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.zxplat.application.inpatient.nursestation.dispensesummary.dto.NurseStationCancelExecuteReturnLineDto;
import com.zxplat.application.inpatient.nursestation.dispensesummary.dto.NurseStationCancelExecuteReturnPatientGroupDto;
import com.zxplat.application.inpatient.nursestation.dispensesummary.dto.NurseStationDispenseSummaryOrderRowDto;
import com.zxplat.application.inpatient.nursestation.dispensesummary.dto.NurseStationDispenseSummaryPatientGroupDto;
import com.zxplat.application.inpatient.nursestation.dispensesummary.dto.NurseStationDispenseSummaryPatientPickupGroupDto;
import com.zxplat.application.inpatient.nursestation.dispensesummary.dto.NurseStationDispenseSummaryPatientPickupLineDto;
import com.zxplat.application.inpatient.nursestation.dispensesummary.dto.NurseStationDispenseSummarySlipHeaderDto;
import com.zxplat.application.inpatient.nursestation.dispensesummary.dto.NurseStationDispenseSummarySlipLineDto;
import com.zxplat.application.inpatient.nursestation.dispensesummary.dto.NurseStationDispenseSummaryTimePointDto;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.zxplat.domain.administration.location.entity.Location;
import com.zxplat.domain.clinical.procedure.entity.Procedure;
import com.zxplat.domain.medication.medicationdispense.entity.MedicationDispense;
import com.zxplat.domain.medication.medicationdispense.queries.model.MedicationDispensePickupPendingQueryModel;
import com.zxplat.domain.medication.medicationdispense.queries.model.MedicationDispenseSummaryItemQueryModel;
import com.zxplat.domain.medication.medicationrequest.entity.MedicationRequest;
import com.zxplat.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.zxplat.domain.share.constants.InpatientNurseStationConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.enums.DispenseStatus;
import com.zxplat.domain.share.enums.SupplyStatus;
import com.zxplat.domain.share.enums.TherapyType;
import com.zxplat.domain.share.utils.EnumUtil;
import com.zxplat.domain.share.utils.ProductDefinitionKeyUtil;
import com.zxplat.domain.workflow.supplyrequest.queries.model.MedicationSummarySlipLineQueryModel;
import com.zxplat.domain.workflow.supplyrequest.queries.model.MedicationSummarySlipQueryModel;
import lombok.RequiredArgsConstructor;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;

/**
 * 住院护士站汇总领药列表装配（IF-IP-SUM-01/02/03/09/10 Asm）。
 */
@Component
@RequiredArgsConstructor
public class NurseStationDispenseSummaryListAssembler {

    /**
     * 从护士站执行药嘱列表按中西药 Tab 收窄 medReqId（IF-IP-SUM-01 Pre ⑤）。
     *
     * <p>**BR-IP-SUM-016** / **BR-IP-ORD-029⑥**：一律剔除 {@link TherapyType#DISCHARGE_MEDICATION}
     * （出院带药执行后仅走药房按患者逐行发药，不进待汇总）。</p>
     *
     * @param executeOrders       {@link com.zxplat.domain.medication.medicationrequest.service.MedicationRequestQueryService#listInpatientNurseStationExecuteOrders} 结果
     * @param medicineCategoryTab west / tcm
     * @return 解析后的 medReqId 列表；无命中时返回空表（域 Query 将返回空页）
     */
    public List<Long> resolveMedReqIds(
        List<MedicationRequestListQueryModel> executeOrders,
        String medicineCategoryTab) {
        List<Long> result = new ArrayList<>();
        if (executeOrders == null || executeOrders.isEmpty()) {
            return result;
        }
        String tab = trimToEmpty(medicineCategoryTab);
        for (MedicationRequestListQueryModel row : executeOrders) {
            if (row == null || row.getId() == null) {
                continue;
            }
            // 出院带药不进汇总领药勾选池（执行完 → 药房按患者发药）
            if (TherapyType.DISCHARGE_MEDICATION.getCode().equals(row.getTherapyEnum())) {
                continue;
            }
            if (InpatientNurseStationConstants.MEDICINE_CATEGORY_TAB_WEST.equals(tab)) {
                if (Boolean.TRUE.equals(row.getTcmFlag())) {
                    continue;
                }
            } else if (InpatientNurseStationConstants.MEDICINE_CATEGORY_TAB_TCM.equals(tab)) {
                if (!Boolean.TRUE.equals(row.getTcmFlag())) {
                    continue;
                }
            }
            result.add(row.getId());
        }
        return result;
    }

    /**
     * 待汇总扁平行 → 就诊→医嘱→时间点分组（IF-IP-SUM-01 Asm ⑩；不做数量合并）。
     *
     * @param page                     待汇总发放分页
     * @param medReqById               药品申请索引
     * @param productByKey             产品目录投影
     * @param encounterById            就诊上下文
     * @param locationById             药房位置
     * @param recordedTimeByProcedureId Procedure 主键 → recorded_time（领药时间列展示）
     * @return 就诊分组列表
     */
    public List<NurseStationDispenseSummaryPatientGroupDto> buildPatientGroups(
        Page<MedicationDispensePickupPendingQueryModel> page,
        Map<Long, MedicationRequestListQueryModel> medReqById,
        Map<String, ProductDefinitionDisplayDto> productByKey,
        Map<Long, InpatientEncounterContextQueryModel> encounterById,
        Map<Long, Location> locationById,
        Map<Long, LocalDateTime> recordedTimeByProcedureId) {
        if (page == null || page.getContent() == null || page.getContent().isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, NurseStationDispenseSummaryPatientGroupDto> groupByEncounter = new LinkedHashMap<>();
        Map<Long, Map<Long, NurseStationDispenseSummaryOrderRowDto>> orderRowByEncounterAndMedReq = new LinkedHashMap<>();
        for (MedicationDispensePickupPendingQueryModel line : page.getContent()) {
            if (line == null || line.getEncounterId() == null || line.getMedReqId() == null) {
                continue;
            }
            NurseStationDispenseSummaryPatientGroupDto patientGroup = groupByEncounter.get(line.getEncounterId());
            if (patientGroup == null) {
                patientGroup = new NurseStationDispenseSummaryPatientGroupDto();
                patientGroup.setEncounterId(line.getEncounterId());
                patientGroup.setPatientId(line.getPatientId());
                InpatientEncounterContextQueryModel encounter = encounterById.get(line.getEncounterId());
                if (encounter != null) {
                    patientGroup.setPatientName(encounter.getPatientName());
                    patientGroup.setBedDisplay(encounter.getBedName());
                }
                patientGroup.setOrderRows(new ArrayList<>());
                groupByEncounter.put(line.getEncounterId(), patientGroup);
            }
            Map<Long, NurseStationDispenseSummaryOrderRowDto> orderRowByMedReq =
                orderRowByEncounterAndMedReq.get(line.getEncounterId());
            if (orderRowByMedReq == null) {
                orderRowByMedReq = new LinkedHashMap<>();
                orderRowByEncounterAndMedReq.put(line.getEncounterId(), orderRowByMedReq);
            }
            NurseStationDispenseSummaryOrderRowDto orderRow = orderRowByMedReq.get(line.getMedReqId());
            if (orderRow == null) {
                orderRow = this.buildOrderRow(line, medReqById, productByKey, locationById);
                orderRow.setTimePoints(new ArrayList<>());
                orderRowByMedReq.put(line.getMedReqId(), orderRow);
                patientGroup.getOrderRows().add(orderRow);
            }
            NurseStationDispenseSummaryTimePointDto timePoint =
                this.buildTimePoint(line, productByKey, recordedTimeByProcedureId);
            orderRow.getTimePoints().add(timePoint);
        }
        List<NurseStationDispenseSummaryPatientGroupDto> groups = new ArrayList<>(groupByEncounter.values());
        for (NurseStationDispenseSummaryPatientGroupDto group : groups) {
            if (group.getOrderRows() == null) {
                continue;
            }
            group.getOrderRows().sort(Comparator
                .comparing((NurseStationDispenseSummaryOrderRowDto orderRow) ->
                        this.resolveDispenseSummaryOpenedTime(orderRow, medReqById),
                    Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(NurseStationDispenseSummaryOrderRowDto::getGroupId,
                    Comparator.nullsLast(Long::compareTo))
                .thenComparing(NurseStationDispenseSummaryOrderRowDto::getMedReqId,
                    Comparator.nullsLast(Long::compareTo)));
            for (NurseStationDispenseSummaryOrderRowDto orderRow : group.getOrderRows()) {
                if (orderRow.getTimePoints() == null) {
                    continue;
                }
                orderRow.getTimePoints().sort(Comparator.comparing(
                    NurseStationDispenseSummaryTimePointDto::getPlannedDispenseTime,
                    Comparator.nullsLast(java.time.LocalDateTime::compareTo)));
            }
        }
        return groups;
    }

    /**
     * 取消执行退药发放行 → 按就诊分组展示（IF-IP-SUM-10 Asm）。
     *
     * <p>入参行须已通过「REFUND + 待退/已退 + CANCEL Procedure」过滤；本方法只做展示装配与患者分组。</p>
     *
     * @param dispenses              已过滤的退药发放行
     * @param encounterById          就诊上下文（床号/姓名）
     * @param productByKey           药品目录展示
     * @param locationById           发放药房
     * @param cancelProcedureById    CANCEL 执行记录（取消人/时间点）
     * @param cancelOperatorNameById 取消执行人姓名
     * @param totalPriceByDispenseId 退药行 → 原账单总价（绝对值）
     * @return 按就诊分组；无行时为空列表
     */
    public List<NurseStationCancelExecuteReturnPatientGroupDto> buildCancelExecuteReturnPatientGroups(
        List<MedicationDispense> dispenses,
        Map<Long, InpatientEncounterContextQueryModel> encounterById,
        Map<String, ProductDefinitionDisplayDto> productByKey,
        Map<Long, Location> locationById,
        Map<Long, Procedure> cancelProcedureById,
        Map<Long, String> cancelOperatorNameById,
        Map<Long, BigDecimal> totalPriceByDispenseId) {
        if (dispenses == null || dispenses.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, NurseStationCancelExecuteReturnPatientGroupDto> groupByEncounter = new LinkedHashMap<>();
        for (MedicationDispense dispense : dispenses) {
            if (dispense == null || dispense.getEncounterId() == null) {
                continue;
            }
            Long encounterId = dispense.getEncounterId();
            NurseStationCancelExecuteReturnPatientGroupDto patientGroup = groupByEncounter.get(encounterId);
            if (patientGroup == null) {
                patientGroup = new NurseStationCancelExecuteReturnPatientGroupDto();
                patientGroup.setEncounterId(encounterId);
                patientGroup.setPatientId(dispense.getPatientId());
                InpatientEncounterContextQueryModel encounter = null;
                if (encounterById != null) {
                    encounter = encounterById.get(encounterId);
                }
                if (encounter != null) {
                    patientGroup.setPatientName(encounter.getPatientName());
                    patientGroup.setBedDisplay(encounter.getBedName());
                    if (patientGroup.getPatientId() == null) {
                        patientGroup.setPatientId(encounter.getPatientId());
                    }
                }
                patientGroup.setReturnLines(new ArrayList<>());
                groupByEncounter.put(encounterId, patientGroup);
            }
            patientGroup.getReturnLines().add(
                this.buildCancelExecuteReturnLine(
                    dispense,
                    productByKey,
                    locationById,
                    cancelProcedureById,
                    cancelOperatorNameById,
                    totalPriceByDispenseId));
        }
        return new ArrayList<>(groupByEncounter.values());
    }

    /**
     * 单条取消执行退药发放行 → 展示 DTO。
     *
     * @param dispense               退药发放事实
     * @param productByKey           药品目录
     * @param locationById           药房
     * @param cancelProcedureById    CANCEL 执行记录
     * @param cancelOperatorNameById 取消执行人姓名
     * @param totalPriceByDispenseId 退药行总价
     * @return 行 DTO
     */
    private NurseStationCancelExecuteReturnLineDto buildCancelExecuteReturnLine(
        MedicationDispense dispense,
        Map<String, ProductDefinitionDisplayDto> productByKey,
        Map<Long, Location> locationById,
        Map<Long, Procedure> cancelProcedureById,
        Map<Long, String> cancelOperatorNameById,
        Map<Long, BigDecimal> totalPriceByDispenseId) {
        NurseStationCancelExecuteReturnLineDto line = new NurseStationCancelExecuteReturnLineDto();
        line.setDispenseId(dispense.getId());
        line.setMedReqId(dispense.getMedReqId());
        line.setMedicationId(dispense.getMedicationId());
        line.setProcedureId(dispense.getProcedureId());
        line.setLocationId(dispense.getLocationId());
        if (locationById != null && dispense.getLocationId() != null) {
            Location location = locationById.get(dispense.getLocationId());
            if (location != null) {
                line.setLocationName(location.getName());
            }
        }
        BigDecimal quantity = dispense.getDispenseQuantity();
        if (quantity == null) {
            quantity = dispense.getQuantity();
        }
        if (quantity == null) {
            line.setQuantity(BigDecimal.ZERO);
        } else {
            line.setQuantity(quantity.abs());
        }
        String unitCode = trimToEmpty(dispense.getUnitCode());
        line.setUnitCode(unitCode);
        String productKey = ProductDefinitionKeyUtil.buildCompositeKey(
            TableNameConstants.MED_MEDICATION_DEFINITION, dispense.getMedicationId());
        ProductDefinitionDisplayDto product = null;
        if (productByKey != null) {
            product = productByKey.get(productKey);
        }
        if (product != null) {
            line.setMedicationName(StringUtils.firstNonBlank(product.getProductName(), product.getMerchandiseName()));
            line.setSpecificationText(this.resolveSpecificationText(product));
        }
        line.setUnitCodeText(ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(
            null, unitCode, product));
        DispenseStatus status = dispense.getStatusEnum();
        if (status != null) {
            line.setRefundStatusEnum(status.getCode());
            line.setRefundStatusEnumText(status.getDisplay());
        }
        // 取消执行人 + 时间点时刻：取 CANCEL Procedure
        if (cancelProcedureById != null && dispense.getProcedureId() != null) {
            Procedure cancelProcedure = cancelProcedureById.get(dispense.getProcedureId());
            if (cancelProcedure != null) {
                line.setCancelOperatorId(cancelProcedure.getRecorderId());
                line.setCancelOccurrenceTime(cancelProcedure.getOccurrenceTime());
                if (cancelOperatorNameById != null && cancelProcedure.getRecorderId() != null) {
                    line.setCancelOperatorName(cancelOperatorNameById.get(cancelProcedure.getRecorderId()));
                }
            }
        }
        if (totalPriceByDispenseId != null && dispense.getId() != null) {
            line.setTotalPrice(totalPriceByDispenseId.get(dispense.getId()));
        }
        return line;
    }

    /**
     * 按中西药 Tab 判定药嘱是否纳入退药清单（与 IF-IP-SUM-01 {@link #resolveMedReqIds} 同口径）。
     *
     * @param medReq              原药嘱实体
     * @param medicineCategoryTab west / tcm
     * @return 纳入时 true
     */
    public boolean matchesMedicineCategoryTab(MedicationRequest medReq, String medicineCategoryTab) {
        if (medReq == null) {
            return false;
        }
        String tab = trimToEmpty(medicineCategoryTab);
        if (InpatientNurseStationConstants.MEDICINE_CATEGORY_TAB_WEST.equals(tab)) {
            return !Boolean.TRUE.equals(medReq.getTcmFlag());
        }
        if (InpatientNurseStationConstants.MEDICINE_CATEGORY_TAB_TCM.equals(tab)) {
            return Boolean.TRUE.equals(medReq.getTcmFlag());
        }
        return true;
    }

    /**
     * 域汇总单头 QueryModel → 展示 DTO（IF-IP-SUM-02/03）。
     */
    public NurseStationDispenseSummarySlipHeaderDto toSlipHeaderDto(
        MedicationSummarySlipQueryModel model,
        Map<Long, Location> locationById,
        Map<Long, String> practitionerNameById) {
        if (model == null) {
            return null;
        }
        NurseStationDispenseSummarySlipHeaderDto dto = new NurseStationDispenseSummarySlipHeaderDto();
        dto.setBusNo(model.getBusNo());
        dto.setApplyTime(model.getApplyTime());
        dto.setDispenseTime(model.getDispenseTime());
        dto.setPurposeLocationId(model.getPurposeLocationId());
        Location location = locationById.get(model.getPurposeLocationId());
        if (location != null) {
            dto.setPurposeLocationName(location.getName());
        }
        dto.setApplicantId(model.getApplicantId());
        dto.setConsumingOrganizationId(model.getConsumingOrganizationId());
        dto.setApplicantName(practitionerNameById.get(model.getApplicantId()));
        dto.setReceiverId(model.getReceiverId());
        dto.setReceiverName(practitionerNameById.get(model.getReceiverId()));
        dto.setSupplyStatusEnum(model.getSupplyStatusEnum());
        dto.setSupplyStatusEnumText(this.resolveSupplyStatusText(model.getSupplyStatusEnum()));
        dto.setDeliveryStatusEnum(model.getDeliveryStatusEnum());
        dto.setDeliveryStatusEnumText(this.resolveDeliveryStatusText(model.getDeliveryStatusEnum()));
        return dto;
    }

    /**
     * 汇总单发放行 → 按患者分组、同 {@code medReqId} 合并数量（IF-IP-SUM-09 摆药单查询）。
     *
     * <p>合并口径：同一就诊下同一药品申请产生的多条发放行数量相加。
     * 剂量/用法/频次优先取药品申请（{@code MedicationRequest}），发放行作回落；
     * 床位展示拼病房名（HOUSE）+ 床名；数量按操作单位直显写入 {@code displayQuantityText}（BR-IP-SUM-015）。</p>
     *
     * @param dispenseRows         汇总单号下发放明细
     * @param productByKey         药品目录展示索引
     * @param encounterById        就诊上下文（床号/姓名）
     * @param houseNameByEncounterId 就诊 → 病房（房间）名称
     * @param medReqById           药品申请索引（剂量/用法/频次真源）
     * @param methodLabelByCode    用法术语码表
     * @param rateLabelByCode      频次主数据码表
     * @param doseUnitLabelByCode  剂量单位术语码表
     * @return 按床号、患者排序后的患者分组
     */
    public List<NurseStationDispenseSummaryPatientPickupGroupDto> buildPatientPickupGroups(
        List<MedicationDispenseSummaryItemQueryModel> dispenseRows,
        Map<String, ProductDefinitionDisplayDto> productByKey,
        Map<Long, InpatientEncounterContextQueryModel> encounterById,
        Map<Long, String> houseNameByEncounterId,
        Map<Long, MedicationRequestListQueryModel> medReqById,
        Map<String, String> methodLabelByCode,
        Map<String, String> rateLabelByCode,
        Map<String, String> doseUnitLabelByCode) {
        List<NurseStationDispenseSummaryPatientPickupGroupDto> groups = new ArrayList<>();
        if (dispenseRows == null || dispenseRows.isEmpty()) {
            return groups;
        }
        Map<Long, NurseStationDispenseSummaryPatientPickupGroupDto> groupByEncounter = new LinkedHashMap<>();
        Map<Long, Map<Long, NurseStationDispenseSummaryPatientPickupLineDto>> lineByEncounterAndMedReq =
            new LinkedHashMap<>();
        // 遍历发放行：先落患者分组，再按 medReqId 累加数量
        for (MedicationDispenseSummaryItemQueryModel row : dispenseRows) {
            if (row == null || row.getEncounterId() == null || row.getMedReqId() == null) {
                continue;
            }
            NurseStationDispenseSummaryPatientPickupGroupDto patientGroup =
                groupByEncounter.get(row.getEncounterId());
            if (patientGroup == null) {
                patientGroup = new NurseStationDispenseSummaryPatientPickupGroupDto();
                patientGroup.setEncounterId(row.getEncounterId());
                patientGroup.setPatientId(row.getPatientId());
                patientGroup.setLines(new ArrayList<>());
                if (encounterById != null) {
                    InpatientEncounterContextQueryModel encounter = encounterById.get(row.getEncounterId());
                    if (encounter != null) {
                        patientGroup.setPatientName(encounter.getPatientName());
                        String houseName = null;
                        if (houseNameByEncounterId != null) {
                            houseName = houseNameByEncounterId.get(row.getEncounterId());
                        }
                        patientGroup.setBedDisplay(this.formatRoomBedDisplay(houseName, encounter.getBedName()));
                    }
                }
                groupByEncounter.put(row.getEncounterId(), patientGroup);
            }
            Map<Long, NurseStationDispenseSummaryPatientPickupLineDto> lineByMedReq =
                lineByEncounterAndMedReq.get(row.getEncounterId());
            if (lineByMedReq == null) {
                lineByMedReq = new LinkedHashMap<>();
                lineByEncounterAndMedReq.put(row.getEncounterId(), lineByMedReq);
            }
            NurseStationDispenseSummaryPatientPickupLineDto line = lineByMedReq.get(row.getMedReqId());
            if (line == null) {
                MedicationRequestListQueryModel medReq = null;
                if (medReqById != null) {
                    medReq = medReqById.get(row.getMedReqId());
                }
                line = this.buildPatientPickupLine(
                    row, medReq, productByKey, methodLabelByCode, rateLabelByCode, doseUnitLabelByCode);
                lineByMedReq.put(row.getMedReqId(), line);
                patientGroup.getLines().add(line);
            } else {
                // 同医嘱多条发药：数量累加；展示字段已在首条写入
                BigDecimal currentQty = line.getItemQuantity();
                BigDecimal addQty = row.getQuantity();
                if (currentQty == null) {
                    line.setItemQuantity(addQty);
                } else if (addQty != null) {
                    line.setItemQuantity(currentQty.add(addQty));
                }
            }
        }
        // 合并完成后回写操作单位直显文案（依赖累加后的 itemQuantity；BR-IP-SUM-015）
        for (NurseStationDispenseSummaryPatientPickupGroupDto group : groupByEncounter.values()) {
            if (group.getLines() == null) {
                continue;
            }
            for (NurseStationDispenseSummaryPatientPickupLineDto line : group.getLines()) {
                if (line == null) {
                    continue;
                }
                // 摆药行数量为发放操作单位累加，跟着 unitCodeText 直显（禁止满包+余量）
                line.setDisplayQuantityText(MedicationPackageSplitQuantityDisplayAssembler.buildPlainQuantityText(
                    line.getItemQuantity(), line.getUnitCodeText()));
            }
            group.getLines().sort(Comparator.comparing(
                NurseStationDispenseSummaryPatientPickupLineDto::getMedReqId,
                Comparator.nullsLast(Long::compareTo)));
        }
        groups.addAll(groupByEncounter.values());
        groups.sort(Comparator
            .comparing(NurseStationDispenseSummaryPatientPickupGroupDto::getBedDisplay,
                Comparator.nullsLast(String::compareTo))
            .thenComparing(NurseStationDispenseSummaryPatientPickupGroupDto::getPatientName,
                Comparator.nullsLast(String::compareTo))
            .thenComparing(NurseStationDispenseSummaryPatientPickupGroupDto::getEncounterId,
                Comparator.nullsLast(Long::compareTo)));
        return groups;
    }

    /**
     * 病房名 + 床名拼接（空段跳过；如 {@code 308 4号床}）。
     *
     * @param houseName 病房/房间名
     * @param bedName   床位名
     * @return 位置展示文案
     */
    private String formatRoomBedDisplay(String houseName, String bedName) {
        String house = trimToEmpty(houseName);
        String bed = trimToEmpty(bedName);
        if (StringUtils.isNotEmpty(house) && StringUtils.isNotEmpty(bed)) {
            return house + " " + bed;
        }
        if (StringUtils.isNotEmpty(house)) {
            return house;
        }
        if (StringUtils.isNotEmpty(bed)) {
            return bed;
        }
        return null;
    }

    /**
     * 单条发放行 → 摆药首行（数量后续可累加；displayQuantityText 在累加后统一回写）。
     *
     * <p>剂量优先 {@code dose+doseUnit}（医嘱→发放）；用法/频次优先医嘱 {@code methodCode}/{@code rateCode}。</p>
     *
     * @param row                 发放明细
     * @param medReq              对应药品申请；可空
     * @param productByKey        药品目录索引
     * @param methodLabelByCode   用法码表
     * @param rateLabelByCode     频次码表
     * @param doseUnitLabelByCode 剂量单位码表
     * @return 摆药药品行
     */
    private NurseStationDispenseSummaryPatientPickupLineDto buildPatientPickupLine(
        MedicationDispenseSummaryItemQueryModel row,
        MedicationRequestListQueryModel medReq,
        Map<String, ProductDefinitionDisplayDto> productByKey,
        Map<String, String> methodLabelByCode,
        Map<String, String> rateLabelByCode,
        Map<String, String> doseUnitLabelByCode) {
        NurseStationDispenseSummaryPatientPickupLineDto line = new NurseStationDispenseSummaryPatientPickupLineDto();
        line.setMedReqId(row.getMedReqId());
        line.setMedicationId(row.getMedicationId());
        line.setItemQuantity(row.getQuantity());
        line.setUnitCode(row.getUnitCode());

        String productKey = ProductDefinitionKeyUtil.buildCompositeKey(
            TableNameConstants.MED_MEDICATION_DEFINITION, row.getMedicationId());
        ProductDefinitionDisplayDto product = null;
        if (productByKey != null) {
            product = productByKey.get(productKey);
        }
        if (product != null) {
            line.setMedicationName(StringUtils.firstNonBlank(product.getProductName(), product.getMerchandiseName()));
            line.setSpecificationText(this.resolveSpecificationText(product));
            line.setUnitCodeText(ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(
                null, row.getUnitCode(), product));
        }

        // 剂量：医嘱 dose → 发放 dose → 用药说明文案
        BigDecimal dose = null;
        String doseUnitCode = null;
        if (medReq != null) {
            dose = medReq.getDose();
            doseUnitCode = medReq.getDoseUnitCode();
        }
        if (dose == null) {
            dose = row.getDose();
            doseUnitCode = row.getDoseUnitCode();
        }
        String dosageText = this.formatPickupDoseDisplay(dose, doseUnitCode, doseUnitLabelByCode, product);
        if (StringUtils.isEmpty(dosageText)) {
            if (medReq != null && StringUtils.isNotEmpty(medReq.getDosageInstruction())) {
                dosageText = medReq.getDosageInstruction();
            } else {
                dosageText = row.getDosageInstruction();
            }
        }
        line.setDosageInstruction(dosageText);

        // 用法：医嘱 methodCode → 发放 methodCode
        String methodCode = null;
        if (medReq != null && StringUtils.isNotEmpty(medReq.getMethodCode())) {
            methodCode = medReq.getMethodCode();
        } else {
            methodCode = row.getMethodCode();
        }
        line.setMethodCode(methodCode);
        line.setMethodCodeText(this.resolveLabelOrCode(methodLabelByCode, methodCode));

        // 频次：医嘱 rateCode → 发放 frequencyCode
        String frequencyCode = null;
        if (medReq != null && StringUtils.isNotEmpty(medReq.getRateCode())) {
            frequencyCode = medReq.getRateCode();
        } else {
            frequencyCode = row.getFrequencyCode();
        }
        line.setFrequencyCode(frequencyCode);
        line.setFrequencyCodeText(this.resolveLabelOrCode(rateLabelByCode, frequencyCode));
        return line;
    }

    /**
     * 剂量展示：数值 + 单位文案（与医嘱查看同族 stripTrailingZeros）。
     *
     * @param dose                单次剂量
     * @param doseUnitCode        剂量单位 code
     * @param doseUnitLabelByCode 单位码表
     * @param product             药品目录（单位回落）
     * @return 如 {@code 2片}；无剂量时 null
     */
    private String formatPickupDoseDisplay(
        BigDecimal dose,
        String doseUnitCode,
        Map<String, String> doseUnitLabelByCode,
        ProductDefinitionDisplayDto product) {
        if (dose == null) {
            return null;
        }
        String doseText = dose.stripTrailingZeros().toPlainString();
        String unit = ProductDefinitionCatalogUnitBandsAssembler.resolveDoseUnitDisplayName(
            doseUnitLabelByCode, doseUnitCode, product);
        if (StringUtils.isNotEmpty(unit)) {
            return doseText + unit;
        }
        return doseText;
    }

    /**
     * 码表翻译：有 label 用 label，否则回落 code 本身。
     *
     * @param labelByCode 码 → 文案
     * @param code        持久化 code
     * @return 展示文案；code 空时 null
     */
    private String resolveLabelOrCode(Map<String, String> labelByCode, String code) {
        if (StringUtils.isEmpty(code)) {
            return null;
        }
        if (labelByCode != null) {
            String label = labelByCode.get(code);
            if (StringUtils.isNotEmpty(label)) {
                return label;
            }
            String trimmed = code.trim();
            label = labelByCode.get(trimmed);
            if (StringUtils.isNotEmpty(label)) {
                return label;
            }
        }
        return code;
    }

    /**
     * 域合并明细 → 展示 DTO（IF-IP-SUM-03；禁止输出 lotNumber）。
     *
     * <p>行序：按药品目录拼音检索码 {@code pyStr} 首字母序，缺拼音时回落药品名；同键再按 itemId 稳定排序
     *（汇总领药单打印与详情同源，BR-IP-SUM-020）。</p>
     *
     * @param domainLines  域合并明细
     * @param productByKey 药品目录投影（含 pyStr）
     * @return 已按拼音排序的展示行
     */
    public List<NurseStationDispenseSummarySlipLineDto> buildSlipLineDtos(
        List<MedicationSummarySlipLineQueryModel> domainLines,
        Map<String, ProductDefinitionDisplayDto> productByKey) {
        List<NurseStationDispenseSummarySlipLineDto> lines = new ArrayList<>();
        if (domainLines == null) {
            return lines;
        }
        Map<Long, String> pySortKeyByItemId = new HashMap<>();
        for (MedicationSummarySlipLineQueryModel row : domainLines) {
            if (row == null) {
                continue;
            }
            NurseStationDispenseSummarySlipLineDto dto = new NurseStationDispenseSummarySlipLineDto();
            dto.setSupplyRequestId(row.getSupplyRequestId());
            dto.setItemId(row.getItemId());
            dto.setItemQuantity(row.getItemQuantity());
            dto.setUnitCode(row.getUnitCode());
            dto.setUnitPrice(row.getUnitPrice());
            dto.setTotalPrice(row.getTotalPrice());
            String productKey = ProductDefinitionKeyUtil.buildCompositeKey(
                TableNameConstants.MED_MEDICATION_DEFINITION, row.getItemId());
            ProductDefinitionDisplayDto product = productByKey.get(productKey);
            if (product != null) {
                dto.setMedicationName(StringUtils.firstNonBlank(product.getProductName(), product.getMerchandiseName()));
                dto.setSpecificationText(this.resolveSpecificationText(product));
                dto.setUnitCodeText(ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(
                    null, row.getUnitCode(), product));
                // 输液拆零；非输液整包装凑整（BR-IP-SUM-015）；禁止满包+余量；总价不重算
                dto.setDisplayQuantityText(MedicationPackageSplitQuantityDisplayAssembler.buildSummaryQuantityText(
                    row.getItemQuantity(), product, dto.getUnitCodeText()));
                pySortKeyByItemId.put(row.getItemId(), this.resolveMedicationPySortKey(product));
            } else {
                pySortKeyByItemId.put(row.getItemId(), "");
            }
            lines.add(dto);
        }
        // 拼音首字母序；缺拼音排后；同拼音按药品名、itemId 稳定
        lines.sort(Comparator
            .comparing((NurseStationDispenseSummarySlipLineDto line) ->
                    this.resolveLinePySortKey(line, pySortKeyByItemId),
                Comparator.nullsLast(String::compareToIgnoreCase))
            .thenComparing(NurseStationDispenseSummarySlipLineDto::getMedicationName,
                Comparator.nullsLast(String::compareToIgnoreCase))
            .thenComparing(NurseStationDispenseSummarySlipLineDto::getItemId,
                Comparator.nullsLast(Long::compareTo)));
        return lines;
    }

    /**
     * 药品拼音排序键：目录 {@code pyStr} 大写；空则空串（排序时靠后并回落药名）。
     *
     * @param product 药品目录投影
     * @return 非 null 排序键
     */
    private String resolveMedicationPySortKey(ProductDefinitionDisplayDto product) {
        if (product == null || StringUtils.isEmpty(product.getPyStr())) {
            return "";
        }
        return product.getPyStr().trim().toUpperCase();
    }

    /**
     * 明细行拼音排序键（无 pyStr 时用「~」前缀药名，保证有拼音的行排前）。
     *
     * @param line               明细行
     * @param pySortKeyByItemId  itemId → pyStr 键
     * @return 排序键
     */
    private String resolveLinePySortKey(
        NurseStationDispenseSummarySlipLineDto line,
        Map<Long, String> pySortKeyByItemId) {
        if (line == null) {
            return "~";
        }
        String pyKey = null;
        if (line.getItemId() != null && pySortKeyByItemId != null) {
            pyKey = pySortKeyByItemId.get(line.getItemId());
        }
        if (StringUtils.isNotEmpty(pyKey)) {
            return pyKey;
        }
        String medicationName = line.getMedicationName();
        if (StringUtils.isEmpty(medicationName)) {
            return "~";
        }
        return "~" + medicationName.trim();
    }

    /**
     * 批量索引 locationId → Location。
     */
    public Map<Long, Location> indexLocations(List<Location> locations) {
        Map<Long, Location> map = new HashMap<>();
        if (locations == null) {
            return map;
        }
        for (Location location : locations) {
            if (location != null && location.getId() != null) {
                map.put(location.getId(), location);
            }
        }
        return map;
    }

    /**
     * 组装领药汇总「医嘱行」：药品名、规格、用法频次、执行科室与长临展示。
     *
     * @param line          待发药明细读模型
     * @param medReqById    药品请求索引
     * @param productByKey  产品目录投影
     * @param locationById  执行地点索引
     * @return 医嘱汇总行 DTO
     */
    private NurseStationDispenseSummaryOrderRowDto buildOrderRow(
        MedicationDispensePickupPendingQueryModel line,
        Map<Long, MedicationRequestListQueryModel> medReqById,
        Map<String, ProductDefinitionDisplayDto> productByKey,
        Map<Long, Location> locationById) {
        NurseStationDispenseSummaryOrderRowDto orderRow = new NurseStationDispenseSummaryOrderRowDto();
        orderRow.setMedReqId(line.getMedReqId());
        orderRow.setMedicationId(line.getMedicationId());
        orderRow.setDosageInstruction(line.getDosageInstruction());
        orderRow.setMethodCode(line.getMethodCode());
        orderRow.setFrequencyCode(line.getFrequencyCode());
        orderRow.setLocationId(line.getLocationId());
        Location location = locationById.get(line.getLocationId());
        if (location != null) {
            orderRow.setLocationName(location.getName());
        }
        MedicationRequestListQueryModel medReq = medReqById.get(line.getMedReqId());
        if (medReq != null) {
            orderRow.setGroupId(medReq.getGroupId());
            orderRow.setTherapyEnum(medReq.getTherapyEnum());
            orderRow.setTherapyEnumText(this.resolveTherapyDisplay(medReq.getTherapyEnum()));
        }
        String productKey = ProductDefinitionKeyUtil.buildCompositeKey(
            TableNameConstants.MED_MEDICATION_DEFINITION, line.getMedicationId());
        ProductDefinitionDisplayDto product = productByKey.get(productKey);
        if (product != null) {
            orderRow.setMedicationName(StringUtils.firstNonBlank(product.getProductName(), product.getMerchandiseName()));
            orderRow.setSpecificationText(this.resolveSpecificationText(product));
            orderRow.setMethodCodeText(StringUtils.firstNonBlank(product.getMethodCodeDictText(), line.getMethodCode()));
            orderRow.setFrequencyCodeText(StringUtils.firstNonBlank(product.getRateCodeDictText(), line.getFrequencyCode()));
        } else {
            orderRow.setMethodCodeText(line.getMethodCode());
            orderRow.setFrequencyCodeText(line.getFrequencyCode());
        }
        return orderRow;
    }

    /**
     * 领药汇总医嘱行开立时刻：优先 {@code req_opened_time}，空回落 {@code req_authored_time}（兼容存量）。
     *
     * @param orderRow   汇总医嘱行
     * @param medReqById 药品申请索引
     * @return 开立时刻；无申请或无时刻时为 null
     */
    private LocalDateTime resolveDispenseSummaryOpenedTime(
        NurseStationDispenseSummaryOrderRowDto orderRow,
        Map<Long, MedicationRequestListQueryModel> medReqById) {
        if (orderRow == null || orderRow.getMedReqId() == null || medReqById == null) {
            return null;
        }
        MedicationRequestListQueryModel medReq = medReqById.get(orderRow.getMedReqId());
        if (medReq == null) {
            return null;
        }
        if (medReq.getReqOpenedTime() != null) {
            return medReq.getReqOpenedTime();
        }
        return medReq.getReqAuthoredTime();
    }

    /**
     * 组装领药汇总时点：逻辑锚 {@code plannedDispenseTime}；展示优先 {@code recordedTime}。
     *
     * @param line                      待发药明细读模型
     * @param productByKey              产品目录投影（单位文案）
     * @param recordedTimeByProcedureId Procedure → recorded_time
     * @return 时点 DTO
     */
    private NurseStationDispenseSummaryTimePointDto buildTimePoint(
        MedicationDispensePickupPendingQueryModel line,
        Map<String, ProductDefinitionDisplayDto> productByKey,
        Map<Long, LocalDateTime> recordedTimeByProcedureId) {
        NurseStationDispenseSummaryTimePointDto timePoint = new NurseStationDispenseSummaryTimePointDto();
        timePoint.setDispenseId(line.getDispenseId());
        timePoint.setProcedureId(line.getProcedureId());
        // 勾选/截止/归组逻辑锚：计划发药时刻（= 执行 planTime）
        timePoint.setPlannedDispenseTime(line.getPlannedDispenseTime());
        // 领药时间列展示：实际执行 recorded；无则前端回落 planned
        if (line.getProcedureId() != null && recordedTimeByProcedureId != null) {
            timePoint.setRecordedTime(recordedTimeByProcedureId.get(line.getProcedureId()));
        }
        timePoint.setQuantity(line.getQuantity());
        timePoint.setUnitCode(line.getUnitCode());
        timePoint.setChargeUnitPrice(line.getChargeUnitPrice());
        String productKey = ProductDefinitionKeyUtil.buildCompositeKey(
            TableNameConstants.MED_MEDICATION_DEFINITION, line.getMedicationId());
        ProductDefinitionDisplayDto product = productByKey.get(productKey);
        timePoint.setUnitCodeText(ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(
            null, line.getUnitCode(), product));
        return timePoint;
    }

    /**
     * 药品规格展示：优先 total_volume，回落商品名。
     *
     * @param product 产品目录投影
     * @return 规格文案；product 为空时 null
     */
    private String resolveSpecificationText(ProductDefinitionDisplayDto product) {
        if (product == null) {
            return null;
        }
        return StringUtils.firstNonBlank(product.getTotalVolume(), product.getMerchandiseName());
    }

    /**
     * 长临嘱 code → 护士站展示文案。
     *
     * @param therapyCode 长临 code
     * @return 展示文案；空或未知 code 时 null
     */
    private String resolveTherapyDisplay(String therapyCode) {
        if (StringUtils.isEmpty(therapyCode)) {
            return null;
        }
        TherapyType therapyType = EnumUtil.fromCode(TherapyType.class, therapyCode);
        if (therapyType != null) {
            return therapyType.getDisplay();
        }
        return null;
    }

    /**
     * 领药申请供应状态 code → 汇总页固定展示文案（草稿/审批/同意等）。
     *
     * @param supplyStatusEnum 供应状态 code
     * @return 展示文案；空或未知时 null
     */
    private String resolveSupplyStatusText(String supplyStatusEnum) {
        if (StringUtils.isEmpty(supplyStatusEnum)) {
            return null;
        }
        SupplyStatus status = EnumUtil.fromCode(SupplyStatus.class, supplyStatusEnum.trim());
        if (status == null) {
            return null;
        }
        if (SupplyStatus.PENDING_APPROVAL.equals(status)) {
            return InpatientNurseStationConstants.SUMMARY_SUPPLY_STATUS_DRAFT_DISPLAY;
        }
        if (SupplyStatus.APPROVAL.equals(status)) {
            return InpatientNurseStationConstants.SUMMARY_SUPPLY_STATUS_APPROVAL_DISPLAY;
        }
        if (SupplyStatus.AGREE.equals(status)) {
            return InpatientNurseStationConstants.SUMMARY_SUPPLY_STATUS_AGREE_DISPLAY;
        }
        if (SupplyStatus.REJECT.equals(status)) {
            return InpatientNurseStationConstants.SUMMARY_SUPPLY_STATUS_REJECT_DISPLAY;
        }
        if (SupplyStatus.WITHDRAW.equals(status)) {
            return InpatientNurseStationConstants.SUMMARY_SUPPLY_STATUS_WITHDRAW_DISPLAY;
        }
        return status.getDisplay();
    }

    /**
     * 发药配送状态 code → 汇总页固定展示文案（待发/已完成等）。
     *
     * @param deliveryStatusEnum 发药状态 code
     * @return 展示文案；空或未知时 null
     */
    private String resolveDeliveryStatusText(String deliveryStatusEnum) {
        if (StringUtils.isEmpty(deliveryStatusEnum)) {
            return null;
        }
        DispenseStatus status = EnumUtil.fromCode(DispenseStatus.class, deliveryStatusEnum.trim());
        if (status == null) {
            return null;
        }
        if (DispenseStatus.PREPARATION.equals(status)) {
            return InpatientNurseStationConstants.SUMMARY_DELIVERY_STATUS_PENDING_DISPLAY;
        }
        if (DispenseStatus.COMPLETED.equals(status)) {
            return InpatientNurseStationConstants.SUMMARY_DELIVERY_STATUS_COMPLETED_DISPLAY;
        }
        return status.getDisplay();
    }
}
