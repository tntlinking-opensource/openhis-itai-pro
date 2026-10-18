# 成码交付自检清单

> 注释 P0 最高：[`comments-gate.md`](references/comments-gate.md)。**先注释 pass，再其他 rg。**  
> **闭环 P0**：本清单 **未全部勾选通过前，禁止向用户发最终消息或问「要不要继续」**（见 [SKILL.md §4](../SKILL.md)）。  
> **`TOUCHED`** = 本轮新增/修改的路径。

**Agent 强制**：禁止无注释代码进入最终汇报；禁止 checklist 未完成时阶段性停下。

---

## P0 注释检索（TOUCHED，优先）

### 人工（不可跳过）

对 TOUCHED 中每个 `*AppService`、`*DomainService`、`*QueryService`、`*MapperImpl`、新增 **DTO/实体/Command**：

- [ ] 类级 JavaDoc 存在
- [ ] 每个 `public` 方法：完整 JavaDoc（`@param` 个数=参数；有返回值则 `@return`）
- [ ] 编排类每个 `public` 方法：**≥2 条**业务向 `//`（含 Gate/Pre/Cmd/Asm 或领域等价阶段）
- [ ] 每个 `if`/`else`/`switch` 分支前 1 条**业务向** `//`
- [ ] DTO/实体/Command **每个字段** JavaDoc

### rg 辅助

```bash
rg -n "\{@inheritDoc\}" TOUCHED
rg -n "/\*\*\s*[\u4e00-\u9fffA-Za-z]{1,6}。\s*\*/" TOUCHED --glob "*.java"
rg -n "// (Gate|Pre|Cmd|Asm)[：:]" TOUCHED --glob "*AppService*.java"
```

C2 命中（「保存。」「查询。」等）→ 扩写 JavaDoc。  
**新增 Request/DTO 字段** → 对照 anti-patterns **§C2**，禁止仅 `@Schema`。

---

## P0 快速检索（注释 pass 通过后）

```bash
rg -n "Json\s*;|private String \w*Json|set\w*Json\(" TOUCHED
rg -n "ICurrentUser|getCurrentUserId\(\)" TOUCHED
rg -n "getNumber\(\)\s*\+\s*1" TOUCHED
rg -n "/constants/\w+Constants\.java" TOUCHED
rg -n "\?[^:]*:" TOUCHED --glob "*.java"
rg -n "domainNow|defaultNow" TOUCHED
rg -n "@Query" TOUCHED --glob "*Repository*.java"
rg -n "PersistenceContext|EntityManager" TOUCHED --glob "*AppService*.java"
rg -n "parseInt|parseLong" TOUCHED
rg -n "private\s+(static\s+)?String\s+trimToNull" TOUCHED --glob "*.java"
rg -n "org\.apache\.commons\.lang3\.StringUtils\.trimToNull|StringUtils\.trimToNull" TOUCHED --glob "*.java"
# health support / orchestration 落点（见 SKILL §2.2.1 / B.6.6～B.6.3a）
rg -n "package com\.openhis\.health\.application\.(inpatient|outpatient)\.support" TOUCHED
rg -n "package com\.openhis\.health\.application\.[^;]+\.(doctorstation|nursestation)\.support" TOUCHED
rg -n "import com\.openhis\.health\.application\.inpatient\..*\.support\." TOUCHED --glob "**/outpatient/**"
rg -n "import com\.openhis\.health\.application\.outpatient\..*\.support\." TOUCHED --glob "**/inpatient/**"
rg -n "import com\.openhis\.health\.application\." TOUCHED --glob "**/module/orchestration/**"
```

命中 `trimToNull` 行：改为 `import static com.zxplat.framework.core.application.util.StringUtils.trimToNull`（chs 模块见 `@zxplat-java-string-utils` 的 `ChsTextSupport.blankToNull` 例外）。

命中 **父级 support 包** 或 **跨场景互调 application support** → 迁 `module.orchestration` / `module.common` / `module.querysupport / 单子模块 `support。

