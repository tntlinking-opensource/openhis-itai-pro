# 门诊处方抽查 · 应用服务设计（AS）

> **文档职责**：报表中心「处方抽查」只读契约。按**开方时间**导出已发药门诊正方药品行；类型必选「西药及中成药 / 中药饮片」（默认西药及中成药），一页两套列（对齐监管 Excel 表头）；**不含退方**、**不要校验码**。
>
> **页面边界**：类型必选 → 开方时间窗 → 查询明细分页 → 导出（列随类型切换）。**纯读**。
>
> **编码**：AS-RPT-014 · **版本**：v1.1 · **日期**：2026-09-09  
> **产品裁决（锁定）**：医师代码=`practitioner.yb_no`；医疗机构执业许可证登记号=`医院 Organization.yb_no`；患者唯一标识=`patient.busNo`；药品医保信息业务编码=`MedicationDefinition.ybNo`；中药制备方法与给药途径均取方头用法；脚注=特殊煎法；药品顺序=开药 `sortNumber`；时间轴=开方时间；仅已发（发放 COMPLETED）；不含退方。

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | IF-RPT-PSC-01～03：初始化、明细分页、导出；类型二选一强制；两套列 |
| **在外** | 处方点评（**AS-OP-PHA-007**）；门诊处方查询主读（**AS-OP-PHA-005**，本报表可复用其已签发/发药预解析能力但不共享页面）；住院处方；写路径 |
| **涉及域** | **MedicationRequest** / **MedicationDispense** / **MedicationDefinition**；**Encounter** / **EncounterDiagnosis** / **Patient**；**Practitioner** / **Organization**；**ConditionDefinition**；**ChargeItem**（方金额） |
| **编排权威** | 本文 §2、§4 |
| **数据锚点** | 门诊 AMB 已签发药品行；发放代表态 COMPLETED；`prescription_no` 不以 `T` 开头（排除退方） |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **药房 / 医务 / 质控** | 选类型 → 设开方时间 → 查询/翻页 → 导出 Excel |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **报表类型** | `WESTERN_PATENT`=西药及中成药；`HERBAL`=中药饮片；**必选**，默认西药及中成药；**禁止**「全部」 |
| **西药及中成药 rx 集合** | 术语 355 中非饮片轨：门诊/急诊/儿科西药与中成药、麻精一、精二等（不含饮片码 2/4/6/9） |
| **中药饮片 rx 集合** | 门诊/急诊/儿科饮片码 `2`/`4`/`6`（历史 `9` 归一为 `2`） |
| **已发** | 关联发放存在 `DispenseStatus=COMPLETED` 的药品申请行（窗口内预解析 medReqId IN） |
| **不含退** | `prescription_no` 不以 `T` 开头（退方号 `TCP…`） |
| **开方时间** | `COALESCE(req_opened_time, req_authored_time)` 闭区间；默认当月 |
| **机构** | 当前租户启用医院：名称=`Organization.name`；执业许可证登记号=`yb_no` |
| **医师代码** | 开方人 `Practitioner.yb_no` |
| **科室代码** | 开单科室 `Organization.yb_no` |
| **患者唯一标识** | `Patient.busNo` |
| **药品医保信息业务编码** | 目录 `MedicationDefinition.ybNo` |
| **西药列** | 对齐《西药及中成药处方》表头（无校验码） |
| **饮片列** | 对齐《中药饮片处方》表头；病/证分列；制备方法与给药途径均填方头用法文案（术语 **`tcm_method_code`**，未命中回落 `method_code`）；脚注=特殊煎法；**无校验码列** |
| **药品顺序** | 行 `sortNumber`（空则按页内同方出现序） |
| **处方金额** | 同方**全部**药品申请关联费用 `totalPrice` 合计（结算刻度）；按页内方号回查整方再汇总，禁止只加当前页行 |
| **导出** | 同筛选；`exportColumns` 裁剪；行上限；**AMB/COMPLETED 预解析仅一次**后翻页收集 |
| **AMB 收窄** | 与 AS-OP-PHA-005 同：按就诊 `start_time` 对齐开方日窗拉 AMB id；跨日挂号但开方落窗内的就诊可能漏收 |

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **本期** | IF-RPT-PSC-01～03；仅门诊 AMB |
| **非目标** | 住院；随机抽比例算法；校验码；点评写；未发药方；退方 |

