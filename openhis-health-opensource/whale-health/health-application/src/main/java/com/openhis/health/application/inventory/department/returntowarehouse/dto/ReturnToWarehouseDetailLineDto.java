/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.returntowarehouse.dto;

import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogEditorHintsDto;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 科室退库单详情明细行。
 */
@Getter
@Setter
public class ReturnToWarehouseDetailLineDto {

    /** 供应请求明细主键 {@code wkf_supply_request.id}。 */
    private Long id;
    /** 物品来源表名。 */
    private String itemTable;
    /** 物品定义主键。 */
    private Long itemId;
    /** 退库数量（拆零量）。 */
    private BigDecimal itemQuantity;
    /** 计量单位编码。 */
    private String unitCode;
    /** 计量单位展示名（术语/产品槽字典文案；禁止回落裸 unitCode）。 */
    private String unitCodeDictText;
    /** 批号。 */
    private String lotNumber;
    /** 效期起。 */
    private LocalDateTime startTime;
    /** 效期止。 */
    private LocalDateTime endTime;
    /** 追溯码（逗号分隔多码）。 */
    private String traceNo;
    /** 业务发生/制单时间。 */
    private LocalDateTime occurrenceTime;
    /** 退库原因说明。 */
    private String reason;
    /** 备注。 */
    private String remake;
    /** 产品展示名称（读模型富化）。 */
    private String productName;
    /** 规格展示文本（来自产品定义 {@code totalVolume}）。 */
    private String specification;
    /** 生产厂家名称（读模型富化）。 */
    private String manufacturer;
    /** 产品目录编辑提示（单位档/换算等，供前端回显编辑器）。 */
    private ProductDefinitionCatalogEditorHintsDto productCatalog;
}
