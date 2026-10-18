/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.ordertemplate.command;

import lombok.Getter;
import lombok.Setter;
import com.zxplat.domain.share.enums.OrderTemplateScene;

import java.math.BigDecimal;

/**
 * 创建组套模板命令。
 */
@Getter
@Setter
public class CreateOrderTemplateCommand {
    /**
     * 归属对象标识（语义由 bindingEnum 决定）。
     */
    private Long objectId;
    /**
     * 绑定类型枚举值（个人/科室/全院）。
     */
    private String bindingEnum;
    /**
     * 适用场景枚举值（门诊/住院/通用）。
     */
    private OrderTemplateScene sceneEnum;
    /**
     * 组套种类（GENERAL/TCM/PRICING；可空时默认 GENERAL）。
     */
    private String templateKindEnum;
    /**
     * 模板来源（MAINTAINED/FROM_ADVICE；可空时默认 MAINTAINED）。
     */
    private String templateSourceEnum;
    /**
     * 模板编码（可空；非空时按归属键范围唯一）。
     */
    private String templateCode;
    /**
     * 模板名称。
     */
    private String name;
    /**
     * 临床说明/适应症提示等描述（可空）。
     */
    private String description;
    /**
     * 列表排序权重（同层级内：数值越小越靠前；默认 0）。
     */
    private Integer sortWeight;
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
