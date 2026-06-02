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

package com.openhis.health.domain.emergency.emgtriage.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 急诊分诊聚合根 {@code emg_triage}（DM-EMG-001 / SDD §4.2）。
 */
@Getter
@Setter
@Entity
@Table(name = "emg_triage")
public class EmgTriage extends BaseAuditedEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    /** 被后继二次分诊取代的历史行（非作废）。 */
    public static final String STATUS_SUPERSEDED = "SUPERSEDED";
    public static final String STATUS_VOIDED = "VOIDED";

    public static final String DESTINATION_SINGLE = "SINGLE";
    public static final String DESTINATION_MULTI = "MULTI";

    public static final String ARRIVAL_EXTERNAL_TRANSFER = "EXTERNAL_TRANSFER";

    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "triage_time", nullable = false)
    private LocalDateTime triageTime;

    @Column(name = "triage_status_enum", nullable = false)
    private String triageStatusEnum;

    @Column(name = "chief_complaint_json")
    private String chiefComplaintJson;

    @Column(name = "symptom_json")
    private String symptomJson;

    @Column(name = "triage_level_enum", nullable = false)
    private Integer triageLevelEnum;

    @Column(name = "suggested_level_enum")
    private Integer suggestedLevelEnum;

    @Column(name = "level_adjust_reason")
    private String levelAdjustReason;

    @Column(name = "change_reason_code")
    private String changeReasonCode;

    @Column(name = "change_remark")
    private String changeRemark;

    @Column(name = "last_amended_by")
    private Long lastAmendedBy;

    @Column(name = "last_amended_at")
    private LocalDateTime lastAmendedAt;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @Column(name = "assigned_doctor_snapshot")
    private String assignedDoctorSnapshot;

    @Column(name = "disease_types_json")
    private String diseaseTypesJson;

    @Column(name = "patient_source")
    private String patientSource;

    @Column(name = "is_green_channel", nullable = false)
    private Boolean isGreenChannel;

    @Column(name = "is_free_register", nullable = false)
    private Boolean isFreeRegister;

    @Column(name = "queue_priority", nullable = false)
    private Integer queuePriority;

    @Column(name = "note")
    private String note;

    @Column(name = "void_reason")
    private String voidReason;

    @Column(name = "void_by")
    private Long voidBy;

    @Column(name = "void_at")
    private LocalDateTime voidAt;

    @Column(name = "prior_emg_triage_id")
    private Long priorEmgTriageId;

    @Column(name = "superseded_by_id")
    private Long supersededById;

    @Column(name = "triage_registration_mode_enum")
    private String triageRegistrationModeEnum;

    @Column(name = "is_staged", nullable = false)
    private Boolean isStaged;

    @Column(name = "stage_queue_seq")
    private Integer stageQueueSeq;

    @Column(name = "provisional_triage_no")
    private String provisionalTriageNo;

    @Column(name = "special_population_tags_json")
    private String specialPopulationTagsJson;

    @Column(name = "mci_event_id")
    private Long mciEventId;

    @Column(name = "blacklist_alert_ack")
    private Boolean blacklistAlertAck;

    @Column(name = "arrival_mode_enum")
    private String arrivalModeEnum;

    @Column(name = "transfer_in_org_id")
    private Long transferInOrgId;

    @Column(name = "destination_mode_enum", nullable = false)
    private String destinationModeEnum;

    @Column(name = "secondary_location_id")
    private Long secondaryLocationId;

    @Column(name = "green_channel_onset_time")
    private LocalDateTime greenChannelOnsetTime;

    @Column(name = "epidemic_survey_json")
    private String epidemicSurveyJson;

    @Column(name = "specialty_green_channel_code")
    private String specialtyGreenChannelCode;

    @Column(name = "ambulance_120_json")
    private String ambulance120Json;

    @Column(name = "police_110_json")
    private String police110Json;

    @Column(name = "monitor_device_binding_json")
    private String monitorDeviceBindingJson;

    @Column(name = "wristband_printed_at")
    private LocalDateTime wristbandPrintedAt;

    @Column(name = "vitals_auto_tier_policy_id")
    private String vitalsAutoTierPolicyId;

    // 覆盖建议分级时 levelAdjustReason 必填（实体不变式，DM v2.9）
    public void assertLevelAdjustReasonWhenOverride() {
        if (suggestedLevelEnum == null) {
            return;
        }
        if (triageLevelEnum != null && triageLevelEnum.equals(suggestedLevelEnum)) {
            return;
        }
        if (StringUtils.isBlank(levelAdjustReason)) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_RULE_CONFLICT, "覆盖建议分级时 levelAdjustReason 必填");
        }
    }

    /**
     * 原 BR-EMG-ETG-018 主诉 JSON 校验已取消，保留方法签名供调用方兼容。
     *
     * @param chiefComplaintOptional 历史参数，不再参与校验
     */
    @SuppressWarnings("unused")
    public void assertChiefComplaintJsonForSave(boolean chiefComplaintOptional) {
        // intentionally empty
    }

    // --- BR-EMG-ETG-005
    public void assertGreenChannelLevel() {
        if (!Boolean.TRUE.equals(isGreenChannel)) {
            return;
        }
        if (triageLevelEnum == null || (triageLevelEnum != 1 && triageLevelEnum != 2)) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_RULE_CONFLICT, "绿通仅允许 I/II 级");
        }
        if (!Boolean.TRUE.equals(isFreeRegister)) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_RULE_CONFLICT, "绿通须 isFreeRegister=true");
        }
    }

    // --- BR-EMG-ETG-011
    public void assertDestinationModeAndSecondary() {
        if (DESTINATION_MULTI.equals(destinationModeEnum) && secondaryLocationId == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "MULTI 去向须填 secondaryLocationId");
        }
    }

    /**
     * BR-EMG-ETG-012：外院转入时转入机构按机构规则可必填——此处做最小非空校验（细则参数化由应用层扩展）。
     */
    public void assertTransferInWhenExternal() {
        if (ARRIVAL_EXTERNAL_TRANSFER.equals(arrivalModeEnum) && transferInOrgId == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "外院转入须选择转入机构");
        }
    }

    // --- BR-EMG-ETG-010
    public void assertQueuePriorityPositive() {
        if (queuePriority == null || queuePriority < 1) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "queuePriority 须为正整数");
        }
    }

    /** 作废迁移（BR-EMG-ETG-009）。 */
    public void applyVoid(Long operatorId, String reason) {
        if (!STATUS_ACTIVE.equals(triageStatusEnum)) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_STATUS, "仅 ACTIVE 可作废");
        }
        if (StringUtils.isBlank(reason) || operatorId == null) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "作废原因与操作人必填");
        }
        this.triageStatusEnum = STATUS_VOIDED;
        this.voidReason = reason.trim();
        this.voidBy = operatorId;
        this.voidAt = LocalDateTime.now();
    }

    public void assertAmendAllowed() {
        if (!STATUS_ACTIVE.equals(triageStatusEnum)) {
            throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_STATUS, "仅 ACTIVE 可修订");
        }
    }

    public boolean isGreenChannelActivation(Boolean previousGreen) {
        return !Boolean.TRUE.equals(previousGreen) && Boolean.TRUE.equals(this.isGreenChannel);
    }

    public boolean isLevelOverride(Integer oldLevel) {
        if (suggestedLevelEnum == null) {
            return false;
        }
        if (triageLevelEnum == null) {
            return false;
        }
        return !Objects.equals(oldLevel, triageLevelEnum)
                && !Objects.equals(suggestedLevelEnum, triageLevelEnum)
                && StringUtils.isNotBlank(levelAdjustReason);
    }

    /** BR-EMG-ETG-014：暂存记录由队列集成侧过滤，聚合内不追加排序不变式。 */
    public void assertStagingDoesNotQueue() {
        // no-op by design
    }
}
