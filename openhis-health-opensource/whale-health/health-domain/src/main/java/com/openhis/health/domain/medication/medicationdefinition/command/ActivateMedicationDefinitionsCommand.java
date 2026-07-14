/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationdefinition.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 批量重新启用药品定义命令。
 */
@Getter
@Setter
public class ActivateMedicationDefinitionsCommand
    extends BatchCommand<ActivateMedicationDefinitionsCommand.ActivateMedicationDefinitionsItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

    /**
     * 批量启用子命令。
     */
    @Getter
    @Setter
    public static class ActivateMedicationDefinitionsItemCommand {
        /** 药品定义标识。 */
        private Long id;
    }
}
