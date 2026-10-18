/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.advice;

import com.zxplat.domain.clinical.allergyintolerance.queries.model.AllergyIntoleranceRecordQueryModel;
import com.zxplat.domain.clinical.allergyintolerance.service.AllergyIntoleranceQueryService;
import com.zxplat.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.enums.ClinicalStatus;
import com.zxplat.domain.share.enums.EnumInterface;
import com.zxplat.domain.share.enums.GenerateSource;
import com.zxplat.domain.share.utils.EnumUtil;
import com.zxplat.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.zxplat.domain.workflow.servicerequest.entity.ServiceRequest;
import com.zxplat.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 皮试药 → 最新护士登记结果读侧加载器（doctor-prescription companion SR → AllergyIntolerance）。
 *
 * <p>供住院/门诊医生站医嘱列表、住院护士站校对/执行列表等同源回显 {@code skinTestOutcome*}。</p>
 */
@Component
@RequiredArgsConstructor
public class SkinTestOutcomeByMedicationRequestLoader {

    /** 皮试检查活动定义解析。 */
    private final ActivityDefinitionQueryService activityDefinitionQueryService;

    /** companion 皮试检查 SR 批量查询。 */
    private final ServiceRequestQueryService serviceRequestQueryService;

    /** 就诊维度过敏/皮试登记读模型。 */
    private final AllergyIntoleranceQueryService allergyIntoleranceQueryService;

    /**
     * 多就诊批量加载皮试药最新登记结果（medReqId 全局唯一，结果 Map 可跨就诊合并）。
     *
     * @param medByEncounter 就诊 → 药品明细列表；可空
     * @return 皮试药 requestId → 最新结果；无登记时不含该 key
     */
    public Map<Long, SkinTestOutcomeSnapshot> loadByEncounters(
        Map<Long, List<MedicationRequestListQueryModel>> medByEncounter) {
        Map<Long, SkinTestOutcomeSnapshot> merged = new HashMap<>();
        if (medByEncounter == null || medByEncounter.isEmpty()) {
            return merged;
        }
        for (Map.Entry<Long, List<MedicationRequestListQueryModel>> entry : medByEncounter.entrySet()) {
            Map<Long, SkinTestOutcomeSnapshot> perEncounter =
                this.loadForEncounter(entry.getKey(), entry.getValue());
            merged.putAll(perEncounter);
        }
        return merged;
    }

