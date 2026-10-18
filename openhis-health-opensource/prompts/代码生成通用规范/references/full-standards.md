# 代码生成通用规范（完整正文）

> 权威全文。入口见 [SKILL.md](../SKILL.md)；模块差异见 [modules-all.md](modules-all.md)。

---

# 代码生成通用规范（全量）

本文件为 **Java/Spring/JPA/Whale 成码** 的完整正文。**P0 总闸表**以 [SKILL.md §2](../SKILL.md) 为准；**反例**见 [anti-patterns.md](anti-patterns.md)；**交付自检**见 [checklist.md](../checklist.md)。模块差异见 [modules-all.md](modules-all.md)。

**非 Whale** 或任务书另有约定时：Flyway 目录、constant 包、事务策略等以任务书覆盖默认值。

---

## 0. 门禁总览（P0 索引，细则见 A/B 节）

> **§2.1 全框架通用**与 **§2.2 模块附加**的分层见 [SKILL.md §2](../SKILL.md)。本节索引仍覆盖全文；health 专规条目在 §2.2 标注。

| # | 主题 | 细则 |
|---|------|------|
| 1 | 注释 + 编排体内业务向 `//` | A.3–A.4、B.1 |
| 2 | 禁三元；`for` 优先 | A.8 |
| 3 | 枚举/字典身份 = String `getCode()` | B.10.7–8.1 |
| 4 | 禁 JSON 出参 `*Json` | B.6.2 |
| 5 | 常量外移（health 追加：→ `domain.share.constants`） | B.10.2.1 |
| 6 | 禁 AppService 直注 ICurrentUser（health 追加：五类 Resolver） | B.6.5 |
| 7 | 分页 `pageNo` 1-based + PagedResultBuilder | B.10.11 |
| 8 | Excel `@Excel` + ExportRowDto | B.10.12 |
| 9 | Repository 禁 @Query；条件下推 DB | B.2 |
| 10 | 批量 I/O；Map 碰撞 O(1) | A.9.1 |
| 11 | save/saveAll 先于 publish | B.14 |
| 12 | 禁薄包装 private | B.5.1.1 |
| 13 | zxplat-application 禁 EntityManager | B.2 |
| 14 | busNo → BusinessSequenceService（**health 专规**） | B.10.9 |
| 15 | 文档先行、编译、DOC-CONFLICT | A.7、A.9 |

**冲突裁决（强制）**

| 冲突 | 裁决 |
|------|------|
| 注释要多 vs 禁薄包装 private | **内联 + `//`**（B.5.1.1 > B.1） |
| SDD 写 application constants vs B.10.2.1 | **domain.share.constants** + DOC-CONFLICT |
| DM/SDD busNo vs B.10.9 | **B.10.9** + DOC-CONFLICT |
| 从 0 生成 vs 扫仓对齐存量 | **不扫仓**（A.7）；增量修订才对比 |
| QueryModel 有 JSON 列 vs 对外 Response | **结构化 DTO**（B.6.2） |

**执行纪律（P0，细则 §0.8–0.9、A.2、A.10）**

- **连续闭环**：差异 → 改码 → 校验 → 继续，0 差异后再汇报（**§0.8**）。
- **空框架直出**：从 0 生成且目标包为空时，**禁止**盘点式目录扫描；只读 DM/SDD + 必要基类签名（**§0.8.1**）。
- **静默执行**：剩余差异 > 0 或自检未完成期间，**禁止**进度播报与向用户要确认；仅最终汇报或阻断汇报（**§0.9**）。

### 0.8 连续闭环执行（P0，默认）

成码会话的默认模式是 **单轮跑完、一次性交付**，不是「做一段、问一段」。

**循环**（Agent 内部，不向用户播报）：

```
读任务书 → 改码（同步注释）→ 注释 pass → checklist rg → 编译/校验
    ↑___________________________________________|
         任一未通过则继续 fix，不得向外发消息
```

**完成标准**（与 [SKILL.md §4.1](../SKILL.md) 一致）：

1. 任务书范围内 **剩余差异 = 0**（或 `DOC-CONFLICT` 已留痕）  
2. **comments-gate 注释 pass** 已完成  
3. **[checklist.md](../checklist.md)** P0 注释检索 + P0 快速检索已通过  
4. **编译/必要校验**通过（用户明示「不考虑编译」则跳过）

**四项全部满足** → 允许 **§6 / 附录最终汇报**；**任一项未满足** → **必须继续执行**，不得结束 Agent turn。

#### 0.8.1 空框架直出（从 0 生成）

目标包为空或任务书指定「新建」时：

- **只读** DM/SDD/AS-SDD + 任务书点名的基类/接口签名（如 `PagedResultBuilder`、`BusinessException`）  
- **禁止**以「对齐风格 / 确认包结构」为由 `find`/`grep`/目录遍历扫仓  
- **禁止**扫完仓再向用户确认「是否按 SDD 继续」——读文档后直接落盘并进入 §0.8 循环

### 0.9 静默执行与禁止中途确认（P0）

在 **§0.8 完成标准未全部满足** 之前，Agent **不得**向用户发送以下类消息：

| 禁止 | 示例（含但不限于） |
|------|-------------------|
| 阶段性交付后停下 | 「A 类已改完，接下来改 B…」 |
| 索要继续确认 | 「要不要继续」「是否继续」「需要我继续吗」「say the word」「确认后我再…」 |
| 把自检留到下一轮 | 「代码已写好，下次再跑 checklist / 补注释 / 编译」 |
| 以规模为由暂停 | 「改动较多，先停在这里请你过目」 |

**允许向外发消息的两种情形**：

1. **最终汇报**：§0.8 四项全部满足（见 [SKILL.md §6](../SKILL.md)）  
2. **阻断汇报**：确实缺少用户输入或存在不可裁决冲突（见 [SKILL.md §4.3](../SKILL.md)）——说明阻塞点与已尝试项，**不是**「正常流程的分批确认」

**Agent 内部**仍可持续使用工具（改文件、编译、rg）；「静默」指 **不对用户做进度播报或确认请求**，不是禁止思考或禁止 tool call。

---

## A. 与语言弱相关的交付规范

### A.1 优先级（压倒性）

1. **绝对禁止项**（上文 **§0**、[checklist.md](../checklist.md)、[anti-patterns.md](anti-patterns.md) + 任务书）——不得违反。  
2. **硬门槛**：注释/JavaDoc 全量（可编译与 Flyway 门禁见下方说明）。  
3. **次要优化**（行数、技巧）——仅在 1、2 满足之后。

### A.2 执行顺序（结构化执行）

- **直接**按 DM+SDD 落盘代码，不需要先扫描工程现状；**只有当用户明确指示"增量/修订存量代码"时**，才需要先对比现有实现再改码。  
- **单轮闭环**：改码 → 注释 pass → checklist → 编译在同一 Agent 会话内连续完成（**§0.8–0.9**、[SKILL.md §4](../SKILL.md)）。  
- 中途不得输出进度或阶段性总结；**禁止**问用户「要不要继续」；未达 §0.8 完成标准必须继续执行（见 **§0.9**）。

### A.3 文档化（多语言 + Java）

Java 须满足 **B.1**。总原则：**注释是硬交付**，「忌废话」仅排除无信息重复，**不**允许省略规定 JavaDoc 或 `@param`/`@return`/`@throws` 的实质内容。

### A.4 过程内注释（实现体内）

> **P0 最高优先级专闸**：可执行清单、密度底线、注释第二遍流程见 **[comments-gate.md](comments-gate.md)**。本节为完整条文。

- 分支、状态路径、多步编排、副作用前、循环入口须有 **业务向** `//` 说明；编排类（`*DomainService`、`*QueryService`、获准改的 `*Manager`、**`*AppService`**）以体内节点为 **P0**。  
- **密度（单轮即验收）**：每个 **有意义的** `if` / `else if` / `else` 分支、每个 `switch` 分支、连续多步编排中 **每一步** 之前须有至少一句 **业务含义** 的 `//`（写清对业务方/患者/资源意味着什么）；**禁止**以复述方法名、`Repository#…`、`save`、`return` 短路径代替业务句。  
- **「忌废话」边界**：仅禁止「只复述类型名/表名」「无提炼整段粘贴 DM」；**不禁止**为读懂而增加的业务说明句。  
- **自检与代码一致**：自检项中的「方法体内 `# 0.4` 等价项」须与实际代码中的 `//` 一一可对；禁止「自检全 ✅、代码缺注」。  
- 规则同 **「0. 门禁总览」第 1 条**：密度宁多勿少，忌头重脚轻；**禁止**用超长方法头 JavaDoc 挤占篇幅却留大段无注释分支体。

### A.5 命名与注释分工

- 宜短、动宾；语义进 JavaDoc/`//`。**禁止** `data`、`item`、`req`、`dto`、`row` 等占位名（领域 Java 额外忌见 **B.1** 与领域模板命名列）。

### A.6 错误信息、日志与测试

- 日志、异常与文档叙事一致；Java 错误码字面量策略见 **B.1** 与历史 **BusinessException** 口径。  
- 测试类/用例须说明覆盖点与规则或状态迁移。

### A.7 规格缺口与冲突

- **文档先行**：默认仅服从任务书给出的 **DM/SDD（及 AS-SDD 等 zxplat-doc 契约）** 与用户口述；实现据此生成或修订。**DM + SDD 是代码生成的唯一权威输入**，不将检索外部仓库、对照其他实现作为成码前置或验收的必经环节。
- **禁止默认发起"盘点现状/找参照物"的目录扫描或文件探查命令**：成码前置只需读取 DM/SDD 文档；不得以"对齐风格/确认包结构"为由发起 `Get-ChildItem`、`find`、`grep` 等遍历命令拖慢执行节奏。**目标包为空时，直接从 0 按 SDD 指定路径新建，无需先探查**。
- **权威顺序**以任务书为准；`DOC-CONFLICT` 与清单双留痕。  
- **busNo 例外**：关于 **业务流水号 / busNo** 的生成与取号，若 DM/SDD/AS 等文档与本 Skill **B.10.9** 不一致，**以 B.10.9 为准**（不得仅因文档旧述沿用错误取法）；须在 `DOC-CONFLICT` 按 **B.10.9** 要求留痕。

### A.8 控制流可读性（stream 与 for）

- **默认**：多步校验、多分支、跨多集合——**`for` + `if-else`**。  
- **可用 `Stream`**：单行或局部、一眼能看完的简单 `map`/`filter`/`collect`。  
- **禁止**：长链 `Optional.ofNullable(...).orElseGet(() -> stream()...)`、深层嵌套三元与 stream 组合等 **炫技式**写法。  
- **禁止**任何三元表达式 `?:`（含简单场景与嵌套场景）；统一改为显式 `if-else`。
- **编排类方法粒度**：可读性优先 **主流程内联 + A.4 注释**，而非碎片化 `private`；拆分门槛见 **B.5.1**。

### A.9 增量、删改与诚信

- **用户明确要求修改/删除已有 API 时**才做全仓库引用检索；从 0 生成新代码时无需检索。  
- **禁止** `git checkout` 覆盖用户已收敛枚举/共享文件骗编译。  
- **禁止**未授权改 **B.7** Manager 整文件。

### A.9.1 批量 I/O 实现约束（硬门槛）

凡存在批量语义的方法（增删改查），数据库 I/O 必须遵守以下规则：

- 批量路径必须通过**集合化/批处理一次性落库**；**禁止**以 `for`/`foreach` 循环逐条执行数据库查询、新增、更新、删除。
- 允许在内存中循环做参数组装、校验、去重、分组；但**数据库 I/O** 必须收敛为批量接口（如批量 `saveAll`、`IN` 条件查询、集合条件更新/逻辑删除）。
- 若受底层能力限制无法一次性批量落库，必须在 `DOC-CONFLICT` 留痕并给出原因与替代方案，禁止静默退化为逐条 I/O。
- **批量入参内存去重（硬门禁）**：写方法接收批量入参时，若存在业务唯一键约束，**必须先在内存对同批次入参做碰撞检测**（去重或报业务异常），再执行数据库唯一性验证；**禁止**仅依赖 DB 约束在入库时报错。
- **批量唯一性校验下推（硬门禁）**：跨行唯一性检查若须到数据库验证，**必须将所有待检项收集为集合后一次性批量查询**（如 `existsByXxxIn`），再在内存做碰撞判断；**禁止**在循环内逐条调用单条 `existsByXxx`。
- **批量内存碰撞校验禁止嵌套循环（硬门禁）**：在内存中对批量集合执行唯一性或碰撞检验时，**禁止**双重 `for` 循环（`O(n²)`）比对；必须先将"已存在记录"或"入参集合"构建为 `Map<业务键, 归属方>` 索引，再以 `O(1)` 查找完成碰撞判断。对"入参集合"与"数据库查询结果"的交叉比对同理：先建 `Map`，再单次遍历入参查找，禁止逐条 `for` 扫描存量列表。

