---
name: code-generation-common-standards
description: >-
  ZXPlat 全框架 Java 成码主规范（zxplat 所有模块统一适用）。
  用户 @ 本 Skill 即触发**合规契约**：须 Read comments-gate+checklist、改码同步注释、
  交付前跑 P0 rg、编译（除非用户说跳过）、一次性汇报含「规范合规摘要」。
  通用项（注释/禁三元/Repository/批量I/O 等）与 Maven 模块无关；health 专规见 §2.2（含 support/orchestration 落点 §2.2.1）。
  **文档：未明示勿落 docs/ops SQL**（见「文档代码一致性」§0）。
  触发：@代码生成通用规范、生成 Java、改 Java、成码、重构、补 JavaDoc、新建 support / orchestration。
---

# 代码生成通用规范

## 0. Agent 合规契约（用户 @ 本 Skill **即生效，不得降级**）

用户在本轮对话 **@ 了本 Skill** → 下列条款对 **本轮全部 TOUCHED Java** 具有**强制约束力**。
**禁止**「已读 Skill 但未按 §2.1 落码 / 未跑 checklist / 未在汇报中证明合规」的伪遵守。

### 0.1 你必须做的事（顺序固定）

| 步骤 | 动作 | 未完成则 |
|------|------|----------|
| A | Read [`comments-gate.md`](references/comments-gate.md) + [`anti-patterns.md`](references/anti-patterns.md) §C | **禁止写码** |
| B | 写/改代码时**同步**写 JavaDoc 与业务向 `//`（禁止先交无注释稿） | **返工补注释** |
| C | 若任务涉及模块边界或新建 `*support / `*Orchestration` → Read [`modules-all.md`](references/modules-all.md) + §2.2.1 / full-standards B.6.6～B.6.7 | 架构违规 |
| D | 注释**第二遍 pass**：逐 TOUCHED 文件按 comments-gate §1 | **返工** |
| E | Read [`checklist.md`](checklist.md)，对 TOUCHED 路径**实际执行** §P0 rg（见 §0.3） | **返工 fix** |
| F | **编译/校验**（用户明示「不考虑编译」则跳过 F） | **修编译** |
| G | 发**一次性最终汇报**，且**必须含 §0.4 合规摘要** | **禁止结束 turn** |

**§0.1 A→G 未全部完成 → 禁止向用户发最终消息、禁止问「要不要继续」。**

### 0.2 通用 vs 模块专属（写清楚边界）

| 类别 | 内容 | 适用 |
|------|------|------|
| **§2.1 全框架通用** | 注释、禁三元、枚举 code、禁 JSON 出参、Repository、EntityManager、批量 I/O、字符串工具、**JPA EntityScan/Repository 扫描同步**… | **zxplat 全部 Java，零例外** |
| **§2.2 模块附加** | health 常量包、五类 Resolver、busNo；各 Module 五层/contracts/Flyway | **仅命中时追加**，不替代 §2.1 |

**存量代码违规不是豁免理由**：本轮 TOUCHED 的新增/修改行须符合 §2.1；不得因「周围文件也这样写」而复制违规模式。

### 0.3 交付前必跑 rg（Agent 用 Shell 执行，TOUCHED = 本轮改动的路径）

```bash
# 注释
rg -n "\{@inheritDoc\}" TOUCHED
rg -n "// (Gate|Pre|Cmd|Asm)[：:]" TOUCHED --glob "*AppService*.java"

