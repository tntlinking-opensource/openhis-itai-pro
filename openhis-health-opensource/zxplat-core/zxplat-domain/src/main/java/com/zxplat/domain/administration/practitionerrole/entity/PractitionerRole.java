/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.practitionerrole.entity;

import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 岗位分配聚合根（{@code adm_pr_assign}）。
 *
 * <p>平台角色真源 {@code zxplat_roles.id}；科室/库房/病区范围由 {@code org_id}/{@code location_id} 表达，
 * 二者可双空——表示已挂角色但尚未配置管辖范围。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "adm_pr_assign")
public class PractitionerRole extends BaseAuditedEntity {

    @Column(name = "practitioner_id", nullable = false)
    private Long practitionerId;

    @Column(name = "org_id")
    private Long orgId;

    @Column(name = "location_id")
    private Long locationId;

    /** 平台角色主键（{@code zxplat_roles.id}）。 */
    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(name = "name")
    private String name;

    @Column(name = "specialty_code")
    private String specialtyCode;

    @Column(name = "type_code")
    private Integer typeCode;

    @Column(name = "active_flag", nullable = false)
    private Boolean activeFlag;

    public void initAsActive() {
        this.activeFlag = Boolean.TRUE;
    }

    /**
     * 断言可新建：医务人员与平台角色必填；科室/位置均可空（仅挂角色、尚未配置管辖范围时允许双空）。
     */
    public void assertAssignable() {
        if (this.practitionerId == null) {
            throw new BusinessException(ErrorCodeConstants.PRACTITIONER_ROLE_INVALID_ARGUMENT, "医务人员标识不能为空");
        }
        if (this.roleId == null) {
            throw new BusinessException(ErrorCodeConstants.PRACTITIONER_ROLE_INVALID_ARGUMENT, "平台角色不能为空");
        }
    }

    public void assertEditable() {
        if (Boolean.TRUE.equals(this.getIsDeleted())) {
            throw new BusinessException(ErrorCodeConstants.PRACTITIONER_ROLE_INVALID_STATUS, "已废弃岗位不可编辑");
        }
    }

    public void assertActivatable() {
        if (Boolean.TRUE.equals(this.getIsDeleted()) || Boolean.TRUE.equals(this.activeFlag)) {
            throw new BusinessException(ErrorCodeConstants.PRACTITIONER_ROLE_INVALID_STATUS, "当前状态不可启用");
        }
    }

    public void assertDeactivatable() {
        if (Boolean.TRUE.equals(this.getIsDeleted()) || !Boolean.TRUE.equals(this.activeFlag)) {
            throw new BusinessException(ErrorCodeConstants.PRACTITIONER_ROLE_INVALID_STATUS, "当前状态不可停用");
        }
    }

    public void assertRetirable() {
        if (Boolean.TRUE.equals(this.getIsDeleted())) {
            throw new BusinessException(ErrorCodeConstants.PRACTITIONER_ROLE_INVALID_STATUS, "岗位已废弃");
        }
    }

    public void assertServiceUpdatable() {
        if (!Boolean.TRUE.equals(this.activeFlag) || Boolean.TRUE.equals(this.getIsDeleted())) {
            throw new BusinessException(ErrorCodeConstants.PRACTITIONER_ROLE_INVALID_STATUS, "停用或废弃岗位不可更新服务");
        }
    }

    public void assertScheduleUpdatable() {
        if (!Boolean.TRUE.equals(this.activeFlag) || Boolean.TRUE.equals(this.getIsDeleted())) {
            throw new BusinessException(ErrorCodeConstants.PRACTITIONER_ROLE_INVALID_STATUS, "停用或废弃岗位不可更新排班");
        }
    }

    public void transitToActive() {
        this.activeFlag = Boolean.TRUE;
    }

    public void transitToInactive() {
        this.activeFlag = Boolean.FALSE;
    }

    public void transitToDeleted(Long operatorId) {
        this.activeFlag = Boolean.FALSE;
        this.setIsDeleted(Boolean.TRUE);
        this.setDeletedBy(operatorId);
        this.setDeletedAt(LocalDateTime.now());
    }

    /**
     * 全量替换场景复用历史软删行：恢复有效态并清空删除审计字段。
     *
     * <p>与 {@code zxplat_user_roles} 分配角色时的「删后重加」策略对齐，避免同业务键重复插入。</p>
     */
    public void reactivateFromSync() {
        this.setIsDeleted(Boolean.FALSE);
        this.setDeletedAt(null);
        this.setDeletedBy(null);
        this.activeFlag = Boolean.TRUE;
    }
}
