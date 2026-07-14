/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.allergyintolerance.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.openhis.whale.core.application.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 过敏与不耐受聚合根。
 */
@Getter
@Setter
@Entity
@Table(name = "cln_allergy_intolerance")
public class AllergyIntolerance extends BaseAuditedEntity {
    @Column(name = "request_id", nullable = false)
    private Long requestId;
    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;
    @Column(name = "patient_id", nullable = false)
    private Long patientId;
    @Column(name = "medication_id")
    private Long medicationId;
    @Column(name = "lot_number")
    private String lotNumber;
    @Column(name = "clinical_status_enum", nullable = false)
    private String clinicalStatusEnum;
    @Column(name = "verification_status_enum", nullable = false)
    private String verificationStatusEnum;
    @Column(name = "type_enum")
    private String typeEnum;
    @Column(name = "category_code")
    private String categoryCode;
    @Column(name = "criticality_enum")
    private String criticalityEnum;
    @Column(name = "code")
    private String code;
    @Column(name = "description")
    private String description;
    @Column(name = "severity_enum")
    private String severityEnum;
    @Column(name = "onset_date_time")
    private LocalDateTime onsetDateTime;
    @Column(name = "recorded_date")
    private LocalDateTime recordedDate;
    @Column(name = "last_reaction_occurrence")
    private LocalDateTime lastReactionOccurrence;
    @Column(name = "exposure_route")
    private String exposureRoute;
    @Column(name = "practitioner_id")
    private Long practitionerId;
    @Column(name = "check_practitioner_id")
    private Long checkPractitionerId;
    @Column(name = "note")
    private String note;

    /**
     * 校验保存锚点字段。
     */
    public void assertRequiredReferences() {
        if (this.requestId == null || this.encounterId == null || this.patientId == null) {
            throw new BusinessException(ErrorCodeConstants.ALLERGY_INTOLERANCE_INVALID_ARGUMENT, "申请、就诊、患者标识不能为空");
        }
    }

    /**
     * 校验状态字段成对存在。
     */
    public void assertStatusPairPresent() {
        if (this.clinicalStatusEnum == null || this.verificationStatusEnum == null) {
            throw new BusinessException(ErrorCodeConstants.ALLERGY_INTOLERANCE_INVALID_ARGUMENT, "临床状态和验证状态必须同时存在");
        }
    }

    /**
     * 校验可删除条件。
     */
    public void assertDeletable() {
        if (this.getIsDeleted() != null && this.getIsDeleted()) {
            throw new BusinessException(ErrorCodeConstants.ALLERGY_INTOLERANCE_RULE_CONFLICT, "记录已删除，不能重复删除");
        }
    }

    /**
     * 执行逻辑删除。
     *
     * @param operatorId 操作人
     */
    public void markDeleted(Long operatorId) {
        this.setIsDeleted(Boolean.TRUE);
        this.setDeletedBy(operatorId);
        this.setDeletedAt(LocalDateTime.now());
    }

    /**
     * 校验人员引用格式。
     */
    public void assertPractitionerReference() {
        if (this.practitionerId != null && this.practitionerId <= 0) {
            throw new BusinessException(ErrorCodeConstants.ALLERGY_INTOLERANCE_INVALID_ARGUMENT, "记录者标识非法");
        }
        if (this.checkPractitionerId != null && this.checkPractitionerId <= 0) {
            throw new BusinessException(ErrorCodeConstants.ALLERGY_INTOLERANCE_INVALID_ARGUMENT, "断言者标识非法");
        }
        if (StringUtils.isNotEmpty(this.lotNumber) && this.lotNumber.length() > 64) {
            throw new BusinessException(ErrorCodeConstants.ALLERGY_INTOLERANCE_INVALID_ARGUMENT, "批号长度不能超过64");
        }
    }
}
