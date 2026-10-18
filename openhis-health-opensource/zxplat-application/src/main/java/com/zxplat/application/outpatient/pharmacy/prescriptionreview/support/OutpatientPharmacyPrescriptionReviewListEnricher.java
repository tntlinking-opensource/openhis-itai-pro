/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.pharmacy.prescriptionreview.support;

import com.zxplat.application.outpatient.pharmacy.prescriptionreview.dto.PrescriptionReviewListRowDto;
import com.zxplat.application.shared.encounter.EncounterContextReader;
import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.zxplat.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.zxplat.domain.administration.encounter.entity.EncounterDiagnosis;
import com.zxplat.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.zxplat.domain.administration.encounter.service.EncounterQueryService;
import com.zxplat.domain.administration.practitioner.service.PractitionerQueryService;
import com.zxplat.domain.clinical.conditiondefinition.queries.model.ConditionDefinitionBatchItemQueryModel;
import com.zxplat.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.zxplat.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.zxplat.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.zxplat.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.zxplat.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.zxplat.domain.share.utils.EncounterDiagnosisIdsSupport;
import com.zxplat.domain.share.utils.MonetaryAmountSupport;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 点评记录列表方级指标 enrichment（AS-OP-PHA-007 IF-PRV-02/03）。
 *
 * <p>跨聚合编排落在应用层（B.6.4）：药品申请行 → 目录标志/名称 → 收费总额 → 就诊年龄 → 诊断并集 → 开方人姓名。
 * 不写点评聚合；指标非 {@code cus_prescription_review} 落库字段。</p>
 */
@Component
@RequiredArgsConstructor
public class OutpatientPharmacyPrescriptionReviewListEnricher {

    /** 药品申请读侧（按处方号批量行）。 */
    private final MedicationRequestQueryService medicationRequestQueryService;

    /** 药品目录标志与通用名。 */
    private final MedicationDefinitionQueryService medicationDefinitionQueryService;

    /** 收费行总额。 */
    private final ChargeItemQueryService chargeItemQueryService;

    /** 门诊就诊上下文（年龄）。 */
    private final EncounterContextReader encounterContextReader;

    /** 就诊诊断实体。 */
    private final EncounterQueryService encounterQueryService;

    /** 诊断定义名称。 */
    private final ConditionDefinitionQueryService conditionDefinitionQueryService;

    /** 开方人姓名。 */
    private final PractitionerQueryService practitionerQueryService;

    /**
     * 为页内点评列表行补齐工作表方级指标列。
     *
     * @param rows 已映射点评字段的列表行；可空
     */
    public void enrichWorksheetColumns(List<PrescriptionReviewListRowDto> rows) {
        // 空页：无需跨聚合读
        if (rows == null || rows.isEmpty()) {
            return;
        }
        LinkedHashSet<String> prescriptionNos = new LinkedHashSet<>();
        for (PrescriptionReviewListRowDto row : rows) {
            // 无处方号则无法 enrichment，跳过收集
            if (row == null || StringUtils.isEmpty(row.getPrescriptionNo())) {
                continue;
            }
            prescriptionNos.add(row.getPrescriptionNo().trim());
        }
        // 页内无有效方号：指标列保持空
        if (prescriptionNos.isEmpty()) {
            return;
        }
        // Pre：按方号批量药嘱行
        List<MedicationRequestListQueryModel> requestRows =
            this.medicationRequestQueryService.listMedicationRequestListModelsByPrescriptionNos(prescriptionNos);
        Map<String, List<MedicationRequestListQueryModel>> linesByPrescriptionNo =
            this.groupLinesByPrescriptionNo(requestRows);

        LinkedHashSet<Long> medicationIds = new LinkedHashSet<>();
        LinkedHashSet<Long> medicationRequestIds = new LinkedHashSet<>();
        LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
        LinkedHashSet<Long> encounterDiagnosisIds = new LinkedHashSet<>();
        LinkedHashSet<Long> requesterIds = new LinkedHashSet<>();
        for (MedicationRequestListQueryModel line : requestRows) {
            // 空行跳过，避免主键污染
            if (line == null) {
                continue;
            }
            if (line.getMedicationId() != null) {
                medicationIds.add(line.getMedicationId());
            }
            if (line.getId() != null) {
                medicationRequestIds.add(line.getId());
            }
            if (line.getEncounterId() != null) {
                encounterIds.add(line.getEncounterId());
            }
            if (line.getRequesterId() != null) {
                requesterIds.add(line.getRequesterId());
            }
            this.collectEncounterDiagnosisIds(line.getEncounterDiagnosisIds(), encounterDiagnosisIds);
        }

        Map<Long, MedicationDefinition> definitionById = this.indexDefinitions(medicationIds);
        Map<Long, BigDecimal> chargeTotalByMedReqId = this.indexChargeTotals(medicationRequestIds);
        Map<Long, String> ageTextByEncounterId = this.indexAgeText(encounterIds);
        Map<Long, Long> conditionDefinitionIdByEncounterDiagnosisId =
            this.indexConditionDefinitionIds(encounterDiagnosisIds);
        Map<Long, String> conditionNameById =
            this.indexConditionNames(conditionDefinitionIdByEncounterDiagnosisId.values());
        Map<Long, String> practitionerNameById = this.practitionerQueryService.mapNamesByIds(requesterIds);

        // Asm：按列表行顺序写回方级指标
        for (PrescriptionReviewListRowDto row : rows) {
            if (row == null || StringUtils.isEmpty(row.getPrescriptionNo())) {
                continue;
            }
            String prescriptionNo = row.getPrescriptionNo().trim();
            List<MedicationRequestListQueryModel> lines = linesByPrescriptionNo.get(prescriptionNo);
            // 无药嘱行：指标列空，点评字段仍保留
            if (lines == null || lines.isEmpty()) {
                continue;
            }
            this.applyFoldMetrics(
                row,
                lines,
                definitionById,
                chargeTotalByMedReqId,
                ageTextByEncounterId,
                conditionDefinitionIdByEncounterDiagnosisId,
                conditionNameById,
                practitionerNameById);
        }
    }

