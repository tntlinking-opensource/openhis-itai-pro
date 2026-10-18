/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.medication.medicationdefinition.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 废弃药品定义命令。
 */
@Getter
@Setter
public class DiscardMedicationDefinitionCommand
    extends BatchCommand<DiscardMedicationDefinitionCommand.DiscardMedicationDefinitionItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

    /**
     * 废弃子命令。
     */
    @Getter
    @Setter
    public static class DiscardMedicationDefinitionItemCommand {
        /** 药品定义标识。 */
        private Long id;
        /** 废弃原因。 */
        private String reason;
    }
}
