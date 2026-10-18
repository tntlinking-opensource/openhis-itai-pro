# AS-BAS-CAT-001 三大目录编辑门禁 · 应用服务详细设计（SDD）

> **编码**：AS-BAS-CAT-001-SDD · **版本**：v1.14 · **日期**：2026-10-06  
> **对应 AS**：`AS-BAS-CAT-001-三大目录编辑门禁.md`

---

## 1. 实现边界

| 项 | 内容 |
|----|------|
| **读侧编排** | `zxplat-application/.../shared/catalog/CatalogEditabilityResolver.java` |
| **写侧门禁** | `zxplat-application/.../shared/catalog/CatalogEditWriteGate.java` |
| **业务码常量** | `zxplat-core/zxplat-share/.../CatalogEditabilityConstants.java` |
| **查询模型** | `zxplat-application/.../shared/catalog/CatalogEditabilityQueryModel.java` |
| **应用服务** | `MedicationCatalogAppService`、`DeviceCatalogAppService`、`ServiceCatalogAppService` |

---

## 2. 跨聚合读能力（B.6.4）

| QueryService | 方法 | 用途 |
|--------------|------|------|
| `MedicationRequestQueryService` | `existsByMedicationId(Long)` | 药品临床引用 |
| `DeviceRequestQueryService` | `existsByDeviceDefId(Long)` | 耗材临床引用 |
| `ServiceRequestQueryService` | `existsByActivityId(Long)` | 诊疗本项临床引用 |
| `ServiceRequestQueryService` | `existsByActivityIdIn(Collection)` | 套餐父项批量临床引用（**BR-CAT-014**） |
| `ActivityDefinitionQueryService` | `listParentActivityIdsByChildrenId(Long)` | 子项 → 未删父套餐 id（**BR-CAT-014**） |
| `SupplyRequestQueryService` | `existsInboundByItemId(Long)` | 药/材入库引用（`APPROVAL`/`AGREE`） |

**判定顺序（诊疗·编辑）**：`resolveActivityEditability` → 本项 `existsByActivityId` → 否则 `listParentActivityIdsByChildrenId` + `existsByActivityIdIn` → 否则 `OK`。

**判定顺序（药/材·编辑）**：`resolveProductEditability` → 先临床 exists → 再 `existsInboundByItemId` → 否则 `OK`。删除仍用 `isLocked`（临床或入库任一即禁删），与编辑优先级无关。

---

## 3. 应用层接口契约

### 3.1 validate*CatalogEdit

| AppService 方法 | 入参 | 出参 |
|-----------------|------|------|
| `validateMedicationCatalogEdit` | `Long id` | `CatalogEditabilityQueryModel` |
| `validateDeviceCatalogEdit` | `String id` | 同上 |
| `validateServiceCatalogEdit` | `Long id` | 同上 |

**CatalogEditabilityQueryModel 字段**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `editabilityCode` | `String` | `OK` / `LOCK_CLINICAL` / `LOCK_INVENTORY` |
| `editabilityMessage` | `String` | 维护页展示文案 |

### 3.2 详情 DTO 回填

| DTO | 字段 |
|-----|------|
| `MedicationDetailDto` | `editabilityCode`, `editabilityMessage` |
| `DeviceCatalogDto` | 同上 |
| `ServiceCatalogDetailDto` | 同上 |

维护态价格展示口径：药/耗材 `purchasePrice` / `retailPrice` → **采购参考价** / **销售参考价**；诊疗仅 `retailPrice` → **售价**（无进价，维护页不展示/不提交 `purchasePrice`）。

**中药饮片 UI 刻度（BR-CAT-011）**：`medication_category_code=4` 时，药品维护页对**采购参考价（进价）展示与录入 3 位小数**（`CHARGE_MONETARY_TCM_UNIT_PRICE_DISPLAY_SCALE` / `MONEY_TCM_UNIT_PRICE_DP`）；**销售参考价（售价）仍 2 位**；落库仍 `scaleCalc` **6 位**；西药/中成药 UI 仍 2 位。批号进价查询/库房进价列等同口径；批号售价与收费仍 2 位。

### 3.3 更新 Request

| Request | 字段 | 说明 |
|---------|------|------|
| `UpdateMedicationCatalogRequest` | `editabilityCode` | 写前传入；服务端重算并 assert |
| `UpdateDeviceCatalogRequest` | `editabilityCode` | 同上 |
| `UpdateServiceCatalogRequest` | `editabilityCode` | 同上 |

---

## 4. 写侧 immutable 字段（CatalogEditWriteGate）

