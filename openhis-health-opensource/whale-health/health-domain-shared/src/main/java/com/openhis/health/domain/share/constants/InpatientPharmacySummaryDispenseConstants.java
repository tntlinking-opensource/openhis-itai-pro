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

    /** 核发成功提示。 */
    String DISPENSE_SUCCESS_MESSAGE = "汇总核发成功";

    /** 驳回成功提示。 */
    String REJECT_SUCCESS_MESSAGE = "汇总驳回成功";
}