# 通用 P0（§2.1）
rg -n "\?[^:]*:" TOUCHED --glob "*.java"          # 禁三元
rg -n "Json\s*;|private String \w*Json" TOUCHED   # 禁 JSON 出参
rg -n "@Query" TOUCHED --glob "*Repository*.java" # Repository
rg -n "PersistenceContext|EntityManager" TOUCHED --glob "*AppService*.java"
rg -n "ICurrentUser" TOUCHED --glob "*AppService*.java"  # AppService 禁直注
rg -n "parseInt|parseLong" TOUCHED                 # 枚举身份
rg -n "private\s+(static\s+)?String\s+trimToNull" TOUCHED --glob "*.java"
# health support / orchestration 落点（新增 *Support 时）
rg -n "package com\.openhis\.health\.application\.(inpatient|outpatient)\.support" TOUCHED
rg -n "package com\.openhis\.health\.application\.[^;]+\.(doctorstation|nursestation)\.support" TOUCHED
# 禁止 application 跨场景互调 */support（应改调 module.orchestration）
rg -n "import com\.openhis\.health\.application\.inpatient\..*\.support\." TOUCHED --glob "**/outpatient/**"
rg -n "import com\.openhis\.health\.application\.outpatient\..*\.support\." TOUCHED --glob "**/inpatient/**"
# module.orchestration 禁止依赖 application DTO
rg -n "import com\.openhis\.health\.application\." TOUCHED --glob "**/module/orchestration/**"
```

**命中即 fix 后重跑**，直至 TOUCHED 范围内通过（或用户明示仅改文档/非 Java 则跳过）。

### 0.4 最终汇报必含「规范合规摘要」（证明 @ Skill 已生效）

```markdown
## 规范合规摘要
- TOUCHED：<路径列表>
- 注释 pass：已完成 / 补全点：<…>
- P0 rg：<各命令 通过|fix 项>
- §2.1 对照：<逐条 遵守|不适用及原因>
- 编译：<模块 mvn 结果 | 用户要求跳过>
- DOC-CONFLICT：<无 | 冲突点>
```

**无「规范合规摘要」的汇报视为未 @ 本 Skill，不合格。**

---

## 1. 你怎么用

| 你在做什么 | 你需要 |
|------------|--------|
| **改/写 zxplat Java（推荐）** | **`@代码生成通用规范`** + 任务书（若有） |
| IF/Mapper/AppService 对准 | 上项 **+** `@应用层代码生成提示词模板` |
| DM 契约表/QueryModel 对准 | 上项 **+** `@领域层代码生成提示词模板` |

**@ 本 Skill = 触发 §0 合规契约**，不是「参考建议」。附录（comments-gate、checklist）是契约组成部分，Agent **必须 Read**，不是可选项。

**适用范围**：`zxplat` 下**所有** Java（health / report / booking / emr / chs / framework / project …），**与 Maven 模块无关**。

---

## 2. P0 总闸

### 2.1 全框架通用（与包无关 — **写清楚、一律遵守**）

以下规则对 **zxplat 任意模块** 的生成/改码均适用。每条含 **必须 / 禁止 / 示例**。

---

#### #0 注释（最高 P0）

| | |
|---|---|
| **必须** | 类级 JavaDoc；每个 `public` 方法完整 JavaDoc（`@param` 个数=参数，有返回值则 `@return`） |
| **必须** | `*AppService` 每个 `public` 方法：`// Gate：` / `// Pre：` / `// Cmd：` / `// Asm：`（可合并步骤，不可整段缺失） |
| **必须** | 每个有意义的 `if`/`else`/`switch` 分支前 1 条**业务向** `//`（写后果，不写「判断 xxx」） |
| **必须** | Request/Response/DTO/实体/Command **每个字段** JavaDoc（`@Schema` **不能**替代） |
| **必须** | `*MapperImpl` 每个 `@Override` 写全 JavaDoc，**禁止** `{@inheritDoc}` 占位 |
| **禁止** | 先提交无注释代码、后补注释；废话 JavaDoc（「保存。」「查询。」） |
| **禁止** | 为凑 JavaDoc 拆 1～2 行薄包装 `private`（缺注释 → 主流程加 `//`） |

```java
// GOOD — AppService
public SaveResponse save(SaveRequest request) {
    // Gate：就诊与患者须一致，防止跨就诊误写
    gate.assertSame(request.getPatientId(), request.getEncounterId());
    // Pre：HTTP 请求转领域命令
    SaveCommand cmd = mapper.toCommand(request);
    // Cmd：领域负责不变量与持久化
    domainService.save(cmd);
    // Asm：映射出参
    return mapper.toResponse(cmd);
}
```

---

#### #1 禁三元运算符

| | |
|---|---|
| **禁止** | `condition ? a : b`（含嵌套、方法实参内的三元） |
| **必须** | 用 `if-else` 赋值或 early return |
| **建议** | 简单循环用 `for`，避免长链 Stream |

```java
// BAD
String name = def.getName() == null ? "" : def.getName();

// GOOD
String name = "";
if (def.getName() != null) {
    name = def.getName();
}
```

---

#### #2 枚举 / 字典身份 = String code

