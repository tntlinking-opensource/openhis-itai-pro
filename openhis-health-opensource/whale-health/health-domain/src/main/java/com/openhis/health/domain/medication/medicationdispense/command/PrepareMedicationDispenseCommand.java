/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
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
