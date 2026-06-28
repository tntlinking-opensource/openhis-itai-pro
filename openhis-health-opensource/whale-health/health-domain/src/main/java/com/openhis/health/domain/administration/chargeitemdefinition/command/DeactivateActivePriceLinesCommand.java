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

package com.openhis.health.domain.administration.chargeitemdefinition.command;

import com.openhis.health.domain.share.command.BatchCommand;
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

        /** 价类持久化码（{@link com.openhis.health.domain.share.enums.PriceType#getCode()}）。 */
        private String priceTypeCode;

        /**
         * 批号；项目级价类（{@code PURCHASE}/{@code SALE}）须为空或空白。
         */
        private String lotNo;
    }
}
