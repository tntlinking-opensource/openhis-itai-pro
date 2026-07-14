/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.supplier.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 废弃供应商命令（支持单条与批量）。
 */
@Getter
@Setter
public class RetireSupplierCommand extends BatchCommand<RetireSupplierCommand.RetireSupplierItemCommand> {
    /**
     * 操作人标识。
     */
    private Long operatorId;

    /**
     * 废弃子命令。
     */
    @Getter
    @Setter
    public static class RetireSupplierItemCommand {
        /**
         * 供应商标识。
         */
        private Long supplierId;
        /**
         * 废弃原因（可空）。
         */
        private String reason;
    }
}