### 4.1 药品 `assertMedicationCatalogUpdateAllowed`

比对字段：`name`, `merchandiseName`, `pharmacologyCategoryCode`, `busNo`, `categoryCode`, `typeCode`, `ybType`, `doseFrom`, `doseUnitCode`, 四类单位编码（采购/拆零/销售/库存）, 三类拆零倍数（采购/库存/销售）。
`surplusRetentionBatchCode`（结余留用批次号）、`mrSettlementTypeCode`（病案结算类型）、`doseUnitRatio`（剂量→拆零倍数）、`totalVolume`（规格）**不在** immutable 集合（锁定态仍可改；剂量倍数对齐 AS BR-CAT-012；规格对齐 AS BR-CAT-013）。

**不比对**：采购/销售参考价（编辑接口不落价）；**拆分属性**（`partAttributeEnum` / `thoPartAttributeEnum`）；**医保等级** `chrgitmLv`；**剂量→拆零倍数** `doseUnitRatio`；**规格** `totalVolume`。

### 4.2 耗材 `assertDeviceCatalogUpdateAllowed`

比对字段：`name`, `merchandiseName`, `busNo`, `categoryCode`, `typeCode`, `itemTypeCode`, `ybType`, 单位编码（`purchaseUnitCode`/`splitUnitCode` 等），倍数。`chargeableFlag`、`mrSettlementTypeCode`、`totalVolume`（规格）**不在** immutable 集合。

**不比对**：采购/销售参考价；**医保等级** `chrgitmLv`；**规格** `totalVolume`（BR-CAT-013）。

### 4.3 诊疗 `assertServiceCatalogUpdateAllowed`

比对字段：`name`, `busNo`, `categoryCode`, `packageUnitCode`, `itemTypeCode`, `ybType`, 售价（请求未传则跳过；异常文案为售价）。`mrSettlementTypeCode`、`typeEnum`（业务类型）**不在** immutable 集合。

**不比对**：进价/`purchasePrice`（诊疗目录无进价）；**医保等级** `chrgitmLv`；**业务类型** `typeEnum`（**BR-CAT-015**）。

**维护页必填（BR-CAT-016，非 immutable）**：前端 `itemTypeCode` required、`typeEnum` 非 required。后端创建/更新：`typeEnum` 无必填；`assertItemTypeCodeWhenPricingPresent` 仅在售价/进价非空时要求 `itemTypeCode`。锁定态灰显字段由 `edit-dialog.vue` schema `disabled`（`isServiceFormFieldDisabled`）绑定，禁止仅靠 `updateSchema({ disabled })` 冲掉 options。

**诊疗写路径补充**：`ServiceCatalogAppService#editServiceCatalog` 在 `CatalogEditabilityConstants.isLocked` 时**不调用** `replacePriceLines`。

### 4.4 删除门禁（BR-CAT-009）

| AppService 方法 | 行为 |
|-----------------|------|
| `deleteMedicationCatalog` | 逐 id `resolveMedicationEditability` → `CatalogEditabilityConstants.isLocked` 则拒绝（临床开单或入库；不另写删除校验方法） |
| `deleteDeviceCatalog` | 逐 id `resolveDeviceEditability` → 同上 |
| `deleteServiceCatalog` | 逐 id `resolveActivityEditability` → 同上 |

锁定态抛 `ValidationException`：`删除{目录}失败：已发生业务引用，不允许删除`。编辑写侧字段门禁（`CatalogEditWriteGate`）**不动**。

---

## 5. 前端实现（openhis-pro-ui）

### 5.1 API（`apps/web-ele/src/api/core/system/catalog.ts`）

| 函数 | 网关 |
|------|------|
| `validateMedicationCatalogEditApi` | `/medicationCatalog/validateMedicationCatalogEdit` |
| `validateDeviceCatalogEditApi` | `/deviceCatalog/validateDeviceCatalogEdit` |
| `validateServiceCatalogEditApi` | `/serviceCatalog/validateServiceCatalogEdit` |

类型：`CatalogEditabilityResult`；更新 Params 含 `editabilityCode`。

### 5.2 共享工具 `catalog-editability.ts`

| 导出 | 用途 |
|------|------|
| `applyCatalogEditabilityFromDetail` | 从详情解析 code/message |
| `isMedicationCatalogFieldLocked` / `isDeviceCatalogFieldLocked` / `isServiceCatalogFieldLocked` | 字段级 disabled |
| `buildCatalogEditabilitySchemaPatches` | `formApi.updateSchema` 批量 disabled |