| | |
|---|---|
| **必须** | 库表 `*_enum`、状态列、事件载荷：**String** + 枚举 **`getCode()`** |
| **禁止** | `Integer.parseInt` / `parseLong` 把 code 转数字再比较 |
| **禁止** | 用 `enum.ordinal()` 或整型常量作持久化身份 |

---

#### #3 禁 JSON 整段出参

| | |
|---|---|
| **禁止** | Response/DTO 中 `private String layoutJson` / `headerJson` / `*Json` 对外原样透出 |
| **必须** | 拆为结构化子 DTO 或二进制字段（如 preview 用 `bodyBase64` + `contentType`） |
| **允许** | 实体/库表 JSON 列存设计真源；**入参**可收 JSON 字符串再解析，**出参**须结构化 |

---

#### #4 常量外移

| | |
|---|---|
| **禁止** | 在 `*AppService` / `*DomainService` / `*Manager` 内声明 `private static final String/PATTERN` 业务字面量 |
| **必须** | 常量进各模块 **`{mod}-domain/.../constants`** 或 **`{mod}-contracts`** |
| **health 追加** | 见 §2.2 → `domain.share.constants` |

---

#### #5 会话封装（AppService 禁直注登录上下文）

| | |
|---|---|
| **禁止** | `*AppService` 构造器/字段注入 `ICurrentUser` |
| **必须** | 经模块内 `*OperatorResolver` / `*PractitionerResolver` / Gate 解析操作人、机构 |
| **health 追加** | 见 §2.2 → 五类 Resolver 真源 |

---

#### #6 分页（有分页接口时）

| | |
|---|---|
| **必须** | Request 继承 `AbstractPageQuery` 或模块等价基类；**`pageNo` 从 1 开始** |
| **必须** | 出参 `PagedResult` + **`PagedResultBuilder`** 组装 |
| **禁止** | `pageNum`；`page.getNumber() + 1` 手算页码 |

---

#### #7 Excel 导出（有导出接口时）

| | |
|---|---|
| **必须** | `*ExportRowDto` + 字段 `@Excel` + **`AnnotatedExcelExporter#exportToXlsxBytes`** |
| **禁止** | AppService 内手写 Apache POI |

---

#### #8 Repository

| | |
|---|---|
| **禁止** | Repository 接口上的 `@Query` JPQL/CQL |
| **必须** | 条件下推 DB（`Specification`、方法名派生、QueryDSL 等项目既有方式） |
| **必须** | LIKE 模糊用 `SearchCodeUtil.toCaseInsensitiveLikePattern`（health 已依赖时） |

---

#### #9 批量 I/O 与事件时序

| | |
|---|---|
| **必须** | 循环内禁 N+1；批量 `findAllById` / `saveAll` |
| **必须** | **`save` / `saveAll` 完成后** 再 `publish` 领域事件（防幽灵事件） |

---

#### #10 编排类禁薄包装 private

| | |
|---|---|
| **禁止** | 仅为「方法看起来短」或「凑 JavaDoc」拆 1～2 行 `private void validateXxx()` |
| **必须** | 单字段校验 inline；多字段复合校验（≥3 关联字段）才可提取 private，且须有实质逻辑 |

---

#### #11 应用层禁 EntityManager

| | |
|---|---|
| **禁止** | `*AppService` 注入 `@PersistenceContext EntityManager` |
| **必须** | 持久化经 DomainService / Repository / QueryService |

---

#### #12 字符串空白归一

| | |
|---|---|
| **必须** | `import static com.zxplat.framework.core.application.util.StringUtils.trimToNull`（写库/比较前） |
| **禁止** | `org.apache.commons.lang3.StringUtils.trimToNull`；类内 `private trimToNull` |
| **chs 例外** | `ChsTextSupport.blankToNull`（见 zxplat-java-string-utils.mdc） |

---

#### #13 文档与编译（非「有空就写 docs」）

| | |
|---|---|
| **必须** | 成码对准时：DM/SDD/AS-SDD 为契约权威；冲突标 `DOC-CONFLICT` |
| **必须** | 交付前编译相关模块（用户明示「不考虑编译」则跳过） |
| **禁止** | 未 @ 文档类 Skill、用户未要求时，为热修/展示修复**主动新建或扩写** `docs/**`、ops SQL；见 [`文档代码一致性`](../文档代码一致性/SKILL.md) **§0** |
| **禁止** | 把「改完库/改完码」整理成 `docs/领域/专题/ops/*.sql` 留仓当纪念 |

