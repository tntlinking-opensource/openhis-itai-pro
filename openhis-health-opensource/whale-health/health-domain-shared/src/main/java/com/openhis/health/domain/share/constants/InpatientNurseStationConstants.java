/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

import java.math.BigDecimal;
import java.time.LocalTime;

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

    /** 入出转：目标床已被他人预约或预约入院日未到（GAP-ATD-007）。 */
    String EX_IP_ATD_207 = "EX-IP-ATD-207";

    /** 入出转：待执行医嘱列表失败（IF-IP-NUR-ATD-03）。 */
    String EX_IP_ATD_103 = "EX-IP-ATD-103";

    /** 入出转：待发/待退药列表失败（IF-IP-NUR-ATD-04）。 */
    String EX_IP_ATD_104 = "EX-IP-ATD-104";

    /** 出院门禁：长期医嘱未停（IF-IP-NUR-ATD-05）。 */
    String EX_IP_ATD_301 = "EX-IP-ATD-301";

    /** 出院门禁：长期已停嘱仍有待执行时点（IF-IP-NUR-ATD-05）。 */
    String EX_IP_ATD_307 = "EX-IP-ATD-307";

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

    /**
     * 出院追溯提示：已核发正向发药单存在空追溯码（IF-IP-NUR-ATD-05/06 软提示码，供前端展示）。
     * <p>后端<strong>不再硬拦</strong>办理出院/清床；前端可据此提示「某日某药未分配追溯码」后仍允许清床。</p>
     */
    String EX_IP_ATD_308 = "EX-IP-ATD-308";

    /** 清床：未结算（IF-IP-NUR-ATD-06）。 */
    String EX_IP_ATD_401 = "EX-IP-ATD-401";

    /** 清床：位置/床态异常（IF-IP-NUR-ATD-06）。 */
    String EX_IP_ATD_402 = "EX-IP-ATD-402";

    /** 清床：展示账户余额非零（IF-IP-NUR-ATD-06，BR-IP-ATD-016）。 */
    String EX_IP_ATD_403 = "EX-IP-ATD-403";

    /**
     * 清床：存在未冲销的清床汇总销售 3505（IF-IP-NUR-ATD-06）。
     * <p>召回后冲销 3506 未成功时禁止再次清床，避免平台双报。</p>
     */
    String EX_IP_ATD_404 = "EX-IP-ATD-404";

    /** 出院召回：当前状态不允许（IF-IP-NUR-ATD-07）。 */
    String EX_IP_ATD_501 = "EX-IP-ATD-501";

    /** 出院召回：反结失败（IF-IP-NUR-ATD-07）。 */
    String EX_IP_ATD_502 = "EX-IP-ATD-502";

    /** 出院召回：无出院嘱或不可退回（IF-IP-NUR-ATD-07）。 */
    String EX_IP_ATD_503 = "EX-IP-ATD-503";

    /** 出院召回：领域持久化失败（历史码；写入口已迁 FIN-004 IF-IP-RCL-01）。 */
    String EX_IP_ATD_504 = "EX-IP-ATD-504";

    /** 无活动床时末次床展示前缀（召回/清床未占床轨）。 */
    String UNOCCUPIED_LAST_BED_DISPLAY_PREFIX = "原";

    /**
     * 无活动床时末次床展示后缀（召回/清床未占床轨）。
     *
     * <p>完整文案由 {@code EncounterContextQueryService} 装配为「原{房名}-{床名}（未占床）」；
     * 无病房名时退化为「原{床名}（未占床）」。</p>
     */
    String UNOCCUPIED_LAST_BED_DISPLAY_SUFFIX = "（未占床）";

    /** 床位列表面板：入科 Tab（IF-IP-NUR-ATD-01）。 */
    String ATD_PANEL_ADMISSION = "admission";

    /** 床位列表面板：出 Tab（IF-IP-NUR-ATD-01）。 */
    String ATD_PANEL_DISCHARGE = "discharge";

    /** 出院待办：长期医嘱未停嘱阻塞原因（IF-IP-NUR-ATD-03）。 */
    String DISCHARGE_BLOCKING_LONG_NOT_STOPPED = "LONG_NOT_STOPPED";

    /** 出院待办：长期已停嘱仍有待执行时点阻塞原因（IF-IP-NUR-ATD-03）。 */
    String DISCHARGE_BLOCKING_LONG_PENDING_SLOTS = "LONG_PENDING_SLOTS";

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

    /**
     * 医嘱校对 Tab：未校对。
     *
     * <p>含开嘱待校（{@code ACTIVE}）与停嘱待校（长期 {@code STOPPED} 且 {@code stop_performer_check_id} 为空）。</p>
     */
    String AUDIT_TAB_PENDING = "pending";

    /**
     * 医嘱校对 Tab：已校对。
     *
     * <p>含 {@code VERIFIED}（在执）与已停嘱校对的 {@code STOPPED}；同一条只一行，同时展示开嘱校对人与停嘱校对人。</p>
     */
    String AUDIT_TAB_PASSED = "passed";

    /**
     * @deprecated 校对页已取消独立「已停止」Tab；已停嘱校对行并入 {@link #AUDIT_TAB_PASSED}。保留常量仅防旧调用。
     */
    @Deprecated
    String AUDIT_TAB_STOPPED = "stopped";

    /**
     * @deprecated 校对页已取消「已作废」Tab。保留常量仅防旧调用。
     */
    @Deprecated
    String AUDIT_TAB_SUSPENDED = "suspended";

