/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.share.constants;

import java.math.BigDecimal;

/**
 * 住院护士站共享常量（AS-IP-NUR-001、AS-IP-NUR-002）。
 *
 * <p>任务筛选 code 由 App 解析；读模型 {@code nurseStationTaskFilter} 仅透传字符串，不在读侧消费。
 * 入出转写操作类型与 {@code EX-IP-ATD-*} 消息码供 {@code NurseStationATDManagerAppService} 使用。</p>
 */
public interface InpatientNurseStationConstants {

    /** 待校对（{@code pendingAuditCount > 0}）。 */
    String TASK_FILTER_PENDING_AUDIT = "pending-audit";

    /** 待执行（{@code pendingExecuteCount > 0}）。 */
    String TASK_FILTER_PENDING_EXECUTE = "pending-execute";

    /** 已处理（待校对与待执行均为 0）。 */
    String TASK_FILTER_PROCESSED = "processed";

    /** 低余额预警阈值（元，BR-IP-NUR-006）。 */
    BigDecimal LOW_BALANCE_WARNING_THRESHOLD = new BigDecimal("500");

    /** 树/列表/详情查询失败。 */
    String EX_IP_NUR_101 = "EX-IP-NUR-101";

    /** 详情：就诊不在护士站可见范围。 */
    String EX_IP_NUR_102 = "EX-IP-NUR-102";

    /** 患者信息维护失败（含领域校验、状态不允许）。 */
    String EX_IP_NUR_201 = "EX-IP-NUR-201";

    /** 入出转：床位列表加载失败（AS-IP-NUR-002 IF-IP-NUR-ATD-01）。 */
    String EX_IP_ATD_101 = "EX-IP-ATD-101";

    /** 入出转：目标床已占或状态不允许（AS-IP-NUR-002 IF-IP-NUR-ATD-02）。 */
    String EX_IP_ATD_201 = "EX-IP-ATD-201";

    /** 入出转：分床/换床/互换持久化失败（AS-IP-NUR-002 IF-IP-NUR-ATD-02）。 */
    String EX_IP_ATD_202 = "EX-IP-ATD-202";

    /** 入出转：待执行医嘱列表失败（IF-IP-NUR-ATD-03）。 */
    String EX_IP_ATD_103 = "EX-IP-ATD-103";

    /** 入出转：待发/待退药列表失败（IF-IP-NUR-ATD-04）。 */
    String EX_IP_ATD_104 = "EX-IP-ATD-104";

    /** 出院门禁：长期医嘱未停（IF-IP-NUR-ATD-05）。 */
    String EX_IP_ATD_301 = "EX-IP-ATD-301";

    /** 出院门禁：临时医嘱未执行完（IF-IP-NUR-ATD-05）。 */
    String EX_IP_ATD_302 = "EX-IP-ATD-302";

    /** 出院门禁：待发/待退药未处理（IF-IP-NUR-ATD-05）。 */
    String EX_IP_ATD_303 = "EX-IP-ATD-303";

    /** 办理出院：状态不允许（IF-IP-NUR-ATD-05）。 */
    String EX_IP_ATD_304 = "EX-IP-ATD-304";

    /** 办理出院：未待出院（IF-IP-NUR-ATD-05）。 */
    String EX_IP_ATD_305 = "EX-IP-ATD-305";

    /** 办理出院：持久化失败（IF-IP-NUR-ATD-05）。 */
    String EX_IP_ATD_306 = "EX-IP-ATD-306";

    /** 清床：未结算（IF-IP-NUR-ATD-06）。 */
    String EX_IP_ATD_401 = "EX-IP-ATD-401";

    /** 清床：位置/床态异常（IF-IP-NUR-ATD-06）。 */
    String EX_IP_ATD_402 = "EX-IP-ATD-402";

    /** 清床：展示账户余额非零（IF-IP-NUR-ATD-06，BR-IP-ATD-016）。 */
    String EX_IP_ATD_403 = "EX-IP-ATD-403";

    /** 出院召回：当前状态不允许（IF-IP-NUR-ATD-07）。 */
    String EX_IP_ATD_501 = "EX-IP-ATD-501";

    /** 出院召回：反结失败（IF-IP-NUR-ATD-07）。 */
    String EX_IP_ATD_502 = "EX-IP-ATD-502";

