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

package com.openhis.health.domain.administration.chargeitem.command;

import com.openhis.health.domain.share.command.BatchCommand;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 半退保留链：在已具备新上游与发放锚点后，批量落库新 {@code adm_charge_item} 行并链回源行（DM-ADM-011 §6.1.9a）。
 */
@Getter
@Setter
public class CreateChargeItemsLinkingRegeneratedAnchorsCommand extends BatchCommand<CreateChargeItemsLinkingRegeneratedAnchorsCommand.Item> {

    /** 操作人主键，写入新行 enterer_id。 */
    private Long operatorId;

    /**
     * 单条：源结算行 + 再生后锚点。
     */
    @Getter
    @Setter
    public static class Item {

        /** 源已结算收费明细主键；新行 refund_id 指向该行（BR-CHG-015）。 */
        private Long sourceChargeItemId;

        /** 再生后上游业务表名，对应 adm_charge_item.service_table。 */
        private String newServiceTable;

        /** 再生后上游业务主键，对应 adm_charge_item.service_id。 */
        private Long newServiceId;

        /** 再生后发放表名；与 newDispenseId 同空或同非空。 */
        private String newDispenseTable;

        /** 再生后发放主键，对应 adm_charge_item.dispense_id。 */
        private Long newDispenseId;
    }

    @Override
    public List<Item> resolveItems() {
        if (this.getItems() != null && !this.getItems().isEmpty()) {
            return this.getItems();
        }
        if (this.getItem() != null) {
            List<Item> normalized = new ArrayList<>();
            normalized.add(this.getItem());
            return normalized;
        }
        throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT, "收费明细保留链锚点入参不能为空");
    }
}
