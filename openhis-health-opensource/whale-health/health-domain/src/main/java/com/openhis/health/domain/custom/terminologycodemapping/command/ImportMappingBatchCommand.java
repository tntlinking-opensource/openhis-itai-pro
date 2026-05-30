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

package com.openhis.health.domain.custom.terminologycodemapping.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 批量导入术语映射命令。
 */
@Getter
@Setter
public class ImportMappingBatchCommand extends BatchCommand<ImportMappingBatchCommand.ImportMappingItemCommand> {
    /**
     * 操作人标识（批量条目共用）。
     */
    private Long operatorId;

    /**
     * 导入子命令。
     */
    @Getter
    @Setter
    public static class ImportMappingItemCommand {
        /**
         * 源术语所在表名。
         */
        private String sourceItemTable;
        /**
         * 源术语编码/编号。
         */
        private String sourceItemNo;
        /**
         * 目标术语编码/编号。
         */
        private String targetItemNo;
        /**
         * 目标术语名称（可空）。
         */
        private String targetName;
    }
}
