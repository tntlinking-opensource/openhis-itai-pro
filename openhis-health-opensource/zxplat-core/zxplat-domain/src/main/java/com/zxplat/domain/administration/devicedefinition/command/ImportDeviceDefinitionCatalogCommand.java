/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.devicedefinition.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 器材目录批量导入命令（DM-ADM-008 §6.1.5）。
 *
 * <p>{@link #overwrite} 控制遇重复业务编码时覆盖或跳过；明细行见 {@link Item}。</p>
 */
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
        /** 医保对码标记。 */
        private Boolean ybMatchFlag;
        /** 医保等级。 */
        private Integer chrgitmLv;
        /** 器材说明。 */
        private String description;
    }
}
