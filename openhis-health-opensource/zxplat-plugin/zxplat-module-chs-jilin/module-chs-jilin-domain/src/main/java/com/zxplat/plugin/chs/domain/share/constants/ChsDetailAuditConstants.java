/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.share.constants;

/**
 * 明细审核（3101/3103）业务常量。
 *
 * <p>触发场景编码对齐吉林省事前联调方案表2；处置方式与提醒类型对齐国家 3103 字典。</p>
 */
public final class ChsDetailAuditConstants {

    /** 提醒类型：事前 */
    public static final String WARN_TYPE_PRIOR = "1";
    /** 提醒类型：事中（联调样例可见；W2 按场景语义赋值） */
    public static final String WARN_TYPE_INTRA = "2";

    /** 处置方式：继续执行 */
    public static final String DSPO_CONTINUE = "1";
    /** 处置方式：返回修改 */
    public static final String DSPO_RETURN_MODIFY = "2";

    /** 平台日期时间格式（3101 就诊/医嘱时间） */
    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /** 默认医嘱行为：临时医嘱 */
    public static final String DEFAULT_DRORD_BHVR = "0";
    /** 默认是否当前医嘱：是 */
    public static final String DEFAULT_CURR_DRORD_FLAG = "1";
    /** 默认长期医嘱标志：否 */
    public static final String DEFAULT_LONG_DRORD_FLAG = "0";
    /** 默认生育状态：否 */
    public static final String DEFAULT_MATN_STAS = "0";
    /** 默认报销标志：是 */
    public static final String DEFAULT_REIM_FLAG = "1";
    /** 门诊就诊类型（med_mdtrt_type） */
    public static final String MED_MDTRT_TYPE_OUTPATIENT = "2";
    /** 住院就诊类型 */
    public static final String MED_MDTRT_TYPE_INPATIENT = "1";

    /** 医保字典 GEND：男 */
    public static final String GEND_MALE = "1";
    /** 医保字典 GEND：女 */
    public static final String GEND_FEMALE = "2";
    /** 医保字典 GEND：未知 */
    public static final String GEND_UNKNOWN = "0";

    /** HIS AdministrativeGender：男 */
    public static final String HIS_GENDER_MALE = "MALE";
    /** HIS AdministrativeGender：女 */
    public static final String HIS_GENDER_FEMALE = "FEMALE";
    /** HIS AdministrativeGender：未知 */
    public static final String HIS_GENDER_UNKNOWN = "UNKNOWN";

    /** 异地结算标志：否（本地） */
    public static final String DEFAULT_OUT_SETL_FLAG = "0";
    /** 医保目录等级缺省：甲类 */
    public static final String DEFAULT_HILIST_LV = "01";
    /** 开方医师职称缺省（联调样例常见） */
    public static final String DEFAULT_DRORD_DR_PROFTTL = "1";
    /** 规格缺省占位（平台必填不可空） */
    public static final String DEFAULT_SPEC = "-";
    /** 数量单位缺省 */
    public static final String DEFAULT_SPEC_UNT = "次";
    /** 目录类别：西药 */
    public static final String HILIST_TYPE_WESTERN_DRUG = "101";
    /** 目录类别：诊疗项目 */
    public static final String HILIST_TYPE_ACTIVITY = "201";
    /** 目录类别：医用材料 */
    public static final String HILIST_TYPE_MATERIAL = "301";
    /** 收费类别缺省（与联调样例药品行一致） */
    public static final String DEFAULT_CHRG_TYPE = "05";

    /** 科室/机构码平台上限（表 116 入院科室标识；平台校验常见 1～30） */
    public static final int DEPT_CODG_MAX_LEN = 30;
    /** 医疗机构名称上限 */
    public static final int MEDINS_NAME_MAX_LEN = 200;
    /**
     * 低值耗材 {@code med_list_codg} 上传位数（长春口径：短码）。
     * <p>高值耗材传完整 {@code yb_no}；完整码同时放 {@code medins_list_codg}。</p>
     */
    public static final int DEVICE_MED_LIST_CODG_MAX_LEN = 15;

    /**
     * 门禁跳过原因：待审明细均挂自费账户（含转自费、代煎），与预结算不进 2204 同口径，不出站 3101。
     */
    public static final String SKIP_REASON_ALL_SELF_PAY =
        "待审明细均为自费账户（含转自费/代煎），跳过事前提醒";

    private ChsDetailAuditConstants() {
    }

    /**
     * 触发场景 {@code trig_scen}（联调表2 · 字符串码 1–10）。
     */
    public static final class TrigScen {
        /** 门诊挂号 */
        public static final String OP_REG = "1";
        /** 门诊收费登记 */
        public static final String OP_FEE_REG = "2";
        /** 住院登记 */
        public static final String IP_REG = "3";
        /** 住院收费登记 */
        public static final String IP_FEE_REG = "4";
        /** 住院执行医嘱 */
        public static final String IP_ORDER_EXEC = "5";
        /** 门诊结算 */
        public static final String OP_SETTLE = "6";
        /** 门诊预结算 */
        public static final String OP_PRE_SETTLE = "7";
        /** 住院结算 */
        public static final String IP_SETTLE = "8";
        /** 住院预结算 */
        public static final String IP_PRE_SETTLE = "9";
        /** 购药划卡 */
        public static final String PHARMACY_SWIPE = "10";

        private TrigScen() {
        }
    }
}
