/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.medicaltech.charging.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.application.shared.pagination.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 分页查询医技耗材选品请求（AS-MT-CHG-001 IF-MT-CHG-01）。
 *
 * <p>品名/编码关键字走父类 {@link AbstractHealthPageRequest} 的 {@code keyword}，
 * 经 {@code InventoryItemProductSelectionPageLoader} 预解析后过滤库存行。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "分页查询医技耗材选品请求")
public class PageMedicalTechDeviceCatalogRequest extends AbstractHealthPageRequest {

    /** 住院就诊主键。 */
    @Schema(description = "就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 指定本科耗材房；空则默认房（全部允许库房并集分页）。 */
    @Schema(description = "耗材房主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long locationId;

    /**
     * 选品定价标记；医技固定耗材口径（对齐护士 BR-IP-PRC-002），可空。
     */
    @Schema(description = "定价标记（固定耗材）")
    private Integer pricingFlag;
}
