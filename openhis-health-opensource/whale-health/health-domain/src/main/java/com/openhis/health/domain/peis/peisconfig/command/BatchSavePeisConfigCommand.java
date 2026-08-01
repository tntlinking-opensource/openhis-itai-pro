/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.peis.peisconfig.command;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量保存 PEIS 模块参数命令。
 */
@Getter
@Setter
public class BatchSavePeisConfigCommand {

    /** 至少一条。 */
    private List<SavePeisConfigCommand> configs = new ArrayList<>();
}
