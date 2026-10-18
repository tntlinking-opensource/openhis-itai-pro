/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.patient.queries;

import lombok.Getter;
import lombok.Setter;

/**
 * 分页检索患者查询入参。
 */
@Getter
@Setter
public class PatientPagedQuery {

    /**
     * 通用关键字（B.10.11，与 {@link AbstractPageQuery#keyword} 对齐）。
     */
    private String keyword;

    /**
     * 姓名关键字。
     */
    private String name;

    /**
     * 身份证号关键字。
     */
    private String idCard;

    /**
     * 院内编码关键字。
     */
    private String busNo;

    /**
     * 性别编码。
     */
    private String genderEnum;

    /**
     * 状态编码。
     */
    private String statusEnum;

    /**
     * 启用标记。
     */
    private Boolean activeFlag;

    /**
     * 临时标记。
     */
    private Boolean tempFlag;

    /**
     * 建档机构标识。
     */
    private Long organizationId;

    /**
     * 页码（从 1 开始）。
     */
    private Integer pageNo;

    /**
     * 每页大小。
     */
    private Integer pageSize;
}