- **复合业务键分隔符安全（硬门禁，新增）**：当需要拼接多字段构成业务唯一键（如 `typeCode + ":" + identifierNo`）时，**禁止使用业务值中可能出现的字符作为分隔符**（如 `:`、`#`、`|` 等常见字符）。
  - 碰撞示例：`typeCode="A:B"` + `identifierNo="C"` 与 `typeCode="A"` + `identifierNo="B:C"` 拼接后均为 `"A:B:C"`，导致校验失效。
  - **正确做法**：使用业务值中不可能出现的分隔符，如 `\u0001`（ASCII SOH）或采用嵌套 Map 结构 `Map<K1, Map<K2, V>>` 避免拼接；或在代码注释中明确说明"业务键中不得包含分隔符字符"的前提假设并在校验前做前置校验。

- **入参归一化一致性（硬门禁，新增）**：当业务键（如身份证号、证件号）需要归一化（大小写转换、trim 等）后进行唯一性校验时，**批量入参内存去重**与**单条数据库唯一性校验**必须使用**完全相同的归一化逻辑**。
  - 错误示例：批量去重时使用 `idCard.trim().toUpperCase()`，但单条校验时直接用原始值比对——导致小写入参通过批量去重却在单条校验时漏过。
  - **正确做法**：在入参进入领域服务的第一时间统一归一化，或确保所有校验点使用同一归一化工具方法；若因历史原因无法统一，必须在 `DOC-CONFLICT` 中详细记录差异及风险。

> 领域层 `*DomainService` 写方法的默认双形态 Command 设计规则（`item`/`items` 包装、`XxxItemCommand` 结构、SDD 字段表约定等）见《**领域层代码生成提示词模板**》D3.2.1。

### A.10 连续闭环执行（默认）

与 **§0.8–0.9** 同义；入口 [SKILL.md §4](../SKILL.md) 为 Agent 第一阅读点。

- **单轮交付**：改码、注释 pass、checklist、编译在同一 Agent 会话内连续完成。  
- **禁止中途确认**：未完成 §0.8 四项前，不得问用户是否继续。  
- **fix 循环在内**：编译失败、rg 命中、注释不达标 → 继续改，不向外汇报进度。

### A.11 成码后与规格交叉核对

- 结项前通读规格与代码，修订至一致并留痕。

---

## B. Java / Spring / JPA / Whale 领域层专规

与 **「0. 门禁总览」、A** 同时生效；细节与历史口径一致处不重复展开。

### B.1 JavaDoc 细则（硬门槛）

- 类与接口：**类级 JavaDoc**（职责、与规格对应、边界假设）。  
- `public`/`protected`：**完整方法头**，`@param`/`@return`/`@throws` 每条实质中文，禁止敷衍占位。  
- **每个** `private` 实例方法、**每个** `static` 方法：方法头 `/** … */`（调用关系、前提、无状态假定）；**`private` 方法不得以"内部方法/无需注释"为由省略 JavaDoc，与 `public` 方法同等要求**（**B.5.1 / B.5.1.1**：**优先减少**不必要的 `private` 数量——**禁止**为凑 JavaDoc 而把单行逻辑包成 `private`；**禁止** JavaDoc 仅写「私有方法：`foo`」复述方法名而无业务语义）。  
- 实体、Command、Query、事件、QueryModel / 查询 DTO：**每字段** JavaDoc；枚举**每常量**注释。  
- 编排类内 **仅** `*Repository`（及同类边界清晰的注入）、`IDomainEventPublisher` 等：**不强制**字段 JavaDoc；**类级与全部方法 JavaDoc + A.4 体内注释**仍强制。
- **`{@inheritDoc}` 禁止项（硬门禁）**：凡实现接口或继承抽象类型的类（含 `*MapperImpl`、`*AppService`、`*QueryService`、`*DomainService` 等），其 `@Override` 方法的 `/** … */` **须在实现类源文件中写全**可读说明，并保留与契约等价的 `@param` / `@return` / `@throws` 实质内容（允许与接口措辞一致）。**禁止**以 **`{@inheritDoc}` 单独成段**或作为 JavaDoc **唯一正文**；**禁止**「见接口」「同 `XxxMapper`」等无拷贝的评审盲区写法。若需强调与上层契约一致，可在完整文档末行追加「与 `FooMapper#bar` 契约一致」类短句，但不得替代正文。

### B.1.1 业务异常统一口径（硬门槛）

- 领域层默认统一使用 `com.zxplat.framework.core.application.exception.BusinessException`（含错误码 + 消息）表达业务失败语义。  
- **禁止**在常规领域开发中新增 `*DomainException`、`*BizException` 等平行业务异常类型（除非任务书/用户明确要求且在 `DOC-CONFLICT` 留痕）。  
- 历史存量若已存在自定义业务异常，增量修订默认优先收敛为 `BusinessException`，避免异常体系分叉。
- **禁止 `Optional.ifPresent` / `orElse(null)` 静默吞掉实体不存在（硬门禁）**：领域写方法加载聚合根或子实体时，若查询结果为空，**必须**抛出 `BusinessException`（含对应 `*_NOT_FOUND` 错误码），禁止用 `ifPresent(...)` 包裹写逻辑——`ifPresent` 在实体不存在时会静默跳过整个业务操作，调用方收到正常返回但数据实际未变更，属于隐蔽的数据丢失错误。正确模式：`repository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.XXX_NOT_FOUND, "..."))` 后再执行写操作。

### B.2 `Repository` 与查询

- **`EntityManager` 与 `@PersistenceContext` 分层约定（硬门禁 + 领域放宽）**  
  - **应用层（`zxplat-application`，硬禁止，P0）**：`*AppService`、`MapperImpl` 及同模块一切应用编排类中，**禁止** `jakarta.persistence.PersistenceContext`（或 `javax.persistence.PersistenceContext`）+ **`EntityManager` 实例字段**（典型反例：`@PersistenceContext private EntityManager entityManager;`，含 `protected`/`package` 等一切字段级注入）；**禁止**在该层以 `EntityManager`/`createQuery`/Criteria **直接访问持久化**或旁路领域 `Repository`/领域服务。应用层访问库表**只许**经领域 `*QueryService`/`*DomainService` 或任务书明示的**非 JPA** 网关，**不得在 App 内持库会话**。  
  - **领域 service 层（`zxplat-domain`，默认可不用、必要时可用）**：`*DomainService`、`*QueryService`——**允许**在确有技术必要时使用 `@PersistenceContext` 注入 **`EntityManager` 字段**并编写 Criteria/JPQL 等；**默认**仍须优先 **`JpaRepository` 派生方法** 或 **`JpaSpecificationExecutor` + `Specification`**，能用仓储表达的**不应**为省事引入 EM。若使用 EM，须在类或相关方法 **JavaDoc** 写明「相对派生/Specification 的必要性」（如性能、派生无法表达的动态谓词等），避免无说明扩散。  
  - **仓储与上浮边界**：`repository` 包内 **Spring Data 自定义片段实现**（`*RepositoryImpl`/`*ReadRepositoryImpl`）若惯例需使用 `EntityManager`，**仅限该实现类自身**；**禁止**将应用层编排改为在 App 内注入 EM 以绕开领域服务（与上款应用层硬禁止一致）。**例外**：`zxplat-domain` 等任务书明示的基础设施；其余与分层冲突的存量须在 `DOC-CONFLICT` 留痕并收敛。
- **禁止** `@Query`/JPQL/SQL；仅派生方法名或上层 JPA API + 代码。
- 涉及批量语义时，`Repository` 层必须提供集合化接口（如 `findByIdIn`、批量 `saveAll`、集合条件更新/删除）；**禁止**服务层循环调用单条 `Repository` 方法拼出批量效果。
- **查询条件必须下推到 DB（硬门禁）**：过滤条件（`isDeleted`、状态枚举、外键等）**必须体现在 Repository 方法签名派生条件中**，在 SQL 层完成过滤；**禁止** `findAll()` 或无过滤查询后在 Java 内存中用 `for`/`if` 手工过滤记录，也**禁止**在 Java 层手工计算分页（`subList`）来替代数据库分页。
- **软删条件命名约束（硬门禁，新增）**：`zxplat-framework` 底座已默认附加 `isDeleted=false` 过滤时，`Repository` 派生方法名中**禁止显式拼接** `AndIsDeletedFalse` / `IsDeletedFalseAnd` / `IsDeletedFalse`；统一使用不含软删片段的方法名（如 `findById(...)`、`existsByRateCode(...)`）。
- **分页必须由数据库完成（硬门禁）**：分页查询须使用 `Pageable` + Spring Data 分页派生方法，将 `LIMIT/OFFSET` 下推到 SQL；**禁止**将全量数据加载到内存后手动截取 `subList` 拼装分页结果——此模式在数据量增大时必然 OOM 且结果不正确。
- **非分页候选集查询须收敛规模（硬门禁）**：凡"候选人重复校验"类查询（如同人校验、同名同性别候选集）必须在 SQL 层保留尽可能多的过滤条件以收敛结果集；**禁止**仅使用软删过滤后将大量数据全量加载到内存再逐条判断，对潜在大候选集须在方法 JavaDoc 中标注规模风险，并考虑增加短路精确匹配路径（如 idCard 精确匹配优先）。
- **候选集查询规模风险标注（硬门禁，新增）**：对于返回集合且无上界约束的 Repository 方法（如 `findAllByNameAndGenderEnum`），必须在方法 JavaDoc 中**显式标注规模风险**："在大医院同名同性别患者可能达数百条，调用方须先尝试精确匹配（如身份证号）做短路，避免全量加载"。禁止无任何风险提醒的高扇出查询方法入库。
- **仓储接口禁止冗余未调用方法（硬门禁）**：`Repository` 接口中禁止声明在服务层（`DomainService`/`QueryService`）中**实际未被调用**的派生查询方法；生成或修订完成后须确认每个接口方法存在至少一处调用点，否则删除——长 JPA 派生方法名若无调用点，既造成维护负担又可能因签名与实际查询需求不符而制造混淆。

#### B.2.1 关键字模糊查询（用户输入驱动，**一律不区分大小写**，硬门槛，zxplat-app 专规）

> 适用于 `zxplat-domain`、`zxplat-domain`、`zxplat-application` 中一切 **JPA Criteria / `Specification` / `@Query` 绑定参数** 等「由用户键入的关键字做子串模糊」场景；与 **B.10.3**（禁业务字面量）、**B.10**（常量入口）一致：**禁止**在 Java 中手写 `"%" + … + "%"`。

- **统一配对（强制）**：凡同一关键字在 `cb.or(...)`（或等价 OR 语义）下参与模糊匹配的**每一个字符串列**，**必须**同时满足：  
  1. **列侧**：`cb.like(cb.lower(root.get(FieldNameConstants.XXX)), pattern)`（JPQL 则 `LOWER(别名.列) LIKE :pattern` 等）；可空列与现有代码风格一致使用 `COALESCE` 包一层再 `LOWER` 亦可。  
  2. **模式侧（唯一来源）**：`String pattern = com.zxplat.domain.share.utils.SearchCodeUtil.toCaseInsensitiveLikePattern(trimmed);`（`trimmed` 为已 `trim` 的关键字）。  
  **禁止**「仅 py/wb 用 `lower` + `toCaseInsensitiveLikePattern`，名称/业务号仍 `LIKE_PERCENT + trimmed` 区分大小写」的双轨写法；**禁止**对 `lower(列)` 传入未小写化的模式串。
- **通配符与 `%`**：`toCaseInsensitiveLikePattern` 内部已用 `GeneralSharedConstants.LIKE_PERCENT` 包边并完成 `Locale.ROOT` 小写化；调用方**不得**再拼接 `%` 或重复 `toLowerCase` 后手写 `%`。
- **Spring Data 派生 `Containing`**：若派生方法涉及用户关键字模糊且需不区分大小写，优先 **`ContainingIgnoreCase`** 或收口为 **`Specification`** + 上条「`lower` + `toCaseInsensitiveLikePattern`」，**禁止**依赖各库默认排序规则造成行为不一致。
- **非 zxplat-app 模块**：若代码已依赖 `zxplat-domain`，**应复用** `SearchCodeUtil.toCaseInsensitiveLikePattern`（与域内一致）；未依赖该模块的 `zxplat-framework` 子模块若需等价行为，须在**本模块已有常量/工具入口**对齐 **`LIKE_PERCENT` + `Locale.ROOT` 小写**语义，**禁止**在业务路径散写 `"%"` 拼接。

### B.3 Flyway 与实体顺序

- Whale 默认目录：`zxplat-app/zxplat-domain/src/main/resources/db/migration/health/`  
- Flyway DDL 属于基础设施层职责，**不是领域层代码生成的前置条件**；生成领域层代码时无需先等 DDL。  
- 禁止 `.sql` 塞进领域 Java 包。当用户明确要求同步生成 DDL 时，顺序为：领域实体先行 → Flyway 增量脚本对齐实体。

#### B.3.1 菜单/权限种子 ID 全局唯一（硬门禁）

> 细则与 BAD/GOOD 见 Cursor 规则 **`zxplat-flyway-menu-seed-ids`**（`**/db/migration/**/*.sql`）。

