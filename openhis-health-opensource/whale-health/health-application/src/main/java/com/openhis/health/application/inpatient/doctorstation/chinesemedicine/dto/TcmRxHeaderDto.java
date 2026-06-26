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

package com.openhis.health.application.inpatient.doctorstation.chinesemedicine.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * IF-DOR-34：中药处方方头展示 DTO（按方聚合）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "中药处方方头")
public class TcmRxHeaderDto {

    /** 前端方标识（自 content_json 解析）。 */
    @Schema(description = "前端方标识")
    private String prescriptionGroupKey;

    /** 处方号（已签发时有值）。 */
    @Schema(description = "处方号")
    private String prescriptionNo;

    /** 方头长临医嘱类型（{@code therapy_enum}）。 */
    @Schema(description = "方头长临医嘱类型")
    private String therapyEnum;

    /** 绑定的中医病证组号。 */
    @Schema(description = "绑定的中医病证组号")
    private String tcmSyndromeGroup;

    /** 中药付数。 */
    @Schema(description = "中药付数")
    private BigDecimal chineseHerbsDoseQuantity;

    /** 代煎标志。 */
    @Schema(description = "代煎标志")
    private Boolean sufferingFlag;

    /** 用法编码。 */
    @Schema(description = "用法编码")
    private String methodCode;

    /** 用法展示名（tcm_method_code 优先）。 */
    @Schema(description = "用法展示名")
    private String methodName;

    /** 用药频次编码（方头口径，取组内首行 {@code rate_code}）。 */
    @Schema(description = "用药频次编码")
    private String rateCode;

    /** 用药频次展示名（由 {@code FrequencyOptionLoader} 标签映射）。 */
    @Schema(description = "用药频次展示名")
    private String rateName;

    /** 用药天数。 */
    @Schema(description = "用药天数")
    private Integer days;

    /** 煎煮法编码。 */
    @Schema(description = "煎煮法编码")
    private String decoctionMethodCode;

    /** 备注。 */
    @Schema(description = "备注")
    private String remark;

    /** 方状态（取行内最高态）。 */
    @Schema(description = "方状态")
    private String statusEnum;

    /** 方状态展示码（退费态优先于请求态，与医嘱列表 displayStatusEnum 口径一致）。 */
    @Schema(description = "方状态展示码")
    private String displayStatusEnum;

    /** 方状态展示文案（如待退费/已退费/已完成）。 */
    @Schema(description = "方状态展示文案")
    private String displayStatusEnumText;

    /** 方内明细行。 */
    @Schema(description = "方内明细行")
    private List<TcmRxLineDto> lines = new ArrayList<>();
}
