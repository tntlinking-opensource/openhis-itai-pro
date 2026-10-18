# 追溯码查询报表 · 应用服务设计（AS）

> **文档职责**：说明「追溯码查询报表」场景的**接口契约（IF）**、业务语义、涉及聚合与 QueryService、规则（BR）、编排意图；作为上游契约供详细设计与实现对齐。
>
> **本文必须写**：IF 编号；用户动作与读写属性；关键校验与状态变化；Gap；异常口径；事件语义；非功能约束；业务向测试要点。
>
> **本文禁止写**：具体类名/应用方法名；Request/Response 字段明细；包路径；Mapper；伪代码；写库顺序；QueryService 字段组装规则。
>
> **页面边界**：报表菜单下**只读**追溯码查询页（**药品**；**不含耗材**）。用户可按**全院在院**或**指定库房**筛选追溯码列表，列表含**在院状态**与（选库房时）**本库状态**列；点击行打开详情 Drawer，内 **Tab「概览 | 明细」**：概览读主表在院态与剩余；明细读子表全流水（调拨/发放/退回等），发药类流水可**附带**患者信息（非主字段）。支持 **Excel 导出**（与列表同筛选、全量命中行）。**无任何写操作**。
>
> **读侧架构**：列表/概览/明细以 **`TraceNumberQueryService`**（**DM-CUS-010** §6.2）为真源；患者 enrich 经 **`MedicationDispenseQueryService`** 反查；品项/库房名经主数据 QueryService enrich。**禁止**平行读 `InventoryItem.trace_no` 逗号串作为列表真源。
>
> **跨页入口**：库存明细（**AS-INV-WHS-004 · IF-INV-DTL-13**）弹窗中追溯码可 **deep link** 至本页并预选该码打开详情 Drawer。
>
> **编码**：AS-CUS-TRC-002 · **版本**：v1.1 · **日期**：2026-07-08

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | **初始化**筛选项；追溯码**列表分页**（全院 / 指定库房）；单码 **概览**（主表）；单码 **明细**（子表时间序 + 可选患者）；**Excel 导出**（= 与列表同筛选之全量行，非当前页）；库存明细 **deep link** 打开详情 |
| **在外** | 追溯码写路径（**AS-CUS-TRC-001** 写 IF）；耗材（`item_table=device`）；国家平台 / 医保 350x；列表行内展示 `InventoryItem.trace_no` |
| **涉及域** | 读：**TraceNumber** / **TraceNumberRecord**；**MedicationDefinition**；**Location**；**MedicationDispense**（患者 enrich）；租户上下文 |
| **跨域** | 仅应用层 enrich；患者信息**非主字段** |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **库管 / 药师** | 选查询范围（全院 / 库房）→ 组合筛选 → 查询列表 → 点行看概览/明细 → 导出 |
| **审计 / 监管** | 只读查询、导出、按码追溯流水 |
| **（来自库存明细）** | 行上「追溯码」→ 弹窗仍在库码列表 → 点码跳转本页详情 |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **全院在院** | 主表 `in_hospital_status_enum = IN_HOSPITAL` 且 `remaining_split_quantity > 0`；**不看** `location_id`（**BR-TRC-006** / **BR-TNR-003**） |
| **本库在库** | 子表按 `location_id` 聚合该码**净拆零量 > 0**（含进货 IN、调拨 IN/OUT、发药 OUT 等全部流水参与轧差） |
| **本库已出** | 指定 `location_id` 下该码子表聚合**净拆零量 ≤ 0**，但码可能仍在院（如已调拨至其他库）或已离院（如已发患者） |
| **查询范围 `queryScope`** | `HOSPITAL`：不限定库房，列表展示全院在院态；`LOCATION`：须选 **`locationId`**，列表额外展示 **本库状态** / **本库净拆零量** |
| **流水时间筛选** | 筛选「在 `[flowTimeStart, flowTimeEnd]` 内**至少有一条子表流水**」的追溯码（闭区间，按流水 `created_at`） |
| **追溯码关键字** | 对 `trace_no` **LIKE** 模糊；支持精确粘贴整码 |
| **药品关键字** | 匹配 **名称 / 院内编码 / 拼音 / 五笔**（经 **MedicationDefinitionQueryService** 预解析 `item_id` 集合） |
| **批号关键字** | 对主表 `lot_number` **LIKE** 模糊 |
| **概览 Tab** | 主表：在院状态、拆零剩余、`trace_unit_code`、品项、批号 |
| **明细 Tab** | 子表时间升序：方向、流水类别、数量+单位、库位、操作类型、单据号、时间；发药/退药类可 enrich **患者姓名**（次要列） |
| **导出** | 与 IF-TRC-RPT-02 **同一套筛选**；导出**列表列**（**不含**明细 Tab 全量流水；流水导出非本期） |

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **本期** | IF-TRC-RPT-01～05；药品；deep link |
| **菜单** | Flyway **`V110__trace_number_report_menu.sql`**：权限 **`Navigation.TraceNumberReport`**；菜单 **`TraceNumberReport`**，路径 **`/report/trace-number`**，父级报表菜单 **`310400000000000122`** |
| **非目标** | 耗材；导出单码全量流水；写操作；管辖库房 Gate（与库存明细 **BR-016** 不同，本报表**不做** Gate）；旧 OpenHIS `TraceNoManage` 页迁移说明 |

