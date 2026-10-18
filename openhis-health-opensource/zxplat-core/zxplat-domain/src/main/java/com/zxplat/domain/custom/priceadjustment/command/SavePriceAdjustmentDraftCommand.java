/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.priceadjustment.command;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 保存调价草稿命令（整单覆盖主行与子表；子表快照由应用层预组装后传入，DM-CUS-011 §6.1.1）。
 */
@Getter
@Setter
public class SavePriceAdjustmentDraftCommand {

    /** 调价单号；空则按 PAD 日序生成新单。 */
    private String busNo;

    /** 操作人 {@code operator_id}。 */
    private Long operatorId;

    /** 机构 {@code org_id}。 */
    private Long orgId;

    /** 调价原因 {@code adjustment_reason}。 */
    private String adjustmentReason;

    /** 制单时间 {@code adjustment_time}；空则取当前时刻。 */
    private Date adjustmentTime;

    /** 项目行列表；同单 {@code item_table} 须一致。 */
    private List<Line> lines;

    /**
     * 调价项目行入参（对应主表一行）。
     */
    @Getter
    @Setter
    public static class Line {

        /** 项目物理表（药品/耗材/诊疗活动/医疗服务）。 */
        private String itemTable;

        /** 项目定义主键 {@code item_id}。 */
        private Long itemId;

        /** 目标进价；诊疗调价须为空。 */
        private BigDecimal newPurchaseAmount;

        /** 目标售价；药品耗材与诊疗均至少填此项或目标进价。 */
        private BigDecimal newSellingAmount;

        /** 诊疗调价：调前售价（一对一子表；与 {@code details} 二选一）。 */
        private BigDecimal oldSellingAmount;

        /** 药品耗材批号明细；诊疗调价勿传多行批号。 */
        private List<DetailLine> details;
    }

    /**
     * 批号明细入参（对应子表一行；物流可按库房+批号多行）。
     */
    @Getter
    @Setter
    public static class DetailLine {

        /**
         * 库位主键 {@code location_id}；可空（诊疗/未入库/应用层未能解析库位时）。
         */
        private Long locationId;

        /** 批号 {@code lot_number}。 */
        private String lotNumber;

        /** 快照数量 {@code quantity}（有 loc 时为本库数量）。 */
        private BigDecimal quantity;

        /** 调前进价 {@code old_purchase_amount}。 */
        private BigDecimal oldPurchaseAmount;

        /** 调前售价；诊疗一对一子表主存字段。 */
        private BigDecimal oldSellingAmount;
    }
}
