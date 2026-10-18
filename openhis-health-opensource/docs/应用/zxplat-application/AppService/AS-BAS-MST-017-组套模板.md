# 基础资料 · 组套模板 · 应用服务设计（AS）

> **文档职责**：说明「组套模板」在**基础资料维护**、**医生站套用/另存**、**护士站划价组套套用**三类入口下的**接口契约（IF）**、业务语义、涉及聚合与 QueryService、规则（BR）、编排意图；作为上游契约供详细设计与实现对齐。
>
> **本文必须写**：IF 编号；用户动作与读写属性；关键校验与状态变化；Gap；异常口径；事件语义；非功能约束；业务向测试要点。
>
> **本文禁止写**：具体类名/应用方法名；Request/Response 字段明细；包路径；Mapper；伪代码；写库顺序；QueryService 字段组装规则。
>
> **页面边界**：**§1.4.1** 基础资料「组套模板维护」页（通用 / 中医 / 划价组套 Tab）；**§1.4.2** 门诊/住院医生站「开立医嘱」工具栏「组套」抽屉（组套 Tab + 医嘱模板 Tab）；**§1.4.3** 住院护士站「住院记账」划价弹窗「组套」入口（仅划价组套，**无**医嘱模板）。医嘱签发、护士划价选品目录、费用明细等仍分别收敛于 **AS-OP-DOR-003** / **AS-IP-DOR-003** / **AS-IP-NUR-005** 等下游 AS。
>
> **覆盖声明**：完整契约以 **§2 IF-BAS-OTL-01～08、10、11** 为准（**IF-09/12 非目标**，见 §1.4.5）；维护端 CRUD 与临床侧「快捷数据源」对齐 **DM-CUS-012** 单表 + `template_source_enum` 统一。
>
> **编码**：AS-BAS-MST-017 · **版本**：v1.7 · **日期**：2026-10-13

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | **维护组套**（`template_source_enum = MAINTAINED`）：通用 / 中医 / 划价三类组套的头与行 CRUD、发布/停用/逻辑删除（IF-BAS-OTL-01～06）；**临床快捷方式**（组套 / 医嘱模板 / 划价组套）：列表 + **预览结构**（IF-BAS-OTL-07～08、11，**不含** enrich/实时定价）；套用期价与库位由前端以行 id 调 **IF-DOR-11** / **IF-IP-PRC-01** `prepickedProductKeys` 补齐；**医嘱模板另存**（`FROM_ADVICE` + **个人或科室**，默认科室）（IF-BAS-OTL-10） |
| **在外** | 医嘱草稿暂存/签发/签退（**AS-OP-DOR-003** / **AS-IP-DOR-003**）；护士划价选品目录与确认写路径（**AS-IP-NUR-005 · IF-IP-PRC-01/02/04**）；目录主数据维护；合理用药、库存 Gate、医保控费 |
| **涉及域** | **OrderTemplate**（头+行，本 AS 主域）；MedicationDefinition / ActivityDefinition / DeviceDefinition（行引用）；Encounter（就诊场景门控）；ChargeItemDefinition（**套用期**由下游目录 IF 读侧解析，**不落组套表**） |
| **编排权威** | 以本文 **§2、§4** 为准；本 AS **仅**提供快捷数据源，**不**代调用下游 saveDraftBatch / confirm*Pricing |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **基础资料维护员 / 科室管理员** | 进入组套模板维护页 → 按种类 Tab 筛选 → 新建/编辑头与行 → 发布或停用 → 逻辑删除 |
| **门诊/住院医生** | 开立医嘱页打开「组套」→ 切换 Tab → 预览结构（IF-08）→ 以 ids 调 **IF-DOR-11** `prepickedProductKeys` 拉目录 → 填充编辑区 → **仍走既有**暂存/签发 IF；选中行后「另存为医嘱模板」（**默认科室**，可选**个人**） |
| **病区护士** | 划价/补费弹窗打开「组套」→ 预览结构（IF-08）→ 以 ids 调 **IF-IP-PRC-01** `prepickedProductKeys` → 填充划价表格 → **仍走** IF-IP-PRC-02/04 确认 |
| **系统** | 组套表**不存单价**；IF-08 **不** enrich/查价；套用期定价与库位与正常开嘱/划价**共用** `ClinicalCatalogPriceResolvesupport（经 prepicked 目录 IF）；维护端固定 `MAINTAINED`；另存固定 `FROM_ADVICE`；**不写** `package_id` / `source_template_id` |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **组套模板** | 领域统一名；对应聚合 `OrderTemplate`（头 `cus_order_template` + 行 `cus_order_template_line`） |
| **维护组套** | 产品名；`template_source_enum = MAINTAINED`；维护端 CRUD；医生站「组套」Tab 仅查此类 |
| **医嘱模板** | 产品名；医生开嘱后另存；`template_source_enum = FROM_ADVICE`；**仅** GENERAL/TCM；医生站「医嘱模板」Tab |
| **通用组套 / 中医组套 / 划价组套** | `template_kind_enum` = `GENERAL` / `TCM` / `PRICING`；划价组套**仅**维护端产生且**无** `FROM_ADVICE` |
| **快捷方式（组套/模板/划价组套）** | IF-08 仅返回头行结构；前端以行 id 调 prepicked 目录 IF 补齐展示/价/库位，再与正常开嘱/划价**同一套**下游 IF 提交；**非**独立保存/签发/划价流程 |
| **成组键** | 行上 `group_key`；GENERAL/TCM 套用后映射药品 `group_id`（**BR-OTL-APY-003**） |
| **多归属合并** | 个人 → 科室 → 全院 → 协定处方顺序展示；同名头不自动去重（**BR-OTL-QRY-001**） |
| **QueryService 能力** | 列表/详情以 **DM-CUS-012 §6.2** 为准；本文不写字段映射细节 |

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **§1.4.1 维护页（本期契约）** | IF-BAS-OTL-01～06：初始化、分页、详情、保存头/行、发布/停用/删除；固定 `MAINTAINED`；支持 GENERAL / TCM / PRICING 三 Tab |
| **§1.4.2 医生站（本期契约）** | IF-BAS-OTL-07～08、10：列表、**预览结构**、另存医嘱模板；套用期价/库位由 prepicked 目录 IF 补齐；保存/签发仍走 **AS-OP-DOR-003** / **AS-IP-DOR-003** |
| **§1.4.3 护士站（本期契约）** | IF-BAS-OTL-11 + IF-08（`applyContext=NURSE_PRICING`）：划价组套列表与预览；确认仍走 **AS-IP-NUR-005 · IF-IP-PRC-02/04** |
| **§1.4.4 应用层落地** | **IF-BAS-OTL-01～08、10、11** 已在 `zxplat/health-application/basic/ordertemplate` 落地 |
| **§1.4.5 非目标（后端代套用）** | **IF-BAS-OTL-09**（医生站确认套用→saveDraftBatch）、**IF-BAS-OTL-12**（护士确认套用→confirm*Pricing）**不实现**；前端选中组套后自行映射并调下游 AS |
| **本期（UI 现状）** | 医生站「组套」按钮为占位（`advice-workspace.vue` / `inpatient-advice-workspace.vue`）；维护页与护士站组套入口**待产品/UI 落地**，契约以本文为准先行 |
| **非目标（老维护 JSON 组套）** | 初版 `tmp_order_group` + `groupJson` _blob 维护路径**本期舍弃**；统一为结构化 `OrderTemplateLine`，不再提供 JSON 整包读写 IF |
| **非目标（老物理删除）** | 初版组合套餐主从**物理删除**；whale 统一**逻辑删除**头并级联软删行（**BR-OTL-021**） |
| **非目标（医嘱模板全院另存）** | 初版 openhis 另存接口含 personal/org/hospital 三路由；whale **IF-BAS-OTL-10** 仅支持 **PERSONAL / DEPARTMENT**（默认科室）；**HOSPITAL** 另存若产品后续需要，单独立项扩展，**不**阻塞本期 |
| **非目标（划价医嘱模板）** | 护士站**不支持** `FROM_ADVICE`；医生**不可**另存 PRICING kind（**BR-OTL-030**） |
| **非目标（签发/划价确费本体）** | 本 AS 不落医嘱草稿、不写护士划价事实；签发、划价确认、吸氧 Gate、库存校验仍走下游 AS |

### 1.5 领域清单（聚合 + 职责 + DM 路径）

| 聚合（域） | 在本场景中的职责（摘要） | DM 路径 |
| --- | --- | --- |
| **OrderTemplate / OrderTemplateLine** | 组套模板头行维护、生命周期、套用只读加载 | `DM-CUS-012-OrderTemplate-组套模板.md` |
| **MedicationDefinition** | 药品行引用校验与展示 enrich | `DM-MED-001-MedicationDefinition-药品定义.md` |
| **ActivityDefinition** | 诊疗行引用；护士划价套用时可展开套餐子项 | `DM-WKF-001-ActivityDefinition-诊疗项目定义.md` |
| **DeviceDefinition** | 耗材行引用 | `DM-ADM-008-DeviceDefinition-器材定义.md` |
| **Frequency** | 行可选默认频次 | `DM-CUS-008-Frequency-频次.md` |
| **Encounter** | 套用场景（门诊/住院）与就诊一致性门控 | `DM-ADM-002-Encounter-就诊.md` |
| **MedicationRequest / ServiceRequest / DeviceRequest** | 医生站套用产出草稿（下游 AS 写） | `DM-MED-003` / `DM-WKF-004` / `DM-WKF-003` |
| **ChargeItem / ChargeItemDefinition** | 划价组套套用产出费用（下游 AS 写） | `DM-ADM-011` / `DM-ADM-010` |
| **ProductDefinition（读）** | 维护端/预览侧目录展示 enrich | 各定义 DM §6.2 目录 QueryService |
| **Organization / Practitioner** | 归属对象解析（科室/个人/全院/协定处方） | `DM-ADM-004` / `DM-ADM-005` |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-OTL-001** | 头表须同时具有合法 `binding_enum` 与 `object_id` | 04、07、10、11 |
| **BR-OTL-003** | `template_code` 非空时租户+归属范围内唯一 | 04 |
| **BR-OTL-005** | 模板名称不得为空 | 04、10 |
| **BR-OTL-006** | 发布为 ACTIVE 时须至少一条有效行 | 06 |
| **BR-OTL-007** | 已逻辑删除的头禁止写侧变更 | 04～06 |
| **BR-OTL-008** | 行类型与引用列互斥且一致 | 05 |
| **BR-OTL-012** | 增/改行时被引用定义须处于可引用发布态 | 05 |
| **BR-OTL-017** | RETIRED 禁止改头属性 | 04 |
| **BR-OTL-018** | 仅 DRAFT/ACTIVE 允许维护行；RETIRED 禁止 | 05 |
| **BR-OTL-023** | TCM 头默认付数必填且 > 0（发布前） | 04、06 |
| **BR-OTL-026** | PRICING 禁止 MEDICATION 行 | 05、06 |
| **BR-OTL-027** | PRICING 仅 INPATIENT 或 COMMON 场景 | 04、11、12 |
| **BR-OTL-029** | `template_source_enum` 必填 | 04、10 |
| **BR-OTL-030** | `FROM_ADVICE` 禁止 PRICING kind | 07、10 |
| **BR-OTL-031** | `template_source_enum` 创建后不可变更 | 04（更新头不含来源） |
| **BR-OTL-APY-001** | 仅 ACTIVE 可预览/快捷选用 | 08、11 |
| **BR-OTL-APY-002** | 预览 scene/kind 须与当前入口/就诊一致 | 08、11 |
| **BR-OTL-APY-003** | 同 `group_key` 多行在前端映射后须同一药品 `group_id`（**下游**开嘱 AS 承接） | —（非本 AS IF） |
| **BR-OTL-APY-004** | 护士站列表/预览须 kind=PRICING 且 MAINTAINED | 08、11 |
| **BR-OTL-APY-005** | **组套套用禁止二次带出**：通用组套（`GENERAL`）与申请单组套（`SERVICE_APPLY`）套用后，下游签发**不得**再按用法/诊疗绑定或合管配置自动带出耗材/诊疗；组套行已是完整开立清单。承接 **AS-OP-DOR-003 · BR-ORD-038**（IF-DOR-23 `skipAutoBind` / 前端跳过触发行） | —（下游签发） |
| **BR-OTL-028** | 组套**不存单价**；套用期由前端以行 id 调 **IF-DOR-11** `prepickedProductKeys`（与历史医嘱/处方同套路）及 **IF-DOR-14** 暂存，与开嘱/划价目录**共用** `ClinicalCatalogPriceResolvesupport：诊疗 **SALE**；药耗 FIFO 批号→**LOT_NUMBER_PRICE**；**IF-08 预览不查价** | 08（结构）→ IF-DOR-11/14 |
| **BR-OTL-QRY-001** | 多归属分段：个人 → 科室 → 全院 → 协定处方；头不自动去重 | 07、11 |
| **BR-OTL-QRY-002** | 列表须支持按 `template_source_enum` 精确过滤；维护端与医生站 Tab 不得默认混查 | 02、07 |
| **BR-BAS-OTL-001** | 维护端创建/保存头固定 `MAINTAINED`；走 DRAFT→发布常规流程 | 04、06 |
| **BR-BAS-OTL-002** | 医嘱模板另存固定 `FROM_ADVICE`；`binding_enum` 为 **PERSONAL 或 DEPARTMENT**（请求缺省 **DEPARTMENT**）；创建后**建议**同事务发布为 ACTIVE（对齐「存完即可引用」） | 10 |
| **BR-BAS-OTL-003** | 医生站列表：`template_kind_enum` 由 Tab（通用/中医）决定；`tcm_flag` 与 TCM kind 对齐 | 07、08 |
| **BR-BAS-OTL-004** | 快捷开嘱/划价**不写** `package_id` / `source_template_id` | —（下游写路径） |
| **BR-BAS-OTL-005** | 护士划价：SERVICE 父项带子项时 **SR 仍一条父请求**；Charge 落**父账单+子项账单**（`parent_id`、冻价，见 **AS-IP-NUR-005 BR-IP-PRC-011**）；**不**在本聚合存价 | —（下游写路径） |
| **BR-BAS-OTL-006** | 护士站**无** `FROM_ADVICE` 列表与另存 IF | 11 |
| **BR-BAS-OTL-007** | 维护端分页按当前 Tab 之 `template_kind_enum` 过滤；`template_source_enum` **可选**（缺省不过滤，含医生站另存 `FROM_ADVICE`） | 02 |
| **BR-BAS-OTL-008** | 个人归属保存时缺省 `object_id` 取当前登录医务人员；科室归属缺省取当前登录科室；全院/协定处方归属取租户级医院根组织 | 04、07、10、11 |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义（业务） | 读/写 | 关键规则（BR） | 关键状态变化（自然语言） | 涉及领域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-BAS-OTL-01 | 进入组套模板维护页 | 初始化下拉：绑定类型、适用场景、组套种类、行类型、模板状态、**中药用法**、**用药频次** | 读 | — | 无 | OrderTemplate（枚举投影）+ 术语/频次 | — |
| IF-BAS-OTL-02 | 维护页：切换 Tab（通用/中医/划价）、绑定/场景/状态/关键字筛选、翻页 | 分页查询组套头列表（**默认**含 MAINTAINED 与 FROM_ADVICE） | 读 | BR-BAS-OTL-007、BR-OTL-QRY-002 | 无 | OrderTemplate | OrderTemplateQueryService · pageOrderTemplates |
| IF-BAS-OTL-03 | 维护页：选中一行、点查看/编辑 | 加载组套头 + 全部有效行 + 目录展示 enrich | 读 | BR-OTL-007 | 无 | OrderTemplate + 目录定义 | OrderTemplateQueryService · getOrderTemplateWithLines |
| IF-BAS-OTL-04 | 维护页：新建/保存头信息 | 创建或更新头（**固定 MAINTAINED**；**不含**来源变更） | 写 | BR-OTL-001～005、017、023、027、029、031、BR-BAS-OTL-001、008 | 新建 → DRAFT；更新头属性 | OrderTemplate | — |
| IF-BAS-OTL-05 | 维护页：保存明细（增删改/全量对齐） | 维护行集合（支持套内排序、成组键、默认剂量/数量/单位等） | 写 | BR-OTL-008、012、018、026 | 行增删改；头状态不变 | OrderTemplateLine | — |
| IF-BAS-OTL-06 | 维护页：发布 / 停用 / 删除 | 头状态迁移或逻辑删除（级联软删行） | 写 | BR-OTL-006、007、021 | DRAFT→ACTIVE；→RETIRED；deleted=true | OrderTemplate | — |
| IF-BAS-OTL-07 | 医生站：打开组套抽屉；切换「组套/医嘱模板」Tab；切换通用/中医；关键字 | 按就诊场景分段加载个人/科室/全院/协定处方候选头（分 `template_source_enum`；仅 ACTIVE） | 读 | BR-OTL-030、BR-OTL-QRY-001～002、BR-BAS-OTL-003、006 | 无 | OrderTemplate + Encounter | OrderTemplateQueryService · listOrderTemplatesByOwnerAndScene |
| IF-BAS-OTL-08 | 医生站/护士站：选中组套后预览 | 加载头行结构（**不含** enrich/实时定价）；前端以定义 id 调 IF-DOR-11 `prepickedProductKeys` 补齐目录/价/库位 | 读 | BR-OTL-APY-001～002 | 无 | OrderTemplate | OrderTemplateQueryService · getOrderTemplateWithLines |
| IF-BAS-OTL-10 | 医生站：选中当前草稿/列表行 → 另存为医嘱模板 | 从就诊医嘱读模型复制为 `FROM_ADVICE`；**默认科室**（`DEPARTMENT`），可选 **个人**（`PERSONAL`，`object_id` = 当前医务人员）；**建议**直接 ACTIVE | 写 | BR-OTL-005、029～031、BR-BAS-OTL-002、008 | 新建头+行；ACTIVE | OrderTemplate | — |
| IF-BAS-OTL-11 | 护士站划价/补费弹窗：打开组套列表 | 加载 **PRICING + MAINTAINED** 划价组套（个人/科室/全院/协定处方分段） | 读 | BR-OTL-027、APY-004、QRY-001、BR-BAS-OTL-006 | 无 | OrderTemplate | OrderTemplateQueryService · listOrderTemplatesByOwnerAndScene |

