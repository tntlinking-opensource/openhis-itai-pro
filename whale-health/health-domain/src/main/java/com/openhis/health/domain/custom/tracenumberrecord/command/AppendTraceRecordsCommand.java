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

package com.openhis.health.domain.custom.tracenumberrecord.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 追加追溯记录命令。
 */
@Getter
@Setter
public class AppendTraceRecordsCommand extends BatchCommand<AppendTraceRecordsCommand.AppendTraceRecordItemCommand> {
    /**
     * 操作人标识（批量条目共用）。
     */
    private Long operatorId;

    @Getter
    @Setter
    public static class AppendTraceRecordItemCommand {
        /**
         * 物品来源表名（药品/耗材等）。
         */
        private String itemTable;
        /**
         * 物品主键。
         */
        private Long itemId;
        /**
         * 位置类型枚举值。
         */
        private String locationTypeEnum;
        /**
         * 位置标识。
         */
        private Long locationId;
        /**
         * 库房/库位标识（可空）。
         */
        private Long locationStoreId;
        /**
         * 批号（可空）。
         */
        private String lotNumber;
        /**
         * 追溯码。
         */
        private String traceNo;
        /**
         * 状态枚举值。
         */
        private String statusEnum;
        /**
         * 单位编码（可空）。
         */
        private String unitCode;
        /**
         * 操作类型（{@link com.openhis.health.domain.share.enums.TraceNumberRecordOperationType#getCode()} 持久化串）。
         */
        private String operationType;
        /**
         * 业务单号（可空；用于追溯链路关联）。
         */
        private String busNo;
    }
}

