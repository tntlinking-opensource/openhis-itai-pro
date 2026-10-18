/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.chargeitemdefinition.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 批量保存收费项目定义命令：按实例维度写入类型、医保类别、价类、批次与价格等。
 */
@Getter
@Setter
public class SaveAllChargeItemDefinitionsCommand extends BatchCommand<SaveAllChargeItemDefinitionsCommand.Item> {
    /** 操作人主键。 */
    private Long operatorId;

    /**
     * 单条价表/定义项。
     */
    @Getter
    @Setter
    public static class Item {
        /** 实例表标识。 */
        private String instanceTable;
        /** 业务实例主键。 */
        private Long instanceId;
        /** 收费类型编码。 */
        private String typeCode;
        /** 医保类别或对照编码。 */
        private String ybType;
        /** 价格类型编码（如零售价、医保价）。 */
        private String priceTypeCode;
        /** 收费项展示名称（写入 {@code charge_name}）。 */
        private String chargeName;
        /** 批次或效期批次号（可空）。 */
        private String lotNo;
        /** 价格数值（元，与 {@code adm_charge_item_definition.price_value} 一致）。 */
        private BigDecimal priceValue;
    }
}