---

## 3. 缺口跟踪（Gap）

**无待跟踪缺口**（**DM-CUS-012 v1.4.0** 已覆盖 IF-BAS-OTL-01～08、10、11 所需领域能力；IF-09/12 为**非目标**，不纳入 Gap）。

| Gap ID | 缺口类型 | 文件 + 章节 | 缺什么 / 不一致点 | 关闭条件 | 影响 IF | 优先级 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| — | — | — | 无待跟踪缺口 | — | — | — | — |

---

## 4. 接口编排说明书（业务步骤）

### IF-BAS-OTL-01 维护页初始化

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户进入基础资料「组套模板维护」页 |
| **主路径步骤** | 加载绑定类型（个人/科室/全院/协定处方）、适用场景（门诊/住院/通用）、组套种类（通用/中医/划价）、行类型（药品/诊疗/耗材）、模板状态（草稿/启用/停用）等枚举选项；**TCM Tab 行编辑**另下发中药用法（`tcm_method_code`）与用药频次（`rateCode`，与 IF-DOR-18 同源） |
| **关键分支** | 划价 Tab 下行类型选项**不含**药品 |
| **失败分支** | 枚举加载失败 → EX-BAS-OTL-101 |
| **查询侧说明** | 纯读；可缓存枚举 |

### IF-BAS-OTL-02 维护端分页查询

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 维护页已打开；用户切换种类 Tab 或筛选/翻页 |
| **主路径步骤** | 固定 `template_source_enum = MAINTAINED` → 按当前 Tab 传入 `template_kind_enum` → 可选绑定类型、场景、状态、名称/编码关键字 → 分页返回头列表（默认按排序权重升序） |
| **关键分支** | 划价 Tab 仅 PRICING；通用/中医 Tab 互斥 GENERAL/TCM |
| **失败分支** | 查询失败 → EX-BAS-OTL-101 |
| **查询侧说明** | 纯读；不保证含行数统计 |

