/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