    /**
     * 单就诊加载皮试药最新登记结果。
     *
     * @param encounterId     就诊主键
     * @param medicationRows  本就诊药品请求列表；可空
     * @return 皮试药 requestId → 最新结果
     */
    public Map<Long, SkinTestOutcomeSnapshot> loadForEncounter(
        Long encounterId,
        List<MedicationRequestListQueryModel> medicationRows) {
        Map<Long, SkinTestOutcomeSnapshot> outcomeByMedicationRequestId = new HashMap<>();
        if (encounterId == null || medicationRows == null || medicationRows.isEmpty()) {
            return outcomeByMedicationRequestId;
        }
        List<Long> skinTestMedicationRequestIds = new ArrayList<>();
        for (MedicationRequestListQueryModel row : medicationRows) {
            if (Boolean.TRUE.equals(row.getSkinTestFlag()) && row.getId() != null) {
                skinTestMedicationRequestIds.add(row.getId());
            }
        }
        if (skinTestMedicationRequestIds.isEmpty()) {
            return outcomeByMedicationRequestId;
        }
        Long skinTestActivityId;
        try {
            skinTestActivityId = activityDefinitionQueryService.requireIdByExactName(
                GeneralSharedConstants.SKIN_TEST_INSPECTION_ACTIVITY_NAME);
        } catch (BusinessException ex) {
            return outcomeByMedicationRequestId;
        }
        List<ServiceRequest> companionServiceRequests = serviceRequestQueryService.listServiceRequestsByBasedOnAnchors(
            TableNameConstants.MED_MEDICATION_REQUEST, skinTestMedicationRequestIds);
        // BR 最新覆盖：索引该皮试药下全部 companion SR id（禁止 putIfAbsent 只留首条）
        Map<Long, Set<Long>> companionSrIdsByMedicationRequestId = new HashMap<>();
        String doctorPrescriptionSource = GenerateSource.DOCTOR_PRESCRIPTION.getCode();
        for (ServiceRequest companion : companionServiceRequests) {
            if (companion == null || Boolean.TRUE.equals(companion.getIsDeleted())) {
                continue;
            }
            if (!doctorPrescriptionSource.equals(companion.getGenerateSourceEnum())) {
                continue;
            }
            if (!skinTestActivityId.equals(companion.getActivityId())) {
                continue;
            }
            if (companion.getBasedOnId() == null || companion.getId() == null) {
                continue;
            }
            Set<Long> companionIds = companionSrIdsByMedicationRequestId.get(companion.getBasedOnId());
            if (companionIds == null) {
                companionIds = new LinkedHashSet<>();
                companionSrIdsByMedicationRequestId.put(companion.getBasedOnId(), companionIds);
            }
            companionIds.add(companion.getId());
        }
        if (companionSrIdsByMedicationRequestId.isEmpty()) {
            return outcomeByMedicationRequestId;
        }
        List<AllergyIntoleranceRecordQueryModel> allergyRecords =
            allergyIntoleranceQueryService.getRecordInfo(encounterId);
        if (allergyRecords == null) {
            allergyRecords = List.of();
        }
        for (Map.Entry<Long, Set<Long>> entry : companionSrIdsByMedicationRequestId.entrySet()) {
            AllergyIntoleranceRecordQueryModel latest = null;
            // 在该皮试药全部 companion requestId 上取 recordedDate 最新的 Allergy
            for (AllergyIntoleranceRecordQueryModel allergyRecord : allergyRecords) {
                if (allergyRecord == null || allergyRecord.getRequestId() == null) {
                    continue;
                }
                if (!entry.getValue().contains(allergyRecord.getRequestId())) {
                    continue;
                }
                if (this.isLaterAllergyRecordForSkinTestOutcome(allergyRecord, latest)) {
                    latest = allergyRecord;
                }
            }
            if (latest != null && StringUtils.isNotEmpty(latest.getClinicalStatusEnum())) {
                SkinTestOutcomeSnapshot snapshot = new SkinTestOutcomeSnapshot();
                snapshot.setOutcomeCode(latest.getClinicalStatusEnum());
                snapshot.setOutcomeText(
                    this.resolveEnumCodeDisplay(ClinicalStatus.class, latest.getClinicalStatusEnum()));
                outcomeByMedicationRequestId.put(entry.getKey(), snapshot);
            }
        }
        return outcomeByMedicationRequestId;
    }

    /**
     * 皮试结果「最新覆盖」比较：{@code recordedDate} 非空优先于空；同刻取更大 id。
     *
     * @param candidate 候选 Allergy
     * @param baseline  当前最新；可空
     * @return true 表示 candidate 应覆盖 baseline
     */
    private boolean isLaterAllergyRecordForSkinTestOutcome(
        AllergyIntoleranceRecordQueryModel candidate,
        AllergyIntoleranceRecordQueryModel baseline) {
        if (candidate == null) {
            return false;
        }
        if (baseline == null) {
            return true;
        }
        LocalDateTime candidateDate = candidate.getRecordedDate();
        LocalDateTime baselineDate = baseline.getRecordedDate();
        // recordedDate 非空优先于空（nulls last）
        if (candidateDate != null && baselineDate == null) {
            return true;
        }
        if (candidateDate == null && baselineDate != null) {
            return false;
        }
        if (candidateDate != null && baselineDate != null) {
            int cmp = candidateDate.compareTo(baselineDate);
            if (cmp > 0) {
                return true;
            }
            if (cmp < 0) {
                return false;
            }
        }
        // 同刻或均空：更大 id 优先
        Long candidateId = candidate.getId();
        Long baselineId = baseline.getId();
        if (candidateId == null) {
            return false;
        }
        if (baselineId == null) {
            return true;
        }
        return candidateId.compareTo(baselineId) > 0;
    }

    /**
     * 持久化枚举 code → 展示文案（皮试临床状态等）。
     *
     * @param enumClass 枚举类型
     * @param code      持久化码
     * @return 展示名；未命中时 null
     */
    private <E extends Enum<E> & EnumInterface> String resolveEnumCodeDisplay(Class<E> enumClass, String code) {
        if (StringUtils.isEmpty(code)) {
            return null;
        }
        E enumValue = EnumUtil.fromCode(enumClass, code);
        if (enumValue == null) {
            return null;
        }
        return enumValue.getDisplay();
    }
}