### IF-BAS-OTL-03 维护端详情（含行）

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户选中一条维护组套进入编辑/查看 |
| **主路径步骤** | 按头 id 加载头 + 全部未删除行（套内排序）→ 批量 enrich 被引用目录的展示名、规格、单位槽等 → TCM 头附带默认付数/代煎展示 |
| **关键分支** | 头已逻辑删除 → 拒绝或空态 |
| **失败分支** | 不存在 → EX-BAS-OTL-102 |
| **查询侧说明** | 纯读 |

### IF-BAS-OTL-04 维护端保存头

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 新建或编辑头表单提交 |
| **主路径步骤** | 新建：`template_source_enum = MAINTAINED`，初始 **DRAFT** → 校验名称、绑定/对象、场景、种类（TCM 付数、PRICING 场景等）→ 创建头；更新：仅允许变更名称、编码、场景、描述、排序等（**不含**来源、**不含** kind 切换若产品禁止） |
| **关键分支** | 个人/科室保存缺省归属对象取当前登录上下文（**BR-BAS-OTL-008**）；编码冲突拒绝 |
| **失败分支** | 校验失败 → EX-BAS-OTL-201；RETIRED 头 → EX-BAS-OTL-202 |
| **查询侧说明** | 写；发布 **OrderTemplateCreatedEvent** / **OrderTemplateUpdatedEvent** |

