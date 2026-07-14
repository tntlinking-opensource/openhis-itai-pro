/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * 门诊护士站应用层展示与占位文案常量（避免编排类内散落业务字面量，对齐《代码生成通用规范》B.10.3）。
 */
public interface OutpatientNurseStationConstants {

    /**
     * 处置执行完成占位摘要：满足 Procedure 聚合「完成前须具备结果语义」门禁，占位含义为护士站一次执行已落账。
     */
    String OUTPATIENT_NURSE_STATION_PROCEDURE_RESULT_TEXT = "门诊护士站处置执行完成";

    /**
     * 耗材拣配追溯码占位：在真实 FIFO 批号链路接入前，仅占位满足非空约束，避免拣配命令缺字段。
     */
    String OUTPATIENT_NURSE_STATION_DEVICE_TRACE_PLACEHOLDER = "NURSE_STATION_TRACE_PENDING";

    /**
     * 耗材拣配批号占位：与追溯码占位配套，后续应由库存 FIFO 分配替换。
     */
    String OUTPATIENT_NURSE_STATION_DEVICE_LOT_PLACEHOLDER = "NURSE_STATION_LOT_PENDING";

    /**
     * 诊疗处置行在缺少目录侧名称回填时的展示占位（列表仍返回锚点与计费聚合，占位仅影响名称列）。
     */
    String OUTPATIENT_NURSE_STATION_SERVICE_LINE_NAME_FALLBACK = "（诊疗项目）";

    /**
     * 药品处置行在缺少目录侧名称回填时的展示占位。
     */
    String OUTPATIENT_NURSE_STATION_MEDICATION_LINE_NAME_FALLBACK = "（药品医嘱）";

    /**
     * 耗材处置行在缺少目录侧名称回填时的展示占位。
     */
    String OUTPATIENT_NURSE_STATION_DEVICE_LINE_NAME_FALLBACK = "（耗材医嘱）";

    /**
     * 三列表对外 requestTable 语义短码：诊疗服务申请。
     */
    String REQUEST_TABLE_VIEW_SERVICE = "SERVICE";

    /**
     * 三列表对外 requestTable 语义短码：药品申请。
     */
    String REQUEST_TABLE_VIEW_MEDICATION = "MEDICATION";

    /**
     * 三列表对外 requestTable 语义短码：耗材请求。
     */
    String REQUEST_TABLE_VIEW_DEVICE = "DEVICE";

    /**
     * 药品执行记录在缺少 therapy 类目回落时的执行分类占位（避免误用请求状态码充当类目）。
     */
    String MEDICATION_PROCEDURE_CATEGORY_FALLBACK = "MEDICATION_ORDER";

    /** IF-NUR-03 执行记录抽屉：已完成操作的展示文案。 */
    String EXECUTION_RECORD_ACTION_COMPLETED = "执行完成";

    /** IF-NUR-03 执行记录抽屉：取消执行操作的展示文案。 */
    String EXECUTION_RECORD_ACTION_CANCEL = "取消执行";

    /** IF-NUR-03 执行记录抽屉：中止执行操作的展示文案。 */
    String EXECUTION_RECORD_ACTION_STOPPED = "不执行";

    /** IF-NUR-03 执行记录抽屉：进行中状态的展示文案。 */
    String EXECUTION_RECORD_ACTION_IN_PROGRESS = "执行中";

    /** IF-NUR-03 执行记录抽屉：待执行状态的展示文案。 */
    String EXECUTION_RECORD_ACTION_PREPARATION = "待执行";

    /** IF-NUR-03 执行记录抽屉：无法识别状态时的展示文案。 */
    String EXECUTION_RECORD_ACTION_DEFAULT = "执行登记";

    /** IF-NUR-03 执行记录抽屉：已完成/进行中记录的操作人列标题语义。 */
    String EXECUTION_RECORD_OPERATOR_ROLE_EXECUTE = "执行人";

    /** IF-NUR-03 执行记录抽屉：取消记录的操作人列标题语义。 */
    String EXECUTION_RECORD_OPERATOR_ROLE_CANCEL = "取消执行人";

    /** 皮试枚举初始化失败（IF-OP-STK-01）。 */
    String EX_OP_STK_101 = "EX-OP-STK-101";

    /** 皮试就诊上下文无效（IF-OP-STK-02/03/04）。 */
    String EX_OP_STK_102 = "EX-OP-STK-102";

    /** 皮试列表查询失败（IF-OP-STK-02）。 */
    String EX_OP_STK_103 = "EX-OP-STK-103";

    /** 皮试双签或必填状态缺失（IF-OP-STK-03）。 */
    String EX_OP_STK_201 = "EX-OP-STK-201";

    /** 皮试检查不可登记（IF-OP-STK-03）。 */
    String EX_OP_STK_202 = "EX-OP-STK-202";

    /** 皮试保存落库失败（IF-OP-STK-03）。 */
    String EX_OP_STK_203 = "EX-OP-STK-203";

    /** 皮试删除目标不存在（IF-OP-STK-04）。 */
    String EX_OP_STK_301 = "EX-OP-STK-301";

    /** 皮试删除失败（IF-OP-STK-04）。 */
    String EX_OP_STK_303 = "EX-OP-STK-303";
}
