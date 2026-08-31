/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * 药品目录批量导入字面量（AS-BAS-CAT-002 / SDD CAT-IMP-SDD-R-002～003）。
 *
 * <p>模板 v1.7：相对 v1.6 在业务信息末追加「国家重点监控药品」「集采药品」（56 列）；导入仍兼容 v1.6 / v1.5 / v1.4。
 * 相对 v1.5 在包装单位后补回「追溯码单位」「追溯码/拆零倍数」。无追溯码列时默认与包装单位同阶。</p>
 */
public interface MedicationCatalogImportConstants {

    /** 模板业务编码。 */
    String TEMPLATE_CODE = "MED_CATALOG_IMPORT";

    /** 当前模板版本（新下载）。 */
    String TEMPLATE_VERSION = "v1.7";

    /** 兼容导入：v1.6（54 列，无国家重点监控/集采两列）。 */
    String TEMPLATE_VERSION_V16 = "v1.6";

    /** 兼容导入：v1.5（无追溯码列，表头「输液标记」）。 */
    String TEMPLATE_VERSION_V15 = "v1.5";

    /** 兼容导入：v1.4（无追溯码列，表头「注射药品」）。 */
    String TEMPLATE_VERSION_V14 = "v1.4";

    /** 指导页 Sheet 名。 */
    String SHEET_GUIDE = "①从这里开始";

    /** 数据录入 Sheet 名。 */
    String SHEET_DATA = "②药品目录";

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

    /** 示例行药品名称前缀。 */
    String EXAMPLE_NAME_PREFIX = "[示例]";

    /** 错误标注列标题。 */
    String ERROR_COLUMN_TITLE = "错误说明";

    /** 院内旧编码写入 definition 的前缀。 */
    String LEGACY_CODE_DEFINITION_PREFIX = "院内旧编码=";

    /** 错误标注文件 token 有效分钟数。 */
    int ERROR_FILE_TTL_MINUTES = 30;

    /** 上传/错误文件最大字节数（15MB）。 */
    long ERROR_FILE_MAX_BYTES = 15L * 1024L * 1024L;

    /** 下载模板默认文件名。 */
    String TEMPLATE_FILE_NAME = "药品目录导入模板_v1.7.xlsx";

    /** 导出文件名时间戳格式。 */
    String EXPORT_FILE_NAME_TIME_PATTERN = "yyyyMMddHHmmss";

    /** 导出文件名前缀（后缀拼时间戳 + .xlsx）。 */
    String EXPORT_FILE_NAME_PREFIX = "药品目录导出_";

    /** 导出所使用的唯一数据 Sheet 名（非导入多 Sheet 结构）。 */
    String EXPORT_SHEET_NAME = "药品目录";

    /**
     * 导出首列：系统药品编号（busNo）；导入模板无此列（导入由系统采番）。
     */
    String EXPORT_BUS_NO_HEADER = "编号";

    /**
     * 导出须写 NUMERIC 的<strong>导入表头名</strong>（倍率/剂量/价格/警戒线）。
     *
     * <p>导出列下标 = 导入列下标 + 1（首列「编号」偏移）；编号列禁止进此集合。</p>
     */
    String[] EXPORT_NUMERIC_HEADER_NAMES = {
        "剂量/拆零倍数",
        "单次剂量",
        "单次最小剂量",
        "单次最大剂量",
        "采购/拆零倍数",
        "包装/拆零倍数",
        "追溯码/拆零倍数",
        "库存/拆零倍数",
        "购入价",
        "零售价",
        "最高零售价",
        "警戒线最低值",
        "警戒线最高值"
    };

    /** 单次导出最大行数（与导入上限对齐，避免同步导出撑爆内存）。 */
    int EXPORT_MAX_ROWS = 3000;

    /** xlsx MIME。 */
    String XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** 批次号前缀。 */
    String BATCH_NO_PREFIX = "MED-IMP-";

    /** 输液标记列下标（0-based，对应 {@link #COLUMN_HEADERS}）。 */
    int COL_INJECT_FLAG = 18;