### IF-BAS-OTL-05 维护端保存行

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 头处于 DRAFT 或 ACTIVE；用户保存明细表格 |
| **主路径步骤** | 校验头未删除且非 RETIRED → 对提交行集合做全量对齐（新增/更新/移除，软删策略）→ 逐行校验类型与引用、默认数量、成组键 → PRICING 拒绝药品行 |
| **关键分支** | ACTIVE 头允许改行（**BR-OTL-018**）；引用停用定义禁止**新建**行 |
| **失败分支** | 引用非法 → EX-BAS-OTL-203；PRICING 含药行 → EX-BAS-OTL-204 |
| **查询侧说明** | 写；行级事件 **OrderTemplateLineAdded/Updated/RemovedEvent** |

### IF-BAS-OTL-06 维护端发布 / 停用 / 删除

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户点击发布、停用或删除 |
| **主路径步骤** | **发布**：断言 DRAFT 且至少一行有效（**BR-OTL-006**）→ ACTIVE；**停用**：ACTIVE/DRAFT → RETIRED；**删除**：头逻辑删除并级联软删行 |
| **关键分支** | 空壳禁止发布；重复删除幂等 |
| **失败分支** | 状态不允许 → EX-BAS-OTL-205 |
| **查询侧说明** | 写；**OrderTemplatePublishedEvent** / **OrderTemplateRetiredEvent** / **OrderTemplateDeletedEvent** |