### 1.5 领域清单（聚合 + 职责 + DM 路径）

| 聚合 | 职责 | DM 路径 |
| --- | --- | --- |
| **MedicationRequest** | 已签发药品行、开方时间、rx 类型、用法/剂量/付数/特殊煎法/顺序 | `DM-MED-003` |
| **MedicationDispense** | 已发 COMPLETED 预解析 | `DM-MED-002` |
| **MedicationDefinition** | 药品编码/医保码/名称/剂型/规格/包装 | `DM-MED-001` |
| **Encounter / Patient** | AMB 收窄、患者 busNo/性别/出生日期 | `DM-ADM-002` / `DM-ADM-001` |
| **EncounterDiagnosis / ConditionDefinition** | 西医诊断或中医病/证 | `DM-ADM-002` / `DM-CLN-002` |
| **Practitioner / Organization** | 医师 yb_no、科室名/yb_no、医院名/yb_no | `DM-ADM-005` / `DM-ADM-004` |
| **ChargeItem** | 方金额合计 | `DM-ADM-011` |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-RPT-PSC-001** | 报表类型必选且仅允许 `WESTERN_PATENT`/`HERBAL`；默认西药及中成药 | 01～03 |
| **BR-RPT-PSC-002** | 开方时间窗必填且起≤止；默认当月 | 01～03 |
| **BR-RPT-PSC-003** | 仅门诊 AMB 已签发药品行 | 02、03 |
| **BR-RPT-PSC-004** | 须已发（发放 COMPLETED）；预解析 medReqId | 02、03 |
| **BR-RPT-PSC-005** | 排除退方（处方号不以 `T` 开头） | 02、03 |
| **BR-RPT-PSC-006** | 按类型收窄 `rx_type_code IN` 对应集合 | 02、03 |
| **BR-RPT-PSC-007** | 列表/导出列随类型切换；禁止混列「全部」 | 02、03 |
| **BR-RPT-PSC-008** | 编码口径：机构/科室/医师 `yb_no`；患者 `busNo`；药品医保 `ybNo` | 02、03 |
| **BR-RPT-PSC-009** | 饮片：制备方法=给药途径=方头用法；脚注=特殊煎法；无校验码 | 02、03 |
| **BR-RPT-PSC-010** | 导出受行上限约束；须传 `exportColumns` | 03 |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义 | 读/写 | 关键规则 | 关键状态变化 | 涉及领域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-RPT-PSC-01 | 进入页 | 默认类型、默认开方月窗、类型下拉 | 读 | BR-RPT-PSC-001、002 | 无 | — | — |
| IF-RPT-PSC-02 | 查询 | 按类型明细分页 | 读 | BR-RPT-PSC-001～009 | 无 | MR/MD/… | `MedicationRequestQueryService#pageOutpatientSignedPrescriptionDrugLines` 等 |
| IF-RPT-PSC-03 | 导出 | 同筛选 Excel | 读 | BR-RPT-PSC-010 | 无 | 同上 | 同分页筛选 |

---

## 3. 缺口跟踪（Gap）

| Gap ID | 缺口类型 | 文件 + 章节 | 缺什么 / 不一致点 | 关闭条件 | 影响 IF | 优先级 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GAP-RPT-PSC-001 | QueryService 能力 | `DM-MED-003` §6.2.21 | 缺：`rxTypeCodes` 多码 IN、排除退方（`prescription_no` 不以 T 开头）筛选能力 | QS PageQuery + Spec 补齐 | 02～03 | P0 | **已关闭** |