    /** 出院召回：无出院嘱或不可退回（IF-IP-NUR-ATD-07）。 */
    String EX_IP_ATD_503 = "EX-IP-ATD-503";

    /** 出院召回：领域持久化失败（IF-IP-NUR-ATD-07）。 */
    String EX_IP_ATD_504 = "EX-IP-ATD-504";

    /** 护士站列表状态筛选：已出院结算（{@code COMPLETED} ∪ {@code DISCHARGED}，AS-IP-NUR-001 BR-IP-NUR-012）。 */
    String STATUS_FILTER_SETTLED_DISCHARGED = "SETTLED_DISCHARGED";

    /** 床位列表面板：入科 Tab（IF-IP-NUR-ATD-01）。 */
    String ATD_PANEL_ADMISSION = "admission";

    /** 床位列表面板：出 Tab（IF-IP-NUR-ATD-01）。 */
    String ATD_PANEL_DISCHARGE = "discharge";

    /** 出院待办：长期医嘱未停嘱阻塞原因（IF-IP-NUR-ATD-03）。 */
    String DISCHARGE_BLOCKING_LONG_NOT_STOPPED = "LONG_NOT_STOPPED";

    /** 出院待办：临时医嘱未执行完阻塞原因（IF-IP-NUR-ATD-03）。 */
    String DISCHARGE_BLOCKING_TEMP_NOT_COMPLETED = "TEMP_NOT_COMPLETED";

    /** 诊疗类目：护理（出院前待执行列表排除，对齐老系统）。 */
    String SERVICE_CATEGORY_NURSING = "26";

    /** 写操作类型：首次分床（IF-IP-NUR-ATD-02）。 */
    String BED_OPERATION_ASSIGN = "ASSIGN";

    /** 写操作类型：换空床（IF-IP-NUR-ATD-02）。 */
    String BED_OPERATION_TRANSFER = "TRANSFER";

    /** 写操作类型：互换床（IF-IP-NUR-ATD-02）。 */
    String BED_OPERATION_SWAP = "SWAP";

    /** 医嘱校对 Tab：未校对（ACTIVE）。 */
    String AUDIT_TAB_PENDING = "pending";

    /** 医嘱校对 Tab：已校对（VERIFIED + STOPPED）。 */
    String AUDIT_TAB_PASSED = "passed";

    /** 医嘱校对 Tab：已停嘱（STOPPED）。 */
    String AUDIT_TAB_STOPPED = "stopped";

    /** 医嘱校对 Tab：已作废（CANCELLED）。 */
    String AUDIT_TAB_SUSPENDED = "suspended";

    /** 长临筛选：全部。 */
    String THERAPY_FILTER_ALL = "all";

    /** 长临筛选：长期医嘱。 */
    String THERAPY_FILTER_LONG = "long";

    /** 长临筛选：临时医嘱。 */
    String THERAPY_FILTER_TEMP = "temp";

    /** 批量校对/退回单批上限（AS-IP-NUR-003 §7 NFR）。 */
    int AUDIT_WRITE_BATCH_MAX = 50;

    /** 诊疗类目：转科（DM-WKF-004 §4.3）。 */
    String SERVICE_CATEGORY_TRANSFER = "32";

    /** 诊疗类目：出院（DM-WKF-004 §4.3）。 */
    String SERVICE_CATEGORY_DISCHARGE = "34";

    /** 医嘱校对列表查询失败（IF-IP-AUD-01）。 */
    String EX_IP_AUD_101 = "EX-IP-AUD-101";

    /** 所选就诊均不在护士站可见范围（IF-IP-AUD-01）。 */
    String EX_IP_AUD_102 = "EX-IP-AUD-102";

    /** 校对：状态非 ACTIVE 或域断言失败（IF-IP-AUD-02）。 */
    String EX_IP_AUD_201 = "EX-IP-AUD-201";

    /** 校对：就诊不可见（IF-IP-AUD-02）。 */
    String EX_IP_AUD_202 = "EX-IP-AUD-202";

    /** 校对：转科/出院就诊联动失败（IF-IP-AUD-02）。 */
    String EX_IP_AUD_203 = "EX-IP-AUD-203";

    /** 退回：状态非 ACTIVE（IF-IP-AUD-03）。 */
    String EX_IP_AUD_301 = "EX-IP-AUD-301";