### IF-BAS-OTL-07 医生站加载可套用列表

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 医生站已选定就诊；用户打开「组套」抽屉 |
| **主路径步骤** | 解析就诊场景 → 按 Tab 确定 `template_source_enum`（组套=MAINTAINED，医嘱模板=FROM_ADVICE）与 `template_kind_enum`（通用=GENERAL，中医=TCM）→ 分别以个人/科室/全院/协定处方归属调用列表查询（仅 **ACTIVE**）→ 按 **BR-OTL-QRY-001** 合并为四段列表返回 |
| **关键分支** | 「医嘱模板」Tab **禁止** PRICING（**BR-OTL-030**）；关键字过滤头名称 |
| **失败分支** | 就诊无效 → EX-BAS-OTL-301 |
| **查询侧说明** | 纯读；**不**返回 DRAFT/RETIRED |

### IF-BAS-OTL-08 预览明细（快捷开嘱/划价数据源）

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 医生站或护士站已选中一条 ACTIVE 组套 |
| **主路径步骤** | 加载头行 → 断言 ACTIVE 且 kind/scene 与入口一致 → 前端以行内定义 id 调 **IF-DOR-11** `prepickedProductKeys`（与历史医嘱/历史处方同套路）补齐展示名、价、默认发药库位 → 映射为编辑 DTO 后调 **IF-DOR-14** 暂存（回传 `dispenseLocationId`，写路径不二次 match 模板） |
| **关键分支** | 维护端 IF-03 详情经 `OrderTemplateAppService#enrichOrderTemplateDetailLines` → `OrderTemplateLineCatalogEnrichAssembler#enrichLines`（AppService 映射 `OrderTemplateLineEnrichRow`）；临床套用预览与维护详情分离 |
| **失败分支** | 非 ACTIVE / kind 不匹配 → EX-BAS-OTL-302 |
| **查询侧说明** | 纯读；**不落库** |

