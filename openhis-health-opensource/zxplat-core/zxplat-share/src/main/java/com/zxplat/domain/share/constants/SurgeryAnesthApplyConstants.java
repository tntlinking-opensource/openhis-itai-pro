/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 住院医生站手术与麻醉申请（AS-IP-DOR-012）场景常量。
 *
 * <p>行种类、侧别、表单种类与用户提示文案；禁止在 AppService 内散落字面量。</p>
 * <p>开立口径：手术仅术式 + 术前嘱托；麻醉仅麻醉项目 + 麻醉药品（平级）；附加/加收由护士划价。</p>
 * <p>手麻申请唯一 basedOn 主子：术前嘱托 → 术式 SR；麻醉项目/药品互不 basedOn、也不挂术式。</p>
 * <p>术式行：{@link #ACTIVITY_ID_PLACEHOLDER} + {@code procedure_definition_id}。</p>
 * <p>麻醉申请单号前缀 {@link AggregateTypeConstants#ANESTHESIA_APPLICATION}（AA），与手术/检验等 AR 分序列；
 * 仅开药时药品 {@code prescriptionNo} 亦写 AA 单号，按前缀区分，不落空壳 SR。</p>
 */
public interface SurgeryAnesthApplyConstants {

    /** 术式行（国临 PD；activity_id=0 占位，本行不计费）。 */
    String LINE_KIND_PROCEDURE = "PROCEDURE";

    /**
     * 术前嘱托行（类目 52；{@code basedOn} = 对应术式 SR；执行不计费）。
     *
     * <p>手麻开立唯一允许的 basedOn 主子关系。</p>
     */
    String LINE_KIND_ENTRUST = "ENTRUST";

    /**
     * 加收行（普通 AD，basedOn 术式/麻醉 SR；执行即计费）。
     *
     * <p>仅护士划价/历史读回使用；医生站开立 Gate 拒绝本种类。</p>
     */
    String LINE_KIND_SURCHARGE = "SURCHARGE";

    /**
     * 无诊疗计费锚时的 activity_id 占位（与转科/出院一致）。
     *
     * <p>术式行、嵌套麻醉空壳使用；执行侧遇术式行（有 procedure_definition_id）跳过建账。</p>
     */
    long ACTIVITY_ID_PLACEHOLDER = 0L;

    /** 麻醉项目行。 */
    String LINE_KIND_ANESTHESIA = "ANESTHESIA";

    /** 麻醉药品行（MedicationRequest，basedOn 麻醉主项 SR）。 */
    String LINE_KIND_MEDICATION = "MEDICATION";

    /** 禁止开立的耗材行种类（Gate 拒绝）。 */
    String LINE_KIND_CONSUMABLE = "CONSUMABLE";

    /** 加收检索侧：手术侧。 */
    String SIDE_SURGERY = "SURGERY";

    /** 加收检索侧：麻醉侧。 */
    String SIDE_ANESTHESIA = "ANESTHESIA";

    /** 选品种类：麻醉主项（侧别类目 AD；手术侧开立不走本检索，术式走 PD）。 */
    String ITEM_KIND_MAIN = "MAIN";

    /**
     * 选品种类：附加项目（已废弃伪需求；开立检索 Gate 拒绝）。
     *
     * <p>常量保留仅兼容旧入参识别后拒绝；加收请护士划价。</p>
     */
    String ITEM_KIND_EXTRA = "EXTRA";

    /** 选品种类：麻醉药品（药品定义全量检索）。 */
    String ITEM_KIND_MEDICATION = "MEDICATION";

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

    /** 选品种类非法（开立仅 MAIN / MEDICATION；EXTRA 已废弃）。 */
    String MSG_ITEM_KIND_INVALID = "选品种类仅允许 MAIN 或 MEDICATION";

    /** 医生站开立禁止附加行（伪需求已废弃；加收由护士划价）。 */
    String MSG_SURCHARGE_FORBIDDEN = "手术与麻醉开立不允许添加附加项目，其他加收请护士划价";

    /** 术前嘱托正文为空。 */
    String MSG_ENTRUST_CONTENT_REQUIRED = "请填写术前嘱托内容";

    /** 术前嘱托未指定挂靠术式。 */
    String MSG_ENTRUST_PARENT_REQUIRED = "术前嘱托须挂靠一条术式";

    /** 术前嘱托挂靠的术式排序号在本单不存在。 */
    String MSG_ENTRUST_PARENT_NOT_FOUND = "术前嘱托挂靠的术式不存在";

    /** 表单种类非法。 */
    String MSG_FORM_KIND_INVALID = "表单种类仅允许 SURGERY 或 ANESTHESIA";

    /** 行种类非法。 */
    String MSG_LINE_KIND_INVALID = "行种类非法";

    /** 术式行缺 PD。 */
    String MSG_PROCEDURE_DEFINITION_REQUIRED = "术式行须选择国临手术定义";

    /** 加收/麻醉行计费 AD 为空。 */
    String MSG_ACTIVITY_DEFINITION_REQUIRED = "计费诊疗项目不能为空";

    /** 麻醉药品行缺药品定义。 */
    String MSG_MEDICATION_DEFINITION_REQUIRED = "麻醉药品行须选择药品";

    /** 麻醉项目/药品行缺频次。 */
    String MSG_RATE_CODE_REQUIRED = "请选择频次";

    /** 药品行缺用法。 */
    String MSG_METHOD_CODE_REQUIRED = "请选择用法";

    /** 药品行缺剂量。 */
    String MSG_DOSE_REQUIRED = "请填写剂量";

    /** 药品行缺单次剂量单位。 */
    String MSG_DOSE_UNIT_REQUIRED = "请选择剂量单位";

    /** 术式默认频次展示名（临时立即）。 */
    String DEFAULT_PROCEDURE_RATE_LABEL = "立即";

    /**
     * 麻醉药品默认用法展示名（静脉注射）。
     *
     * <p>开立默认与空 methodCode 回落；不用药品目录常见「静脉滴注」。
     * 麻醉申请执行侧不走 USAGE/ACTIVITY 自动带（见护士站 ScopeSupport）。</p>
     */
    String DEFAULT_MEDICATION_METHOD_LABEL = "静脉注射";

    /**
     * 麻醉药品默认用法术语 code（method_code=404；术语未配「静脉注射」label 时硬回落）。
     */
    String DEFAULT_MEDICATION_METHOD_CODE = "404";

    /** 数量非法。 */
    String MSG_QUANTITY_INVALID = "开立数量必须大于 0";

    /** 单位为空。 */
    String MSG_UNIT_CODE_REQUIRED = "开立单位不能为空";

    /**
     * 国临术式未启用或不可开立（历史文案键保留，语义已不再要求 PD↔AD 对码）。
     */
    String MSG_PD_ACTIVITY_MISMATCH = "国临手术定义不存在或未启用";

    /** 国临术式未启用。 */
    String MSG_PROCEDURE_DEFINITION_UNAVAILABLE = "国临手术定义不存在或未启用";

    /** AD 不存在或已停用。 */
    String MSG_ACTIVITY_DEFINITION_UNAVAILABLE = "诊疗项目不存在或已停用";

    /** 申请单不存在。 */
    String MSG_APPLY_FORM_NOT_FOUND = "申请单不存在";

    /** 非草稿不可改存。 */
    String MSG_NOT_DRAFT = "仅草稿态可改存，请先撤回";

    /** 非草稿不可删除。 */
    String MSG_NOT_DRAFT_FOR_DELETE = "仅草稿态可删除，已签发请先撤回";

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

    /** 拟手术/麻醉计划时间早于入院（BR-IP-SUR-TIME-001）。 */
    String MSG_PLANNED_TIME_BEFORE_ADMISSION = "拟手术时间不能早于入院时间";

    /** 拟手术/麻醉计划时间晚于出院（BR-IP-SUR-TIME-001）。 */
    String MSG_PLANNED_TIME_AFTER_DISCHARGE = "拟手术时间不能晚于出院时间";

    /** 麻醉申请（含手术嵌套麻醉）须指定麻醉医师。 */
    String MSG_ANESTHETIST_REQUIRED = "请选择麻醉医师";
}
