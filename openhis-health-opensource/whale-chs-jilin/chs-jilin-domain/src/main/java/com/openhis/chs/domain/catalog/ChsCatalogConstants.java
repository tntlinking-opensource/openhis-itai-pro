package com.openhis.chs.domain.catalog;

import java.time.format.DateTimeFormatter;

/**
 * 国家医保目录业务常量。
 */
public final class ChsCatalogConstants {

    public static final String DEFAULT_CATALOG_UPDT_TIME = "2020-01-01";
    public static final String UPDT_TIME_DATE_PATTERN = "yyyy-MM-dd";
    public static final DateTimeFormatter UPDT_TIME_DATE_FORMATTER =
            DateTimeFormatter.ofPattern(UPDT_TIME_DATE_PATTERN);
    public static final String DEFAULT_LIST_TYPE = "101";
    public static final String DEFAULT_VALI_FLAG = "1";
    public static final int DEFAULT_PLATFORM_PAGE_SIZE = 50;
    public static final int DEFAULT_LOCAL_PAGE_SIZE = 20;
    public static final String DEFAULT_CATALOG_DOWNLOAD_VER = "0";
    public static final String SOURCE_INFNO_1312 = "1312";
    /** 1301 西药中成药文件型目录 */
    public static final String SOURCE_INFNO_1301 = "1301";
    public static final String OPERATION_UPLOAD_3301 = "UPLOAD3301";
    public static final String OPERATION_REVOKE_3302 = "REVOKE3302";
    public static final int CATALOG_3301_BATCH_SIZE = 100;
    public static final int MEDICATION_MATCH_CANDIDATE_POOL = 200;
    public static final int MEDICATION_MATCH_DEFAULT_MAX = 20;

    public static final String LIST_TYPE_WESTERN_DRUG = "101";
    /** 中药饮片（1302） */
    public static final String LIST_TYPE_HERB = "102";
    public static final String LIST_TYPE_MEDICAL_SERVICE = "201";
    public static final String LIST_TYPE_MEDICAL_CONSUMABLE = "301";
    /** 1305 医疗服务项目首包版本分支 F002 */
    public static final String INITIAL_VER_SERVICE_F002 = "F002";
    /** 1305 医疗服务项目首包版本分支 F003 */
    public static final String INITIAL_VER_SERVICE_F003 = "F003";
    /** 1308 手术操作目录规范初始版本 */
    public static final String INITIAL_VER_SURGERY = "-1";
    /** 探测接口默认最多探测版本数 */
    public static final int DEFAULT_MAX_DOWNLOAD_VERSIONS = 50;

    /**
     * @deprecated 正式下载已改为无轮次上限、平台驱动停链；保留常量仅为兼容旧引用。
     */
    @Deprecated
    public static final int CATALOG_DOWNLOAD_VERSION_SAFETY_CAP = Integer.MAX_VALUE;

    /** 13xx 文件包解析入库单批 flush 行数 */
    public static final int CATALOG_UPSERT_BATCH_SIZE = 500;

    /** 游标历史 API 最多返回条数（UI 展示用，避免一次拉全表快照） */
    public static final int CATALOG_DOWNLOAD_CURSOR_HISTORY_LIMIT = 200;

    private ChsCatalogConstants() {
    }
}
