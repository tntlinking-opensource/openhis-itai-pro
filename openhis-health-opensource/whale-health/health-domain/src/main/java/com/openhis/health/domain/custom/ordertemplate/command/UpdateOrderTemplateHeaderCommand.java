/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.ordertemplate.command;

import lombok.Getter;
import lombok.Setter;
import com.openhis.health.domain.share.enums.OrderTemplateScene;

import java.math.BigDecimal;

/**
 * 更新组套模板头命令（仅覆盖非空字段）。
 */
@Getter
@Setter
public class UpdateOrderTemplateHeaderCommand {
    /**
     * 组套模板标识。
     */
    private Long orderTemplateId;
    /**
     * 适用场景枚举值（门诊/住院/通用）。
     */
    private OrderTemplateScene sceneEnum;
    /**
     * 组套种类（GENERAL/TCM/PRICING）。
     */
    private String templateKindEnum;
    /**
     * 模板编码（可空；非空时按归属键范围唯一）。
     */
    private String templateCode;
    /**
     * 模板名称。
     */
    private String name;
    /**
     * 描述（可空）。
     */
    private String description;
    /**
     * 列表排序权重（同层级内：数值越小越靠前；默认 0）。
     */
    private Integer sortWeight;
    /**
     * 状态枚举值（草稿/启用/停用）。
     */
    private String statusEnum;
    /**
     * 备注（可空）。
     */
    private String remark;
    /**
     * TCM 组套默认中药付数。
     */
    private BigDecimal defaultChineseHerbsDoseQuantity;
    /**
     * TCM 组套默认代煎标志。
     */
    private Boolean defaultSufferingFlag;
    /**
     * 操作人标识。
     */
    private Long operatorId;
}
