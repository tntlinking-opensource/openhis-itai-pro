# 基础资料 · 三大目录编辑门禁 · 应用服务设计（AS）

> **文档职责**：说明药品 / 耗材 / 诊疗三类目录维护页在**存在业务引用**时，如何判定 `editabilityCode`、裁剪可改字段、写侧拒绝越权修改；作为前后端联调与 DM §9 字段矩阵的上游契约。
>
> **编码**：AS-BAS-CAT-001 · **版本**：v1.16 · **日期**：2026-10-10
>
> **关联 DM**：`DM-MED-001` §9.1、`DM-ADM-008` §9.2、`DM-WKF-001` §6.2.6 / §9.6  
> **详细设计**：`AS-BAS-CAT-001-三大目录编辑门禁-SDD.md`

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | 维护页打开编辑弹窗时展示可编辑性说明；按业务码禁用结构/价格/拆零字段；保存时回传 `editabilityCode`；写侧对 immutable 字段做服务端断言；**锁定态禁止删除** |
| **在外** | 目录 CRUD 全量 IF（分页/导入/启停/医保对码等）仍分散于各目录 AppService；本 AS 约束**编辑态字段裁剪**与**删除门禁** |
| **涉及域** | MedicationDefinition / DeviceDefinition / ActivityDefinition（目录主数据）；MedicationRequest / DeviceRequest / ServiceRequest（临床引用）；SupplyRequest（入库引用）；ChargeItemDefinition（维护态定价行） |
| **编排权威** | 读侧 `CatalogEditabilityResolver`；写侧 `CatalogEditWriteGate`（`zxplat-application/.../shared/catalog`） |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **基础资料维护员** | 打开药品/耗材/诊疗编辑弹窗 → 若已有业务引用则看到告警与字段灰显 → 仅改允许字段 → 保存 |
| **系统** | 详情/validate 返回 `editabilityCode` + `editabilityMessage`；更新接口携带 `editabilityCode` 供写侧门禁 |

### 1.3 业务码（editabilityCode）

| 码 | 含义 | 药品/耗材触发 | 诊疗触发 |
| --- | --- | --- | --- |
| `OK` | 无引用，结构/维护态价格均可改 | 无 `MedicationRequest`/`DeviceRequest` 且无入库中/已同意 `SupplyRequest` | 无本项 `ServiceRequest`，且（若为套餐子项）无任一父项 `ServiceRequest` |
| `LOCK_CLINICAL` | 存在临床开单引用 | 存在任一未删 `MedicationRequest`/`DeviceRequest`（且未命中入库码） | 本项存在任一未删 `ServiceRequest`；**或**本项作为套餐子项时任一未删父项存在 `ServiceRequest`（**BR-CAT-014**） |
| `LOCK_INVENTORY` | 存在入库事实（仅药/耗材） | `SupplyRequest.item_id=目录主键` 且 `status_enum ∈ {APPROVAL, AGREE}` | **不适用** |

**优先级（药/耗材·编辑）**：先 `LOCK_CLINICAL`（开过），未开过再 `LOCK_INVENTORY`（入库）。删除与编辑不同：任一业务引用即整单禁删（见 BR-CAT-009），不按编辑优先级裁剪字段。

### 1.4 页面边界（openhis-pro-ui）

| 目录 | 维护页路径 |
| --- | --- |
| 药品 | `apps/web-ele/src/views/basic/medicationcatalog/medication-catalog-edit-dialog.vue` |
| 耗材 | `apps/web-ele/src/views/basic/devicecatalog/[id]/index.vue` |
| 诊疗 | `apps/web-ele/src/views/basic/servicecatalog/edit-dialog.vue` |

前端共享工具：`apps/web-ele/src/views/basic/catalog/catalog-editability.ts`。

---

## 2. 接口总览

| IF 编号 | 用户动作 | 接口语义 | 读/写 | 关键规则 |
| --- | --- | --- | --- | --- |
| **IF-BAS-CAT-01** | 打开药品编辑 | 详情回填或单独校验可编辑性 | 读 | BR-CAT-001～003 |
| **IF-BAS-CAT-02** | 保存药品 | 写侧 immutable 字段断言 | 写 | BR-CAT-004 |
| **IF-BAS-CAT-03** | 打开耗材编辑 | 同上 | 读 | BR-CAT-001～003 |
| **IF-BAS-CAT-04** | 保存耗材 | 同上 | 写 | BR-CAT-004 |
| **IF-BAS-CAT-05** | 打开诊疗编辑 | 同上（无码 2） | 读 | BR-CAT-001～003 |
| **IF-BAS-CAT-06** | 保存诊疗 | 同上；锁定态跳过维护态定价行替换 | 写 | BR-CAT-004、BR-CAT-005 |