    /** 退回：就诊不可见（IF-IP-AUD-03）。 */
    String EX_IP_AUD_302 = "EX-IP-AUD-302";

    /** 医嘱执行 Tab：待执行。 */
    String EXECUTE_TAB_PENDING = "pending";

    /** 医嘱执行 Tab：已执行。 */
    String EXECUTE_TAB_EXECUTED = "executed";

    /** 医嘱执行 Tab：不执行。 */
    String EXECUTE_TAB_SKIPPED = "skipped";

    /** 医嘱执行 Tab：已取消执行。 */
    String EXECUTE_TAB_CANCELLED = "cancelled";

    /** 批量执行/不执行单批上限（AS-IP-NUR-004 §7 NFR）。 */
    int EXECUTE_WRITE_BATCH_MAX = 50;

    /** 医嘱执行列表查询失败（IF-IP-EXE-01）。 */
    String EX_IP_EXE_101 = "EX-IP-EXE-101";

    /** 所选就诊均不在护士站可见范围（IF-IP-EXE-01）。 */
    String EX_IP_EXE_102 = "EX-IP-EXE-102";

    /** 医嘱引用频次不可用（IF-IP-EXE-01）。 */
    String EX_IP_EXE_201 = "EX-IP-EXE-201";

    /** 执行时槽位非待执行（IF-IP-EXE-02）。 */
    String EX_IP_EXE_301 = "EX-IP-EXE-301";

    /** 库存 Gate 失败（IF-IP-EXE-02）。 */
    String EX_IP_EXE_302 = "EX-IP-EXE-302";

    /** 写操作就诊/请求不可见或状态非法（IF-IP-EXE-02）。 */
    String EX_IP_EXE_303 = "EX-IP-EXE-303";

    /** 执行域写失败（IF-IP-EXE-02）。 */
    String EX_IP_EXE_304 = "EX-IP-EXE-304";

    /** 不执行时槽位非 pending（IF-IP-EXE-04）。 */
    String EX_IP_EXE_401 = "EX-IP-EXE-401";

    /** 不执行携带 procedureId（IF-IP-EXE-04）。 */
    String EX_IP_EXE_402 = "EX-IP-EXE-402";

    /** 不执行域写失败（IF-IP-EXE-04）。 */
    String EX_IP_EXE_403 = "EX-IP-EXE-403";

    /** 住院护士站医嘱执行完成结果摘要（Procedure complete 门禁占位）。 */
    String INPATIENT_NURSE_EXECUTE_PROCEDURE_RESULT_TEXT = "住院护士站医嘱执行完成";

    /** 护士划价/补费写路径单批上限（AS-IP-NUR-005 §7 NFR）。 */
    int PRICING_WRITE_BATCH_MAX = 50;

    /** 已划价展示：直接划价（无 based_on_*）。 */
    String PRICING_MODE_DIRECT = "DIRECT";

    /** 已划价展示：医嘱补费（有父锚点）。 */
    String PRICING_MODE_SUPPLEMENT = "SUPPLEMENT";

    /** 划价选品/列表：定价来源未配置（IF-IP-PRC-01/03/05）。 */
    String EX_IP_PRC_101 = "EX-IP-PRC-101";

    /** 划价：就诊不在护士站可见范围（IF-IP-PRC-01～05）。 */
    String EX_IP_PRC_102 = "EX-IP-PRC-102";

    /** 直接划价批次夹带父锚点（IF-IP-PRC-02）。 */
    String EX_IP_PRC_201 = "EX-IP-PRC-201";

    /** 耗材划价未指定发放库房（IF-IP-PRC-02/04）。 */
    String EX_IP_PRC_202 = "EX-IP-PRC-202";

    /** 吸氧/心电当日时长超限（IF-IP-PRC-02/04）。 */
    String EX_IP_PRC_203 = "EX-IP-PRC-203";

    /** 吸氧计时 Gate 类型（BR-IP-PRC-010）。 */
    String DURATION_GATE_KIND_OXYGEN = "OXYGEN";

    /** 心电监测计时 Gate 类型（BR-IP-PRC-010）。 */
    String DURATION_GATE_KIND_ECG = "ECG";

    /** 吸氧当日累计时长上限（小时，对齐老系统 SpecialConstant.OXYGEN_DURATION）。 */
    int OXYGEN_DAILY_DURATION_MAX_HOURS = 20;