---

#### #14 JPA EntityScan / Repository 扫描同步

| | |
|---|---|
| **必须** | 新增或搬迁 `@Entity` / `JpaRepository` 包后，确认本模块 `*AutoConfiguration`（或启动类）上 `@EntityScan` + `@EnableJpaRepositories` 已覆盖 |
| **禁止** | 假定 `@ComponentScan` 会注册 JPA Bean；只建接口不改扫描白名单 |
| **细则** | Rule `@zxplat-jpa-scan`；模块配置落点见 [modules-all.md](references/modules-all.md) |

---

### 2.2 模块附加（**不替代** §2.1，仅追加）

| 场景 | 追加规则 |
|------|----------|
| **zxplat-app** | 常量 → `com.zxplat.domain.share.constants`；**禁** `zxplat-application/.../constants/` |
| **zxplat-app** | 五类 Resolver 真源 → `module.common.validator` |
| **zxplat-app** | `busNo` → `BusinessSequenceService` + `AggregateTypeConstants` |
| **zxplat-app** | **`support / `orchestration` 落点** → 见 **§2.2.1**；**禁止**父级 `support；**禁止** `application` 跨子包互调 `*/support |
| **扩展 Module** | 六层分包、contracts、adapter **禁**依赖 `{mod}-application` / `zxplat-application` → [modules-all.md](references/modules-all.md) |

#### §2.2.1 health：`support / `orchestration` 落点（P0）

> **成码前**：先判定**只读 vs 写编排**、**复用范围（几个 application 子包）**，再定包。**禁止**「先建 `support 再说」。细则 → [full-standards.md B.6.6～B.6.7](references/full-standards.md)。
>
> **命名说明**：写编排上收包用 **`module.orchestration`**（**禁止** `module.workflow`——与 `domain.workflow` 聚合混淆）。

**决策顺序（自上而下命中即停）**

| 顺序 | 条件 | 落点 | 命名示例 |
|------|------|------|----------|
| 1 | **只读** + 跨聚合组装 / 读 Gate / Loader | `module.querysupport.{域}` | `NurseStationEncounterVisibilitysupport、`NurseStationOrderExecuteListLoader` |
| 2 | **只读** + 跨顶层场景展示回填（≥2 个 AppService） | `module.common.assembler.{域}` | `InpatientAccountBalanceDisplayDtoApplier` |
| 3 | **只读** + 跨场景 Gate/分页（errorCode 一致） | `module.common.validator` / `module.common.assembler` | `PatientEncounterConsistencyGate` |
| 4 | **写编排** + **≥2 个** `zxplat-application` **子包**复用（含跨 `inpatient`/`outpatient`、跨护士站/医生站子模块） | `module.orchestration.{advice\|nursestation\|financial\|encounter}` | `DoctorStationSkinTestSyncOrchestrationsupport、`DeviceDispenseNurseWorkflowsupport、`ServiceApplyFormOrchestrationsupport |
| 5 | **写编排** + **仅单** application 子包调用 | `application/{IF}/{子模块}/support/` | `InpatientTcmDecoctionExecutesupport、`NurseStationAutoRollLinePoster` |
| 6 | 纯函数工具（无 Spring、无跨聚合 I/O） | `domain.share.utils`（静态方法） | `BloodPressureParser` |
| 7 | 仅单 AppService、≤3 步、无复用 | **AppService 内联** + `//` | **禁止**为此新建类 |

**硬门禁（新增 + 迁移后）**

