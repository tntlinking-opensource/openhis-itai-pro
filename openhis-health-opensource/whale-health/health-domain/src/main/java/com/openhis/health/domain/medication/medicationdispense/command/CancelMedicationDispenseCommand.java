/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationdispense.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 作废未发药品发放命令。
 */
@Getter
@Setter
public class CancelMedicationDispenseCommand extends BatchCommand<CancelMedicationDispenseCommand.Item> {
    /** 操作人标识。 */
    private Long operatorId;

    /**
     * 作废子命令。
     */
    @Getter
    @Setter
    public static class Item {
        /** 发放单标识。 */
        private Long id;
        /** 未执行原因编码。 */
        private String notPerformedReasonEnum;
    }
}
