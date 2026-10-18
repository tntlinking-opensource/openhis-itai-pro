/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.insurance.medcas.ExtensionIn;

import lombok.Getter;
import lombok.Setter;

/**
 * 4401 病案首页上传命令。
 *
 * <p>职责：携带已组装的平台 JSON 与合同上下文，由 {@code MedcasHomepageUploadExtension} 出站。</p>
 */
@Getter
@Setter
public class MedcasHomepageUploadExtensionIn {

    /** 医保合同业务号。 */
    private String contractBusNo;

    /** 4401 平台 input JSON（已序列化）。 */
    private String inputJson;

    /** 操作员主键（审计）。 */
    private Long operatorId;
}
