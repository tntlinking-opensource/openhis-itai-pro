/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.practitioner.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 保存医务人员签名图（adm_practitioner.signature）。
 */
@Getter
@Setter
public class SavePractitionerSignatureRequest {
    private Long practitionerId;
    /** data:image/*;base64,... 或纯 Base64；传空串表示清除 */
    private String signatureImage;
}
