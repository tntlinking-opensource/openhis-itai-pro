/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.mrms;

import lombok.Getter;
import lombok.Setter;

/**
 * 国临码 → 医保码解析结果（上传出站与结算清单预览共用）。
 *
 * <p>{@code outboundCode} 仅在已对医保码时有值；未对码时为 null，上传须报错。</p>
 */
@Getter
@Setter
public class MedcasDualCodeResolveResult {

    /** 国临码（首页落库真源）。 */
    private String clinicCode;

    /**
     * 国临目录（clinical_v2）主键；结算清单「对码」写回用。
     * 目录中无该国临码时为 null。
     */
    private Long clinicalDefinitionId;

    /** 目录对照出的医保码；未对码时为 null。 */
    private String ybNo;

    /** 目录对照出的医保名称；未对码时为 null。 */
    private String ybName;

    /**
     * 上传出站码：仅医保码；未对码时 null（禁止回退国临）。
     */
    private String outboundCode;

    /** 是否已对上医保码。 */
    private boolean mapped;
}
