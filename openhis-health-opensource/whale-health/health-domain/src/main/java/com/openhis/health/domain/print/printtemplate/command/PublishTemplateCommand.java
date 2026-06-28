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

package com.openhis.health.domain.print.printtemplate.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 发布画布打印模板命令（TC-PRT-TPL-PUB-001）。
 */
@Getter
@Setter
public class PublishTemplateCommand {

    /** 待发布 draft 主键。 */
    private Long draftId;

    /** 租户主键（可选校验）。 */
    private Long tenantId;

    /** 模板 busNo（可选校验）。 */
    private String busNo;
}
