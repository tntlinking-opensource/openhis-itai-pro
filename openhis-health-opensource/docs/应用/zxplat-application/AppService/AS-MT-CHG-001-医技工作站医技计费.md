# 医技工作站 · 医技计费 · 应用服务设计（AS）

> **文档职责**：**仅住院**医技工作站「医技计费」——划**诊疗项目**（执行科室=登录 TECH）与**耗材**（登录医技科对应耗材房），挂待结算费用；来源码均为 **`medical-tech-pricing`（医技划价）**。  
> **参照**：住院护士划价（`AS-IP-NUR-005`）；护士对 TECH 执行科诊疗拒绝划价（**BR-IP-PRC-034**），由本模块承接。  
> **协同**：`DD-医技工作站-执行计费与护士协同-2026-09.md` · 索引 `DD-医技工作站-模块索引-2026-09.md`  
> **SDD**：`health/AppService-SDD/AS-MT-CHG-001-医技工作站医技计费-SDD.md`  
> **编码**：AS-MT-CHG-001 · **版本**：v1.3.0 · **日期**：2026-10-11

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | 住院就诊上下文下载入医技计费页；**诊疗选品**（执行科室=登录 TECH）与**耗材选品**（本科耗材房库存）；确认划价（分轨多行）；已划价列表（含 orderType）；取消划价（冲销，待结算可冲） |
| **在外** | 医技执行 / 费用清单；门诊（本模块无入口）；药品划价；护士站划价；库存扣减本体（耗材发放路径） |
| **涉及域** | ServiceRequest；Procedure；DeviceRequest；DeviceDispense；ChargeItem；ActivityDefinition；DeviceDefinition；Location；Encounter；Organization |
| **编排权威** | 本文 §2、§4 |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **医技人员** | 登录 TECH 科 → 医技站切住院 → 选患者 → 医技计费 → 切「诊疗项目/耗材」→ 选品确认划价 → 必要时冲销 |
| **系统** | 诊疗活动 `org_id`=登录 TECH；耗材库房∈登录科下属耗材房；写 **`medical-tech-pricing`**（费用明细来源列「医技划价」/「医技冲销」）；费用 **PENDING_SETTLEMENT** |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **医技计费** | 住院；诊疗（本科 TECH 执行科）+ 耗材（本科耗材房）；来源一律医技划价 |
| **与护士划价差异** | 护士可选非 TECH 诊疗+耗材（取药模板房）；医技仅本科 TECH 诊疗 + 本科耗材房 |
| **诊疗划价** | ServiceRequest VERIFIED + Procedure COMPLETED（org_id=登录 TECH）+ Charge；可带子项账单 |
| **耗材划价** | DeviceRequest + Dispense finalize（同护士开关扣库）+ Charge；**不**写 Procedure |
| **门诊** | 无本模块 |

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **本期** | IF-MT-CHG-01～07 |
| **非目标** | 药品、部分数量冲销、门诊计费、医技工作量统计本体 |

### 1.5 领域清单

