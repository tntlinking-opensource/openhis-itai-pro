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

package com.openhis.health.domain.custom.priceadjustment.support;

import com.openhis.health.domain.share.constants.CustomConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;

/**
 * 调价项目物理表口径判断（药品耗材批号调价 vs 诊疗一对一调价）。
 *
 * <p>仅基于 {@code item_table} 区分展示与校验分支，不引入额外存储结构。</p>
 */
public final class PriceAdjustmentItemTableSupport {

    private PriceAdjustmentItemTableSupport() {
    }

    /**
     * 是否诊疗类项目表（一对一子表、仅调前/目标售价）。
     *
     * @param itemTable 项目物理表
     * @return 诊疗活动或医疗服务定义表时为 true
     */
    public static boolean isClinicalItemTable(String itemTable) {
        if (itemTable == null) {
            return false;
        }
        String table = itemTable.trim();
        if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(table)) {
            return true;
        }
        if (TableNameConstants.ADM_HEALTHCARE_SERVICE.equals(table)) {
            return true;
        }
        return false;
    }

    /**
     * 是否药品耗材物流类项目表（批号多行子表、进价与售价双侧可选）。
     *
     * @param itemTable 项目物理表
     * @return 药品或耗材定义表时为 true
     */
    public static boolean isLogisticsItemTable(String itemTable) {
        if (itemTable == null) {
            return false;
        }
        String table = itemTable.trim();
        if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(table)) {
            return true;
        }
        if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(table)) {
            return true;
        }
        return false;
    }

    /**
     * 是否为调价明细/制单支持的合法项目物理表（四档 item_table）。
     *
     * @param itemTable 项目物理表
     * @return 药品/耗材/诊疗/医疗服务定义表时为 true
     */
    public static boolean isSupportedItemTable(String itemTable) {
        if (isClinicalItemTable(itemTable)) {
            return true;
        }
        if (isLogisticsItemTable(itemTable)) {
            return true;
        }
        return false;
    }

    /**
     * 解析读侧展示口径。
     *
     * @param itemTable 项目物理表
     * @return {@link CustomConstants.PriceAdjustment#ADJUSTMENT_STYLE_CLINICAL} 或 logistics；未知表返回 null
     */
    public static String resolveAdjustmentStyle(String itemTable) {
        if (isClinicalItemTable(itemTable)) {
            return CustomConstants.PriceAdjustment.ADJUSTMENT_STYLE_CLINICAL;
        }
        if (isLogisticsItemTable(itemTable)) {
            return CustomConstants.PriceAdjustment.ADJUSTMENT_STYLE_LOGISTICS;
        }
        return null;
    }
}
