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

    /** 绑定耗材执行直发批号占位（库存/批号匹配由耗材发放页统一处理）。 */
    String INPATIENT_NURSE_EXECUTE_DEVICE_LOT_PLACEHOLDER = "NURSE_STATION_LOT_PENDING";

    /** 绑定耗材执行直发追溯号占位（库存由其他页面统一处理）。 */
    String INPATIENT_NURSE_EXECUTE_DEVICE_TRACE_PLACEHOLDER = "IP-NUR-EXE-TRACE-PENDING";

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
}