- `health/` 与 `health-final/` **共用** schema history，且常开 **outOfOrder**：低版本 seed 可能在高版本已写入后执行。  
- `zxplat_permission_grants` 的 `ON CONFLICT (permission_name, provider_name, provider_key)` **不能**消化 **主键**冲突。  
- 新增菜单 seed 前：对拟用的 `permissions` / `menus` / `grants` **三个 id** 在**全部** `db/migration/**` 做全局检索；**禁止**在 `310800…` 上顺序 +1 猜空号。  
- 授权 id 优先独立雪花段（锚点：`V17__seed_inventory_opening_menu.sql`）。  
- **整理 Flyway**（合并脚本、重编号、搬 `health`↔`health-final`、改号段策略）时：**同轮更新**本条锚点与规则 `zxplat-flyway-menu-seed-ids` 中的示例路径/ID，禁止残留失效引用。  

---

### B.4 编排类禁「服务文件内码表」与「实现类内常量」

- 禁止类头 `TYPE_*`、类级 `Set`/`EnumSet` 白名单等；允许 **B.5** 类末少量 `private static` 无状态校验函数（有 JavaDoc、不扛业务字典）。  
- **禁止**在 Service / DomainService / QueryService / Manager 等实现类中声明 `private static final Pattern`、`private static final String`、`private static final int` 等任何形式的**字面量常量字段**；此类常量必须外移到 **B.10** 约定的常量类（按需新建），实现类仅通过类名引用。  
- 正则表达式（`Pattern.compile(...)`）不得在类初始化时内联写死；必须定义为 `constants` 包对应接口中的字段。

#### B.4.1 正则表达式性能门禁（硬门槛，新增）

> 适用于所有需要正则校验的业务代码。

- **禁止在方法体内使用 `Pattern.matches(regex, input)` 或 `String.matches(regex)`**：这两种调用每次都会隐式执行 `Pattern.compile(regex)`，造成每次调用都重新编译正则，在批量操作场景下产生严重的性能开销（O(N) 编译成本）。
- **正确做法**：在 `constants` 包对应接口中预编译为字段（如 `GeneralSharedConstants.PHONE_PATTERN`），类型为 `Pattern`：
  ```java
  Pattern PHONE_PATTERN = Pattern.compile(PHONE_REGEX);
  ```
  业务代码使用 `GeneralSharedConstants.PHONE_PATTERN.matcher(input).matches()`（或通过 import static 引用该字段）。
- **自检要求**：生成完成后搜索代码中所有 `Pattern.matches` 和 `.matches(` 调用，确保无隐式重编译正则；发现违规须立即修正。

### B.5 类内成员顺序

- 编排类：**字段 → 构造器 → public/protected 实例 → static → private 实例**；禁止 `private static` 插在 public 之间。

#### B.5.1 编排类 `private` 方法拆分纪律（硬门槛，新增）

> 适用于 `*AppService`、`*DomainService`、`*QueryService`、获准改的 `*Manager` 及 `*MapperImpl` 中的流程编排与展示装配。**目标**：主流程可读、类内方法表不碎片化；与 **B.6.1**（跨类提取到 `module.common`）互补，**不**鼓励「私有方法越多越好」。

**默认（强制）**

- 单个对外 IF / 领域入口的 **Gate → Pre → Cmd → Asm** 主干，**优先**写在对应 `public` 方法体内，用 **A.4** 业务向 `//` 分段；**禁止**为「类文件变短」「每个 if 一块」「每个 DTO 字段一行」而拆出仅调用 **1 次** 的 `private`。
- **禁止**用大量 `private` 替代：第二个 `@Service`、包内协作 Bean、`common.service.*`（见 **B.6.1**）；也**禁止**把本可内联的展示回落（如名称三档、单行枚举转展示）拆成十几个 `resolve*` / `build*` / `apply*` 且各仅一处调用。

**允许拆出 `private` 的情形（须满足其一，且拆出后调用方能自上而下读完主流程）**

| 情形 | 条件 | 说明 |
|------|------|------|
| **真复用（同类）** | 同一实现类内 **≥2 处**调用同一段逻辑 | 逻辑块宜 **≥8 行**有效代码（不含 JavaDoc）；方法名须表达**业务块**（如 `syncAdviceStatesAfterChargeSettlement`），禁止 `step1` / `doValidate` |
| **真复用（跨类）** | **≥2** 个 `AppService` / `MapperImpl` 同构 | **必须**提取到 **B.6.1** `module.common`，**禁止**在各业务类各留一份 `private` |
| **可读性瓶颈** | 单条 `public` 主干（含 Gate/Pre/Cmd 连续步骤）**>约 80～100 行**（含必要 `//`） | 仅拆出**语义完整**的阶段块（如「整段预结算门禁」「整段待收费行装配」）；拆后 `public` 方法应像目录一样 **10～20 行内**能看清步骤顺序 |

**禁止拆分的情形（硬门禁）**

- 仅调用 **1 次**，且有效逻辑 **<约 15 行**（不含 JavaDoc）的 `private`（单行 `assert`、单次 `setXxx`、单次 `EnumUtil.fromCode` 等须留在调用处）。
- 把每个 `assert*`、每个校验分支、每个 Mapper 单行调用各拆一个 `private`。
- 仅为满足「每个 `private` 都要有 JavaDoc」而把无独立业务含义的代码块提成方法（注释应落在主流程 **A.4** `//`，见 **B.1**）；**不得**用新增 `private` 规避 JavaDoc 义务（见 **B.5.1.1**）。
- 以「减少 `public` 方法行数」为由拆 `private`，导致阅读 IF 实现需在类末来回跳转 **>5 个** 仅单次使用的 `private`。
- **薄包装 / 语法糖 privatization**（无论是否仅 1 次调用）：见 **B.5.1.1** 全文，**一律内联**。

##### B.5.1.1 禁止薄包装 `private`（硬门禁，P0）

> 与 **B.5.1**「禁止拆分」配套。**问题形态**：为「类看起来整齐」「每个 private 都有 JavaDoc」「方法名像领域语言」而把**无独立业务命题**的 1～3 行代码提成 `private`，反而迫使读者在 `public` 主干与类末之间跳转，可读性低于直接内联。

**薄包装定义（满足任一条即属薄包装，禁止新建）**

- 方法体（不含 JavaDoc、`//`）实质仅为：**单行** JDK/框架调用（如 `LocalDateTime.now()`、`UUID.randomUUID()`）、**单行** null/空串回落、**单行** `repository.findById(...).orElseThrow(...)`、**单行** `EnumUtil.fromCode` / `requireFromCode`、**单行** `entity.setXxx(...)` 或 `list.add(...)` 的组合（合计有效代码 **≤3 行**）。
- 方法名**不能**用一句话向产品/审查者说明「违反了哪条业务不变量或完成了哪段编排阶段」——仅是对调用处语法的改名（如 `domainNow`、`defaultNow`、`nowAt`、`loadOne`、`resolveCode`）。

**典型反例 → 正确写法（禁止左侧模式）**

| 禁止（薄包装） | 必须（内联） |
|----------------|--------------|
| `private LocalDateTime domainNow() { return LocalDateTime.now(); }` | 调用处直接 `LocalDateTime.now()`，或沿用 Command/事件已传入的时刻字段 |
| `private LocalDateTime defaultNow(LocalDateTime t) { if (t == null) { return LocalDateTime.now(); } return t; }` | 调用处显式 `if-else`（**A.8** 禁三元）；或 `Objects.requireNonNullElse(t, LocalDateTime.now())` **写在调用处**，不单独提方法 |
| `private Encounter requireEncounter(Long id) { return repo.findById(id).orElseThrow(...); }` 且 **仅 1 处**调用 | 该处直接 `orElseThrow` |
| `private XxxStatus resolveStatus(String code) { return EnumUtil.fromCode(XxxStatus.class, code); }` 且 **仅 1 处**调用 | 该处直接 `EnumUtil.fromCode` |
| `private void appendActiveLocation(...)` 仅 `new` + `set*` + `list.add` **≤3 行**且 **仅 1 处**调用 | 写入 `public` 主干对应步骤，用 **A.4** `//` 标业务含义 |

**命名红旗（成码时见到即复核，默认应内联）**

- `domainNow` / `nowAt` / `currentTime` / `defaultNow` / `orNow` / `coalesceNow`
- `loadOne` / `loadX` / `requireX` / `fetchX`（体仅为 `findById` + `orElseThrow` 且单调用点）
- `resolveX` / `buildX` / `applyX` / `mapX`（体仅为单次枚举解析、单次 setter、单次 DTO 字段拷贝且单调用点）

**仍允许保留的 `private`（须能说出业务块名称，且通常 ≥8 行或 ≥2 处调用）**

- 具名 **BR/不变量** 校验块：`assertNoOpenInpatientForAmb`、`assertNurseStationPatientSavable`（含仓储查询 + 多条件 + `BusinessException`）
- **整段**子表/位置/参与者同步：`completeActiveHouseAndBedLocations`、`saveAdmissionDx`（多实体、多仓储、事件前状态变更）
- 同类 **≥2 处**调用的提取（或应迁 **B.6.1** / `domain.share.utils`，见 **B.10.6**）

**与 B.1 JavaDoc 的优先级（硬门禁）**

- 「每个 `private` 要有 JavaDoc」**不等于**「把每行逻辑都包成 `private` 再写 JavaDoc」。
- **禁止**占位 JavaDoc：`/** 私有方法：defaultNow。 */`、`/** 业务分支处理。 */` 且无 `@param`/`@return` 实质——此类方法**应删除并内联**，注释改为主流程 **A.4** `//`。
- 若 `public` 主干因内联变长，用 **A.4** 步骤注释与阶段空行提升可读性，**不要**用薄 `private` 换行。

**成码自检（强制，本轮 touched 编排类）**

1. 列出本轮新增/修改的 `private` 方法：体 **≤3 行**有效代码且调用点 **=1** → **合并回**调用处（**B.5.1.1**）。
2. 检索类内是否出现 `domainNow`、`defaultNow`、`nowAt`、`orNow` 等命名；若体为 `now()`/null 回落 → **删除方法、内联**。
3. `private` 的 JavaDoc 若仅复述方法英文名、无业务不变量说明 → 视为违规薄包装信号，优先内联而非补一句废话 JavaDoc。
4. 与 **B.5.1** 软上限、`B.6.1` 收口自检一并执行。

**软上限（成码自检，非机械否决）**

- 新建或大改单个 `*AppService` 时，`private` 实例方法数宜 **≤ public 接口方法数的 1 倍**；明显超出时，须逐项对照上表说明属于「真复用」或「可读性瓶颈」，否则应**合并回** `public` 主干。
- 存量类（如已存在大量 `private` 的财务 AppService）**不在**默认重构范围；**增量**不得继续堆砌仅单次使用的 `private`，除非任务书明确要求整理。

**与其它条款关系**

- 跨场景复用 → **B.6.1** `module.common`（assembler / validator / converter）。
- 注释密度 → **A.4** / **B.1**（主流程 `//` 优先，不靠增加 `private` 数量凑注释位；薄包装见 **B.5.1.1**）。
- 可读性 → **A.8**（内联 `for`/`if-else` 优于碎片化 `private` 链式跳转）。
- 薄包装 → **B.5.1.1**（`domainNow`/`defaultNow`/单行 `resolve*` 等 **P0 禁止**）。

### B.6 Whale 模块与包职责（硬门槛，zxplat-app）

> 真源：`docs/架构/模块化开发规范.md` §5。领域层 `*DomainService` 与 `*QueryService` 同包 `domain/.../service`；**禁止**新建 `readmodel` 包。

| Maven 模块 | Java 包（示例） | 放什么 | 禁止 |
|------------|----------------|--------|------|
| **zxplat-domain** | `domain.share.{constants,enums,utils,dto,events}` | 常量、枚举、无 Spring 工具 | Spring Bean |
| **zxplat-domain** | `domain.{boundedContext}.*` | 实体、Repository、`*DomainService`、`*QueryService`、Command | 跨聚合读组装（见 B.6.4） |
| **health-module-support** | `module.common.*` | 跨场景 validator / assembler / pagination / converter / dto | HTTP 契约、`*Constants`、**写编排** |
| **health-module-support** | `module.querysupport.{域}` | `*Loader`、`*ReadAssembler`、`EncounterContextFacade`、读侧 dto | `Repository`、**写路径**、`querysupport.service.*` |
| **health-module-support** | `module.orchestration.{域}` | **跨 application 子包**写编排（调 `*DomainService`）；入参 Command/Spec/model | 依赖 `zxplat-application`、Request/Response、`@Transactional` |
| **health-module-support** | `module.shared.*` | 诊断 JSON 解析（`DiagnosisJsonSnapshotParser`）、Whale SPI、OU 桥接 | 业务 IF 编排 |
| **zxplat-application** | `application.{basic,inpatient,outpatient,inventory}/**` | IF 专属 request/response/dto/mapper/service、**单子模块** `{IF}/{子模块}/support | 父级 `*.support；**跨子包 import `*/support**；`common`/`platform`/`querysupport/`Report 子包 |
| **report-adapter** | `com.zxplat.domain.application.report.*` + `com.zxplat.framework.report.adapter.*` | 打印数据集、DataReader 适配 | **禁** `zxplat-application`、`report-application` |

**装配**：`health-module-support → `HealthModuleSharedAutoConfiguration`（扫 `module.shared` + `module.*`）；`zxplat-app` 仅扫 **`com.zxplat.domain`**。

