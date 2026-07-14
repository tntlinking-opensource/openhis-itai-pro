/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.medication.medicationdefinition;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 药品目录导入事件。
 */
@Getter
@Setter
public class MedicationCatalogImportedEvent extends DomainEvent {
    /** 导入批次号。 */
    private String batchNo;
    /** 成功条数。 */
    private Integer successCount;
    /** 失败条数。 */
    private Integer failCount;

    /** 操作人主键（MedicationCatalogImportedEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public MedicationCatalogImportedEvent() {
        super();
    }

    /**
     * 处理String batchNo, Integer successCount, Integer failCount逻辑。
     * @return 返回结果
     */
    public MedicationCatalogImportedEvent(String batchNo, Integer successCount, Integer failCount, Long operatorId) {
        super(null, AggregateTypeConstants.MEDICATION_DEFINITION);
        this.batchNo = batchNo;
        this.successCount = successCount;
        this.failCount = failCount;
        this.operatorId = operatorId;
    }
}