### IF-BAS-OTL-10 医生站另存为医嘱模板

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 医生选中当前就诊下已有草稿或列表行；触发「另存为模板/医嘱模板」 |
| **主路径步骤** | 读取选中医嘱事实（药/诊/材）→ 映射为模板行（含成组键、剂量/频次/付数等；药品 `dosageText` 优先 `dosageInstruction`，回落 `dose`+`doseUnitCode`；诊疗 `executionCount`→`defaultDays`）→ 创建头：`FROM_ADVICE`、kind 由是否含中药推导 GENERAL/TCM、**默认科室归属**（`DEPARTMENT`，`object_id` = 当前会话科室）；用户勾选个人时改为 `PERSONAL`（`object_id` = 当前登录医务人员）→ 批量增行 → **建议**同事务 **发布为 ACTIVE**（对齐存完即可引用） |
| **关键分支** | 请求 `bindingEnum` 缺省或空白 → **DEPARTMENT**；仅允许 PERSONAL / DEPARTMENT；**禁止** PRICING kind（**BR-OTL-030**）；不复制患者事实 id |
| **失败分支** | 无选中行 → EX-BAS-OTL-304；含非法 kind → EX-BAS-OTL-204 |
| **查询侧说明** | 写；**OrderTemplateCreatedEvent** + **OrderTemplatePublishedEvent** |

