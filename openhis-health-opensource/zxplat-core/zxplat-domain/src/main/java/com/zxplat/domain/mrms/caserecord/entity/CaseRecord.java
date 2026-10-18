/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.mrms.caserecord.entity;

import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.domain.BaseAuditedEntity;
import com.zxplat.domain.mrms.caserecord.converter.CaseRecordStatusConverter;
import com.zxplat.domain.share.enums.CaseRecordStatus;
import com.zxplat.domain.share.enums.CaseRecordTransitionAction;
import com.zxplat.domain.share.enums.WorkflowRole;
import com.zxplat.domain.share.constants.MrmsErrorCodes;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

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

    /**
     * 院内病案号 {@code medcasno}，租户内唯一（非空时）；格式 {@code CR}{@code yy}{@code MM}{@code ####}。
     *
     * <p>主档可提前建档时为空；医生首次保存首页时由发号器写入。</p>
     */
    @Column(name = "medcasno", length = 32)
    private String medcasno;

    /** 工作流状态，持久化 {@link CaseRecordStatus#getCode()}（仅 DRAFT/PENDING_ARCHIVE/ARCHIVED）。 */
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

    /** 列表冗余：证件号码（同步自首页 {@code certno}）。 */
    @Column(name = "certno", length = 32)
    private String certno;

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

    /** 最近 4401 上传尝试时间（{@link com.zxplat.domain.mrms.upload.service.UploadLogDomainService} 回写）。 */
    @Column(name = "chs_upload_at")
    private LocalDateTime chsUploadAt;

    /** 最近 4401 上传错误摘要。 */
    @Column(name = "chs_upload_error", length = 500)
    private String chsUploadError;

    /**
     * 结算清单上传状态（对齐老 uploadSettFlag）：
     * {@code 0}未传 / {@code 1}已传未提交 / {@code 2}已提交。
     */
    @Column(name = "settle_upload_status_code", length = 8)
    private String settleUploadStatusCode;

    /** 4101A 平台返回清单流水号 {@code setl_list_id}。 */
    @Column(name = "setl_list_id", length = 64)
    private String setlListId;

    /** 最近结算清单上传/提交时间。 */
    @Column(name = "settle_upload_at")
    private LocalDateTime settleUploadAt;

    /** 最近结算清单上传错误摘要。 */
    @Column(name = "settle_upload_error", length = 500)
    private String settleUploadError;

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
     * 初始化新建主档默认值（状态 draft；病案号可空，待首次保存首页发号）。
     *
     * @param tenantId 租户
     * @param patientId 患者
     * @param encounterId 就诊
     * @param medcasno 病案号（bootstrap 传 null；已有号时传入）
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
        this.medcasno = trimToNull(medcasno);
        this.statusEnum = CaseRecordStatus.DRAFT;
        this.patnIptCnt = patnIptCnt;
        this.setCreatedAt(LocalDateTime.now());
        this.setCreatedBy(operatorId);
        this.setIsDeleted(false);
    }

    /**
     * 首次写入病案号（已有非空号则幂等跳过）。
     *
     * @param medcasno 发号器生成的病案号
     * @param operatorId 操作人
     */
    public void assignMedcasnoIfAbsent(String medcasno, Long operatorId) {
        // 已发号则不再覆盖，保证号终身稳定
        if (trimToNull(this.medcasno) != null) {
            return;
        }
        String no = trimToNull(medcasno);
        if (no == null) {
            throw new BusinessException(MrmsErrorCodes.MEDCASNO_GENERATE_FAILED, "病案号不能为空");
        }
        this.medcasno = no;
        this.setUpdatedAt(LocalDateTime.now());
        this.setUpdatedBy(operatorId);
    }

    /**
     * 断言医生可编辑首页（草稿、待归档审批、已归档）。
     *
     * <p>已归档仍可直接改并再次提交，不再整份锁死。</p>
     */
    public void assertEditableByDoctor() {
        if (statusEnum != CaseRecordStatus.DRAFT
                && statusEnum != CaseRecordStatus.PENDING_ARCHIVE
                && statusEnum != CaseRecordStatus.ARCHIVED) {
            throw new BusinessException(MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "当前状态不允许医生编辑首页");
        }
    }

    /**
     * 提交归档审批。
     *
     * <p>草稿或已归档进入待审；已在待审则保持待审。</p>
     *
     * @param operatorId 提交人
     */
    public void submitArchive(Long operatorId) {
        if (statusEnum != CaseRecordStatus.DRAFT
                && statusEnum != CaseRecordStatus.PENDING_ARCHIVE
                && statusEnum != CaseRecordStatus.ARCHIVED) {
            throw new BusinessException(MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "当前状态不可提交归档");
        }
        this.statusEnum = CaseRecordStatus.PENDING_ARCHIVE;
        this.returnReason = null;
        this.setUpdatedAt(LocalDateTime.now());
        this.setUpdatedBy(operatorId);
    }

    /**
     * 归档审批同意：PENDING_ARCHIVE → ARCHIVED。
     *
     * @param operatorId 审批人
     * @param qualityGradeCode 可选质量等级
     */
    public void approveArchive(Long operatorId, String qualityGradeCode) {
        if (statusEnum != CaseRecordStatus.PENDING_ARCHIVE) {
            throw new BusinessException(MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "仅待归档审批可同意");
        }
        this.statusEnum = CaseRecordStatus.ARCHIVED;
        applyArchiveMetadata(operatorId, qualityGradeCode);
        this.setUpdatedAt(LocalDateTime.now());
        this.setUpdatedBy(operatorId);
    }

    /**
     * 归档审批驳回：PENDING_ARCHIVE → DRAFT。
     *
     * @param rejectReason 驳回原因
     * @param operatorId 审批人
     */
    public void rejectArchive(String rejectReason, Long operatorId) {
        if (statusEnum != CaseRecordStatus.PENDING_ARCHIVE) {
            throw new BusinessException(MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "仅待归档审批可驳回");
        }
        String reason = trimToNull(rejectReason);
        if (reason == null) {
            throw new BusinessException(MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "驳回原因不能为空");
        }
        this.statusEnum = CaseRecordStatus.DRAFT;
        this.returnReason = reason;
        this.setUpdatedAt(LocalDateTime.now());
        this.setUpdatedBy(operatorId);
    }

    /**
     * 执行状态迁移并更新审计字段。
     *
     * <p>主档 {@code status_code} 仅承载归档三态。质控/收案/退回/解档已拆出：
     * {@link CaseRecordTransitionAction#RECEIVE}/{@link CaseRecordTransitionAction#RETURN}/
     * {@link CaseRecordTransitionAction#START_QC}/{@link CaseRecordTransitionAction#UNARCHIVE} 一律拒绝。
     * {@link CaseRecordTransitionAction#SUBMIT}→{@link CaseRecordStatus#PENDING_ARCHIVE}；
     * {@link CaseRecordTransitionAction#ARCHIVE} 仅允许自 {@link CaseRecordStatus#PENDING_ARCHIVE}。</p>
     *
     * @param action 迁移动作
     * @param role 操作角色
     * @param returnReason 退回原因（旧退回动作已禁用，忽略）
     * @param operatorId 操作人
     */
    public void applyTransition(
            CaseRecordTransitionAction action,
            WorkflowRole role,
            String returnReason,
            Long operatorId) {
        assertTransitionAllowed(action, role);
        this.statusEnum = resolveTargetStatus(action);
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
        this.certno = homepage.getCertno();
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
     * 病案流转 Gate：主档仅归档三态；质控/收案/退回/解档拒绝。
     *
     * @param action 流转动作
     * @param role   操作角色（医生/病案室等）
     * @throws BusinessException 状态或角色不允许
     */
    private void assertTransitionAllowed(CaseRecordTransitionAction action, WorkflowRole role) {
        if (statusEnum == null) {
            throw new BusinessException(MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "病案状态无效");
        }
        // 质控与旧收案流已从主档拆出，禁止再经本入口改 status_code
        if (action == CaseRecordTransitionAction.RECEIVE
                || action == CaseRecordTransitionAction.RETURN
                || action == CaseRecordTransitionAction.START_QC
                || action == CaseRecordTransitionAction.UNARCHIVE) {
            throw new BusinessException(
                    MrmsErrorCodes.CASE_RECORD_INVALID_STATUS,
                    "质控/收案/退回/解档已从主档归档状态拆出；归档请用提交归档与归档审批");
        }
        // 医生提交归档：DRAFT → PENDING_ARCHIVE
        if (action == CaseRecordTransitionAction.SUBMIT) {
            if (role != WorkflowRole.DOCTOR) {
                throw new BusinessException(MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "仅医生可提交归档");
            }
            if (statusEnum != CaseRecordStatus.DRAFT) {
                throw new BusinessException(MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "仅草稿可提交归档");
            }
            return;
        }
        // 病案室同意归档：PENDING_ARCHIVE → ARCHIVED（兼容旧 ARCHIVE 动作名）
        if (action == CaseRecordTransitionAction.ARCHIVE) {
            if (role != WorkflowRole.RECORD_ROOM) {
                throw new BusinessException(MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "仅病案室可归档");
            }
            if (statusEnum != CaseRecordStatus.PENDING_ARCHIVE) {
                throw new BusinessException(MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "仅待归档审批可归档");
            }
            return;
        }
        throw new BusinessException(MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "不支持的状态迁移");
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
     * 解档元数据（解档能力已取消；保留方法供存量调用方编译，调用方须先拒绝）。
     *
     * @param reason 解档原因
     * @param operatorId 操作人
     */
    public void applyUnarchiveMetadata(String reason, Long operatorId) {
        throw new BusinessException(MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "已禁止解档");
    }

    /**
     * 流转动作 → 目标病案归档状态。
     *
     * @param action 流转动作
     * @return 目标状态枚举
     */
    private CaseRecordStatus resolveTargetStatus(CaseRecordTransitionAction action) {
        if (action == CaseRecordTransitionAction.SUBMIT) {
            return CaseRecordStatus.PENDING_ARCHIVE;
        }
        if (action == CaseRecordTransitionAction.ARCHIVE) {
            return CaseRecordStatus.ARCHIVED;
        }
        throw new BusinessException(MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "不支持的状态迁移");
    }
}
