/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.domain.entity;

import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import com.openhis.whale.mrms.domain.converter.CaseRecordStatusConverter;
import com.openhis.whale.mrms.domain.enums.CaseRecordStatus;
import com.openhis.whale.mrms.domain.enums.CaseRecordTransitionAction;
import com.openhis.whale.mrms.domain.enums.WorkflowRole;
import com.openhis.whale.mrms.domain.support.MrmsErrorCodes;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 病案主档聚合根，一次住院对应一条有效记录。
 */
@Getter
@Setter
@Entity
@Table(name = "mrms_case_record")
public class CaseRecord extends BaseAuditedEntity {

    /** 患者主键 {@code adm_patient.id}。 */
    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    /** 就诊主键 {@code adm_encounter.id}，租户内唯一绑定。 */
    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    /** 院内病案号，租户内唯一。 */
    @Column(name = "medcasno", nullable = false, length = 32)
    private String medcasno;

    /** 病案状态。 */
    @Convert(converter = CaseRecordStatusConverter.class)
    @Column(name = "status_code", nullable = false, length = 32)
    private CaseRecordStatus statusEnum;

    /** 本院该患者第几次住院（4401 patn_ipt_cnt）。 */
    @Column(name = "patn_ipt_cnt")
    private Integer patnIptCnt;

    /** 列表冗余：患者姓名。 */
    @Column(name = "psn_name", length = 100)
    private String psnName;

    /** 列表冗余：住院号。 */
    @Column(name = "ipt_no", length = 64)
    private String iptNo;

    /** 列表冗余：性别字典码。 */
    @Column(name = "gend_code", length = 16)
    private String gendCode;

    /** 列表冗余：入院时间。 */
    @Column(name = "adm_time")
    private LocalDateTime admTime;

    /** 列表冗余：出院时间。 */
    @Column(name = "dscg_time")
    private LocalDateTime dscgTime;

    /** 列表冗余：入院科别名称。 */
    @Column(name = "adm_caty_name", length = 100)
    private String admCatyName;

    /** 列表冗余：出院科别名称。 */
    @Column(name = "dscg_caty_name", length = 100)
    private String dscgCatyName;

    /** 列表冗余：主任/副主任医师姓名。 */
    @Column(name = "chfdr_name", length = 50)
    private String chfdrName;

    /** 列表冗余：主治医师 {@code adm_practitioner.id}。 */
    @Column(name = "atddr_practitioner_id")
    private Long atddrPractitionerId;

    /** 列表冗余：主治医师姓名。 */
    @Column(name = "atddr_name", length = 50)
    private String atddrName;

    /** 列表冗余：主诊断名称摘要。 */
    @Column(name = "main_diag_name", length = 200)
    private String mainDiagName;

    /** 退回原因（病案室填写）。 */
    @Column(name = "return_reason", length = 500)
    private String returnReason;

    /** 归档时间。 */
    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    /** 归档操作人。 */
    @Column(name = "archived_by")
    private Long archivedBy;

    /** 病案质量等级（甲/乙/丙）。 */
    @Column(name = "quality_grade_code", length = 16)
    private String qualityGradeCode;

    /**
     * 持久化状态码（DTO/查询兼容）。
     *
     * @return 状态 code
     */
    public String getStatusCode() {
        return statusEnum == null ? null : statusEnum.getCode();
    }

    /**
     * 初始化新建主档默认值。
     */
    public void initNewRecord(
            Long tenantId,
            Long patientId,
            Long encounterId,
            String medcasno,
            int patnIptCnt,
            Long operatorId) {
        this.setTenantId(tenantId);
        this.patientId = patientId;
        this.encounterId = encounterId;
        this.medcasno = medcasno;
        this.statusEnum = CaseRecordStatus.DRAFT;
        this.patnIptCnt = patnIptCnt;
        this.setCreatedAt(LocalDateTime.now());
        this.setCreatedBy(operatorId);
        this.setIsDeleted(false);
    }

    /**
     * 断言医生可编辑首页（draft/returned）。
     */
    public void assertEditableByDoctor() {
        if (statusEnum != CaseRecordStatus.DRAFT && statusEnum != CaseRecordStatus.RETURNED) {
            throw new BusinessException(MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "当前状态不允许医生编辑首页");
        }
    }

    /**
     * 执行状态迁移并更新审计字段。
     *
     * @param action 迁移动作
     * @param role 操作角色
     * @param returnReason 退回原因（退回时必填）
     * @param operatorId 操作人
     */
    public void applyTransition(
            CaseRecordTransitionAction action,
            WorkflowRole role,
            String returnReason,
            Long operatorId) {
        assertTransitionAllowed(action, role);
        this.statusEnum = resolveTargetStatus(action);
        if (action == CaseRecordTransitionAction.RETURN) {
            this.returnReason = trimToNull(returnReason);
        }
        this.setUpdatedAt(LocalDateTime.now());
        this.setUpdatedBy(operatorId);
    }

