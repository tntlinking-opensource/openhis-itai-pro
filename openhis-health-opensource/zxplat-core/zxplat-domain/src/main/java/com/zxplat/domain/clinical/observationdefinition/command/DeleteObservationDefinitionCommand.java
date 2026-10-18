/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.observationdefinition.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 软删临床观察定义命令。
 */
@Getter
@Setter
public class DeleteObservationDefinitionCommand {

    /** 观察定义主键。 */
    private Long id;

    /** 操作人主键（写入 deleted_by）。 */
    private Long operatorId;
}
