/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.application.document.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InitEmrPageRequest {

    /** 患者 id（字符串） */
    private Long patientId;
    /** 就诊 id（字符串，门诊/住院会话） */
    private Long encounterId;
    /** 适用科室/机构 id */
    private Long organizationId;
}
