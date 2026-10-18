/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.chargeitem.query;

import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

/**
 * 住院护士站费用明细查询行（DM-ADM-011 §6.2.15；AS-IP-NUR-006 IF-IP-CDT-02）。
 *
 * <p>双轨：{@code businessDateTime} 为逻辑轴（划价/滚费账单 occurrence，或净剩 Procedure
 * {@code occurrence_time}），供日期过滤与倒序；{@code displayDateTime} 为页面展示轴（医嘱执行优先
 * {@code recorded_time}，无则回落逻辑轴）。</p>
 */
@Getter
@RequiredArgsConstructor
public class InpatientNurseStationCostDetailChargeRow {

    /** 收费明细事实行。 */
    private final ChargeItem chargeItem;

    /** 逻辑业务时间（occurrence；日期区间过滤与明细倒序）。 */
    private final LocalDateTime businessDateTime;

    /**
     * 页面展示时间（医嘱执行 = Procedure.recorded_time；划价/滚费 = 同 businessDateTime）。
     */
    private final LocalDateTime displayDateTime;
}