| 规则 | 说明 |
|------|------|
| **禁止 application 跨子包互调 `*/support** | 住院 **不得** `import 门诊 `*/support（反之亦然）；护士站划价 **不得** `import 护士站执行 `*/support——一律改调 `module.orchestration.*` |
| **`module.orchestration` 契约** | 可注入 `*DomainService` / 写路径 `*QueryService`；入参用 Command / Spec / `module.common` model；**禁止**依赖 `zxplat-application` 的 Request/Response/DTO；**禁止** `@Transactional` |
| **AppService ↔ orchestration 边界** | DTO/Request ↔ Spec/Command 的映射在 **AppService 私有方法**完成（带 JavaDoc）；orchestration **不得** import `application.*` |
| **querysupport 读 enrich 边界** | 只读 enrich 用 `module.querysupport.{域}` + 中性 Row DTO；AppService 在边界做 DTO↔Row 映射，querysupport **不得**依赖 application DTO |
| **禁止父级 `support 包** | 同前：`inpatient.support、`doctorstation.support、`nursestation.support 等 |

**禁止路径（本轮新增代码）**

| 禁止包 / 模式 | 原因 | 应改为 |
|---------------|------|--------|
| `application.inpatient.support 等父级 | 无 IF 归属 | `module.orchestration` / `module.common` / 单子模块 `support |
| `application.*` 互调对方 `*/support | 包依赖方向混乱 | `module.orchestration.{域}` |
| `module.querysupport.*` 写 `*DomainService` | querysupport 只读 | `module.orchestration.{域}` |
| `module.common.*` 写编排写库 | common 只读展示/Gate | `module.orchestration.{域}` |
| `module.orchestration` import `application.*` | 模块依赖倒置 | Spec/Command 下沉 `module.common` model |

```text
# GOOD
module/querysupport/nursestation/NurseStationEncounterVisibilitySupport.java      # 只读
module/common/assembler/inpatient/InpatientAccountBalanceDisplayDtoApplier.java    # 只读展示
module/orchestration/advice/DoctorStationSkinTestSyncOrchestrationSupport.java   # 写编排；门诊+住院皮试 companion
module/orchestration/advice/ServiceApplyFormOrchestrationSupport.java            # 写编排；门诊+住院医嘱
module/orchestration/nursestation/DeviceDispenseNurseWorkflowSupport.java        # 写编排；执行+划价+门诊处置
module/querysupport/ordertemplate/OrderTemplateLineCatalogEnrichAssembler.java   # 只读 enrich；AppService 映射 Row
domain/share/utils/BloodPressureParser.java                                      # 纯函数；无 Spring
application/.../orderexecute/support/InpatientTcmDecoctionExecuteSupport.java      # 写编排；仅执行子模块

# BAD（禁止新增 / 迁移后禁止残留）
application/inpatient/.../import outpatient/.../advice/support/...
application/inpatient/nursestation/inpatientbilling/.../import .../orderexecute/support/...
application/inpatient/support/...
module/workflow/...   # 与 domain.workflow 混淆
```

**冲突裁决**：内联+`//` > 薄包装 private；**orchestration 落点以 §2.2.1 为准**；存量跨包 `support 须按任务书迁入 `module.orchestration` 并删旧类。

---

## 3. 目标模块（仅模块差异，按需 Read）

| 任务落点 | 追加阅读 |
|----------|----------|
| `zxplat-app` | modules-all §health + full-standards B.6、**B.6.6～B.6.7**、B.10 |
| `zxplat-report | modules-all §report |
| `zxplat-module-booking` | modules-all §booking |
| 其他 Module | modules-all 对应节 |

---

## 4. 执行纪律

### 4.1 唯一终止条件（§0.1 A→G 全部满足）

向用户发最终消息前：**改码完成** + **注释 pass** + **checklist rg 通过** + **编译通过（或用户跳过）** + **汇报含 §0.4 合规摘要**。

### 4.2 禁止中途打断

未完成上述四项期间，**禁止**：阶段性总结后停下、问「要不要继续」、把 checklist/编译留到下一轮。

### 4.3 唯一阻断汇报

仅当缺用户输入、文档不可裁决、环境错误无法修复时——说明阻塞点，**不是**分批确认。

---

## 5. 与场景 Skill 分工

| 层级 | 内容 |
|------|------|
| **本 Skill §2.1** | 全框架通用 P0（注释、三元、Repository…） |
| **本 Skill §2.2 + modules-all** | 模块差异 |
| **应用层/领域层模板** | IF/DM 对准（**不重复** §2.1） |

---

## 6. 附录索引

| 文件 | 用途 |
|------|------|
| [comments-gate.md](references/comments-gate.md) | 注释专闸全文（§0.1 步骤 A/D **必读**） |
| [checklist.md](checklist.md) | 交付自检 + rg 全集（§0.1 步骤 E **必读**） |
| [anti-patterns.md](references/anti-patterns.md) | BAD/GOOD 反例 |
| [full-standards.md](references/full-standards.md) | 完整正文 |
| [modules-all.md](references/modules-all.md) | 模块差异 |
