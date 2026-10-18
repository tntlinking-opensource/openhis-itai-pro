# 住院护士站 · 护理记录 · 应用服务设计（AS）

> **文档职责**：工作台右侧「护理记录」Tab——纸质单/时间轴同源数据，列表、单条/批量保存、删除、打印。**患者上下文**在 **AS-IP-NUR-001**；与体温单共用 **DM-DIA-002**，**`type_enum=NURSING_RECORD`**。
>
> **编码**：AS-IP-NUR-014 · **版本**：v1.1.2 · **日期**：2026-06-20  
> **编号说明**：`AS-IP-NUR-006` 已用于费用明细。

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | IF-IP-NRC-01 页初始化；IF-IP-NRC-02 按就诊+日期范围列表；IF-IP-NRC-03 保存/更新单条；IF-IP-NRC-04 纸质批量保存；IF-IP-NRC-05 删除；IF-IP-NRC-06 打印 |
| **在外** | 左侧患者列表（**001**）；体温单（**007**）；Tab 内患者分页（老系统有，新 UI 无）；**同步体温单**双写（老系统 `vitalSignsSyncFlag`，ZXPlat 统一聚合**不需要**）；模板 CRUD（老系统有，新 UI 无）；`reservedCol1/2`（UI 有列无落点）；I/O 汇总报表 |
| **涉及域** | VitalSign（**DM-DIA-002**）；Encounter；Practitioner |
| **编排权威** | 以本文 **§2、§4** 为准；展示字段对齐护士站护理记录 UI，存储为主表+明细 |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **病区护士** | 选中患者 → 纸质单/时间轴切换 → 查看本周记录 → 新增/编辑/复制/删除 → 时间轴单条保存或纸质**保存全部** → 打印 |
| **系统** | 每条记录=一张主表（`recorded_at`=日期+时间）+ 明细；病情观察→`remark`；签名→`recorder_id` |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **记录时点** | `recorded_at = recordDate + recordTime` |
| **患者卡片** | 读/写 IF（02～06）请求须带 `patientId`，供 **001** 可见性 Gate（BR-IP-NRC-001） |
| **单据类型** | 固定 `NURSING_RECORD` |
| **仅 remark 可保存** | **BR-VS-008**：`NURSING_RECORD` 且 `remark` 非空即可 |
| **纸质 skeleton 行** | 预置空行不落库；仅 `isDirty` 且含业务值提交（**BR-IP-NRC-011**） |
| **复制记录** | 纯前端草稿，不调后端 |
| **字段映射（主表）** | `temperature`→主表；`conditionMeasure`→`remark`；`nurseSignature`→`recorder_id` |
| **字段映射（明细）** | `pulse`→`PULSE`；`respiratory`→`RESP`；`heartRate`→`HR`；`oxygenSaturation`→`SPO2`；`consciousness`→`CONSCIOUSNESS`；`pupilLeft/Right`→`PUPIL_L`/`PUPIL_R`；`oxygenMode/Rate/Flow`→`OXYGEN_MODE`/`OXYGEN_RATE`/`OXYGEN_FLOW`；`intakeName/Amount/Route`→`INTAKE_NAME`/值/`INTAKE_ROUTE`；`outputName/Amount/ColorCharacter`→`OUTPUT_NAME`/值/`OUTPUT_CHARACTER`；`skinStatus`→`SKIN_STATUS`；`tubeCare`→`TUBE_CARE` |
| **血压** | UI `bloodPressure`（如 `128/76`）→ 拆为 `SBP`+`DBP`；无法拆分时可写 `BP` |

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **本期** | IF-IP-NRC-01～06 |
| **非目标** | Tab 内患者分页（**001**）；同步体温单 flag；模板增删改；`reservedCol1/2` 落库；EMR 宽表 `doc_emr`；复制记录后端接口 |

### 1.5 领域清单（聚合 + 职责 + DM 路径）

