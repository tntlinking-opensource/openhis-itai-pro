/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.ordertemplate.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 组套模板头列表/详情行 DTO。
 */
@Data
@Accessors(chain = true)
@Schema(description = "组套模板头行")
public class OrderTemplateHeaderRowDto {

    @Schema(description = "组套模板头 id")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 模板名称。 */
    private Long orderTemplateId;

    @Schema(description = "模板名称")
    private String name;

    /** 模板编码。 */
    @Schema(description = "模板编码")
    private String templateCode;

    /** 绑定类型 code。 */
    @Schema(description = "绑定类型 code")
    private String bindingEnum;

    /** 绑定类型展示名。 */
    @Schema(description = "绑定类型展示名")
    private String bindingEnumText;

    /**
     * 归属对象 id。
     *
     * <p>语义由 {@link #bindingEnum} 决定：PERSONAL → 医务人员 id；DEPARTMENT / HOSPITAL / AGREEMENT → 组织 id。</p>
     */
    @Schema(description = "归属对象 id（个人=医务人员，科室/全院/协定=组织）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long objectId;

    /** 归属对象展示名（个人=姓名，科室/全院/协定=组织名称）。 */
    @Schema(description = "归属对象展示名")
    private String objectName;

    /** 适用场景 code。 */
    @Schema(description = "适用场景 code")
    private String sceneEnum;

    /** 适用场景展示名。 */
    @Schema(description = "适用场景展示名")
    private String sceneEnumText;

    /** 组套种类 code。 */
    @Schema(description = "组套种类 code")
    private String templateKindEnum;

    /** 组套种类展示名。 */
    @Schema(description = "组套种类展示名")
    private String templateKindEnumText;

    /** 模板来源 code。 */
    @Schema(description = "模板来源 code")
    private String templateSourceEnum;

    /** 模板来源展示名。 */
    @Schema(description = "模板来源展示名")
    private String templateSourceEnumText;

    /** 模板状态 code。 */
    @Schema(description = "模板状态 code")
    private String statusEnum;

    /** 模板状态展示名。 */
    @Schema(description = "模板状态展示名")
    private String statusEnumText;

    /** 排序权重。 */
    @Schema(description = "排序权重")
    private Integer sortWeight;

    /** TCM 默认付数。 */
    @Schema(description = "TCM 默认付数")
    private BigDecimal defaultChineseHerbsDoseQuantity;

    /** TCM 默认代煎。 */
    @Schema(description = "TCM 默认代煎")
    private Boolean defaultSufferingFlag;

    /** 描述。 */
    @Schema(description = "描述")
    private String description;

    /** 备注。 */
    @Schema(description = "备注")
    private String remark;
}
