/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 国家医保目录（1312 等）HIS 侧业务参数。
 *
 * <p>平台字段取值（{@code list_type}、{@code vali_flag} 等）权威定义在
 * {@code com.zxplat.plugin.chs.domain.share.enums}（如 {@code ListType}、{@code ValiFlag}），
 * 禁止在本类重复维护第二份平台 code/文案。</p>
 */
public interface InsuranceCatalogConstants {

    /** 本地无游标时 1312 增量拉取起始日期（与历史 一致）。 */
    String DEFAULT_CATALOG_UPDT_TIME = "2020-01-01";

    /** 1312 平台分页缺省 page_size。 */
    int DEFAULT_PLATFORM_PAGE_SIZE = 50;

    /** 本地目录分页缺省 page_size。 */
    int DEFAULT_LOCAL_PAGE_SIZE = 20;

    /** 文件型目录下载初始版本号。 */
    String DEFAULT_CATALOG_DOWNLOAD_VER = "0";

    /** 单次目录下载最多连续拉取版本数（防止平台异常死循环）。 */
    int DEFAULT_MAX_DOWNLOAD_VERSIONS = 50;
}
