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
public class ImportDeviceDefinitionCatalogCommand extends BatchCommand<ImportDeviceDefinitionCatalogCommand.Item> {
    /** 操作人标识。 */
    private Long operatorId;

    /** 是否覆盖更新：true=允许覆盖已存在目录；false=遇到已存在目录则记为失败并跳过。 */
    private Boolean overwrite;

    @Getter
    @Setter
    public static class Item {
        /** 器材业务编码（院内唯一）。 */
        private String busNo;
        /** 器材名称。 */
        private String name;
        /** 器材分类编码。 */
        private String categoryCode;
        /** 器材种类编码。 */
        private String typeCode;
        /** 包装单位。 */
        private String unitCode;
        /** 包装规格描述。 */
        private String totalVolume;
        /** 库存单位编码（持久化列 stock_unit_code）。 */
        private String stockUnitCode;
        /** 所属科室标识。 */
        private Long orgId;
        /** 所在位置标识。 */
        private Long locationId;
        /** 医保标记。 */
        private Boolean ybFlag;
        /** 医保编码。 */
        private String ybNo;
        /** 医药机构目录编码。 */
        private String ybOrgNo;
        /** 医保对码标记。 */
        private Boolean ybMatchFlag;
        /** 医保等级。 */
        private Integer chrgitmLv;
        /** 器材说明。 */
        private String description;
    }
}
