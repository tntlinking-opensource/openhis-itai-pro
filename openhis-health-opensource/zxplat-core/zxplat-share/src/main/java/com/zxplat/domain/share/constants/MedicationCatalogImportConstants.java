/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 药品目录批量导入字面量（AS-BAS-CAT-002 / SDD CAT-IMP-SDD-R-002～003）。
 *
 * <p>模板 v1.0：56 列，对齐新增药品表单分区；库存/价格列含括号说明（定价基准单位、包装单位、库存单位）。
 * 仅接受当前版本；旧文件须重新下载模板。</p>
 */
public interface MedicationCatalogImportConstants {

    /** 模板业务编码。 */
    String TEMPLATE_CODE = "MED_CATALOG_IMPORT";

    /** 当前模板版本（新下载）。 */
    String TEMPLATE_VERSION = "v1.0";

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

    /** 库内判重 token 分段符（名称、厂家主键、规格）。 */
    char DUP_TOKEN_SEPARATOR = '\u0001';

    /** 库内判重：院内旧编码键前缀，后接 trim 后的旧编码。 */
    String DUP_TOKEN_LEGACY_PREFIX = "L:";

    /** 库内判重：名称键前缀，后接名称、厂家主键、归一化规格。 */
    String DUP_TOKEN_NAME_PREFIX = "N:";

    /**
     * 院内旧编码判重 token（机构内旧编码唯一，规格与厂家不参与）。
     *
     * @param legacyCode 院内旧编码
     * @return {@code L:} + trim 后的编码；空白时返回 null
     */
    static String legacyDuplicateToken(String legacyCode) {
        String legacy = MedicationCatalogImportConstants.trimDuplicatePart(legacyCode);
        if (legacy.isEmpty()) {
            return null;
        }
        return DUP_TOKEN_LEGACY_PREFIX + legacy;
    }

    /**
     * 无旧编码时的名称判重 token。厂家段必须是厂家主键字符串，空主键用空串。
     *
     * @param name                药品名称
     * @param manufacturerSegment 厂家主键字符串；尚未建档时调用方不要传入文案
     * @param totalVolume         规格
     * @return {@code N:} + 名称 + 分隔符 + 厂家段 + 分隔符 + 归一化规格；三段皆空时返回 null
     */
    static String nameDuplicateToken(String name, String manufacturerSegment, String totalVolume) {
        String normalizedName = MedicationCatalogImportConstants.trimDuplicatePart(name);
        String normalizedManufacturer = MedicationCatalogImportConstants.trimDuplicatePart(manufacturerSegment);
        String normalizedVolume = MedicationCatalogImportConstants.normalizeDuplicateVolume(totalVolume);
        if (normalizedName.isEmpty() && normalizedManufacturer.isEmpty() && normalizedVolume.isEmpty()) {
            return null;
        }
        return DUP_TOKEN_NAME_PREFIX
            + normalizedName
            + DUP_TOKEN_SEPARATOR
            + normalizedManufacturer
            + DUP_TOKEN_SEPARATOR
            + normalizedVolume;
    }

    /**
     * 从名称判重 token 取出药品名称，供按名称批量加载候选目录。
     *
     * @param token {@link #nameDuplicateToken} 的结果
     * @return trim 后的名称；不是名称键或名称为空时返回 null
     */
    static String nameFromDuplicateToken(String token) {
        if (token == null || !token.startsWith(DUP_TOKEN_NAME_PREFIX)) {
            return null;
        }
        String body = token.substring(DUP_TOKEN_NAME_PREFIX.length());
        String name = body;
        int separator = body.indexOf(DUP_TOKEN_SEPARATOR);
        if (separator >= 0) {
            name = body.substring(0, separator);
        }
        if (name.isEmpty()) {
            return null;
        }
        return name;
    }

    /**
     * 判重分段 trim；null 当空串。
     *
     * @param value 原始分段
     * @return 非 null
     */
    private static String trimDuplicatePart(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    /**
     * 规格归一化：全角冒号、全角点转为半角，避免同药因标点差异被当成新品。
     *
     * @param totalVolume 规格原文
     * @return 非 null
     */
    private static String normalizeDuplicateVolume(String totalVolume) {
        String trimmed = MedicationCatalogImportConstants.trimDuplicatePart(totalVolume);
        if (trimmed.isEmpty()) {
            return "";
        }
        return trimmed.replace('：', ':').replace('．', '.');
    }

    /** 错误标注文件 token 有效分钟数。 */
    int ERROR_FILE_TTL_MINUTES = 30;

    /** 上传/错误文件最大字节数（15MB）。 */
    long ERROR_FILE_MAX_BYTES = 15L * 1024L * 1024L;

    /** 下载模板默认文件名。 */
    String TEMPLATE_FILE_NAME = "药品目录导入模板_v1.0.xlsx";

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
        "购入价（包装单位）",
        "零售价（包装单位）",
        "最高零售价（包装单位）",
        "警戒线最低值（库存单位）",
        "警戒线最高值（库存单位）"
    };

    /** 单次导出最大行数（与导入上限对齐，避免同步导出撑爆内存）。 */
    int EXPORT_MAX_ROWS = 3000;

    /** xlsx MIME。 */
    String XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** 批次号前缀。 */
    String BATCH_NO_PREFIX = "MED-IMP-";

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
        "包装单位（定价基准单位）",
        "包装/拆零倍数",
        "追溯码单位",
        "追溯码/拆零倍数",
        "库存单位",
        "库存/拆零倍数",
        "购入价（包装单位）",
        "零售价（包装单位）",
        "最高零售价（包装单位）",
        "门诊拆分属性",
        "住院临时医嘱拆分属性",
        "警戒线最低值（库存单位）",
        "警戒线最高值（库存单位）",
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
        "包装单位（定价基准单位）",
        "包装/拆零倍数",
        "追溯码单位",
        "追溯码/拆零倍数",
        "库存单位",
        "库存/拆零倍数",
        "购入价（包装单位）",
        "零售价（包装单位）",
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
