/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationdispense.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 配药命令。
 */
@Getter
@Setter
public class PrepareMedicationDispenseCommand extends BatchCommand<PrepareMedicationDispenseCommand.Item> {
    /** 操作人标识。 */
    private Long operatorId;

    /**
     * 配药子命令。
     */
    @Getter
    @Setter
    public static class Item {
        /** 发放单标识。 */
        private Long id;
        /** 确认后的发放库房（库位）标识。 */
        private Long locationId;
        /** 配药人标识。 */
        private Long preparerId;
        /** 追溯码。 */
        private String traceNo;
        /** 批号。 */
        private String lotNumber;
        /** 本次配药数量。 */
        private BigDecimal quantity;
    }
}