### B.6.1 `module.common`

- **位置**：`health-module-support → `com.zxplat.domain.module.common.*`
- **上收门槛**：≥2 个 AppService/MapperImpl 同构 → 必须提取；单处使用 → 内联（B.5.1）。
- **常用组件**：五类 Resolver、`PageWindowGate`、`PatientEncounterConsistencyGate`、`PagedResultBuilder`、`OrderRequestCatalogWarmAssembler`、`TerminologyOptionLoader` 等。
- **禁止**：`module.common.service.*`、`*Loader`/写编排、业务常量（→ `domain.share.constants`）、注入 `*DomainService`/`*Repository`/`*QueryService`（术语白名单除外）。

### B.6.2 对外 JSON 禁止透出（硬门槛）

> 与 §0.17 同档。约束 **HTTP 出参**；库表/事件内 JSON 列仍允许存 JSON。

- Response/DTO **禁止** `*Json` 整段 `String` 下发；须 Mapper/Assembler 解析为结构化 DTO（诊断列表经 `DiagnosisJsonSnapshotParser`，位置三级名等）。
- **例外**：用户明示「用 JSON 返回」或 AS/AS-SDD 单列豁免；存量兼容须在 `DOC-CONFLICT` 标注待迁移。
- **禁止**：AppService `writeValueAsString` 拼业务 Response；QueryModel 有什么 JSON 列就原样 set 给前端。

### B.6.3a `module.orchestration`（跨 application 子包写编排）

> **命名**：**禁止** `module.workflow`（与 `com.zxplat.domain.workflow.*` 聚合混淆）。

- **位置**：`health-module-support → `com.zxplat.domain.module.orchestration.{advice|nursestation|financial|encounter|…}`
- **职责**：≥2 个 `zxplat-application` 子包复用的写编排（调 `*DomainService`）；与 IF 无关的中性流程 Bean。
- **允许**：注入各 DM `*DomainService`、写路径 Gate 所需 `*QueryService`；入参/出参用 Command、`module.common.assembler.*.model`、简单值对象。
- **禁止**：`import com.zxplat.domain.application.*`；`Repository`/`EntityManager`；`@Transactional`。
- **调用纪律**：各 `*AppService` 注入 `module.orchestration.*`；**禁止** `application` 子包互调 `*/support。

### B.6.3 `module.querysupport（跨聚合只读）

- **位置**：`health-module-support → `com.zxplat.domain.module.querysupport.{encounter|product|order|advice|pharmacy|financial|administration|procedure}`
- **职责**：`*Loader`、`*ReadAssembler`、`EncounterContextFacade`；读侧 dto 放 `querysupport.{域}.dto`。
- **允许**：注入各 DM `*QueryService`；委托 `module.common.assembler` 做术语/展示回填。
- **禁止**：`Repository`/`EntityManager`、写编排、`querysupport.service.*`、应用层 `*QueryService` 命名。
- **Loader 清单与迁移口径**：见架构定稿 §5 与 `ReadModel弃用迁移/10-就诊上下文读组装-禁止领域层Support.md`。

### B.6.4 领域层禁止跨聚合（P0）

- `*QueryService` / `*DomainService` / `*PageQuerysupport **只访问本聚合** `*Repository` 与实体。
- **禁止**：注入他聚合 `*QueryService`/`*Repository`；Criteria/EM 跨根 `exists`/`join`/`subquery(...).from(OtherAggregate)`。
- **正确**：`module.querysupport.*Loader` 分步查各聚合 → 内存合并 id → 本聚合 `*QueryService` 用 `IN (...)` / `resolved*Scope` 入参。
- **岗位/就诊收窄**：禁止领域层 `platformRoleId` EXISTS；须 Loader 预解析 `resolvedPractitionerIdsForRole` 等再传入 QueryService。

### B.6.5 登录会话与五类 Resolver（P0）

- **AppService / Loader / MapperImpl 禁止** `ICurrentUser` 与 `getCurrentUserId()` 等直读；**唯一封装点** `module.common.validator.*Resolver`。
- **主键空间（禁止混用）**：`zxplat_users.id`→操作人；`adm_practitioner.id`→医务人员；`adm_organization.id`→科室；`adm_location.id`→库房/病区（location ≠ org）。
- **五类 Resolver**：`CurrentOperatorResolver`、`CurrentPractitionerResolver`、`CurrentManagedOrganizationIdsResolver`、`CurrentManagedLocationIdsResolver`、`CurrentManagedWardLocationIdsResolver`——写用 `require*`，读用 `find*`/`list*OrEmpty`/`resolveOrganizationUnitIdForQuery`。
- **Gate 复用**：患者一致且 AS errorCode 相同 → `PatientEncounterConsistencyGate`；AS 规定分页必填 → `PageWindowGate`；分页出参 → `PagedResultBuilder`（禁止 `getNumber()+1`）。
- **增量纪律（本轮新增代码）**：

| 场景 | 必须 | 禁止 |
|------|------|------|
| 写路径 operatorId | `CurrentOperatorResolver#requireOperatorId` | `getCurrentUserId()` + null 判断 |
| 读路径机构收窄 | `CurrentManagedOrganizationIdsResolver` | Request 传 `organizationId` 伪造范围 |
| 跨场景写 | `*DomainService` / `module.shared.identity` SPI | `module.common.service.*`、Loader 写编排 |
| 跨场景读 | `module.querysupport.*Loader` | 领域 `*Querysupport、App 内复制跨表编排 |

**成码前检索（应用层）**：先查 `module.common` / `module.querysupport 是否已有可复用 Bean，再写 `private` 或新建类。

### B.6.6 `support 落点纪律（P0，zxplat-application / health-module-support）

> **禁止**在 `inpatient`、`outpatient`、`doctorstation`、`nursestation` 等**父级包**随意新建 `support 目录堆放编排类。  
> Skill 速查表见 [SKILL.md §2.2.1](../SKILL.md)。

#### B.6.6.1 什么是 `support

| 类特征 | 是否 `support | 说明 |
|--------|----------------|------|
| 被 ≥2 个 AppService 复用的 Gate/编排 Bean | 是 | 须按 B.6.6.2 选包 |
| 纯函数工具、无 Spring、无跨聚合 I/O | 否 | `domain.share.utils` 或子模块内 `private static` |
| 单 AppService 内 ≤3 步校验/映射 | 否 | AppService 内联 + `//`（B.5.1） |
| 跨聚合只读 Loader/Assembler | 否（不叫 application support） | `module.querysupport.{域}` |
| 跨顶层场景展示 Assembler | 否 | `module.common.assembler.{域}` |

#### B.6.6.2 落点决策（新增代码强制）

```text
是否只读？
├─ 是 → 是否跨聚合？
│        ├─ 是 → module.querysupport.{encounter|order|nursestation|…}
│        └─ 否 → 是否 ≥2 个顶层场景 AppService 复用？
│                 ├─ 是 → module.common.assembler / validator
│                 └─ 否 → 拥有者子模块内联或 querysupport（按 AS-SDD）
└─ 否（写编排）→ 是否 ≥2 个 application 子包复用？
                 ├─ 是 → module.orchestration.{advice|nursestation|financial|encounter|…}
                 └─ 否 → 唯一调用方 application/{IF}/{子模块}/support
```

#### B.6.6.3 禁止路径（新增）

| 包路径模式 | 禁止原因 |
|------------|----------|
| `application.inpatient.support | 无 IF 归属 |
| `application.outpatient.support | 同上 |
| `application.*.doctorstation.support | 过宽 |
| `application.*.nursestation.support | 过宽 |
| **`application.*` 互调对方子包 `*/support** | 须改 `module.orchestration` |
| `module.workflow.*` | 与 **domain.workflow** 聚合混淆 |
| `module.querysupport.*` 注入 `*DomainService` 并写库 | querysupport 只读 |
| `module.common.*` 写编排调 `*DomainService` | common 禁写编排 |
| `module.orchestration.*` import `application.*` | 模块依赖倒置 |

#### B.6.6.4 正例（已对齐 / 迁移目标）

| 类 | 落点 | 理由 |
|----|------|------|
| `NurseStationEncounterVisibilitysupport | `module.querysupport.nursestation` | 只读 Gate |
| `OrderTemplateLineCatalogEnrichAssembler` | `module.querysupport.ordertemplate` | 只读 enrich；AppService 边界映射 `OrderTemplateLineEnrichRow` |
| `InpatientAccountBalanceDisplayDtoApplier` | `module.common.assembler.inpatient` | 只读展示 |
| `ServiceAdviceCommandAssembler` / `OrderChargeDraftAssembler` | `module.common.assembler.advice` | 只读 Command 组装；Mapper 委托 |
| `AdviceSignTargetSpec` / `SkinTestMedicationDraftSpec` | `module.common.assembler.advice.model` | orchestration 中性入参；AppService DTO↔Spec |
| `PositionPractitionerRoleWriteGate` / `OrderTemplateScopeResolver` | `module.common.validator` | 只读 Gate/Resolver |
| `BloodPressureParser` | `domain.share.utils` | 纯函数；无 Spring |
| `DoctorStationSkinTestSyncOrchestrationsupport | `module.orchestration.advice` | 写编排；门诊+住院皮试 companion |
| `ServiceApplyFormOrchestrationsupport | `module.orchestration.advice` | 写编排；门诊+住院医嘱 |
| `OutpatientMedicationSignChargesupport | `module.orchestration.advice` | 写编排；西药+中医签发 |
| `DeviceDispenseNurseWorkflowsupport | `module.orchestration.nursestation` | 写编排；执行+划价+取消+门诊处置 |
| `AdmissionVitalSignsupport | `module.orchestration.nursestation` | 写编排；入院体征写入 |
| `EncounterInpatientAdmissionWritesupport | `module.orchestration.encounter` | 写编排；入院登记跨聚合 |
| `InpatientTcmDecoctionExecutesupport | `application.../orderexecute/support | 写编排；**仅**执行子模块 |

#### B.6.6.5 存量迁移