---

## A. 全局

- [ ] 文档 0 差异（或 DOC-CONFLICT）
- [ ] **comments-gate 注释 pass 已完成**
- [ ] P0 注释人工项 + rg 已通过
- [ ] P0 快速检索已通过
- [ ] 可编译（用户明示跳过则否）

## B. 注释摘要

- [ ] **忌废话 ≠ 少写**；`//` 写业务后果
- [ ] **§1.1 私有函数必须有注释**：TOUCHED 的每个 `private`/`private static` 有 JavaDoc；前端本地函数有 JSDoc（极薄例外除外）
- [ ] 缺注释 → 主流程加 `//`，禁薄包装 private
- [ ] MapperImpl 禁 `{@inheritDoc}` 占位

## C–I. 其他门禁（§2.1 全框架通用）

- [ ] Repository 无 @Query；LIKE 用 SearchCodeUtil（health 已依赖时）
- [ ] AppService 无直注 ICurrentUser；无 EntityManager
- [ ] 无 `*Json` Response 整段透出
- [ ] 常量已外移（health → `domain.share.constants`；其他 Module → `{mod}-domain/constants` 或 contracts）
- [ ] 有分页时用 PagedResultBuilder；有导出时用 @Excel + AnnotatedExcelExporter
- [ ] save 先于 publish；禁三元
- [ ] 字符串已 trimToNull（chs 用 ChsTextSupport.blankToNull）；无 private trimToNull
- [ ] **JPA 扫描**：本轮新增/搬迁实体或 `*Repository` 包时，已同步本模块 `*AutoConfiguration`（或启动类）的 `@EntityScan` + `@EnableJpaRepositories`；CHS 另同步 `WiChsJpaRepositoryScanOverlapTest`（见 `@zxplat-jpa-scan`）
- [ ] **菜单 seed ID**：本轮新增 `zxplat_permissions` / `zxplat_menus` / `zxplat_permission_grants` 种子时，三个 id 已在全部 `db/migration/**`（含 `health-final`）全局唯一检索；禁 `310800…` 顺序接力（见 `@zxplat-flyway-menu-seed-ids` / B.3.1）
- [ ] **Flyway 整理**：若本轮合并/重编号/搬迁 seed，已同步更新 `@zxplat-flyway-menu-seed-ids` 与 B.3.1 中的锚点路径与示例 ID

## K. health `support / `orchestration` 落点（命中 zxplat-application / health-module-support 时）

- [ ] 新增 `*support 已按 **SKILL §2.2.1 / B.6.6.2 / B.6.3a** 判定只读 vs 写编排、复用子包数
- [ ] **禁止**父级 `application.inpatient.support / `outpatient.support / `*.doctorstation.support / `*.nursestation.support
- [ ] **禁止** `application` 跨子包 `import 对方 `*/support（住院↔门诊、护士站子模块互调）
- [ ] 只读跨聚合 → `module.querysupport.{域}`；跨顶层场景展示 → `module.common.assembler`
- [ ] 写编排 ≥2 子包 → `module.orchestration.{域}`；单子模块 → `{IF}/{子模块}/support；单处简单 → AppService 内联
- [ ] `module.orchestration` **禁止** import `application.*`
- [ ] orchestration 入参用 Spec/Command；AppService 私有方法完成 DTO↔Spec 映射（带 JavaDoc）
- [ ] querysupport enrich 用中性 Row DTO；**禁止** querysupport import application DTO
- [ ] rg 父级 support / 跨场景 support import 已通过（见 §P0 快速检索）

## J. 汇报前

- [ ] **§0.8 / SKILL §4.1 四项门禁已全部满足**（改码 0 差异、注释 pass、本 checklist、编译）
- [ ] 汇报含 **「注释专闸」** 小节（comments-gate §8）
- [ ] **一次性**最终汇报（**禁止**「已完成部分 X，是否继续 Y」）
