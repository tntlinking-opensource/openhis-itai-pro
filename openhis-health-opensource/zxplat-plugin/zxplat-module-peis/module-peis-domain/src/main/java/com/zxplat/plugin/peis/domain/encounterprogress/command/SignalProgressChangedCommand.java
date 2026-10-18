/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.domain.encounterprogress.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 体检进度变更信号命令（DM-PEIS-008 §5.1.1）。
 */
@Getter
@Setter
public class SignalProgressChangedCommand {

    /** 就诊主键。 */
    private Long encounterId;

    /** 进度快照 code（可选）。 */
    private String progressCode;

    /** 触发来源（DES/MAS/INT）。 */
    private String triggerSource;
}
