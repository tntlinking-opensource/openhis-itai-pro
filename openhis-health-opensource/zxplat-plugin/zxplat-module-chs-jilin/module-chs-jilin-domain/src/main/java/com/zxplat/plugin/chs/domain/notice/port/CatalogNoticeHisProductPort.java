/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.notice.port;

import com.zxplat.plugin.chs.domain.notice.model.HisMappedProduct;
import com.zxplat.plugin.chs.domain.notice.model.HisProductLvChange;

import java.util.List;

/**
 * 院内药品/诊疗/耗材定义 chrgitm_lv 回写端口（领域定义，bridge 实现）。
 */
public interface CatalogNoticeHisProductPort {

    /**
     * 按 projectCode / hilistCode / yb_no 查找院内 MD/AD/DD 并更新 {@code chrgitm_lv}。
     * <p>匹配优先 yb_no=hilistCode，其次 yb_no=projectCode，再次 busNo=projectCode。</p>
     *
     * @param tenantId    租户（预留过滤；当前 HIS 定义多为机构级）
     * @param projectCode 物价/项目码（可为 null）
     * @param hilistCode  国家目录码（可为 null）
     * @param chrgitmLv   目标等级（国标 1/2/3 或 01/02/03；写入院内时映射为 yb_type 口径 2/3/4）
     * @return 实际发生变更的列表；无命中返回空列表
     */
    List<HisProductLvChange> updateProductChrgitmLv(
        Long tenantId,
        String projectCode,
        String hilistCode,
        String chrgitmLv);

    /**
     * 按 hilist/project 回查院内产品当前 {@code chrgitm_lv}（取首个命中）。
     *
     * @return 国标等级字符串；无命中返回 null
     */
    String findProductChrgitmLv(Long tenantId, String hilistCode, String projectCode);

    /**
     * 列出已启用且需走医保的院内目录项（诊疗套餐父项除外），并带上 SALE 售价。
     *
     * @return 诊疗 / 药品 / 耗材；无则空列表
     */
    List<HisMappedProduct> listActiveInsuranceProducts();
}
