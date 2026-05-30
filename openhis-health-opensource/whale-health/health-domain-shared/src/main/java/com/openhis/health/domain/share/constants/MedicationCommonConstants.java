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

public interface MedicationCommonConstants {

    /**
     * 发放单号前缀。
     */
    String DISPENSE_BUS_NO_PREFIX = "DISP-";

    /**
     * 申请单号前缀。
     */
    String REQUEST_BUS_NO_PREFIX = "REQ-";

    /**
     * 汇总单号前缀。
     */
    String SUMMARY_NO_PREFIX = "SUM-";

    /**
     * 草药单位码（按次）。
     */
    String HERBAL_UNIT_CODE = "PAY";

    /**
     * 草药给药途径码。
     */
    String HERBAL_METHOD_CODE = "HERBAL";

    /**
     * 草药给药频次码（每日一次）。
     */
    String HERBAL_RATE_CODE = "QD";

    /**
     * 取药科室模板（ServiceLocationTemplate）未命中目录分类时的回落编码，与医生站 FIFO 装配口径一致。
     */
    String DEFAULT_SERVICE_LOCATION_DISTRIBUTION_CATEGORY_CODE = "DEFAULT";

    /**
     * 门诊医嘱/发药工作站列表：布尔「是」列固定展示（皮试/输液等标志列与人读一致）。
     */
    String WORKSTATION_BOOLEAN_DISPLAY_YES = "是";

    /**
     * 门诊医嘱/发药工作站列表：布尔「否」列固定展示。
     */
    String WORKSTATION_BOOLEAN_DISPLAY_NO = "否";

    /**
     * 门诊医嘱/发药工作站列表：{@code tcmFlag=true} 时的草药复方类别展示（与 {@link #TCM_CLASS_DISPLAY_WESTERN_AND_PATENT} 成对）。
     */
    String TCM_CLASS_DISPLAY_TCM = "中药";

    /**
     * 门诊医嘱/发药工作站列表：{@code tcmFlag=false} 时的类别展示（西药与中成药口径；勿单独写作「西药」以免与中成药混淆）。
     */
    String TCM_CLASS_DISPLAY_WESTERN_AND_PATENT = "西药中成药";

    /**
     * 门诊药房退药（AS-OP-PHA-002）：确认退药之操作类型码（与 AS-SDD {@code confirmType} 对齐）。
     */
    String OUTPATIENT_PHARMACY_RETURN_CONFIRM_TYPE_CONFIRM = "CONFIRM";

    /**
     * 门诊药房退药：拒绝退药之操作类型码。
     */
    String OUTPATIENT_PHARMACY_RETURN_CONFIRM_TYPE_DECLINE = "DECLINE";

    /**
     * 门诊药房退药：确认退药成功时返回给前端的提示文案（AS-OP-PHA-002 IF-PHA-02-04 Response.message）。
     */
    String OUTPATIENT_PHARMACY_RETURN_CONFIRM_SUCCESS_MESSAGE = "退药确认成功";

    /**
     * 门诊药房退药：拒绝退药成功时返回给前端的提示文案。
     */
    String OUTPATIENT_PHARMACY_RETURN_DECLINE_SUCCESS_MESSAGE = "退药已拒绝";

    // 表名常量已集中到 TableNameConstants。
}
