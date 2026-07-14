/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * 门诊挂号应用编排共享常量（对齐 AS-OP-FIN-001 IF-REG-09/10）。
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

    /** 退号校验：药品已发未全退或退药中。 */
    String CANCEL_REASON_CODE_MEDICATION_NOT_RETURNED = "CANCEL-BIZ-413";

    /** 退号校验：药房待配药/已配药/待发药在途。 */
    String CANCEL_REASON_CODE_MEDICATION_IN_PHARMACY = "CANCEL-BIZ-415";

    /** 退号校验：耗材已执行未取消。 */
    String CANCEL_REASON_CODE_DEVICE_NOT_CANCELLED = "CANCEL-BIZ-414";

    /** 存在待收费项目，须医生撤回已签发医嘱后再退号。 */
    String CANCEL_MESSAGE_PENDING_CHARGE =
        "存在待收费项目，请医生撤回已签发医嘱后再退号";

    /** 存在已收费账单，须先完成门诊退费后再退号。 */
    String CANCEL_MESSAGE_SETTLED_CHARGE =
        "存在已收费账单，请先完成门诊退费后再退号";

    /** 存在退费处理中账单，须待退费完成后再退号。 */
    String CANCEL_MESSAGE_REFUNDING_CHARGE =
        "存在退费处理中的账单，请待退费完成后再退号";

    /** 存在未退完药品，须先完成退药后再退号。 */
    String CANCEL_MESSAGE_MEDICATION_NOT_RETURNED =
        "存在未退完的药品，请先完成退药后再退号";

    /** 存在药房在途药品（待配药/已配药/待发药），须先完成门诊退费后再退号。 */
    String CANCEL_MESSAGE_MEDICATION_IN_PHARMACY =
        "存在药房待配药、已配药或待发药项目，请先完成门诊退费后再退号";

    /** 存在已执行未取消的耗材，须护士取消执行后再退号。 */
    String CANCEL_MESSAGE_DEVICE_NOT_CANCELLED =
        "存在已执行未取消的耗材，请护士取消执行后再退号";
}
