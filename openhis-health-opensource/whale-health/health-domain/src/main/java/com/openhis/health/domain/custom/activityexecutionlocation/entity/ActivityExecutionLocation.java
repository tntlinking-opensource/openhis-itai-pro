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

package com.openhis.health.domain.custom.activityexecutionlocation.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

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
     * 诊疗定义标识。
     */
    @Column(name = "activity_definition_id")
    private Long activityDefinitionId;

    /**
     * 收费项定义标识。
     */
    @Column(name = "charge_item_definition_id")
    private Long chargeItemDefinitionId;

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
     * 优先级（数值越大越优先）。
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
        if (StringUtils.isBlank(this.activityCategoryCode)) {
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
     * 断言锚点至少存在一个。
     */
    public void assertAnchorPresent() {
        if (this.activityDefinitionId == null && this.chargeItemDefinitionId == null) {
            throw new BusinessException(ErrorCodeConstants.ACTIVITY_EXECUTION_LOCATION_INVALID_ARGUMENT, "诊疗定义与收费项定义不能同时为空");
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
