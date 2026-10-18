/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.supplier.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 启用供应商命令（支持单条与批量）。
 */
@Getter
@Setter
public class ActivateSupplierCommand extends BatchCommand<ActivateSupplierCommand.ActivateSupplierItemCommand> {
    /**
     * 操作人标识。
     */
    private Long operatorId;

    /**
     * 启用子命令。
     */
    @Getter
    @Setter
    public static class ActivateSupplierItemCommand {
        /**
         * 供应商标识。
         */
        private Long supplierId;
    }
}
