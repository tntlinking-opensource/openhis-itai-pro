/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.medicalinfo.model;

import com.alibaba.fastjson2.annotation.JSONField;
import com.openhis.chs.gateway.scheme.common.model.wrapper.ChsDataInput;
import lombok.Data;
import lombok.experimental.Accessors;

/** 【5302A】人员门诊慢特病审批查询 input */
public class Med5302AInput extends ChsDataInput<Med5302AInput.Med5302AData> {

    /** 5302A input.data */
    @Data
    @Accessors(chain = true)
    public static class Med5302AData {

        @JSONField(name = "psn_no")
        private String psnNo;

        /** 可选；不填时慢病、特病都返回。 */
        @JSONField(name = "med_type")
        private String medType;
    }
}
