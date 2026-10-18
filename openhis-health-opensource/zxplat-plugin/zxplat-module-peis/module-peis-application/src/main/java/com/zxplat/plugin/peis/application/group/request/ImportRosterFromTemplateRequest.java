/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.group.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 团检名册模板导入请求。
 */
@Getter
@Setter
public class ImportRosterFromTemplateRequest {

    /** 合同主键。 */
    private Long contractId;

    /** 默认体检分组主键。 */
    private Long defaultBatchId;

    /** 导入页选中的单位树节点。 */
    private Long selectedCompanyId;

    /** 兼容旧版：仅分组 id。 */
    private Long batchId;

    /** 人员类型：STUDENT / STAFF / ENTERPRISE。 */
    private String memberKind;

    /** xlsx 文件字节。 */
    private byte[] fileBytes;

    /** 已废弃：导入不再自动建单。 */
    private Boolean autoEnroll;

    /** 教职工是否同步 Campus（默认 true；仅 STAFF 生效）。 */
    private Boolean syncCampus;
}
