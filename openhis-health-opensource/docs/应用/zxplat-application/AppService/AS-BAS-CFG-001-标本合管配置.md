# AS-BAS-CFG-001 标本合管配置

> **编码**：AS-BAS-CFG-001 · **菜单**：配置管理 · **菜单名**：合管配备 · **路径**：`/config/specimentubemerge`  
> **AppService**：`SpecimenTubeMergeAppService` → `/api/app/specimenTubeMerge/*`  
> **版本**：v1.3 · **日期**：2026-09-28 · 设备下多合管组（`group_no`）

## 1. 接口（Phase 1 配置）

| IF | 方法 | 说明 |
|---|---|---|
| IF-STM-01 | init | 单位/目标表等静态下拉（采血费为各组内勾选，见 IF-STM-04 `groups[].bindBloodCollectionFee`） |
| IF-STM-02 | pageDevices | 左栏启用设备分页 |
| IF-STM-03 | getBindings | 读设备合管配置（含多组） |
| IF-STM-04 | saveBindings | 全量保存设备下全部组 |
| IF-STM-05 | searchActivities | 检验类 activity（category=22）简易下拉；**合管配备页选品走诊疗目录分页** |
| IF-STM-06 | listConsumableOptions | 耗材简易下拉；**合管配备页选品走器材目录分页** |
| IF-STM-07 | unitOptionsByTarget | 单位联动 |
| IF-STM-08 | deleteDeviceBindings | 清空设备下全部绑定（管理用，前端 Phase 1 未接） |

## 2. getBindings 出参

`deviceId`、`deviceName`、`deviceBusNo`、`groups[{groupNo, groupName, activityIds[], bindBloodCollectionFee, consumableLines[{id,targetId,targetName,quantity,unitCode,unitName,displayOrder}]}]`

- `groupName` 只读展示「组N」，不落库；存量无组数据迁移后均为组 1。

## 3. saveBindings 入参

`deviceId`、`groups[{groupNo?, activityIds[], bindBloodCollectionFee, consumableLines[{targetId, quantity, unitCode, displayOrder?}]}]`

- 未传 `groupNo` 时服务端按提交顺序从 1 编号；空 `groups` 表示清空该设备全部绑定。

### 3.1 保存校验（IF-STM-04 / BR-STM-002）

| 目标 | 规则 | 失败提示要点 |
|---|---|---|
| 检验项目 | 跨设备与同设备跨组全局唯一；同组内不重复 | 「该检验项目已绑定到…，同一检验项目不能配置到多个合管组」/「不同合管组不能配置相同检验项目」 |
| 采血费勾选 | 诊疗目录须存在名称精确唯一「采血费」；**允许**多设备、多组同时勾选 | 未找到 / 名称不唯一时明示「勾选采血费失败：…」 |
| 耗材 | **允许**跨设备、跨组复用同一耗材定义；同组内不重复；数量>0 且单位必填 | 数量/单位/同组重复 |

## 4. 领域

`DM-ADM-015`（设备存在性）+ `DM-CUS-014`（绑定对齐，含 `group_no`）

## 5. 环境与种子

- 采血费：须存在 `wkf_activity_definition.name = 采血费`（名称精确**唯一**匹配；展示名代码写死「采血费」）
- 菜单/表结构：`V11` 初建；全局目标 UK 已由 `V18` 删除；**`V48` 增加 `group_no`，存量入组 1**

## 6. 前端合管配备页（Phase 1）

- **分组**：设备下可「新增分组」；展示「组1」「组2」…；删除组后保存时按序重排或按提交 `groupNo` 对齐。
- **采血费**：各组「绑定检验项目」标题行内独立勾选；保存字段 `groups[].bindBloodCollectionFee`。
- **检验项目选品**：`SelectTable` + `serviceCatalog/pageServiceCatalog`（`categoryCode=22`）；跨组不可选相同项目。
- **耗材选品**：`SelectTable` + `deviceCatalog/pageDeviceCatalog`；各组独立。
- **保存**：仍走 IF-STM-04 `saveBindings` 全量契约（整机全部组一次提交）。

## 7. Phase 2 查询与开嘱带出（已实现）

### 7.1 绑定解析优先级（BR-STM-010）

| 场景 | 规则 |
|---|---|
| 检验 activity 已在合管配置 | **只读**该合管组派生绑定（采血费 + 耗材）；**不查** `cus_service_device_template` |
| 未配置合管 | 回落 `cus_service_device_template`（门诊/住院按 range 区分） |
| 同批待签发去重 | 同一 `deviceId + groupNo` 下采血费/耗材各只带出 **1 份** |

### 7.2 读侧能力

| 组件 | 说明 |
|---|---|
| `SpecimenTubeMergeQueryService#findGroupRefByActivityId` | activity → 合管组（deviceId+groupNo） |
| `SpecimenTubeMergeQueryService#mapGroupRefByActivityIds` | 批量反查 |
| `SpecimenTubeMergeBindResolveSupport#listDerivedBindsByDeviceIdAndGroupNo` | 按组列出派生绑定 |

### 7.3 接入点

| 入口 | IF | 说明 |
|---|---|---|
| 门诊医生站绑定弹窗 | IF-DOR-12 / IF-DOR-23 | `DoctorStationAdviceOrderBindQueryService` + `advice-draft-bind.service` |
| 住院医生站开立绑定 | `resolveInpatientDoctorStationAdviceDraftBinds` | **恒空**（**BR-IP-ORD-036** / **§7.7**） |
| 住院护士执行 bindItems | IF-IP-EXE-01 | 未合管挂住院模板；已合管空（**BR-IP-EXE-013**） |
| 门诊护士站采血贴 | IF-NUR-02 enrich | `groupSpecimenBarcodePrintTargets`（按 device+groupNo） |
| 住院护士站采血贴 | IF-IP-EXE-01 enrich | `groupPrintPendingSpecimenBarcodeRows` |

### 7.4 采血条码/贴

- 前端按 `specimenTubeMergeDeviceId` + `specimenTubeMergeGroupNo` 合管分组打印；不同组不合管。

### 7.5 Phase 3 LIS 外发合管

| 项 | 规则 |
|---|---|
| 分组 | 同就诊 + 同合管组（`mapGroupRefByActivityIds`，键为 deviceId:groupNo） |
| 其余 | 与既有 `+` 拼接 / 锚点 SR 口径不变 |

## 8. 变更记录

| 日期 | 说明 |
|------|------|
| 2026-09-28 | 设备下多合管组；get/save 契约改为 `groups[]`；开嘱/采血贴/LIS 按组聚组 |
