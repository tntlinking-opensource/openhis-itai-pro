/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * 诊疗目录批量导入字面量（镜像药品/耗材目录导入）。
 *
 * <p>模板 v1.0：与「新增诊疗」手工表单可填字段对齐（16 列）；不含 busNo/子项套餐/库存单位。</p>
 */
public interface ServiceCatalogImportConstants {

    /** 模板业务编码。 */
    String TEMPLATE_CODE = "SVC_CATALOG_IMPORT";

    /** 当前模板版本。 */
    String TEMPLATE_VERSION = "v1.0";

    /** 指导页 Sheet 名。 */
    String SHEET_GUIDE = "①从这里开始";

    /** 数据录入 Sheet 名。 */
    String SHEET_DATA = "②诊疗目录";

    /** 下拉可选值 Sheet 名。 */
    String SHEET_OPTIONS = "③可选值一览";

    /** 隐藏系统元数据 Sheet 名。 */
    String SHEET_SYS = "_sys";

    /** 字段表头行（0-based）。 */
    int HEADER_ROW_INDEX = 1;

    /** 用户数据起始行（0-based，对应 Excel 第 5 行）。 */
    int DATA_START_ROW_INDEX = 4;

    /**
     * 「③可选值一览」Sheet 中可选值数据起始行（0-based）。
     *
     * <p>第 0 行为各列标题；自本行起写 label 列表，供命名区域/下拉引用。</p>
     */
    int OPTION_DATA_START_ROW_INDEX = 1;

    /** 单文件有效行上限。 */
    int IMPORT_MAX_ROWS = 3000;

    /** 数据验证下拉覆盖的 Excel 最大行号（1-based）。 */
    int VALIDATION_END_ROW = 3004;

    /** 示例行诊疗名称前缀。 */
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
    String TEMPLATE_FILE_NAME = "诊疗目录导入模板_v1.0.xlsx";

    /** 导出文件名时间戳格式。 */
    String EXPORT_FILE_NAME_TIME_PATTERN = "yyyyMMddHHmmss";

    /** 导出文件名前缀。 */
    String EXPORT_FILE_NAME_PREFIX = "诊疗目录导出_";

    /** 导出唯一数据 Sheet 名。 */
    String EXPORT_SHEET_NAME = "诊疗目录";

    /** 导出首列：系统编号（busNo）。 */
    String EXPORT_BUS_NO_HEADER = "编号";

    /**
     * 导出须写 NUMERIC 的<strong>导入表头名</strong>（成本价/零售价/最高零售价）。
     *
     * <p>导出列下标 = 导入列下标 + 1（首列「编号」偏移）。</p>
     */
    String[] EXPORT_NUMERIC_HEADER_NAMES = {
        "成本价",
        "零售价",
        "最高零售价"
    };

    /** 单次导出最大行数。 */
    int EXPORT_MAX_ROWS = 3000;

    /** xlsx MIME。 */
    String XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** 批次号前缀。 */
    String BATCH_NO_PREFIX = "SVC-IMP-";

    /**
     * 字段表头规范名（去 * 后，列序即指纹源）。共 16 列。
     *
     * <p>A 基础 0～4；B 计价 5～7；C 医保与说明 8～15。</p>
     */
    String[] COLUMN_HEADERS = {
        // A 基础信息（5）
        "诊疗名称",
        "目录分类",
        "业务类型",
        "销售/计价单位",
        "财务类型",
        // B 计价（3）
        "成本价",
        "零售价",
        "最高零售价",
        // C 医保与说明（8）
        "医保标志",
        "医保编号",
        "医保类别",
        "医保对码标记",
        "医保等级",
        "病案结算类型",
        "院内旧编码",
        "描述"
    };

    /** 必填列规范名（用于表头 * 与校验）。 */
    String[] REQUIRED_HEADERS = {
        "诊疗名称",
        "目录分类",
        "业务类型",
        "销售/计价单位",
        "财务类型",
        "零售价"
    };

    // —— 业务错误码（镜像 DEV_CAT_IMP_* 编号） ——

    String ERR_FILE_OPEN = "SVC_CAT_IMP_101";
    String ERR_SHEET_MISSING = "SVC_CAT_IMP_102";
    String ERR_TEMPLATE_VERSION = "SVC_CAT_IMP_103";
    String ERR_COLUMN_FINGERPRINT = "SVC_CAT_IMP_104";
    String ERR_NO_DATA = "SVC_CAT_IMP_201";
    String ERR_TOO_MANY_ROWS = "SVC_CAT_IMP_202";
    String ERR_REQUIRED = "SVC_CAT_IMP_301";
    String ERR_NUMBER = "SVC_CAT_IMP_302";
    String ERR_TERMINOLOGY = "SVC_CAT_IMP_303";
    String ERR_LIMIT_PRICE = "SVC_CAT_IMP_305";
    String ERR_DUP_FILE = "SVC_CAT_IMP_306";
    String ERR_DUP_DB = "SVC_CAT_IMP_307";
    String ERR_WRITE = "SVC_CAT_IMP_401";
    String ERR_TOKEN = "SVC_CAT_IMP_410";
}