    /** 心电监测当日累计时长上限（小时，对齐老系统 SpecialConstant.ECG_DURATION）。 */
    int ECG_DAILY_DURATION_MAX_HOURS = 24;

    /** 划价目录/定价/数量非法（IF-IP-PRC-02/04）。 */
    String EX_IP_PRC_204 = "EX-IP-PRC-204";

    /** 划价写路径域失败整批回滚（IF-IP-PRC-02/04）。 */
    String EX_IP_PRC_205 = "EX-IP-PRC-205";

    /** 补费父医嘱非法或不可补（IF-IP-PRC-04）。 */
    String EX_IP_PRC_301 = "EX-IP-PRC-301";

    /** 取消划价未选行或同批重复 (orderType, requestId)（IF-IP-PRC-06）。 */
    String EX_IP_PRC_401 = "EX-IP-PRC-401";

    /** 取消划价目标非 nurse-pricing 或不可加载（IF-IP-PRC-06）。 */
    String EX_IP_PRC_402 = "EX-IP-PRC-402";

    /** 取消划价关联费用已结算或退费中（IF-IP-PRC-06）。 */
    String EX_IP_PRC_403 = "EX-IP-PRC-403";

    /** 取消划价耗材发放态不可回退（IF-IP-PRC-06）。 */
    String EX_IP_PRC_404 = "EX-IP-PRC-404";

    /** 取消划价域写失败整批回滚（IF-IP-PRC-06）。 */
    String EX_IP_PRC_405 = "EX-IP-PRC-405";

    /** 自动计费：就诊不在护士站可见范围（IF-IP-ARG-02～07）。 */
    String EX_IP_ARG_201 = "EX-IP-ARG-201";

    /** 自动计费：配置组合键冲突（IF-IP-ARG-03/04）。 */
    String EX_IP_ARG_301 = "EX-IP-ARG-301";

    /** 自动计费：床位服务/定价无效（IF-IP-ARG-03/04）。 */
    String EX_IP_ARG_302 = "EX-IP-ARG-302";

    /** 自动计费：规则不存在或已删除（IF-IP-ARG-04～07）。 */
    String EX_IP_ARG_401 = "EX-IP-ARG-401";

    /** 自动计费：启停状态不允许（IF-IP-ARG-06/07）。 */
    String EX_IP_ARG_501 = "EX-IP-ARG-501";

    /** 费用明细展示模式：明细（IF-IP-CDT-02）。 */
    String COST_DETAIL_PRESENTATION_MODE_DETAIL = "DETAIL";

    /** 费用明细展示模式：汇总（IF-IP-CDT-02）。 */
    String COST_DETAIL_PRESENTATION_MODE_SUMMARY = "SUMMARY";

    /** 费用明细展示模式文案：明细（IF-IP-CDT-01 初始化下发）。 */
    String COST_DETAIL_PRESENTATION_MODE_DETAIL_DISPLAY = "明细";

    /** 费用明细展示模式文案：汇总（IF-IP-CDT-01 初始化下发）。 */
    String COST_DETAIL_PRESENTATION_MODE_SUMMARY_DISPLAY = "汇总";

    /** 费用大类块：药品（BR-IP-CDT-008）。 */
    String COST_DETAIL_EXPENSE_BLOCK_MEDICATION = "MEDICATION";

    /** 费用大类块：诊疗（BR-IP-CDT-008）。 */
    String COST_DETAIL_EXPENSE_BLOCK_SERVICE = "SERVICE";

    /** 费用大类块：耗材（BR-IP-CDT-008）。 */
    String COST_DETAIL_EXPENSE_BLOCK_DEVICE = "DEVICE";

    /** 费用大类块展示：药品（BR-IP-CDT-008）。 */
    String COST_DETAIL_EXPENSE_BLOCK_MEDICATION_DISPLAY = "药品";

    /** 费用大类块展示：诊疗（BR-IP-CDT-008）。 */
    String COST_DETAIL_EXPENSE_BLOCK_SERVICE_DISPLAY = "诊疗";

    /** 费用大类块展示：耗材（BR-IP-CDT-008）。 */
    String COST_DETAIL_EXPENSE_BLOCK_DEVICE_DISPLAY = "耗材";

    /** 费用明细列表/装配失败（IF-IP-CDT-02）。 */
    String EX_IP_CDT_101 = "EX-IP-CDT-101";

