/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.share.constants;

/**
 * 【1901】字典表下载/同步相关的 type 键与同步配置。
 *
 * <p>1901 按 {@code type} 分页拉取国家字典；本类常量是<b>请求参数里的 type 字符串</b>，
 * 不是字典项的取值。具体取值（如 med_type=11）见 {@code share.enums} 下各字段枚举；
 * health 镜像（如 {@code YbMedType}）须与枚举 code/label 人工同步（Skill §2.2.4）。</p>
 */
public final class PlatformDictConstants {

    /** 1901 type：医疗类别，取值见 {@link com.openhis.chs.domain.share.enums.MedType} */
    public static final String DICT_TYPE_MED_TYPE = "med_type";

    /** 1901 type：险种类型（职工/居民等） */
    public static final String DICT_TYPE_INSUTYPE = "insutype";

    /** 1901 type：人员类别 */
    public static final String DICT_TYPE_PSN_TYPE = "psn_type";

    /** 1901 type：性别 */
    public static final String DICT_TYPE_GEND = "gend";

    /** 1901 type：就诊凭证类型，取值见 {@link com.openhis.chs.domain.share.enums.MdtrtCertType} */
    public static final String DICT_TYPE_MDTRT_CERT_TYPE = "mdtrt_cert_type";

    /** 1901 type：支付地点 */
    public static final String DICT_TYPE_PAY_LOC = "pay_loc";

    /** 1901 type：清算类别（对账清算用） */
    public static final String DICT_TYPE_CLR_TYPE = "clr_type";

    /** 1901 type：目录类别，取值见 {@link com.openhis.chs.domain.share.enums.ListType} */
    public static final String DICT_TYPE_LIST_TYPE = "list_type";

    /** 1901 type：诊断类别，取值见 {@link com.openhis.chs.domain.share.enums.DiagType} */
    public static final String DICT_TYPE_DIAG_TYPE = "diag_type";

    /** 1901 type：参保地医保区划（部分局点用大写 ADMDVS 作 type） */
    public static final String DICT_TYPE_INSUPLC_ADMDVS = "INSUPLC_ADMDVS";

    /** 1901 type：行政区划（吉林等局点） */
    public static final String DICT_TYPE_ADMDVS = "ADMDVS";

    /** 1901 type：参保地医保区划（小写字段名风格，与 1101 报文字段一致） */
    public static final String DICT_TYPE_INSUPLC_ADMDVS_FIELD = "insuplc_admdvs";

    /** 1901 type：就医地医保区划 */
    public static final String DICT_TYPE_MDTRTAREA_ADMVS = "mdtrtarea_admvs";

    /** 全量同步任务默认从医疗类别字典开始 */
    public static final String DEFAULT_DICT_TYPE = DICT_TYPE_MED_TYPE;

    /** 区划字典同步页默认 type */
    public static final String DEFAULT_ADMDVS_DICT_TYPE = DICT_TYPE_INSUPLC_ADMDVS;

    /**
     * 本地缓存桶：1101 返回的 {@code insuplc_admdvs} 编码 → 中文区划名。
     *
     * <p>国标 1901 未统一定义区划 type，此处为 HIS 展示用 lookup key，非 1901 入参 type。</p>
     */
    public static final String LOOKUP_INSUPLC_ADMDVS = "insuplc_admdvs";

    /** 1901 分页拉取每页条数 */
    public static final int DEFAULT_PAGE_SIZE = 500;

    /** 本地同步任务状态：成功 */
    public static final String SYNC_STATUS_SUCCESS = "SUCCESS";
    /** 本地同步任务状态：失败 */
    public static final String SYNC_STATUS_FAILED = "FAILED";

    /** 1901 入参有效标志字段名（基线规范大写 Vali_Flag） */
    public static final String VALI_FLAG_KEY_SPEC = "Vali_Flag";

    /** 1901 入参有效标志字段名（吉林等局点实际用小写 vali_flag） */
    public static final String VALI_FLAG_KEY = "vali_flag";

    private PlatformDictConstants() {
    }
}
