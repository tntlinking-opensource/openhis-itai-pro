/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.tracenumber.command;

import com.openhis.health.domain.share.product.MedicationProductUnitConversionSource;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * 从业务行逗号串展开记录追溯码（DM-CUS-010 §6.1.2 · SDD §5.1.2）。
 */
@Getter
@Setter
public class RecordTraceRecordsFromLineItemsCommand {

    /** 操作人标识。 */
    private Long operatorId;

    /** 业务行列表。 */
    private List<LineItem> lines;

    /**
     * 含逗号分隔追溯码的业务行。
     */
    @Getter
    @Setter
    public static class LineItem {

        /** 逗号分隔追溯码串。 */
        private String traceNos;

        /** 品项表（本期药品）。 */
        private String itemTable;

        /** 药品定义标识。 */
        private Long itemId;

        /** 批号。 */
        private String lotNumber;

        /** 追溯码单位。 */
        private String traceUnitCode;

        /**
         * 建主档时写入的追溯→拆零倍数快照；须 &gt; 0。
         */
        private BigDecimal traceUnitRatio;

        /** 业务数量。 */
        private BigDecimal quantity;

        /** 业务单位。 */
        private String unitCode;

        /** 药品五类单位换算源（应用层预加载）。 */
        private MedicationProductUnitConversionSource unitConversionSource;

        /** 记录类别。 */
        private String recordCategoryEnum;

        /** 轨迹方向。 */
        private String recordDirectionEnum;

        /** 库位。 */
        private Long locationId;

        /** 库位类型。 */
        private String locationTypeEnum;

        /** 仓位标识。 */
        private Long locationStoreId;

        /** 操作类型。 */
        private String operationType;

        /** 业务单号。 */
        private String busNo;
    }
}
