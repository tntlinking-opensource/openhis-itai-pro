/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

import com.openhis.health.domain.share.enums.DispenseStatus;

import java.util.List;

/**
 * 医嘱开立目录（IF-DOR-11）与库存预减读侧对齐的共享常量。
 *
 * <p>口径对齐 openhis {@code AdviceUtils#subtractInventory}：候选库房下全局汇总
 * {@code DRAFT}/{@code PREPARATION} 占用，<strong>不限就诊/患者</strong>；读侧须 {@code now &lt; limitTime} 才计入。</p>
 */
public interface DoctorStationOrderCatalogConstants {

    /** 草稿发放预减有效窗口（小时）；过期后读侧不再计入占用（openhis 签发草稿 +2h）。 */
    int DISPENSE_RESERVE_DRAFT_LIMIT_HOURS = 2;

    /** 待发（PREPARATION）预减有效窗口（小时）；openhis 执行/收费推进待发 +12h。 */
    int DISPENSE_RESERVE_PREPARATION_LIMIT_HOURS = 12;

    /**
     * 纳入库存预减统计的发放状态集合（药品 JPA 枚举列、耗材侧与 {@link DispenseStatus#getCode()} 对照）。
     *
     * <p>仅 {@link DispenseStatus#DRAFT}、{@link DispenseStatus#PREPARATION}；已配药及以后由实发/作废链路释放。</p>
     */
    List<DispenseStatus> DISPENSE_OCCUPANCY_STATUSES_FOR_ORDER_CATALOG = List.of(
        DispenseStatus.DRAFT,
        DispenseStatus.PREPARATION);
}
