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

package com.openhis.health.domain.administration.location.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 编辑位置信息命令：维护名称、形态、模式、类型 JSON、排序与检索码等，可携带变更字段列表供审计。
 */
@Getter
@Setter
public class EditLocationInfoCommand extends BatchCommand<EditLocationInfoCommand.EditLocationInfoItemCommand> {
    /** 操作人主键。 */
    private Long operatorId;

    /** 单条编辑项。 */
    @Getter
    @Setter
    public static class EditLocationInfoItemCommand {
        /** 位置主键。 */
        private Long locationId;
        /** 位置名称。 */
        private String name;
        /** 地点形态枚举编码（院区/楼栋/床位等）。 */
        private String formEnum;
        /** 管理模式枚举编码。 */
        private String modeEnum;
        /** 类型扩展 JSON（与前端/字典约定）。 */
        private String typeJson;
        /** 展示排序。 */
        private Integer displayOrder;
        /** 拼音检索串。 */
        private String pyStr;
        /** 五笔检索串。 */
        private String wbStr;
        /** 本次实际变更的字段名列表。 */
        private List<String> changedFields;
    }
}