    /**
     * 药嘱行按处方号分组（保首次出现顺序）。
     *
     * @param requestRows 药嘱列表模型
     * @return 处方号 → 行列表
     */
    private Map<String, List<MedicationRequestListQueryModel>> groupLinesByPrescriptionNo(
        List<MedicationRequestListQueryModel> requestRows) {
        Map<String, List<MedicationRequestListQueryModel>> map = new LinkedHashMap<>();
        // 空集合：返回空 Map
        if (requestRows == null || requestRows.isEmpty()) {
            return map;
        }
        for (MedicationRequestListQueryModel line : requestRows) {
            if (line == null || StringUtils.isEmpty(line.getPrescriptionNo())) {
                continue;
            }
            String key = line.getPrescriptionNo().trim();
            List<MedicationRequestListQueryModel> bucket = map.get(key);
            // 首次出现该方号：建桶
            if (bucket == null) {
                bucket = new ArrayList<>();
                map.put(key, bucket);
            }
            bucket.add(line);
        }
        return map;
    }

    /**
     * 收集就诊诊断主键到目标集合（lenient 解析）。
     *
     * @param idsText 逗号分隔就诊诊断 id 串
     * @param target  目标集合
     */
    private void collectEncounterDiagnosisIds(String idsText, Set<Long> target) {
        List<Long> ids = EncounterDiagnosisIdsSupport.parseOrderedIds(idsText, true);
        for (Long id : ids) {
            if (id != null) {
                target.add(id);
            }
        }
    }

    /**
     * 批量索引药品定义。
     *
     * @param medicationIds 药品定义主键
     * @return id → 定义
     */
    private Map<Long, MedicationDefinition> indexDefinitions(LinkedHashSet<Long> medicationIds) {
        Map<Long, MedicationDefinition> map = new HashMap<>();
        // 无目录 id：跳过定义读
        if (medicationIds == null || medicationIds.isEmpty()) {
            return map;
        }
        List<MedicationDefinition> rows = this.medicationDefinitionQueryService.findAllByIds(medicationIds);
        for (MedicationDefinition def : rows) {
            if (def != null && def.getId() != null) {
                map.put(def.getId(), def);
            }
        }
        return map;
    }

