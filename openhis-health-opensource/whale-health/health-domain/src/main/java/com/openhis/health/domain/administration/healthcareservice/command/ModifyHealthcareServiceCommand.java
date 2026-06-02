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

package com.openhis.health.domain.administration.healthcareservice.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 修改医疗服务命令（批量）。
 * <p>
 * 用于变更已有医疗服务的名称、类型、分类或提供机构等信息，可配合变更字段列表做增量更新或审计。
 */
@Getter
@Setter
public class ModifyHealthcareServiceCommand extends BatchCommand<ModifyHealthcareServiceCommand.Item> {
    /** 执行本次批量修改的操作人标识。 */
    private Long operatorId;
    /**
     * 单条修改项：目标服务标识与待更新字段。
     */
    @Getter
    @Setter
    public static class Item {
        /** 被修改的医疗服务主键。 */
        private Long serviceId;
        /** 医疗服务显示名称。 */
        private String name;
        /** 服务类型字典编码。 */
        private String typeCode;
        /** 服务分类字典编码。 */
        private String categoryCode;
        /** 提供该服务的组织机构主键。 */
        private Long offeredOrgId;
        /** 本次实际发生变更的字段名列表（用于部分更新或留痕）。 */
        private List<String> changedFields;
    }
}
