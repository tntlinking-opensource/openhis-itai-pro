/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * 耗材目录批量导入字面量（镜像 AS-BAS-CAT-002 药品导入）。
 *
 * <p>模板 v1.3：与「新增耗材」手工表单可填字段对齐（32 列）；不含 busNo/拼音五笔/状态/医药机构目录编码。
 * 「不收费耗材」列：是→chargeableFlag=false，否→true；空白保持 false。</p>
 */
public interface DeviceCatalogImportConstants {

    /** 模板业务编码。 */
    String TEMPLATE_CODE = "DEV_CATALOG_IMPORT";

    /** 当前模板版本。 */
    String TEMPLATE_VERSION = "v1.3";

    /** 指导页 Sheet 名。 */
    String SHEET_GUIDE = "①从这里开始";

    /** 数据录入 Sheet 名。 */
    String SHEET_DATA = "②耗材目录";

    /** 下拉可选值 Sheet 名。 */
    String SHEET_OPTIONS = "③可选值一览";

    /** 隐藏系统元数据 Sheet 名。 */
    String SHEET_SYS = "_sys";

    /** 字段表头行（0-based）。 */
    int HEADER_ROW_INDEX = 1;

    /** 用户数据起始行（0-based，对应 Excel 第 5 行）。 */
    int DATA_START_ROW_INDEX = 4;

    /** 单文件有效行上限。 */
    int IMPORT_MAX_ROWS = 3000;

    /** 数据验证下拉覆盖的 Excel 最大行号（1-based）。 */
    int VALIDATION_END_ROW = 3004;

    /** 示例行耗材名称前缀。 */
    String EXAMPLE_NAME_PREFIX = "[示例]";

    /** 错误标注列标题。 */
    String ERROR_COLUMN_TITLE = "错误说明";

    /** 院内旧编码写入 description 的前缀。 */
    String LEGACY_CODE_DEFINITION_PREFIX = "院内旧编码=";

    /** 错误标注文件 token 有效分钟数。 */
    int ERROR_FILE_TTL_MINUTES = 30;

    /** 上传/错误文件最大字节数（15MB）。 */
    long ERROR_FILE_MAX_BYTES = 15L * 1024L * 1024L;

    /** 下载模板默认文件名。 */
    String TEMPLATE_FILE_NAME = "耗材目录导入模板_v1.3.xlsx";

    /** 导出文件名时间戳格式。 */
    String EXPORT_FILE_NAME_TIME_PATTERN = "yyyyMMddHHmmss";

    /** 导出文件名前缀。 */
    String EXPORT_FILE_NAME_PREFIX = "耗材目录导出_";

    /** 导出唯一数据 Sheet 名。 */
    String EXPORT_SHEET_NAME = "耗材目录";

    /** 导出首列：系统编号（busNo）。 */
    String EXPORT_BUS_NO_HEADER = "编号";

    /** 单次导出最大行数。 */
    int EXPORT_MAX_ROWS = 3000;

    /** xlsx MIME。 */
    String XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** 批次号前缀。 */
    String BATCH_NO_PREFIX = "DEV-IMP-";

    /**
     * 字段表头规范名（去 * 后，列序即指纹源）。共 32 列。
     *
     * <p>A 基础 0～12；B 库存单位 13～24；C 业务 25～31。</p>
     */
    String[] COLUMN_HEADERS = {
        // A 基础信息（13）
        "耗材名称",
        "商品名",
        "耗材分类",
        "耗材种类",
        "生产厂家",
        "财务类型",
        "包装规格",
        "批准文号",
        "医保编码",
        "医保等级",
        "病案结算类型",
        "医保对码",
        "医保类别",
        // B 库存与单位（12）
        "采购单位",
        "拆零单位",
        "采购/拆零倍数",
        "包装单位",
        "包装/拆零倍数",
        "库存单位",
        "库存/拆零倍数",
        "购入价",
        "零售价",
        "最高零售价",
        "警戒线最低值",
        "警戒线最高值",
        // C 业务信息（7）
        "院内旧编码",
        "型号",
        "过敏标记",
        "医保标记",
        "高值耗材标志",
        "不收费耗材",
        "说明"
    };

    /** 必填列规范名（用于表头 * 与校验）。 */
    String[] REQUIRED_HEADERS = {
        "耗材名称",
        "耗材分类",
        "财务类型",
        "包装规格",
        "采购单位",
        "拆零单位",
        "采购/拆零倍数",
        "包装单位",
        "包装/拆零倍数",
        "库存单位",
        "库存/拆零倍数",
        "购入价",
        "零售价"
    };

    // —— 业务错误码（镜像 MED_CAT_IMP_* 编号） ——

    String ERR_FILE_OPEN = "DEV_CAT_IMP_101";
    String ERR_SHEET_MISSING = "DEV_CAT_IMP_102";
    String ERR_TEMPLATE_VERSION = "DEV_CAT_IMP_103";
    String ERR_COLUMN_FINGERPRINT = "DEV_CAT_IMP_104";
    String ERR_NO_DATA = "DEV_CAT_IMP_201";
    String ERR_TOO_MANY_ROWS = "DEV_CAT_IMP_202";
    String ERR_REQUIRED = "DEV_CAT_IMP_301";
    String ERR_NUMBER = "DEV_CAT_IMP_302";
    String ERR_TERMINOLOGY = "DEV_CAT_IMP_303";
    String ERR_RATIO = "DEV_CAT_IMP_304";
    String ERR_LIMIT_PRICE = "DEV_CAT_IMP_305";
    String ERR_DUP_FILE = "DEV_CAT_IMP_306";
    String ERR_DUP_DB = "DEV_CAT_IMP_307";
    String ERR_WRITE = "DEV_CAT_IMP_401";
    String ERR_TOKEN = "DEV_CAT_IMP_410";
}
