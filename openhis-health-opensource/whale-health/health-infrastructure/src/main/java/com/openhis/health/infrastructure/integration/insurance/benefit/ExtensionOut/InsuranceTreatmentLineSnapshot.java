/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionOut;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 待遇检查行快照。
 */
@Data
@Accessors(chain = true)
public class InsuranceTreatmentLineSnapshot {

    /** 人员编号。 */
    private String psnNo;

    /** 待遇检查类型 code。 */
    private String trtChkType;

    /** 基金支付类型 code。 */
    private String fundPayType;

    /** 享受待遇标志。 */
    private String trtEnjymntFlag;

    /** 开始日期。 */
    private String begndate;

    /** 结束日期。 */
    private String enddate;

    /** 待遇检查结果描述。 */
    private String trtChkRslt;
}
