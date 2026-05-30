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
 * 发放单汇总落账命令。
 */
@Getter
@Setter
/**
 * UpdateDispenseStatusSummarizedCommand。
 */
public class UpdateDispenseStatusSummarizedCommand {

    /** 逗号分隔 id 列表，或已写入 {@code supportInfo} 的汇总锚点。 */
    /**
     * idListOrSummaryKey。
     */
    private String idListOrSummaryKey;

    /** 操作人主键。 */
    /**
     * operatorId。
     */
    private Long operatorId;
}
