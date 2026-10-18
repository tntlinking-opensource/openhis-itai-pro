/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.registrationtype.request;

import lombok.Data;

/**
 * 诊查费候选项查询（IF-BAS-REG-02）。
 */
@Data
public class ListConsultationActivityOptionsRequest {
    /** 关键字（名称/编码）；可空。 */
    private String keyword;
}