### 1.5 领域清单（聚合 + 职责 + DM 路径）

| 聚合（域） | 在本场景中的职责（摘要） | DM 路径 |
| --- | --- | --- |
| **TraceNumber + TraceNumberRecord（CUS）** | 列表/概览/明细读真源 | `DM-CUS-010-TraceNumberRecord-追溯码记录.md` v2.0.5 |
| **MedicationDefinition（MED）** | 品名/规格/编码 enrich | `DM-MED-001-MedicationDefinition-药品定义.md` |
| **Location（ADM）** | 库房名 enrich | `DM-ADM-003-Location-位置.md` |
| **MedicationDispense（MED）** | 发药/退药流水患者 enrich（只读反查） | `DM-MED-002-MedicationDispense-药品发放.md` |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述（语义锚点） | 绑定 IF |
| --- | --- | --- |
| **BR-TRC-RPT-001** | 列表真源 **仅** 主表 + 子表聚合；**禁止**用 `wkf_inventory_item.trace_no` 作列表数据源 | 02～05 |
| **BR-TRC-RPT-002** | **`item_table` 固定 `medication`**（药品定义表）；不含耗材 | 02～05 |
| **BR-TRC-RPT-003** | `queryScope=LOCATION` 时 **`locationId` 必填** | 02、05 |
| **BR-TRC-RPT-004** | **`inHospitalStatusEnum` 可选**；未传则不过滤在院态 | 02、05 |
| **BR-TRC-RPT-005** | **`locationStockStatus` 仅当 `queryScope=LOCATION` 有效**：`IN_LOCATION`=本库净量>0；`OUT_OF_LOCATION`=本库净量≤0 | 02、05 |
| **BR-TRC-RPT-006** | **`traceNoKeyword` / `medicationKeyword` / `lotNumberKeyword` / 流水时间区间均可选**；空则该维不过滤 | 02、05 |
| **BR-TRC-RPT-007** | 导出须与列表 **同一筛选条件**；范围为**全量命中行**（非当前页） | 05 |
| **BR-TRC-RPT-008** | 明细 Tab 患者 enrich **仅** `operation_type` 为发药/退药扩展码且应用层能关联 `MedicationDispense` 时展示；**不得**作为主列排序键 | 04 |
| **BR-TRC-RPT-009** | deep link：URL 携带 **`traceNo`**（可选 **`openDetail=true`**）时，进入页后自动打开该码详情 Drawer | 02～04 |
| **BR-TRC-RPT-010** | 列表默认排序：**最近一条子表流水时间**降序（`null` 殿后）；同码不重复行 | 02 |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义（业务） | 读/写属性 | 关键规则（BR） | 关键状态变化（自然语言） | 涉及领域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| **IF-TRC-RPT-01** | 进入页面 / 打开筛选项 | 返回查询范围枚举、在院状态、本库状态、**管辖药库+药房+科室药房**下拉 | 读 | — | — | **Location** + 术语 | **LocationQueryService** |
| **IF-TRC-RPT-02** | 查询、翻页、刷新、deep link 预载 | 追溯码**列表分页**（组合筛选 + 全院/库房双轨列） | 读 | BR-TRC-RPT-001～006、010 | — | **TraceNumber** + **TraceNumberRecord**（聚合）+ **MedicationDefinition** | **`TraceNumberQueryService`** |
| **IF-TRC-RPT-03** | 详情 Drawer · Tab「概览」 | 按 **`traceNo`** 返回主表在院态、剩余、品项、批号（enrich 品名） | 读 | BR-TRC-RPT-002 | — | **TraceNumber** + **MedicationDefinition** | **`TraceNumberQueryService`** |
| **IF-TRC-RPT-04** | 详情 Drawer · Tab「明细」 | 按 **`traceNo`** 返回子表**时间序**全记录 + 库位/操作类型 enrich；发药类附带患者 | 读 | BR-TRC-RPT-008 | — | **TraceNumberRecord** + **Location** + **MedicationDispense** | **`TraceNumberQueryService#listRecordsByTraceNo`** + **MedicationDispenseQueryService** |
| **IF-TRC-RPT-05** | 导出 Excel | 同 IF-TRC-RPT-02 筛选全量导出列表列 | 读 | BR-TRC-RPT-007 | — | 同 IF-TRC-RPT-02 | 同 IF-TRC-RPT-02 |

