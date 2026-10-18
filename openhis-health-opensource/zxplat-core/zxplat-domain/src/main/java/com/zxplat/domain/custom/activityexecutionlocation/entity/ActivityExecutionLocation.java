/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.activityexecutionlocation.entity;

import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.zxplat.framework.core.application.util.StringUtils;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 执行科室规则聚合根：锚定开立科室，描述某诊疗在哪个执行科室（及可选地点）、何时段生效。
 *
 * <p>与取药科室（ServiceLocationTemplate）并列：本表回答「诊疗在哪做」，取药表回答「药/材往哪发」。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cus_activity_execution_location")
public class ActivityExecutionLocation extends BaseAuditedEntity {

    /**
     * 开立科室标识（Organization）；配置页左树与医生站就诊科室同口径。
     */
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    /**
     * 诊疗定义标识；为空表示未绑单品（大类默认或业务类型层）。
     */
    @Column(name = "activity_definition_id")
    private Long activityDefinitionId;

    /**
     * 活动类别编码。
     */
    @Column(name = "activity_category_code", nullable = false)
    private String activityCategoryCode;

    /**
     * 业务类型码（术语 {@code activity_type_code}，与 AD {@code type_enum} 同轴）。
     * 可空：与 {@code activityDefinitionId} 均为空时表示大类默认；本字段有值且未绑单品时表示业务类型层。
     */
    @Column(name = "activity_type_code")
    private String activityTypeCode;

    /**
     * 执行科室标识（承担执行责任的 Organization）。
     */
    @Column(name = "execution_org_id", nullable = false)
    private Long executionOrgId;

    /**
     * 执行位置标识。
     */
    @Column(name = "execution_location_id")
    private Long executionLocationId;

    /**
     * 生效开始时间。
     */
    @Column(name = "start_time")
    private LocalTime startTime;

    /**
     * 生效结束时间。
     */
    @Column(name = "end_time")
    private LocalTime endTime;

    /**
     * 优先级（数值越小越优先，1 最优先；与取药科室一致）。
     */
    @Column(name = "priority", nullable = false)
    private Integer priority;

    /**
     * 展示顺序。
     */
    @Column(name = "display_order")
    private Integer displayOrder;

    /**
     * 备注。
     */
    @Column(name = "remark")
    private String remark;

    /**
     * 断言新增/更新必填字段（开立科室、执行科室、活动类别、优先级）。
     */
    public void assertRequiredFields() {
        if (this.organizationId == null) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_EXECUTION_LOCATION_INVALID_ARGUMENT, "开立科室标识不能为空");
        }
        if (StringUtils.isEmpty(this.activityCategoryCode)) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_EXECUTION_LOCATION_INVALID_ARGUMENT, "活动类别编码不能为空");
        }
        if (this.executionOrgId == null) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_EXECUTION_LOCATION_INVALID_ARGUMENT, "执行科室标识不能为空");
        }
        if (this.priority == null) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_EXECUTION_LOCATION_INVALID_ARGUMENT, "优先级不能为空");
        }
    }

    /**
     * 断言规则锚点有效：诊疗大类必填；单品与业务类型均可空，不必按层级填全。
     */
    public void assertAnchorPresent() {
        if (StringUtils.isEmpty(this.activityCategoryCode)) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_EXECUTION_LOCATION_INVALID_ARGUMENT, "活动类别编码不能为空");
        }
    }

    /**
     * 标记逻辑删除。
     *
     * @param operatorId 操作人标识
     */
    public void markDeleted(Long operatorId) {
        this.setIsDeleted(Boolean.TRUE);
        this.setDeletedBy(operatorId);
        this.setDeletedAt(LocalDateTime.now());
    }
}
