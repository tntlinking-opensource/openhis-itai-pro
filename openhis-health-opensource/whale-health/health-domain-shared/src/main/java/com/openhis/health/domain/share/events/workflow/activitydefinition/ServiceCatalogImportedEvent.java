/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.workflow.activitydefinition;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 诊疗目录导入事件。
 */
@Getter
@Setter
public class ServiceCatalogImportedEvent extends DomainEvent {
    /** 导入批次号。 */
    private String batchNo;
    /** 成功条数。 */
    private Integer successCount;
    /** 失败条数。 */
    private Integer failCount;

    /** 操作人主键。 */
    private Long operatorId;

    /**
     * 无参构造。
     */
    public ServiceCatalogImportedEvent() {
        super();
    }

    /**
     * 构造批次导入事件。
     *
     * @param batchNo      批次号
     * @param successCount 成功条数
     * @param failCount    失败条数
     * @param operatorId   操作人主键
     */
    public ServiceCatalogImportedEvent(String batchNo, Integer successCount, Integer failCount, Long operatorId) {
        super(null, AggregateTypeConstants.ACTIVITY_DEFINITION);
        this.batchNo = batchNo;
        this.successCount = successCount;
        this.failCount = failCount;
        this.operatorId = operatorId;
    }
}
