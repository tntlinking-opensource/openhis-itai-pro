/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.observationdefinition.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 停用临床观察定义命令。
 */
@Getter
@Setter
public class DeactivateObservationDefinitionCommand {

    /** 观察定义主键。 */
    private Long id;
}
