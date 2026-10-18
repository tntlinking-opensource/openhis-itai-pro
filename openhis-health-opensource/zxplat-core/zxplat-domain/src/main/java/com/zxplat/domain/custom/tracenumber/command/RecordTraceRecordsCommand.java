/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.tracenumber.command;

import com.zxplat.domain.share.product.MedicationProductUnitConversionSource;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * 记录追溯码记录命令（DM-CUS-010 §6.1.1 · SDD §5.1.1）。
 */
@Getter
@Setter
public class RecordTraceRecordsCommand {

    /** 操作人标识。 */
    private Long operatorId;

    /** 记录行列表。 */
    private List<RecordLine> lines;

    /**
     * 单条追溯记录行。
     */
    @Getter
    @Setter
    public static class RecordLine {

        /** 追溯码。 */
        private String traceNo;

        /** 品项表（建主档时必填，本期固定药品表）。 */
        private String itemTable;

        /** 药品定义标识。 */
        private Long itemId;

        /** 批号。 */
        private String lotNumber;

        /** 追溯码单位（建主档/进货时）。 */
        private String traceUnitCode;

        /**
         * 建主档时写入的追溯→拆零倍数快照；须 &gt; 0。
         */
        private BigDecimal traceUnitRatio;

        /** 记录类别：HOSPITAL_IO / INTERNAL_TRANSFER。 */
        private String recordCategoryEnum;

        /** 轨迹方向：IN / OUT。 */
        private String recordDirectionEnum;

        /** 本条业务数量。 */
        private BigDecimal quantity;

        /** 本条业务单位。 */
        private String unitCode;

        /**
         * 药品五类单位换算源；须由应用层预加载后填入，领域层禁止跨聚合查药品目录（B.6.4）。
         */
        private MedicationProductUnitConversionSource unitConversionSource;

        /** 库位（记录用）。 */
        private Long locationId;

        /** 库位类型（记录用）。 */
        private String locationTypeEnum;

        /** 仓位标识（可空）。 */
        private Long locationStoreId;

        /** 操作类型 code。 */
        private String operationType;

        /** 业务单据号。 */
        private String busNo;
    }
}
