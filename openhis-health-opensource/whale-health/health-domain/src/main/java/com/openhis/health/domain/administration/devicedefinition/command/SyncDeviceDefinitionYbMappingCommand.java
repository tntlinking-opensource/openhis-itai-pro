/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.devicedefinition.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 同步器材定义医保对码命令（DM-ADM-008 医保映射维护）。
 *
 * <p>按 {@link Item#id} 批量更新 yb 相关字段，不改动非医保主数据属性。</p>
 */
@Getter
@Setter
public class SyncDeviceDefinitionYbMappingCommand extends BatchCommand<SyncDeviceDefinitionYbMappingCommand.Item> {
    /** 操作人标识。 */
    private Long operatorId;
    @Getter
    @Setter
    public static class Item {
        /** 器材定义标识。 */
        private Long id;
        /** 医保标记。 */
        private Boolean ybFlag;
        /** 医保编码。 */
        private String ybNo;
        /** 医药机构目录编码。 */
        private String ybOrgNo;
        /** 对码标记。 */
        private Boolean ybMatchFlag;
        /** 医保等级。 */
        private Integer chrgitmLv;

        /**
         * 医保对码标识（存量兼容字段）。
         *
         * <p>DOC-CONFLICT：SDD 字段全集未包含该字段；存量实现曾使用该字段回写。当前保留以避免破坏既有调用方。</p>
         */
        private String ybMappingCode;
    }
}