| 聚合 | 职责 | DM |
| --- | --- | --- |
| ServiceRequest / Procedure | 诊疗划价请求与执行记录 | DM-WKF-004 / DM-CLN-004 |
| DeviceRequest / DeviceDispense | 耗材划价请求与发放 | DM-WKF-003 / 007 |
| ChargeItem | 待结算费用 | DM-ADM-011 |
| ActivityDefinition / Location / Organization | 本科诊疗 / 耗材房 | DM-WKF-001 / ADM-003 / 004 |
| Encounter | 住院就诊门闸 | DM-ADM-002 |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-MT-CHG-001** | 须登录 TECH；入参须为住院就诊 | 全部 |
| **BR-MT-CHG-002** | 耗材选品仅耗材；诊疗选品仅活动定义；禁止药品 | 01、02、06、07 |
| **BR-MT-CHG-003** | 耗材每行库房必填且须为登录科下属耗材房 | 02 |
| **BR-MT-CHG-004** | 耗材确认划价：DeviceRequest（落目录选中 `lotNumber`）+ Dispense finalize（有批号**锁批定批**，空则 FIFO；同事务待扣预减，同 **AS-IP-NUR-005 BR-IP-PRC-008/019**）+ Charge **PENDING_SETTLEMENT**；**不**写 Procedure | 02 |
| **BR-MT-CHG-005** | 已划价列表：耗材=`medical-tech-pricing`+本科房（过渡读历史误标 nurse-pricing）；诊疗=`medical-tech-pricing` ServiceRequest | 03、04 |
| **BR-MT-CHG-006** | 取消划价：仅待结算未冲销可冲；耗材回退发放；诊疗 CANCEL Procedure；Request 保持完成态 | 04 |
| **BR-MT-CHG-007** | 耗材划价不强制绑定医技执行 Procedure | 02 |
| **BR-MT-CHG-008** | 诊疗选品/确认：活动定义 `org_id` 须等于登录 TECH；生成来源固定 `medical-tech-pricing` | 06、07 |
| **BR-MT-CHG-009** | 诊疗确认划价：ServiceRequest VERIFIED → Procedure COMPLETED（org_id=登录 TECH）→ Charge；有套餐子表则冻价写子账单 | 07 |

---

## 2. 页面与接口总览

| IF | 用户动作 | 语义 | 读写 | BR | 状态变化 | 域 | 查询 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-MT-CHG-01 | 检索耗材 | 本科耗材房选品目录 | 读 | 001～002 | 无 | DeviceDefinition | 目录/库存读 |
| IF-MT-CHG-02 | 确认耗材划价 | 批量写耗材请求+费用+发放 | 写 | 001～004、007 | 新建请求/费用/发放 | DR/DD/Charge | — |
| IF-MT-CHG-03 | 刷新已划价 | 耗材+诊疗列表（orderType） | 读 | 005 | 无 | SR/DR/Charge | — |
| IF-MT-CHG-04 | 冲销 | 取消划价（分轨） | 写 | 006 | 冲销留痕 | Charge/Procedure/DD | — |
| IF-MT-CHG-05 | 进页初始化 | 默认耗材房等 | 读 | 001、003 | 无 | Location | — |
| IF-MT-CHG-06 | 检索诊疗 | 登录 TECH 执行科诊疗选品 | 读 | 001、002、008 | 无 | ActivityDefinition | 目录读 |
| IF-MT-CHG-07 | 确认诊疗划价 | 批量写诊疗请求+Procedure+费用 | 写 | 001、002、008、009 | 新建 SR/Procedure/Charge | SR/Proc/Charge | — |

---

## 3. 缺口跟踪（Gap）

**无待跟踪缺口**（复用护士划价既有领域写能力；应用层未实现非 Gap）。

---

## 4. 接口编排说明书

### IF-MT-CHG-01 耗材选品

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已选住院就诊；登录 TECH |
| **主路径步骤** | 解析登录科下属耗材房 → 检索耗材目录与可选库存展示 → 返回 |
| **失败分支** | EX-MT-CHG-101 |

### IF-MT-CHG-02 确认耗材划价

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 勾选/录入至少一行耗材 |
| **主路径步骤** | Gate TECH+住院 → 校验每行库房属本科 → 写 DeviceRequest（落 `lotNumber`）+ Dispense finalize（锁批/FIFO）+ Charge → 条件合并扣库 |
| **失败分支** | 库房非法 EX-MT-CHG-201；写失败 EX-MT-CHG-202 |

### IF-MT-CHG-03 已划价列表

| 维度 | 说明 |
| --- | --- |
| **主路径步骤** | 耗材：就诊+本科来源；诊疗：就诊+`medical-tech-pricing`；enrich 确费人/计费人；行带 `orderType` |

### IF-MT-CHG-04 取消划价

| 维度 | 说明 |
| --- | --- |
| **主路径步骤** | 按 orderType 分轨：耗材回退发放+冲销；诊疗 CANCEL Procedure+冲销；未扣库不回补 |
| **失败分支** | 已结算不可冲 EX-MT-CHG-203 |

### IF-MT-CHG-05 初始化

