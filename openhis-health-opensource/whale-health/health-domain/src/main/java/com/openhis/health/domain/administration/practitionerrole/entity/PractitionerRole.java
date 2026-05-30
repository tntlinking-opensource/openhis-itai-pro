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

package com.openhis.health.domain.administration.practitionerrole.entity;

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
 * 岗位角色聚合根。
 *
 * <p>对应 DM-ADM-006 §3.1 与 SDD §4.2。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "adm_pr_assign")
public class PractitionerRole extends BaseAuditedEntity {

    /** practitioner 标识。 */
    @Column(name = "practitioner_id", nullable = false)
    private Long practitionerId;
    /** org 标识。 */
    @Column(name = "org_id")
    private Long orgId;
    /** location 标识。 */
    @Column(name = "location_id")
    private Long locationId;
    /** role 代码。 */
    @Column(name = "role_code", nullable = false)
    private Integer roleCode;
    /** 名称。 */
    @Column(name = "name")
    private String name;
    /** specialty 代码。 */
    @Column(name = "specialty_code")
    private String specialtyCode;
    /** type 代码。 */
    @Column(name = "type_code")
    private Integer typeCode;
    /** active 标志位。 */
    @Column(name = "active_flag", nullable = false)
    private Boolean activeFlag;

    /**
     * 初始化领域状态：新建岗位默认启用（ACTIVE）。
     */
    public void initAsActive() {
        this.activeFlag = Boolean.TRUE;
    }

    /**
     * 断言可分配（assignRole）的前置条件满足。
     *
     * <p>覆盖 BR-PRL-007、BR-PRL-008 的聚合内可校验部分。</p>
     */
    public void assertAssignable() {
        if (this.practitionerId == null) {
            throw new BusinessException(ErrorCodeConstants.PRACTITIONER_ROLE_INVALID_ARGUMENT, "医务人员标识不能为空");
        }
        if (this.roleCode == null) {
            throw new BusinessException(ErrorCodeConstants.PRACTITIONER_ROLE_INVALID_ARGUMENT, "角色编码不能为空");
        }
        if (this.orgId == null && this.locationId == null) {
            throw new BusinessException(ErrorCodeConstants.PRACTITIONER_ROLE_RULE_CONFLICT, "科室与位置不可同时为空");
        }
    }

    /**
     * 断言可编辑（editRole）的前置条件满足。
     */
    public void assertEditable() {
        if (Boolean.TRUE.equals(this.getIsDeleted())) {
            throw new BusinessException(ErrorCodeConstants.PRACTITIONER_ROLE_INVALID_STATUS, "已废弃岗位不可编辑");
        }
    }

    /**
     * 断言可启用（activateRole）的前置条件满足。
     */
    public void assertActivatable() {
        if (Boolean.TRUE.equals(this.getIsDeleted()) || Boolean.TRUE.equals(this.activeFlag)) {
            throw new BusinessException(ErrorCodeConstants.PRACTITIONER_ROLE_INVALID_STATUS, "当前状态不可启用");
        }
    }

    /**
     * 断言可停用（deactivateRole）的前置条件满足。
     */
    public void assertDeactivatable() {
        if (Boolean.TRUE.equals(this.getIsDeleted()) || !Boolean.TRUE.equals(this.activeFlag)) {
            throw new BusinessException(ErrorCodeConstants.PRACTITIONER_ROLE_INVALID_STATUS, "当前状态不可停用");
        }
    }

    /**
     * 断言可废弃（retireRole）的前置条件满足。
     */
    public void assertRetirable() {
        if (Boolean.TRUE.equals(this.getIsDeleted())) {
            throw new BusinessException(ErrorCodeConstants.PRACTITIONER_ROLE_INVALID_STATUS, "岗位已废弃");
        }
    }

    /**
     * 断言允许更新服务项（updateServices）。
     */
    public void assertServiceUpdatable() {
        if (!Boolean.TRUE.equals(this.activeFlag) || Boolean.TRUE.equals(this.getIsDeleted())) {
            throw new BusinessException(ErrorCodeConstants.PRACTITIONER_ROLE_INVALID_STATUS, "停用或废弃岗位不可更新服务");
        }
    }

    /**
     * 断言允许更新排班项（updateSchedule）。
     */
    public void assertScheduleUpdatable() {
        if (!Boolean.TRUE.equals(this.activeFlag) || Boolean.TRUE.equals(this.getIsDeleted())) {
            throw new BusinessException(ErrorCodeConstants.PRACTITIONER_ROLE_INVALID_STATUS, "停用或废弃岗位不可更新排班");
        }
    }

    /**
      * 处理transitToActive逻辑。
     */
    public void transitToActive() {
        this.activeFlag = Boolean.TRUE;
    }

    /**
      * 处理transitToInactive逻辑。
     */
    public void transitToInactive() {
        this.activeFlag = Boolean.FALSE;
    }

    /**
     * 软删岗位关联并记录操作人。
     *
     * @param operatorId 操作人主键
     */
    public void transitToDeleted(Long operatorId) {
        this.activeFlag = Boolean.FALSE;
        this.setIsDeleted(Boolean.TRUE);
        this.setDeletedBy(operatorId);
        this.setDeletedAt(LocalDateTime.now());
    }
}
