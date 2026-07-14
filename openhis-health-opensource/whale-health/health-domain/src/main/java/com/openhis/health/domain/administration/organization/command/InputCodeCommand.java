/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.organization.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 输入码命令片段：拼音与五笔检索串，供机构/地点等主数据检索。
 */
@Getter
@Setter
/**
 * InputCodeCommand。
 */
public class InputCodeCommand {
    /** 拼音码。 */
    /**
     * pyStr。
     */
    private String pyStr;
    /** 五笔码。 */
    /**
     * wbStr。
     */
    private String wbStr;
}
