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
 * 门诊医生站「申请退费」应用编排用到的非枚举业务常量（与《代码生成通用规范》B.10.3 对齐）。
 */
public interface DoctorStationRefundApplicationConstants {

    /**
     * 作废药品发放时写入的未执行原因码：门诊退费申请路径触发的未发药作废。
     */
    String MEDICATION_DISPENSE_NOT_PERFORMED_REASON_OUTPATIENT_REFUND_APP = "OUTPATIENT_REFUND_APPLICATION";

    /**
     * 作废耗材发放时写入的未执行原因码：门诊退费申请路径触发的未发耗作废。
     */
    String DEVICE_DISPENSE_NOT_PERFORMED_REASON_OUTPATIENT_REFUND_APP = "OUTPATIENT_REFUND_APPLICATION";

    /**
     * 申请退耗材命令中的原因文本：医生站门诊退费申请。
     */
    String DEVICE_ORDER_REFUND_REASON_OUTPATIENT_REFUND_APP = "门诊医生站退费申请";

    /**
     * 取消诊疗服务申请时的原因文本：门诊退费申请。
     */
    String SERVICE_REQUEST_CANCEL_REASON_OUTPATIENT_REFUND_APP = "门诊退费申请";

    /**
     * 停止诊疗服务申请时的原因文本：门诊退费申请。
     */
    String SERVICE_REQUEST_STOP_REASON_OUTPATIENT_REFUND_APP = "门诊退费申请";

    /**
     * IF-DRA-02 拒绝语义码：耗材已执行（与 AS EX-DRA-201 对齐，供前端稳定分支）。
     */
    String REJECT_REASON_CODE_EX_DRA_201 = "EX-DRA-201";
}
