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

package com.openhis.health.application.outpatient.doctorstation.advice.assembler;

import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.medication.medicationrequest.command.PrescriptionSplitLineContext;
import com.openhis.health.domain.medication.medicationrequest.command.SplitPrescriptionCommand;
import com.openhis.health.domain.medication.medicationrequest.command.SplitPrescriptionResult;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestDomainService;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.share.constants.PrescriptionSplitConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.share.enums.YbMedType;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.module.common.assembler.product.DoctorStationAdviceFifoAllocationAssembler;
import com.openhis.health.module.common.validator.OutpatientPrescriptionAmountLimitSettingSupport;
import com.openhis.health.module.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
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
 * 门诊医生站签发前处方分方装配器。
 *
 * <p>在 IF-DOR-15 药品签发循环之前，拉取当前就诊下全部未收费药品行，装配 {@link PrescriptionSplitLineContext}
 * 快照（医保医疗类别、特殊药品属性、费用合计、FIFO 主库房），委托 {@link MedicationRequestDomainService#splitPrescription}
 * 回写 {@code prescription_no} 与 {@code rx_type_code}。住院路径不调用本装配器。</p>
 */
@Component
@RequiredArgsConstructor
public class OutpatientPrescriptionSplitAssembler {

    private final MedicationRequestQueryService medicationRequestQueryService;
    private final MedicationRequestDomainService medicationRequestDomainService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final EncounterQueryService encounterQueryService;
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;
    private final DoctorStationAdviceFifoAllocationAssembler doctorStationAdviceFifoAllocationAssembler;
    private final OutpatientPrescriptionAmountLimitSettingSupport outpatientPrescriptionAmountLimitSettingSupport;

    /**
     * 对当前就诊下全部未收费药品医嘱执行分方，并返回申请主键到处方号的映射。
     *
     * @param encounterId 就诊主键
     * @param orgId       开立机构（FIFO 库房解析）
     * @param operatorId  操作人
     * @return 药品申请主键 → 处方号；无参与行时返回空 Map
     */
    public Map<Long, String> splitAndAssignPrescriptions(Long encounterId, Long orgId, Long operatorId) {
        // Pre：装配未收费全集行快照（含 med_type、金额、FIFO 首段库位）
        List<PrescriptionSplitLineContext> lines = buildUnbilledLineContexts(encounterId, orgId);
        if (lines.isEmpty()) {
            return Map.of();
        }
        SplitPrescriptionCommand command = new SplitPrescriptionCommand();
        command.setEncounterId(encounterId);
        command.setOperatorId(operatorId);
        command.setLines(lines);
        command.setAmountLimitYuan(outpatientPrescriptionAmountLimitSettingSupport.resolveAmountLimitYuan());
        // Cmd：领域装箱并持久化 prescription_no / rx_type_code
        SplitPrescriptionResult result = medicationRequestDomainService.splitPrescription(command);
        return result.getPrescriptionNoByRequestId();
    }

    /**
     * 收集未收费药品行并装配分方快照。
     *
     * @param encounterId 就诊主键
     * @param orgId       开立机构
     * @return 参与分方的行快照列表
     */
    private List<PrescriptionSplitLineContext> buildUnbilledLineContexts(Long encounterId, Long orgId) {
        List<MedicationRequestListQueryModel> medicationRows =
            medicationRequestQueryService.listMedicationRequestsByEncounterForDoctorStation(
                encounterId, GenerateSource.DOCTOR_PRESCRIPTION.getCode());
        if (medicationRows.isEmpty()) {
            return List.of();
        }
        // Pre：一次加载就诊全部收费行，供结算判定与金额汇总（避免多次扫描同一列表）
        ChargeItemQueryCommand chargeQuery = new ChargeItemQueryCommand();
        chargeQuery.setEncounterId(encounterId);
        List<ChargeItem> chargeItems = chargeItemQueryService.listChargeItemsByEncounterId(chargeQuery);
        MedicationChargeRollup chargeRollup = rollupMedicationChargesForSplit(chargeItems);

        Set<Long> diagnosisIds = new HashSet<>();
        Set<Long> medicationDefIds = new LinkedHashSet<>();
        List<MedicationRequestListQueryModel> candidates = new ArrayList<>();
        for (MedicationRequestListQueryModel row : medicationRows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            String status = row.getStatusEnum();
            // 仅草稿/已签发未收费行参与分方
            if (!RequestStatus.DRAFT.getCode().equals(status)
                && !RequestStatus.ACTIVE.getCode().equals(status)) {
                continue;
            }
            if (chargeRollup.settledRequestIds.contains(row.getId())) {
                // 已结算行不参与重分方
                continue;
            }
            candidates.add(row);
            if (row.getEncounterDiagnosisId() != null) {
                diagnosisIds.add(row.getEncounterDiagnosisId());
            }
            if (row.getMedicationId() != null) {
                medicationDefIds.add(row.getMedicationId());
            }
        }
        if (candidates.isEmpty()) {
            return List.of();
        }
        Map<Long, String> medTypeByDiagnosisId = encounterQueryService.mapMedTypeByEncounterDiagnosisIds(diagnosisIds);
        List<ProductDefinitionBatchLookup> productLookups = new ArrayList<>();
        for (Long medicationDefId : medicationDefIds) {
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
            lookup.setProductDefinitionId(medicationDefId);
            productLookups.add(lookup);
        }
        Map<String, ProductDefinitionDisplayDto> productByKey = productDefinitionBatchLoader.loadAsMap(productLookups);
        // 同一药品+单位+数量+分类的 FIFO 首段库位相同，缓存避免重复库存试算
        Map<String, Long> fifoLocationByCacheKey = new HashMap<>();

        List<PrescriptionSplitLineContext> lines = new ArrayList<>();
        for (MedicationRequestListQueryModel row : candidates) {
            PrescriptionSplitLineContext ctx = new PrescriptionSplitLineContext();
            ctx.setRequestId(row.getId());
            ctx.setMedicationId(row.getMedicationId());
            ctx.setGroupId(row.getGroupId());
            ctx.setTcmFlag(row.getTcmFlag());
            Long diagnosisId = row.getEncounterDiagnosisId();
            String medType = medTypeByDiagnosisId.get(diagnosisId);
            if (StringUtils.isBlank(medType)) {
                medType = YbMedType.GENERAL_OUTPATIENT.getCode();
            }
            ctx.setMedType(medType);
            BigDecimal chargeTotal = chargeRollup.chargeTotalByRequestId.get(row.getId());
            if (chargeTotal == null) {
                chargeTotal = BigDecimal.ZERO;
            }
            ctx.setChargeTotalPrice(chargeTotal);
            String productKey = ProductDefinitionKeyUtil.buildCompositeKey(
                TableNameConstants.MED_MEDICATION_DEFINITION, row.getMedicationId());
            ProductDefinitionDisplayDto product = productByKey.get(productKey);
            if (product != null) {
                ctx.setPartAttributeEnum(product.getPartAttributeEnum());
            }
            Long pharmacyLocationId = null;
            if (row.getMedicationId() != null && row.getQuantity() != null) {
                String categoryCode = null;
                if (product != null) {
                    categoryCode = product.getCategoryCode();
                }
                String fifoCacheKey = buildFifoLocationCacheKey(
                    row.getMedicationId(), row.getUnitCode(), row.getQuantity(), categoryCode, row.getMethodCode());
                pharmacyLocationId = fifoLocationByCacheKey.get(fifoCacheKey);
                if (pharmacyLocationId == null && !fifoLocationByCacheKey.containsKey(fifoCacheKey)) {
                    pharmacyLocationId = doctorStationAdviceFifoAllocationAssembler.resolvePrimaryMedicationDispenseLocationId(
                        orgId,
                        encounterId,
                        row.getMedicationId(),
                        categoryCode,
                        row.getMethodCode(),
                        row.getUnitCode(),
                        row.getQuantity());
                    fifoLocationByCacheKey.put(fifoCacheKey, pharmacyLocationId);
                }
            }
            ctx.setPharmacyLocationId(pharmacyLocationId);
            lines.add(ctx);
        }
        return lines;
    }

    /**
     * 单次扫描就诊收费行：解析发放锚点、识别已结算药品申请、汇总待分方金额。
     *
     * @param chargeItems 就诊维度收费行
     * @return 分方费用 rollup 结果
     */
    private MedicationChargeRollup rollupMedicationChargesForSplit(List<ChargeItem> chargeItems) {
        Set<Long> dispenseIds = new HashSet<>();
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null || Boolean.TRUE.equals(chargeItem.getIsDeleted())) {
                continue;
            }
            if (TableNameConstants.MED_MEDICATION_DISPENSE.equals(chargeItem.getServiceTable())
                && chargeItem.getServiceId() != null) {
                dispenseIds.add(chargeItem.getServiceId());
            }
            if (TableNameConstants.MED_MEDICATION_DISPENSE.equals(chargeItem.getDispenseTable())
                && chargeItem.getDispenseId() != null) {
                dispenseIds.add(chargeItem.getDispenseId());
            }
        }
        Map<Long, Long> dispenseIdToRequestId = Map.of();
        if (!dispenseIds.isEmpty()) {
            dispenseIdToRequestId = medicationDispenseQueryService.mapMedicationDispenseIdsToMedReqIds(dispenseIds);
        }
        Set<Long> settledRequestIds = new LinkedHashSet<>();
        Map<Long, BigDecimal> chargeTotalByRequestId = new HashMap<>();
        String settledCode = ChargeItemStatus.SETTLED.getCode();
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null || Boolean.TRUE.equals(chargeItem.getIsDeleted())) {
                continue;
            }
            Long medicationRequestId = resolveMedicationRequestIdFromCharge(chargeItem, dispenseIdToRequestId);
            if (medicationRequestId == null) {
                continue;
            }
            if (settledCode.equals(chargeItem.getStatusEnum())) {
                // 任一收费行已结算则整单药品申请退出分方
                settledRequestIds.add(medicationRequestId);
                continue;
            }
            BigDecimal lineTotal = chargeItem.getTotalPrice();
            if (lineTotal == null) {
                continue;
            }
            BigDecimal existing = chargeTotalByRequestId.get(medicationRequestId);
            if (existing == null) {
                chargeTotalByRequestId.put(medicationRequestId, lineTotal);
            } else {
                chargeTotalByRequestId.put(medicationRequestId, existing.add(lineTotal));
            }
        }
        MedicationChargeRollup rollup = new MedicationChargeRollup();
        rollup.dispenseIdToRequestId = dispenseIdToRequestId;
        rollup.settledRequestIds = settledRequestIds;
        rollup.chargeTotalByRequestId = chargeTotalByRequestId;
        return rollup;
    }

    /**
     * 构建 FIFO 主库位缓存键（药品+开立单位+数量+目录分类）。
     *
     * @param medicationId  药品定义主键
     * @param unitCode      开立单位
     * @param quantity      开立数量
     * @param categoryCode  药品目录 categoryCode
     * @param methodCode    给药途径编码
     * @return 缓存键
     */
    private static String buildFifoLocationCacheKey(
        Long medicationId,
        String unitCode,
        BigDecimal quantity,
        String categoryCode,
        String methodCode
    ) {
        String unit;
        if (unitCode == null) {
            unit = "";
        } else {
            unit = unitCode;
        }
        String qty;
        if (quantity == null) {
            qty = "";
        } else {
            qty = quantity.toPlainString();
        }
        String category;
        if (categoryCode == null) {
            category = "";
        } else {
            category = categoryCode;
        }
        String method;
        if (methodCode == null) {
            method = "";
        } else {
            method = methodCode;
        }
        return medicationId
            + PrescriptionSplitConstants.PARTITION_KEY_SEPARATOR
            + unit
            + PrescriptionSplitConstants.PARTITION_KEY_SEPARATOR
            + qty
            + PrescriptionSplitConstants.PARTITION_KEY_SEPARATOR
            + category
            + PrescriptionSplitConstants.PARTITION_KEY_SEPARATOR
            + method;
    }

    /**
     * 从收费行锚点解析药品申请主键。
     *
     * @param chargeItem             收费行
     * @param dispenseIdToRequestId  发放主键 → 药品申请主键
     * @return 药品申请主键；非药品锚点时返回 null
     */
    private static Long resolveMedicationRequestIdFromCharge(
        ChargeItem chargeItem,
        Map<Long, Long> dispenseIdToRequestId
    ) {
        if (TableNameConstants.MED_MEDICATION_REQUEST.equals(chargeItem.getServiceTable())) {
            return chargeItem.getServiceId();
        }
        if (TableNameConstants.MED_MEDICATION_DISPENSE.equals(chargeItem.getServiceTable())) {
            return dispenseIdToRequestId.get(chargeItem.getServiceId());
        }
        if (TableNameConstants.MED_MEDICATION_DISPENSE.equals(chargeItem.getDispenseTable())
            && chargeItem.getDispenseId() != null) {
            return dispenseIdToRequestId.get(chargeItem.getDispenseId());
        }
        return null;
    }

    /**
     * 分方费用 rollup 中间结果（单次扫描收费行产出）。
     */
    private static final class MedicationChargeRollup {
        private Map<Long, Long> dispenseIdToRequestId;
        private Set<Long> settledRequestIds;
        private Map<Long, BigDecimal> chargeTotalByRequestId;
    }
}