    /** 费用明细：就诊不在护士站可见范围（IF-IP-CDT-02）。 */
    String EX_IP_CDT_102 = "EX-IP-CDT-102";

    /** 费用明细：presentationMode 非法（IF-IP-CDT-02）。 */
    String EX_IP_CDT_201 = "EX-IP-CDT-201";

    /** 费用明细：日期区间非法（IF-IP-CDT-02）。 */
    String EX_IP_CDT_202 = "EX-IP-CDT-202";

    /** 费用明细：账单缺产品/分类/业务时间或 service_table 无法归类（IF-IP-CDT-02）。 */
    String EX_IP_CDT_203 = "EX-IP-CDT-203";

    /** 汇总领药 Tab：西药（IF-IP-SUM-01）。 */
    String MEDICINE_CATEGORY_TAB_WEST = "west";

    /** 汇总领药 Tab：中药（IF-IP-SUM-01）。 */
    String MEDICINE_CATEGORY_TAB_TCM = "tcm";

    /** 汇总领药写路径单批上限（AS-IP-NUR-010 §7 NFR）。 */
    int SUMMARY_WRITE_BATCH_MAX = 500;

    /** 汇总领药拆零数量换算小数位（HALF_UP，对齐 BR-IP-SUM-008）。 */
    int SUMMARY_SPLIT_QUANTITY_SCALE = 6;

    /** 汇总 Tab 发放态展示：待发（SupplyDelivery PREPARATION）。 */
    String SUMMARY_DELIVERY_STATUS_PENDING_DISPLAY = "待发";

    /** 汇总 Tab 发放态展示：已发（SupplyDelivery COMPLETED）。 */
    String SUMMARY_DELIVERY_STATUS_COMPLETED_DISPLAY = "已发";

    /** 汇总 Tab 审批态展示：待发送（SupplyRequest PENDING_APPROVAL）。 */
    String SUMMARY_SUPPLY_STATUS_DRAFT_DISPLAY = "待发送";

    /** 汇总 Tab 审批态展示：待核发（SupplyRequest APPROVAL）。 */
    String SUMMARY_SUPPLY_STATUS_APPROVAL_DISPLAY = "待核发";

    /** 汇总 Tab 审批态展示：已核发（SupplyRequest AGREE）。 */
    String SUMMARY_SUPPLY_STATUS_AGREE_DISPLAY = "已核发";

    /** 汇总 Tab 审批态展示：已驳回（SupplyRequest REJECT）。 */
    String SUMMARY_SUPPLY_STATUS_REJECT_DISPLAY = "已驳回";

    /** 汇总 Tab 审批态展示：已撤回（SupplyRequest WITHDRAW）。 */
    String SUMMARY_SUPPLY_STATUS_WITHDRAW_DISPLAY = "已撤回";

    /** 汇总领药：未选患者或无权（IF-IP-SUM-01～05）。 */
    String EX_IP_SUM_101 = "EX-IP-SUM-101";

    /** 汇总领药：列表查询失败（IF-IP-SUM-01～03）。 */
    String EX_IP_SUM_102 = "EX-IP-SUM-102";

    /** 汇总领药：汇总单不存在（IF-IP-SUM-03）。 */
    String EX_IP_SUM_103 = "EX-IP-SUM-103";

    /** 汇总领药：重复汇总或非 PREPARATION（IF-IP-SUM-04）。 */
    String EX_IP_SUM_201 = "EX-IP-SUM-201";

    /** 汇总领药：单位槽/换算失败（IF-IP-SUM-04）。 */
    String EX_IP_SUM_202 = "EX-IP-SUM-202";

    /** 汇总领药：ChargeItem 缺失（IF-IP-SUM-04）。 */
    String EX_IP_SUM_203 = "EX-IP-SUM-203";

    /** 汇总领药：批量校验或域写失败（IF-IP-SUM-04）。 */
    String EX_IP_SUM_204 = "EX-IP-SUM-204";

    /** 汇总领药：发送审批失败或状态不允许（IF-IP-SUM-05）。 */
    String EX_IP_SUM_205 = "EX-IP-SUM-205";

    /** 撤销汇总失败或状态不允许（IF-IP-SUM-08）。 */
    String EX_IP_SUM_206 = "EX-IP-SUM-206";