### IF-BAS-OTL-11 护士站加载划价组套列表

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 护士站划价或补费弹窗打开「组套」 |
| **主路径步骤** | 固定 `template_kind_enum = PRICING` 且 `template_source_enum = MAINTAINED` → 场景限定住院/通用 → 个人/科室/全院/协定处方四次列表查询合并（**BR-OTL-QRY-001**）→ 仅 **ACTIVE** |
| **关键分支** | **不提供** `FROM_ADVICE` 查询（**BR-BAS-OTL-006**） |
| **失败分支** | 查询失败 → EX-BAS-OTL-101 |
| **查询侧说明** | 纯读 |

---

## 5. 业务异常与用户提示

| 异常 ID | 场景 | 用户提示（语义） | 可重试 |
| --- | --- | --- | --- |
| EX-BAS-OTL-101 | 枚举或列表加载失败 | 组套数据加载失败，请稍后重试 | 是 |
| EX-BAS-OTL-102 | 维护端详情不存在或已删除 | 组套不存在或已删除 | 否 |
| EX-BAS-OTL-201 | 头保存校验失败（名称空、编码重复、场景/种类非法） | 请检查组套基本信息后重试 | 是 |
| EX-BAS-OTL-202 | 对已停用/已删除头执行不允许的写操作 | 当前组套不可编辑 | 否 |
| EX-BAS-OTL-203 | 行引用目录不存在或不可引用 | 组套明细含有无效项目，请修正后保存 | 是 |
| EX-BAS-OTL-204 | PRICING 含药品行或 FROM_ADVICE 试图创建 PRICING | 划价组套不可包含药品；医嘱模板不支持划价类型 | 否 |
| EX-BAS-OTL-205 | 发布/停用/删除状态不允许 | 当前状态不允许该操作 | 否 |
| EX-BAS-OTL-301 | 医生站就诊上下文无效 | 请先选择有效就诊 | 是 |
| EX-BAS-OTL-302 | 预览目标非 ACTIVE 或 kind/scene 与入口不符 | 组套不可用或与当前场景不匹配 | 否 |
| EX-BAS-OTL-304 | 另存无选中医嘱 | 请先选择要保存的医嘱 | 否 |

---

## 6. 事件语义与消费方

| 事件 | 触发 IF | 消费方（语义） |
| --- | --- | --- |
| `OrderTemplateCreatedEvent` | 04、10 | 可选检索索引/缓存刷新 |
| `OrderTemplateUpdatedEvent` | 04 | 同上 |
| `OrderTemplatePublishedEvent` | 06、10 | 同上；医生站/护士站可套用集合生效 |
| `OrderTemplateRetiredEvent` | 06 | 从默认可套用集合移除 |
| `OrderTemplateDeletedEvent` | 06 | 维护端列表不可见 |
| `OrderTemplateLineAddedEvent` / `Updated` / `Removed` | 05 | 可选缓存行级刷新 |

> **说明**：本 AS 临床 IF **不**写下游医嘱/划价事实；下游事件由 **AS-OP-DOR-003** / **AS-IP-DOR-003** / **AS-IP-NUR-005** 各自发布。

---

## 7. 非功能性需求

