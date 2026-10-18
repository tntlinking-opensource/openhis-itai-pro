# health-module-support 消解方案

> 状态：**S7 + S8 已完成（2026-07-12）** · `health-module-support` 已删除 · Enforcer + ArchUnit 守卫已落地  
> **S8**：`application/shared/` 收敛优化（§12）——125 → **89** 类；0 反向依赖；application 层 **0** 生产 `*Support`（S8-C2/C3）  
> 目标（S0～S7）：**删除本 Maven 模块**，将 246 个类按职责归位，不引入新的「Adapter 桶」或 FlowContributor。

---

## 0. 优化方案（总览）

### 0.1 三个优化目标


| 目标         | 做法                                                                            | 预期结果                                       |
| ---------- | ----------------------------------------------------------------------------- | ------------------------------------------ |
| **结构**     | 删除 `health-module-support`，类归位 application / infrastructure / domain          | 消除误导包名与 AutoConfiguration 扫描耦合             |
| **读路径**    | Loader hydrate → Domain L1；页面只留 L0 分页 + L2 DTO                                | Loader 数量 ↓、bridge 与 app 同读路径              |
| **Report** | **保留** `application/report/`**；`querysupport/report/` 迁入各 feature/support（§4） | support −4（迁出非删除）；domain report API **保留** |


### 0.2 核心策略：读路径三段式

**所有列表/详情读统一为：**

```
① L0 分页/筛选（DB）  →  ② L1 batch hydrate（domain，可多次查 + 内存拼）  →  ③ L2 DTO（application，无 I/O）
```


| 原 Loader 职责             | 优化后归属                             | 是否可删 Loader           |
| ----------------------- | --------------------------------- | --------------------- |
| 选 PageQuery、调分页         | L0 `*QueryService.pageXxx`        | 简单页：AppService 直调     |
| 多表 batch + 拼 QueryModel | **L1 新/扩 `*ContextQueryService`** | Loader 变薄或删除          |
| 诊断/术语/格式化/打印列           | L2 `*Assembler` / `*Enricher`     | 保留，纯内存                |
| 写路径跨聚合                  | `*Orchestrator`（原 orchestration）  | 与读分离，不并进 QueryService |


**Domain 允许多次查询 + 内存 join**（3～5 轮 batch/请求）；禁止的是 N+1 与全表拉回再 filter。

### 0.3 L1 优先建设清单（读合并）

按 ROI 排序，消解过程中同步做：


| 优先级 | Domain 能力                                                                    | 替代/support 类                                            | 步骤                               |
| --- | ---------------------------------------------------------------------------- | ------------------------------------------------------- | -------------------------------- |
| P0  | `EncounterContextQueryService` 扩展 batch hydrate                              | `Outpatient/InpatientEncounterContextLoader` 内重复组装      | S2b：Loader 委托 domain，删 duplicate |
| P0  | `ProductDefinitionQueryService.batchLoadByKeys` + `**shared/product/dto/*`** | `ProductDefinitionBatchLoader` + **DisplayDto（155 引用）** | S2：uplift + dto 同批迁              |
| P1  | `MedicationDispenseQueryService.pageSlip` 强化                                 | `PharmacyDispenseSlipQuerySupport` + PageLoader 内查询     | S3 Pharmacy 试点                   |
| P2  | `InpatientSettleContextQueryService`（charge/payment batch）                   | `InpatientSettleReadLoader` 内 hydrate 部分                | S5 financial                     |
| P2  | 医嘱执行列表 batch 产品/术语                                                           | `NurseStationOrderExecuteListLoader` 部分逻辑               | S4 nursestation                  |


**不 uplift**：门诊/住院 slip 合一、结算多 tab 合一、万能 mega-query。

### 0.4 Loader 优化决策树

> S8 补充：**默认** `application/{domain}/{feature}/support/`；仅 exceptions 进 `shared/{topic}/`（§12.1）。

```
该读逻辑是否 ≥2 消费者 或 bridge 需要？
  ├─ 否 → 迁 {domain}/{feature}/support/（或 AppService 直调 L0）
  └─ 是 → 投影是否相同且无 UI 列？
        ├─ 否 → L1 只抽公共 batch 子步骤；页面各留 L2
        └─ 是 → 迁入 domain L1；或留 shared/{topic}/ 薄 L2

该页是否仅单聚合分页？
  └─ 是 → 删除 Loader，AppService → L0.page + Mapper
```

### 0.5 分阶段交付（PR 粒度，2026-07-12 确认）


| 阶段           | 交付物                                                          | 读路径优化点                                        | 验收                                                         |
| ------------ | ------------------------------------------------------------ | --------------------------------------------- | ---------------------------------------------------------- |
| **S0**       | 冻结 `module.`* 新增                                             | —                                             | CI 门禁 / Code Review 拒绝新增                                   |
| **S-R**      | report 4 类 support → `application/report/{feature}/support/` | —                                             | compile + test 绿；同步 AS-RPT SDD                             |
| **S1**       | platform → infrastructure                                    | —                                             | identity/org/审批 Gate；`WhaleRoleLookup` 改调 `UserAppService` |
| **S2a～S2d**  | shared 闭包（**纯移动**，4 PR）                                      | pagination → validator → product/dto → option | 每 PR import 消减可追踪                                          |
| **S2-L1a/b** | L1 uplift（**独立行为 PR**）                                       | Product batch；Encounter hydrate 去重            | 与 §5.0「禁止迁+重构同 PR」一致                                       |
| **S3**       | Pharmacy 试点                                                  | slip pagination-first                         | **先录 baseline**，迁移后 p95 不比 baseline 差                      |
| **S4**       | Nursestation                                                 | 按 Feature 拆分                                  | 该域无 `import com.zxplat.domain.module`                     |
| **S5**       | 其余域 + **workinjury**                                         | financial、inventory、encounter、advice…         | 含 `application/insurance/workinjury` module import 清零      |
| **S6～S7**    | 删模块 + Enforcer                                               | 含 observability 2 类删除                         | 全仓库无 module 包                                              |
| **S8**        | `shared/` 收敛（§12）                                            | 消费者审计 → feature 回迁；topic kebab-case；解耦反向依赖 | 125 → **89** 类；0 反向依赖；0 topic 嵌套子包                      |


### 0.6 量化预期（消解完成后）


| 指标                                | 现状                            | 目标                             |
| --------------------------------- | ----------------------------- | ------------------------------ |
| Maven 模块                          | `health-module-support` 246 类 | **0**（模块删除）                    |
| `*PageLoader` / 厚 `*QuerySupport` | ~40+                          | **≤15**（仅复杂页保留薄 L2）            |
| domain L1 Context/Batch 服务        | 1（Encounter）                  | **≥4**（+Product、Settle、Slip 等） |
| 单请求 DB round-trip（列表页）            | 部分页 N+1 风险                    | **1 分页 + 3～5 batch**           |
| bridge 读 application              | 0（已禁止）                        | 保持；共用 L1                       |
| `application/shared/` 类数          | **125**（S7 后实测）               | **~95 KEEP** + 单消费者迁 feature（§12） | **✅ 89** |
| shared → feature 反向依赖           | **3** 处（§12.4）                  | **0**                               |
| topic 包名 kebab-case 合规          | 6 处 camelCase + 5 处嵌套子包       | **0**                               |


### 0.7 简单页 vs 复杂页（示例）

**简单页 — 可删 Loader**

```java
// AppService
Page<ChargeItemRow> page = chargeItemQueryService.pageByEncounter(query);
return mapper.toResponse(page);
```

**复杂页 — 薄 Loader 保留**

```java
// EncounterMedicationDispenseSlipPageLoader（L2）
Page<Long> slipIds = medicationDispenseQueryService.pageSlipIds(query);           // L0
List<SlipRowModel> rows = medicationDispenseQueryService.hydrateSlipRows(slipIds); // L1
Map<ProductKey, ProductDisplay> products = productDefinitionQueryService.batchLoadByKeys(keys); // L1
return assembler.toDto(rows, products, enricher.printColumns());                   // L2
```

---

## 1. 为什么要消解


| 问题        | 现状                                                                                                                          |
| --------- | --------------------------------------------------------------------------------------------------------------------------- |
| POM 描述过时  | 「供外部 Module adapter 共用的 HIS 读路径」——**已无 bridge 消费者**                                                                         |
| 实际消费者     | **465** 个源文件、**1621** 处 `import com.zxplat.domain.module.`*（含大量 `dto/`、`request/`、`mapper` 对 module DTO 的引用，非仅 AppService） |
| Bridge 禁令 | 各 `module-*-bridge` Enforcer **禁止**依赖 `health-module-support`                                                               |
| 包名误导      | `com.zxplat.domain.module.`* 暗示 extension module，实为 application 读/写编排                                                      |
| 扫描耦合      | `HealthModuleSharedAutoConfiguration` 扫描整个 `com.zxplat.domain.module`，与 application 启动边界纠缠                                 |


消解后：**bridge 只读 `zxplat-domain` QueryService；application 自管页面级 Loader/Assembler/Orchestrator；platform 能力进 infrastructure。**

---

## 2. 目标架构

```
zxplat-domain          Port 接口（FinanceApprovalAuthorizer 等）
zxplat-domain                 事实 + QueryService（bridge 与 app 共用读）
zxplat-domain
  ├ integration/              Extension SPI + NoOp（已存在）
  ├ platform/                 **HIS 侧** whale SPI 实现（新建，见 §2.4）
  │    identity/              implements ILoginUserOrganizationUnitSync 等
  │    organization/          implements IOrganizationUnitScopeBridge
  │    authorization/         implements zxplat-domain *Authorizer
  ├ observability/            产品级 pointcut 配置（可选）；通用切面在 zxplat-framework
  └ config/
whale-framework               SPI + 通用横切（**不依赖 zxplat-domain**）
  ├ zxplat-framework/               I*Bridge、ILogin*、PermissionAspect、AppServiceAspect…
  ├ zxplat-framework/           User/OU/Role 领域
  └ whale-permission/         IPermissionChecker、@RequirePermission
health-application
  ├ shared/                   跨页面（pagination、validator、diagnosis、product）
  └ {domain}/                 AppService + support/（Loader / Assembler / Orchestrator）
module-*-application          独立扩展页（CHS 模式：/api/app/insurance*）
module-*-bridge               ExtensionImpl only；读 zxplat-domain

DELETE: health-module-support
```

`**{domain}` 举例**（= `health-application` 下的一级业务包，按 **页面/工作台归属** 划分，不是 DM 聚合名）：


| `{domain}`          | 典型页面 / AppService   | 迁入的 support 示例（来自 module-support）                                                                               |
| ------------------- | ------------------- | --------------------------------------------------------------------------------------------------------------- |
| `outpatient/`       | 门诊医生站、门诊收费、门诊发药     | `querysupport/pharmacy/EncounterMedicationDispenseSlipPageLoader` → `outpatient/pharmacy/dispense/support/`     |
| `inpatient/`        | 住院护士站、住院结算、住院发药     | `querysupport/nursestation/NurseStationOrderExecuteListLoader` → `inpatient/nursestation/orderexecute/support/` |
| `inventory/`        | 药库/药房/科室库出入库        | `common/assembler/tracenumber/*WriteSupport` → `inventory/warehouse/stocktake/support/`                         |
| `finance/`          | 调价审批                | `querysupport/priceadjustment/*` → `finance/priceadjustment/support/`                                           |
| `basic/`            | 医嘱模板、岗位             | `querysupport/ordertemplate/*` → `basic/ordertemplate/support/`                                                 |
| `**mrms/`**         | **病案**（已合并进 health） | `MrmsOperatorResolver` → `application/shared/mrms/`（全 mrms Feature 共用）                                          |
| `shared/`（非 domain） | 多页共用                | `ProductDefinitionBatchLoader`、`PageQueryBinder`、诊断 Assembler                                                   |


**单域目录长什么样**（以门诊发药为例）：

```
health-application/.../application/outpatient/pharmacy/dispense/
  ├ service/OutpatientPharmacyDispenseAppService.java
  ├ support/
  │    ├ EncounterMedicationDispenseSlipPageLoader.java       ← L2 读
  │    ├ EncounterMedicationDispenseSlipReadAssembler.java
  │    └ （写路径 Orchestrator 若有，与 Loader **同级** 放本目录）
  ├ mapper/ ...
  └ dto/ request/ response/ ...
```

**与 `module-*-application` 的区别**：`insurance/workinjury/`* 现仍在 `health-application/insurance/`，终态应像 CHS 一样迁到 `module-wi-jilin-application`；核心 HIS 页留在 `health-application/{domain}/`。

### 2.1 放置规则 + 读模型分层

**放置（去哪）**

1. **跟着页面走** → `application/{domain}/{feature}/support/`（读 Loader、写 Orchestrator **同目录平铺**）；多 Feature 共用 → `application/shared/{topic}/`
2. **实现 domain-shared / zxplat-framework Port** → `zxplat-domain/platform/{topic}/`
3. **稳定跨消费者读事实** → `zxplat-domain` 的 `*QueryService`（见下表 L1）