- 命中 B.6.6.3 禁止路径的**存量** `support：非本轮任务不强制搬迁；**本轮 TOUCHED 禁止复制**该模式到新文件。
- 用户/任务书点名搬迁时：按 B.6.6.2 迁包并全量改 import，删空目录；跨子包 `support **优先**迁入 `module.orchestration.{域}`。
- **已完成的典型搬迁**（供对照，非穷举）：
  - 跨住院↔门诊 `*/support → `module.orchestration.{advice|nursestation|encounter}`
  - 只读 enrich（组套模板行）→ `module.querysupport.ordertemplate` + AppService DTO↔Row 边界
  - 纯函数（血压解析等）→ `domain.share.utils`
  - Command 组装器（Mapper 委托）→ `module.common.assembler.advice`
  - 单处 ≤3 步过滤（护理记录保存）→ AppService 内联 private，**删**独立 Support 类

### B.7 `*Manager` / `*QueryManager` 冻结

- **存量文件场景**：非本轮点名修改则 **整文件禁止编辑**。
- **空框架场景**：目标工程中不存在 Manager 文件时，不存在冻结对象；若 DM/SDD 无明确指示，本轮不生成 Manager，新能力落在 `{聚合}DomainService` / `{聚合}QueryService`。

### B.8 声明式事务注解（`@Transactional`）— **全文禁止**

- **禁止**在任意业务代码上使用 `@Transactional`（含 AppService、DomainService、QueryService、Manager、support 等；**无**任务书豁免）。
- **AppService 写路径**：框架 **`AppServiceAspect`** 以编程式事务为每个 `*AppService` 公共方法自动开启/提交/回滚（UoW，`PROPAGATION_REQUIRED`）；**业务侧无需、禁止**再写事务注解。
- **AppService 读路径**（`get*`/`list*`/`find*` 等）：由 **`ReadOnlyTransactionAspect`** 自动只读事务。
- **领域/支撑类**：参与调用方 AppService 已开启的 UoW；**禁止**在 DomainService、QueryService、support 等再声明事务边界。
- **批次/单条失败隔离**：在 AppService 编排层 **catch 后不向上抛** 即可继续后续条目；**禁止**为「独立提交」在业务代码引入 `@Transactional(REQUIRES_NEW)` 或自建编程式事务工具。
- **`@TransactionalEventListener`** 不受本条禁止（事件阶段监听，非事务边界注解）。

### B.9 JPA 实体

- **禁止** BV、`@ColumnDefault` 等在实体上堆砌；校验在领域方法；默认值走迁移/DB。

### B.10 常量与类型码（zxplat-app 专规）

> **总闸索引**：枚举 / 字典 / 状态 / 类型在库与事件中的**身份一律 `getCode()`（String）**，禁止数字枢纽——先读 **§0.1.1、§0.12**，再读本节子条款。

- **枚举与常量两条路并存，必须先决策再落地**；**禁止**新建 `*TypeCodes` 薄包装。
- **决策入口**：见下方 **B.10.0**；确定归属后再按 **B.10.1 / B.10.2** 落位。
- **常量包结构（强制）**：非枚举共享常量位于 `com.zxplat.domain.share.constants`，按语义拆分为多个顶层 `public interface XxxConstants` 源文件（如 `GeneralSharedConstants`、`AggregateTypeConstants`、`FieldNameConstants`、`TableNameConstants`、`ErrorCodeConstants`、`ChargeItemDmConstants`、`TerminologyCodeSystemNameConstants` 等）。新增常量归入语义最接近的现有接口；确需新主题时新增同级 `*Constants.java`，**禁止**再合并为单一大类承载全部常量。

#### B.10.0 枚举 vs 常量决策规则（硬门槛，zxplat-app 专规，新增）

> 本节仅适用于 `zxplat-app` 模块；其他工程以任务书约定为准。

在新建任何状态码、类型码、分类码之前，**必须先通过以下决策表确定归属**，禁止"按当前类方便"直接用某 `*Constants` 内的 `XxxStatus` 式 interface 分组承载业务状态集合。

| 判断条件 | 正确归属 | 典型示例 |
|---------|---------|---------|
| **有限个业务值集合**，具有领域语义，跨聚合 / 跨服务可复用，或未来可能需要在代码中基于枚举值做行为分派 | **`zxplat-domain/enums/XxxStatus.java`（Java enum）** | `OrganizationStatus`、`LocationForm`、`PaymentStatus`、`HealthcareServiceStatus`（待迁移） |
| **仅在单一服务/方法内部**用于路由/分发的离散标记，无跨域业务含义，且**不构成**枚举/字典/状态身份 | `constants` 包内**小型**专用接口分组，**只许明文字符串常量**（如 `"PUBLISH_BY_CHARGE"`）；**禁止**用 `int`/`Integer` 或纯数字 `String` 作分支键；**禁止**冒充领域状态集 | 与 **§0.1.1、§0.12** 一致；历史若存在整型路由，增量不得扩大，收敛为字符串或枚举 `getCode()` |
| 格式字符串、业务编号前缀、正则表达式、通用分隔符、批量上限等**非状态类**通用项 | `GeneralSharedConstants`（或语义最接近的通用 `*Constants`） | `PHONE_REGEX`、`BATCH_LIMIT`、`MedicationCommonConstants` 中的前缀等 |
| 聚合专属错误码 | `ErrorCodeConstants` | `PATIENT_NOT_FOUND`、`ENCOUNTER_INVALID_STATUS` |

**强制执行规则（逐条）**：

1. **业务状态集合必须是枚举（硬门禁）**：凡属于"某聚合/实体的生命周期状态"（如草稿/生效/停用/废弃），**必须**以 Java `enum` 落在 `zxplat-domain/enums/` 包中；**禁止**以某 `*Constants.XxxStatus` 式 interface + `int` 常量的形式实现，即使该状态集与已有枚举存在 DB 码值冲突（DOC-CONFLICT 时应建独立新枚举，不应退化为 interface 常量）。

2. **枚举路径（zxplat-app 专规）**：新建枚举统一落在  
   `zxplat-domain` → `com.zxplat.domain.share.enums.XxxStatus`（或对应语义名）；  
   枚举每个常量**必须有中文 `/** */` JavaDoc**（说明业务含义）；**持久化与对外契约身份一律 `getCode()`**（见 **§0.1.1、§0.12、B.10.7**），**禁止**以整型字段作为该类枚举的数据库或事件身份。

3. **禁止枚举镜像常量**：`enums/` 下已存在枚举时，**禁止**再在任一 `*Constants` 接口中重复维护同码值整套 `STATUS_*` 常量；业务代码直接引用枚举。

4. **存量 `*Constants.XxxStatus` 式 interface 的处置原则**：若发现存量中已有违规的"状态 interface 常量"，**本轮若未被要求迁移则维持现状 + DOC-CONFLICT 留痕**；若本轮被点名修改，则须同步迁移为枚举并全仓替换引用。

5. **自检**：生成完成后在 `constants` 包各 `*Constants` 接口中搜索所有形如 `XxxStatus`、`XxxState`、`XxxPhase` 的分组，逐一核对是否符合「非枚举身份的内部明文字符串路由」条件；不符合则报告为遗留 DOC-CONFLICT，下轮迁移至枚举。

#### B.10.7 `EnumInterface` 枚举模板统一（硬门槛，zxplat-app 专规）

> 适用于 `zxplat-app/zxplat-domain` 的 `com.zxplat.domain.share.enums` 目录内所有业务枚举。  
> **与 §0.1.1、§0.12 对齐（总闸）**：业务枚举 / 字典 / 状态在**库表与事件 JSON 中的身份仅为 `getCode()`（String）**；**禁止**为新建枚举/字典设计数值型库列，**禁止**在应用层以 `getValue()`、`Integer`、`parseInt`、纯数字串与 `code` 互转表达同一身份。

**目标**：保证「**持久化身份（`code`）**、**对外编码**、**前端展示（`info`）**」边界清晰且全仓一致；`value` / `sortOrder` 若存在，**不得**再承担字典或状态存值语义。

- **统一接口**：目录内所有业务枚举 **必须** `implements EnumInterface`
- **统一枚举项三参**：所有枚举常量 **必须**形如 `XXX(value, code, info)`
  - **value**：`Integer`，**仅**兼容存量模板与**非身份**用途（如展示排序、历史报表序号）；**禁止**作为新建 Flyway 列或事件载荷中枚举/字典/状态的主存值；**禁止**在业务分支中与 `code` 互转替代身份判断
  - **code**：`String`，**唯一**的持久化身份、查询条件、事件载荷键值（与 DM/SDD 字面量一致）
  - **info**：`String`，用于展示；**必须中文**
- **统一字段**：每个枚举 **必须**声明三字段且类型固定：
  - `private final Integer value;`
  - `private final String code;`
  - `private final String info;`
- **统一构造器**：每个枚举 **必须**提供三参构造器 `Xxx(Integer value, String code, String info)` 并对三字段赋值
- **统一方法**：每个枚举 **必须**显式实现三方法（不要仅依赖 Lombok 生成以保证可读性与一致性）：
  - `public Integer getValue()`
  - `public String getCode()`
  - `public String getInfo()`
- **解析/比较规则（关键）**：  
  - **身份与字典语义**：**只**比较或持久化 **`getCode()`**；统一用 **`EnumUtil.fromCode` / `requireFromCode`**（见 **B.10.8**）。**禁止**手写 `fromCode(Integer)` / `getByValue` 类工厂在业务路径复活整型身份。  
  - **禁止**把 `getValue()` 当字符串拼接进 `VARCHAR` 枚举列；**禁止** `getCode().equals(Integer)` 等混型比较。  
- **常量注释**：每个枚举常量须有中文注释（JavaDoc 或行注释），`info` 与注释语义应一致；不得用枚举名/英文占位充当展示文案；若保留 `value` 字段，须在枚举类 JavaDoc 写明「**非持久化身份，不得用于状态/字典列**」

#### B.10.8 枚举按码解析统一入口（硬门槛，zxplat-app 专规）

> 适用于 `com.zxplat.domain.share.enums` 下 **implements `EnumInterface`** 的业务枚举。

- **禁止**在枚举类上再写静态工厂 `public static Xxx fromCode(...)`（含手写 `for (values())` 比对 `getCode()` / `getSortOrder()` 等与 `EnumUtil` 重复的解析逻辑）。  
- **统一入口**：`com.zxplat.domain.share.utils.EnumUtil`  
  - **枚举 / 字典 / 状态身份（默认且强制）**：与库表字符列、事件字符串字段对齐时，**只**使用 **`EnumUtil.fromCode(Class<E>, String)`** / **`requireFromCode`**（`fromCode` 找不到返回 `null`，`requireFromCode` 抛 `IllegalArgumentException`，与既有工程约定一致）；**禁止**以 `fromSortOrder` / `getValue()` 路径承载上述身份（见 **§0.12、B.10.7**）。  
  - **`fromSortOrder` / `requireFromSortOrder`（收窄解释）**：**仅**允许用于**已存在**且列语义为「排序权重 / 非字典非状态」的存量兼容；**禁止**在新需求中把 `Long sortOrder` 或整型当作枚举或字典身份；**禁止**在业务服务内用其与 `code` 互转。  
- **JPA AttributeConverter** 等已使用 `EnumUtil.fromCode` 的路径保持不变，禁止旁路再造一轮私有解析。  
- **例外（须在枚举 JavaDoc 明示语义）**：若某枚举必须定义「未知码回落默认常量」「空串与 null 等价」等与工具类默认语义不一致的解析策略，可保留**非 `fromCode` 命名**的静态工厂（如 `resolvePersisted`），且体内须优先调用 `EnumUtil`，禁止复制比对循环；无此类语义则不新增。

#### B.10.8.1 枚举持久化与展示口径（硬门槛，zxplat-app 专规，新增）

> 与 **B.10.7**（`value` / `code` / `info` 三分工）及 **B.10.8**（`EnumUtil` 统一解析）配套：**落库身份**与**人读展示**分离。**本框架成码默认口径**：凡以字符列承载的业务枚举语义，**身份就是 `getCode()` 字符串**（如 `"COMPLETED"`、`"ACTIVE"`），**禁止**用「数字字符串 ↔ code / 英文名」做回转或映射层偷懒。

- **字符串类持久化列（强制）**：凡库表 `VARCHAR`（及同类字符型）/事件 JSON 字符串键/对外契约中以**字符串**承载的枚举语义字段，**落库、更新、查询条件、QueryService 组装**均**直接与 `getCode()` 对齐**，作为跨版本稳定的主真相；**禁止**在该类列中存 `getInfo()` 中文展示文案作为主值。
- **禁止数字串冒充与「串数字 ↔ code」回转（硬门禁）**：**禁止**在领域/应用/QueryService路径上，以 `"1"`、`"3"`、`"01"` 等**纯数字串**表示枚举身份后再转 `ACTIVE`/`COMPLETED` 等；**禁止** `Integer.parseInt` / `String.valueOf(int)` / `valueOf` 链在字符列与枚举之间周转。**不存在**「先约定数字再映射成 code」的成码模式——字符列上**只读写枚举 `getCode()`**（与 DM/SDD 约定的 code 字面量一致）。
- **展示文案（强制）**：接口出参、页面、报表等需人读文案时，**统一**先用 `EnumUtil.fromCode` / `requireFromCode` 解析为枚举常量，再取 **`getInfo()`**（或与 **B.10.7** 约定一致的展示 getter）作为 display；**禁止**在业务编排中手写「数字/序号 → 中文」平行 `switch`/`Map` 重复维护同义项。
- **整型列（仅存量只读兼容，禁止扩散）**：若既有库列**历史原因**仍为整型且与 **`getValue()`** 等对齐，**仅**允许在 **JPA `AttributeConverter` 或单一映射层**收口，**禁止**在领域服务 / 应用服务 / 领域事件 / Mapper 中散落 `parseInt` ↔ `toString` ↔ `code` 链路。**本轮新建或扩展的枚举 / 字典 / 状态字段一律字符列 + `getCode()`**，**禁止**新增「整型枚举列」「数字串 VARCHAR 枚举列」或「字符串数字」中转链路；与 **§0.1.1、§0.12** 冲突的存量须在 `DOC-CONFLICT` 单列迁移计划。
- **新增能力收口**：新建 Flyway 与实体字段**只**接受 **字符列 + `code`**；不得再以「先落整型再映射」规避枚举建模。
- **服务申请状态收口（硬门禁，zxplat-app 专规）**：`wkf_service_request.status_enum`（及同类「非药品医嘱服务申请」写模型/查询/投影中对请求生命周期状态的比较与赋值）**必须**直接使用 **`com.zxplat.domain.share.enums.RequestStatus`** 的 **`getCode()`**（如 `RequestStatus.ACTIVE.getCode()`）。**禁止**新建 `ServiceRequestPersistedStatusCodes`、`ServiceRequestStatusCodes`、`*RequestStatusMirror` 等在 `constants` 包内**重复镜像**该枚举的字符串常量接口；若 DM/SDD 另命名「服务申请状态集」，仍须映射到既有 `RequestStatus` 或先在 `enums` 扩展枚举并经 **B.10.0** 决策，不得退化为常量接口字典。
- **领域事件载荷携带状态（与 B.15 配套）**：写侧在状态迁移并 `save` 之后发布的事件中，凡 QueryService 结果需携带 `status_enum` / `*Status*` 列的，**须在事件载荷中显式携带**与写模型一致的持久化 `code`（字符串）；领域事件 **`model.set…(event.get…())`**，禁止在领域事件处理（已废止）内用枚举常量或字面量「猜测」目标状态。

#### B.10.1 常量最小化与分组优先级（硬门槛，zxplat-app 专规）

> 本节仅适用于 `zxplat-app` 模块；其他工程以任务书约定为准。

- **只建本轮被引用的常量**：本次交付中没有直接引用（编译路径、方法路径均未使用）的常量，**禁止预置/提前创建**。  
- **先复用后新增**：先查 `constants` 包现有 `*Constants` 接口；已有同义常量必须复用，禁止同义重复命名（如同一状态码重复定义在多个接口）。  
- **通用优先**：可跨领域复用的常量（通用正则、批量上限、公共分隔符等）优先放 `GeneralSharedConstants` 或语义最接近的通用 `*Constants`，**不要下沉到仅某聚合命名的专属接口**。  
- **领域分组从严**：领域专属常量接口仅承载该聚合独有且无法通用化的项（如专属业务键）；能归并到通用接口的一律归并。  
- **禁止枚举镜像常量**：已存在枚举（如状态、标识状态）时，禁止再在常量类重复维护同码值整套 `STATUS_*` / `IDENTIFIER_STATE_*`，应直接引用枚举（服务申请请求状态统一 **`RequestStatus`**，禁止 `ServiceRequestPersistedStatusCodes` 式镜像）。

#### B.10.2 常量类路径（硬门槛）

> 适用于 `zxplat-app` 工程；其他工程以任务书约定为准。  
> **前提**：本节仅适用于通过 **B.10.0** 决策后确定归属为"非枚举常量"的项目；业务状态类项目必须先走 B.10.0 确认为枚举后再落 `enums/` 包，不得因"统一入口"臆想再合并成单一大常量类。  
> **应用层落点**：编排类（含 `*AppService`）引用的业务常量**一律**走 **B.10.2.1**，不得因「本模块私有」在 `zxplat-application` 下另起常量包。

- **统一入口（唯一合法路径）**：所有非枚举常量按需追加到  
  `zxplat-domain` → `com.zxplat.domain.share.constants` 下**语义匹配的 `XxxConstants` 接口**（或 **`XxxQueueTab`** 等专用页签接口，见 **B.10.2.1**）；同一文件内可用嵌套 `public interface` 细分场景（与现有 `CustomConstants` 等一致）。  
- 实现类（领域/应用/QueryService）**禁止**再声明与已有 `*Constants` 同义的字段，统一通过 **`XxxConstants.常量名`**（或嵌套分组 **`OuterConstants.InnerGroup.CONST`**）引用。

#### B.10.2.1 应用层禁止自建 constants 与命名收口（硬门槛，zxplat-app 专规，新增）

> 与 **B.4**（编排类内禁死常量）、**B.6.1**（`module.common` 不放业务常量集合）、**B.10.1**（只建被引用常量）配套。  
> **典型正面**：`OutpatientChargeConstants`、`OutpatientChargeQueueTab`、`OutpatientRefundConstants`、`OutpatientRefundQueueTab`（均在 `domain.share.constants`）。  
> **典型反面**：`com.zxplat.domain.application.outpatient.financial.refund.constants.*`、`…charge.constants.*`、或在 `*AppService` 同包声明 `interface RefundConstants`。

**禁止项（P0）**

| 禁止行为 | 正确做法 |
|---------|---------|
| 在 `zxplat-application` 任意包下新建 `constants/` 目录或 `*Constants.java` | 将字面量追加到 `zxplat-domain` → `domain.share.constants` |
| 按 AppService 包路径「镜像」建常量（如 `refund/constants`、`charge/constants` 各一份） | 按**业务场景 + 语义**命名顶层接口（如 `OutpatientRefundConstants`），与 Java 包路径**解耦** |
| 在 `module.common` 新增 `*Constants` 承载 `EX-*`、页签码 | 上收 `domain.share.constants`；`common` 仅装配/校验/转换（**B.6.1**） |
| 为已有 `enums` 下枚举再建镜像 `*StatusCodes` / `*QueueTab` 重复整套 `getCode()` | 业务状态用 **enum**；页签/路由离散码用 **B.10.0** 决策后的 `*QueueTab` 或既有接口 |
| SDD/AS 写「本模块 `constants` 包」而不指 `domain.share.constants` | SDD §2/§2.3 锚点写全限定名，如 `com.zxplat.domain.share.constants.OutpatientRefundConstants` |

**新建 / 追加流程（强制按序）**

1. **B.10.0**：先判枚举还是非枚举常量；有限业务状态集 → `enums/`，不得退化为 constants 字典。  
2. **B.10.1**：在 `domain.share.constants` 检索同义项；`EX-*` 消息码、渠道码、占位符等**能复用则复用**，禁止同义重复。  
3. **落位与命名**：  
   - **编排消息码、渠道、展示占位、汇总码（如 `MIXED`）** → `{场景}Constants`（如 `OutpatientChargeConstants`、`OutpatientRefundConstants`）；常量名宜与 AS/SDD 的 `EX-*` 或 BR 编号可追溯。  
   - **队列页签 / 请求 `queueTabCode` 离散取值**（`ALL`/`PENDING`/`SETTLED` 等，**非**领域生命周期枚举）→ 独立 `{场景}QueueTab` 接口（与 `OutpatientChargeQueueTab` 同构），**禁止**塞进 `*AppService` 或 Request 类内 `public static final`。  
   - **跨场景通用**（正则、批量上限、`LIKE_PERCENT`）→ `GeneralSharedConstants` 等通用接口，**禁止**下沉到门诊子包 constants。  
4. **仅追加被引用项**：本轮 IF/方法未使用的常量**禁止预置**（**B.10.1**）。确需新主题时新增**同级** `*Constants.java`，**禁止**合并为「万能 Constants」大杂烩。  
5. **迁移存量**：若发现 `zxplat-application` 下仍有 `constants` 包或 App 内常量类，本轮须迁到 `domain.share.constants` 并删旧类，全仓替换 import；SDD 同步改锚点。

**与分包的关系**：应用层 Java 包可按域收口（如 `outpatient/financial/refund`），**常量接口名与物理包路径无对应关系**——常量始终在 `domain-shared`，由多 AppService 共享。

#### B.10.3 字符串字面量使用禁止（硬门槛）

- **除以下两类场景外，一律禁止在业务代码中直接写字符串/数值字面量**：  
  1. 抛出异常 / 返回错误提示的消息文本（`throw new XxxException("患者姓名不能为空")`）；  
  2. 日志语句中的描述文本（`log.warn("患者 {} 状态异常", ...)`）。  
- 其余情况（正则、字段默认值、状态码、URL 片段、SQL 参数、**JPA/事件字段路径字面量**等）**必须抽到对应常量接口**（字段路径见 **B.10.10 `FieldNameConstants`**），禁止字面量散落在 Service/Entity/ValueObject 实现代码中。

#### B.10.4 常量分类与归属规则（硬门槛）

- 常量设计必须先做语义分类，再决定归属，禁止“按当前类方便”随意落位。  
- **通用常量优先**：跨领域可复用项（格式校验正则、通用批量上限、通用证件类型、聚合类型短码等）必须放在 `GeneralSharedConstants`、`AggregateTypeConstants` 等对应通用接口，禁止塞进无关聚合命名接口。  
- **错误码集中管理**：错误码统一放在 `ErrorCodeConstants`（或任务书约定的同级错误码接口），禁止散落在无关业务常量接口中。  
- **领域分组最小化**：`Patient` 等领域分组仅保留无法抽象为通用项的业务专有常量；凡可复用项一律上收通用分组。  
- 新增常量前必须先复用既有同义项，禁止同义重复与镜像定义。

### B.10.5 下拉 / KeyValue 返回 DTO 收敛（硬门槛，zxplat-app 专规）

> 本节仅适用于 `zxplat-app` 模块；其他工程以任务书约定为准。

- **统一返回**：所有"下拉选项 / KeyValue / 字典项 / 树形下拉（含 children）"返回对象 **统一使用** `com.zxplat.domain.share.dto.OptionDto`。  
- **禁止新建**：禁止为某个业务对象单独新建 `*DictDto` / `*OptionDto` / `*KeyValueDto` / `OrganizationDictDto` 这类"仅 value/label/children"的重复 DTO（无论在 `zxplat-domain` 还是 application 层）。  
- **树形用法**：树结构请用 `OptionDto.children` 表达；必要时用 `busNo` 携带层级编码；无需层级时 `children=null`。  
- **库房选品 `productBusNo`（硬门禁，zxplat-app 专规）**：凡库存/采购场景的 **`ProductDefinitionItemDto`**（及同构选品分页行），**必须**回填目录 **`bus_no`** 为出参 **`productBusNo`**——经 **`ProductDefinitionDisplayDto#getProductBusNo()`**（来源为药品/耗材/器材定义实体 **`getBusNo()`**，主数据取号与 **B.10.9** 一致）；Mapper **`toProductDefinitionItemDto` / `toProductDefinitionItemDtoFromInventory`** 在装配 `productName` 等同档展示字段时**一并** `setProductBusNo`，**禁止**选品下拉仅返名称不返编号。  
- **兼容约束**：如果历史接口已对外暴露非 `OptionDto` 结构，修订时须同步改调用方与初始化 DTO 字段类型，并在同轮交付内完成全仓引用检索与替换（A.9）。

