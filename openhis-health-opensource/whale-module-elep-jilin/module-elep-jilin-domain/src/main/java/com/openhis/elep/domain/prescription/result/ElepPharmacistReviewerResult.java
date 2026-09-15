/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.domain.prescription.result;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 当前登录人作为院内医保药师的只读投影。
 */
@Data
@Accessors(chain = true)
public class ElepPharmacistReviewerResult {

    /** 是否可审方。 */
    private boolean allowed;

    /** 拦截原因。 */
    private String blockReason;

    /** 药师医保码。 */
    private String pharCode;

    /** 药师姓名。 */
    private String pharName;

    /** 药师科室医保码。 */
    private String pharDeptCode;

    /** 药师科室名称。 */
    private String pharDeptName;

    /** 职称编码。 */
    private String pharProfttlCodg;

    /** 职称名称。 */
    private String pharProfttlName;

    /** 执业资格证号。 */
    private String pharPracCertNo;
}