**application 包深度（固定两层，禁止再嵌套）**

```
application/
├── shared/{topic}/              ← 多页共用（topic 仅一层，如 advice、inpatient-admission）
└── {domain}/{feature}/          ← 一个菜单/工作台
    ├── service/
    ├── support/                 ← Loader、Assembler、Orchestrator 平铺；禁止 support 下再分子包
    ├── mapper/
    └── dto/ request/ response/
```

禁止：`application/{domain}/support/`（缺 feature）、`…/support/orchestration/`、`…/support/loaders/` 等第三层。

`**application/shared/{topic}/` 已定 topic 清单**（仅一层，不再嵌套）：


| topic                           | 内容                                                |
| ------------------------------- | ------------------------------------------------- |
| `pagination/`                   | PageQueryBinder、PagedResultBuilder                |
| `product/` + `product/dto/`     | 产品展示、目录、BatchLoader 残留                            |
| `validator/`                    | 上下文 Resolver、Gate（非 platform Port）                |
| `option/`                       | 术语/频次选项 Loader                                    |
| `encounter/`                    | EncounterContextReader、ContextLoader              |
| `advice/`                       | OP/IP 共用医嘱 Assembler                              |
| `diagnosis/`                    | 诊断快照 Assembler                                    |
| `organization/`                 | `OrganizationDisplayResolver` 等展示解析（无 whale Port） |
| `inpatient-admission/`          | 入院写校验 + 写入                                        |
| `inpatient-vitals/`             | 入院体征                                              |
| `inpatient-transfer/`           | 转科出院 Hold                                         |
| `nursestation-device-dispense/` | 设备发放护士流                                           |
| `nursestation-procedure/`       | 处置执行人                                             |
| `payment/`                      | 第三方支付 + 三方退款                                      |
| `financial/`                    | 日结 FinType 等跨 OP/IP                               |
| `tracenumber/`                  | 追溯码写路径                                            |
| `concurrency/`                  | HealthConcurrencySupport                          |
| `mrms/`                         | MrmsOperatorResolver                              |
| `converter/`                    | `TextValueConverter`（9 consumers，X-OP/IP；§12.2 可上提 framework） |
| `pharmacy/`                     | `MedicationDispenseInventoryAvailabilityGate`（3 consumers，X-OP/IP） |


**S8 放置原则（§12.1，优先于上表「历史落位」）**

```
默认 → application/{domain}/{feature}/support/（跟着页面走）
例外 → application/shared/{topic}/（仅当 ≥2 消费者 且 投影相同，或 INFRA）
```

**读模型分层（合并到哪）——合并的是「事实加载」，不是「页面 DTO」**


| 层           | 位置                                                      | 职责                                         | 性能要求                                 |
| ----------- | ------------------------------------------------------- | ------------------------------------------ | ------------------------------------ |
| **L0 单聚合**  | `zxplat-domain/{aggregate}/service/*QueryService`       | 单表/单聚合 CRUD 读、Repository Specification 分页  | 条件下推 DB；禁止 application 直连 Repository |
| **L1 跨聚合读** | `zxplat-domain` 内 `*ContextQueryService`、`*BatchLookup` | 多聚合 **按 ID 批量** 组装 **QueryModel**（无 UI 字段） | **先 ID 分页/筛选，再 batch join**；同键只查一次   |
| **L2 页面读**  | `application/{域}/support/*Loader`、`*Assembler`          | 页面筛选编排、DTO/展示列、Enricher                    | **不新增 DB 往返**；只调 L0/L1 + 纯内存装配       |


```
AppService
  └─ L2 Loader（页面入口：Queue / Slip / Settle…）
        ├─ L0 EncounterQueryService.pageXxx(PageQuery)   ← 谓词、排序、分页在 DB
        └─ L1 EncounterContextQueryService.hydrate(ids)  ← 对当页 ID 批量 hydrate
              └─ L0 Patient / Org / Practitioner … batchByIds
```

**L1  uplift 判据（满足 ≥2 条才合并进 domain）**

- ≥2 个消费者（AppService 页 + bridge，或两个 AppService 页）
- 投影相同：同一套 `*QueryModel` 字段，无页面专属 UI 列
- 加载路径相同：同一批 L0 batch 调用顺序（避免「通用方法」暗中多查表）
- 数据量可控：按 **当页 encounterId/chargeItemId 列表** hydrate，非全表扫描

**禁止合并（保留 L2 分方法）**

- **队列/页签谓词不同** → 保留 `pageNurseStationQueue` / `pageSettleQueue` 等独立入口；合并的是 hydrate，不是 page 方法
- **门诊 vs 住院** 根聚合或 join 路径不同 → 不合成一个 `loadDispenseSlip(encounterClass, …)`
- **展示列可选**（打印 vs 列表 vs 导出）→ 用 L2 `Enricher` 叠加，不把打印字段并进 L1 QueryModel
- **万能 Query**（20+ 可选 filter 的一个方法）→ 用 typed `*PageQuery`，一种页面一种 Query 类

**性能硬约束（ArchUnit / Code Review 检查）**

1. **Pagination-first**：任何列表必须先 L0 分页拿 ID，再 L1 batch；禁止 `listAll()` 后内存 filter（除极小字典表）
2. **Batch-by-IDs**：关联加载用 `mapXxxByIds(Set<Long>)`；禁止循环单条 `getById`
3. **No N+1 in L2**：Loader 内 foreach 调 QueryService 单条查询 → 必须 refactor 为 batch
4. **Projection minimal**：L1 QueryModel 只含业务事实；诊断/术语/格式化放 L2 Assembler
5. **重复查询合并**：同 request 内相同 ID 集合不重复 hit DB（Loader 级缓存 `Map`，非跨请求缓存）

现有 §2.1 第 3 条「按需 uplift」= **只 uplift L1 hydrate**，不 uplift L2 DTO 装配。

### 2.2 Extension 模型（不变）

- **Track A**：独立页 → `module-*-application`（参考 `module-chs-jilin-application`）
- **Track B**：嵌入核心流 → `health-application` + `Extension.isEnabled()` + `IntegrationSpiAutoConfiguration` NoOp + Maven Profile（`with-chs`、`with-work-injury` 等）
- **不做**：FlowContributor / CapabilityRegistry（v1）；bulk「Adapter」包名

### 2.3 命名约定

#### 应用层读/写（`application/**/support/`）


| 后缀                              | 含义                                          |
| ------------------------------- | ------------------------------------------- |
| Loader                          | 只读，组合 domain QueryService（L2 页面编排）          |
| Assembler                       | 无 I/O，model → DTO                           |
| Enricher                        | 在已有列表上补展示列                                  |
| Orchestrator                    | 写路径跨聚合（现有 `*OrchestrationSupport` 迁移时可保留旧名） |
| Gate                            | 鉴权/校验，失败即抛                                  |
| Provider                        | 选项/配置列表                                     |
| Dto/Row/Spec/Model/Query/Result | 非 Bean 载体                                   |


#### 平台接线（`infrastructure/platform/`）

实现 **zxplat-framework** 或 **zxplat-domain** Port 时，**类名与 Port 对齐**（去掉 `I` 前缀），不用泛称 Adapter：


| Port 类型                            | 实现类后缀                               | 示例                                                                                        |
| ---------------------------------- | ----------------------------------- | ----------------------------------------------------------------------------------------- |
| `I*Bridge`                         | `*Bridge`（优先，少用 `*Adapter`）         | `IOrganizationUnitScopeBridge` → `HealthOrganizationUnitScopeBridge`（现名 `…Adapter` 可迁移时改） |
| `I*Resolver` / `ILogin*Query` 等    | `*Resolver`、`*QueryService`、`*Sync` | `ILoginOrganizationScopeQuery` → `LoginOrganizationScopeQueryService`                     |
| zxplat-domain `*Authorizer` | `*Gate`                             | `FinanceApprovalAuthorizer` → `FinanceApprovalRoleGate`                                   |


**Resolver** 在 platform 层表示「实现框架/领域 Port」；在 application 层表示「解析单一上下文值（无 Port）」——以是否 `implements` Port 区分。

#### 可选模块集成（`module-*-bridge`）


| 后缀             | 含义                                                    |
| -------------- | ----------------------------------------------------- |
| ExtensionImpl  | `implements` zxplat-domain 中 `*Extension` SPI |
| NoOp*Extension | 空实现（已在 infrastructure noop 包）                         |


与 platform **Bridge** 不同：Extension 是可插拔业务能力（CHS/WI/EMR），Bridge 是 zxplat-framework 与 HIS 组织/身份模型的固定接线。

#### 禁止用 Adapter 的场景


| 不要用 `*Adapter`                  | 应改用                                                          |
| ------------------------------- | ------------------------------------------------------------ |
| 读 Loader / Assembler / Enricher | Loader、Assembler、Enricher                                    |
| domain Port → Extension 委托      | Port 名去 `I`（如 `EmrAdmissionConditionResolver`），或 `*Resolver` |
| module-bridge 集成                | `*ExtensionImpl`                                             |
| Observability 切面                | `*ObservabilityAspect` / `*MetricsAspect`                    |


**反例（迁移时改名）**：`MrmsEmrAdmissionConditionResolverAdapter` → `EmrAdmissionConditionResolver`（implements domain `AdmissionConditionResolver`，委托 `EmrAdmissionConditionExtension`）。

#### 遗留 `*Support`

现有 ~72 个 `*Support`：**迁移时保留类名**；**新代码**用上表精确后缀。

包名映射：`com.zxplat.domain.module.*` →  
`com.zxplat.domain.application.shared.{topic}.*` /  
`com.zxplat.domain.application.{domain}.{feature}.support.*` /  
`com.zxplat.domain.infrastructure.platform.*`  

（**无** `application.… .orchestration.*`；原 orchestration 包整体取消。）

### 2.4 whale-framework 与 platform 边界（是否增强 framework？）

**结论：合理，但只增强「SPI + 通用机制」；HIS 实现仍放 `zxplat-domain/platform`，不并进 framework。**

依赖方向：`whale-framework` **不得**依赖 `zxplat-domain` / `zxplat-domain`（framework 已被 zxplat-framework、whale-permission 等多产品共用）。


| 能力                | 现况                                                         | 建议放哪                                                                               | 是否增强 whale-framework                                                     |
| ----------------- | ---------------------------------------------------------- | ---------------------------------------------------------------------------------- | ------------------------------------------------------------------------ |
| 登录选医院/科室          | `ILoginOrganizationScopeQuery`（zxplat-framework SPI）             | `LoginOrganizationScopeQueryService` → **platform/identity**（读 `adm_organization`） | SPI 已在 framework；**实现不进 framework**                                      |
| HIS OU ↔ ZXPlat OU | `IOrganizationUnitScopeBridge`（zxplat-framework SPI）             | `HealthOrganizationUnitScopeAdapter` → **platform/organization**                   | 同上                                                                       |
| 登录前刷新用户科室         | `ILoginUserOrganizationUnitSync`（zxplat-framework SPI）           | `HealthLoginUserOrganizationUnitSync` → **platform/identity**                      | 可在 **zxplat-framework** 强化调用时机/文档；sync 逻辑依赖 `adm_pr_assign` → health       |
| 菜单/API 权限         | `@RequirePermission` + `PermissionAspect`                  | 已在 **zxplat-framework**                                                                  | **已具备**，无需重复                                                             |
| 财务审批等业务 Gate      | `FinanceApprovalAuthorizer`（**zxplat-domain** Port） | `FinanceApprovalRoleGate` → **platform/authorization**                             | Port 属 HIS 业务；framework 只提供 `UserAppService` / role 查询                   |
| Integration 调用耗时  | `Integration*ObservabilityAspect`                          | pointcut 针对 `health.infrastructure.integration`                                    | **合理增强 zxplat-framework**：通用 `MethodMetricsAspect` + 可配置 pointcut；health 只配表达式 |


**增强 framework 的合适项（v1 可选，非消解阻塞）**

1. **zxplat-framework**：可配置 Micrometer 切面（替代硬编码 `com.zxplat.domain...*Adapter`），与现有 `AppServiceAspect` / `PermissionAspect` 同级。
2. **zxplat-framework**：文档化 SPI 生命周期（何时调 `ILoginUserOrganizationUnitSync`）；不迁入 HIS 表逻辑。
3. **不新增** `zxplat-bridge` 模块把 platform 实现塞进 framework——会把 framework 绑死在 OpenHIS 组织模型上。

**platform 层的职责一句话**：实现 zxplat-framework / zxplat-domain 上已定义的 Port，是 **产品适配层**，不是第二套 framework。

### 2.5 载体类型（Query / Dto / Result / Command）放哪

module-support 内除 Bean 外还有大量 **非 Spring 载体**，迁移时与 Loader 同批处理，避免只迁类、漏改 465 个 consumer。


