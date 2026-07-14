/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.share.enums;

/**
 * 2301 费用明细上传字段 {@code hosp_appr_flag}（医院审批标志）。
 *
 * <p>院内正常计费明细默认 {@link #APPROVED}（审批通过）。</p>
 */
public enum HospApprFlag implements ChsCodeEnum {

    /** 审批通过（code 1；默认） */
    APPROVED("1", "审批通过");

    private final String code;
    private final String label;

    HospApprFlag(String code, String label) {
        this.code = code;
        this.label = label;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getLabel() {
        return label;
    }
}
