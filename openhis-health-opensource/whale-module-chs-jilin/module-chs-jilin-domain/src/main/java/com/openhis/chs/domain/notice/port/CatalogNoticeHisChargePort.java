/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.notice.port;

import com.openhis.chs.domain.notice.model.HisUnchargedFeeLine;

import java.util.Collection;
import java.util.List;

/**
 * 院内未收费费用明细 chrgitm_lv 同步端口（领域定义，bridge 实现）。
 * <p>已收费/已结算明细不得更新。</p>
 */
public interface CatalogNoticeHisChargePort {

    /**
     * 查询未收费明细（按产品/yb_no/项目码）。
     *
     * @param tenantId    租户
     * @param projectCode 物价/项目码（可为 null）
     * @param hilistCode  国家目录码（可为 null）
     * @param productIds  已回写的产品主键集合（可为 null/空）
     * @return 未收费明细；无则空列表
     */
    List<HisUnchargedFeeLine> listUnchargedFeeLines(
        Long tenantId,
        String projectCode,
        String hilistCode,
        Collection<Long> productIds);

    /**
     * 同步更新未收费明细的收费项目等级。
     * <p>实现必须校验明细仍为未收费状态；已收费不得更新。</p>
     *
     * @param itemLineId 收费明细行主键
     * @param chrgitmLv  目标等级
     */
    void updateUnchargedFeeLineChrgitmLv(Long itemLineId, String chrgitmLv);
}
