/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.mrms.caserecord.entity;

import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import com.openhis.health.domain.mrms.caserecord.converter.CaseRecordStatusConverter;
import com.openhis.health.domain.share.enums.CaseRecordStatus;
import com.openhis.health.domain.share.enums.CaseRecordTransitionAction;
import com.openhis.health.domain.share.enums.WorkflowRole;
import com.openhis.health.domain.share.constants.MrmsErrorCodes;
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

    /** 院内病案号 {@code medcasno}，租户内唯一，格式 {@code yyyy#####}。 */
    @Column(name = "medcasno", nullable = false, length = 32)
    private String medcasno;

    /** 工作流状态，持久化 {@link CaseRecordStatus#getCode()}（draft/submitted/archived 等）。 */
    @Convert(converter = CaseRecordStatusConverter.class)
    @Column(name = "status_code", nullable = false, length = 32)
    private CaseRecordStatus statusEnum;

    /** 本院该患者第几次住院（4401 patn_ipt_cnt）。 */
    @Column(name = "patn_ipt_cnt")
    private Integer patnIptCnt;

    /** 列表冗余：患者姓名（同步自首页 {@code psn_name}）。 */
    @Column(name = "psn_name", length = 100)
    private String psnName;

    /** 列表冗余：住院号 {@code ipt_no}（{@code adm_encounter.bus_no}）。 */
    @Column(name = "ipt_no", length = 64)
    private String iptNo;

    /** 列表冗余：性别字典码（同步自首页 {@code gend_code}）。 */
    @Column(name = "gend_code", length = 16)
    private String gendCode;

    /** 列表冗余：入院时间（同步自首页 {@code adm_time}）。 */
    @Column(name = "adm_time")
    private LocalDateTime admTime;

    /** 列表冗余：出院时间（同步自首页 {@code dscg_time}）。 */
    @Column(name = "dscg_time")
    private LocalDateTime dscgTime;

    /** 列表冗余：入院科别名称（同步自首页 {@code adm_caty_name}）。 */
    @Column(name = "adm_caty_name", length = 100)
    private String admCatyName;

    /** 列表冗余：出院科别名称（同步自首页 {@code dscg_caty_name}）。 */
    @Column(name = "dscg_caty_name", length = 100)
    private String dscgCatyName;

    /** 列表冗余：主任/副主任医师姓名（同步自首页 {@code chfdr_name}）。 */
    @Column(name = "chfdr_name", length = 50)
    private String chfdrName;

    /** 列表冗余：主治医师 {@code adm_practitioner.id}。 */
    @Column(name = "atddr_practitioner_id")
    private Long atddrPractitionerId;

    /** 列表冗余：主治医师姓名（同步自首页 {@code atddr_name}）。 */
    @Column(name = "atddr_name", length = 50)
    private String atddrName;

    /** 列表冗余：出院主诊断名称摘要（来自首页 discharge_main 首条）。 */
    @Column(name = "main_diag_name", length = 200)
    private String mainDiagName;

    /** 病案室退回原因（RETURN 迁移写入，医生端展示）。 */
    @Column(name = "return_reason", length = 500)
    private String returnReason;

    /** 病案室归档完成时间，与 {@link CaseRecordTransitionAction#ARCHIVE} 同事务写入。 */
    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    /** 归档操作人 {@code adm_practitioner.id}。 */
    @Column(name = "archived_by")
    private Long archivedBy;

    /** 病案质量等级（甲/乙/丙）。 */
    @Column(name = "quality_grade_code", length = 16)
    private String qualityGradeCode;

    /** 4401 上传状态冗余（pending/success/failed/skipped）。 */
    @Column(name = "chs_upload_status_code", length = 32)
    private String chsUploadStatusCode;

    /** 最近 4401 上传尝试时间（{@link com.openhis.health.domain.mrms.upload.service.UploadLogDomainService} 回写）。 */
    @Column(name = "chs_upload_at")
    private LocalDateTime chsUploadAt;

    /** 最近 4401 上传错误摘要。 */
    @Column(name = "chs_upload_error", length = 500)
    private String chsUploadError;

    /** 解档原因（UNARCHIVE 迁移写入，高权限审计留痕）。 */
    @Column(name = "unarchive_reason", length = 500)
    private String unarchiveReason;

    /**
     * 持久化状态码（DTO/查询兼容）。
     *
     * @return 状态 code
     */
    public String getStatusCode() {
        if (statusEnum == null) {
            return null;
        }
        return statusEnum.getCode();
    }

    /**
     * 初始化新建主档默认值（状态 draft）。
     *
     * @param tenantId 租户
     * @param patientId 患者
     * @param encounterId 就诊
     * @param medcasno 病案号
     * @param patnIptCnt 住院次
     * @param operatorId 操作人
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
        // 仅 draft/returned 允许医生编辑首页
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
        // 退回/解档须落库原因字段供列表与审计展示
        if (action == CaseRecordTransitionAction.RETURN) {
            this.returnReason = trimToNull(returnReason);
        }
        if (action == CaseRecordTransitionAction.UNARCHIVE) {
            this.unarchiveReason = trimToNull(returnReason);
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
        // 无首页时跳过冗余刷新
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

    /**
     * 病案流转 Gate：按动作与角色校验当前 statusEnum 是否允许迁移。
     *
     * @param action 流转动作
     * @param role   操作角色（医生/病案室等）
     * @throws BusinessException 状态或角色不允许
     */
    private void assertTransitionAllowed(CaseRecordTransitionAction action, WorkflowRole role) {
        if (statusEnum == null) {
            throw new BusinessException(MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "病案状态无效");
        }
        // 医生提交：draft/returned → submitted
        if (action == CaseRecordTransitionAction.SUBMIT) {
            if (role != WorkflowRole.DOCTOR) {
                throw new BusinessException(MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "仅医生可提交");
            }
            if (statusEnum != CaseRecordStatus.DRAFT && statusEnum != CaseRecordStatus.RETURNED) {
                throw new BusinessException(MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "当前状态不可提交");
            }
            return;
        }
        // 病案室接收：submitted → received
        if (action == CaseRecordTransitionAction.RECEIVE) {
            if (role != WorkflowRole.RECORD_ROOM) {
                throw new BusinessException(MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "仅病案室可接收");
            }
            if (statusEnum != CaseRecordStatus.SUBMITTED) {
                throw new BusinessException(MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "仅已提交病案可接收");
            }
            return;
        }
        // 病案室退回：submitted/received → returned
        if (action == CaseRecordTransitionAction.RETURN) {
            if (role != WorkflowRole.RECORD_ROOM) {
                throw new BusinessException(MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "仅病案室可退回");
            }
            if (statusEnum != CaseRecordStatus.SUBMITTED && statusEnum != CaseRecordStatus.RECEIVED) {
                throw new BusinessException(MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "当前状态不可退回");
            }
            return;
        }
        // 开始质控：received → qcing
        if (action == CaseRecordTransitionAction.START_QC) {
            if (role != WorkflowRole.RECORD_ROOM) {
                throw new BusinessException(MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "仅病案室可开始质控");
            }
            if (statusEnum != CaseRecordStatus.RECEIVED) {
                throw new BusinessException(MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "仅已接收病案可开始质控");
            }
            return;
        }
        // 归档：qcing → archived
        if (action == CaseRecordTransitionAction.ARCHIVE) {
            if (role != WorkflowRole.RECORD_ROOM) {
                throw new BusinessException(MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "仅病案室可归档");
            }
            if (statusEnum != CaseRecordStatus.QCING) {
                throw new BusinessException(MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "仅质控中病案可归档");
            }
            return;
        }
        // 解档：archived → received
        if (action == CaseRecordTransitionAction.UNARCHIVE) {
            if (role != WorkflowRole.RECORD_ROOM) {
                throw new BusinessException(MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "仅病案室可解档");
            }
            if (statusEnum != CaseRecordStatus.ARCHIVED) {
                throw new BusinessException(MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "仅已归档病案可解档");
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
        // 归档时可选写入病案质量等级（甲/乙/丙）
        if (trimToNull(qualityGradeCode) != null) {
            this.qualityGradeCode = trimToNull(qualityGradeCode);
        }
    }

    /**
     * 解档元数据（与 {@link CaseRecordTransitionAction#UNARCHIVE} 同事务调用）。
     *
     * @param reason 解档原因
     * @param operatorId 操作人
     */
    public void applyUnarchiveMetadata(String reason, Long operatorId) {
        this.archivedAt = null;
        this.archivedBy = null;
        this.unarchiveReason = trimToNull(reason);
        this.setUpdatedAt(LocalDateTime.now());
        this.setUpdatedBy(operatorId);
    }

    /**
     * 流转动作 → 目标病案状态。
     *
     * @param action 流转动作
     * @return 目标状态枚举
     */
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
        if (action == CaseRecordTransitionAction.UNARCHIVE) {
            return CaseRecordStatus.RECEIVED;
        }
        throw new BusinessException(MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "不支持的状态迁移");
    }
}
