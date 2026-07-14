/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
