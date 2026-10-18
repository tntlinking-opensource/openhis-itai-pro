/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.medication.medicationdefinition.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 批量停用药品定义命令。
 */
@Getter
@Setter
public class RetireMedicationDefinitionsCommand
    extends BatchCommand<RetireMedicationDefinitionsCommand.RetireMedicationDefinitionsItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

    /**
     * 批量停用子命令。
     */
    @Getter
    @Setter
    public static class RetireMedicationDefinitionsItemCommand {
        /** 药品定义标识。 */
        private Long id;
    }
}
