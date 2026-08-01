/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.peis.commonresult.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 停用 PEIS 常见结果命令。
 */
@Getter
@Setter
public class DeactivatePeisCommonResultCommand {

    /** 常见结果主键。 */
    private Long id;
}
