package com.openhis.whale.mrms.domain.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 病案主档聚合根。表 mrms_case_record。
 */
@Getter
@Setter
@Entity
@Table(name = "mrms_case_record")
public class CaseRecord extends BaseAuditedEntity {

    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "medcasno", nullable = false, length = 40)
    private String medcasno;

    @Column(name = "status_code", nullable = false, length = 32)
    private String statusCode;

    @Column(name = "patn_ipt_cnt")
    private Integer patnIptCnt;

    @Column(name = "inpatient_no", length = 32)
    private String inpatientNo;

    @Column(name = "patient_name", length = 100)
    private String patientName;

    @Column(name = "gender_code", length = 16)
    private String genderCode;

    @Column(name = "admission_at")
    private LocalDateTime admissionAt;

    @Column(name = "discharge_at")
    private LocalDateTime dischargeAt;

    @Column(name = "dept_code", length = 64)
    private String deptCode;

    @Column(name = "dept_name", length = 200)
    private String deptName;

    @Column(name = "attending_practitioner_id")
    private Long attendingPractitionerId;

    @Column(name = "attending_practitioner_name", length = 100)
    private String attendingPractitionerName;

    @Column(name = "primary_diagnosis_text", length = 500)
    private String primaryDiagnosisText;

    @Column(name = "return_reason", length = 500)
    private String returnReason;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    @Column(name = "archived_by")
    private Long archivedBy;

    @Column(name = "quality_grade_code", length = 16)
    private String qualityGradeCode;
}