    /**
     * 按药品申请 id 汇总收费行 totalPrice。
     *
     * @param medicationRequestIds 药品申请主键
     * @return medReqId → 行额合计（未 scale）
     */
    private Map<Long, BigDecimal> indexChargeTotals(LinkedHashSet<Long> medicationRequestIds) {
        Map<Long, BigDecimal> map = new HashMap<>();
        // 无药嘱 id：无收费可挂
        if (medicationRequestIds == null || medicationRequestIds.isEmpty()) {
            return map;
        }
        ChargeItemQueryCommand cmd = new ChargeItemQueryCommand();
        cmd.setServiceIds(medicationRequestIds);
        List<ChargeItem> chargeItems = this.chargeItemQueryService.listChargeItemsByServiceIds(cmd);
        for (ChargeItem item : chargeItems) {
            // 缺 serviceId 或金额则跳过，避免污染合计
            if (item == null || item.getServiceId() == null || item.getTotalPrice() == null) {
                continue;
            }
            BigDecimal current = map.get(item.getServiceId());
            // 同药嘱多收费行累加
            if (current == null) {
                map.put(item.getServiceId(), item.getTotalPrice());
            } else {
                map.put(item.getServiceId(), current.add(item.getTotalPrice()));
            }
        }
        return map;
    }

    /**
     * 就诊 id → 年龄文案。
     *
     * @param encounterIds 就诊主键
     * @return encounterId → ageText
     */
    private Map<Long, String> indexAgeText(LinkedHashSet<Long> encounterIds) {
        Map<Long, String> map = new HashMap<>();
        // 无就诊：年龄列空
        if (encounterIds == null || encounterIds.isEmpty()) {
            return map;
        }
        List<OutpatientEncounterContextQueryModel> contexts =
            this.encounterContextReader.listOutpatientContextsByEncounterIdsOrdered(new ArrayList<>(encounterIds));
        for (OutpatientEncounterContextQueryModel ctx : contexts) {
            if (ctx != null && ctx.getEncounterId() != null) {
                map.put(ctx.getEncounterId(), ctx.getAgeText());
            }
        }
        return map;
    }

    /**
     * 就诊诊断 id → 诊断定义 id。
     *
     * @param encounterDiagnosisIds 就诊诊断主键
     * @return ED id → conditionDefinitionId
     */
    private Map<Long, Long> indexConditionDefinitionIds(LinkedHashSet<Long> encounterDiagnosisIds) {
        Map<Long, Long> map = new HashMap<>();
        // 无诊断绑定：诊断列空
        if (encounterDiagnosisIds == null || encounterDiagnosisIds.isEmpty()) {
            return map;
        }
        for (EncounterDiagnosis ed :
            this.encounterQueryService.listEncounterDiagnosisEntitiesByIds(encounterDiagnosisIds)) {
            if (ed != null && ed.getId() != null && ed.getConditionDefinitionId() != null) {
                map.put(ed.getId(), ed.getConditionDefinitionId());
            }
        }
        return map;
    }

    /**
     * 诊断定义 id → 名称。
     *
     * @param conditionDefinitionIds 诊断定义主键
     * @return id → name
     */
    private Map<Long, String> indexConditionNames(java.util.Collection<Long> conditionDefinitionIds) {
        Map<Long, String> map = new HashMap<>();
        // 无定义 id：名称 Map 空
        if (conditionDefinitionIds == null || conditionDefinitionIds.isEmpty()) {
            return map;
        }
        Set<Long> unique = new HashSet<>(conditionDefinitionIds);
        List<ConditionDefinitionBatchItemQueryModel> conditions =
            this.conditionDefinitionQueryService.getConditionDefinitionListByIds(new ArrayList<>(unique));
        for (ConditionDefinitionBatchItemQueryModel item : conditions) {
            if (item != null && item.getId() != null) {
                map.put(item.getId(), item.getName());
            }
        }
        return map;
    }

