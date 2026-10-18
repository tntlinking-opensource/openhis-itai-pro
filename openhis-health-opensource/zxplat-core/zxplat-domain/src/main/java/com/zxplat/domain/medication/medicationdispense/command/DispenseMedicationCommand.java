/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.medication.medicationdispense.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 发药命令。
 */
@Getter
@Setter
public class DispenseMedicationCommand extends BatchCommand<DispenseMedicationCommand.Item> {
    /** 操作人标识。 */
    private Long operatorId;

    /**
     * 发药子命令。
     */
    @Getter
    @Setter
    public static class Item {
        /** 发放单标识。 */
        private Long id;
        /** 发药人标识。 */
        private Long practitionerId;
        /** 实际发放数量。 */
        private BigDecimal dispenseQuantity;
    }
}