| 维度 | 说明 |
| --- | --- |
| **主路径步骤** | 返回默认耗材房选项（登录科下属） |

### IF-MT-CHG-06 诊疗选品

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已选住院就诊；登录 TECH |
| **主路径步骤** | 活动定义分页，`org_id`=登录 TECH → 装配价目/单位槽 → 返回 |
| **失败分支** | EX-MT-CHG-101 |

### IF-MT-CHG-07 确认诊疗划价

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 勾选/录入至少一行诊疗 |
| **主路径步骤** | Gate TECH+住院 → 断言活动 org=登录 TECH → 权威定价 → createFromMedicalTechPricing → Procedure COMPLETED → Charge → 套餐子账单 |
| **失败分支** | 活动非本科 / 写失败 EX-MT-CHG-202 |

---

## 5. 业务异常与用户提示

| 码 | 场景 | 提示 | 可重试 |
| --- | --- | --- | --- |
| EX-MT-CHG-101 | 选品失败 | 无法加载目录 | 是 |
| EX-MT-CHG-201 | 库房非本科 | 只能从本科耗材房划价 | 否 |
| EX-MT-CHG-202 | 写失败 / 活动非本科 | 划价失败请重试 / 只能划价本科执行科室的诊疗项目 | 是 |
| EX-MT-CHG-203 | 已结算 | 已结算不可冲销 | 否 |

---

## 6. 事件语义与消费方

| 事件 | 触发 | 消费方 |
| --- | --- | --- |
| 耗材划价完成 | IF-02 | 医技执行右侧待发、费用清单 |
| 诊疗划价完成 | IF-07（`MedicalTechPricingServiceRequestCreatedEvent`） | 已划价列表、费用清单 |
| 冲销完成 | IF-04 | 同上刷新 |

---

## 7. 非功能性需求

同事务整批划价；失败整批回滚；须 TECH 登录。诊疗与耗材确认分轨提交（前端不可混批）。

---

## 8. 测试要点

正向：本科房耗材划价；本科 TECH 诊疗划价出待结算；冲销分轨。异常：他科房拒绝；他科执行科诊疗拒绝。规则：门诊无入口；费用来源列均为「医技划价」。

---

## 9. 关联文档与变更历史

| 文档 | 路径 |
| --- | --- |
| 协同 / 索引 DD | `health/设计决策/DD-医技工作站-*.md` |
| 护士划价参照 | `AS-IP-NUR-005`（**BR-IP-PRC-034** TECH 诊疗禁护士划） |
| 医技执行 | `AS-MT-EXE-001`（执行侧住院诊疗仍不新建 Charge；费用认划价/护士执行轨） |
| 本模块 SDD | `health/AppService-SDD/AS-MT-CHG-001-医技工作站医技计费-SDD.md` |
| 前端入口 | 菜单 `MedicalTechWorkstation` · Tab「医技计费」（仅 `encounterScope=INPATIENT`） |

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.3.1 | 2026-10-18 | **代码先行回写**：**BR-MT-CHG-004**：目录 `lotNumber` 落请求并 finalize 锁批定批（同护士划价 **BR-IP-PRC-008/019**）；对齐 **SDD** / **DM-WKF-003/007** |
| v1.3.0 | 2026-10-11 | **代码先行回写**：支持住院医技划**诊疗项目**（IF-06/07；来源仍 `medical-tech-pricing`）；BR-008/009；列表/冲销分轨 orderType |
| v1.2.0 | 2026-09-23 | 已划价列表补**确费人/计费人**展示名 |
| v1.1.0 | 2026-09-22 | 写源 `medical-tech-pricing`；BR-MT-CHG-005 过渡读历史误标 nurse-pricing（禁新写） |
| v1.0.2 | 2026-09-21 | 挂接前端菜单入口（住院 Tab；门诊壳隐藏） |
| v1.0.1 | 2026-09-19 | 头注挂接配套 SDD 路径 |
| v1.0.0 | 2026-09-19 | 初版：仅住院仅耗材；本科耗材房；划价不扣库 |
