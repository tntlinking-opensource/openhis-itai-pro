/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.gateway.scheme.register.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 【2301】审批信息上传 output（10-P0 §5.2）。
 */
@Data
@Accessors(chain = true)
public class Wi2301Output {

    /** 若返回则写 anchor；仍须再 9102 确认（BR-WI-APR-001）。 */
    @JSONField(name = "qualification_id")
    private String qualificationId;
}
