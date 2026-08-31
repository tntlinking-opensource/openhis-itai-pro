/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
import com.openhis.health.domain.share.utils.EncounterDiagnosisIdsSupport;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.application.shared.product.DoctorStationAdviceFifoAllocationAssembler;
import com.openhis.health.application.outpatient.doctorstation.advice.support.OutpatientPrescriptionAmountLimitSettingResolver;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 门诊医生站签发前处方分方装配器（西药/中成药）。
 *
 * <p>在 IF-DOR-15 药品签发循环之前，装配 {@link PrescriptionSplitLineContext} 快照并委托
 * {@link MedicationRequestDomainService#splitPrescription} 仅回写 {@code prescription_no} 与 {@code rx_type_code}
 * （不改医嘱状态、不改收费状态）。住院路径不调用本装配器。</p>
 *
 * <p>参与集 = 本批待签发药品草稿 ∪ 本就诊已签发（ACTIVE）且未 SETTLED 的<strong>非中药饮片</strong>行，
 * 用于防止「开一个签一个」绕过分方；<b>禁止</b>给未纳入本批发签的其它 DRAFT 写处方号
 * （「签发以后才有方」）。</p>
 *
 * <p><b>中药隔离（BR-TCM-ISO / BR-ORD-023）</b>：{@code tcmFlag=true} 的中药饮片<strong>禁止</strong>进入本装配器。
 * 中药处方号由 IF-DOR-37 {@code TcmRxSignAssembler} 按方标识取号；西药签发不得改写已签发中药的
 * {@code prescription_no}，否则会与已落库收费行/代煎 companion 处方号漂移。</p>
 */
@Component
@RequiredArgsConstructor
public class OutpatientPrescriptionSplitAssembler {

    private final MedicationRequestQueryService medicationRequestQueryService;
    private final MedicationRequestDomainService medicationRequestDomainService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final EncounterQueryService encounterQueryService;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    private final DoctorStationAdviceFifoAllocationAssembler doctorStationAdviceFifoAllocationAssembler;
    private final OutpatientPrescriptionAmountLimitSettingResolver outpatientPrescriptionAmountLimitSettingResolver;

    /**
     * 对本批西药/中成药签目标与已签发未收费非中药执行分方，返回申请主键到处方号的映射。
     *
     * <p>中药饮片（{@code tcmFlag=true}）不参与；其处方号由 IF-DOR-37 维护。</p>
     *
     * @param encounterId                  就诊主键
     * @param orgId                        开立机构（FIFO 库房解析）
     * @param operatorId                   操作人
     * @param signingMedicationRequestIds  本批待签发的药品申请主键（须覆盖当前批次全部西药/中成药签发目标）
     * @return 药品申请主键 → 处方号；无参与行时返回空 Map
     */
    public Map<Long, String> splitAndAssignPrescriptions(
        Long encounterId,
        Long orgId,
        Long operatorId,
        Collection<Long> signingMedicationRequestIds
    ) {
        // Pre：仅装配「本批西药/中成药签草稿 ∪ 已签发未收费非中药」快照；排除中药饮片与其它未签发草稿
        List<PrescriptionSplitLineContext> lines =
            buildSplitLineContexts(encounterId, orgId, signingMedicationRequestIds);
        if (lines.isEmpty()) {
            return Map.of();
        }
        SplitPrescriptionCommand command = new SplitPrescriptionCommand();
        command.setEncounterId(encounterId);
        command.setOperatorId(operatorId);
        command.setLines(lines);
        command.setAmountLimitYuan(outpatientPrescriptionAmountLimitSettingResolver.resolveAmountLimitYuan());
        // Cmd：领域装箱并持久化 prescription_no / rx_type_code（不碰收费状态）
        SplitPrescriptionResult result = medicationRequestDomainService.splitPrescription(command);
        return result.getPrescriptionNoByRequestId();
    }

    /**
     * 收集参与西药/中成药分方的药品行并装配快照。
     *
     * <p>纳入：本批发签目标中仍为 DRAFT 的非中药行；以及本就诊 ACTIVE 且未 SETTLED 的非中药行。
     * 排除：{@code tcmFlag=true} 中药饮片（中药手动分方，禁止被西药签发改号）；
     * 未在本批发签集合内的其它 DRAFT（避免未签发却落处方号）。</p>
     *
     * @param encounterId                  就诊主键
     * @param orgId                        开立机构
     * @param signingMedicationRequestIds  本批待签发药品申请主键
     * @return 参与分方的行快照列表
     */
    private List<PrescriptionSplitLineContext> buildSplitLineContexts(
        Long encounterId,
        Long orgId,
        Collection<Long> signingMedicationRequestIds
    ) {
        List<MedicationRequestListQueryModel> medicationRows =
            medicationRequestQueryService.listMedicationRequestsByEncounterForDoctorStation(
                encounterId, GenerateSource.DOCTOR_PRESCRIPTION.getCode());
        if (medicationRows.isEmpty()) {
            return List.of();
        }
        // Pre：本批待签发主键集合；空集合时仍可仅重分已 ACTIVE 未收费非中药行（防御）
        Set<Long> signingIds = new HashSet<>();
        if (signingMedicationRequestIds != null) {
            for (Long signingId : signingMedicationRequestIds) {
                if (signingId != null) {
                    signingIds.add(signingId);
                }
            }
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
            // Gate：中药饮片不进西药分方，避免改写 IF-DOR-37 已落号及收费/代煎 companion 处方号
            if (Boolean.TRUE.equals(row.getTcmFlag())) {
                continue;
            }
            if (chargeRollup.settledRequestIds.contains(row.getId())) {
                // 已结算行不参与重分方
                continue;
            }
            String status = row.getStatusEnum();
            boolean alreadySignedUnsettled = RequestStatus.ACTIVE.getCode().equals(status);
            // 仅本批发签目标允许以 DRAFT 入方；其它草稿「签发后才有方」
            boolean signingDraft =
                RequestStatus.DRAFT.getCode().equals(status) && signingIds.contains(row.getId());
            if (!alreadySignedUnsettled && !signingDraft) {
                continue;
            }
            candidates.add(row);
            collectEncounterDiagnosisIdsFromIds(row.getEncounterDiagnosisIds(), diagnosisIds);
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
        Map<String, ProductDefinitionDisplayDto> productByKey = productDefinitionDisplayTerminologyAssembler.loadAsMap(productLookups);
        // 同一药品+单位+数量+分类的 FIFO 首段库位相同，缓存避免重复库存试算
        Map<String, Long> fifoLocationByCacheKey = new HashMap<>();

        List<PrescriptionSplitLineContext> lines = new ArrayList<>();
        for (MedicationRequestListQueryModel row : candidates) {
            PrescriptionSplitLineContext ctx = new PrescriptionSplitLineContext();
            ctx.setRequestId(row.getId());
            ctx.setMedicationId(row.getMedicationId());
            ctx.setMethodCode(row.getMethodCode());
            ctx.setRateCode(row.getRateCode());
            ctx.setDosageInstruction(row.getDosageInstruction());
            ctx.setDose(row.getDose());
            ctx.setDoseUnitCode(row.getDoseUnitCode());
            ctx.setGroupId(row.getGroupId());
            ctx.setTcmFlag(row.getTcmFlag());
            Long diagnosisId = EncounterDiagnosisIdsSupport.firstId(row.getEncounterDiagnosisIds());
            String medType = medTypeByDiagnosisId.get(diagnosisId);
            if (StringUtils.isEmpty(medType)) {
                medType = YbMedType.GENERAL_OUTPATIENT.getCode();
            }
            ctx.setMedType(medType);
            // 分方同方：诊断有交集即可；仍写 canonical set key 供装箱解析 id 集，禁止裸串 equals
            ctx.setDiagnosisSetKey(EncounterDiagnosisIdsSupport.toDiagnosisSetKey(row.getEncounterDiagnosisIds()));
            BigDecimal chargeTotal = chargeRollup.chargeTotalByRequestId.get(row.getId());
            if (chargeTotal == null) {
                chargeTotal = BigDecimal.ZERO;
            }
            ctx.setChargeTotalPrice(chargeTotal);
            String productKey = ProductDefinitionKeyUtil.buildCompositeKey(
                TableNameConstants.MED_MEDICATION_DEFINITION, row.getMedicationId());
            ProductDefinitionDisplayDto product = productByKey.get(productKey);
            if (product != null) {
                // 分方特殊管理轨只认药品性质，禁止把门诊拆分属性（整包不拆等）塞进性质快照
                ctx.setPharmacologyCategoryCode(product.getPharmacologyCategoryCode());
                // 普通药西药/中成药分轨依赖目录分类码（medication_category_code）
                ctx.setMedicationCategoryCode(product.getCategoryCode());
            }
            Long pharmacyLocationId = null;
            if (row.getMedicationId() != null && row.getQuantity() != null) {
                String categoryCode = null;
                if (product != null) {
                    categoryCode = product.getCategoryCode();
                }
                String fifoCacheKey = buildFifoLocationCacheKey(
                    row.getMedicationId(),
                    row.getUnitCode(),
                    row.getQuantity(),
                    categoryCode,
                    row.getMethodCode(),
                    Boolean.TRUE.equals(row.getSkinTestFlag()));
                pharmacyLocationId = fifoLocationByCacheKey.get(fifoCacheKey);
                if (pharmacyLocationId == null && !fifoLocationByCacheKey.containsKey(fifoCacheKey)) {
                    pharmacyLocationId = doctorStationAdviceFifoAllocationAssembler.resolvePrimaryMedicationDispenseLocationId(
                        orgId,
                        encounterId,
                        row.getMedicationId(),
                        categoryCode,
                        row.getMethodCode(),
                        row.getUnitCode(),
                        row.getQuantity(),
                        Boolean.TRUE.equals(row.getSkinTestFlag()));
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
     * 构建 FIFO 主库位缓存键（药品+开立单位+数量+目录分类+皮试单批优先）。
     *
     * @param medicationId            药品定义主键
     * @param unitCode                开立单位
     * @param quantity                开立数量
     * @param categoryCode            药品目录 categoryCode
     * @param methodCode              给药途径编码
     * @param skinTestPreferSingleLot 皮试单批优先（BR-ORD-034），与非皮试分行缓存
     * @return 缓存键
     */
    private static String buildFifoLocationCacheKey(
        Long medicationId,
        String unitCode,
        BigDecimal quantity,
        String categoryCode,
        String methodCode,
        boolean skinTestPreferSingleLot
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
        String skinPart;
        if (skinTestPreferSingleLot) {
            skinPart = "ST1";
        } else {
            skinPart = "ST0";
        }
        return medicationId
            + PrescriptionSplitConstants.PARTITION_KEY_SEPARATOR
            + unit
            + PrescriptionSplitConstants.PARTITION_KEY_SEPARATOR
            + qty
            + PrescriptionSplitConstants.PARTITION_KEY_SEPARATOR
            + category
            + PrescriptionSplitConstants.PARTITION_KEY_SEPARATOR
            + method
            + PrescriptionSplitConstants.PARTITION_KEY_SEPARATOR
            + skinPart;
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
     * 从医嘱绑定 ids 串 收集就诊诊断主键（去重写入 target）。
     */
    private static void collectEncounterDiagnosisIdsFromIds(String idsText, Set<Long> target) {
        if (target == null || StringUtils.isEmpty(idsText)) {
            return;
        }
        try {
            target.addAll(EncounterDiagnosisIdsSupport.parseUniqueOrderedIds(idsText, true));
        } catch (NumberFormatException ignored) {
            // lenient 路径不应抛出；兜底忽略
        }
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