### B.10.6 工具类新增约束（硬门槛，zxplat-app 专规）

> 本节仅适用于 `zxplat-app` 模块；其他工程以任务书约定为准。

- **禁止主动预置未消费工具类**：`domain.share.utils` 下的工具类（`*Util`）必须以“本轮代码中存在真实调用点”为新增前提；**禁止**为了“可能后续会用到”而提前新增未被任何业务代码消费的工具类。
- **通用性前置校验**：新增工具类前必须满足“跨聚合可复用、非单一领域专有规则、无业务状态依赖”三项；不满足时应下沉到对应聚合内部实现，禁止上收 `share.utils`。
- **收口自检**：交付前需确认本轮新增 `*Util` 至少有一处有效调用（非测试占位调用）；若无调用点，删除该工具类或并入实际使用类，避免形成死工具沉淀。
- **批量命令提取逻辑复用（新增）**：当多个服务内出现同构的 `extractXxxIds`/`collectXxxIds` 循环（从 `List<ItemCommand>` 提取标识）时，必须下沉到 `domain.share.utils` 统一工具（如 `CommandBatchUtil`）复用；禁止在各 `*DomainService` 内重复声明同构私有提取方法。

#### B.10.9 业务编号（busNo）采集（硬门槛，zxplat-app / Whale 栈专规）

> 适用于需生成对外可见 **业务流水号 / busNo** 的领域编排、应用编排；技术主键（`@GeneratedValue` 等）不在本节范围。

- **统一入口（强制）**：凡新写或修订 **busNo / 业务流水号** 生成逻辑，**必须**使用 `com.zxplat.framework.core.infrastructure.sequence.BusinessSequenceService` 的公开方法（`getSeq`、`getSeqByDay`、`getSeqNo`、`getSeqNoByDay`、`nextValue`、批量重载及 `formatString` 等）。**禁止**自造「表内 `MAX+1`」「应用内随机/时间戳拼接当唯一号」「绕过框架的 Redis/本地文件序号」等旁路；确因历史兼容必须旁路时须在 `DOC-CONFLICT` 留痕并给出收敛计划。
- **业务数据 vs 非业务数据（强制分流）**：
  - **业务数据**（随就诊日/会计日/业务发生日归档、对账、统计或法规上要求「按自然日分段」的流水号，如挂号单、收费单、处方号等）：**序号必须跟日期走**——使用 **按天** API：`getSeqByDay` / `getSeqByDay(prefix, seqLength)` / `getSeqByDay(prefix, seqLength, count)`、`getSeqNoByDay` / `getSeqNoByDay(prefix, count)`。框架内部以 `prefix + ":" + 当天日期串` 作为序号键，实现「跨天从 1 重新递增」。
  - **非业务数据**（主数据、配置、模板、术语库等**不**按业务发生日滚动的编号）：使用 **全局连续递增** 路径：`getSeq` / `getSeq(prefix, seqLength)`、`getSeqNo`、`nextValue` 等，**禁止**误用按天 API 导致编号无意义地按日截断。
- **前缀与聚合对齐（强制）**：不同聚合 / 不同业务线的 **busNo 前缀字符串** 必须与 `com.zxplat.domain.share.constants.AggregateTypeConstants` 中**对应聚合**的常量一致（该接口常量语义即为「聚合类型短码 / BUSNO 前缀」）。**禁止**在 `*DomainService`、`*AppService`、`*Manager` 等实现类中手写与 `AggregateTypeConstants` 不一致的游离前缀字面量；若 DM/SDD 要求的前缀在该接口中尚不存在，须**先在 `AggregateTypeConstants` 增补常量**再引用（并遵守 **B.10.3**）。
- **批量（强制）**：同一事务或同一请求内需多条 busNo 时，优先使用 `BusinessSequenceService` 的 **批量** 重载（如 `getSeq(prefix, seqLength, count)`、`getSeqByDay(..., count)`），避免在循环内逐条调用单条 API 放大锁竞争与往返。
- **与设计文档冲突时的权威（强制）**：**DM、SDD、AS、DM §6.2 查询契约、接口契约** 等任意设计/交付文档中，对 **busNo（业务流水号）取法**（含是否按日、所用 API、前缀来源与字面量）的叙述若与本节 **B.10.9** 不一致，**一律以本 Skill 本节为真源** 落地代码与评审，**不得以「文档如此写」为由沿用旧取法**。须在 **`DOC-CONFLICT`**（或本轮交付等效留痕）中单列：**文档位置与原述**、**实际采纳 B.10.9 的要点**，并安排后续修订文档以消除差异。
- **与框架文档**：实现细节见 [`framework/topics/business-sequence.md`](../../framework/topics/business-sequence.md)；成码时以本节门禁为准；若与 **B.10.9** 冲突，**仍以 B.10.9 为准**。

