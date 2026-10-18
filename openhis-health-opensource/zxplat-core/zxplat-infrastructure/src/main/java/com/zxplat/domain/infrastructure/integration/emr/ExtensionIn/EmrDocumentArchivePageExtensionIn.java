/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.emr.ExtensionIn;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 文书待归档审批分页入参。
 */
@Getter
@Setter
public class EmrDocumentArchivePageExtensionIn {

    /** 租户。 */
    private Long tenantId;

    /**
     * 就诊标识集合（字符串，与 emr_document.encounter_id 一致）；
     * 空表示不限就诊（由应用层按 AMB/IMP 预筛后传入）。
     */
    private List<String> encounterIds;

    /** 书写科室；空表示不限。 */
    private Long organizationId;

    /** 页码（从 1）。 */
    private Integer pageIndex;

    /** 页大小。 */
    private Integer pageSize;
}
