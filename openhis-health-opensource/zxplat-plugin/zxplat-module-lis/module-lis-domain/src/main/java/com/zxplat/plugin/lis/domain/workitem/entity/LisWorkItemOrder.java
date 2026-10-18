/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.lis.domain.workitem.entity;

import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 检验工单并管明细（表 {@code lis_work_item_order}，P0 骨架）。
 */
@Getter
@Setter
@Entity
@Table(name = "lis_work_item_order")
public class LisWorkItemOrder extends BaseAuditedEntity {

    @Column(name = "work_item_id", nullable = false)
    private Long workItemId;

    @Column(name = "service_request_id", nullable = false)
    private Long serviceRequestId;

    @Column(name = "activity_id")
    private Long activityId;

    @Column(name = "link_status_enum", length = 32)
    private String linkStatusEnum;

    @Column(name = "sort_number")
    private Integer sortNumber;
}