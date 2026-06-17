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

/**
 * 创建医疗服务命令（批量）。
 * <p>
 * 用于登记新的医疗服务项，包括名称、类型、分类及提供机构等主数据。
 */
@Getter
@Setter
public class CreateHealthcareServiceCommand extends BatchCommand<CreateHealthcareServiceCommand.Item> {
    /** 执行本次批量创建的操作人标识。 */
    private Long operatorId;
    /**
     * 单条创建项：新医疗服务的基础属性。
     */
    @Getter
    @Setter
    public static class Item {
        /** 医疗服务显示名称。 */
        private String name;
        /** 服务类型字典编码。 */
        private String typeCode;
        /** 服务分类字典编码。 */
        private String categoryCode;
        /** 提供该服务的组织机构主键。 */
        private Long offeredOrgId;
        /** 医保目录编码或对照码。 */
        private String ybNo;
    }
}
