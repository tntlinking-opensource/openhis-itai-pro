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
