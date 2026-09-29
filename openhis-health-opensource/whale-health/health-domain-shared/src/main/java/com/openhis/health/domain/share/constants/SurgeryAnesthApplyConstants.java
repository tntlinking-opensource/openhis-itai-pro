/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * 住院医生站手术与麻醉申请（AS-IP-DOR-012）场景常量。
 *
 * <p>行种类、侧别、表单种类与用户提示文案；禁止在 AppService 内散落字面量。</p>
 */
public interface SurgeryAnesthApplyConstants {

    /** 术式行（国临 PD + 挂靠 AD）。 */
    String LINE_KIND_PROCEDURE = "PROCEDURE";

    /** 加收行（仅 AD，无 PD）。 */
    String LINE_KIND_SURCHARGE = "SURCHARGE";

    /** 麻醉项目行。 */
    String LINE_KIND_ANESTHESIA = "ANESTHESIA";

    /** 禁止开立的耗材行种类（Gate 拒绝）。 */
    String LINE_KIND_CONSUMABLE = "CONSUMABLE";

    /** 加收检索侧：手术侧。 */
    String SIDE_SURGERY = "SURGERY";

    /** 加收检索侧：麻醉侧。 */
    String SIDE_ANESTHESIA = "ANESTHESIA";

    /** 拟手术横表加载：手术申请单。 */
    String FORM_KIND_SURGERY = "SURGERY";

    /** 拟手术横表加载：麻醉申请单。 */
    String FORM_KIND_ANESTHESIA = "ANESTHESIA";

    /** 嵌套麻醉壳行默认单位（无行单位时回落）。 */
    String NEST_SHELL_DEFAULT_UNIT_CODE = "次";

    /** 未登录/未绑定医务人员。 */
    String MSG_OPERATOR_NOT_RESOLVED = "未获取到当前操作人";

    /** 就诊标识为空（EX-IP-SUR-102）。 */
    String MSG_ENCOUNTER_ID_REQUIRED = "就诊标识不能为空";

    /** 关键字为空（EX-IP-SUR-102）。 */
    String MSG_KEYWORD_REQUIRED = "检索关键字不能为空";

    /** 申请单号为空。 */
    String MSG_PRESCRIPTION_NO_REQUIRED = "申请单号不能为空";

    /** 签发码为空。 */
    String MSG_SIGN_CODE_REQUIRED = "签发码不能为空";

    /** 行列表为空或非法。 */
    String MSG_LINES_REQUIRED = "申请行不能为空";

    /** 无术式行。 */
    String MSG_PROCEDURE_LINE_REQUIRED = "手术申请至少需要一条术式行";

    /** 含耗材行。 */
    String MSG_CONSUMABLE_FORBIDDEN = "开立禁止选择耗材行";

    /** 主手术标志超过一行。 */
    String MSG_MAIN_PROCEDURE_FLAG_DUPLICATE = "同一申请单内主手术标志至多一行为真";

    /** 侧别非法。 */
    String MSG_SIDE_CODE_INVALID = "加收侧别仅允许 SURGERY 或 ANESTHESIA";

    /** 表单种类非法。 */
    String MSG_FORM_KIND_INVALID = "表单种类仅允许 SURGERY 或 ANESTHESIA";

    /** 行种类非法。 */
    String MSG_LINE_KIND_INVALID = "行种类非法";

    /** 术式行缺 PD。 */
    String MSG_PROCEDURE_DEFINITION_REQUIRED = "术式行须选择国临手术定义";

    /** 计费 AD 为空。 */
    String MSG_ACTIVITY_DEFINITION_REQUIRED = "计费诊疗项目不能为空";

    /** 数量非法。 */
    String MSG_QUANTITY_INVALID = "开立数量必须大于 0";

    /** 单位为空。 */
    String MSG_UNIT_CODE_REQUIRED = "开立单位不能为空";

    /** PD 无挂靠 AD 或不匹配。 */
    String MSG_PD_ACTIVITY_MISMATCH = "国临定义未挂靠诊疗项目或与落行计费项不一致";

    /** AD 不存在或已停用。 */
    String MSG_ACTIVITY_DEFINITION_UNAVAILABLE = "诊疗项目不存在或已停用";

    /** 申请单不存在。 */
    String MSG_APPLY_FORM_NOT_FOUND = "申请单不存在";

    /** 非草稿不可改存。 */
    String MSG_NOT_DRAFT = "仅草稿态可改存，请先撤回";

    /** 非草稿不可签发。 */
    String MSG_NOT_ALL_DRAFT_FOR_SIGN = "申请单存在非草稿行，无法整单签发";

    /** 可撤回校验失败。 */
    String MSG_NOT_WITHDRAWABLE = "当前状态不可撤回，请确认是否已执行";

    /** 嵌套麻醉：手术单不存在或不可操作。 */
    String MSG_NEST_SURGERY_NOT_FOUND = "关联手术申请单不存在或不可取消勾选";

    /** 嵌套麻醉：enabled 为空。 */
    String MSG_NEST_ENABLED_REQUIRED = "是否勾选嵌套麻醉不能为空";

    /** 关联手术 AR 不存在。 */
    String MSG_LINKED_SURGERY_NOT_FOUND = "关联的手术申请单不存在";
}
