/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

import com.zxplat.domain.share.enums.DispenseStatus;

import java.util.List;

/**
 * 医嘱开立目录（IF-DOR-11）与签发 FIFO 选批预减相关的共享常量。
 *
 * <p><strong>目录「还可开」</strong>：候选库房下仅汇总 {@code DRAFT}/{@code PREPARATION}
 * （{@link #DISPENSE_OCCUPANCY_STATUSES_FOR_ORDER_CATALOG}），<strong>不限就诊/患者</strong>。</p>
 * <p><strong>签发 FIFO 选批</strong>：在目录占用之上可另叠住院 {@code SUMMARIZED}
 * （{@link #DISPENSE_OCCUPANCY_STATUSES_FOR_FIFO_INPATIENT_SUMMARY_EXTRA}），不进入目录读侧。</p>
 *
 * <ul>
 *   <li><strong>药品</strong>：读侧须 {@code now &lt; limitTime} 才计入（草稿约 2h、待发约 24h，见下列小时常量）。</li>
 *   <li><strong>耗材</strong>：读侧<strong>不</strong>按 {@code limitTime} 过期；待发一直预占至执行或取消/签退释放。</li>
 * </ul>
 */
public interface DoctorStationOrderCatalogConstants {

    /**
     * 草稿发放 {@code limit_time} 落库偏移（小时）。
     *
     * <p>药品目录预减：过期后读侧不再计入占用。耗材仍写该字段作审计/兼容，但预减读侧忽略过期。</p>
     */
    int DISPENSE_RESERVE_DRAFT_LIMIT_HOURS = 2;

    /**
     * 待发（PREPARATION）{@code limit_time} 落库偏移（小时）。
     *
     * <p>药品目录预减与发药门控：约 24h 后过期不再占库存。耗材预减读侧忽略该窗，须执行或取消才释放。</p>
     */
    int DISPENSE_RESERVE_PREPARATION_LIMIT_HOURS = 24;

    /**
     * 纳入库存预减统计的发放状态集合（药品 JPA 枚举列、耗材侧与 {@link DispenseStatus#getCode()} 对照）。
     *
     * <p>仅 {@link DispenseStatus#DRAFT}、{@link DispenseStatus#PREPARATION}；已配药及以后由实发/作废链路释放。</p>
     */
    List<DispenseStatus> DISPENSE_OCCUPANCY_STATUSES_FOR_ORDER_CATALOG = List.of(
        DispenseStatus.DRAFT,
        DispenseStatus.PREPARATION);

    /**
     * FIFO 签发选批额外并入的占用状态：住院汇总待核发。
     *
     * <p>不进入 IF-DOR-11 目录「还可开」预减（仍仅 {@link #DISPENSE_OCCUPANCY_STATUSES_FOR_ORDER_CATALOG}）；
     * 仅在签发/FIFO 选批时叠加，与发药门禁对 {@link DispenseStatus#SUMMARIZED} 的预占对齐，
     * 避免门诊/其它签发抢已汇总未核发的批号。</p>
     */
    List<DispenseStatus> DISPENSE_OCCUPANCY_STATUSES_FOR_FIFO_INPATIENT_SUMMARY_EXTRA = List.of(
        DispenseStatus.SUMMARIZED);
}
