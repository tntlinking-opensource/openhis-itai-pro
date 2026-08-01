/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.physicalexam.financial.groupcharge.response;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 团检收费页初始化响应。
 */
@Getter
@Setter
public class GroupChargePageInitResponse {

    /** 支付渠道选项（占位，后续对齐门诊收费）。 */
    private List<String> paymentChannelOptions = new ArrayList<>();
}
