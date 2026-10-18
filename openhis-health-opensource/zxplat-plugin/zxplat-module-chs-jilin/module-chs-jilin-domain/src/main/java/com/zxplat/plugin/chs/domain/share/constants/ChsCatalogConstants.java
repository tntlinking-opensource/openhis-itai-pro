/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.share.constants;

import java.time.format.DateTimeFormatter;

/**
 * 国家医保<b>目录下载/对码</b>模块的内部常量（非平台字典取值）。
 *
 * <p>平台字段 {@code list_type}、{@code vali_flag} 的取值见 {@code share.enums}；
 * 目录下载交易号见 {@link ChsInfNo#DRUG_CATALOG_DOWNLOAD} 等。</p>
 */
public final class ChsCatalogConstants {

    /** 1312 增量查询：无历史游标时的默认起始 updt_time */
    public static final String DEFAULT_CATALOG_UPDT_TIME = "2020-01-01";
    /** 1317 机构目录匹配全量：默认起始 updt_time（对齐老插件 2001-01-01） */
    public static final String DEFAULT_1317_UPDT_TIME = "2001-01-01";
    /** 目录 updt_time 日期格式（平台要求 yyyy-MM-dd） */
    public static final String UPDT_TIME_DATE_PATTERN = "yyyy-MM-dd";
    /** 目录 updt_time 格式化器 */
    public static final DateTimeFormatter UPDT_TIME_DATE_FORMATTER =
            DateTimeFormatter.ofPattern(UPDT_TIME_DATE_PATTERN);
    /** 调用 1312/1301 等平台接口时的默认 pageSize */
    public static final int DEFAULT_PLATFORM_PAGE_SIZE = 50;
    /** 1317 单次同步自动翻页上限（防止异常死循环） */
    public static final int MAX_1317_REFRESH_PAGES = 200;
    /** 院内目录镜像分页默认 pageSize */
    public static final int DEFAULT_LOCAL_PAGE_SIZE = 20;
    /** 13xx 首包下载：版本号为空时的占位（表示从首版开始） */
    public static final String DEFAULT_CATALOG_DOWNLOAD_VER = "0";
    /** 本地审计：3301 目录对照上传操作标识 */
    public static final String OPERATION_UPLOAD_3301 = "UPLOAD3301";
    /** 本地审计：3302 目录对照撤销操作标识 */
    public static final String OPERATION_REVOKE_3302 = "REVOKE3302";
    /** 3301 单次批量上传最大行数 */
    public static final int CATALOG_3301_BATCH_SIZE = 100;
    /** 药品智能对码：从国家目录候选池取前 N 条再打分 */
    public static final int MEDICATION_MATCH_CANDIDATE_POOL = 200;
    /** 药品智能对码：返回给前端的推荐条数上限 */
    public static final int MEDICATION_MATCH_DEFAULT_MAX = 20;

    /** 西药中成药 HIS 主数据表（1301/101 类目录落库关联） */
    public static final String TABLE_MED_MEDICATION_DEFINITION = "med_medication_definition";
    /** 医疗服务项目 HIS 主数据表（1305/201 类） */
    public static final String TABLE_WKF_ACTIVITY_DEFINITION = "wkf_activity_definition";
    /** 医用耗材 HIS 主数据表（1306/301 类） */
    public static final String TABLE_ADM_DEVICE_DEFINITION = "adm_device_definition";
    /** 目录对码时认为「有效」的 HIS 产品状态 */
    public static final String PRODUCT_STATUS_ACTIVE = "ACTIVE";

    /** 1305 医疗服务目录：部分局点首包版本分支标识 F002 */
    public static final String INITIAL_VER_SERVICE_F002 = "F002";
    /** 1305 医疗服务目录：部分局点首包版本分支标识 F003 */
    public static final String INITIAL_VER_SERVICE_F003 = "F003";
    /** 1308 手术操作目录：规范约定的初始版本号 */
    public static final String INITIAL_VER_SURGERY = "-1";
    /** 目录版本探测：单次任务最多尝试的版本个数 */
    public static final int DEFAULT_MAX_DOWNLOAD_VERSIONS = 50;

    /** 13xx ZIP 解析入库：Jdbc batch 每批 flush 行数 */
    public static final int CATALOG_UPSERT_BATCH_SIZE = 500;

    /** 下载游标历史 API：每个 infno 最多返回条数（避免全局 LIMIT 截掉较早同步的接口如 1301） */
    public static final int CATALOG_DOWNLOAD_CURSOR_HISTORY_PER_INFNO = 50;

    /** @deprecated 请用 {@link #CATALOG_DOWNLOAD_CURSOR_HISTORY_PER_INFNO}；保留兼容旧引用 */
    public static final int CATALOG_DOWNLOAD_CURSOR_HISTORY_LIMIT = 200;

    private ChsCatalogConstants() {
    }
}
