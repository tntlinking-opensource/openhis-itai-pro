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

package com.openhis.health.domain.administration.devicedefinition.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SyncDeviceDefinitionYbMappingCommand extends BatchCommand<SyncDeviceDefinitionYbMappingCommand.Item> {
    /** 操作人标识。 */
    private Long operatorId;
    @Getter
    @Setter
    public static class Item {
        /** 器材定义标识。 */
        private Long id;
        /** 医保标记。 */
        private Boolean ybFlag;
        /** 医保编码。 */
        private String ybNo;
        /** 医药机构目录编码。 */
        private String ybOrgNo;
        /** 对码标记。 */
        private Boolean ybMatchFlag;
        /** 医保等级。 */
        private Integer chrgitmLv;

        /**
         * 医保对码标识（存量兼容字段）。
         *
         * <p>DOC-CONFLICT：SDD 字段全集未包含该字段；存量实现曾使用该字段回写。当前保留以避免破坏既有调用方。</p>
         */
        private String ybMappingCode;
    }
}