    /** 追溯码单位列下标（0-based）。 */
    int COL_TRACE_UNIT = 35;

    /** 追溯码/拆零倍数列下标（0-based）。 */
    int COL_TRACE_RATIO = 36;

    /** v1.4 旧表头名（与 {@link #COL_INJECT_FLAG} 同列）。 */
    String HEADER_INJECT_FLAG_LEGACY = "注射药品";

    /**
     * 字段表头规范名（去 * 后，列序即指纹源）。共 56 列，对齐新增药品表单分区。
     *
     * <p>A 基础 0～11；B 临床 12～29；C 库存单位 30～45；D 业务 46～55。</p>
     */
    String[] COLUMN_HEADERS = {
        // A 基础信息（12）
        "药品名称",
        "商品名",
        "药品分类",
        "生产厂家",
        "财务类型",
        "批准文号",
        "贯标国家编码",
        "医保编码",
        "医保等级",
        "病案结算类型",
        "医保对码",
        "医保类别",
        // B 临床与规格（18）
        "药品性质",
        "规格",
        "剂量单位",
        "剂量/拆零倍数",
        "剂型",
        "皮试判别",
        "输液标记",
        "用量限定",
        "默认用法",
        "默认频次",
        "单次剂量",
        "单次最小剂量",
        "单次最大剂量",
        "用法说明",
        "限制使用范围",
        "药品说明",
        "DDD值",
        "DDD单位",
        // C 库存与单位（16）
        "采购单位",
        "拆零单位",
        "采购/拆零倍数",
        "包装单位",
        "包装/拆零倍数",
        "追溯码单位",
        "追溯码/拆零倍数",
        "库存单位",
        "库存/拆零倍数",
        "购入价",
        "零售价",
        "最高零售价",
        "门诊拆分属性",
        "住院临时医嘱拆分属性",
        "警戒线最低值",
        "警戒线最高值",
        // D 业务信息（10）
        "院内旧编码",
        "69码",
        "基药标识",
        "儿童用药标志",
        "自制药标识",
        "限制使用",
        "抗生素",
        "处方药标志",
        "国家重点监控药品",
        "集采药品"
    };

    /** 必填列规范名（用于表头 * 与校验，对齐手工新建表单）。 */
    String[] REQUIRED_HEADERS = {
        "药品名称",
        "商品名",
        "药品分类",
        "生产厂家",
        "财务类型",
        "药品性质",
        "规格",
        "剂量单位",
        "剂量/拆零倍数",
        "剂型",
        "采购单位",
        "拆零单位",
        "采购/拆零倍数",
        "包装单位",
        "包装/拆零倍数",
        "追溯码单位",
        "追溯码/拆零倍数",
        "库存单位",
        "库存/拆零倍数",
        "购入价",
        "零售价",
        "门诊拆分属性",
        "住院临时医嘱拆分属性"
    };

    // —— 业务错误码 ——

    String ERR_FILE_OPEN = "MED_CAT_IMP_101";
    String ERR_SHEET_MISSING = "MED_CAT_IMP_102";
    String ERR_TEMPLATE_VERSION = "MED_CAT_IMP_103";
    String ERR_COLUMN_FINGERPRINT = "MED_CAT_IMP_104";
    String ERR_NO_DATA = "MED_CAT_IMP_201";
    String ERR_TOO_MANY_ROWS = "MED_CAT_IMP_202";
    String ERR_REQUIRED = "MED_CAT_IMP_301";
    String ERR_NUMBER = "MED_CAT_IMP_302";
    String ERR_TERMINOLOGY = "MED_CAT_IMP_303";
    String ERR_RATIO = "MED_CAT_IMP_304";
    String ERR_LIMIT_PRICE = "MED_CAT_IMP_305";
    String ERR_DUP_FILE = "MED_CAT_IMP_306";
    String ERR_DUP_DB = "MED_CAT_IMP_307";
    String ERR_WRITE = "MED_CAT_IMP_401";
    String ERR_TOKEN = "MED_CAT_IMP_410";
}
