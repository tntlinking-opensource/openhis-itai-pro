/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.domain.dict;

/**
 * CRB 编码对照领域常量（错误文案与默认值）。
 */
public final class CrbDictConstants {

    private CrbDictConstants() {
    }

    /** 国家码与名称须同时填写或同时清空。 */
    public static final String MSG_CODE_NAME_PAIR_REQUIRED = "国家码与名称须同时填写或同时清空";

    /** 租户或业务码缺失。 */
    public static final String MSG_TENANT_OR_BUS_NO_REQUIRED = "租户或院内业务码不能为空";

    /** 对照行不存在或已删除。 */
    public static final String MSG_MAP_NOT_FOUND = "对照行不存在或已删除";

    /** 同租户业务码唯一冲突。 */
    public static final String MSG_DUPLICATE_BUS_NO = "该院内编码已存在对照";

    /** 检验结果形态非法。 */
    public static final String MSG_INVALID_RESULT_SHAPE = "检验结果形态仅允许 QUAL 或 QUANT";

    /** 导入命令行列表为空。 */
    public static final String MSG_IMPORT_ROWS_REQUIRED = "导入行列表不能为空";

    /** 检验项目默认启用推送。 */
    public static final boolean DEFAULT_LAB_PUSH_ENABLED = true;

    /** 单次导入行数上限（AS-CRB-DICT NFR）。 */
    public static final int MAX_IMPORT_ROWS = 5000;

    /** 分页默认每页条数。 */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /** 分页最大每页条数。 */
    public static final int MAX_PAGE_SIZE = 100;
}
