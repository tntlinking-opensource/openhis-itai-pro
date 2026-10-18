/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.chargeitem.command;

import com.zxplat.domain.share.command.BatchCommand;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
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

    /** 操作人主键（创建路径不写 enterer_*；收费人仅结算/退费完成时落库）。 */
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