#### B.10.10 属性路径与事件载荷字段名（FieldNameConstants，硬门槛，zxplat-app 专规）

> 与 **`TableNameConstants`**（物理表名）并列：**表名**用 `TableNameConstants`，**JPA 属性名 / 领域事件 JSON 键名 / 动态排序属性路径**等 camelCase 字段字面量用 **`FieldNameConstants`**。

- **统一入口（强制）**：`zxplat-domain` → `com.zxplat.domain.share.constants.FieldNameConstants`（`public interface FieldNameConstants`，内联 `String UPPER_SNAKE = "camelCase"`；常量 **JavaDoc 写该字段的业务含义**（禁止仅用「属性名：{@code …}」式模板）；camelCase 字面量由常量赋值自明；语义易混或同义异键字段须在 JavaDoc 中说明差异并 **`@link` 互链**）。
- **适用场景（强制收口，禁止散写字面量）**：
  - JPA Criteria：`root.get(...)`、关联路径中的属性名实参；
  - Spring Data：`Sort.Order.desc("…")` / `Sort.Order.asc("…")`、`Sort.by(..., "…")` 中的属性名、`Pageable` 构造中的排序字段；
  - 应用层查询请求：`setSortBy("…")` 等与持久化属性对齐的排序字段名；
  - 领域领域事件载荷/反序列化：`readString` / `readLong` / `readDateTime` / `readLocalDate` / `readInteger` / `readBigDecimal` / `readBoolean` / `readLocalDateTime` / `readProperty` 等读取载荷时传入的**键名**；
  - 变更清单：`changedFields.add("…")` 等与实体属性名一致的字符串。
- **命名规则**：Java 常量标识符使用 **`UPPER_SNAKE`**，值为 **与契约完全一致** 的 camelCase 字符串（与实体 `@Column` / 事件载荷 JSON 键一致）。
- **同义异键（强制分设）**：同一业务含义在不同事件或版本中使用不同键名（如 `cancelledAt` 与 `cancelledTime`）时，**必须**在 `FieldNameConstants` 中分别定义常量，并在二者 JavaDoc 中 **互链**（`@link`），禁止混用或合并为一个字面量常量。
- **与 B.10.1 的关系**：仅允许新增**本仓已有真实引用**或本轮交付将使用的键；禁止为“将来可能用到”预置大量未引用常量。
- **豁免（仍遵守 B.10.3）**：面向用户的展示文案、异常消息、日志占位符不按本节收口；**禁止**以「非字段语义」为由用纯数字串充当枚举/字典/状态路由键（与 **§0.1.1、§0.12** 冲突）。若某字符串**既是**字段键又是展示文案，字段路径仍须走 `FieldNameConstants`。

#### B.10.11 领域分页入参与机构收窄（硬门槛，zxplat-app 专规）

> **适用范围**：`**zxplat-domain**` / **`zxplat-domain`** 中 **一切**「分页、可关键字、可日期区间的列表查询」——**无例外地按本节流水线落地**，直至任务书或 AS/DM §6.2 查询契约对**某一具体场景**写明不同策略（须在代码注释与文档 **BR** 可核对）。HTTP / 应用层 Request 与领域 `*PageQuery` 装配须与本节一致。

##### B.10.11.0 分页语义真源（请求 / 响应 / Spring，强制）

以下为本仓库 **HTTP ↔ 领域 ↔ Spring Data** 的 **唯一**分页契约；成码、OpenAPI、前端 `PageParams`/`PagedResult` 须与此一致，**禁止**第二套解释。

| 概念 | 字段名 | 语义 | 说明 |
|------|--------|------|------|
| **对外请求 / 领域入参** | `pageNo` | **1-based**（第 1 页 = `1`） | 定义于 `AbstractPageQuery`；未传或 `<1` 时在边界按「第一页」处理（见 `springPageIndex()`）。 |
| **对外请求 / 领域入参** | `pageSize` | 每页条数 | 经 `getBoundedPageSize()` 默认与上限防护（与父类实现一致）。 |
| **对外请求 / 领域入参** | `keyword` | 关键字 | 语义由具体 QueryService/查询解释；与 **B.2.1** 模糊检索配对。 |
| **Spring Data 内部** | `Pageable.getPageNumber()` | **0-based** | **仅**在构造 `PageRequest` / `Pageable` 时使用；**禁止**将 `Page.getNumber()` 原样作为对外 `pageNo` 返回。 |
| **对外响应** | `PagedResult.pageNo`（或 JSON 中等价字段） | **1-based**，与请求同名同语义 | **必须**经 **`com.zxplat.domain.module.common.pagination.PagedResultBuilder`** 写入（见 **B.10.11.1～B.10.11.2**）；**禁止**在 AppService/MapperImpl 手写 `page.getNumber() + 1` 或对 `PagedResult` 连续 `setter`；**禁止**把 Spring `Page.getNumber()`（0-based）原样写入对外 `pageNo`。 |

**框架类型（索引）**：统一分页响应类型为 `com.zxplat.framework.core.application.dto.PagedResult`；领域分页基类为 `com.zxplat.domain.share.query.AbstractPageQuery`。二者字段语义以本节表格为准；若框架实现变更，以 **`AbstractPageQuery` / `PagedResult` 源码 JavaDoc** 与本节 **B.10.11.0** 一致的口径为验收依据。

##### B.10.11.1 分页响应与 `PagedResultBuilder`（P0，`zxplat-app` / `zxplat-application`）

- **响应类型**：对外分页 JSON / OpenAPI 契约中，承载当前页列表的容器 **必须** `extends com.zxplat.framework.core.application.dto.PagedResult<行DTO>`；字段 **仅**使用基类约定：`content`、`totalCount`、`pageNo`（**1-based**）、`pageSize`。**禁止**在子类再声明 `records`/`rows`/`total`/`pageNum`/`totalElements`/`totalPages` 等与基类重复或异名的分页字段。
- **工具类（唯一装配入口）**：`com.zxplat.domain.module.common.pagination.PagedResultBuilder`。
- **禁止手写组装（P0）**：对任意 `extends PagedResult<?>` 的实例，**禁止**在 `*AppService`、`*MapperImpl` 中连续调用 `setContent` / `setTotalCount` / `setPageNo` / `setPageSize` 拼装分页（含「`Page<QueryModel>` 映射为 `List<Dto>`」「按 `bus_no` 聚合后的列表 + 独立 `mergedTotal`」等）；**一律**通过 **`PagedResultBuilder`** 对应重载返回。

##### B.10.11.2 `PagedResultBuilder` 方法选用与泛型约束（P0）

| 场景 | 方法 | 说明 |
|------|------|------|
| `Page<T>` 与 `PagedResult<T>` 的泛型 **T 相同**，且可直接使用 `page.getContent()` | `buildFromPage(response, page)` | **禁止**在 `Page<ProductDefinitionQueryModel>` 上调用，而行元素类型实为 `DoctorStationAdviceCatalogRowDto` 等已映射 DTO——此时 **T 不匹配**，编译亦会失败。 |
| 已将 `Page<?>` 映射为 `List<Dto>`，**总条数与页码仍与该 `Page` 一致** | `build(response, dtoRows, page)` | **典型 Mapper / AppService**；**0-based → 1-based** 的页码换算 **仅**在 `PagedResultBuilder` 内部完成，业务代码 **禁止**写 `page.getNumber() + 1`。 |
| 聚合/二次分页：`mergedTotal` 与入参已对齐的 **1-based** `pageNo`、`pageSize` | `build(response, content, totalCount, pageNo, pageSize)` | 如药库/药房按单号合并列表；入参 `pageNo` **已为对外 1-based**。 |
| 全量列表（不分页） | `buildList(response, rows)` | |
| 固定空分页壳 | `buildEmpty(response)` | `page == null` 且仍需 `PagedResult` 形状时亦可 `build(response, list, null)`（见工具类 JavaDoc）。 |

##### B.10.11.3 Spring 页码换算唯一入口（P0）

- **禁止**在 `PagedResultBuilder` **之外**出现 `page.getNumber() + 1`（或等价散落换算）。
- **合并前自检（建议）**：在 `zxplat-application` 内检索——除 `PagedResultBuilder` 自身外，不应出现对 `PagedResult` 子类的 `setContent`/`setTotalCount`/`setPageNo`/`setPageSize` 链式组装；不应出现 `getNumber() + 1`。

**命名禁令（硬门禁）**：新代码与文档 **一律使用 `pageNo`**；**禁止**新增或使用 `pageNum`、`currentPage`（与工程既有契约不一致时）等别名替代 **`pageNo`**。存量若仍残留 `pageNum`，修订时按全仓替换收敛至 **`pageNo`**。

**领域边界转换（强制）**：构造 `Pageable` 时 **必须**使用 **`query.springPageIndex()`**（或等价：`max(0, pageNo - 1)`，且 `pageNo` 取自 **`AbstractPageQuery`**），**禁止**在领域服务内手写「假设请求页码为 0-based」的 `-1`/`+1` 散落逻辑。

**应用层 Request（强制）**：凡分页列表，领域侧 **`XxxPageQuery` 已继承 `AbstractPageQuery` 的**，对应 HTTP Request DTO **同样继承 `AbstractPageQuery`**（或仅含业务字段且分页字段与父类 **完全一致**）；**禁止**在同一 Request 上重复声明 `pageNo`/`pageSize`/`keyword`（合并迁移时产生重复字段须删除）。

**初始化类接口（强制）**：工作站初始化、下拉开列表等若仅需一页候选数据，**仍**使用父类 **`pageNo`/`pageSize`/`keyword`**；**禁止**再 invent `practitionerPageNo` 等与 **`AbstractPageQuery` 并行的第二套分页字段**；机构、库房等 **从 Resolver 取**（科室 **`CurrentManagedOrganizationIdsResolver`**，库房 **`CurrentManagedLocationIdsResolver`**），**禁止**在 Request 上承载 `organizationId` 用于「当前工作台」收窄（与下款一致）。

**统一流程（成码时按序自检）**

1. **入参 DTO**：声明 **`XxxPageQuery extends AbstractPageQuery`**；公共分页与检索字段**只**放在父类（**`pageNo`**、`pageSize`、`keyword`、`startDate`、`endDate`、`sortBy`、`sortDirection`、`where`）；业务筛选字段**只**放在子类（状态、类型、排除表名等）。
2. **禁止**：在 `*PageQuery` 上声明 **`organizationId` / `orgId`** 等用于「当前登录工作台所属机构」收窄的字段（与下列第 3 步重复且易被伪造）。
3. **查询实现（应用层 / Loader）**：注入 **`CurrentManagedOrganizationIdsResolver`**，用 **`resolveOrganizationUnitIdForQuery()`** 得到 **`adm_organization.id`**；**非 null** 则拼 **`organization_id`（或契约等价列）** 相等谓词；**null** 则不拼（除非 SDD 单列「无会话查全院」——须文档与代码双重留痕）。**禁止** App/Loader 内 `ICurrentUser#getCurrentOrganizationUnitId()`。
4. **查询实现（领域 QueryService，增量）**：机构/库房/病区 id **由 App/Loader 解析后传入** Query 参数；**禁止**新建 QueryService 方法内注入 `ICurrentUser`（见 **B.6.5**）。
5. **应用层**：将 HTTP Request 映射为领域 `*PageQuery` 时**不再** `setOrganizationId`；历史 Request 若仍带该字段，按「遗留忽略」处理并在 SDD 标注。
6. **文档**：DM / DM §6.2 / AS-SDD 的 Request 表须与本节一致；冲突以本节为准改代码并 **`DOC-CONFLICT`** 修订文档。

- **统一基类（强制）**：凡「分页列表」类领域/QueryService 查询入参，**须**以 `com.zxplat.domain.share.query.AbstractPageQuery` 为父类（提供 **`pageNo`、`pageSize`、`keyword`**、可选时段与排序等）。业务专有字段声明在子类中（如 `statusEnum`、`excludedServiceTable`）。
- **禁止把机构主键放进通用分页 DTO（强制）**：`**PageQuery` 上禁止声明 `organizationId` / `orgId`** 等用于「当前工作台机构收窄」的字段；调用方不得依赖 HTTP/Ajax 传科室 ID 扩大或伪造可见范围。
- **机构收窄唯一入口（应用层，强制）**：`**AppService` / `module.querysupport.*Loader`** 使用 **`CurrentManagedOrganizationIdsResolver#resolveOrganizationUnitIdForQuery()`**（读）或 **`#requireSessionOrganizationUnitId`**（写）；**禁止**直读 `ICurrentUser`（**B.6.5**）。
- **应用层 HTTP Request**：队列、工作站列表等 API 的 Request **不得**再承载「可选 organizationId」用于筛选；初始化类接口若历史字段仍存在，须在 SDD 标注 **已废弃/忽略**，以会话为准。
- **例外（须在对应 AS/DM §6.2 查询契约单列）**：个别列表 intentionally **不按会话机构过滤**（例如某 EXISTS 护士站队列）；须在文档写明理由，且代码注释与 **BR** 可追溯。
- **文档对齐**：修订 DM §查询、DM §6.2、`AS-SDD` §Request 表时，须与本节一致；冲突按本节收敛代码并 **`DOC-CONFLICT`** 修订文档。