| 类型         | 后缀示例                                       | 目标包                               | 说明                                              |
| ---------- | ------------------------------------------ | --------------------------------- | ----------------------------------------------- |
| API 入参     | `*Request`（继承 `AbstractHealthPageRequest`） | 留在 `{feature}/request/`           | 只改 `import`，不挪文件（除非 request 本身在 module 内）       |
| API 出参     | `*Response`                                | 留在 `{feature}/response/`          | 同上                                              |
| Loader 中间态 | `*Query`、`*Result`、`*Row`                  | `{feature}/dto/`                  | 与 Loader **同 feature**                          |
| 跨页产品展示     | `ProductDefinitionDisplayDto` 等            | `application/shared/product/dto/` | **155+ 引用**，S2 必迁                               |
| 分页基础设施     | `PagedResult`、`PagedResultBuilder`         | `application/shared/pagination/`  | 与 `PageQueryBinder` 同批                          |
| 写命令        | `*Command`、`*SaveContext`                  | 与 Orchestrator **同目录** `support/` | 如 `InpatientAutoRollServiceOrderExecuteSpec` |


**规则**：`querysupport/*/dto/*`、`common/dto/*` 迁入对应 feature 的 `dto/` 或 `shared/{topic}/dto/`，不保留 module 内 `dto` 子包路径。

### 2.6 存量 `assembler/` 与终态 `support/`（不批量重命名）

`health-application` 部分 Feature 已用 `assembler/`（如 `doctorstation/advice/assembler/`），module 迁入目标为 `support/`。


