/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.chargeitemdefinition.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 批量保存价格行命令：对已有或新建定义写入价类、批次与金额（支持按主键更新）。
 */
@Getter
@Setter
public class SavePriceLineCommand extends BatchCommand<SavePriceLineCommand.Item> {
    /** 操作人主键。 */
    private Long operatorId;

    /**
     * 单条价格行。
     */
    @Getter
    @Setter
    public static class Item {
        /** 收费项目定义主键；空表示新建由领域分配。 */
        private Long id;
        /** 实例表标识。 */
        private String instanceTable;
        /** 业务实例主键。 */
        private Long instanceId;
        /** 财务类型编码（fin_type_code）；缺省时领域层从同实例历史定价行继承。 */
        private String typeCode;
        /** 医保类别或对照编码（可空）。 */
        private String ybType;
        /** 价格类型编码。 */
        private String priceTypeCode;
        /** 批次号（可空）。 */
        private String lotNo;
        /** 价格数值（元）。 */
        private BigDecimal priceValue;

        /**
         * 发布状态持久化码；新建行缺省时领域层落 {@code ACTIVE}。
         */
        private String statusEnum;
    }
}
