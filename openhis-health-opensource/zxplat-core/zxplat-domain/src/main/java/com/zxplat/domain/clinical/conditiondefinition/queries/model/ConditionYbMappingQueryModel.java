/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.conditiondefinition.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 国临码对照出的医保码与医保名称（病案上传/结算清单回显）。
 *
 * <p>真源为「国临目录（clinical_v2）绑医保码」；未对码时仅有 {@code clinicalDefinitionId}，
 * {@code ybNo}/{@code ybName} 为空。禁止把「医保目录同码」误判为已对码。</p>
 */
@Getter
@Setter
public class ConditionYbMappingQueryModel {

    /**
     * 国临目录（clinical_v2）主键；结算清单「对码」写回用。
     * 国临码在目录中不存在时为 null。
     */
    private Long clinicalDefinitionId;

    /** 医保诊断编码（yb_no）；未对码时为 null。 */
    private String ybNo;

    /** 医保诊断名称（优先疾病与诊断目录 name）；未对码时为 null。 */
    private String ybName;
}
