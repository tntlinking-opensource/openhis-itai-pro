/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.chargeitemdefinition.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 批量停用当前启用定价行命令：将匹配键下的 {@code ACTIVE} 行迁为 {@code INACTIVE}（调价审批 BR-PRC-APR-003）。
 */
@Getter
@Setter
public class DeactivateActivePriceLinesCommand extends BatchCommand<DeactivateActivePriceLinesCommand.Item> {

    /** 操作人主键（审计 {@code updated_by}）。 */
    private Long operatorId;

    /**
     * 单条停用匹配键（实例 + 价类 + 可选批号）。
     */
    @Getter
    @Setter
    public static class Item {

        /** 实例表标识。 */
        private String instanceTable;

        /** 业务实例主键。 */
        private Long instanceId;

        /** 价类持久化码（{@link com.zxplat.domain.share.enums.PriceType#getCode()}）。 */
        private String priceTypeCode;

        /**
         * 批号；项目级价类（{@code PURCHASE}/{@code SALE}）须为空或空白。
         */
        private String lotNo;
    }
}
