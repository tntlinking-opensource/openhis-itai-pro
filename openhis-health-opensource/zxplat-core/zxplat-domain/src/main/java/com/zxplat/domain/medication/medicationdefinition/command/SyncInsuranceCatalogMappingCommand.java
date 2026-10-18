/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.medication.medicationdefinition.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 同步医保目录对码命令。
 */
@Getter
@Setter
public class SyncInsuranceCatalogMappingCommand
    extends BatchCommand<SyncInsuranceCatalogMappingCommand.SyncInsuranceCatalogMappingItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

    /**
     * 医保对码同步子命令。
     */
    @Getter
    @Setter
    public static class SyncInsuranceCatalogMappingItemCommand {
        /** 药品定义标识。 */
        private Long id;
        /** 是否对码。 */
        private Boolean ybMatchFlag;
        /** 医保编码。 */
        private String ybNo;
        /** 医保等级。 */
        private Integer chrgitmLv;
        /** 同步时间。 */
        private LocalDateTime syncTime;
    }
}
