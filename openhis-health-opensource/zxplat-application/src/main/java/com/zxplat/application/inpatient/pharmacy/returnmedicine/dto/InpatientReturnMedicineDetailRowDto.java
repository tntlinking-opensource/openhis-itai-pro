/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.pharmacy.returnmedicine.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 住院退药明细行 DTO（IF-IPR-03）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院退药明细行")
public class InpatientReturnMedicineDetailRowDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 待退或已退发放行主键 {@code med_medication_dispense.id}。 */
    @Schema(description = "待退/已退发放行 id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dispenseId;

    /** 原药嘱 id（med_req_id）。 */
    @Schema(description = "原药嘱 id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long requestId;

    /** 药品定义 id。 */
    @Schema(description = "药品定义 id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long itemId;

    /** 药品名称。 */
    @Schema(description = "药品名称")
    private String itemName;

    /** 批号。 */
    @Schema(description = "批号")
    private String lotNumber;

    /** 应退数量（绝对值）。 */
    @Schema(description = "应退数量（绝对值）")
    private BigDecimal quantity;

    /** 单位 code。 */
    @Schema(description = "单位 code")
    private String unitCode;

    /** 单位展示。 */
    @Schema(description = "单位展示")
    private String unitCodeText;

    /** 开立医生。 */
    @Schema(description = "开立医生")
    private String doctorName;

    /** 发放状态 code。 */
    @Schema(description = "发放状态 code")
    private String refundEnum;

    /** 发放状态展示。 */
    @Schema(description = "发放状态展示")
    private String refundEnumText;

    /** 业务表名，固定 med_medication_request。 */
    @Schema(description = "业务表名")
    private String serviceTable;

    /** 追溯码（逗号分隔）。 */
    @Schema(description = "追溯码（逗号分隔）")
    private String traceNo;

    /**
     * 退药发放业务单号。
     * <p>码上放心 116 上传键；与 TraceNumber {@code MEDICATION_RETURN_IN} 的 bus_no 一致。</p>
     */
    @Schema(description = "退药发放业务单号（码上放心上传键）")
    private String busNo;

    /** 是否可勾选确认；仅 PENDING_REFUND 为 true。 */
    @Schema(description = "是否可勾选确认")
    private Boolean selectable;
}
