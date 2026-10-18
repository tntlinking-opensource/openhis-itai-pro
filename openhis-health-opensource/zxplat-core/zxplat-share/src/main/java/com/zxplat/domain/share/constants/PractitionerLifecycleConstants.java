/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 医务人员与平台用户生命周期联动常量（DM-ADM-005 §9.1）。
 */
public interface PractitionerLifecycleConstants {

    /** 平台用户软删联动注销 {@code adm_practitioner} 时的默认原因文案。 */
    String RETIRE_REASON_BOUND_USER_DELETED = "绑定系统用户已删除";

    /** 签名 Base64 最大字符数（约 400KB 原图压缩后）。 */
    int SIGNATURE_MAX_LENGTH = 512_000;
}
