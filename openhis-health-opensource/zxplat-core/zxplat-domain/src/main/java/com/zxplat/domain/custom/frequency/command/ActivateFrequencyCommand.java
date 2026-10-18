/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.frequency.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 启用频次命令。
 */
@Getter
@Setter
public class ActivateFrequencyCommand {
    /**
     * 频次标识。
     */
    private Long id;
    /**
     * 操作人标识。
     */
    private Long operatorId;
}