> **领域读 IF 映射**（**AS-CUS-TRC-001**）：IF-TRC-RPT-02 → **IF-TRC-23**；IF-TRC-RPT-03 → **IF-TRC-01**；IF-TRC-RPT-04 → **IF-TRC-02**（+ 应用 enrich）。

---

## 3. 缺口跟踪（Gap）

| Gap ID | 缺口类型 | 文件 + 章节 | 缺什么 / 不一致点 | 关闭条件 | 影响 IF | 优先级 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| **GAP-TRC-RPT-001** | **QueryService 能力** | **DM-CUS-010** · **§6.2** | 缺 **`pageTraceNumberReport**（报表组合筛选分页 + 本库状态聚合） | DM §6.2 + SDD §5.2 补齐；领域代码实现 | IF-TRC-RPT-02、05 | 高 | 待实现 |
| **GAP-TRC-RPT-002** | **QueryService 能力** | **DM-CUS-010** · **§6.2** | 缺 **`listInStockTraceNumbersByInventoryItemKey`**（库存行四元组查本库仍在库码） | 同上 | IF-INV-DTL-13（经 **IF-TRC-22**） | 高 | 待实现 |

**无其他待跟踪缺口**。

---

## 4. 接口编排说明书（业务步骤）

### IF-TRC-RPT-01 初始化

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户进入追溯码查询报表 |
| **主路径步骤** | 返回 **`queryScope`** 选项（全院/指定库房）；**在院状态**（`IN_HOSPITAL` / `NOT_IN_HOSPITAL`）；**本库状态**（在库/已出本库）；**管辖药库+药房+科室药房** Location 下拉（与库存明细 IF-01 同源，**不做** Gate 收窄请求） |
| **关键分支** | 无 |
| **失败分支** | EX-TRC-RPT-201 |
| **查询侧说明** | 无 |

### IF-TRC-RPT-02 追溯码列表分页

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 读权限；筛选已确定 |
| **主路径步骤** | 校验 **BR-TRC-RPT-003** → 调用 **`pageTraceNumberReport**（**IF-TRC-23**）：`item_table=medication` + 可选追溯码/药品/批号/在院态/流水时间/库房与本库状态 → 分页 → enrich 药品名、编码、库房名 → **BR-TRC-RPT-010** 排序 → 返回 |
| **关键分支** | `queryScope=HOSPITAL`：不返回本库列或本库列置空；`queryScope=LOCATION`：返回 **本库状态** + **本库净拆零量** |
| **关键分支** | URL **`traceNo` + openDetail**：列表加载后自动选中并触发 IF-TRC-RPT-03/04（**BR-TRC-RPT-009**） |
| **失败分支** | 缺 `locationId`（LOCATION 范围）→ **EX-TRC-RPT-202**；查询失败 → **EX-TRC-RPT-203** |
| **查询侧说明** | 流水时间筛选用子表 EXISTS；本库状态由子表 `location_id` 聚合 |

### IF-TRC-RPT-03 单码概览

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户打开详情 Drawer · 概览 Tab；或 deep link |
| **主路径步骤** | 按 **`traceNo`** 读主表（**IF-TRC-01**）→ enrich 药品定义展示字段 → 返回在院态、拆零剩余、`trace_unit_code`、批号 |
| **关键分支** | 码不存在 → **EX-TRC-RPT-204** |
| **失败分支** | 同上 |
| **查询侧说明** | 全院语义，不看库位 |

