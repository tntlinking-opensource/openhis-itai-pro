/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.share.constants;

import java.time.format.DateTimeFormatter;

/**
 * 国家医保目录（1312 等）业务字面量。
 */
public interface InsuranceCatalogConstants {

    /** 本地无游标时 1312 增量拉取起始日期（与 OpenHIS 一致）。 */
    String DEFAULT_CATALOG_UPDT_TIME = "2020-01-01";

    /** 1312 updt_time 日期格式（yyyy-MM-dd）。 */
    String UPDT_TIME_DATE_PATTERN = "yyyy-MM-dd";

    /** 1312 updt_time 日期格式化器。 */
    DateTimeFormatter UPDT_TIME_DATE_FORMATTER = DateTimeFormatter.ofPattern(UPDT_TIME_DATE_PATTERN);

    /** 目录类别缺省 list_type（西药中成药）。 */
    String DEFAULT_LIST_TYPE = "101";

    /** 1312 有效标志缺省 vali_flag（有效）。 */
    String DEFAULT_VALI_FLAG = "1";

    /** 1312 平台分页缺省 page_size。 */
    int DEFAULT_PLATFORM_PAGE_SIZE = 50;

    /** 本地目录分页缺省 page_size。 */
    int DEFAULT_LOCAL_PAGE_SIZE = 20;

    /** 文件型目录下载初始版本号。 */
    String DEFAULT_CATALOG_DOWNLOAD_VER = "0";

    /** 1301 西药中成药 list_type。 */
    String LIST_TYPE_WESTERN_DRUG = "101";

    /** 1305 医疗服务项目 list_type。 */
    String LIST_TYPE_MEDICAL_SERVICE = "201";

    /** 1306 医用耗材 list_type。 */
    String LIST_TYPE_MEDICAL_CONSUMABLE = "301";

    /** 单次目录下载最多连续拉取版本数（防止平台异常死循环）。 */
    int DEFAULT_MAX_DOWNLOAD_VERSIONS = 50;
}