    /** 皮试表单枚举初始化失败（IF-IP-STK-01）。 */
    String EX_IP_STK_101 = "EX-IP-STK-101";

    /** 皮试：就诊不可见（IF-IP-STK-02～04）。 */
    String EX_IP_STK_102 = "EX-IP-STK-102";

    /** 皮试列表查询失败（IF-IP-STK-02）。 */
    String EX_IP_STK_103 = "EX-IP-STK-103";

    /** 皮试保存：双签或必填状态缺失（IF-IP-STK-03）。 */
    String EX_IP_STK_201 = "EX-IP-STK-201";

    /** 皮试保存：皮试药非已校对或不在本就诊（IF-IP-STK-03）。 */
    String EX_IP_STK_202 = "EX-IP-STK-202";

    /** 皮试保存：皮试检查定价无效（IF-IP-STK-03）。 */
    String EX_IP_STK_203 = "EX-IP-STK-203";

    /** 皮试保存：重复划价/并发（IF-IP-STK-03 · BR-IP-STK-006）。 */
    String EX_IP_STK_204 = "EX-IP-STK-204";

    /** 皮试保存：v1.0 互斥码（已废止，v1.1 改用 {@link #EX_IP_STK_207}）；保留常量避免历史引用断裂。 */
    String EX_IP_STK_205 = "EX-IP-STK-205";

    /** 皮试保存：写失败（IF-IP-STK-03）。 */
    String EX_IP_STK_206 = "EX-IP-STK-206";

    /** 皮试保存：医生未开立皮试检查医嘱（IF-IP-STK-03 · BR-IP-STK-009）。 */
    String EX_IP_STK_207 = "EX-IP-STK-207";

    /** 皮试删除：记录不存在（IF-IP-STK-04）。 */
    String EX_IP_STK_301 = "EX-IP-STK-301";

    /** 皮试删除：已划价不可删（IF-IP-STK-04）。 */
    String EX_IP_STK_302 = "EX-IP-STK-302";

    /** 皮试删除：删除失败（IF-IP-STK-04）。 */
    String EX_IP_STK_303 = "EX-IP-STK-303";

    /** 批量取消执行单批上限（AS-IP-NUR-012 §7 NFR）。 */
    int ORDER_CANCEL_EXECUTE_BATCH_MAX = 50;

    /** 取消执行：未选 procedureId / 重复 / 超批 / 原因为空 / 就诊不可见（IF-IP-CXL-01）。 */
    String EX_IP_CXL_201 = "EX-IP-CXL-201";

    /** 取消执行：Procedure 不可取消或已被对冲（IF-IP-CXL-01）。 */
    String EX_IP_CXL_202 = "EX-IP-CXL-202";

    /** 取消执行：关联费用已结算或退费中（IF-IP-CXL-01）。 */
    String EX_IP_CXL_203 = "EX-IP-CXL-203";

    /** 取消执行：汇总单态并发冲突或已核发（IF-IP-CXL-01）。 */
    String EX_IP_CXL_204 = "EX-IP-CXL-204";

    /** 取消执行：域写失败（IF-IP-CXL-01）。 */
    String EX_IP_CXL_205 = "EX-IP-CXL-205";

    /** 取消执行成功默认提示（IF-IP-CXL-01）。 */
    String ORDER_CANCEL_EXECUTE_SUCCESS_MESSAGE = "取消执行成功";

    /** 取消执行成功且自动重建汇总单提示（IF-IP-CXL-01）。 */
    String ORDER_CANCEL_EXECUTE_REBUILD_SUCCESS_MESSAGE = "取消执行成功，已自动重建汇总单";

    /** 取消执行自动撤汇总时 Supply 驳回原因码（APPROVAL 路径，BR-IP-CXL-011）。 */
    String ORDER_CANCEL_EXECUTE_SUPPLY_REJECT_REASON_CODE = "AUTO_CANCEL_EXECUTE";

    /** 体温单窗口：3 天（IF-IP-TMP-01）。 */
    String TEMPERATURE_SHEET_WINDOW_3D = "3d";

    /** 体温单窗口：7 天（IF-IP-TMP-01 默认）。 */
    String TEMPERATURE_SHEET_WINDOW_7D = "7d";

    /** 体温单窗口：14 天（IF-IP-TMP-01）。 */
    String TEMPERATURE_SHEET_WINDOW_14D = "14d";