### IF-TRC-RPT-04 单码流转明细

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户切换详情 Drawer · 明细 Tab |
| **主路径步骤** | 按 **`traceNo`** 读子表时间序（**IF-TRC-02**）→ enrich 库位名、操作类型中文、方向/类别 Label → 对发药/退药类流水按 `bus_no` 反查 **MedicationDispense** 取患者姓名（**BR-TRC-RPT-008**，次要列）→ 返回 |
| **关键分支** | 无发药关联时患者列空 |
| **失败分支** | 码不存在 → **EX-TRC-RPT-204** |
| **查询侧说明** | 含 `INTERNAL_TRANSFER` 调拨成对与 `HOSPITAL_IO` 进出院流水 |

### IF-TRC-RPT-05 导出 Excel

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户导出；筛选与 IF-TRC-RPT-02 一致 |
| **主路径步骤** | 复用 IF-TRC-RPT-02 条件分页循环拉取全量（上限 **SDD** 约定，建议 ≤ **3 万行**）→ 格式化为列表列 → 写 Excel（工作表名 **「追溯码查询」**） |
| **关键分支** | 达上限 **`truncated=true`** 并提示 |
| **失败分支** | EX-TRC-RPT-205 |
| **查询侧说明** | **不含**明细 Tab 流水行 |

---

## 5. 业务异常与用户提示

| 异常 ID | 场景 | 用户提示语义 | 可重试 |
| --- | --- | --- | --- |
| **EX-TRC-RPT-201** | 初始化失败 | 筛选项加载失败 | 是 |
| **EX-TRC-RPT-202** | 指定库房范围未选库房 | 请选择库房 | 是 |
| **EX-TRC-RPT-203** | 列表查询失败 | 列表加载失败 | 是 |
| **EX-TRC-RPT-204** | 追溯码不存在 | 未找到该追溯码 | 否 |
| **EX-TRC-RPT-205** | 导出失败 | 导出失败，请稍后重试 | 视原因 |
| **EX-TRC-RPT-206** | 导出截断 | 导出数据已触达行数上限，部分记录未包含 | — |

---

## 6. 事件语义与消费方

| 事件（业务名） | 触发点（IF） | 消费方 / 意图 | 幂等语义 |
| --- | --- | --- | --- |
| **（只读无落库事件）** | IF-TRC-RPT-01～05 | — | 查询幂等 |

---

## 7. 非功能性需求

| 维度 | 要求 |
| --- | --- |
| **性能** | 列表分页；导出分页循环；子表聚合走 `(location_id, trace_no)` 索引 |
| **一致性** | 只读；列表本库状态与子表聚合一致 |
| **安全** | 租户隔离；与库存读权限同级（库管/药师） |
| **导出上限** | 建议 **3 万行**；超限 **`truncated`** |

---

## 8. 测试要点（业务视角）

| 类别 | 要点 |
| --- | --- |
| **全院列表** | 在院/不在院筛选；追溯码/药品/批号/流水时间组合筛选 |
| **库房列表** | 选库房 → 本库在库 vs 已出本库；调拨 OUT 后本库已出但全院仍在院 |
| **概览** | 主表剩余与在院态；耗尽码 `NOT_IN_HOSPITAL` |
| **明细** | 调拨成对 IN/OUT；发药 OUT + 退药 IN；患者列为次要 |
| **导出** | 与列表同筛选全量；截断提示 |
| **deep link** | 库存明细弹窗点码 → 本页打开详情 |
| **边界** | 仅药品；无耗材行 |

---

## 9. 关联文档与变更历史

### 9.1 关联文档

| 类型 | 路径 |
| --- | --- |
| **追溯编排 AS** | `AS-CUS-TRC-001-追溯码流转.md`（IF-TRC-01/02/22/23） |
| **库存明细 AS** | `AS-INV-WHS-004-库存明细.md`（IF-INV-DTL-13） |
| **DM** | `DM-CUS-010-TraceNumberRecord-追溯码记录.md` |

### 9.2 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| **v1.1** | **2026-07-08** | 菜单 Flyway **V110**（**`Navigation.TraceNumberReport`** / **`/report/trace-number`**） |
| **v1.0** | **2026-07-06** | 初稿：报表列表（全院/库房 + 本库状态列）；详情概览/明细 Tab；导出；库存明细 deep link；药品 only |
