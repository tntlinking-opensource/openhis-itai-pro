/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.bridge.lab.pathb;

import lombok.Getter;
import lombok.Setter;

/**
 * yunlis {@code l_testresult} + {@code l_testdescribe} 明细读模型。
 */
@Getter
@Setter
public class YunlisTestResultRow {

    private Long testResultId;
    private String testId;
    private String chineseName;
    private String testResult;
    private String unit;
    private String refLo;
    private String refHi;
    private String resultTip;
    private String sampleNo;
}
