/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.audit.model;

import com.alibaba.fastjson2.annotation.JSONField;
import com.zxplat.plugin.chs.gateway.scheme.common.model.wrapper.ChsDataInput;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** 【3103】事前提醒反馈 input */
public class Audit3103Input extends ChsDataInput<Audit3103Input.Audit3103Data> {

    /** 3103 input.data */
    @Data
    @Accessors(chain = true)
    public static class Audit3103Data {

        @JSONField(name = "warn_type")
        private String warnType;

        private List<Audit3103WarnRow> warns;
    }
}
