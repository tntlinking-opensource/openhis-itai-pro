/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.domain.workitem.entity;

import com.zxplat.domain.share.enums.PacsOrderLinkStatus;
import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 检查工单医嘱关联子实体（{@code pacs_work_item_order}）。
 */
@Getter
@Setter
@Entity
@Table(name = "pacs_work_item_order")
public class PacsWorkItemOrder extends BaseAuditedEntity {

    /** 工单主键。 */
    @Column(name = "work_item_id", nullable = false)
    private Long workItemId;

    /** 关联 ServiceRequest 主键。 */
    @Column(name = "service_request_id", nullable = false)
    private Long serviceRequestId;

    /** 冗余 ActivityDefinition 主键。 */
    @Column(name = "activity_id", nullable = false)
    private Long activityId;

    /** 关联态枚举 code。 */
    @Column(name = "link_status_enum", nullable = false)
    private String linkStatusEnum;

    /** 合并组号。 */
    @Column(name = "group_code")
    private String groupCode;

    /** 组内排序。 */
    @Column(name = "sort_number")
    private Integer sortNumber;

    /**
     * 初始化为有效关联。
     */
    public void initActive() {
        this.linkStatusEnum = PacsOrderLinkStatus.ACTIVE.getCode();
    }

    /**
     * 作废本条关联。
     */
    public void markCancelled() {
        this.linkStatusEnum = PacsOrderLinkStatus.CANCELLED.getCode();
    }

    /**
     * 是否仍为有效关联。
     *
     * @return active 时 true
     */
    public boolean isActiveLink() {
        return PacsOrderLinkStatus.ACTIVE.getCode().equals(this.linkStatusEnum);
    }
}
