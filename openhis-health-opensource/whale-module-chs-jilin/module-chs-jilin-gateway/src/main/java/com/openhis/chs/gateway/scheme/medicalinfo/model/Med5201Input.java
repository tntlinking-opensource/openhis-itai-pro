/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.medicalinfo.model;

import com.alibaba.fastjson2.annotation.JSONField;
import com.openhis.chs.gateway.scheme.common.model.wrapper.ChsDataInput;
import lombok.Data;
import lombok.experimental.Accessors;

/** 【5201】就诊信息查询 input */
public class Med5201Input extends ChsDataInput<Med5201Input.Med5201Data> {

    /** 5201 input.data */
    @Data
    @Accessors(chain = true)
    public static class Med5201Data {

        @JSONField(name = "psn_no")
        private String psnNo;

        private String begntime;

        private String endtime;

        @JSONField(name = "med_type")
        private String medType;

        @JSONField(name = "mdtrt_id")
        private String mdtrtId;
    }
}
