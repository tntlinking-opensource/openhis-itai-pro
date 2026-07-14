/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.servicelocationtemplate.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.openhis.whale.core.application.util.StringUtils;

import java.time.LocalTime;

/**
 * 取药科室模板聚合根。
 */
@Getter
@Setter
@Entity
@Table(name = "cus_service_location_template")
public class ServiceLocationTemplate extends BaseAuditedEntity {

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "item_table", nullable = false)
    private String itemTable;

    @Column(name = "distribution_category_code", nullable = false)
    private String distributionCategoryCode;

    @Column(name = "def_location_id", nullable = false)
    private Long defLocationId;

    @Column(name = "item_definition_id")
    private Long itemDefinitionId;

    @Column(name = "method_code")
    private String methodCode;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(name = "priority", nullable = false)
    private Integer priority;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(name = "remark")
    private String remark;

    /**
     * 软删当前规则（逻辑删除）。
     *
     * @param operatorId 操作人标识
     */
    public void markDeleted(Long operatorId) {
        this.setIsDeleted(Boolean.TRUE);
        this.setDeletedBy(operatorId);
        this.setDeletedAt(java.time.LocalDateTime.now());
    }

    /**
     * 断言行级必填字段与时段合法性。
     */
    public void assertRequiredFields() {
        if (this.organizationId == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_LOCATION_TEMPLATE_INVALID_ARGUMENT, "开立科室标识不能为空");
        }
        if (StringUtils.isEmpty(this.itemTable)) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_LOCATION_TEMPLATE_INVALID_ARGUMENT, "物品表名不能为空");
        }
        if (StringUtils.isEmpty(this.distributionCategoryCode)) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_LOCATION_TEMPLATE_INVALID_ARGUMENT, "发放类别编码不能为空");
        }
        if (this.defLocationId == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_LOCATION_TEMPLATE_INVALID_ARGUMENT, "默认发药位置不能为空");
        }
        if (this.priority == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_LOCATION_TEMPLATE_INVALID_ARGUMENT, "优先级不能为空");
        }
        if (this.startTime != null && this.endTime != null && !this.startTime.isBefore(this.endTime)) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_LOCATION_TEMPLATE_INVALID_ARGUMENT, "生效开始时间必须早于结束时间");
        }
    }

    /**
     * 判断是否在给定时刻生效（空时段表示全天有效）。
     *
     * @param atTime 当日时刻
     * @return 是否生效
     */
    public boolean isEffectiveAt(LocalTime atTime) {
        if (atTime == null) {
            atTime = LocalTime.now();
        }
        LocalTime start;
        if (this.startTime == null) {
            start = LocalTime.MIN;
        } else {
            start = this.startTime;
        }
        LocalTime end;
        if (this.endTime == null) {
            end = LocalTime.MAX;
        } else {
            end = this.endTime;
        }
        return !atTime.isBefore(start) && !atTime.isAfter(end);
    }
}
