/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

import java.util.List;

/**
 * 门诊处方抽查报表常量（AS-RPT-014）。
 *
 * <p>时间格式复用 {@link GeneralSharedConstants#DATE_DISPLAY_PATTERN}，本接口不重复定义。</p>
 */
public interface PrescriptionSpotCheckReportConstants {

    /** 报表类型：西药及中成药。 */
    String REPORT_TYPE_WESTERN_PATENT = "WESTERN_PATENT";

    /** 报表类型：中药饮片。 */
    String REPORT_TYPE_HERBAL = "HERBAL";

    /** 报表类型展示：西药及中成药。 */
    String REPORT_TYPE_WESTERN_PATENT_LABEL = "西药及中成药";

    /** 报表类型展示：中药饮片。 */
    String REPORT_TYPE_HERBAL_LABEL = "中药饮片";

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

    /** 西药及中成药导出 sheet 名。 */
    String EXPORT_SHEET_WESTERN_PATENT = "西药及中成药处方";

    /** 中药饮片导出 sheet 名。 */
    String EXPORT_SHEET_HERBAL = "中药饮片处方";

    /** 西药及中成药导出文件名前缀。 */
    String EXPORT_FILE_PREFIX_WESTERN_PATENT = "西药及中成药处方_";

    /** 中药饮片导出文件名前缀。 */
    String EXPORT_FILE_PREFIX_HERBAL = "中药饮片处方_";

    /** 就诊类型固定文案：门诊。 */
    String VISIT_TYPE_OUTPATIENT = "门诊";

    /** 性别短文案：男。 */
    String GENDER_MALE_SHORT = "男";

    /** 性别短文案：女。 */
    String GENDER_FEMALE_SHORT = "女";

    /** 页面初始化失败（IF-RPT-PSC-01）。 */
    String EX_RPT_PSC_101 = "EX-RPT-PSC-101";

    /** 类型/开方时间窗非法（IF-RPT-PSC-02/03）。 */
    String EX_RPT_PSC_102 = "EX-RPT-PSC-102";

    /** 查询失败（IF-RPT-PSC-02）。 */
    String EX_RPT_PSC_103 = "EX-RPT-PSC-103";

    /** 导出失败（IF-RPT-PSC-03）。 */
    String EX_RPT_PSC_104 = "EX-RPT-PSC-104";

    /**
     * 西药及中成药处方类别码集合（术语 355，不含饮片轨）。
     *
     * <p>含门诊/急诊/儿科西药与中成药、麻精一、精二；不含饮片码 2/4/6（历史 9 由归一处理）。</p>
     *
     * @return 不可变码列表
     */
    static List<String> listWesternPatentRxTypeCodes() {
        return List.of(
            PrescriptionSplitConstants.RX_TYPE_OUTPATIENT_WESTERN,
            PrescriptionSplitConstants.RX_TYPE_EMERGENCY_WESTERN,
            PrescriptionSplitConstants.RX_TYPE_PEDIATRIC_WESTERN,
            PrescriptionSplitConstants.RX_TYPE_NARCOTIC_PSYCH_ONE,
            PrescriptionSplitConstants.RX_TYPE_PSYCH_TWO,
            PrescriptionSplitConstants.RX_TYPE_OUTPATIENT_PATENT,
            PrescriptionSplitConstants.RX_TYPE_EMERGENCY_PATENT,
            PrescriptionSplitConstants.RX_TYPE_PEDIATRIC_PATENT);
    }

    /**
     * 中药饮片处方类别码集合（术语 355）。
     *
     * <p>含门诊/急诊/儿科饮片；历史冗余码 {@code 9} 由
     * {@link PrescriptionSplitConstants#normalizeRxTypeCode} 在 Spec 侧归一为 {@code 2}。</p>
     *
     * @return 不可变码列表
     */
    static List<String> listHerbalRxTypeCodes() {
        return List.of(
            PrescriptionSplitConstants.RX_TYPE_OUTPATIENT_HERBAL,
            PrescriptionSplitConstants.RX_TYPE_EMERGENCY_HERBAL,
            PrescriptionSplitConstants.RX_TYPE_PEDIATRIC_HERBAL);
    }
}
