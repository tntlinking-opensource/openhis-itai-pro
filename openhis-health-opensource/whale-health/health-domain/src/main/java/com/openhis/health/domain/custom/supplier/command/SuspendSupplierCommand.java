/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.supplier.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 停用供应商命令（支持单条与批量）。
 */
@Getter
@Setter
public class SuspendSupplierCommand extends BatchCommand<SuspendSupplierCommand.SuspendSupplierItemCommand> {
    /**
     * 操作人标识。
     */
    private Long operatorId;

    /**
     * 停用子命令。
     */
    @Getter
    @Setter
    public static class SuspendSupplierItemCommand {
        /**
         * 供应商标识。
         */
        private Long supplierId;
        /**
         * 停用原因（可空）。
         */
        private String reason;
    }
}
