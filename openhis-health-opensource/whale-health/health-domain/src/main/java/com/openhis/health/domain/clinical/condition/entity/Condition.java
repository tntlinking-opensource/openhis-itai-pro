/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.condition.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 诊断记录聚合根。
 */
@Getter
@Setter
@Entity
@Table(name = "cln_condition")
public class Condition extends BaseAuditedEntity {
    @Column(name = "verification_status_enum")
    private String verificationStatusEnum;
    @Column(name = "clinical_status_enum")
    private String clinicalStatusEnum;
    @Column(name = "category_enum")
    private String categoryEnum;
    @Column(name = "severity_enum")
    private String severityEnum;
    @Column(name = "bus_no")
    private String busNo;
    @Column(name = "body_site_json")
    private String bodySiteJson;
    @Column(name = "body_structure_id")
    private Long bodyStructureId;
    @Column(name = "patient_id", nullable = false)
    private Long patientId;
    @Column(name = "description")
    private String description;
    @Column(name = "onset_datetime")
    private LocalDateTime onsetDatetime;
    @Column(name = "onset_age")
    private Integer onsetAge;
    @Column(name = "onset_text")
    private String onsetText;
    @Column(name = "abatement_datetime")
    private LocalDateTime abatementDatetime;
    @Column(name = "abatement_age")
    private Integer abatementAge;
    @Column(name = "abatement_string")
    private String abatementString;
    @Column(name = "recorded_datetime")
    private LocalDateTime recordedDatetime;
    @Column(name = "recorder_id")
    private Long recorderId;
    @Column(name = "stage_summary_text")
    private String stageSummaryText;
    @Column(name = "evidence")
    private String evidence;
    @Column(name = "yb_no")
    private String ybNo;
    @Column(name = "definition_id")
    private Long definitionId;
    @Column(name = "tcm_flag")
    private Boolean tcmFlag;
    /** 就诊标识（诊断事实归属字段）。 */
    @Column(name = "encounter_id")
    private Long encounterId;

    /**
     * 校验患者锚点。
     */
    public void assertPatientBound() {
        if (this.patientId == null) {
            throw new BusinessException(ErrorCodeConstants.CONDITION_INVALID_ARGUMENT, "患者标识不能为空");
        }
    }

    /**
     * 刷新记录元信息。
     */
    public void refreshRecordMeta(Long operatorId) {
        if (this.recordedDatetime == null) {
            this.recordedDatetime = LocalDateTime.now();
        }
        if (this.recorderId == null) {
            this.recorderId = operatorId;
        }
    }

    /**
     * 校验删除前置。
     */
    public void assertNoAdviceReferenceBeforeDelete(Integer adviceRefCount) {
        if (adviceRefCount != null && adviceRefCount > 0) {
            throw new BusinessException(ErrorCodeConstants.CONDITION_RULE_CONFLICT, "诊断已被医嘱引用，不能删除");
        }
    }

    /**
     * 执行逻辑删除。
     */
    public void markDeleted(Long operatorId) {
        this.setIsDeleted(Boolean.TRUE);
        this.setDeletedBy(operatorId);
        this.setDeletedAt(LocalDateTime.now());
    }
}
