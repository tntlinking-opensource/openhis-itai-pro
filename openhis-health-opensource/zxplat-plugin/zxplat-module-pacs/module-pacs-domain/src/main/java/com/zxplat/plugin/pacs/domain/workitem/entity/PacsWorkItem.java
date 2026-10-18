/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.domain.workitem.entity;

import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.enums.PacsWorkItemStatus;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 检查工单聚合根（{@code pacs_work_item}）。
 *
 * <p>不冗余 PHI；影像实体不在本表，仅维护 {@code hasMedia} 标记。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "pacs_work_item")
public class PacsWorkItem extends BaseAuditedEntity {

    /** 检查号；登记确认后非空。 */
    @Column(name = "accession_number")
    private String accessionNumber;

    /** 患者主键。 */
    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    /** 就诊主键。 */
    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    /** 登记/执行科室。 */
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    /** 检查执行态枚举 code。 */
    @Column(name = "status_enum", nullable = false)
    private String statusEnum;

    /** 模态码。 */
    @Column(name = "modality_code")
    private String modalityCode;

    /** 检查部位码。 */
    @Column(name = "body_site_code")
    private String bodySiteCode;

    /** 执行设备主键。 */
    @Column(name = "device_id")
    private Long deviceId;

    /** 执行人主键。 */
    @Column(name = "performer_practitioner_id")
    private Long performerPractitionerId;

    /** 计划检查时间。 */
    @Column(name = "scheduled_time")
    private LocalDateTime scheduledTime;

    /** 实际完成时间。 */
    @Column(name = "performed_time")
    private LocalDateTime performedTime;

    /** 是否已有影像/关键图。 */
    @Column(name = "has_media", nullable = false)
    private Boolean hasMedia = Boolean.FALSE;

    /**
     * 初始化为已接单态。
     */
    public void initRegistered() {
        this.statusEnum = PacsWorkItemStatus.REGISTERED.getCode();
        if (this.hasMedia == null) {
            this.hasMedia = Boolean.FALSE;
        }
    }

    /**
     * 校验可编辑登记（BR-PWI-06）。
     */
    public void assertCanEditRegistration() {
        if (PacsWorkItemStatus.REGISTERED.getCode().equals(this.statusEnum)) {
            return;
        }
        if (PacsWorkItemStatus.SCHEDULED.getCode().equals(this.statusEnum)) {
            return;
        }
        throw new BusinessException(ErrorCodeConstants.PACS_WORK_ITEM_INVALID_STATUS, "当前状态不可编辑登记信息");
    }

    /**
     * 写入检查号并进入待摄（BR-PWI-05）。
     *
     * @param accession 非空检查号
     */
    public void markScheduledWithAccession(String accession) {
        if (StringUtils.isEmpty(accession)) {
            throw new BusinessException(ErrorCodeConstants.PACS_WORK_ITEM_INVALID_ARGUMENT, "检查号不能为空");
        }
        this.assertCanEditRegistration();
        this.accessionNumber = accession;
        this.statusEnum = PacsWorkItemStatus.SCHEDULED.getCode();
    }

    /**
     * 进入执行中。
     */
    public void markInProgress() {
        this.assertHasAccession();
        if (PacsWorkItemStatus.COMPLETED.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.PACS_WORK_ITEM_INVALID_STATUS, "已检不可再开始执行");
        }
        if (PacsWorkItemStatus.ENTERED_IN_ERROR.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.PACS_WORK_ITEM_INVALID_STATUS, "已作废不可开始执行");
        }
        this.statusEnum = PacsWorkItemStatus.IN_PROGRESS.getCode();
    }

    /**
     * 校验可完成检查（BR-PWI-07）。
     */
    public void assertCanComplete() {
        this.assertHasAccession();
        if (PacsWorkItemStatus.REGISTERED.getCode().equals(this.statusEnum)) {
            return;
        }
        if (PacsWorkItemStatus.SCHEDULED.getCode().equals(this.statusEnum)) {
            return;
        }
        if (PacsWorkItemStatus.IN_PROGRESS.getCode().equals(this.statusEnum)) {
            return;
        }
        throw new BusinessException(ErrorCodeConstants.PACS_WORK_ITEM_INVALID_STATUS, "当前状态不可确认已检");
    }

    /**
     * 标记已检。
     *
     * @param performed 完成时间
     */
    public void markCompleted(LocalDateTime performed) {
        this.assertCanComplete();
        this.statusEnum = PacsWorkItemStatus.COMPLETED.getCode();
        this.performedTime = performed;
    }

    /**
     * 校验可整单作废（BR-PWI-08）。
     */
    public void assertCanCancel() {
        if (PacsWorkItemStatus.REGISTERED.getCode().equals(this.statusEnum)) {
            return;
        }
        if (PacsWorkItemStatus.SCHEDULED.getCode().equals(this.statusEnum)) {
            return;
        }
        throw new BusinessException(ErrorCodeConstants.PACS_WORK_ITEM_INVALID_STATUS, "已进入执行或完成，不可作废整单");
    }

    /**
     * 标记整单作废。
     */
    public void markCancelled() {
        this.assertCanCancel();
        this.statusEnum = PacsWorkItemStatus.ENTERED_IN_ERROR.getCode();
    }

    /**
     * 回写媒体标记（BR-PWI-10）。
     *
     * @param flag 是否已有媒体
     */
    public void markHasMediaFlag(Boolean flag) {
        if (flag == null) {
            throw new BusinessException(ErrorCodeConstants.PACS_WORK_ITEM_INVALID_ARGUMENT, "hasMedia 不能为空");
        }
        this.hasMedia = flag;
    }

    /**
     * 校验检查号已存在。
     */
    public void assertHasAccession() {
        if (StringUtils.isEmpty(this.accessionNumber)) {
            throw new BusinessException(ErrorCodeConstants.PACS_WORK_ITEM_RULE_CONFLICT, "缺少检查号，请先完成登记确认");
        }
    }
}