/** InpatientNurseStation 共享常量：filter all，值 {@code all}。 */
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

    /**
     * 诊疗类目：嘱托（DM-WKF-004 §4.3；与 {@link ServiceRequestCategoryConstants#SERVICE_CATEGORY_ENTRUST} 同码）。
     *
     * <p>住院嘱托医嘱；类型列展示「嘱托」、执行不计费（AS-IP-DOR-003 · BR-IP-ORD-028）。</p>
     */
    String SERVICE_CATEGORY_ENTRUST = "52";

    /** 护士站医嘱列表类型列：药品。 */
    String ORDER_KIND_DISPLAY_MEDICATION = "药品";

    /** 护士站医嘱列表类型列：诊疗。 */
    String ORDER_KIND_DISPLAY_SERVICE = "诊疗";

    /** 护士站医嘱列表类型列：出院医嘱（category_enum=34）。 */
    String ORDER_KIND_DISPLAY_DISCHARGE = "出院";

    /** 护士站医嘱列表类型列：转科医嘱（category_enum=32）。 */
    String ORDER_KIND_DISPLAY_TRANSFER = "转科";

    /** 护士站医嘱列表类型列：护理医嘱（category_enum=26）。 */
    String ORDER_KIND_DISPLAY_NURSING = "护理";

    /** 护士站医嘱列表类型列：嘱托医嘱（category_enum=52；BR-IP-ORD-028）。 */
    String ORDER_KIND_DISPLAY_ENTRUST = "嘱托";
    /** 医嘱校对列表查询失败（IF-IP-AUD-01）。 */
    String EX_IP_AUD_101 = "EX-IP-AUD-101";

    /** 所选就诊均不在护士站可见范围（IF-IP-AUD-01）。 */
    String EX_IP_AUD_102 = "EX-IP-AUD-102";

    /**
     * 校对：开嘱校对状态非 ACTIVE、停嘱校对状态/长临/痕迹不满足，或域断言失败（IF-IP-AUD-02）。
     *
     * <p>开嘱校对与停嘱校对共用本码；停嘱校对失败含非 STOPPED、非长期、已写过停嘱校对痕迹等。</p>
     */
    String EX_IP_AUD_201 = "EX-IP-AUD-201";

    /** 校对：就诊不可见（IF-IP-AUD-02）。 */
    String EX_IP_AUD_202 = "EX-IP-AUD-202";

    /** 校对：转科/出院就诊联动失败（IF-IP-AUD-02；仅开嘱校对成功后触发，停嘱校对不触发）。 */
    String EX_IP_AUD_203 = "EX-IP-AUD-203";

    /**
     * 取消停嘱校对：状态非 STOPPED、尚无停嘱校对痕迹，或域断言失败（IF-IP-AUD-03 停嘱校对撤销分支）。
     */
    String EX_IP_AUD_204 = "EX-IP-AUD-204";

    /** 退回：状态/来源/派生/门控未满足；取消停嘱校对路径不走本码（见 {@link #EX_IP_AUD_204}）（IF-IP-AUD-03 / BR-IP-AUD-013）。 */
    String EX_IP_AUD_301 = "EX-IP-AUD-301";

    /** 退回：就诊不可见（IF-IP-AUD-03）。 */
    String EX_IP_AUD_302 = "EX-IP-AUD-302";

    /** 退回写路径 Gate 用户提示（BR-IP-AUD-013；与 {@link #EX_IP_AUD_301} 成对）。 */
    String MSG_IP_AUD_REJECT_BLOCKED =
        "该医嘱仍存在执行记录、待发/待退药或不可回退费用，无法退回";

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

    /**
     * 长嘱不可拆（IF-IP-EXE-01）：为凑满包装批次，相对开嘱日最多向前探测的自然日数；
     * 超限仍凑不满则降级可拆展开。拆零下限切批（BR-IP-EXE-036）共用本前瞻上限。
     */
    int STANDING_ORDER_PACKAGE_BATCH_MAX_LOOKAHEAD_DAYS = 366;

    /**
     * 长嘱批次探测：单次向后扩展频次窗的步进自然日数（下一盒/下一拆零单位锚点）。
     */
    int STANDING_ORDER_PACKAGE_BATCH_EXPAND_STEP_DAYS = 14;

    /**
     * 长嘱批次探测循环上限（一盒一点 / 拆零下限共用），防止 NEED_MORE 死循环。
     */
    int STANDING_ORDER_BATCH_PROBE_MAX_ROUNDS = 64;

    /**
     * 自然日末时钟（23:59:59，无纳秒）。
     *
     * <p>用作频次展开/批次探测窗的<strong>日级右界</strong>（开嘱日 + 前瞻天数后的当天结束）。
     * 刻意不用 {@link LocalTime#MAX}（含纳秒），与护士站既有日末钳位同形，避免与秒级 planTime 比较漂移。</p>
     */
    LocalTime DAY_END_CLOCK = LocalTime.of(23, 59, 59);

    /**
     * 口服类用法展示名前缀（术语 {@code method_code} 展示名以本前缀开头即视为口服类，
     * 含口服、口服缓释、口服控释等；对齐 **BR-IP-EXE-035**）。
     */
    String ORAL_METHOD_DISPLAY_PREFIX = "口服";

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

    /** 护理级别医嘱禁止护士站执行/不执行（IF-IP-EXE-02/04 · BR-IP-EXE-027）。 */
    String EX_IP_EXE_306 = "EX-IP-EXE-306";

    /** 就诊待结算禁止执行/不执行（IF-IP-EXE-02/04）。 */
    String EX_IP_EXE_307 = "EX-IP-EXE-307";

    /** 护理级别医嘱仅允许校对，不允许护士站执行或不执行登记。 */
    String MSG_NURSING_LEVEL_NOT_EXECUTABLE = "护理级别医嘱仅允许校对，不允许护士站执行";

    /**
     * 批量执行库存不足提示前缀（IF-IP-EXE-02 / EX-IP-EXE-302）；后接顿号分隔的药品展示名清单。
     */
    String MSG_EXE_STOCK_INSUFFICIENT_PREFIX = "以下药品库存不足，无法执行所选计划点：";

    /**
     * 长嘱皮试药跨批软提示（IF-IP-EXE-02 · BR-IP-EXE-034）。
     * 占位：{@code {patientName}}、{@code {medicationName}}、{@code {lastSkinTestLot}}。
     */
    String MSG_EXE_LONG_TERM_SKIN_TEST_CROSS_LOT =
        "{patientName}的{medicationName}{lastSkinTestLot}批号已用完，务必请取到新批次的{medicationName}后告知医生需要重新做皮试";

    /** 就诊已出院/待结算/已结算（PENDING/COMPLETED/DISCHARGED）禁止医嘱及费用变更（非 ChargeItem 账单态）。 */
    String MSG_ENCOUNTER_PENDING_SETTLEMENT_ORDER_FORBIDDEN = "患者已办理出院或已结算，不可变更医嘱及费用";

    /**
     * 实际执行时分须与计划时点同一自然日（BR-IP-EXE-030；仅允许改 HH:mm，禁止改日期）。
     */
    String MSG_ACTUAL_TIME_DATE_MUST_MATCH_PLAN = "实际执行时分须与计划时点同一天，仅允许调整时分";

    /**
     * 实际执行时刻不得早于医嘱开立时刻（BR-IP-EXE-024；秒级；相等允许）。
     */
    String MSG_ACTUAL_TIME_BEFORE_OPENED = "执行时间不能早于开立时间";

    /**
     * 开嘱日首次执行槽默认 actualTime 相对开立时刻的延后分钟数（BR-IP-EXE-032）。
     *
     * <p>列表 {@code defaultActualTime} 与写侧空/未改时分默认同口径；跨自然日时钳到开立日末，
     * 以满足与 planTime 同日（BR-IP-EXE-030）。</p>
     */
    int FIRST_EXECUTION_DEFAULT_ACTUAL_DELAY_MINUTES = 2;

    /** 划价/取消划价/改时：就诊已出院或已结算不可写（IF-IP-PRC-02/04/06/07）。 */
    String EX_IP_PRC_406 = "EX-IP-PRC-406";

    /** 改划价展示时间域写失败整批回滚（IF-IP-PRC-07）。 */
    String EX_IP_PRC_407 = "EX-IP-PRC-407";

    /** 住院护士站医嘱执行完成结果摘要（Procedure complete 门禁占位）。 */
    String INPATIENT_NURSE_EXECUTE_PROCEDURE_RESULT_TEXT = "住院护士站医嘱执行完成";

    /** 住院护士站划价诊疗完成结果摘要（Procedure complete 占位，BR-RPT-NWL-020）。 */
    String INPATIENT_NURSE_PRICING_PROCEDURE_RESULT_TEXT = "住院护士站划价完成";

    /** 取消划价时 Procedure CANCEL 行状态原因（IF-IP-PRC-06 对称还原）。 */
    String INPATIENT_NURSE_PRICING_CANCEL_PROCEDURE_REASON = "护士划价取消";

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

    /** 自动计费：计费记录列表加载失败（IF-IP-ARG-09）。 */
    String EX_IP_ARG_901 = "EX-IP-ARG-901";

    /** 自动计费：配置未关联床位滚费医嘱（IF-IP-ARG-09）。 */
    String EX_IP_ARG_902 = "EX-IP-ARG-902";

    /** 费用明细展示模式：明细（IF-IP-CDT-02）。 */
    String COST_DETAIL_PRESENTATION_MODE_DETAIL = "DETAIL";

    /** 费用明细展示模式：汇总（IF-IP-CDT-02）。 */
    String COST_DETAIL_PRESENTATION_MODE_SUMMARY = "SUMMARY";

    /** 费用明细展示模式文案：明细（IF-IP-CDT-01 初始化下发）。 */
    String COST_DETAIL_PRESENTATION_MODE_DETAIL_DISPLAY = "明细";

    /** 费用明细展示模式文案：汇总（IF-IP-CDT-01 初始化下发）。 */
    String COST_DETAIL_PRESENTATION_MODE_SUMMARY_DISPLAY = "汇总";

    /** 费用明细状态筛选：待结算（IF-IP-CDT-02 默认）。 */
    String COST_DETAIL_STATUS_FILTER_PENDING_SETTLEMENT = "PENDING_SETTLEMENT";

    /** 费用明细状态筛选：已结算（IF-IP-CDT-02）。 */
    String COST_DETAIL_STATUS_FILTER_SETTLED = "SETTLED";

    /** 费用明细状态筛选文案：待结算（IF-IP-CDT-01 初始化下发）。 */
    String COST_DETAIL_STATUS_FILTER_PENDING_SETTLEMENT_DISPLAY = "待结算";

    /** 费用明细状态筛选文案：已结算（IF-IP-CDT-01 初始化下发）。 */
    String COST_DETAIL_STATUS_FILTER_SETTLED_DISPLAY = "已结算";

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

    /** 汇总展开明细活动类型：划价（BR-IP-CDT-018）。 */
    String COST_DETAIL_ACTIVITY_TYPE_PRICING = "PRICING";

    /** 汇总展开明细活动类型：执行（BR-IP-CDT-018）。 */
    String COST_DETAIL_ACTIVITY_TYPE_EXECUTE = "EXECUTE";

    /** 汇总展开明细活动类型：自动计费（滚费 order-execute + auto-roll-fees SR，BR-IP-CDT-018）。 */
    String COST_DETAIL_ACTIVITY_TYPE_AUTO_ROLL = "AUTO_ROLL";

    /** 汇总展开明细活动展示：划价（BR-IP-CDT-018）。 */
    String COST_DETAIL_ACTIVITY_TYPE_PRICING_DISPLAY = "划价";

    /** 汇总展开明细活动展示：执行（BR-IP-CDT-018）。 */
    String COST_DETAIL_ACTIVITY_TYPE_EXECUTE_DISPLAY = "执行";

    /** 汇总展开明细活动展示：自动计费（BR-IP-CDT-018）。 */
    String COST_DETAIL_ACTIVITY_TYPE_AUTO_ROLL_DISPLAY = "自动计费";

    /** 汇总展开明细活动展示：冲销（护士划价负向留痕，BR-IP-CDT-018）。 */
    String COST_DETAIL_ACTIVITY_TYPE_CANCEL_PRICING_DISPLAY = "冲销";

    /**
     * 费用明细「来源」列：护士划价冲销行展示文案（BR-IP-CDT-018；
     * {@code generate_source} 仍为 {@code nurse-pricing}，仅展示覆盖）。
     */
    String COST_DETAIL_GENERATE_SOURCE_NURSE_OFFSET_DISPLAY = "护士冲销";

    /** 汇总展开明细活动展示：取消执行（BR-IP-CDT-018）。 */
    String COST_DETAIL_ACTIVITY_TYPE_CANCEL_EXECUTE_DISPLAY = "取消执行";

    /** 汇总展开明细活动展示：取消自动计费（BR-IP-CDT-018；冲销/退费类留痕）。 */
    String COST_DETAIL_ACTIVITY_TYPE_CANCEL_AUTO_ROLL_DISPLAY = "取消自动计费";

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

    /**
     * 汇总领药：勾选含出院带药发放行（IF-IP-SUM-04；**BR-IP-SUM-016** /
     * **BR-IP-ORD-029⑥**）。
     */
    String EX_IP_SUM_207 = "EX-IP-SUM-207";

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

    /** 皮试保存：皮试检查定价无效（IF-IP-STK-03；v1.4 本 Tab 不再划价，保留常量避免历史引用断裂）。 */
    String EX_IP_STK_203 = "EX-IP-STK-203";

    /** 皮试保存：重复划价/并发（IF-IP-STK-03 · 原 BR-IP-STK-006；v1.4 废止本 Tab 计费，保留常量）。 */
    String EX_IP_STK_204 = "EX-IP-STK-204";

    /** 皮试保存：v1.0 互斥码（已废止，v1.1 改用 {@link #EX_IP_STK_207}）；保留常量避免历史引用断裂。 */
    String EX_IP_STK_205 = "EX-IP-STK-205";

    /** 皮试保存：写失败（IF-IP-STK-03）。 */
    String EX_IP_STK_206 = "EX-IP-STK-206";

    /** 皮试保存：医生未开立皮试检查医嘱（IF-IP-STK-03 · BR-IP-STK-009）。 */
    String EX_IP_STK_207 = "EX-IP-STK-207";

    /** 皮试保存：就诊已出院或已结算不可登记（IF-IP-STK-03）。 */
    String EX_IP_STK_208 = "EX-IP-STK-208";

    /** 皮试保存：皮试检查尚未执行（IF-IP-STK-03 · BR-IP-STK-004）。 */
    String EX_IP_STK_209 = "EX-IP-STK-209";

    /** 皮试保存：批号缺失或不在发放候选内（IF-IP-STK-03 · BR-IP-STK-013）。 */
    String EX_IP_STK_210 = "EX-IP-STK-210";

    /** 皮试删除：记录不存在（IF-IP-STK-04）。 */
    String EX_IP_STK_301 = "EX-IP-STK-301";

    /** 皮试删除：已划价不可删（IF-IP-STK-04；v1.4 废止本门禁，保留常量）。 */
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

    /** 取消执行：就诊待结算（IF-IP-CXL-01）。 */
    String EX_IP_CXL_206 = "EX-IP-CXL-206";

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

    /** 体温单删除：记录不存在（IF-IP-TMP-05）。 */
    String EX_IP_TMP_301 = "EX-IP-TMP-301";

    /** 体温单删除：写失败（IF-IP-TMP-05）。 */
    String EX_IP_TMP_302 = "EX-IP-TMP-302";

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

    /** 追溯码分配：未选患者或无权（IF-IP-TRC-01～04 · BR-IP-TRC-001）。 */
    String EX_IP_TRC_101 = "EX-IP-TRC-101";

    /** 追溯码分配：列表查询失败（IF-IP-TRC-01）。 */
    String EX_IP_TRC_102 = "EX-IP-TRC-102";

    /** 追溯码分配：码不在汇总码池（IF-IP-TRC-03/04 · BR-IP-TRC-005）。 */
    String EX_IP_TRC_201 = "EX-IP-TRC-201";

    /** 追溯码分配：追溯码格式非法（IF-IP-TRC-03/04 · BR-IP-TRC-014）。 */
    String EX_IP_TRC_202 = "EX-IP-TRC-202";

    /** 追溯码分配：批号缺失（IF-IP-TRC-04）。 */
    String EX_IP_TRC_203 = "EX-IP-TRC-203";

    /** 追溯码分配：药房未录入汇总追溯码（IF-IP-TRC-04 · BR-IP-TRC-013）。 */
    String EX_IP_TRC_204 = "EX-IP-TRC-204";

    /** 追溯码分配：部分追溯码无法匹配（IF-IP-TRC-03）。 */
    String EX_IP_TRC_205 = "EX-IP-TRC-205";

    /** 追溯码分配：跨汇总单混用码（IF-IP-TRC-03）。 */
    String EX_IP_TRC_206 = "EX-IP-TRC-206";

    /** 追溯码分配：发放行状态不允许（IF-IP-TRC-04 · BR-IP-TRC-002）。 */
    String EX_IP_TRC_207 = "EX-IP-TRC-207";

    /** 追溯码分配：追溯码数量超限（IF-IP-TRC-03/04 · BR-IP-TRC-008）。 */
    String EX_IP_TRC_208 = "EX-IP-TRC-208";

    /** 追溯码分配组状态：待分配（IF-IP-TRC-02）。 */
    String TRACE_ASSIGNMENT_STATUS_PENDING = "PENDING";

    /** 追溯码分配组状态：已分配（IF-IP-TRC-02）。 */
    String TRACE_ASSIGNMENT_STATUS_ASSIGNED = "ASSIGNED";

    /** 追溯码有效长度（与门诊前端 TRACE_CODE_LENGTH 同族 · BR-IP-TRC-014）。 */
    int TRACE_CODE_LENGTH = 20;

    /** 单次批量解析追溯码上限（AS-IP-NUR-015 §7 性能）。 */
    int TRACE_CODE_BATCH_RESOLVE_MAX = 50;

    /** 追溯码解析失败原因：格式非法。 */
    String TRACE_CODE_FAIL_REASON_INVALID = "INVALID";

    /** 追溯码解析失败原因：不在码池。 */
    String TRACE_CODE_FAIL_REASON_NOT_IN_POOL = "NOT_IN_POOL";

    /** 追溯码解析失败原因：无法匹配药批组。 */
    String TRACE_CODE_FAIL_REASON_NOT_MATCHED = "NOT_MATCHED";

    /** 追溯码解析失败原因：码数超限。 */
    String TRACE_CODE_FAIL_REASON_OVER_LIMIT = "OVER_LIMIT";

    /** 追溯码分配组状态展示：待分配（IF-IP-TRC-02）。 */
    String TRACE_ASSIGNMENT_STATUS_PENDING_DISPLAY = "待分配";

    /** 追溯码分配组状态展示：已分配（IF-IP-TRC-02）。 */
    String TRACE_ASSIGNMENT_STATUS_ASSIGNED_DISPLAY = "已分配";

    /** 码池摘要最大展示长度（IF-IP-TRC-02 脱敏截断）。 */
    int TRACE_POOL_PREVIEW_MAX_LENGTH = 120;
}