| 策略                | 说明                                                |
| ----------------- | ------------------------------------------------- |
| **module 迁入**     | 一律进 `{feature}/support/`（终态）                      |
| **存量 assembler/** | **迁移期保留**，不为此做全库 rename PR                        |
| **新代码**           | 只写 `support/`；触达旧 `assembler/` 时可顺带合并进 `support/` |
| **禁止**            | 同一 Feature 下向 `assembler/` **新增** module 迁入类      |


## 3. 当前 inventory（246 main + 1 test）


| 顶包               | 类数  | 消解动作                                                                              |
| ---------------- | --- | --------------------------------------------------------------------------------- |
| `querysupport/`  | 111 | 按域迁入 `application/{域}/support/` 或 `**application/report/{feature}/support/`**（§4） |
| `common/`        | 101 | `shared/` + 各域 `support/`                                                         |
| `shared/`        | 18  | platform / shared / **config 删除**                                                 |
| `orchestration/` | 16  | 并入各 Feature 的 `support/` 或 `shared/{topic}/`（**不保留 orchestration 包**，见 §3.4）      |


### 3.1 querysupport 按域（迁移 + 合并）

> 消解 **≠** 111 个类原样平移。迁移时按 §2.1 分层：**能升 L1 的合并 hydrate，页面入口保持独立**。


| 子包                 | 类数    | L2 目标（页面）                                                                                       | L1 合并方向（domain）                                                                           |
| ------------------ | ----- | ----------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------- |
| pharmacy           | 21    | `application/*/pharmacy/support/`                                                               | `MedicationDispenseQueryService` 保持 slip 分页；`ProductDefinitionBatchLoader` → domain batch |
| nursestation       | 22    | 按 Feature 拆分（如 `inpatient/nursestation/orderexecute/support/`），**无** `nursestation/support/` 大桶 |                                                                                           |
| financial          | 14    | `application/*/financial/support/`                                                              | 结算 charge/payment 批量读留在 `ChargeItemQueryService` / `PaymentReconciliationQueryService`    |
| encounter          | 8     | `application/shared/encounter/` 或各页 support                                                     | **重点合并区**，见下表                                                                             |
| product            | 9     | `application/shared/product/` + `**shared/product/dto/`**                                       | `**ProductDefinitionQueryService.batchLoadByKeys**`（ uplift BatchLoader，71 引用）            |
| priceadjustment    | 8     | `application/finance/priceadjustment/support/`                                                  | 审批定价快照若双页复用 → L1 orchestration 留 application                                              |
| advice             | 7     | `application/shared/advice/`（OP/IP 医生站、护士站共用）                                                   | 目录/列表 ID 批量已在 domain；展示 enrich 留 L2                                                       |
| order              | 5     | doctorstation support                                                                           | 同 advice                                                                                  |
| administration     | 4     | administration support                                                                          | 单聚合为主，少 uplift                                                                            |
| activitydefinition | 3     | clinical support                                                                                | 套餐子项解析可 L1                                                                                |
| inventory          | 2     | inventory support                                                                               | 低库存过滤谓词留 L0 `InventoryItemQueryService`                                                   |
| ordertemplate      | 2     | ordertemplate support                                                                           | 复用 product batch                                                                          |
| procedure          | 1     | nursestation / clinical                                                                         | —                                                                                         |
| feedetail          | 1     | financial support                                                                               | —                                                                                         |
| **report**         | **4** | `**application/report/{feature}/support/`**（见 §4）                                               | domain report 专用 Query API **保留**                                                         |


#### 3.1.1 建议合并清单（消解过程中做，不单独开「重构大战」）


| 现状重复                                                                                                    | 动作                                                               | 性能收益                          |
| ------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------- | ----------------------------- |
| `EncounterContextQueryService`（domain）与 `Outpatient/InpatientEncounterContextLoader` 单条/批量 hydrate 逻辑重复 | Loader **委托** domain；删除 Loader 内重复 `toXxxContextModels`          | bridge 与 app 同路径；减少双倍维护       |
| `EncounterContextReader` 10+ 个 `page*Queue`                                                             | **保留** 多个 page 入口；抽取 private `hydrateInpatientPage(ids)` 共用      | 各队列 SQL 谓词独立，hydrate 只查当页     |
| `ProductDefinitionBatchLoader`（50+ 引用）                                                                  | uplift → `ProductDefinitionQueryService.batchLoadByKeys`         | 一次 batch 四表路由，消除多处 copy       |
| `PharmacyDispenseSlipQuerySupport` + `EncounterMedicationDispenseSlipPageLoader`                        | Loader 只编排；slip 行查询统一进 `MedicationDispenseQueryService.pageSlip` | 分页在 dispense 表，避免先拉全量 request |
| 门诊/住院 `ReturnMedicineDetailLoader`                                                                      | 若 hydrate 相同 → L1 合并；**page 入口仍分**                               | 按 return 单 ID 批量，不合并队列        |
| `OrganizationDisplayResolver` / `DiagnosisSnapshotDisplayResolver`                                      | 留 L2 shared；**不**进 domain（~~DiagnosisSnapshotDisplayResolver~~ 已并入 `AdmissionPrimaryDiagnosisAssembler`） | 纯展示，无 DB 或仅字典                 |


#### 3.1.2 明确不合并


| 类/场景                                                                | 原因                                                                     |
| ------------------------------------------------------------------- | ---------------------------------------------------------------------- |
| `EncounterMedicationDispenseSlipPageLoader` 门诊 vs 住院                | 定价/库存/charge 关联路径不同，硬合并会多 join 或 over-fetch                            |
| `InpatientSettleReadLoader` 多 tab（charges / summary / payment view） | 各 tab 投影字段差大；共用 L1 `loadSettleContext(encounterId)`，**分** L2 Assembler |
| `NurseStationOrderExecuteListLoader` vs `OrderViewListAssembler`    | 列表谓词（执行态/医嘱类型）不同；共享 batch 产品定义即可                                       |
| 任何「一个大 `query(PageQuery q)` 包打天下」                                   | 难优化索引；改一屏影响全局                                                          |


#### 3.1.3 Pharmacy 试点（S3）合并验收

- slip 列表：DB 分页 → 当页 ID → batch product/inventory/charge（≤3 轮 batch）
- 无 foreach 内单条 QueryService 调用（ArchUnit 或 SpotBugs 可选）
- `EncounterContextReader.get*ByEncounterId` 与 bridge 的 `EncounterContextQueryService` 行为一致
- 压测：门诊发药队列 p95 不比消解前劣化（**S3 前 baseline**，见 §5.0.1）

### 3.2 common 按职责


| 子包                    | 类数  | 目标                                                              |
| --------------------- | --- | --------------------------------------------------------------- |
| validator             | 23  | 见下表 **validator 分流**                                            |
| assembler/product     | 12  | `application/shared/product/` + `shared/product/dto/`           |
| assembler/advice      | 15  | `application/shared/advice/`                                    |
| assembler/tracenumber | 11  | `application/shared/tracenumber/`（库存/发药写路径共用，非报表）               |
| assembler/inpatient   | 6   | `application/shared/inpatient/` 或各 inpatient Feature `support/` |
| assembler/option      | 5   | `application/shared/option/`                                    |
| assembler/financial   | 4   | `application/shared/financial/` 或各 financial Feature            |
| pagination            | 3   | `application/shared/pagination/`                                |
| support（payment）      | 3   | `application/shared/payment/`（Extension 胶水）                     |
| dto/product           | 2   | `application/shared/product/dto/`                               |
| dto/nursestation      | 2   | 随 §3.6 护士站 Feature                                              |
| 其余 assembler          | 若干  | 跟随消费 AppService 所在域                                             |


#### 3.2.1 validator 分流（23 类，唯一目标）


| 类                                                                   | 目标包                                                                   |
| ------------------------------------------------------------------- | --------------------------------------------------------------------- |
| `FinanceApprovalRoleGate`                                           | `infrastructure/platform/authorization/`                              |
| `WhaleRoleLookup`                                                   | **S1/S2b**：改调 `UserAppService`，删 Repository 直连；迁至 `shared/validator/` |
| `InventoryWarningRoleGate`                                          | `application/shared/validator/`                                       |
| `PaymentChannelGate`                                                | `application/shared/validator/`                                       |
| `StaffManagedScopeWriteGate`                                        | `application/shared/validator/`                                       |
| `PatientEncounterConsistencyGate`                                   | `application/shared/validator/`                                       |
| `PageWindowGate`                                                    | `application/shared/validator/`                                       |
| `InpatientNursingOrderGate`                                  | `application/shared/advice/`                                          |
| `InpatientAdviceRevokeGate`                                  | `application/shared/advice/`                                          |
| `CurrentPractitionerResolver`                                       | `application/shared/validator/`                                       |
| `CurrentManagedOrganizationIdsResolver`                             | `application/shared/validator/`                                       |
| `CurrentManagedLocationIdsResolver`                                 | `application/shared/validator/`                                       |
| `CurrentManagedWardLocationIdsResolver`                             | `application/shared/validator/`                                       |
| `SystemOperatorResolver`                                            | `application/shared/validator/`                                       |
| `PrintOperatorResolver`                                             | `application/shared/validator/`                                       |
| `MrmsOperatorResolver`                                              | `application/shared/mrms/`                                            |
| `OutpatientPrescriptionAmountLimitSettingResolver`                   | `application/outpatient/doctorstation/advice/support/`                |
| `InventoryWarningSettingResolver`                                    | `application/inventory/inventorydetail/support/`                      |
| `DeviceAutoInventoryDeductSettingResolver`                           | `application/inventory/inventorydetail/support/`                      |
| `CatalogEditabilityResolver` + `CatalogEditabilityQueryModel` | `application/shared/catalog/`                                         |
| `PositionPractitionerRoleWriteGate`                                 | `application/basic/position/support/`                                 |
| `OrderTemplateScopeResolver`                                        | `application/basic/ordertemplate/support/`                            |


### 3.3 shared → platform / shared


| 子包               | 类                                       | 目标                                        |
| ---------------- | --------------------------------------- | ----------------------------------------- |
| identity（6）      | 见下表                                     | `infrastructure/platform/identity/`       |
| organization（4）  | 见下表                                     | `infrastructure/platform/organization/`   |
| diagnosis（5）     | `EncounterDiagnosisSnapshotAssembler` 等 | `application/shared/diagnosis/`           |
| observability（2） | `Integration*ObservabilityAspect`       | 删除或 `infrastructure/observability/`（§4.6） |
| config（1）        | `HealthModuleSharedAutoConfiguration`   | **删除**（S6）                                |


#### 3.3.1 platform/identity（6 类）


| 类                                        | 实现 Port                                                                                                   |
| ---------------------------------------- | --------------------------------------------------------------------------------------------------------- |
| `LoginOrganizationScopeQueryService`     | `ILoginOrganizationScopeQuery`                                                                            |
| `HealthLoginUserOrganizationUnitSync`    | `ILoginUserOrganizationUnitSync`                                                                          |
| `UserLoginOrganizationSyncService`       | （内部编排，随 sync 同包）                                                                                          |
| `LoginDefaultOrganizationResolver`       | —                                                                                                         |
| `HealthPractitionerOrganizationResolver` | —                                                                                                         |
| `HealthPractitionerManagedScopeResolver` | —（**暂留** `module.shared.identity`，依赖 `PractitionerManagedScopeLoader`，S5 随 administration 迁入后再迁 platform） |


#### 3.3.2 platform/organization（3 类）


| 类                                    | 说明                                               |
| ------------------------------------ | ------------------------------------------------ |
| `HealthOrganizationUnitScopeAdapter` | → 重命名 `HealthOrganizationUnitScopeBridge`（可选 S1） |
| `OrganizationUnitLinkSyncService`    | OU 链接同步                                          |
| `OrganizationUnitLinkCode`           | 常量，同包                                            |


#### 3.3.3 application/shared/organization（1 类）


| 类                             | 说明                                 |
| ----------------------------- | ---------------------------------- |
| `OrganizationDisplayResolver` | 展示解析（无 whale Port；**不**进 platform） |


#### 3.3.4 application/shared/diagnosis（5 类）

`EncounterDiagnosisSnapshotAssembler`、`DiagnosisSnapshotDisplayResolver`、`DiagnosisJsonSnapshotParser`、`PatientHistoryCommonDiagnosisAssembler`、`PatientHistoryCommonDiagnosisRow`

### 3.4 orchestration（16）→ 写入 `support/`，不保留 `orchestration/` 包

原 `module.orchestration.`* 均为**写路径跨聚合**；迁移后与 Loader **同一目录规则**，类名可保留 `*OrchestrationSupport` 或逐步改 `*Orchestrator`。


| 原类                                                    | 目标包（唯一）                                                        |
| ----------------------------------------------------- | -------------------------------------------------------------- |
| `ServiceApplyFormOrchestrator`                | `application/shared/advice/`                                   |
| `OutpatientMedicationSignChargeOrchestrator`               | `application/outpatient/doctorstation/advice/support/`         |
| `DoctorStationSkinTestSyncOrchestrator`       | `application/shared/doctorstation/advice/`                     |
| `AdmissionDiagnosisSubmitGate`           | `application/shared/inpatient-admission/`                      |
| `EncounterInpatientAdmissionOrchestrator`             | `application/shared/inpatient-admission/`                      |
| `InpatientTransferDischargeHoldOrchestrator`  | `application/shared/inpatient-transfer/`                       |
| `OutpatientChargeSettlementGateSupport`               | `application/outpatient/financial/charge/support/`             |
| `OutpatientPaymentDraftLifecycleOrchestrator`              | `application/outpatient/financial/charge/support/`             |
| `InpatientAutoRollOrderExecutionOrchestrator` | `application/inpatient/nursestation/inpatientbilling/support/` |
| `InpatientAutoRollServiceOrderExecuteSpec`         | 同上（与 Orchestrator 同目录）                                         |
| `AdmissionVitalSignOrchestrator`                           | `application/shared/inpatient-vitals/`                         |
| `AdmissionVitalSignSaveContext`                       | 同上                                                             |
| `DeviceDispenseNurseWorkflowOrchestrator`                  | `application/shared/nursestation-device-dispense/`             |
| `LongTermMedicationMonthlyDispenseCapGate`         | `application/inpatient/nursestation/orderexecute/support/`     |
| `ProcedureExecutionPerformerOrchestrator`     | `application/shared/nursestation-procedure/`                   |
| `HealthConcurrencySupport`                            | `application/shared/concurrency/`                              |


**querysupport 中的写路径（1）**（一并按此规则，不归 orchestration 包）：


| 原类                                           | 目标包                                            |
| -------------------------------------------- | ---------------------------------------------- |
| `PriceAdjustmentApprovePricingOrchestration` | `application/finance/priceadjustment/support/` |


### 3.5 Import 热度（health-application，1621 处）

> S2 必须优先迁 **引用 ≥20** 的类，否则后续每个 Feature PR 都会重复改 import。


| 引用数 | module 类                                                              | 目标                              |
| --- | --------------------------------------------------------------------- | ------------------------------- |
| 155 | `querysupport.product.dto.ProductDefinitionDisplayDto`                | `shared/product/dto/`           |
| 114 | `common.pagination.PageQueryBinder.AbstractHealthPageRequest`         | `shared/pagination/`            |
| 81  | `common.pagination.PagedResultBuilder`                                | `shared/pagination/`            |
| 78  | `common.dto.product.ProductDefinitionCatalogEditorHintsDto`           | `shared/product/dto/`           |
| 71  | `querysupport.product.ProductDefinitionBatchLoader`                   | domain L1 + 删 Loader            |
| 65  | `common.validator.CurrentPractitionerResolver`                        | `shared/validator/`             |
| 62  | `common.pagination.PageQueryBinder`                                   | `shared/pagination/`            |
| 53  | `common.dto.product.ProductDefinitionCatalogUnitBandDto`              | `shared/product/dto/`           |
| 48  | `common.validator.PageWindowGate`                                     | `shared/validator/`             |
| 43  | `common.assembler.product.ProductDefinitionCatalogUnitBandsAssembler` | `shared/product/`               |
| 36  | `common.validator.CurrentManagedOrganizationIdsResolver`              | `shared/validator/`             |
| 33  | `common.validator.CurrentManagedLocationIdsResolver`                  | `shared/validator/`             |
| 32  | `querysupport.encounter.EncounterContextReader`                       | `shared/encounter/`             |
| 31  | `querysupport.administration.PractitionerRoleScopeLoader`             | `shared/practitioner/`          |


**推论**：S2 不是「迁 3 个 pagination 类」，而是 **shared/product + shared/pagination + shared/validator** 整包闭包（约 40 类 + domain Product batch），一次 PR 可消 **~700** 处 import。

### 3.6 Feature 级迁移表（高耦合域）

#### 3.6.1 Pharmacy（21 类 → 按 Feature 拆分，禁止 `pharmacy/support/` 大桶）


| 原类                                                                                                          | 目标包                                                                                          |
| ----------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------- |
| `EncounterMedicationDispenseSlipPageLoader` + ReadAssembler + PrintEnricher + SlipLoaderQuery + slip dto（4） | `outpatient/pharmacy/dispense/support/` + `…/dto/`；住院同结构 → `inpatient/pharmacy/dispense/`    |
| `PharmacyEncounterQueueLoader`                                                                              | OP → `outpatient/pharmacy/dispense/support/`；IP → `inpatient/pharmacy/dispense/support/`     |
| `PharmacyDispenseSlipQuerySupport`                                                                          | 逻辑 uplift L0 后删除或薄封装于 dispense                                                               |
| `PharmacyDispenseRecordLoader` + ReadAssembler + ChargePricingSupport + record dto（2）                 | `outpatient/pharmacy/dispenserecord/` 与 `inpatient/pharmacy/dispenserecord/`                 |
| `ReturnMedicineDetailLoader` / `InpatientReturnMedicineDetailLoader`                                        | `outpatient/pharmacy/returnmedicine/support/` / `inpatient/pharmacy/returnmedicine/support/` |
| `MedicationSummarySlipLoader`                                                                         | `inpatient/pharmacy/summarydispense/support/`                                                |
| `InpatientMedicationDispenseChargePricingLoader`                                                            | `inpatient/pharmacy/dispense/support/`                                                       |
| `MedicationDispenseRefundSupersedeLoader`                                                                   | 消费方所在 returnmedicine Feature                                                                 |
| `MedicationDispenseInventoryAvailabilityGate`                                                            | `shared/pharmacy/` 或 dispense support（仅 1 消费者则进 Feature）                                     |
| `MedicationDefinitionExternalReferenceGuard`                                                                | `shared/catalog/`                                                                            |


#### 3.6.2 Nursestation（22 querysupport + 6 common/orchestration → 按菜单 Feature）


| 原类                                                                                            | 目标包                                                       |
| --------------------------------------------------------------------------------------------- | --------------------------------------------------------- |
| `NurseStationOrderExecuteListLoader` + ReadAssembler + Query + Result + execute dto（6）        | `inpatient/nursestation/orderexecute/support/` + `…/dto/` |
| `NurseStationOrderViewListAssembler` + order view dto（4）                                      | `inpatient/nursestation/orderaudit/support/`              |
| `NurseStationCostDetailReadAssembler` + cost dto（3） + `InpatientNurseStationCostDetailLoader` | `inpatient/nursestation/inpatientbilling/support/`        |
| `InpatientAutoRollEncounterContextLoader`                                                     | `inpatient/nursestation/inpatientbilling/support/`        |
| `InpatientNurseStationTcmOrchestrator`                                                             | `inpatient/nursestation/orderexecute/support/`            |
| `NurseStationEncounterVisibilityResolver`                                                      | `shared/nursestation/` 或 orderexecute（按消费者定）              |
| `NurseStationSkinTestExecuteExcludeHelper`                                                   | `outpatient/nursestation/skintest/support/`               |
| `NurseStationOutpatientTcmExecuteExcludeHelper`                                              | `outpatient/nursestation/skintest/support/`               |
| `TemperatureSheetDetailItemHelper` + dto                                                     | `inpatient/nursestation/temperaturesheet/support/`        |
| `AdmissionVitalSignOrchestrator` + SaveContext + dto                                               | `shared/inpatient-vitals/`（§3.4）                          |
| `DeviceDispenseNurseWorkflowOrchestrator`                                                          | `shared/nursestation-device-dispense/`                    |
| `LongTermMedicationMonthlyDispenseCapGate`                                                 | `inpatient/nursestation/orderexecute/support/`            |
| `ProcedureExecutionPerformerOrchestrator`                                             | `shared/nursestation-procedure/`                          |
| AutoRoll Orchestrator + Command                                                               | `inpatient/nursestation/inpatientbilling/support/`        |


#### 3.6.3 Financial（14 类）


| 原类                                                                                 | 目标包                                                       |
| ---------------------------------------------------------------------------------- | --------------------------------------------------------- |
| `OutpatientChargeEncounterQueueLoader`                                             | `outpatient/financial/charge/support/`                    |
| `OutpatientChargeSettlementGateSupport` + `OutpatientPaymentDraftLifecycleOrchestrator` | `outpatient/financial/charge/support/`（§3.4）              |
| `InpatientSettleReadLoader` + ReadAssembler + 各 tab Query/Result + settle dto（3）   | `inpatient/financial/settle/support/` + `…/dto/`          |
| `InpatientReverseSettlePreOrchestrator`                                                 | `inpatient/financial/settle/support/`                     |
| `DailySettlementFinTypeAmountLoader`                                              | `application/shared/financial/`（OP/IP dailysettlement 共用） |
| `ThirdPartyPaymentSettleOrchestrator` + RefundSupport                                   | `application/shared/payment/`                             |


#### 3.6.4 Encounter（8 类）


| 原类                                                                     | 目标包                                                |
| ---------------------------------------------------------------------- | -------------------------------------------------- |
| `EncounterContextReader`                                               | `application/shared/encounter/`                    |
| `OutpatientEncounterContextLoader` + `InpatientEncounterContextLoader` | S2b 变薄后 `shared/encounter/` 或删除                    |
| `EncounterContextPageQuerySupport`                                     | `shared/encounter/`                                |
| `ClinicRegistrationLoader`                                             | `outpatient/financial/registration/support/`       |
| `InpatientDoctorStationEncounterVisibilityResolver`                     | `inpatient/doctorstation/encountermanage/support/` |
| `InpatientPatientQueueNursingLevelEnrichAssembler` + dto               | `inpatient/nursestation/ATDManager/support/`       |


### 3.7 S3 Pharmacy 试点 = 依赖闭包（非 21 文件平移）

S3 单次 PR 应包含：

```
shared/pagination/          PageQueryBinder, PagedResultBuilder, PagedResult（若 S2 未完成则并入）
shared/product/               BatchLoader 的 L2 残留 + CatalogUnitBandsAssembler + ClinicalCatalog* 
shared/product/dto/           ProductDefinitionDisplayDto, CatalogEditorHintsDto, UnitBandDto
shared/option/                TerminologyOptionLoader, FrequencyOptionLoader
shared/encounter/             EncounterContextReader（或 S2b 已完成）
§3.6.1 全部 pharmacy 类
domain                      ProductDefinitionQueryService.batchLoadByKeys（若 S2 未完成则并入）
```

**触达 consumer 预估**：pharmacy AppService/Mapper ~15 文件 + inventory/product 相关 request ~100+ 文件（若 S2 已做则仅 pharmacy 域）。

---

## 4. Report 保留 — support 迁入 application/report

> **口径（2026-07-18）**：  
> - **无**独立 `module-report` / 独立 `docs/report/*` 模块文档树；勿再引用 `report/04`、`report/05` 等旧路径。  
> - **勿**再用 A/B/C/D「单证分类」描述临床检验/检查报告查看；该能力在 **health**（`DM-CLN-006`、医生站报告查询、外系统报告页、**AS-BAS-PRT** 打印）。  
> - 下文 `application/report/` 仅表示 health 内**跨域统计报表**包名（工作量/追溯码等），与临床报告查看不是同一概念。

**决策（2026-07-12 修正）**：**不删除** `health-application/application/report/`**（59 文件，含 practitionerworkload / nurseworkload / tracenumber）。独立 Maven 模块 `module-report` 仍不建设；报表 AppService 继续留在 `application/report/{feature}/`，与 AS-RPT-001 / AS-RPT-002 / AS-CUS-TRC-002 一致。

**消解动作**：仅将 `health-module-support` 中 report 专用 support **迁入**对应 feature，并随 S2 将其余 `module.`* 依赖（pagination、product、validator 等）改指向 `application/shared/`。

### 4.1 本模块 → application/report（4 类，git mv + 改 package）


| 原类                                                                 | 目标包                                                |
| ------------------------------------------------------------------ | -------------------------------------------------- |
| `querysupport/report/PractitionerWorkloadLoader`             | `application/report/practitionerworkload/support/` |
| `querysupport/report/PractitionerWorkloadCostEnricher`    | 同上                                                 |
| `querysupport/report/PractitionerWorkloadFinTypeEnricher` | 同上                                                 |
| `querysupport/report/NurseWorkloadLoader`                    | `application/report/nurseworkload/support/`        |


`tracenumber` 的 Loader/Enrichment 已在 `application/report/tracenumber/support/`，**无需从 support 再迁**。

### 4.2 health-application（59 文件 — **保留**）

```
application/report/practitionerworkload/**   ← 保留 AppService / dto / mapper / assembler
application/report/nurseworkload/**          ← 保留；与 §4.1 NurseWorkloadLoader 合并 support/
application/report/tracenumber/**              ← 保留；统计只读，非发药/库存追溯写路径
```

**包形态例外**：`application/report/` 为跨域只读统计垂直（非 `{domain}/{feature}`），**允许存量保留**；新增报表 feature 仍落 `application/report/{feature}/`，禁止回退到 `module.querysupport.report`。

### 4.3 zxplat-domain / domain-shared（**保留** report 专用 API）

下列方法为 report AppService 直接消费，**消解过程中不删**：

- `ChargeItemQueryService`：全部 `*PractitionerWorkload*`
- `EncounterQueryService`：`listEncounterIdsForPractitionerWorkloadScope`、`mapPractitionerWorkloadEncounterContextByIds`、`listEncounterIdsForNurseWorkloadScope`
- `PaymentReconciliationQueryService`：`listPractitionerWorkloadChargeItemIdsByPaymentFilter`
- `ProcedureQueryService` / `DeviceDispenseQueryService`：护士工作量 report 方法
- `TraceNumberQueryService`：`pageTraceNumberReport` + `buildReportSpecification`
- 相关 QueryModel / Constants / ErrorCode
- `ProcedureNetExecutionFilterSupport` — **已在** `zxplat-domain/.../procedure/support/`，**不迁**；护士工作量 report 经 `ProcedureQueryService` 消费

### 4.4 前端（**保留**）

`openhis-pro-ui`：`views/report/`**、`api/report/**`、`workbench-workload-report-section.vue` — 与 backend report 同步维护，**不在消解中删除**。

### 4.5 注释误导 — 这些 **不能** 因 report 迁移而删

Javadoc 写「供 report-adapter 共用」，但**已被核心 AppService 引用**：

- `EncounterMedicationDispenseSlipPageLoader` → 门诊/住院发药
- `NurseStationOrderExecuteListLoader` → 医嘱执行
- `NurseStationCostDetailReadAssembler` → 护士站费用明细
- `InpatientSettleReadLoader` → 住院结算

### 4.6 observability（2026-07-12 确认：S6 删除）

`IntegrationAdapterObservabilityAspect` / `IntegrationPortObservabilityAspect` 靠 `HealthModuleSharedAutoConfiguration` 扫描注册，无 Java 直接引用。


| 项   | 说明                                                                             |
| --- | ------------------------------------------------------------------------------ |
| 切点  | `health.infrastructure.integration..*Adapter.*(..)`                            |
| 实码  | integration 包 **0** 个 `*Adapter`，约 **172** 个 `*Extension` → **切面从未生效**         |
| 决策  | **S6 随模块删除**；若未来需要集成监控，在 zxplat-framework 做可配置 MetricsAspect，切点改为 `*Extension`（§2.4） |


---

## 5. 执行步骤

```
S0    冻结                  禁止向 com.zxplat.domain.module 新增类
S-R   Report support 迁入   §4.1：4 类 git mv → application/report/{feature}/support/；同步 AS-RPT SDD
S1    Platform 迁出         shared/identity、organization、authorization（~11）；HealthPlatformAutoConfiguration
      └ WhaleRoleLookup     改调 UserAppService，禁止 Repository 直连
S2a   shared/pagination/    PageQueryBinder、PagedResultBuilder、PagedResult（~257 import）
      └ 过渡副本            module.common.pagination 保留 @Deprecated 副本 3 类（2 个 Pharmacy 消费者），S3 删
S2b   shared/validator/     Gate、Resolver 等 + WhaleRoleLookup（~200 import）
      └ WhaleRoleLookup       改调 UserAppService；infrastructure 保留 @Deprecated 副本至 S5
      └ 过渡副本              module.common.validator 保留 5 类 @Deprecated 副本（querysupport 消费者）
S2c   shared/product/       product + product/dto（~350 import）
      └ product/dto         4 个 DTO 落 `domain/share/product/dto/`（application↔module-support 边界共享）
      └ 过渡副本            module 内 19 类 @Deprecated 服务副本（querysupport/assembler 消费者），S5 删
S2d   shared/option/        TerminologyOptionLoader 等 5 类（~50 import）
      └ 过渡副本            module.common.assembler.option 保留 3 类 @Deprecated（Terminology/Frequency/OptionDto），S5 删
S2-L1a Product L1 uplift    ProductDefinitionQueryService.batchLoadByKeys（**独立 PR**，在 S2c 之后）
S2-L1b Encounter hydrate    EncounterContextReader 委托 EncounterContextQueryService（**独立 PR**）
S3    Pharmacy 试点         §3.6.1 + §3.7 闭包；**S3 前必须先录发药队列 baseline**
S4    Nursestation          §3.6.2 + §3.4 写路径
S5    其余域                financial、inventory、encounter、advice、order… + insurance/workinjury
S6    删模块                含 observability 2 类、HealthModuleSharedAutoConfiguration、POM 依赖
S7    守卫                  Enforcer；ArchUnit；booking-starter 多余依赖
```

每步：**移动类 → 改 package/import → 同步触达 AS/SDD → CI 绿 → 一步一 PR**。

**硬约束**：git mv 与 L1 行为改动 **不得同 PR**（§5.0）；S2a～S2d 必须 **S2 先于 S3**。

### 5.0 单 PR 操作清单（机械步骤）

1. **git mv** 类到目标包（保留 history）
2. 改 `package` 声明
3. 全仓库 `import` 替换（IDE Structured Replace；优先改 module-support 内部互引再改 application）
4. 若迁 platform：在 `zxplat-domain` 新增 `platform/config/HealthPlatformAutoConfiguration.java`（`@ComponentScan("com.zxplat.domain.infrastructure.platform")`）
5. `mvn -pl zxplat/health-application,zxplat/zxplat-domain,zxplat/health-app -am test`
6. PR 描述附 **import 消减数**（`rg -c 'import com.zxplat.domain.module' health-application | wc -l`）
7. **同步更新**本 PR 触达的 AS/SDD 中 `module.*` 路径（成码期以本文件为准，文档不得长期滞后）

**禁止**：同一 PR 既迁类又做 L1 行为重构（拆成「纯移动 PR」+「行为 PR」）。

### 5.0.1 S3 baseline（开工前必做）

S3 Pharmacy 验收要求 **p95 不劣于 baseline**。在第一次改 pharmacy 代码前：

1. 选定接口：门诊发药 slip 列表（`EncounterMedicationDispenseSlipPageLoader` 对应 AppService 入口）
2. 记录：响应时间 p50/p95、单次请求 domain QueryService 调用次数（或 SQL 轮次）
3. 写入 PR 描述或 `health-application` test 注释，作为 S3 回归对照

**默认**：无自动化 harness 时，至少保留可重复的手工步骤文档。

### 5.1 S6 删除清单

- `health-module-support/` 整个模块
- `zxplat/pom.xml` 中 `<module>health-module-support</module>`
- `health-application/pom.xml`、`health-starter/pom.xml` 依赖
- `whale-dependencies/pom.xml` BOM 条目（`health-module-support`）
- `module-booking-starter/pom.xml` 中对 support 的**空依赖**（无 Java import，S6 删依赖即可）
- `module-emr/pom.xml` parent `dependencyManagement` 中的 support 条目（若有）
- `HealthModuleSharedAutoConfiguration` 及 `META-INF/spring` 注册
- `IntegrationAdapterObservabilityAspect`、`IntegrationPortObservabilityAspect`（§4.6，切点无效）
- `**health-app/.../HealthApplication.java`**：删除 `scanBasePackages` 中的 `com.zxplat.domain.module`（方案原仅写 starter，**遗漏此处**）
- `**health-starter/.../HealthApplicationConfigurationAppService`**：`FinanceApprovalRoleGate` import 改指向 `infrastructure/platform`（S1 完成后再删 support）

### 5.2 S1 之后 / S6 之后 Spring 扫描

**S1 完成后**新增（与 `HealthApplication` 已有 `com.zxplat.domain.infrastructure` 扫描并存）：

- `zxplat-domain/platform/config/HealthPlatformAutoConfiguration` → 扫描 `com.zxplat.domain.infrastructure.platform`

**S6 完成后** `health-app` 的 `HealthApplication` 扫描包：

- `com.zxplat.domain.application`
- `com.zxplat.domain.infrastructure`（含 platform、integration、noop）

**删除** `scanBasePackages` 中的 `com.zxplat.domain.module`。

`health-starter` 不单独扫包；`HealthApplicationConfigurationAppService` 仅改 `FinanceApprovalRoleGate` import 指向 platform。

### 5.3 S7 Enforcer 建议


| 模块                   | 规则                                              |
| -------------------- | ----------------------------------------------- |
| `module-*-bridge`    | 禁止 `health-module-support`、`health-application` |
| `health-application` | 禁止 `module-*-bridge`                            |
| `zxplat-domain`      | 禁止 `health-application`、`health-module-support` |


### 5.4 ArchUnit

`ModuleSupportArchitectureTest`（querysupport/shared 禁 Repository）在迁移完成后：

- 删除，或
- 改写为对 `application..support` + `application.shared..` + `infrastructure.platform..` 的同等规则，放在 `health-application` / `zxplat-domain` 的 test 中

**新增规则（S7）**：

- `application..support` 与 `application.shared..` 禁止依赖 `..repository..`、`jakarta.persistence..`
- `infrastructure.platform..` 允许依赖 `zxplat-domain` Repository（platform 是唯一例外）
- `common.validator.WhaleRoleLookup` 模式禁止再现

### 5.5 Maven 依赖终态

```
health-app
  └─ health-starter
       ├─ health-application  （不再依赖 health-module-support）
       ├─ zxplat-domain
       └─ zxplat-domain
DELETE: health-module-support
```

`health-application/pom.xml` 在 **S6** 移除对 support 的依赖；S1～S5 迁移期 **保留**该依赖，避免半迁移态编译失败。

---

## 6. Bridge 读路径（L1 共用，L2 不共用）

Bridge 只依赖 **L0/L1**（如已有的 `EncounterContextQueryService`）。  
Application 的 L2 Loader **不得** 成为 bridge 依赖；若 bridge 需要与某页面相同事实，在 L1 增加 batch hydrate 方法，而非暴露 DTO Loader。

**合并优先级**：bridge 已用的 L1 > 50+ 引用的 batch（ProductDefinition）> encounter hydrate 去重 > 其余按需。

---

## 7. 关联但未纳入本模块的工作


| 项                                    | 说明                                                                                                                                                                                 |
| ------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **MRMS 已合并进 health**                 | **不改变消解主线**。`domain/mrms` + `application/mrms/{feature}/` 已是标准形态；`MrmsOperatorResolver` → `application/shared/mrms/`；upload 等页用 `PageWindowGate` → `application/shared/validator/` |
| `application/insurance/workinjury/*` | 应迁到未来的 `module-wi-jilin-application`（CHS 同模式）                                                                                                                                      |
| `health-application-core` 拆 Maven 模块 | 可选终态，非 v1 阻塞项                                                                                                                                                                      |
| Report 前端                            | **保留**；与 `application/report` 同步维护                                                                                                                                                 |


---

## 8. 验收标准

- `health-module-support` 从构建树移除，`mvn clean verify` 通过
- 全仓库无 `import com.zxplat.domain.module`（health-application 从 1621 → 0）
- `application/shared/` 已创建并承载 product/pagination/validator 闭包
- 无 `HealthModuleSharedAutoConfiguration`
- Bridge 模块 Enforcer 仍禁止 support
- `querysupport/report/` 已迁入 `application/report/{feature}/support/`；`application/report/`** 与前端 **保留可用**
- 新代码遵守 §2.1 分层与性能硬约束
- §3.1.1 合并项至少完成 encounter hydrate 去重 + ProductDefinition batch uplift（S2-L1a/b）
- S3 前已录 pharmacy baseline，迁移后 p95 不劣化
- `HealthApplication` 启动扫描不含 `com.zxplat.domain.module`
- S5 含 `application/insurance/workinjury` 的 `module.*` import 清零
- **S8**：§12.7 全部满足（shared ≤100 类、0 反向依赖、B 组迁完、C 组有决议）

---

## 9. 方案审查

### 9.0 开工前确认（2026-07-12 三轮，已锁定）


| 决策                          | 结论                                                          |
| --------------------------- | ----------------------------------------------------------- |
| 第一步                         | **S0 冻结** → S-R → S1 → S2a～d → …                            |
| Report                      | **保留** `application/report/`**；S-R 仅迁 4 类 support           |
| S2 PR 切分                    | **4 PR** 纯移动（pagination → validator → product/dto → option） |
| L1 uplift                   | **独立 PR**（S2-L1a Product batch、S2-L1b Encounter hydrate）    |
| OrganizationDisplayResolver | `application/shared/organization/`                          |
| ZXPlatRoleLookup             | S2b 已重构为 UserAppService + 迁至 shared/validator/              |
| Observability 切面            | **S6 删除**（切点无匹配）                                            |
| S3 性能                       | **必须先录 baseline**                                           |
| AS/SDD                      | **每个迁移 PR 同步改**                                             |
| workinjury                  | **S5 纳入** module import 清理                                  |
| finance vs financial        | **不统一 rename**，沿用现有路径                                       |
| 存量 assembler/               | 迁移期保留，不批量 rename（§2.6）                                      |


### 9.1 仍须执行（P0）

> 对照 `health-module-support` + `health-application` import 统计审查。


| 问题                          | 证据                                                                     | 建议                                        |
| --------------------------- | ---------------------------------------------------------------------- | ----------------------------------------- |
| **消费者规模低估**                 | **465** 文件 / **1621** import（非 ~90 AppService）                         | S2a～d 按 §3.5 热度迁 shared；每 PR 报 import 消减  |
| **S6 启动扫描遗漏**               | `HealthApplication.java` 仍 scan `com.zxplat.domain.module`            | §5.1 已列                                   |
| **starter 硬依赖 support 类**   | `HealthApplicationConfigurationAppService` → `FinanceApprovalRoleGate` | S1 platform 后再改 import                    |
| **S3 试点范围**                 | Pharmacy 21 类 + §3.7 闭包                                                | **强制 S2a～d 先于 S3**                        |
| **DTO 泄漏**                  | ~~155 次 module DTO~~ S2c 已迁 `domain/share/product/dto/`                | application 侧 import 已切 domain-shared     |
| **application/shared 收敛**     | S7 后 **125** 类；§12 审计：88 KEEP / 26 MOVE / 11 EVALUATE                  | **S8** 按消费者回迁；非「再扩 shared」              |
| **platform 尚不存在**           | `zxplat-domain/platform/` 0 文件                                 | S1 创建 + `HealthPlatformAutoConfiguration` |
| **门诊+住院 advice**            | `common/assembler/advice` 15 类跨 OP/IP                                  | S5 → `application/shared/advice/`（§3.2）   |
| **ArchUnit 盲区**             | 仅 querysupport/shared；`WhaleRoleLookup` 直连 Repository                  | S2b 重构 + §5.4 扩展                          |


### 9.2 中等风险（P1）


| 问题                                     | 说明                                                   |
| -------------------------------------- | ---------------------------------------------------- |
| **L1 膨胀**                              | Encounter/Slip/Settle Context 按聚合根拆，禁止单类承接全部 hydrate |
| **测试薄弱**                               | 仅 1 个 ArchUnit；S3 补 golden JSON + §5.0.1 baseline    |
| **inventory 大量 request 依赖 module DTO** | 随 S2c product/dto 迁移一次性解决                            |
| **domain Javadoc 残留**                  | Report `@link` 在 S-R 迁入后改指向 `application.report.`*   |


### 9.3 可接受、无需改主线


| 项                               | 理由                                               |
| ------------------------------- | ------------------------------------------------ |
| `health-application` 单 jar 变大   | ~242 类迁入后可接受                                     |
| MRMS 已合并                        | §7                                               |
| NoOp 双注册                        | 删 support AutoConfiguration 后由 infrastructure 独占 |
| 存量 `assembler/` 与 `support/` 并存 | §2.6：迁移期保留，不批量 rename                            |


### 9.4 结论

**S0～S7 已完成**（删 module、建 shared 闭包、Enforcer 落地）。**S8 主线**：§12 消费者审计 → 单消费者迁 feature → topic 规范化 → 解耦反向依赖 → L1 uplift 独立 PR。S7 时 shared 偏大是**消解第一阶段合理妥协**（按 import 闭包批量平移），不是终态设计。

---

## 10. 关联文档同步（2026-07-12）

以下 **docs** 架构规约已与消解终态对齐：


| 文档                                                                           |
| ---------------------------------------------------------------------------- |
| `.cursor/skills/代码生成通用规范/SKILL.md` §2.2.1                                    |
| `.cursor/skills/代码生成通用规范/references/health-support-dissolution.md`           |
| `.cursor/skills/代码生成通用规范/references/modules-all.md`                          |
| `.cursor/skills/代码生成通用规范/references/full-standards.md` B.6                   |
| `.cursor/skills/代码生成通用规范/checklist.md` §K                                    |
| `.cursor/skills/代码生成通用规范/references/anti-patterns.md` §14                    |
| `.cursor/skills/应用层代码生成提示词模板/SKILL.md`                                       |
| `.cursor/skills/领域层代码生成提示词模板/SKILL.md`                                       |
| `.cursor/skills/模块代码规范/SKILL.md`                                             |
| `.cursor/skills/模块代码规范/references/module-bridge-his-read.md` §6              |
| `.cursor/skills/模块代码规范/references/integration-assembler-orchestration.md` §5 |
| `docs/架构/模块化开发规范.md` §7                                              |


**AS/SDD 路径**（§9.0 确认）：**每个迁移 PR 同步更新**触达的 `docs/领域/AppService`* / `AppService-SDD*` 中 `module.*` 引用；S8 PR 同步更新 `shared.*` 路径；未触达文档成码仍以本文件 + `health-support-dissolution.md` 为准。

---

## 12. `application/shared` 收敛优化（S8，2026-07-12）

> **背景**：S0～S7 按 import 闭包将 `health-module-support` 平移到 `application/shared/{topic}/`，优先保证删模块与 CI 绿。S7 后实测 **125** 个 Java 类，其中约 **26** 个仅 1 个（或 0 个）外部消费者——**应回迁到对应页面的 `{domain}/{feature}/support/`**，而非长期留在 shared。  
> **原则**：**默认跟页面走；shared 是例外，不是默认归宿。**

### 12.1 放置决策（S8 权威规则）

```
新增辅助类
  ├─ 消费者 = 1 个页面/Feature
  │     ├─ 简单（单聚合分页 + Mapper）→ 写在 AppService 流程内，不建 support 类
  │     └─ 中等/复杂 → application/{domain}/{feature}/support/
  ├─ 消费者 ≥ 2 且投影相同（无页面专属 UI 列）
  │     ├─ 稳定跨域 DTO / INFRA → application/shared/{topic}/
  │     └─ 可 batch 的事实加载 → 优先 uplift domain L1；shared 只留薄 L2
  └─ 消费者 ≥ 2 但投影不同 → 各 Feature 各留 L2；仅抽公共 batch 到 domain L1
```

| 组 | 条件 | 动作 |
| --- | --- | --- |
| **KEEP** | consumers ≥3，或 cross OP/IP，或 `pagination`/`validator` INFRA | 留 `shared/{topic}/` |
| **MOVE** | consumers 0–1 | 迁 `{domain}/{feature}/support/` |
| **EVALUATE** | consumers 2–3，投影待确认 | 产品/架构评审后 KEEP 或 MOVE |

**与 S0～S7 的关系**：S2「按 topic 闭包纯移动」是**第一阶段**；S8 是**第二阶段精修**，不推翻 §2.1 topic 清单，而是把误落入 shared 的单消费者类迁回 feature。

### 12.2 S7 后审计汇总（125 类）

| 组 | 数量 | 说明 |
| --- | ---: | --- |
| **KEEP** | 88 | 真横切或多消费者共用 |
| **MOVE** | 26 | 15 个 1-consumer + 11 个 0-consumer（shared 内部闭包） |
| **EVALUATE** | 11 | 各 2-consumer，需确认投影是否相同 |

**Top 引用热度**（外部 consumers，须留 shared）：

| 类 | Consumers | Topic |
| --- | ---: | --- |
| `PageQueryBinder` | 178 | `pagination/` |
| `PagedResult` | 99 | `pagination/` |
| `PagedResultBuilder` | 85 | `pagination/` |
| `ProductDefinitionBatchLoader` | 82 | `product/`（→ S2-L1a uplift 后删除） |
| `CurrentPractitionerResolver` | 67 | `validator/` |
| `ProductDefinitionCatalogUnitBandsAssembler` | 51 | `product/` |
| `PageWindowGate` | 48 | `validator/` |
| `EncounterContextReader` | 36 | `encounter/` |

**不在 §2.1 原清单、S8 补充的 topic**：

| Topic | 类 | 理由 |
| --- | --- | --- |
| `converter/` | `TextValueConverter`（9 consumers，X-OP/IP） | 暂留 shared；长期可上提 `zxplat-framework` |
| `pharmacy/` | `MedicationDispenseInventoryAvailabilityGate`（3 consumers，X-OP/IP） | 暂留 shared |

**高引用但包名不在原清单的 `inpatient/`（6 类）**：`InpatientInfoQueueDisplayAssembler` 等 18～4 consumers——S8 **暂留** shared，长期评估是否拆到 `inpatient/nursestation/encountermanage/support/` 或 `inpatient/financial/settle/support/`。

### 12.3 包结构违规（S8-R0 纯重命名）

#### Topic 目录 camelCase → kebab-case

| 当前 | 目标 |
| --- | --- |
| `inpatientadmission/` | `inpatient-admission/` |
| `inpatientvitals/` | `inpatient-vitals/` |
| `inpatienttransfer/` | `inpatient-transfer/` |
| `nursestationdevicedispense/` | `nursestation-device-dispense/` |
| `nursestationprocedure/` | `nursestation-procedure/` |
| `doctorstationadvice/` | 并入 `advice/` 或迁 `outpatient/doctorstation/skintest/support/`（见 §12.5 B 组） |

#### Topic 下禁止嵌套子包（平铺或迁 feature）

| 当前 | 处置 |
| --- | --- |
| `advice/dto/`、`advice/model/` | 随 B 组迁 feature `support/`；KEEP 类平铺进 `advice/` |
| `tracenumber/command/` | 命令类平铺进 `tracenumber/` |
| `nursestation/support/` | `NurseStationEncounterVisibilityResolver` → feature（§12.5 C 组） |
| `nursestationdevicedispense/support/`、`nursestationprocedure/support/` | 去掉 `/support`，类平铺进 topic 根 |

### 12.4 反向依赖（S8 必先解耦，再迁类）

shared **禁止** `import application.{domain}..support..`：

| shared 类 | 当前依赖 feature 类 | S8 解耦方案 |
| --- | --- | --- |
| `EncounterContextReader` | `outpatient.financial.registration.support.ClinicRegistrationLoader` | ① `ClinicRegistrationLoader` 留 registration feature；② shared 通过构造注入 / 小接口 `ClinicRegistrationReadPort`；或 ③ 挂号上下文 uplift domain L1 |
| `DeviceDispenseNurseWorkflowOrchestrator` | `inventory.inventorydetail.support.DeviceAutoInventoryDeductSettingResolver` | 随该类迁 `inpatient/nursestation/devicedispense/support/`，依赖方向自然正确 |
| `InpatientDischargeClinicalGateSupport` | `inpatient.nursestation.orderexecute.support.NurseStationOrderExecuteListReadAssembler` | Gate 迁 `inpatient/nursestation/ATDManager/support/`；读侧改调 domain QueryService 或 L1，不依赖 L2 Assembler |

### 12.5 迁出清单

#### B — MOVE（26 类，随各 feature PR）

**1-consumer（15 类）**

| 类 | Cons | 建议目标包 |
| --- | ---: | --- |
| `AdviceChargeAnchorRollupHelper` | 1 | `outpatient/nursestation/treatment/support/` |
| `AdviceRequestExtensionSnapshotDto` | 1 | `outpatient/doctorstation/advice/support/` |
| `ProcedurePerformerDisplayAssembler` | 1 | `outpatient/nursestation/treatment/support/` |
| `ServiceApplyFormDetailItemSpec` 等 5 个 `advice/model/*` | 1 | `outpatient/doctorstation/advice/support/` |
| `HealthConcurrencySupport` | 1 | `inpatient/nursestation/orderexecute/support/` |
| `PatientHistoryCommonDiagnosisAssembler` | 1 | `outpatient/doctorstation/diagnosis/support/` |
| `EncounterContextPageQuerySupport` | 1 | `outpatient/financial/registration/support/` |
| `PaymentChannelRefundOrchestrator` | 1 | `outpatient/financial/refund/support/` |
| `ChargePricingTypeCodeResolver` | 1 | `inventory/warehouse/inboundreceipt/support/` |
| `DoctorStationAdviceFifoAllocationResult` / `FifoSegmentDraft` | 1 | `inpatient/nursestation/orderexecute/support/` |

**0 外部 consumer — shared 内部闭包（11 类）**

随父类 MOVE 或平铺；`OrganizationDisplayResolver`、`InpatientEncounterContextLoader` 待确认 dead code 后删除。

#### C — EVALUATE（11 类，各 2 consumers）

| 类 | 消费者 | 倾向 |
| --- | --- | --- |
| `AdviceOrderBindQueryLoader` + `AdviceOrderBindRowDto` | IP 医生站 advice + IP 护士站 orderexecute | **KEEP** `shared/advice/`（跨站） |
| `InpatientNursingOrderGate` | 同上 | **KEEP** `shared/advice/`（§3.2.1） |
| `OrderRequestFifoSignContextLoader` | OP 医生站 advice + OP 中医 | **MOVE** → `outpatient/doctorstation/advice/support/` |
| `OrderChargePricingAssembler` | IP billing + orderexecute | **EVALUATE** → 可能 `inpatient/nursestation/inpatientbilling/support/` |
| `OutpatientEncounterContextLoader` | OP charge + registration | **EVALUATE** → 委托 `EncounterContextQueryService`（S2-L1b）后删除 |
| `InpatientTransferDischargeHoldOrchestrator` | IP 医生站 + 护士站 orderaudit | **KEEP** `inpatient-transfer/`（§3.4） |
| `AdmissionVitalSignOrchestrator` + `SaveContext` | IP ATDManager + encountermanage | **KEEP** `inpatient-vitals/`（§3.4） |
| `NurseStationEncounterVisibilityResolver` | IP 护理记录 + 体温单 | **MOVE** → `inpatient/nursestation/nursingrecord/support/` |
| `ProductDefinitionPurchasePageLoader` | 药库 inboundreceipt + purchaseorder | **KEEP** 或 `inventory/warehouse/support/`（同投影） |

#### A — KEEP 要点（88 类）

完整列表见 S8 审计；核心 topic 与 §2.1 一致：`pagination`（3）、`validator`（15）、`product`（18）、`advice`（20）、`option`（5）、`encounter`（2）、`diagnosis`（4）、`financial`（5）、`tracenumber`（6）、`payment`（3）、`inpatient-admission`（2）、`inpatient-vitals`（1 DTO 高引用）、`nursestation-device-dispense`（1）、`nursestation-procedure`（1）、`mrms`（1）、`concurrency`（0 外部→随 MOVE）、`converter`（1）、`pharmacy`（1）、`inpatient/`（6）、`pricing`（1 高引用 `ChargePricingCommandAssembler`）。

### 12.6 S8 分阶段交付（PR 粒度）

| 阶段 | 交付物 | 类型 | 验收 |
| --- | --- | --- | --- |
| **S8-0** | 解耦 §12.4 三处反向依赖 | 行为 / 接口 | `rg shared` 无 `import.*application\.(outpatient\|inpatient\|inventory)\.` | **✅ 2026-07-12** |
| **S8-R0** | kebab-case topic 重命名 + 去掉 topic 下 `/support` 子包 | **纯移动** | compile 绿；包名合规 | **✅ 2026-07-12**：camelCase topic 合并为 `shared/nursestation/`、`shared/inpatient/`；0 嵌套子包（`product/dto/` 除外） |
| **S8-R1** | `advice/dto`、`advice/model`、`tracenumber/command` 平铺或随 B 组迁出 | **纯移动** | 0 topic 嵌套子包 | **✅ 2026-07-12**：18 类平铺进 `advice/`、`tracenumber/`；`doctorstationadvice/` 并入 `advice/` |
| **S8-M1** | B 组 15 个 1-consumer 随触达 feature PR 迁出 | **纯移动** | 每 PR 附 consumer 数 | **✅ 2026-07-12**：11 MOVE + 4 KEEP（见暂缓决议） |
| **S8-M2** | C 组评审结论落地（MOVE 项） | **纯移动** | 11 项有书面 KEEP/MOVE 决议 | **✅ 2026-07-12**：见 §12.5 C 决议表 |
| **S8-L1** | 延续 S2-L1a/b：`ProductDefinitionQueryService.batchLoadByKeys`；`EncounterContextLoader` 委托 domain | **独立行为 PR** | 与 git mv **不同 PR**（§5.0） | **✅ L1b 2026-07-12**：删除 Loader 2 类；**✅ L1a**：删 BatchLoader；**✅ L1c 2026-07-12**：删 `EncounterContextPageQuerySupport`，L0 并入 Reader |
| **S8-C** | 0-consumer dead code 清理；`WhaleRoleLookup` → `WhaleRoleResolver`；`*GateSupport` → `*Gate`（触达时） | 清理 / 改名 | ArchUnit §5.4 扩展 | **✅ 2026-07-12**：删 dead code；3 个住院 Gate 改名；`HealthApplicationArchitectureTest` 增 §12.4 反向依赖规则 |
| **S8-C2** | `shared/` 存量 `*Support` → 角色后缀（Gate/Assembler/Loader/Orchestrator/Resolver/Helper/Parser） | **纯改名** | shared **0** `*Support`；compile + ArchUnit 绿 | **✅ 2026-07-12**：31 类批量改名（见下表） |
| **S8-C3** | `application/{feature}/**/support/` 存量 `*Support` → 角色后缀 | **纯改名** | health-application **0** 生产 `*Support`；compile + ArchUnit 绿 | **✅ 2026-07-12**：67 类批量改名（见 §12.10） |
| **S8-C4** | application 层误用 `*Command` 载体 → `Query`（读）/ `Spec`（写编排入参） | **纯改名** | health-application **0** 误用 `*Command` 载体 | **✅ 2026-07-12**：7 类（含 `ClinicalCatalogUnitPriceQuery`） |

**硬约束**（继承 §5.0）：**git mv 与 L1 行为改动不得同 PR**；一步一 PR，`mvn compile` 绿。

#### S8 执行记录（2026-07-12）

| 类 | 自 | 至 | 备注 |
| --- | --- | --- | --- |
| `EncounterContextReader` 挂号委托 | `shared/encounter` | 删除 4 方法；`OutpatientRegistrationAppService` 直注 `ClinicRegistrationLoader` | S8-0 |
| `DeviceAutoInventoryDeductSettingResolver` | `inventory/.../support` | `shared/setting/` | S8-0 |
| `InpatientDischargeClinicalGateSupport` | `shared/validator` | `inpatient/nursestation/ATDManager/support/` | S8-0 |
| `DeviceDispenseNurseWorkflowOrchestrator` | `shared/nursestationdevicedispense/support` | `shared/nursestationdevicedispense/` | S8-R0 |
| `ProcedureExecutionPerformerOrchestrator` | `shared/nursestationprocedure/support` | `shared/nursestationprocedure/` | S8-R0 |
| `AdviceChargeAnchorRollupHelper` | `shared/advice` | `outpatient/nursestation/treatment/support/` | S8-M1 |
| `ProcedurePerformerDisplayAssembler` | `shared/advice` | `outpatient/nursestation/treatment/support/` | S8-M1 |
| `PatientHistoryCommonDiagnosisAssembler` + `Row` | `shared/diagnosis` | `outpatient/doctorstation/diagnosis/support/` | S8-M1（闭包随行） |
| `PaymentChannelRefundOrchestrator` | `shared/payment` | `outpatient/financial/refund/support/` | S8-M1 |
| `ChargePricingTypeCodeResolver` | `shared/pricing` | `inventory/warehouse/inboundreceipt/support/` | S8-M1 |
| `OrderRequestFifoSignContextLoader` | `shared/advice` | `outpatient/doctorstation/advice/support/` | S8-M2 |
| `advice/dto/*`（7）、`advice/model/*`（7） | 嵌套子包 | `shared/advice/` 平铺 | S8-R1 |
| `tracenumber/command/*`（4） | 嵌套子包 | `shared/tracenumber/` 平铺 | S8-R1 |
| `DoctorStationSkinTestSyncOrchestrator` | `shared/doctorstationadvice` | `shared/advice/` | S8-R0 topic 收敛 |
| `ProductDefinitionPurchasePageLoader` | `shared/product` | `inventory/warehouse/support/` | S8-M2 |
| `MrmsOperatorResolver` | `shared/mrms` | `mrms/support/` | S8-C（病案域单投影） |
| `OrganizationDisplayResolver` | `shared/organization` | **删除**（0 consumer） | S8-C dead code |
| `nursestationdevicedispense/` + `nursestationprocedure/` + `concurrency/` + `setting/` | 4 topic | `shared/nursestation/` 合并（4 类） | S8-R0 |
| `inpatientadmission/` + `inpatientvitals/` + `inpatienttransfer/` | 3 topic | `shared/inpatient/` 合并（6 类） | S8-R0 |
| `WhaleRoleLookup` | `shared/validator` | 重命名 `WhaleRoleResolver` | S8-C |
| 收费/挂号小票打印 | 直注 `OutpatientEncounterContextLoader` | 改注 `EncounterContextReader` | S8-L1b 前置（封装） |
| `Outpatient/InpatientEncounterContextLoader` | 2 类薄包装 | **删除**；逻辑并入 `EncounterContextReader` | S8-L1b |
| `EncounterContextPageQuerySupport` | `shared/encounter` | **删除**；L0 分页编排并入 `EncounterContextReader`；`ClinicRegistrationLoader` 改注 Reader | S8-L1c |
| `ProductDefinitionBatchLoader` | `shared/product` | **删除**；`loadAsMap` 并入 `ProductDefinitionDisplayTerminologyAssembler`；~82 consumers 改注 | S8-L1a |
| `InventoryWarningRoleGate` | `shared/validator` | `inventory/inventorydetail/support/` | S8-M1 |
| `StaffManagedScopeWriteGate` | `shared/validator` | `basic/position/support/` | S8-M1 |
| `ServiceApplyFormOrchestrator` + model/spec（5） | `shared/advice` | `outpatient/doctorstation/advice/support/`（闭包 6 类） | S8-M1 |
| `HealthConcurrencySupport` | `shared/nursestation` | **删除**；执行并发 → `NurseStationProcedureOccurrenceConcurrencyGate`；发药映射内联 `DeviceDispenseNurseWorkflowOrchestrator` | S8-M1 |
| `DiagnosisSnapshotDisplayResolver` | `shared/diagnosis` | **删除**；逻辑并入 `AdmissionPrimaryDiagnosisAssembler`（0 外部 consumer） | S8-C |
| `InpatientPendingSettlementClinicalGateSupport` | `shared/validator` | 重命名 `InpatientPendingSettlementClinicalGate` | S8-C |
| `InpatientNursingOrderGateSupport` | `shared/advice` | 重命名 `InpatientNursingOrderGate` | S8-C |
| `InpatientAdviceRevokeGateSupport` | `shared/advice` | 重命名 `InpatientAdviceRevokeGate` | S8-C |
| `MedicationDispenseSlipQueryLoader` | OP/IP `PharmacyDispenseSlipQuerySupport` + `MedicationDispenseRefundSupersedeLoader` 各 2 | `shared/pharmacy/` 合并 1 类 | §12.8 P1 |
| `InpatientDischargeClinicalGate`（原 `*GateSupport`） | `inpatient/nursestation/ATDManager/support/` | 重命名 | §12.8 feature Gate |
| `OutpatientChargeSettlementGate`（原 `*GateSupport`） | `outpatient/financial/charge/support/` | 重命名 | §12.8 feature Gate |
| `InpatientTcmRxRevokeGate`（原 `*GateSupport`） | `inpatient/doctorstation/chinesemedicine/support/` | 重命名 | §12.8 feature Gate |
| `InpatientDisplayAccountBalanceAssembler` | `shared/inpatient/` | **删除**；并入 `InpatientAccountBalanceDisplayAssembler` | §12.8 内部合并 |
| `ServiceChildOverrideHints` | `shared/advice/` | **删除**；嵌套类并入 `AdviceExtensionJsonParser` | §12.8 内部合并 |
| `ProductDefinitionDisplayTerminologyAssembler` | `shared/product/` | **删除**；私有方法并入 `ProductDefinitionDisplayTerminologyAssembler` | §12.8 内部合并 |
| `TraceNumberRecordCategoryResolver` + `TraceNumberRecordLineAssemblySupport` + `RecordTraceFromDispenseLineSpec` | `shared/tracenumber/` | **删除**；并入 `TraceNumberRecordOrchestrator` | §12.8 tracenumber 链 |
| `RecordTraceFromStocktakeGainLossLineSpec` | `shared/tracenumber/` | **删除**；嵌套类并入 `TraceNumberStocktakeGainLossTraceOrchestrator` | §12.8 tracenumber 链 |
| `AdvicePackageChildItemDto` | `shared/advice/` | **删除**；嵌套类并入 `AdviceExtensionJsonParser` | §12.8 内部合并 |
| `AdviceRequestExtensionSnapshotDto` | `shared/advice/` | **删除**；嵌套类并入 `AdviceExtensionJsonParser` | §12.8 暂缓结案 |
| `ProcedurePerformerItemDto` | `shared/advice/` | **删除**；嵌套类并入 `AdviceExtensionJsonParser` | §12.8 暂缓结案 |
| `DoctorStationAdviceFifoAllocationResult` + `FifoSegmentDraft` | `shared/product/` | **删除**；嵌套类并入 `DoctorStationAdviceInventoryUnitQuantityHelper` | §12.8 暂缓结案 |
| `ProcedurePerformCountGate`（原 `*GateSupport`） | `domain/clinical/procedure/support/` | 重命名 | §12.8 domain Gate |

**S8-C2 命名收敛**（`shared/` 31 类 `*Support` → 角色后缀，包路径不变）：

| 原类名 | 新类名 | topic |
| --- | --- | --- |
| `AdviceChargeDisplayStatusSupport` | `AdviceChargeDisplayStatusAssembler` | advice |
| `AdviceExtensionJsonSupport` | `AdviceExtensionJsonParser` | advice |
| `DoctorStationSkinTestSyncOrchestrationSupport` | `DoctorStationSkinTestSyncOrchestrator` | advice |
| `InpatientMedicationRequestExecutionMeasureSupport` | `InpatientMedicationRequestExecutionMeasureHelper` | advice |
| `ProductDefinitionOrderChargePricingSupport` | `OrderChargePricingAssembler` | advice |
| `ServiceAdviceDraftCommandSupport` | `ServiceAdviceDraftCommandHelper` | advice |
| `TcmMethodCodeDisplaySupport` | `TcmMethodCodeDisplayAssembler` | advice |
| `DailySettlementFinTypeAmountSupport` | `DailySettlementFinTypeAmountLoader` | financial |
| `PaymentEntererMatchSupport` | `PaymentEntererMatchResolver` | financial |
| `AdmissionDiagnosisSubmitValidationSupport` | `AdmissionDiagnosisSubmitGate` | inpatient |
| `AdmissionVitalSignSupport` | `AdmissionVitalSignOrchestrator` | inpatient |
| `EncounterInpatientAdmissionWriteSupport` | `EncounterInpatientAdmissionOrchestrator` | inpatient |
| `InpatientTransferDischargeHoldOrchestrationSupport` | `InpatientTransferDischargeHoldOrchestrator` | inpatient |
| `DeviceAutoInventoryDeductSettingSupport` | `DeviceAutoInventoryDeductSettingResolver` | nursestation |
| `DeviceDispenseNurseWorkflowSupport` | `DeviceDispenseNurseWorkflowOrchestrator` | nursestation |
| `ProcedureExecutionPerformerOrchestrationSupport` | `ProcedureExecutionPerformerOrchestrator` | nursestation |
| `ThirdPartyPaymentRefundSupport` | `ThirdPartyPaymentRefundOrchestrator` | payment |
| `ThirdPartyPaymentSettleSupport` | `ThirdPartyPaymentSettleOrchestrator` | payment |
| `MedicationDispenseInventoryAvailabilitySupport` | `MedicationDispenseInventoryAvailabilityGate` | pharmacy |
| `MedicationDispenseSlipQuerySupport` | `MedicationDispenseSlipQueryLoader` | pharmacy |
| `ChargeItemProductLookupSupport` | `ChargeItemProductLookupHelper` | product |
| `ClinicalCatalogDispenseLocationResolveSupport` | `ClinicalCatalogDispenseLocationResolver` | product |
| `ClinicalCatalogPriceResolveSupport` | `ClinicalCatalogPriceResolver` | product |
| `MedicationPackageSplitQuantityDisplaySupport` | `MedicationPackageSplitQuantityDisplayAssembler` | product |
| `ProductDefinitionSelectionTerminologySupport` | `ProductDefinitionDisplayTerminologyAssembler` | product |
| `ProductTableNameSupport` | `ProductTableNameHelper` | product |
| `TraceNumberBatchIdentityResolveSupport` | `TraceNumberBatchIdentityResolver` | tracenumber |
| `TraceNumberDispenseTraceWriteSupport` | `TraceNumberDispenseTraceOrchestrator` | tracenumber |
| `TraceNumberRecordOrchestrationSupport` | `TraceNumberRecordOrchestrator` | tracenumber |
| `TraceNumberStocktakeGainLossTraceWriteSupport` | `TraceNumberStocktakeGainLossTraceOrchestrator` | tracenumber |
| `TraceNumberSupplyAgreeTraceWriteSupport` | `TraceNumberSupplyAgreeTraceOrchestrator` | tracenumber |

**暂缓**（shared 内部闭包）→ **✅ 2026-07-12 全部内联合并结案**：

- ~~`AdviceRequestExtensionSnapshotDto`~~ → **✅** 嵌套 `AdviceExtensionJsonParser.AdviceRequestExtensionSnapshotDto`
- ~~`ProcedurePerformerItemDto`~~ → **✅** 嵌套 `AdviceExtensionJsonParser.ProcedurePerformerItemDto`
- ~~`DoctorStationAdviceFifoAllocationResult` / `FifoSegmentDraft`~~ → **✅** 嵌套 `DoctorStationAdviceInventoryUnitQuantityHelper.*`
- B 组原 4 KEEP 决议 → 改为**内联合并**（不迁 feature，避免反向依赖）

**当前指标**：`application/shared/` **89** 类（基线 125；§12.7 ≤100 **达标**）；0 处 shared→feature 反向依赖。

**S8-M1 最终**：B 组 15 项：**11 MOVE + 4 内联合并**（原 KEEP 的 4 DTO 已并入父类）。

#### C — EVALUATE 决议（2026-07-12 落地）

| 类 | 决议 | 状态 |
| --- | --- | --- |
| `AdviceOrderBindQueryLoader` + `AdviceOrderBindRowDto` | **KEEP** `shared/advice/` | ✅ |
| `InpatientNursingOrderGate`（原 `*GateSupport`） | **KEEP** `shared/advice/` | ✅ 已改名 |
| `OrderRequestFifoSignContextLoader` | **MOVE** → `outpatient/doctorstation/advice/support/` | ✅ S8-M2 |
| `OrderChargePricingAssembler` | **KEEP** `shared/advice/`（`OrderChargeDraftAssembler` 闭包 + OP 医生站） | ✅ |
| `OutpatientEncounterContextLoader` | **删除**（S2-L1b 并入 `EncounterContextReader`） | ✅ |
| `InpatientTransferDischargeHoldOrchestrator` | **KEEP** `shared/inpatient/` | ✅ |
| `AdmissionVitalSignOrchestrator` + `SaveContext` | **KEEP** `shared/inpatient/` | ✅ |
| `NurseStationEncounterVisibilityResolver` | **MOVE** → `inpatient/nursestation/nursingrecord/support/` | ✅ S8-M2 |
| `ProductDefinitionPurchasePageLoader` | **MOVE** → `inventory/warehouse/support/` | ✅ S8-M2 |
| `InpatientPendingSettlementClinicalGate`（原 `*GateSupport`） | **KEEP** `shared/validator/` | ✅ 已改名 |
| `InpatientAdviceRevokeGate`（原 `*GateSupport`） | **KEEP** `shared/advice/` | ✅ 已改名 |

### 12.7 S8 验收标准（2026-07-12 自检）

| 项 | 状态 |
| --- | --- |
| `application/shared/` 类数 **≤ 100** | **✅ 89** |
| **0** 处 shared → feature 反向依赖 | **✅**（`HealthApplicationArchitectureTest#sharedMustNotDependOnFeaturePackages`） |
| **0** 处 topic camelCase；**0** 处 topic 嵌套 `dto/model/command` 子包（`product/dto/` 除外） | **✅** |
| B 组单消费者类迁出或确认 KEEP/删除 | **✅**（11 MOVE + 4 KEEP） |
| C 组 11 类 KEEP/MOVE 书面决议 | **✅** §12.5 C 决议表 |
| S2-L1a/b 至少一项完成 | **✅** L1a + L1b + L1c |
| 禁止新增 `*Support` 后缀（存量触达改名进行中） | **✅** shared **0** + application feature **0** 生产 `*Support`（S8-C2/C3）；domain/infrastructure 存量另计 |

### 12.8 与读路径优化的协同

S8 **结构收敛**与 **L1 uplift**并行但不混 PR：

| 优先级 | 动作 | 影响 shared 类 |
| --- | --- | --- |
| P0 | `EncounterContextQueryService` batch hydrate | ~~Loader 删除~~ ✅ S8-L1b/c 完成 |
| P0 | `ProductDefinitionQueryService.batchLoadByKeys` | ~~删除 `ProductDefinitionBatchLoader`~~ ✅ S8-L1a 完成 |
| P1 | `MedicationDispenseQueryService.pageSlip` + OP/IP `PharmacyDispenseSlipQuerySupport` 合并 | **✅** `shared/pharmacy/MedicationDispenseSlipQueryLoader`（含退药 supersede）；删 OP/IP 各 2 重复类 |
| P2 | `InpatientSettleContextQueryService` | **✅** `InpatientSettleReadLoader` 已接入；`*Balance*` 4 类 **KEEP**（跨 6+ 住院 IF） |

### 12.9 S8 阶段小结（2026-07-12）

**结构收敛（S8）已全部闭环**：125 → **89** 类；B/C 组决议落地；L1a/b/c 完成；§12.8 P0～P2 完成；暂缓 4 DTO 内联合并；ArchUnit 守卫上线。

**无阻塞后续**（§0.6 长期读路径指标，属 S3～S5 范畴）：

- 厚 Loader ≤15、domain L1 ≥4 等量化目标——按业务触达迭代，不属 S8 验收范围

### 12.10 S8-C3 feature 命名收敛（2026-07-12）

`health-application` 内 **67** 个 feature `*Support` 类已批量改为角色后缀，包路径不变：

| 域 | 类数 | 主要后缀 |
| --- | --- | --- |
| inventory | 18 | Assembler / Loader / Filter / Helper / Orchestrator |
| report | 9 | Loader / Enricher / Assembler |
| outpatient | 14 | Loader / Orchestrator / Helper / Resolver |
| inpatient | 16 | Orchestrator / Gate / Resolver / Assembler |
| basic | 5 | Gate / Resolver / Provider |
| finance | 2 | Assembler / Helper |
| mrms | 2 | Loader / Orchestrator |
| print | 1 | Helper（`PrintJsonHelper`） |

**示例**（完整列表见 git diff）：

- `OutpatientFeeDetailQuerySupport` → `OutpatientFeeDetailLoader`
- `ServiceApplyFormOrchestrationSupport` → `ServiceApplyFormOrchestrator`
- `NurseStationInpatientBillingProcedureSupport` → `NurseStationInpatientBillingProcedureOrchestrator`
- `DocumentDetailReadOrchestrationSupport` → `DocumentDetailReadLoader`
- `PractitionerWorkloadFinTypeEnrichmentSupport` → `PractitionerWorkloadFinTypeEnricher`

**未纳入本轮**（另开 PR / 触达时改）：

- `zxplat-domain` / `zxplat-domain` 内 ~20 个 `*Support`（含 `FinTypeCodeMappingSupport` 等 domain 工具）
- `zxplat-domain` 的 `IntegrationAfterCommitSupport`（集成 SPI 命名）
- 测试 `ConcurrencyTestHelper`（原 `ConcurrencyTestSupport`）已同步改名

**S8-C4 载体命名**（application 层 `Command` 误用 → 合规后缀）：

| 原类名 | 新类名 | 角色 |
| --- | --- | --- |
| `ClinicalCatalogUnitPriceCommand` | `ClinicalCatalogUnitPriceQuery` | 读侧 Resolver 入参 |
| `ResolveTraceCodesCommand` | `ResolveTraceCodesQuery` | 读侧 Resolver 入参 |
| `InpatientAutoRollServiceOrderExecuteCommand` | `InpatientAutoRollServiceOrderExecuteSpec` | 写 Orchestrator 入参 |
| `RecordTraceFromSupplyLineCommand` | `RecordTraceFromSupplyLineSpec` | 写 Orchestrator 入参 |
| `RecordTraceTransferPairCommand` | `RecordTraceTransferPairSpec` | 写 Orchestrator 入参 |
| `RecordTraceFromDispenseLineCommand` | `RecordTraceFromDispenseLineSpec` | 写 Orchestrator 入参（嵌套） |
| `RecordTraceFromStocktakeGainLossLineCommand` | `RecordTraceFromStocktakeGainLossLineSpec` | 写编排中间入参（嵌套） |

**保留 `Command` 的合法用例**：`*CommandAssembler`、`*CommandHelper`、`BatchCommandItemAssembler`（装配 **domain** `*Command`，自身不是载体）。

---

## 11. 参考

- Extension NoOp：`zxplat-domain/.../integration/noop/config/IntegrationSpiAutoConfiguration.java`
- CHS 独立 application：`module-chs-jilin-application`
- Bridge README 模式：`module-wi-jilin-bridge/README.md`

