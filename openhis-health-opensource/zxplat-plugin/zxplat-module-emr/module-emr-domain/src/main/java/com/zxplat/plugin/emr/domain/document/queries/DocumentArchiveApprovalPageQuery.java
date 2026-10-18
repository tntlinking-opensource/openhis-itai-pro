/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.domain.document.queries;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 文书待归档审批分页查询入参（DM-EMR-001 §6.2）。
 *
 * <p>门诊/住院就诊 class 由应用层先筛 encounterIds 再下传，本聚合不跨查 Encounter。</p>
 */
@Getter
@Setter
public class DocumentArchiveApprovalPageQuery {

    /** 租户；可空表示不按下推租户（多租户环境应由应用层传入）。 */
    private Long tenantId;

    /**
     * 就诊标识集合；空表示不限就诊（仅按 status）。
     * 应用层按门诊/住院 class 预筛后传入。
     */
    private List<String> encounterIds;

    /** 书写科室；空表示不限。 */
    private Long organizationId;

    /** 页码（从 1）。 */
    private Integer pageIndex;

    /** 页大小。 */
    private Integer pageSize;
}