| 聚合 | 职责 | DM |
| --- | --- | --- |
| **VitalSign** | 护理记录 CRUD | **DM-DIA-002** |
| **Encounter** | 可见性、纸质抬头 | **DM-ADM-002** |
| **Practitioner** | 签名展示 | **DM-ADM-005** |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-IP-NRC-001** | 读/写 `encounterId` 必填；**001** 可见 | 02～06 |
| **BR-IP-NRC-002** | 写侧 `type_enum=NURSING_RECORD` | 03、04 |
| **BR-IP-NRC-003** | 至少一项：体温、明细、或 `remark`（**BR-VS-008**） | 03、04 |
| **BR-IP-NRC-004** | 更新带 `vitalSignId`；新增由领域生成 `bus_no` | 03、04 |
| **BR-IP-NRC-005** | 明细 code 须启用且非 `TEMP` | 03、04 |
| **BR-IP-NRC-006** | 病情观察/签名不落明细字典 | 03、04 |
| **BR-IP-NRC-007** | `recorder_id` 默认当前 Practitioner | 03、04 |
| **BR-IP-NRC-008** | 删除须校验归属就诊且类型为护理记录 | 05 |
| **BR-IP-NRC-009** | 批量保存同一事务 fail-fast | 04 |
| **BR-IP-NRC-010** | 时间轴列表 `recorded_at` 降序；纸质升序 | 02 |
| **BR-IP-NRC-011** | 批量提交过滤 skeleton 行与全空行 | 04 |
| **BR-IP-NRC-012** | 纸质 extra 行可删；skeleton 行仅清空内容 | 05（删 extra 等同 05） |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义 | 读/写 | 关键规则 | 关键状态变化 | 涉及域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-IP-NRC-01 | 进入 Tab | 页初始化 | 读 | — | 无 | 术语 | CodeSystemQueryService |
| IF-IP-NRC-02 | 选患者/切周 | 列表（含明细展开） | 读 | BR-IP-NRC-001、010 | 无 | VitalSign | VitalSignQueryService |
| IF-IP-NRC-03 | 时间轴保存单条 | 保存/更新一条 | 写 | BR-IP-NRC-002～008 | upsert | VitalSign | — |
| IF-IP-NRC-04 | 纸质「保存全部」 | 批量保存 dirty 行 | 写 | BR-IP-NRC-003～011 | 多条 upsert | VitalSign | — |
| IF-IP-NRC-05 | 删除（时间轴/纸质 extra） | 删除一条 | 写 | BR-IP-NRC-001、008 | 逻辑删除 | VitalSign | — |
| IF-IP-NRC-06 | 打印 | 打印读侧 | 读 | BR-IP-NRC-001 | 无 | VitalSign | VitalSignQueryService |

---

## 3. 缺口跟踪（Gap）

| Gap ID | 缺口类型 | 文件 + 章节 | 缺什么 / 不一致点 | 关闭条件 | 影响 IF | 优先级 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| — | — | — | **无待跟踪缺口** | — | — | — | — |

---

## 4. 接口编排说明书（业务步骤）

### IF-IP-NRC-01 护理记录页初始化

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 进入 Tab |
| **主路径步骤** | 返回视图模式选项、护理记录常用体征项列表、默认周区间说明 |
| **关键分支** | — |
| **失败分支** | — |
| **查询侧说明** | 术语只读 |

---

### IF-IP-NRC-02 列表护理记录

| 维度 | 说明 |
| --- | --- |
| **触发前提** | **001** 已选患者 |
| **主路径步骤** | 校验可见 → 按就诊+类型+日期范围查主表 → 批量加载明细 → 映射为 UI 行（含 remark、签名展示名、血压合并）→ 返回；纸质抬头增量取自 Encounter |
| **关键分支** | 无数据时纸质视图仍可由前端生成 skeleton，已存行与本接口合并展示 |
| **失败分支** | 就诊不可见 → EX-IP-NRC-102 |
| **查询侧说明** | 切周/换患者重调 |

---

### IF-IP-NRC-03 保存单条护理记录

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 时间轴卡片点保存 |
| **主路径步骤** | 校验可见 → 合并日期时间 → 拆分血压 → 映射 UI 字段至主表/明细 → 校验至少一项有效 → 有 id 则更新、无 id 则新增 → 提交领域保存 |
| **关键分支** | 仅病情观察非空：仅写 remark 即可 |
| **失败分支** | 时点缺失 → EX-IP-NRC-201；无有效内容 → EX-IP-NRC-202；编码无效 → EX-IP-NRC-203；写失败 → EX-IP-NRC-204 |
| **查询侧说明** | 成功后刷新 IF-IP-NRC-02 |

---

