/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.gateway.scheme.register.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 【9102】审批信息查询 input（10-P0 §4.1）。
 */
@Data
@Accessors(chain = true)
public class Wi9102Input {

    /** 查询开始 yyyyMMdd；默认当天 -30 天。 */
    @JSONField(name = "startTime")
    private String startTime;

    /** 查询结束 yyyyMMdd；默认当天。 */
    @JSONField(name = "endTime")
    private String endTime;

    /** 人员编号 psn_no；anchor / 1101。 */
    @JSONField(name = "psn_no")
    private String psnNo;
}
