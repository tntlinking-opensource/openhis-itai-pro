/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.tracenumber;

import com.zxplat.domain.share.product.MedicationProductUnitConversionSource;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * 供应单行追溯写编排命令（AS-CUS-TRC-001-SDD §3.1）。
 */
@Getter
@Setter
public class RecordTraceFromSupplyLineSpec {

    /** 操作人标识。 */
    private Long operatorId;

    /** 供应类型 code（{@link com.zxplat.domain.share.enums.SupplyType#getCode()}）。 */
    private String supplyTypeCode;

    /** 业务单据号。 */
    private String busNo;

    /** 品项表（本期药品）。 */
    private String itemTable;

    /** 品项标识。 */
    private Long itemId;

    /** 批号。 */
    private String lotNumber;

    /** 逗号分隔追溯码；空白跳过。 */
    private String traceNos;

    /** 追溯码单位（进货必填）。 */
    private String traceUnitCode;

    /** 行业务数量。 */
    private BigDecimal quantity;

    /** 行业务单位。 */
    private String unitCode;

    /** 药品五类单位换算源（应用层预加载）。 */
    private MedicationProductUnitConversionSource unitConversionSource;

    /** 子表记录库位。 */
    private Long locationId;

    /** 库位类型（可空）。 */
    private String locationTypeEnum;

    /** 仓位标识（可空）。 */
    private Long locationStoreId;

    /** 单端轨迹方向 OUT/IN；调拨成对由 {@link RecordTraceTransferPairSpec} 处理。 */
    private String recordDirectionEnum;

    /**
     * 显式每码拆零量（退领镜像领用 OUT 用）；非空时跳过剩余先出/前填重算。
     */
    private List<TraceNumberRecordOrchestrator.ExplicitPerCodeSplit> explicitPerCodeSplits;
}