**网关路径（与实现对齐）**：

- `POST /medicationCatalog/validateMedicationCatalogEdit`
- `POST /medicationCatalog/getMedicationCatalog`（详情含 `editabilityCode`）
- `POST /medicationCatalog/editMedicationCatalog`（Request 含 `editabilityCode`）
- `POST /deviceCatalog/validateDeviceCatalogEdit`
- `POST /deviceCatalog/getDeviceCatalog`
- `POST /deviceCatalog/editDeviceCatalog`
- `POST /serviceCatalog/validateServiceCatalogEdit`
- `POST /serviceCatalog/getServiceCatalog`
- `POST /serviceCatalog/editServiceCatalog`

---

## 3. 业务规则（BR-CAT-*）

| 规则 ID | 描述 |
| --- | --- |
| **BR-CAT-001** | 业务码由应用层 `CatalogEditabilityResolver` 跨聚合判定，目录聚合本身不实现 |
| **BR-CAT-002** | 详情 DTO 与 validate 接口均须返回 `editabilityCode`、`editabilityMessage` |
| **BR-CAT-003** | 前端锁定态须 disabled 与 DM 字段矩阵一致的 immutable 字段；厂家/批准文号/说明等描述字段仍 editable；**名称**锁定；药品另锁**药品性质** |
| **BR-CAT-004** | 写侧 `CatalogEditWriteGate` 比对库内快照与 Request，immutable 字段变更抛 `ValidationException` |
| **BR-CAT-005** | 诊疗 `LOCK_CLINICAL` 时应用层跳过 `replacePriceLines`，避免误动维护态 PURCHASE/SALE 行 |
| **BR-CAT-006** | 锁定态（`LOCK_CLINICAL` / `LOCK_INVENTORY` / 诊疗锁定）**医保等级**与**拆分属性**（药）仍可改 |
| **BR-CAT-007** | 更新 Request 须携带与服务端解析一致的 `editabilityCode`（防篡改；写侧以服务端重算为准） |
| **BR-CAT-008** | 锁定态药品/耗材/诊疗 **`mrSettlementTypeCode`（病案结算类型）仍可改**（与医保等级同口径；变更后病案首页须点「重新统计」才覆盖费用快照）；耗材 `chargeableFlag`（是否收费耗材）始终可改 |
| **BR-CAT-009** | 删除门禁：删除前复用编辑第一道 `resolve*Editability` + `CatalogEditabilityConstants.isLocked`；锁定态拒绝，提示 `删除{目录}失败：已发生业务引用，不允许删除`（**不**新增删除专用校验方法，编辑写侧字段门禁不动） |
| **BR-CAT-010** | 维护页价格展示文案：药/耗材维护态 `PURCHASE`/`SALE` 分别展示为**采购参考价**/**销售参考价**；**诊疗**仅维护态 `SALE`，展示为**售价**（无进价/采购参考价；JSON 字段名仍为 `retailPrice`，`purchasePrice` 维护页不展示不提交） |
| **BR-CAT-011** | **中药饮片进价 UI 刻度**：药品分类 `medication_category_code=4`（中草药）时，维护页与列表对 **`purchasePrice`（采购参考价/进价）允许展示与录入 3 位小数**；**`retailPrice`（销售参考价/售价）与限价恒为 2 位**（与西药同）。西药/中成药进价仍为 2 位。写侧闸见 `PackageUnitPriceScaleGate` / **BR-MON-001**；UI 3 位进价不得反推为售价或结算 2 位权威 |
| **BR-CAT-012** | 药品 **`doseUnitRatio`（剂量→拆零倍数 / 拆零/剂量倍数）** 无论发布态或是否发生业务引用（`LOCK_CLINICAL` / `LOCK_INVENTORY`）**始终可改**；写侧不入 immutable；前端不 disabled。剂量单位 `doseUnitCode` 仍仅在 `LOCK_CLINICAL` 锁定 |
| **BR-CAT-013** | 药品/耗材 **`totalVolume`（规格）** 无论发布态或是否发生业务引用（`LOCK_CLINICAL` / `LOCK_INVENTORY`，含卖完）**始终可改**；写侧不入 immutable；前端不 disabled。剂型/剂量单位仍仅在 `LOCK_CLINICAL`（药）或结构锁（耗材剂量单位）锁定 |
| **BR-CAT-014** | **诊疗套餐子项继承父项开立锁**：开立/划价只落父项 `ServiceRequest.activity_id`（**BR-IP-PRC-011**）。编辑门禁判定子项时，除本项 `existsByActivityId` 外，须经 `ActivityDefinitionQueryService#listParentActivityIdsByChildrenId` 反查未删父 id，再 `ServiceRequestQueryService#existsByActivityIdIn`；任一父项已开立 → 子项同样 `LOCK_CLINICAL`（售价等 immutable 与父项同口径）。提示文案：`CatalogEditabilityConstants.MSG_LOCK_CLINICAL_PACKAGE_CHILD` |
| **BR-CAT-015** | 诊疗 **业务类型 `typeEnum`（术语 `activity_type_code`）** 无论是否发生业务引用（`LOCK_CLINICAL`）**始终可改**；写侧不入 immutable；前端不 disabled。目录分类 `categoryCode` 仍锁定（开立/LIS-PACS 身份）。业务类型仅驱动申请单分类 chips |
| **BR-CAT-016** | 诊疗维护页必填：**财务类型 `itemTypeCode`**（前端 `rules: required`）。后端 `CreateServiceCatalogRequest.itemTypeCode` **无** `@NotBlank`；`ServiceCatalogAppService#assertItemTypeCodeWhenPricingPresent` 在售价或进价非空时拒绝空财务类型（定价 insert 另见 **BR-CHGDEF-WR-002**）。**业务类型 `typeEnum` 亦必填**（前端 `rules: required`；选项仅术语 `activity_type_code`；领域拒空；**禁止**硬编码兜底；对齐 **DM-WKF-001 BR-ACTDEF-011**） |

---

## 4. 字段可编辑矩阵（摘要）

完整矩阵见各 DM §9；锁定态下 **immutable** 字段组：

| 目录 | 锁定字段（摘要） | 锁定态仍可改 |
| --- | --- | --- |
| **药品** | 通用名称/商品名称、药品性质、分类、财务/医保类别、剂型、剂量单位、采购/拆零/销售/库存单位及对应拆零倍数、采购参考价/销售参考价 | 厂家、批准文号、医保编码、拆分属性、医保等级、病案结算类型、规格、剂量→拆零倍数、最高零售价等 |
| **耗材** | 耗材名称/商品名称、分类、耗材类型、财务/医保类别、单位/倍数、采购参考价/销售参考价 | 厂家、规格、是否收费耗材、医保等级、病案结算类型、最高零售价等 |
| **诊疗** | 诊疗名称、目录分类、计价单位、财务/医保类别、售价 | 拼音/五笔、说明、子项、医保等级、病案结算类型、业务类型、最高零售价等 |

---

## 5. 异常口径

| 场景 | 用户可见提示 |
| --- | --- |
| 锁定态修改 immutable 字段 | `更新目录失败：{字段名}已发生业务引用，不允许修改` |
| 锁定态删除目录项 | `删除{目录}失败：已发生业务引用，不允许删除` |
| 记录不存在 / 已废弃 | 各目录 AppService 既有校验 |

---

## 6. 测试要点

1. 无引用目录：全字段可编辑，保存成功。
2. 仅临床引用：名称/结构/价格/拆零 disabled；改价保存被拒；拆分属性、医保等级、病案结算类型、规格、剂量→拆零倍数可改；药品性质/剂量单位不可改。
3. 仅入库引用（药/耗材）：同 2（无药品性质以外的额外差异）；病案结算类型、规格与剂量→拆零倍数仍可改。
4. 临床 + 入库：按 `LOCK_CLINICAL` 展示（编辑优先二级）；删除仍被拒。
5. 前端保存 Request 携带 `editabilityCode`；Network 与详情一致。
6. 锁定态目录删除被拒，提示「已发生业务引用，不允许删除」。
7. 维护页价格标签：药/耗材为采购参考价 / 销售参考价；诊疗为售价（无进价）。
8. 中草药（分类码 4）：进售价 InputNumber / 列表可 3 位小数；西药/中成药仍 2 位；落库 6 位不变（BR-CAT-011）。
9. 锁定态药品改 `doseUnitRatio` 保存成功；前端「拆零/剂量倍数」不灰显（BR-CAT-012）。
10. 锁定态诊疗改 `mrSettlementTypeCode` 可点选保存；`syncMrSettlementTypeOptions` 不得再绑 editability disabled（BR-CAT-008 残留清理）。
11. 锁定态（含卖完/已开立）药/耗材改 `totalVolume` 保存成功；前端规格不灰显（BR-CAT-013）。
12. 父套餐已开立、子项目录无独立 SR：打开子项编辑 → `LOCK_CLINICAL` + 套餐父项文案；改售价保存被拒；跳过 `replacePriceLines`（BR-CAT-014）。
13. 锁定态诊疗改 `typeEnum`（业务类型）保存成功；前端业务类型下拉不灰显（BR-CAT-015）。
14. 锁定态：名称/目录分类/计价单位/财务类型/医保类别/售价灰显；业务类型不灰（schema 内 `disabled`，BR-CAT-003）。
15. 新增/未锁编辑：财务类型、业务类型缺省均无法提交；业务类型下拉含字典新增码（刷缓存/重开编辑后，BR-CAT-016 / BR-ACTDEF-011）。

---

## 7. 文档变更历史

| 版本 | 日期 | 摘要 |
| ---- | ---- | ---- |
| v1.16 | 2026-10-10 | **BR-CAT-011** 纠偏：中药仅**进价** UI/维护允许 3 位；**售价/限价恒 2 位**；对齐 `PackageUnitPriceScaleGate` / DM-MED-001 §9.3 |
| v1.15 | 2026-10-07 | **BR-CAT-016**：业务类型维护页必填；选项仅术语 `activity_type_code`；对齐 **DM-WKF-001 v1.0.20** |
| v1.14 | 2026-10-06 | **BR-CAT-016**：财务类型维护页必填、后端有价才验；业务类型不必填；锁定字段须灰显 |
| v1.13 | 2026-10-06 | **BR-CAT-015**：诊疗业务类型 `typeEnum` 移出 immutable；锁定态仍可改（申请单分类 chips）；目录分类仍锁 |
| v1.12 | 2026-10-06 | §1.1：编排权威路径对齐 `shared/catalog`（删除已迁走的 `basic/catalog/support`） |
| v1.11 | 2026-09-29 | **BR-CAT-014**：诊疗套餐子项继承父项开立锁；§1.3 诊疗触发口径扩写 |
| v1.10 | 2026-09-22 | BR-CAT-013：药/耗材规格锁定态始终可改（含卖完）；§4/§6 矩阵与测试点 |
| v1.9 | 2026-09-18 | BR-CAT-012：药品剂量→拆零倍数锁定态始终可改；§4/§6 矩阵与测试点；诊疗病案结算残留清理说明 |
| v1.8 | 2026-09-10 | BR-CAT-008：锁定态病案结算类型改为可改（与医保等级同口径；首页须「重新统计」） |
| v1.7 | 2026-08-03 | 增补 BR-CAT-011：中药饮片维护态进售价 UI 允许 3 位小数（落库仍 6 位） |
| v1.6 | 2026-07-28 | BR-CAT-010：诊疗价格文案改为售价（无进价/采购参考价）；药/耗材仍为采购参考价/销售参考价 |
| v1.5 | 2026-07-17 | 锁定态：名称不可改、药品性质不可改；拆分属性与医保等级可改；价格文案统一为采购参考价/销售参考价（BR-CAT-006/010） |
| v1.4 | 2026-07-17 | 编辑优先级改为先临床后入库；删除仍按 isLocked 整单禁删；药/耗材写门禁不再比对维护态参考价 |
| v1.3 | 2026-07-16 | 增补删除门禁 BR-CAT-009：锁定态禁止删除 |
| v1.2 | 2026-07-15 | §4：药/耗材锁定字段文案改为采购参考价/零售参考价（诊疗仍为购入价/零售价） |
| v1.1 | 2026-07-14 | 页面/矩阵用语「器材」→「耗材」；增补病案结算类型门禁（BR-CAT-008）与耗材收费标记可编辑口径 |
| v1.0 | 2026-07-04 | 初稿：业务码、IF、BR、字段矩阵摘要、前端页面路径；对齐 zxplat + openhis-pro-ui 落地 |
