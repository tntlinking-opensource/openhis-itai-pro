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

package com.openhis.health.application.common.dto.product;

import com.openhis.health.domain.share.constants.ProductDefinitionCatalogUnitBandConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 *
 * <p>与 {@code med_medication_definition} / 耗材定义五类单位读模型列同源装配；拆零槽 {@link #ratioToSplit} 恒为 1，
 * 其余槽为相对拆零的倍数（与读模型 {@code *_unit_ratio} 一致）。槽语义 {@link #bandKind} 须为
 * {@link ProductDefinitionCatalogUnitBandConstants} 中 {@code BAND_*} 字面量。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "产品定义目录单位槽（采购/拆零/包装/库存/剂量等）")
public class ProductDefinitionCatalogUnitBandDto {

    /**
     * 槽位语义编码，与 {@link ProductDefinitionCatalogUnitBandConstants} 中 {@code BAND_*} 常量一致。
     */
    @Schema(description = "单位槽语义编码")
    private String bandKind;

    /** 该槽位单位编码（术语侧持久化码）。 */
    @Schema(description = "单位编码")
    private String unitCode;

    /** 该槽位单位展示名（读模型字典文本列）。 */
    @Schema(description = "单位展示名")
    private String unitName;

    /**
     * 相对拆零单位的换算倍数；拆零槽为 1。
     */
    @Schema(description = "相对拆零单位的倍数")
    private BigDecimal ratioToSplit;

    /**
     * 该槽位单位单价（元），由目录基准单价按 {@link #ratioToSplit} 等比换算；未做单价装配时为 {@code null}。
     */
    @Schema(description = "该单位单价（元）")
    private BigDecimal unitPrice;
}
