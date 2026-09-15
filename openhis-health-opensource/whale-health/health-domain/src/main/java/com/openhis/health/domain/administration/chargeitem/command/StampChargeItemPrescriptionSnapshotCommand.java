/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.chargeitem.command;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 门诊结算：将上游处方号快照写入收费行（仅快照，业务真源仍在药品/诊疗请求）。
 */
@Getter
@Setter
public class StampChargeItemPrescriptionSnapshotCommand {

    /** 操作人（审计占位；本命令不改删除字段）。 */
    private Long operatorId;

    /** 待盖章条目。 */
    private List<Item> items;

    /**
     * 解析条目；{@code null} 时返回空列表。
     *
     * @return 非 null 列表
     */
    public List<Item> resolveItems() {
        if (this.items == null) {
            return new ArrayList<>();
        }
        return this.items;
    }

    /**
     * 单条收费行处方号快照。
     */
    @Getter
    @Setter
    public static class Item {

        /** 收费明细主键。 */
        private Long chargeItemId;

        /** 结算时刻从上游请求抄写的处方号；空则跳过该行。 */
        private String prescriptionNo;
    }
}
