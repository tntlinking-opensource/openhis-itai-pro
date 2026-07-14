/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * 住院药房汇总发药（AS-IP-PHA-002）编排消息码与批上限。
 *
 * <p>与 {@code AS-IP-PHA-002} §5 异常表及 §7 NFR 对齐；应用层引用本接口，禁止在 {@code health-application} 自建 constants 包。</p>
 */
public interface InpatientPharmacySummaryDispenseConstants {

    /** 未登录或无权操作该药房（EX-IPS-101）。 */
    String EX_IPS_101 = "EX-IPS-101";

    /** 列表/详情查询失败（EX-IPS-102）。 */
    String EX_IPS_102 = "EX-IPS-102";

    /** 汇总单不存在或状态已变（EX-IPS-103）。 */
    String EX_IPS_103 = "EX-IPS-103";

    /** 重复核发或状态非 APPROVAL（EX-IPS-201）。 */
    String EX_IPS_201 = "EX-IPS-201";

    /** 驳回状态不允许（EX-IPS-202）。 */
    String EX_IPS_202 = "EX-IPS-202";

    /** 库存停供或不足（EX-IPS-203）。 */
    String EX_IPS_203 = "EX-IPS-203";

    /** 追溯码或调配药师（EX-IPS-204）。 */
    String EX_IPS_204 = "EX-IPS-204";

    /** 批量校验或域写失败（EX-IPS-205）。 */
    String EX_IPS_205 = "EX-IPS-205";

    /** 单次批量核发/驳回汇总单数量上限（AS §7）。 */
    int SUMMARY_DISPENSE_BATCH_MAX = 50;

/** InpatientPharmacySummaryDispense 共享常量：success message，值 {@code 汇总核发成功}。 */
    String DISPENSE_SUCCESS_MESSAGE = "汇总核发成功";

/** InpatientPharmacySummaryDispense 共享常量：success message，值 {@code 汇总驳回成功}。 */
    String REJECT_SUCCESS_MESSAGE = "汇总驳回成功";
}