    /**
     * 从首页刷新列表冗余摘要字段。
     *
     * @param homepage 首页
     * @param mainDiagName 主诊断名称摘要
     */
    public void refreshSummaryFrom(CaseRecordHomepage homepage, String mainDiagName) {
        if (homepage == null) {
            return;
        }
        this.psnName = homepage.getPsnName();
        this.iptNo = homepage.getIptNo();
        this.gendCode = homepage.getGendCode();
        this.admTime = homepage.getAdmTime();
        this.dscgTime = homepage.getDscgTime();
        this.admCatyName = homepage.getAdmCatyName();
        this.dscgCatyName = homepage.getDscgCatyName();
        this.chfdrName = homepage.getChfdrName();
        this.atddrPractitionerId = homepage.getAtddrPractitionerId();
        this.atddrName = homepage.getAtddrName();
        this.mainDiagName = mainDiagName;
        this.setUpdatedAt(LocalDateTime.now());
    }

    private void assertTransitionAllowed(CaseRecordTransitionAction action, WorkflowRole role) {
        if (statusEnum == null) {
            throw new BusinessException(MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "病案状态无效");
        }
        if (action == CaseRecordTransitionAction.SUBMIT) {
            if (role != WorkflowRole.DOCTOR) {
                throw new BusinessException(MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "仅医生可提交");
            }
            if (statusEnum != CaseRecordStatus.DRAFT && statusEnum != CaseRecordStatus.RETURNED) {
                throw new BusinessException(MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "当前状态不可提交");
            }
            return;
        }
        if (action == CaseRecordTransitionAction.RECEIVE) {
            if (role != WorkflowRole.RECORD_ROOM) {
                throw new BusinessException(MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "仅病案室可接收");
            }
            if (statusEnum != CaseRecordStatus.SUBMITTED) {
                throw new BusinessException(MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "仅已提交病案可接收");
            }
            return;
        }
        if (action == CaseRecordTransitionAction.RETURN) {
            if (role != WorkflowRole.RECORD_ROOM) {
                throw new BusinessException(MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "仅病案室可退回");
            }
            if (statusEnum != CaseRecordStatus.SUBMITTED && statusEnum != CaseRecordStatus.RECEIVED) {
                throw new BusinessException(MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "当前状态不可退回");
            }
            return;
        }
        if (action == CaseRecordTransitionAction.START_QC) {
            if (role != WorkflowRole.RECORD_ROOM) {
                throw new BusinessException(MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "仅病案室可开始质控");
            }
            if (statusEnum != CaseRecordStatus.RECEIVED) {
                throw new BusinessException(MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "仅已接收病案可开始质控");
            }
            return;
        }
        if (action == CaseRecordTransitionAction.ARCHIVE) {
            if (role != WorkflowRole.RECORD_ROOM) {
                throw new BusinessException(MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "仅病案室可归档");
            }
            if (statusEnum != CaseRecordStatus.QCING) {
                throw new BusinessException(MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "仅质控中病案可归档");
            }
        }
    }

    /**
     * 写入归档元数据（与 {@link CaseRecordTransitionAction#ARCHIVE} 同事务调用）。
     *
     * @param operatorId 归档人
     * @param qualityGradeCode 病案质量等级（可选）
     */
    public void applyArchiveMetadata(Long operatorId, String qualityGradeCode) {
        this.archivedAt = LocalDateTime.now();
        this.archivedBy = operatorId;
        if (trimToNull(qualityGradeCode) != null) {
            this.qualityGradeCode = trimToNull(qualityGradeCode);
        }
    }

    private CaseRecordStatus resolveTargetStatus(CaseRecordTransitionAction action) {
        if (action == CaseRecordTransitionAction.SUBMIT) {
            return CaseRecordStatus.SUBMITTED;
        }
        if (action == CaseRecordTransitionAction.RECEIVE) {
            return CaseRecordStatus.RECEIVED;
        }
        if (action == CaseRecordTransitionAction.RETURN) {
            return CaseRecordStatus.RETURNED;
        }
        if (action == CaseRecordTransitionAction.START_QC) {
            return CaseRecordStatus.QCING;
        }
        if (action == CaseRecordTransitionAction.ARCHIVE) {
            return CaseRecordStatus.ARCHIVED;
        }
        throw new BusinessException(MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "不支持的状态迁移");
    }
}
