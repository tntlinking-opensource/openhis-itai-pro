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
 * 住院账户余额展示口径（AS-IP-FIN-002 §1.3、AS-IP-FIN-003 队列/摘要读侧）。
 */
public interface InpatientAccountBalanceDisplayConstants {

    /** 常态：卡片/队列仅展示单一账户余额文案。 */
    String DISPLAY_MODE_NORMAL = "NORMAL";

    /** 反结后待重新结算（保留枚举；展示仍走 NORMAL 单一余额文案）。 */
    String DISPLAY_MODE_REVERSED_PENDING_RESETTLE = "REVERSED_PENDING_RESETTLE";

    /** 反结待再结副文案（保留常量；当前产品不在卡片展示）。 */
    String SUB_TEXT_REVERSED_PENDING_RESETTLE = "上次结算已撤销，请重新结算";

    /** 已结清主文案（保留常量；当前产品统一输出余额数值）。 */
    String SUMMARY_TEXT_SETTLED = "已结清";

    /** 金额比较/展示 scale，与 {@link InpatientPrepayConstants#PREPAY_AMOUNT_COMPARE_SCALE} 一致。 */
    int AMOUNT_DISPLAY_SCALE = InpatientPrepayConstants.PREPAY_AMOUNT_COMPARE_SCALE;

    /** 视为零的金额绝对值上限（2 位小数口径）。 */
    String ZERO_AMOUNT_EPSILON = "0.005";
}