### 3.1 落点（GAP-RPT-PSC-001）

| 层 | 路径 |
| --- | --- |
| QS | `OutpatientSignedPrescriptionDrugLinePageQuery.rxTypeCodes` / `excludeRefundPrescription`；`MedicationRequestQueryService#buildOutpatientSignedPrescriptionDrugLineSpecification` |
| App | `…/report/prescriptionspotcheck/…PrescriptionSpotCheckReportAppService` |
| 常量 | `PrescriptionSpotCheckReportConstants`（zxplat-domain） |
| 菜单 | Flyway `V29__menu_prescription_spot_check_report.sql` |

---

## 4. 接口编排说明书（业务步骤）

### IF-RPT-PSC-01 页面初始化

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 进入页 |
| **主路径步骤** | 登录 Gate → 默认类型西药及中成药 → 默认本月开方起止日 → 类型选项 |
| **失败分支** | EX-RPT-PSC-101 |

### IF-RPT-PSC-02 明细分页

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 查询 |
| **主路径步骤** | 校验类型与时间窗 → AMB 就诊预解析 → 开方窗 medReq 预解析 → COMPLETED 发放收窄 → 组 PageQuery（rx 集合 + 排除退方）→ 分页药品行 → 批量装医院/患者/医师/科室/诊断/目录/费用/用法文案 → 按类型映射行 DTO |
| **失败分支** | EX-RPT-PSC-102/103 |

### IF-RPT-PSC-03 导出

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 导出 |
| **主路径步骤** | 同筛选循环至上限 → 按类型选导出行 DTO + `exportColumns` 写 Excel |
| **失败分支** | EX-RPT-PSC-102/104 |

---

## 5. 业务异常与用户提示

| 语义代码 | 场景 | 用户提示方向 | 可重试 |
| --- | --- | --- | --- |
| EX-RPT-PSC-101 | 初始化失败 | 页面初始化失败 | 是 |
| EX-RPT-PSC-102 | 类型/时间窗非法 | 请选择报表类型与有效开方时间 | 否 |
| EX-RPT-PSC-103 | 查询失败 | 查询失败 | 是 |
| EX-RPT-PSC-104 | 导出失败 | 导出失败 | 是 |

---

## 6. 事件语义与消费方

**本期无领域写操作。**

---

## 7. 非功能性需求

| 类别 | 要求 |
| --- | --- |
| **性能** | 常规月窗分页 ≤ 2s；导出行上限；批量 I/O，禁止页内 N+1；**导出禁止每页重跑 AMB/窗内 medReq/COMPLETED 预解析**；列表 `MAX_PAGE_SIZE=500` |
| **租户** | 当前租户 |
| **金额** | 方金额结算 2 位 |

---

## 8. 测试要点（业务视角）

| 类别 | 要点 |
| --- | --- |
| **类型** | 默认西药；切换饮片列变化；不可空类型 |
| **已发/退** | 未发不入；TCP 退方不入 |
| **编码** | 医师/科室/机构 yb_no；患者 busNo；药品 ybNo |
| **饮片** | 用法写入制备方法与给药途径；脚注=特殊煎法；无校验码列 |

---

## 9. 关联文档与变更历史

| 类型 | 路径 |
| --- | --- |
| SDD | `AppService-SDD/AS-RPT-014-门诊处方抽查-SDD.md` |
| Excel 样例 | `docs/西药及中成药处方（最终版本）.xlsx`、`中药饮片处方（最终版本）.xlsx` |
| 协作 | `AS-OP-PHA-005`（已签发读能力复用） |

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.0 | 2026-09-09 | 首版：类型二选一、已发正方、开方时间、编码口径锁定 |
| v1.1 | 2026-09-09 | 校验修订：导出预解析一次复用；方金额整方汇总；AMB 口径与性能说明；MAX_PAGE_SIZE=500 |
