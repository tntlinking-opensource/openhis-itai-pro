/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * EMR 文书类型 code（与 {@code emr_document_type.code} / 文书 {@code document_type_code} 对齐）。
 *
 * <p>供 health 侧经 Extension 过滤文书时引用；真源种子见 EMR Flyway。</p>
 */
public final class EmrDocumentTypeCodes {

    private EmrDocumentTypeCodes() {
    }

    /** 门诊病历。 */
    public static final String OUTPATIENT_RECORD = "OUTPATIENT_RECORD";
}
