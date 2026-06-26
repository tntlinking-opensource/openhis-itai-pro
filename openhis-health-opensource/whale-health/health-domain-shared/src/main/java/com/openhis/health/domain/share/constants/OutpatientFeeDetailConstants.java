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
 * 门诊费用明细读侧语义码（AS-OP-FIN-005 IF-OP-FDT-01～03；**BR-OP-FDT-006/009/013**）。
 */
public interface OutpatientFeeDetailConstants {

    /** 收退 Tab：全部。 */
    String TRADE_DIRECTION_ALL = "ALL";

    /** 收退 Tab：仅收费 payment。 */
    String TRADE_DIRECTION_CHARGE = "CHARGE";

    /** 收退 Tab：仅退费 payment。 */
    String TRADE_DIRECTION_REFUND = "REFUND";

    /** 收退 Tab「全部」展示文案。 */
    String TRADE_DIRECTION_ALL_LABEL = "全部";

    /** 收退 Tab「收费」展示文案。 */
    String TRADE_DIRECTION_CHARGE_LABEL = "收费";

    /** 收退 Tab「退费」展示文案。 */
    String TRADE_DIRECTION_REFUND_LABEL = "退费";

    /** 费用大类：药品。 */
    String FEE_CATEGORY_MEDICATION = "MEDICATION";

    /** 费用大类：诊疗。 */
    String FEE_CATEGORY_ACTIVITY = "ACTIVITY";

    /** 费用大类：耗材。 */
    String FEE_CATEGORY_DEVICE = "DEVICE";

    /** 费用大类：挂号。 */
    String FEE_CATEGORY_REGISTER = "REGISTER";

    /** 费用大类「药品」展示文案。 */
    String FEE_CATEGORY_MEDICATION_LABEL = "药品";

    /** 费用大类「诊疗」展示文案。 */
    String FEE_CATEGORY_ACTIVITY_LABEL = "诊疗";

    /** 费用大类「耗材」展示文案。 */
    String FEE_CATEGORY_DEVICE_LABEL = "耗材";

    /** 费用大类「挂号」展示文案。 */
    String FEE_CATEGORY_REGISTER_LABEL = "挂号";

    /** 默认分页大小（按就诊块）。 */
    int DEFAULT_PAGE_SIZE = 10;

    /**
     * 全量扫描就诊块时的分页尺寸（与 {@link com.openhis.health.domain.share.query.AbstractPageQuery#getBoundedPageSize()} 上限一致）。
     */
    int ENCOUNTER_BLOCK_SCAN_PAGE_SIZE = 1000;

    /** 收费员下拉预加载条数上限。 */
    int CASHIER_OPTION_PAGE_SIZE = 500;

    /** 导出行数上限（明细平铺行）。 */
    int EXPORT_MAX_ROWS = 50000;

    /**
     * {@code fin_payment_reconciliation.charge_item_ids} 历史 varchar(255) 列长上限；
     * 快照字符串达到该长度时视为可能被截断，读侧须补全同笔结算漏挂的已结行。
     */
    int CHARGE_ITEM_IDS_VARCHAR_LEGACY_MAX_LENGTH = 255;

    /** 导出 xlsx MIME 类型。 */
    String EXPORT_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** 导出工作表名称。 */
    String EXPORT_SHEET_NAME = "门诊费用明细";

    /** 未登录或主数据加载失败（IF-OP-FDT-01～03）。 */
    String EX_OP_FDT_101 = "EX-OP-FDT-101";

    /** 日期区间或分页/Tab 参数非法（IF-OP-FDT-02/03）。 */
    String EX_OP_FDT_102 = "EX-OP-FDT-102";

    /** 明细缺产品/分类或 service_table 无法归类（IF-OP-FDT-02/03）。 */
    String EX_OP_FDT_103 = "EX-OP-FDT-103";

    /** 查询执行失败（IF-OP-FDT-02）。 */
    String EX_OP_FDT_104 = "EX-OP-FDT-104";

    /** 导出失败（IF-OP-FDT-03）。 */
    String EX_OP_FDT_105 = "EX-OP-FDT-105";

    /** 导出无数据（可选拒绝策略；本期允许空表头导出）。 */
    String EX_OP_FDT_201 = "EX-OP-FDT-201";
}
