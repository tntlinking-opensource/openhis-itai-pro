/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.terminologycodemapping.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 批量导入术语映射命令。
 */
@Getter
@Setter
public class ImportMappingBatchCommand extends BatchCommand<ImportMappingBatchCommand.ImportMappingItemCommand> {
    /**
     * 操作人标识（批量条目共用）。
     */
    private Long operatorId;

    /**
     * 导入子命令。
     */
    @Getter
    @Setter
    public static class ImportMappingItemCommand {
        /**
         * 源术语所在表名。
         */
        private String sourceItemTable;
        /**
         * 源术语编码/编号。
         */
        private String sourceItemNo;
        /**
         * 目标术语编码/编号。
         */
        private String targetItemNo;
        /**
         * 目标术语名称（可空）。
         */
        private String targetName;
    }
}