    /** 体温单打印范围：当前页（IF-IP-TMP-04）。 */
    String TEMPERATURE_SHEET_PRINT_SCOPE_CURRENT_PAGE = "CURRENT_PAGE";

    /** 体温单打印范围：全部（IF-IP-TMP-04）。 */
    String TEMPERATURE_SHEET_PRINT_SCOPE_ALL = "ALL";

    /** 体温单保存：未登录（IF-IP-TMP-03 · BR-IP-TMP-010）。 */
    String EX_IP_TMP_101 = "EX-IP-TMP-101";

    /** 体温单读/写：就诊不可见（IF-IP-TMP-02～04 · BR-IP-TMP-001）。 */
    String EX_IP_TMP_102 = "EX-IP-TMP-102";

    /** 体温单保存：日期或时点非法（IF-IP-TMP-03 · BR-IP-TMP-003）。 */
    String EX_IP_TMP_201 = "EX-IP-TMP-201";

    /** 体温单保存：无任何有效测量值（IF-IP-TMP-03 · BR-IP-TMP-004）。 */
    String EX_IP_TMP_202 = "EX-IP-TMP-202";

    /** 体温单保存：体征项编码无效（IF-IP-TMP-03）。 */
    String EX_IP_TMP_203 = "EX-IP-TMP-203";

    /** 体温单保存：写失败（IF-IP-TMP-03）。 */
    String EX_IP_TMP_204 = "EX-IP-TMP-204";

    /** 护理记录视图：时间轴（IF-IP-NRC-01）。 */
    String NURSING_RECORD_VIEW_MODE_TIMELINE = "timeline";

    /** 护理记录视图：纸质单（IF-IP-NRC-01）。 */
    String NURSING_RECORD_VIEW_MODE_PAPER = "paper";

    /** 护理记录视图「时间轴」展示文案（IF-IP-NRC-01）。 */
    String NURSING_RECORD_VIEW_MODE_TIMELINE_DISPLAY = "时间轴";

    /** 护理记录视图「纸质单」展示文案（IF-IP-NRC-01）。 */
    String NURSING_RECORD_VIEW_MODE_PAPER_DISPLAY = "纸质单";

    /** 护理记录默认周范围说明（IF-IP-NRC-01）。 */
    String NURSING_RECORD_DEFAULT_WEEK_RANGE_DESCRIPTION = "默认展示当前自然周（周一至周日）";

    /** 护理记录打印模板 code（IF-IP-NRC-06）。 */
    String NURSING_RECORD_PRINT_TEMPLATE_CODE = "nursing_record";

    /** 护理记录行类型：预置骨架行（IF-IP-NRC-04 过滤）。 */
    String NURSING_RECORD_ROW_TYPE_SKELETON = "skeleton";

    /** 护理记录行类型：纸质额外行。 */
    String NURSING_RECORD_ROW_TYPE_EXTRA = "extra";

    /** 护理记录读/写：就诊不可见（IF-IP-NRC-02～06 · BR-IP-NRC-001）。 */
    String EX_IP_NRC_102 = "EX-IP-NRC-102";

    /** 护理记录保存：记录时点缺失（IF-IP-NRC-03 · BR-IP-NRC-003）。 */
    String EX_IP_NRC_201 = "EX-IP-NRC-201";

    /** 护理记录保存：无有效内容（IF-IP-NRC-03 · BR-VS-008）。 */
    String EX_IP_NRC_202 = "EX-IP-NRC-202";

    /** 护理记录保存：体征项编码无效（IF-IP-NRC-03）。 */
    String EX_IP_NRC_203 = "EX-IP-NRC-203";

    /** 护理记录保存：写失败（IF-IP-NRC-03）。 */
    String EX_IP_NRC_204 = "EX-IP-NRC-204";

    /** 护理记录批量保存：第 N 行失败（IF-IP-NRC-04 · BR-IP-NRC-009）。 */
    String EX_IP_NRC_205 = "EX-IP-NRC-205";

    /** 护理记录删除：记录不存在（IF-IP-NRC-05）。 */
    String EX_IP_NRC_301 = "EX-IP-NRC-301";

    /** 护理记录删除：写失败（IF-IP-NRC-05）。 */
    String EX_IP_NRC_302 = "EX-IP-NRC-302";
}