### 5.3 页面行为

1. `loadDetail` / `applyDetailFormValues` 设置 `editabilityCode`、`editabilityMessage`。
2. 锁定态展示 `ElAlert`（warning）。
3. `sync*EditabilitySchema()` 或 schema 内合并 `disabled`。
4. 编辑保存：`submitData.editabilityCode = editabilityCode`。
5. 新建模式：`editabilityCode` 固定 `OK`。
6. 价格表单项 label：药/耗材为采购参考价 / 销售参考价；诊疗为售价（无进价）。
7. **BR-CAT-011**：中草药（分类码 `4`）进售价 `InputNumber.precision=3`、列表 `formatMoneyUnitPriceDisplay(..., 3)`；其余分类仍 2 位。

### 5.4 前端锁定字段清单

与 `catalog-editability.ts` 中 `*_STRUCTURE_LOCKED_FIELDS` 保持一致：

- 药/耗材/诊疗均锁定 **名称**（药另含 `merchandiseName`、`pharmacologyCategoryCode`）
- **`mrSettlementTypeCode` 不纳入结构锁定**（锁定态仍可改；首页费用须「重新统计」）；诊疗页 `syncMrSettlementTypeOptions` **禁止**再绑 editability `disabled`
- 药品 **`doseUnitRatio` 不纳入临床锁定**（BR-CAT-012）；`doseUnitCode` 仍仅 `LOCK_CLINICAL` 锁定
- 药/耗材 **`totalVolume`（规格）不纳入锁定**（BR-CAT-013；含卖完/已开立）
- **不**锁定 `chrgitmLv`；药**不**锁定 `partAttributeEnum` / `thoPartAttributeEnum`
- 耗材 `chargeableFlag` 不纳入锁定清单

---

## 6. 领域层补充

| 聚合 | 变更 |
|------|------|
| `DeviceDefinition#assertUpdatableByBusinessCode` | 接受 `OK` / `LOCK_CLINICAL` / `LOCK_INVENTORY`（不再仅允许 OK） |

---

## 7. 文档变更历史

| 版本 | 日期 | 摘要 |
| ---- | ---- | ---- |
| v1.14 | 2026-10-06 | §4.3：BR-CAT-016 财务类型/业务类型必填口径；锁定灰显绑 schema |
| v1.13 | 2026-10-06 | §4.3：诊疗 `typeEnum` 移出 immutable（BR-CAT-015） |
| v1.12 | 2026-10-06 | §1：去掉 `basic/catalog/support` 双真源；常量路径对齐 `zxplat-core/zxplat-share` |
| v1.11 | 2026-09-29 | §1/§2：**BR-CAT-014** 子项继承父开立锁；读侧路径对齐 `shared/catalog`；增补 `listParentActivityIdsByChildrenId` / `existsByActivityIdIn` |
| v1.10 | 2026-09-22 | §4.1/4.2 / §5.4：药/耗材 `totalVolume` 移出 immutable（BR-CAT-013） |
| v1.9 | 2026-09-18 | §4.1 / §5.4：药品 `doseUnitRatio` 移出 immutable（BR-CAT-012）；诊疗病案结算 sync 残留清理 |
| v1.8 | 2026-09-10 | §4 / §5.4：病案结算类型移出 immutable；锁定态可改（对齐 AS BR-CAT-008） |
| v1.7 | 2026-08-03 | §3.2/§5.3：中药饮片进售价 UI 3 位（BR-CAT-011）；落库仍 6 位 |
| v1.6 | 2026-07-28 | §3.2/§4.3/§5.3：诊疗价格文案改为售价（无进价）；药/耗材仍为采购参考价/销售参考价 |
| v1.5 | 2026-07-17 | §4/§5.4：名称与药品性质入 immutable；拆分属性与医保等级可改；价格文案销售参考价；修正 Support 类名为 Resolver/WriteGate |
| v1.4 | 2026-07-17 | §2：编辑判定先临床后入库；删除与编辑优先级解耦 |
| v1.3 | 2026-07-16 | §4.4：删除门禁与编辑同口径（BR-CAT-009） |
| v1.2 | 2026-07-15 | §4.1/4.2：药/耗材维护态价格文案改为采购参考价/零售参考价 |
| v1.1 | 2026-07-14 | §4 / §5.4：病案结算类型纳入结构锁定；「器材」用语改为「耗材」；`chargeableFlag` 不入门禁 |
| v1.0 | 2026-07-04 | 初稿：Support 类、QueryService 扩展、DTO/Request、写侧字段清单、前端路径与 API |
