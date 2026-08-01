/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.share.constants;

/**
 * 医保模块业务错误码。
 */
public final class ChsErrorCodes {

    public static final String CONTRACT_INVALID = "INS-C01";
    public static final String CONTRACT_NOT_FOUND = "INS-C02";
    public static final String PLATFORM_FAILED = "INS-C03";
    public static final String FILE_PARSE_FAILED = "INS-C04";
    public static final String MAPPING_FAILED = "INS-C05";
    public static final String SIGN_IN_FAILED = "INS-C06";
    /** 同一 payment 对应多笔医保结算，无法唯一定位 */
    public static final String SETTLEMENT_AMBIGUOUS = "INS-C07";
    /** 结算领域不变量违反（状态机/金额恒等式/2207 透传） */
    public static final String SETTLEMENT_INVARIANT = "INS-C08";
    /** 明细审核入参不合法（缺 trigScen/patient/warn 等） */
    public static final String AUDIT_PARAM_INVALID = "INS-C09";
    /** 明细审核 syscode 未配置且命令未传（产品 Q12） */
    public static final String AUDIT_SYSCODE_REQUIRED = "INS-C10";

    /** 医保文件格式不支持或损坏，须院外洗成标准落地包后再导入 */
    public static final String NOTICE_FILE_UNSUPPORTED = "NOTICE_FILE_UNSUPPORTED";
    /** 标准落地包解析失败（缺列/坏 CSV/坏 meta） */
    public static final String NOTICE_PARSE_FAILED = "NOTICE_PARSE_FAILED";
    /** 相同内容 hash 已入库，禁止重复导入（除非 force） */
    public static final String NOTICE_DUPLICATE_HASH = "NOTICE_DUPLICATE_HASH";
    /** 预览令牌不存在或已过期 */
    public static final String NOTICE_PREVIEW_EXPIRED = "NOTICE_PREVIEW_EXPIRED";
    /** 标准落地包存储失败 */
    public static final String NOTICE_STORE_FAILED = "NOTICE_STORE_FAILED";
    /** 通知文件头不存在 */
    public static final String NOTICE_DOC_NOT_FOUND = "NOTICE_DOC_NOT_FOUND";
    /** 应用被拒绝（未二次确认、文件已撤销等） */
    public static final String NOTICE_APPLY_DENIED = "NOTICE_APPLY_DENIED";
    /** 应用过程失败（关键写库异常） */
    public static final String NOTICE_APPLY_FAILED = "NOTICE_APPLY_FAILED";
    /** 待办不存在 */
    public static final String NOTICE_TODO_NOT_FOUND = "NOTICE_TODO_NOT_FOUND";
    /** 待办已关闭 */
    public static final String NOTICE_TODO_CLOSED = "NOTICE_TODO_CLOSED";
    /** 医保文件接口参数不合法（如关闭待办未填原因） */
    public static final String NOTICE_PARAM_INVALID = "NOTICE_PARAM_INVALID";
    /** 合规校验执行失败 */
    public static final String NOTICE_COMPLIANCE_FAILED = "NOTICE_COMPLIANCE_FAILED";

    /** 工具类禁止实例化 */
    private ChsErrorCodes() {
    }
}
