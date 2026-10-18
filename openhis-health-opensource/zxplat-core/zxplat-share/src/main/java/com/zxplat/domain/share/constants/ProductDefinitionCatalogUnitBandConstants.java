/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 产品定义目录/选品「单位槽」语义编码（与 API 中 {@code bandKind} 字符串身份对齐，B.10.8.1）。
 *
 * <p>由应用层 {@code ProductDefinitionCatalogUnitBandsAssembler} 装配。</p>
 */
public interface ProductDefinitionCatalogUnitBandConstants {

/** ProductDefinitionCatalogUnitBand 共享常量：purchase，值 {@code PURCHASE}。 */
    String BAND_PURCHASE = "PURCHASE";

    /** 拆零（最小包装）单位槽。 */
    String BAND_SPLIT = "SPLIT";

    /**
     * 包装单位槽（医生站选品、进货选品等与读模型 {@code package_unit_code} 列同源装配）。
     */
    String BAND_PACKAGE = "PACKAGE";

/** ProductDefinitionCatalogUnitBand 共享常量：sales，值 {@code SALES}。 */
    String BAND_SALES = "SALES";

/** ProductDefinitionCatalogUnitBand 共享常量：stock，值 {@code STOCK}。 */
    String BAND_STOCK = "STOCK";

/** ProductDefinitionCatalogUnitBand 共享常量：dose，值 {@code DOSE}。 */
    String BAND_DOSE = "DOSE";

    /**
     * 追溯码贴码单位槽（可游离于五类业务单位；同源读模型 {@code trace_unit_code}/{@code trace_unit_ratio}）。
     *
     * <p>仅供扫码应扫数换算，不参与进销存开单与定价槽。</p>
     */
    String BAND_TRACE = "TRACE";
}