| 维度 | 要求 |
| --- | --- |
| **性能** | 维护端分页默认 ≤50 条/页；医生站/护士站三归属合并列表宜并行读；预览 enrich 批量加载目录，禁止按行 N+1 |
| **一致性** | 维护端头行同一事务；另存模板「创建+行+发布」宜单事务 |
| **权限** | 维护端按机构数据权限过滤绑定对象；个人模板仅本人可改；科室/全院按角色授权 |
| **审计** | 头行逻辑删除保留审计；`template_source_enum` 创建后不可变 |
| **幂等** | 重复删除幂等 |

---

## 8. 测试要点（业务视角）

| 类别 | 要点 |
| --- | --- |
| **维护 CRUD** | 三 kind Tab 互斥筛选；MAINTAINED 固定；DRAFT→发布需有行；PRICING 拒绝药行；TCM 发布需付数 |
| **来源隔离** | 维护列表默认含 MAINTAINED 与 FROM_ADVICE（可传 `templateSourceEnum` 精确筛选）；医生站列表按 Tab 分 source；护士列表不含 FROM_ADVICE |
| **医生站快捷** | 个人/科室/全院/协定处方分段；预览含实时价；前端映射后走既有 saveDraftBatch |
| **医嘱模板另存** | FROM_ADVICE + 默认 **DEPARTMENT**（可选 **PERSONAL**）+ 直接 ACTIVE；禁止 PRICING / HOSPITAL |
| **护士划价组套** | 仅 PRICING+MAINTAINED；预览仅结构；套用 ids → IF-IP-PRC-01 prepicked → 确认走 IF-IP-PRC-02/04 |
| **负向** | RETIRED 不可预览；scene 不匹配拒绝 |
| **回归** | 预览价与开嘱下拉同口径；维护端停用后列表即时不可见 |

---

## 9. 关联文档与变更历史

### 9.1 关联文档

| 文档 | 关系 |
| --- | --- |
| `DM-CUS-012-OrderTemplate-组套模板.md` | 聚合、不变式、领域服务与套用编排权威 |
| `AS-BAS-MST-017-组套模板-SDD.md` | 应用层 IF 字段、编排序列与实现细节（本文不写） |
| `AS-OP-DOR-003-门诊医生站医嘱开立.md` | 医生站暂存/签发（组套选中后仍调本文 IF） |
| `AS-IP-DOR-003-住院医生站医嘱开立.md` | 住院医生站暂存/签发 |
| `AS-IP-NUR-005-住院护士站划价.md` | 划价确认（组套选中后仍调 IF-IP-PRC-02/04） |

### 9.2 变更历史

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v1.7 | 2026-10-13 | **BR-OTL-APY-005**：通用/申请单组套套用后签发禁止二次带出绑定耗材；对齐 **AS-OP-DOR-003 · BR-ORD-038** |
| v1.6 | 2026-06-18 | **`OrderTemplateBinding.AGREEMENT`（协定处方）**；IF-07/11 列表四段（`agreementList`）；**BR-OTL-QRY-001** / **BR-BAS-OTL-008** 扩展 |
| v1.5 | 2026-06-17 | **IF-BAS-OTL-01**：维护页初始化增补 `tcmMethodCodeOptions`/`medicationRateOptions`（TCM Tab 行编辑；频次与 IF-DOR-18 同源） |
| v1.4 | 2026-06-12 | **IF-BAS-OTL-10**：另存支持 PERSONAL / DEPARTMENT（缺省科室）；**BR-BAS-OTL-002** 修订 |
| v1.3 | 2026-06-11 | **IF-BAS-OTL-08**：预览仅返回结构，**不** enrich/实时查价；套用期 prepicked 目录 IF + 写路径 `dispenseLocationId`/`performLocation` 透传 |
| v1.2 | 2026-06-11 | 组套/模板/划价组套明确为**快捷方式**（IF-07～08、11）；**废止** IF-09/12 后端代套用；IF-01～08、10、11 应用层已落地 |
| v1.1 | 2026-06-03 | 新增 §1.4.4 应用层落地范围（IF-01～06 已落地）；修正关联 SDD 引用为 AS-SDD；引用 DM **v1.4.1** |
| v1.0 | 2026-06-03 | 首版：覆盖维护组套、医嘱模板（医生另存）、医生站/护士站套用；单 AS 收敛 IF-BAS-OTL-01～12；Gap 声明领域层已就绪 |
