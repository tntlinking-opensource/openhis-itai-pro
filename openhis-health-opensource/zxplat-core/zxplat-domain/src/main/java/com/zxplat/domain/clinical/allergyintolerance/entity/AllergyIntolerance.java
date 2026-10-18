/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.allergyintolerance.entity;

import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.enums.AllergyRecordType;
import com.zxplat.domain.share.enums.ClinicalStatus;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.zxplat.framework.core.application.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 过敏与不耐受聚合根。
 */
@Getter
@Setter
@Entity
@Table(name = "cln_allergy_intolerance")
public class AllergyIntolerance extends BaseAuditedEntity {
    @Column(name = "request_id")
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
     * 是否皮试来源（显式 skin_test，或历史空类型但绑了申请）。
     *
     * @return 皮试行返回 true
     */
    public boolean isSkinTestRecord() {
        // 新数据以 type_enum=skin_test 为准
        if (AllergyRecordType.SKIN_TEST.getCode().equals(this.typeEnum)) {
            return true;
        }
        // 存量皮试行可能未写 type，仍靠申请主键识别
        if (StringUtils.isEmpty(this.typeEnum) && this.requestId != null) {
            return true;
        }
        return false;
    }

    /**
     * 是否主诉登记。
     *
     * @return 主诉行返回 true
     */
    public boolean isDeclaredRecord() {
        return AllergyRecordType.DECLARED.getCode().equals(this.typeEnum);
    }

    /**
     * 是否「无已知过敏」声明。
     *
     * @return NKDA 行返回 true
     */
    public boolean isNkdaRecord() {
        return AllergyRecordType.NKDA.getCode().equals(this.typeEnum);
    }

    /**
     * 是否计入患者有效过敏史（阳性皮试或仍有效的主诉）。
     *
     * @return 有效过敏原返回 true
     */
    public boolean isActiveAllergen() {
        // 已软删不参与过敏史
        if (Boolean.TRUE.equals(this.getIsDeleted())) {
            return false;
        }
        // NKDA 表示无过敏，不是过敏原
        if (this.isNkdaRecord()) {
            return false;
        }
        // 仅 clinical_status=active 计入有效过敏（皮试阳性 / 仍有效主诉）
        if (!ClinicalStatus.ACTIVE.getCode().equals(this.clinicalStatusEnum)) {
            return false;
        }
        // 阳性皮试或仍有效的主诉才算过敏原
        if (this.isSkinTestRecord() || this.isDeclaredRecord()) {
            return true;
        }
        return false;
    }

    /**
     * 校验保存锚点字段。
     *
     * <p>皮试行仍须申请+就诊+患者；主诉与 NKDA 只须就诊+患者。</p>
     */
    public void assertRequiredReferences() {
        // 主诉与 NKDA 不绑申请；皮试仍须申请主键
        if (this.encounterId == null || this.patientId == null) {
            throw new BusinessException(ErrorCodeConstants.ALLERGY_INTOLERANCE_INVALID_ARGUMENT, "就诊、患者标识不能为空");
        }
        if (this.isSkinTestRecord() && this.requestId == null) {
            throw new BusinessException(ErrorCodeConstants.ALLERGY_INTOLERANCE_INVALID_ARGUMENT, "皮试记录必须绑定申请");
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
