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
 * 保存画布打印模板草稿命令（TC-PRT-TPL-SAVE-001）。
 */
@Getter
@Setter
public class SaveDraftCommand {

    /** 租户主键。 */
    private Long tenantId;

    /** 机构主键（DM-PRT-001 §9.1 deferred；写侧恒 NULL，保留供后续院点 override）。 */
    private Long organizationId;

    /** 模板业务键 busNo。 */
    private String busNo;

    /** 模板展示名称。 */
    private String name;

    /** PrintJson 版式 JSON 字符串。 */
    private String printJson;

    /** 示例打印数据 JSON 字符串。 */
    private String samplePrintData;

    /** 场景码（可选）。 */
    private String scenarioCode;

    /** 已有草稿主键；更新时传入。 */
    private Long existingId;
}
