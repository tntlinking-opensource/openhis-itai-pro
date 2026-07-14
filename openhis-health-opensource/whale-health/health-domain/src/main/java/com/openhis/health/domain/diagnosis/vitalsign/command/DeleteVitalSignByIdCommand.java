/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.diagnosis.vitalsign.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 按主表 id 删除生命体征单据命令（DM-DIA-002 v2.0 §6.1.2）。
 */
@Getter
@Setter
public class DeleteVitalSignByIdCommand {

    /** 主表 id。 */
    private Long id;

    /** 操作人 id。 */
    private Long operatorId;
}