    /**
     * 将一方药嘱行折叠为工作表指标并写回列表行。
     *
     * @param row                                      目标列表行
     * @param lines                                    同方药嘱行
     * @param definitionById                           目录索引
     * @param chargeTotalByMedReqId                    收费合计
     * @param ageTextByEncounterId                     年龄
     * @param conditionDefinitionIdByEncounterDiagnosisId 就诊诊断→定义
     * @param conditionNameById                        定义名称
     * @param practitionerNameById                     开方人姓名
     */
    private void applyFoldMetrics(
        PrescriptionReviewListRowDto row,
        List<MedicationRequestListQueryModel> lines,
        Map<Long, MedicationDefinition> definitionById,
        Map<Long, BigDecimal> chargeTotalByMedReqId,
        Map<Long, String> ageTextByEncounterId,
        Map<Long, Long> conditionDefinitionIdByEncounterDiagnosisId,
        Map<Long, String> conditionNameById,
        Map<Long, String> practitionerNameById) {
        LinkedHashSet<Long> distinctMedicationIds = new LinkedHashSet<>();
        LinkedHashSet<Long> basicMedicationIds = new LinkedHashSet<>();
        LinkedHashSet<String> genericNames = new LinkedHashSet<>();
        boolean anyAntibiotic = false;
        boolean anyInject = false;
        BigDecimal amountSum = BigDecimal.ZERO;
        Long encounterId = null;
        Long orderingPractitionerId = null;
        List<String> diagnosisIdsTexts = new ArrayList<>();

        for (MedicationRequestListQueryModel line : lines) {
            if (line == null) {
                continue;
            }
            // 年龄/医师取同方首个非空就诊与开方人
            if (encounterId == null && line.getEncounterId() != null) {
                encounterId = line.getEncounterId();
            }
            if (orderingPractitionerId == null && line.getRequesterId() != null) {
                orderingPractitionerId = line.getRequesterId();
            }
            if (StringUtils.isNotEmpty(line.getEncounterDiagnosisIds())) {
                diagnosisIdsTexts.add(line.getEncounterDiagnosisIds());
            }
            Long medicationId = line.getMedicationId();
            if (medicationId != null) {
                distinctMedicationIds.add(medicationId);
                MedicationDefinition def = definitionById.get(medicationId);
                // 目录命中才累计标志与通用名
                if (def != null) {
                    if (Boolean.TRUE.equals(def.getAntibioticFlag())) {
                        anyAntibiotic = true;
                    }
                    if (Boolean.TRUE.equals(def.getInjectFlag())) {
                        anyInject = true;
                    }
                    if (Boolean.TRUE.equals(def.getBasicFlag())) {
                        basicMedicationIds.add(medicationId);
                    }
                    if (StringUtils.isNotEmpty(def.getName())) {
                        genericNames.add(def.getName().trim());
                    }
                }
            }
            if (line.getId() != null) {
                BigDecimal lineTotal = chargeTotalByMedReqId.get(line.getId());
                // 有收费行额则累加处方金额
                if (lineTotal != null) {
                    amountSum = amountSum.add(lineTotal);
                }
            }
        }

        // Asm：写回年龄
        if (encounterId != null) {
            row.setPatientAgeText(ageTextByEncounterId.get(encounterId));
        }
        // Asm：诊断并集展示名
        String unionIds = EncounterDiagnosisIdsSupport.unionOrderedIdsTexts(diagnosisIdsTexts);
        String diagnosisName = EncounterDiagnosisIdsSupport.joinOrderedDisplayNames(
            unionIds,
            encounterDiagnosisId -> {
                Long conditionDefinitionId = conditionDefinitionIdByEncounterDiagnosisId.get(encounterDiagnosisId);
                // 无定义映射则该诊断段空名
                if (conditionDefinitionId == null) {
                    return null;
                }
                return conditionNameById.get(conditionDefinitionId);
            });
        row.setDiagnosisName(diagnosisName);
        row.setDrugVarietyCount(Integer.valueOf(distinctMedicationIds.size()));
        // 抗菌/注射：有则 1，无则 0（与工作表 0/1 口径）
        if (anyAntibiotic) {
            row.setAntibioticFlag(Integer.valueOf(1));
        } else {
            row.setAntibioticFlag(Integer.valueOf(0));
        }
        if (anyInject) {
            row.setInjectFlag(Integer.valueOf(1));
        } else {
            row.setInjectFlag(Integer.valueOf(0));
        }
        row.setBasicDrugVarietyCount(Integer.valueOf(basicMedicationIds.size()));
        row.setGenericNameCount(Integer.valueOf(genericNames.size()));
        row.setPrescriptionAmount(MonetaryAmountSupport.scaleDisplay(amountSum));
        // Asm：开方医师姓名
        if (orderingPractitionerId != null) {
            row.setOrderingPractitionerName(practitionerNameById.get(orderingPractitionerId));
        }
    }
}
