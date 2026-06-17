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

package com.openhis.health.domain.share.constants;

/**
 * 产品定义目录/选品「单位槽」语义编码（与 API 中 {@code bandKind} 字符串身份对齐，B.10.8.1）。
 *
 * <p>由应用层 {@code ProductDefinitionCatalogUnitBandsAssembler} 装配。</p>
 */
public interface ProductDefinitionCatalogUnitBandConstants {

    /** 采购单位槽。 */
    String BAND_PURCHASE = "PURCHASE";

    /** 拆零（最小包装）单位槽。 */
    String BAND_SPLIT = "SPLIT";

    /**
     * 包装单位槽（医生站选品、进货选品等与读模型 {@code package_unit_code} 列同源装配）。
     */
    String BAND_PACKAGE = "PACKAGE";

    /** @deprecated 已由 {@link #BAND_PACKAGE} 替代。 */
    @Deprecated
    String BAND_SALES = "SALES";

    /** 库存单位槽。 */
    String BAND_STOCK = "STOCK";

    /** 剂量单位槽。 */
    String BAND_DOSE = "DOSE";
}
