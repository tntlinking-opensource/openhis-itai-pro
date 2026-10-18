/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 团检合同类型编码（与 peis_contract_type 种子一致；booking 号源分池只读引用）。
 */
public final class PeisContractTypeCodes {

    private PeisContractTypeCodes() {
    }

    /** 学生。 */
    public static final String STUDENT = "STUDENT";

    /** 教职工。 */
    public static final String STAFF = "STAFF";

    /** 企业。 */
    public static final String ENTERPRISE = "ENTERPRISE";

    /** 散客（无合同时进此池）。 */
    public static final String WALK_IN = "WALK_IN";
}
