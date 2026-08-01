/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * 门诊挂号应用编排共享常量（对齐 AS-OP-FIN-001 IF-REG-09/10）。
 *
 * <p>退号收费门禁（BR-REG-015）仅保留非挂号锚点账单三态原因码；药耗收口由 AS-OP-FIN-003 IF-REF-04 承担。</p>
 */
public interface OutpatientRegistrationConstants {

    /** 退号校验通过原因码。 */
    String CANCEL_REASON_CODE_OK = "OK";

    /** 退号校验：存在待收费明细（非挂号锚点）。 */
    String CANCEL_REASON_CODE_PENDING_CHARGE = "CANCEL-BIZ-410";

    /** 退号校验：存在已收费未全退账单（非挂号锚点）。 */
    String CANCEL_REASON_CODE_SETTLED_CHARGE = "CANCEL-BIZ-411";

    /** 退号校验：存在退费处理中账单（非挂号锚点）。 */
    String CANCEL_REASON_CODE_REFUNDING_CHARGE = "CANCEL-BIZ-412";

    /** 存在待收费项目，须医生撤回已签发医嘱后再退号。 */
    String CANCEL_MESSAGE_PENDING_CHARGE =
        "存在待收费项目，请医生撤回已签发医嘱后再退号";

    /** 存在已收费账单，须先完成门诊退费后再退号。 */
    String CANCEL_MESSAGE_SETTLED_CHARGE =
        "存在已收费账单，请先完成门诊退费后再退号";

    /** 存在退费处理中账单，须待退费完成后再退号。 */
    String CANCEL_MESSAGE_REFUNDING_CHARGE =
        "存在退费处理中的账单，请待退费完成后再退号";

    /**
     * 挂号窗口列表：退号完成行状态展示文案。
     *
     * <p>库内状态仍为 {@code CANCELLED}；窗口不展示枚举默认「已取消」，以免与未收费取消混淆。</p>
     */
    String CLINIC_REGISTER_STATUS_TEXT_REFUNDED = "退号";
}