### IF-IP-NRC-04 批量保存

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 纸质模式点「保存全部」 |
| **主路径步骤** | 过滤 skeleton 与全空行 → 逐条执行 IF-IP-NRC-03 同等校验与保存 → 任一失败整体回滚 |
| **关键分支** | 部分行仅 remark |
| **失败分支** | 第 N 行失败 → EX-IP-NRC-205（含行号） |
| **查询侧说明** | 成功后清除 dirty 标记并刷新列表 |

---

### IF-IP-NRC-05 删除

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 时间轴删除；或纸质删除 extra 行 |
| **主路径步骤** | 校验可见 → 校验记录属当前就诊且为护理记录 → 逻辑删除主表及明细 |
| **关键分支** | skeleton 行无 id，不调本接口 |
| **失败分支** | 不存在 → EX-IP-NRC-301；写失败 → EX-IP-NRC-302 |
| **查询侧说明** | 删后刷新列表 |

---

### IF-IP-NRC-06 打印

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 点打印 |
| **主路径步骤** | 同 IF-IP-NRC-02 读模型；模板 code `nursing_record` |
| **关键分支** | 不改库 |
| **失败分支** | 未选患者 → EX-IP-NRC-102 |
| **查询侧说明** | 报表侧渲染 |

---

## 5. 业务异常与用户提示

| 语义代码 | 场景 | 用户提示方向 | 可重试 |
| --- | --- | --- | --- |
| EX-IP-NRC-102 | 就诊不可见 | 请先选择患者 | 是 |
| EX-IP-NRC-201 | 记录时点缺失 | 请填写记录日期与时间 | 是 |
| EX-IP-NRC-202 | 无有效内容 | 请至少填写一项观察内容 | 是 |
| EX-IP-NRC-203 | 编码无效 | 体征项未启用 | 否 |
| EX-IP-NRC-204 | 单条保存失败 | 保存失败，请稍后重试 | 是 |
| EX-IP-NRC-205 | 批量部分失败 | 第 N 行保存失败 | 是 |
| EX-IP-NRC-301 | 删除不存在 | 记录不存在，请刷新 | 否 |
| EX-IP-NRC-302 | 删除失败 | 删除失败，请稍后重试 | 是 |

---

## 6. 事件语义与消费方

| 事件语义 | 触发相位 | 典型消费方 |
| --- | --- | --- |
| `VitalSignSavedEvent` | IF-IP-NRC-03、04 | EMR 摘要 |
| `VitalSignDeletedEvent` | IF-IP-NRC-05 | 同上 |

---

## 7. 非功能性需求

| 类别 | 要求 |
| --- | --- |
| **性能** | 列表 7 天 ≤2s |
| **事务** | 批量保存原子 |
| **一致性** | 写路径领域层事务；事件 beforeCommit |

---

## 8. 测试要点（业务视角）

| 类别 | 要点 |
| --- | --- |
| **正向** | 纸质/时间轴同源；仅病情观察可保存；体征+remark 并存 |
| **映射** | 血压拆分；SPO2；I/O 明细；remark/recorder 不落明细 |
| **批量** | 纸质 save all；skeleton 过滤；失败回滚 |
| **删除** | extra 可删；skeleton 不可删 |
| **舍弃** | 同步体温单；模板 CRUD |

---

## 9. 关联文档与变更历史

| 类型 | 路径 |
| --- | --- |
| 并列 AS | `AS-IP-NUR-007` |
| 上游 | `AS-IP-NUR-001` |
| DM | `DM-DIA-002` v2.0.4 · §9.1.3 护理记录 UI 字段映射 |
| SDD | `AS-IP-NUR-014-住院护士站护理记录-SDD.md` |
| **module-support** | `application.inpatient.nursestation.{feature}.support` · `NurseStationEncounterVisibilitysupport |

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.1.3 | 2026-06-20 | **落点对齐**：`BloodPressureParser` → `domain.share.utils`；行过滤内联 AppService |
| v1.1.2 | 2026-06-20 | **support 落点对齐**：`NurseStationEncounterVisibilitysupport → `module.querysupport.nursestation`；详见 **AS-SDD** |（已迁至 application.inpatient.nursestation.{feature}.support）（已迁至 application.shared / feature support（见 DISSOLUTION.md））
| v1.1.1 | 2026-06-19 | §1.3 补 patientId Gate 口径（对齐 SDD/成码） |
| v1.1.0 | 2026-06-19 | 对齐 UI/老系统；完整字段映射；§4 维度表；舍弃项入 §1.4 |
| v1.0.0 | 2026-06-19 | 初版 |
