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
 * 从已发布模板复制草稿命令（TC-PRT-TPL-FORK-001）。
 */
@Getter
@Setter
public class ForkDraftFromPublishedCommand {

    /** 租户主键。 */
    private Long tenantId;

    /** 机构主键（DM-PRT-001 §9.1 deferred；写侧恒 NULL，保留供后续院点 override）。 */
    private Long organizationId;

    /** 模板 busNo。 */
    private String busNo;

    /** 指定 published 主键；为空则取最新 published。 */
    private Long publishedId;
}
