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

package com.openhis.health.domain.workflow.devicedispense.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 汇总完成回写发放单命令。
 *
 * <p>约定：{@code summaryKeyOrIdList} 为 {@code batchKey|id1,id2,...}；若无 {@code |} 则整串解析为 id 列表，批次键为空。</p>
 */
@Getter
@Setter
public class UpdateDispenseStatusCompletedFromSummaryCommand {

    /** 汇总key或标识列表。 */
    private String summaryKeyOrIdList;

    /** 操作人主键。 */
    private Long operatorId;
}
