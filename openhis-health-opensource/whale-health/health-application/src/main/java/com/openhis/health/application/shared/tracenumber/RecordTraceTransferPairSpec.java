/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.tracenumber;

import com.openhis.health.domain.share.product.MedicationProductUnitConversionSource;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * 院内调拨/请领成对 OUT+IN 追溯写编排命令（AS-CUS-TRC-001-SDD §3.3）。
 */
@Getter
@Setter
public class RecordTraceTransferPairSpec {

    /** 操作人标识。 */
    private Long operatorId;

    /** 供应类型 code。 */
    private String supplyTypeCode;

    /** 同一业务单据号。 */
    private String busNo;

    /** 来源库位（OUT）。 */
    private Long outLocationId;

    /** 来源库位类型。 */
    private String outLocationTypeEnum;

    /** 目的库位（IN）。 */
    private Long inLocationId;

    /** 目的库位类型。 */
    private String inLocationTypeEnum;

    /** 成对明细行。 */
    private List<TransferLine> lines;

    /**
     * 调拨成对明细：每码一行。
     */
    @Getter
    @Setter
    public static class TransferLine {

        /** 追溯码。 */
        private String traceNo;

        /** 品项表。 */
        private String itemTable;

        /** 品项标识。 */
        private Long itemId;

        /** 批号。 */
        private String lotNumber;

        /** 业务数量。 */
        private BigDecimal quantity;

        /** 业务单位。 */
        private String unitCode;

        /** 单位换算源。 */
        private MedicationProductUnitConversionSource unitConversionSource;

        /** 仓位标识（可空）。 */
        private Long locationStoreId;
    }
}
