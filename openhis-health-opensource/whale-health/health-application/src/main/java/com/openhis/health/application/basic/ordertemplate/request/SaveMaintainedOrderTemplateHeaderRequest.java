/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.ordertemplate.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 维护端保存组套模板头请求（IF-BAS-OTL-04）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "维护端保存组套模板头请求", requiredProperties = {"bindingEnum", "sceneEnum", "templateKindEnum", "name"})
public class SaveMaintainedOrderTemplateHeaderRequest {

    @Schema(description = "组套模板头 id")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 组套模板头 id。 */
    private Long orderTemplateId;

    @Schema(description = "绑定类型 code", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "绑定类型不能为空")
    private String bindingEnum;

    /** 归属对象 id。 */
    @Schema(description = "归属对象 id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long objectId;

    /** 适用场景 code。 */
    @Schema(description = "适用场景 code", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "适用场景不能为空")
    private String sceneEnum;

    /** 组套种类 code。 */
    @Schema(description = "组套种类 code", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "组套种类不能为空")
    private String templateKindEnum;

    /** 模板编码。 */
    @Schema(description = "模板编码")
    private String templateCode;

    /** 模板名称。 */
    @Schema(description = "模板名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "模板名称不能为空")
    private String name;

    /** 描述。 */
    @Schema(description = "描述")
    private String description;

    /** 排序权重。 */
    @Schema(description = "排序权重")
    private Integer sortWeight;

    /** 备注。 */
    @Schema(description = "备注")
    private String remark;

    /** TCM 默认付数。 */
    @Schema(description = "TCM 默认付数")
    private BigDecimal defaultChineseHerbsDoseQuantity;

    /** TCM 默认代煎。 */
    @Schema(description = "TCM 默认代煎")
    private Boolean defaultSufferingFlag;
}
