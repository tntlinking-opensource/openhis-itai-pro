/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.notice;

import com.zxplat.plugin.chs.domain.notice.model.HisUnchargedFeeLine;
import com.zxplat.plugin.chs.domain.notice.port.CatalogNoticeHisChargePort;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 未收费费用明细同步端口实现（当前为明确 stub，禁止静默误以为已接通）。
 * <p>
 * <b>缺口</b>：HIS 费用明细表尚无可靠独立 {@code chrgitm_lv} 列与「未收费」状态真源对齐。
 * <b>后续表锚点</b>：以财务域 {@code adm_charge_item}（或等价明细表）为准，按
 * productIds / yb_no / projectCode 筛 DRAFT/PLANNED 等未收费行；已收费禁止返回/回写。
 * <b>现阶段行为</b>：{@link #listUnchargedFeeLines} 恒返回空列表；
 * {@link #updateUnchargedFeeLineChrgitmLv} 为空操作；apply 主路径可验收，
 * {@code apply_log.affected_refs_json} 写入 {@code []}。
 * </p>
 */
@Component
public class CatalogNoticeHisChargePortImpl implements CatalogNoticeHisChargePort {

    /**
     * 列出可同步甲乙类的未收费费用明细。
     * <p>Stub：恒返回空；接通后按 productIds / yb_no / project 查未收费行，已收费不得返回。</p>
     *
     * @param tenantId    租户
     * @param projectCode 项目码（可空）
     * @param hilistCode  国家码（可空）
     * @param productIds  院内产品主键集合（可空）
     * @return 当前恒为空列表
     */
    @Override
    public List<HisUnchargedFeeLine> listUnchargedFeeLines(
            Long tenantId,
            String projectCode,
            String hilistCode,
            Collection<Long> productIds) {
        // stub：表锚点未接通前不回查 HIS 费用明细，避免误同步
        return Collections.emptyList();
    }

    /**
     * 回写未收费明细的收费项目等级。
     * <p>Stub：无独立明细 lv 字段前不改表；接通后须先校验仍为未收费状态。</p>
     *
     * @param itemLineId 费用明细行主键
     * @param chrgitmLv  目标甲乙类
     */
    @Override
    public void updateUnchargedFeeLineChrgitmLv(Long itemLineId, String chrgitmLv) {
        // stub：无独立明细 lv 字段前不改表
    }
}