##### B.10.11.4 zxplat-app 会话科室 id 语义（强制）

- **`CurrentManagedOrganizationIdsResolver#resolveOrganizationUnitIdForQuery()`** / **`#requireSessionOrganizationUnitId`** 在 **health** 场景返回 **`adm_organization.id`**（HIS 组织主键），**非** `zxplat_organization_units.id`。
- 存量 **`ICurrentUser#getCurrentOrganizationUnitId()`** 在 health 装配与会话存储路径上语义同上；Whale OU id 仅用于 identity 表（`zxplat_user_organization_units`）与 RBAC 树，**禁止**与业务表 `organization_id` / `org_id` 列混用。

#### B.10.12 Excel 注解导出（硬门槛，Whale 栈 / zxplat-app 专规）

> **适用范围**：`zxplat-app` 及同栈一切「服务端生成 xlsx 并返回字节流或下载响应」的 **AppService 导出 IF**；与 **B.6.1**（`module.common` 不放通用 Excel 工具）、**B.10.3**（表头/工作表名字面量收口）配套。

##### B.10.12.0 框架真源（强制）

| 能力 | 包路径 | 说明 |
|------|--------|------|
| 列注解 | `com.zxplat.framework.core.application.annotation.Excel` | 声明列标题 `name`、列序 `sort`、可选 `scale`/`roundingMode`/`defaultValue` |
| 写表工具 | `com.zxplat.framework.core.application.util.AnnotatedExcelExporter` | **`exportToXlsxBytes(sheetName, rows, rowType)`** → `byte[]` |
| POI 依赖 | **`zxplat-framework` `pom.xml`**（`poi-ooxml`，版本由 `zxplat-framework` BOM 管理） | **禁止**在 `zxplat-application` 等业务模块**重复声明** POI 依赖（除非 `DOC-CONFLICT` 单列且说明无法经 `zxplat-framework` 传递） |

**禁止**在 `com.zxplat.domain.module.common` 或业务场景包下新建第二套 `@Excel` / `ExcelUtil` / `AnnotatedExcelExporter`；存量若存在，增量不得扩大使用面，收敛至 **`zxplat-framework`**。

##### B.10.12.1 导出行 DTO（强制）

- **独立类型**：每个导出场景须声明 **`*ExportRowDto`**（如 `InventoryDetailCurrentExportRowDto`），**只**承载导出列字段；**禁止**在列表/详情 `*ListRowDto` 上直接堆 `@Excel`（避免 API 契约与 Excel 列耦死）。
- **列定义真源**：导出列标题、顺序、数值 `scale` **只**在 `*ExportRowDto` 字段上的 **`@Excel`** 维护；AS/SDD 的 **BR-012** 等列序须与此 DTO **逐列对齐**。
- **`BigDecimal` 格式化**：金额类列的 `scale` 须与业务常量一致（如 **`ChargeItemAmountScaleConstants.CHARGE_MONETARY_SCALE`**）；**禁止**在 AppService 导出前循环 `setScale` 仅为 Excel 展示（应在装配阶段已按列表口径算好，或依赖注解 `scale` 写出）。
- **展示字段**：导出列优先使用**已装配的展示串**（如 `unitName`、`inventoryStatusText`、`expirationDate`），**禁止**在导出循环内再查字典/枚举。

##### B.10.12.2 应用层编排（强制）

**统一流水线（成码时按序自检）**：

1. **Pre**：与列表/汇总同构的数据装载与 **`enrichRows`**（或等价装配），**禁止**导出路径单独 N+1。
2. **Asm（Mapper）**：提供 **`toXxxExportRowDto(已装配列表行)`**（或批量映射），**只**做字段投影，**禁止**承载业务流程判断。
3. **Asm（写表）**：调用 **`AnnotatedExcelExporter.exportToXlsxBytes(工作表名, exportRows, XxxExportRowDto.class)`**。
4. **Response**：按 AS/SDD 设置 `fileName`、`contentType`（如 `InventoryWarehouseDetailExportConstants.XLSX_CONTENT_TYPE`）、`body`；若文档约定截断语义，须返回 **`exportedRowCount`**、**`truncated`**（与汇总上限常量对齐）。

**禁止项（硬门禁）**：

- **禁止**在 `*AppService#export*` 内 `new XSSFWorkbook()` / `sheet.createRow` / `createCell` **逐列**写表头与数据。
- **禁止**为导出列在 `*Constants` 中维护 **`EXPORT_HEADER_*`** 表头字面量（表头以 **`@Excel(name=…)`** 为唯一真源）；工作表名、默认文件名、MIME、行上限等业务字面量仍可放在 `domain.share.constants` 的 `*ExportConstants`。
- **禁止**复制 openhis 旧栈 `ExcelUtil` / `NewExcelUtil` 到 whale 业务仓；新导出一律走本节框架能力。

##### B.10.12.3 与文档 / 前端契约

- AS/SDD 的 IF-导出步骤 6（Asm）须写明 **`Mapper#toXxxExportRowDto` → `AnnotatedExcelExporter`**，白名单依赖 **`zxplat-framework`** 而非「App 内 POI」。
- 导出截断、空表是否仍出表头等**产品语义**以 AS 为准；代码须与 Response 字段一致，冲突以 AS/SDD 收敛并 **`DOC-CONFLICT`** 留痕。

### B.11 时间字段类型（硬门槛）

**判断规则（按优先级）**：

1. **DM/SDD 已明确标注**的字段类型，以文档为准，不做推断。  
2. DM/SDD 标注为 `DateTime` 且**字段名以 `*Time` / `*At` 结尾**（如 `createdAt`、`operatedAt`、`startTime`）→ `LocalDateTime`。  
3. DM/SDD 标注为 `Date` 或**字段名以 `*Date` 结尾**（如 `birthDate`、`enrollDate`、`expireDate`）→ `LocalDate`。  
4. 字段语义明确为**具体时刻**（含时分秒精度）→ `LocalDateTime`；语义为**仅需日期**（年月日精度）→ `LocalDate`。  
5. 无法判断时一律 `LocalDateTime`，并在 SDD §3 注释中标注原因。  

> 同一聚合内不得混用：`birthDate`（`LocalDate`）与 `startTime`（`LocalDateTime`）各自正确；**禁止**把本应是 `LocalDate` 的字段写成 `LocalDateTime`（如把 `enrollDate` 写成 `enrollDateTime`），反之亦然。  

- 禁止编排内手写 `tenant_id`。

### B.12 绝对禁止速查（已迁移，此处不重复）

> 超长禁止项列举已迁至 **[checklist.md](../checklist.md)**（含建议检索命令）与 **[anti-patterns.md](anti-patterns.md)**（BAD/GOOD）。P0 索引见 **[SKILL.md §2](../SKILL.md)** 与上文 **§0**。

### B.14 领域事件发布时序（硬门禁）

> 适用于所有含 `IDomainEventPublisher` 的领域服务编排。

- **强制顺序**：在任何一个领域写方法中，**数据库写操作（`saveAll` / `save` / 集合更新/逻辑删除等）必须在领域事件发布之前完成**；禁止在 `saveAll` 调用之前（包括在 `for` 循环体内）发布事件。
- **原因**：若先 `publish` 再 `save`，当 `save` 抛出异常导致事务回滚，事件已投递但数据库状态未变更，造成"幽灵事件"——下游消费方收到通知但源数据未改变，引发数据不一致。
- **正确模式**：先将所有待保存实体收集到 `toSave` 集合并执行 `repository.saveAll(toSave)`，之后再逐条或批量调用 `domainEventPublisher.publish(...)`。
- **禁止模式**：在 `for` 循环内先 `publish` 再 `add` 到 `toSave`，最后才 `saveAll`。

### B.13 删除语义（逻辑删除门禁，zxplat-framework 底座专规）

> 本节约定依赖 `zxplat-framework` 全局软删拦截机制；其他工程以任务书约定为准。

- **默认（强制）**：业务语义上的"删除/废弃/作废"统一为**逻辑删除**：落库字段统一称 **`isDeleted`**，对外表现为记录不可见/不可参与业务计算；查询默认过滤 `isDeleted = false`。
- **工程底座约定（全局软删接管）**：允许在领域层/仓储调用 `JpaRepository/CrudRepository.delete*`，因为底座已通过 `zxplat-framework` 的全局软删拦截将其改写为 **`isDeleted=true` 的 UPDATE**（而非物理 DELETE）。
- **仍然禁止（防误用门禁）**：
  - **禁止**无参 `deleteAll()` 这类"全表清空"调用（默认应被底座拦截/拒绝）；若确需物理清理，仅限运维/数据修复场景，必须显式放行并在 `DOC-CONFLICT` 留痕。
  - **禁止**在业务代码中以"物理清理"为目的绕开软删（除非任务书/DM/SDD 明确授权物理删语义）。
- **放行机制**：需要"跳过软删过滤/允许物理删除"的少数方法，必须显式标注 `@SkipSoftDeleteFilter` 并在方法 JavaDoc 写清楚用途与风险边界（仅限修复/回收/运维，不用于常规业务流程）。

### B.15 QueryService 读侧（与《领域层代码生成提示词模板》§6.2 一致）

> **2026-05-28 起**：`com.zxplat.domain.readmodel` 包、`*RMQueryService`、`*Projector` **已删除**，**禁止**新增或扩展。读侧能力**仅**通过各 bounded context 下既有 **`{Aggregate}QueryService`** 实现（见 `ReadModel弃用迁移/09-架构约束-仅使用既有DM-QueryService.md`）。

- **B.11**：`queries/model/*QueryModel` 与主表实体的**日期/时刻**列类型须与 DM §3 一致。
- **B.2**：QueryService 内过滤与分页**下推**至 `Specification`/派生查询；**禁止** `findAll()` 后内存筛、手工 `subList` 分页。
- **B.10.8.1**：查询结果中的枚举/状态列**只读写 `getCode()` 字符串**，禁止数字串冒充。
- **应用层红线**：**只注入** `*QueryService`/`*DomainService`；**禁止**注入 `*ReadRepository`、**禁止** `import ...readmodel...`。
- **组装规则真源**：DM §6.2 各方法小节的「过滤口径 / 组装规则 / 查询范围」；AS-SDD 编排序列中的 `SomeQueryService#method` 须与白名单一致。

---

## C. 与《领域层代码生成提示词模板》的分工

| 内容 | 归属 |
|------|------|
| 注释密度（含 **A.4** 单轮验收）、可读性、连续闭环执行、编译、注解禁令、码表禁令、JavaDoc、Flyway 路径、分包、Manager 冻结、**对外禁止 JSON 串透出 / 出参结构化 DTO（§0.17、B.6.2）**、**常量唯一落点 / 禁止 App 层 constants 包（B.10.2.1）**、**登录会话与数据范围 / whale vs adm / 五类 Resolver（B.6.5）**、**busNo / `BusinessSequenceService` / `AggregateType` 前缀（B.10.9）**、**Excel 导出 / `@Excel` / `AnnotatedExcelExporter`（B.10.12）** | **本 Skill（全量）** |
| 本轮 **领域名**、DM/SDD 路径、**领域方法全集与 SDD 对齐**、聚合不变量、BR 落点、事件清单、白名单方法、禁区文件 | **领域层模板** |
| QueryService / QueryModel / 查询 DTO实现、§4.2 与 §3 的逐行对齐、投影幂等专规 | **《领域层代码生成提示词模板》**（**B.15** 绑定通用栈规） |

执行领域成码：**同时加载/投喂**《领域层代码生成》与**本 Skill**；执行 QueryService 扩展成码：加载《**领域层代码生成**》与**本 Skill**；**执行应用层成码：加载《应用层代码生成提示词模板》与本 Skill**，注释密度（A.4）、JavaDoc（B.1，含禁止 `{@inheritDoc}` 占位）、禁三元（A.8）、for 优先、**出参禁止 JSON 串透出（§0.17、B.6.2）**、**App 层禁止直读 ICurrentUser / 须五类 Resolver（B.6.5）**、`@Schema` 字段注解、文件独立（每类单独 `.java`）等全量工程门禁对 `AppService`/`Mapper`/`Request`/`DTO` 同等适用，**不因「应用层」身份豁免**；冲突时以 **本 Skill** 为工程门禁准绳。

---

## 附录：最终汇报最小要求

**前置**：§0.8 完成标准四项 **全部满足**；否则 **禁止** 本附录汇报（须继续执行闭环）。

完成汇报仅在自检 0 差异时允许给出，**一次性**发出，**禁止**末尾附加「是否继续 / 要不要我…」。内容至少包含：

- 本轮对齐范围与最终“剩余差异 = 0”结论；
- 关键差异已消除项（方法、字段、状态、事件、查询口径）；
- 编译结果与必要校验结果；
- 若存在 `DOC-CONFLICT`，给出冲突点与采纳依据。


