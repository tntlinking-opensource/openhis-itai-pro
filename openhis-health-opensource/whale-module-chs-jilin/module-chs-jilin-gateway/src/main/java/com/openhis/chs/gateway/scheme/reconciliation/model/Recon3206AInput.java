/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.reconciliation.model;

import com.alibaba.fastjson2.annotation.JSONField;
import com.openhis.chs.gateway.scheme.common.model.wrapper.ChsDataInput;
import lombok.Data;
import lombok.experimental.Accessors;

/** 【3206A】清算机构分页查询 input */
public class Recon3206AInput extends ChsDataInput<Recon3206AInput.Recon3206AData> {

    /** 3206A input.data */
    @Data
    @Accessors(chain = true)
    public static class Recon3206AData {

        @JSONField(name = "fixmedins_code")
        private String fixmedinsCode;

        @JSONField(name = "page_num")
        private Integer pageNum;

        @JSONField(name = "page_size")
        private Integer pageSize;
    }
}
