/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.administration.devicedefinition;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 耗材目录导入事件。
 */
@Getter
@Setter
public class DeviceCatalogImportedEvent extends DomainEvent {
    /** 导入批次号。 */
    private String batchNo;
    /** 成功条数。 */
    private Integer successCount;
    /** 失败条数。 */
    private Integer failCount;

    /** 操作人主键（DeviceCatalogImportedEvent）。 */
    private Long operatorId;

    /**
     * 无参构造。
     */
    public DeviceCatalogImportedEvent() {
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
    public DeviceCatalogImportedEvent(String batchNo, Integer successCount, Integer failCount, Long operatorId) {
        super(null, AggregateTypeConstants.DEVICE_DEFINITION);
        this.batchNo = batchNo;
        this.successCount = successCount;
        this.failCount = failCount;
        this.operatorId = operatorId;
    }
}
