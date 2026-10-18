/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 药品结余留用报表常量（AS-RPT-015）。
 *
 * <p>时间格式复用 {@link GeneralSharedConstants#DATE_DISPLAY_PATTERN}，本接口不重复定义。</p>
 */
public interface SurplusRetentionReportConstants {

    /** 明细默认页大小。 */
    int DEFAULT_PAGE_SIZE = 20;

    /** 明细分页单页上限（列表请求；导出内部分页见 QuerySupport）。 */
    int MAX_PAGE_SIZE = 500;

    /** 导出内部分页页大小（大于列表默认，降低导出轮次）。 */
    int EXPORT_PAGE_SIZE = 500;

    /** 导出行数上限。 */
    int EXPORT_MAX_ROWS = 50000;

    /** 导出 xlsx MIME。 */
    String EXPORT_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** 导出 sheet 名。 */
    String EXPORT_SHEET = "药品结余留用";

    /** 导出文件名前缀。 */
    String EXPORT_FILE_PREFIX = "药品结余留用_";

    /** 是否中选：是。 */
    String YES_TEXT = "是";

    /** 是否中选：否。 */
    String NO_TEXT = "否";

    /**
     * 参保类型展示：自费（合同分类 SELF_CHARGE / ChargeTypeCategory.SELF_PAY）；
     * 自费行的参保地区同写此文案。
     */
    String INSURED_CATEGORY_SELF_PAY = "自费";

    /** 参保类型展示：省医保。 */
    String INSURED_CATEGORY_PROVINCIAL = "省医保";

    /** 参保类型展示：市医保。 */
    String INSURED_CATEGORY_MUNICIPAL = "市医保";

    /** 页面初始化失败（IF-RPT-SRB-01）。 */
    String EX_RPT_SRB_101 = "EX-RPT-SRB-101";

    /** 批次/签发时间窗非法（IF-RPT-SRB-02/03）。 */
    String EX_RPT_SRB_102 = "EX-RPT-SRB-102";

    /** 查询失败（IF-RPT-SRB-02）。 */
    String EX_RPT_SRB_103 = "EX-RPT-SRB-103";

    /** 导出失败（IF-RPT-SRB-03）。 */
    String EX_RPT_SRB_104 = "EX-RPT-SRB-104";
}
