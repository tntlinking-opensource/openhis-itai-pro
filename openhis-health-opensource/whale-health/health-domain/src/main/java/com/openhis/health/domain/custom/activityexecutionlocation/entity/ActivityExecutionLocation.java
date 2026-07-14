/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.activityexecutionlocation.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.openhis.whale.core.application.util.StringUtils;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 执行科室规则聚合根。
 */
@Getter
@Setter
@Entity
@Table(name = "cus_activity_execution_location")
public class ActivityExecutionLocation extends BaseAuditedEntity {

    /**
     * 诊疗定义标识；为空表示该诊疗大类下的默认规则。
     */
    @Column(name = "activity_definition_id")
    private Long activityDefinitionId;

    /**
     * 活动类别编码。
     */
    @Column(name = "activity_category_code", nullable = false)
    private String activityCategoryCode;

    /**
     * 执行科室标识。
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
     * 断言新增/更新必填字段。
     */
    public void assertRequiredFields() {
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
     * 断言规则锚点有效：诊疗大类必填；诊疗项目可选，为空时表示该大类下的默认规则。
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
