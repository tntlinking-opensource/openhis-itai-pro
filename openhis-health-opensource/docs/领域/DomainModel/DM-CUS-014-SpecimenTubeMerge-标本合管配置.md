# DM-CUS-014 SpecimenTubeMerge 标本合管配置

> **限界上下文**：本地扩展 · 标本合管  
> **聚合类型**：实体聚合（单表 `cus_specimen_tube_merge`）  
> **版本**：v1.3 · **日期**：2026-10-18

## 1. 概述

一行表示「某设备某合管组」内的一个绑定目标：

- `wkf_activity_definition`：检验项目或采血费 activity  
- `adm_device_definition`：合管耗材（须 `quantity` + `unit_code`）

结构：

- **设备**（`adm_device`）下可有多个合管组（`group_no`，从 1 起；展示名「组N」不落库）  
- **同组**检验项目合管（一管血）；**不同组**不合管（各采各管）  
- **每组**各自配置检验项目、耗材、采血费  

唯一性：

- **检验项目**（非采血费）：全局仅归属一个合管组（跨设备与同设备跨组均不可重复；领域写侧 BR-STM-002）  
- **采血费 / 耗材**：允许绑定到多台设备、同一设备下多个合管组（同组内仍不可重复）  
- 库表约束：`uk_cus_specimen_tube_merge_device_group_target`（`device_id + group_no + target_table + target_id`）；见 Flyway V48

存量：旧数据无组概念时统一落入 `group_no=1`；后续仅手动新增组。

## 2. 写侧

`alignBindingsForDevice`：按 `device_id` **全量对齐全部组**——入参行为期望保留集（须带 `groupNo`），缺席旧行软删（含整组删除）。

采血费：诊疗目录须存在名称精确为「采血费」的 **唯一** activity；配置页在各组「绑定检验项目」标题行内勾选；界面展示名写死「采血费」。未找到 / 名称不唯一时须给出可读业务提示（勿笼统「目标冲突」）。

前端选品：检验项目/耗材使用目录分页 + `SelectTable` 多列展示（编码、规格、单位、售价等），非简易 Option 下拉。

## 3. 读侧

`listByDeviceId`：返回设备下未删绑定行（含全部组）。  
`listByDeviceIdAndGroupNo`：返回指定组绑定行。  
`findGroupRefByActivityId` / `mapGroupRefByActivityIds`：检验 activity 反查合管分组（`deviceId + groupNo`）；**仅唯一归属时返回**（采血费跨组多行时不参与合管分组反查）。  
`findDeviceIdByActivityId` / `mapDeviceIdByActivityIds`：兼容包装，仅回填设备主键。  
应用层 `SpecimenTubeMergeBindResolveSupport#listDerivedBindsByDeviceIdAndGroupNo`：列出该组采血费 + 耗材派生绑定（不含组内检验 activity 行）。

## 4. 业务规则

- BR-STM-001：耗材行数量>0 且单位必填  
- BR-STM-002：检验项目（`wkf_activity_definition`，不含采血费）跨设备与跨组全局唯一；采血费与耗材允许跨设备、跨组复用；同一合管组内任意目标不可重复（BR-STM-003）  
- BR-STM-003：保存时同一合管组内目标不可重复  
- BR-STM-010：开嘱/绑定查询时，activity 已配置合管则优先合管派生绑定，否则回落 `cus_service_device_template`；同批待签发同一合管组采血费/耗材各只带出一份  
- BR-STM-011（Phase 3，院点 H22010402403 / H22010202493 / H22010200596）：LIS medsync 外发时**同一申请单**（`wkf_service_request.prescription_no`）+ 同一合管组（`deviceId+groupNo`）一组 POST；**不同申请单禁止合管串号**（`prescription_no` 空则本 SR 独占一桶）；`groupCode`/`groupName` 对码字段 `+` 拼接；`applyNo` 取桶内首条 SR 的 `bus_no`（禁止把 `prescription_no` 当 applyNo）  
- BR-STM-012（Phase 4，同上院点）：医生站报告查询 LIS 仍逐 SR 列行；打开 PDF 的 `applyNo` 仅在**同一申请单 + 同一合管组**内回落锚点 SR（与外发分桶一致）；跨申请单各用本 SR `bus_no`，禁止整次就诊按合管组串历史报告  

## 9. 变更记录

| 日期 | 说明 |
|------|------|
| 2026-10-18 | 代码先行回写：合管/报告/补传分桶边界改为「申请单 + 合管组」；修复跨天重复开检验打开历史 PDF（BR-STM-011/012） |
| 2026-09-28 | 代码先行回写：设备下多合管组（`group_no`）；检验项目跨组唯一；派生/采血贴/LIS 按 `deviceId+groupNo` 聚组；存量入组 1（V48） |

<!-- MODE-B-METHOD-INVENTORY 2026-09-28 -->

## 附录 · 模式 B 已实现方法清单（2026-09-28）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`SpecimenTubeMergeDomainService`

| 方法 |
|------|
| `alignBindingsForDevice` |
| `deleteDeviceBindings` |

### QueryService：`SpecimenTubeMergeQueryService`

| 方法 |
|------|
| `listByDeviceId` |
| `listByDeviceIdAndGroupNo` |
| `findDeviceIdByActivityId` |
| `findGroupRefByActivityId` |
| `mapDeviceIdByActivityIds` |
| `mapGroupRefByActivityIds` |

