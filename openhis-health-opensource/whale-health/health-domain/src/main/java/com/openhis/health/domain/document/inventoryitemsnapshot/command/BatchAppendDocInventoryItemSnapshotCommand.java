/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.document.inventoryitemsnapshot.command;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 批量追加库存明细快照命令（IF-10 · {@code batchAppendSnapshots}）。
 */
@Getter
@Setter
public class BatchAppendDocInventoryItemSnapshotCommand {

    /** 租户。 */
    private Long tenantId;

    /** 批次单号（trim 非空，BR-DOC-SNAP-001；序号由应用层 SequenceService 生成）。 */
    private String busNo;

    /** 本批次快照完成时刻。 */
    private LocalDateTime snapshotTime;

    /** 任务操作者/系统账号。 */
    private Long createdBy;

    /** 待落库行；空列表时领域服务 no-op。 */
    private List<DocInventoryItemSnapshotRowCommand> rows;
}
