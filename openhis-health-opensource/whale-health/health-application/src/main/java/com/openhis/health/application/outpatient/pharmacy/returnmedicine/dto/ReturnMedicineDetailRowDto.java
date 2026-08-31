/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.pharmacy.returnmedicine.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 退药明细行（IF-PHA-02-03）。
 */
@Data
/** 退药明细行。 */
@Schema(description = "退药明细行")
public class ReturnMedicineDetailRowDto {

    @Schema(description = "发放单标识", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dispenseId;

    @Schema(description = "请求单标识（负向）", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long requestId;

    /** 原药品/耗材请求单标识。 */
    @Schema(description = "原药品/耗材请求单标识", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long refundMedicineId;

    /** 药品/耗材定义标识。 */
    @Schema(description = "药品/耗材定义标识", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long itemId;

    /** 药品/耗材名称。 */
    @Schema(description = "药品/耗材名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String itemName;

    /** 批号。 */
    @Schema(description = "批号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String lotNumber;

    /** 应退数量。 */
    @Schema(description = "应退数量（绝对值）", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal quantity;

    /** 单位编码。 */
    @Schema(description = "单位编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String unitCode;

    /** 单位显示。 */
    @Schema(description = "单位显示", requiredMode = Schema.RequiredMode.REQUIRED)
    private String unitCodeText;

    /** 该行收费合计。 */
    @Schema(description = "该行收费合计（按发放单汇总 ChargeItem，与门诊发药展示口径一致）", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal totalPrice;

    /** 开单医生姓名。 */
    @Schema(description = "开单医生姓名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String doctorName;

    /** 退药状态编码。 */
    @Schema(description = "退药状态编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String refundEnum;

    /** 退药状态展示。 */
    @Schema(description = "退药状态展示", requiredMode = Schema.RequiredMode.REQUIRED)
    private String refundEnumText;

    /** 请求状态编码。 */
    @Schema(description = "请求状态编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String reqStatus;

    /** 请求状态展示。 */
    @Schema(description = "请求状态展示", requiredMode = Schema.RequiredMode.REQUIRED)
    private String reqStatusText;

    /** 业务表名。 */
    @Schema(description = "业务表名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String serviceTable;

    /** 追溯码。 */
    @Schema(description = "追溯码（逗号分隔）")
    private String traceNo;

    /**
     * 退药发放业务单号。
     * <p>码上放心 116 上传键；与 TraceNumber {@code MEDICATION_RETURN_IN} 的 bus_no 一致。</p>
     */
    @Schema(description = "退药发放业务单号（码上放心上传键）")
    private String busNo;
}
