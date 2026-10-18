---
name: application-layer-codegen-prompt-template
description: **可选插件**：应用层 IF/AS 对准（Request/Response、Mapper、AppService 编排）。**主规范**见《代码生成通用规范》——用户默认只 @ 那份；需 IF 对准 checklist 时再 @ 本 Skill。触发：AS-SDD、IF 对准、AppService 编排步骤。
---

# 应用层代码生成提示词模板

> **定位：可选插件，非主规范。** 工程门禁（注释、禁三元、B.6、B.10、Resolver、分页、Excel 等）**全部**在《**代码生成通用规范**》；用户日常 **只 @ 通用规范 + AS-SDD** 即可成码。  
> **何时额外 @ 本 Skill**：需要 IF 清单、Request/Response 字段表、Mapper 两段映射、AppService 编排步骤等**应用层对准**专用 checklist 时。

## 使用目标

本 Skill **只解决「应用层对准」**：锁定本轮要实现的 IF、接口契约、Mapper 两段映射、编排顺序、领域调用边界与落盘结构。

**默认口径（强制）**：**文档先于代码**。应用层代码生成唯一权威输入为 **AS + AS-SDD**，代码仅为实现载体，不是需求来源。

**强制前置**：成码须已加载《**代码生成通用规范**》（用户默认只 @ 那一份即可）。本 Skill **不重复**其核心条款；下文仅补充应用层对准。

**增量目标（P0）**：本轮新增/修改代码须符合《代码生成通用规范》**B.6**；未列入任务书时不得借机全库批量改 Gate/操作人。

> **注释/JavaDoc/@Schema/禁三元/for 优先**：见《代码生成通用规范》**§0、A.4、A.8、B.1**；本 Skill 不重复展开。

## Whale 包职责与落盘（强制）

| 落在哪里 | 包 / 模块 | 内容 |
|----------|-----------|------|
| **本 Skill 主战场** | `zxplat-application` → `application.{basic,inpatient,outpatient,inventory}/**` | IF 专属 `request`/`response`/`dto`/`mapper`/`service`；单场景 `*/support 门控 |
| **跨场景复用（注入，勿复制）** | `health-module-support → `module.common.*` | Resolver、Gate、`PagedResultBuilder`、目录预热 Assembler 等 |
| **跨聚合读（注入，勿在 App 编排）** | `health-module-support → `module.querysupport.*` | `*Loader`、`EncounterContextFacade` |
| **诊断/身份桥接** | `health-module-support → `module.shared.*` | `DiagnosisJsonSnapshotParser`、Whale SPI |
| **常量/枚举** | `zxplat-domain` → `domain.share.*` | **禁止**在 `zxplat-application` 下建 `constants` |

**禁止**在 `zxplat-application` 新建 `common`、`platform`、`querysupport、`Report 子包。真源：`docs/架构/模块化开发规范.md` §5。

---

## 权威文档形态与章节锚点

| 文档 | 主职责 |
|------|--------|
| AS | IF 业务语义、BR、异常口径、NFR 意图、跨域边界 |
| AS-SDD | 应用方法名、HTTP、Request/Response 字段、方法调用级步骤、NFR 代码落点、测试矩阵 |

**开工前至少定位以下锚点（强制通读，禁止只看标题）**：

| 章节 | 成码用途 |
|------|----------|
| AS `§1` 场景概述 | 角色动作、边界、跨域协作语义 |
| AS `§2` 页面与接口总览 | IF 清单、读写属性、规则绑定 |
| AS `§4` 接口编排说明 | IF 触发前提、主路径、失败分支、QueryService意图 |
| AS-SDD `§2` 代码结构 | 目标包、依赖注入、白名单方法 |
| AS-SDD `§3.1` 方法表 | IF → AppService 方法名与 HTTP |
| AS-SDD `§3.2+` IF 细节 | Request/Response 字段、关键校验、领域编排序列 |
| AS-SDD `§5` 写库顺序 | Cmd 顺序、事务边界、回滚策略 |
| AS-SDD `§6` NFR 落点 | 幂等、并发、容错、脱敏、审计代码位置 |

---

## 文档路径约定（默认仓库，自动匹配）

- AS 根目录：`docs/应用/zxplat-application/AppService/`
- AS-SDD 根目录：`docs/应用/zxplat-application/AppService-SDD/`
- 目标应用模块（默认）：`D:\workSpace\zxplat\zxplat-app\zxplat-application\`

文件名按场景名称匹配：`AS-*-{名称}.md`、`AS-*-{名称}-SDD.md`，仅按名称匹配，不依赖编号输入。

---

## 开工前输入验真（强制）

1. 必须完整读取 AS + AS-SDD 两份文档，缺任一份即中止。
2. 必须先完成 IF 集合一致性预检（AS `§2` vs AS-SDD `§3.1`）。
3. 若 IF 集合、编号、名称或 HTTP 不一致，只允许输出 `DOC-CONFLICT` 与修复建议，禁止盲改代码。
4. 未完成双文档阅读与预检，不得进入任何代码编辑。
5. **AS-SDD 所引用的一切类、接口、方法，默认全部视为已存在**（以 AS-SDD 为唯一权威）；**禁止**为核实任何类/接口/方法是否存在而执行任何目录扫描、文件探查或 `rg`/`Get-ChildItem` 命令——直接按文档给出的名称编写调用代码，**不做任何预验证，不构造兜底实现，不注释掉调用代码**。
   - 最终编译会自动暴露真正缺失的符号，无需在生成阶段主动排查。
   - **唯一允许中断的场景**：在代码编写过程中（基于已读文件，非额外扫描）**可以确认**某个被调用的类/方法名在代码库中确实缺失，且缺失会导致当前 IF 无法继续编写；此时**立即中断并上报**（见 §执行纪律防跑偏 Rule 15），**禁止**尝试自行修复或构造替代。

---

## Whale 应用层工程体例（强制，从工程现有代码提取）

以下规范从 `zxplat-application` 工程现有代码提取，生成代码须与之一致。

### A. AppService 类结构规范（硬门禁）

- 凡含**写路径**的 AppService：**必须**注入 `CurrentOperatorResolver`；AS 规定分页必填的 IF：**必须**注入 `PageWindowGate`。
- 类注解：`@Service @Slf4j` + `@RequiredArgsConstructor`（或 `@AllArgsConstructor`，与工程现有风格一致）。
- **禁止 `@RestController`**：Whale 通过 `AppServiceControllerRegistrar` 将 `*AppService` 动态注册为 `POST /api/app/{serviceName}/{methodName}`，不另写独立 Controller。
- **HTTP 路径生成规则（硬门禁）**：AS-SDD `§3.1` 与各 IF 小节中的 HTTP 必须按 `POST /api/app/{serviceName}/{methodName}` 填写；其中 `serviceName = AppService 类名去掉 AppService 后缀并将首字母小写`（例如 `OutpatientRegistrationAppService -> outpatientRegistration`），`methodName` 必须与 `public` 方法名逐字一致；禁止写成业务自定义 REST 风格路径（如 `/api/op/...`）。
- **禁止声明式 `@Transactional`**（全文）：写/读事务分别由框架 **`AppServiceAspect`**、**`ReadOnlyTransactionAspect`** 自动管理；业务代码 **不得**再写事务注解或自建编程式事务工具。
- 每个注入字段须有**单行** `/** 中文说明 */` JavaDoc，描述其在本 AppService 中的用途边界（写侧/读侧/映射/辅助加载等）。
- 类级 JavaDoc 须含三要素：① 一句话编排职责；② `<p>职责边界：</p>` 条目列表（各依赖服务的边界说明）；③ 接入说明（`AppServiceControllerRegistrar → POST /api/app/{serviceName}/{methodName}；不另写 RestController`）。

### B. AppService 方法结构规范

每个 `public` 方法固定走四步，与 AS-SDD `§3.x` 编排序列一一对应：

1. **写前校验**：**单字段** inline 校验（**禁止**为单字段再拆 `private validate*`）；**多字段复合**校验（通常 **≥3 个关联字段**或 AS 明确写「组合校验」）方可提取为 `private void validateXxxRequest(XxxRequest request)`，且须满足《代码生成通用规范》**B.5.1**（非薄包装、非仅 1 次调用的 1～2 行包装）
2. **Request → Command/Query**：通过 Mapper 映射，禁止在 AppService 方法体内手工逐字段拷贝
3. **调用白名单领域方法**：仅调用 AS-SDD `§2.3` 列出的 DomainService / QueryService 方法
4. **结果 → DTO 返回**：通过 Mapper 映射结果，或调用私有 `getXxx(id)` 回读最新状态

方法 JavaDoc：`/** 中文一句话说明 */`，`@param` 每个参数实质中文，`@return` 返回内容，`@throws` 列出所有显式抛出的 `BusinessException` 及触发条件（统一使用 `com.zxplat.framework.core.application.exception.BusinessException`，禁止用 `ValidationException` / `EntityNotFoundException` 等平行异常）。

方法体内注释：每个关键步骤、每条 `if` 分支、每次领域调用前须有 `//` 业务说明；禁止复述方法名（`// 调用 save` 是废话），必须说清业务含义（如 `// 先删旧定价再批量写入，保证实例维度价格幂等`）。

**私有辅助方法命名约定**：

| 用途 | 命名规律 |
|------|----------|
| 写前参数校验 | `validateCreateXxxRequest` / `validateUpdateXxxRequest` |
| 辅助数据加载 | `loadXxxLines(Long instanceId)` |
| 定价/字典回填 | `enrichXxxFields(XxxDto dto, List<Entity> rows)` |
| 写操作跨服务组合 | `replaceXxx(Long instanceId, ...)` |
| 排序字段白名单 | `isAllowedXxxSortField(String sortBy)` |

### C. Mapper 接口与 MapperImpl 规范

**Mapper 接口（`public interface XxxMapper`，无注解）**：
- 类级 JavaDoc：说明本 Mapper 负责哪些实体/QueryService与哪些 DTO/Command 之间的映射；注明禁止承载业务流程判断。
- 每个方法须有完整 JavaDoc（`@param` 调用方保证非空、`@return` 返回内容，含 `null` 时机说明）。
- 方法命名规律：`toCreateCommand(Request)`、`toEditCommand(Request)`、`toRetireCommand(List<Long>)`、`toDto(QueryModel/Entity)`、`toDetailDto(QueryModel)`。

**MapperImpl（`@Component class XxxMapperImpl implements XxxMapper`）**：
- 每个 `@Override` 方法须有完整 JavaDoc，**与对应 Mapper 接口方法全文一致**（说明句、`@param`、`@return` 逐条相同）。
- **禁止**仅用 `{@inheritDoc}` 作为实现类方法上的 JavaDoc 占位（与**《代码生成通用规范》B.1、B.12**一致）；成码与修订存量时须**在 `*MapperImpl.java` 内复制**接口侧完整 `/** … */`，便于单文件阅读与评审，不依赖 IDE 继承展示。
- `toDto` / `toDetailDto` 类方法开头必须 null 检查，null 时直接 `return null`。
- 逐字段搬运，不承载校验、枚举推断、金额换算等业务逻辑——这些移至 AppService 私有方法。
- 批量 id 命令组装统一使用 `BatchCommandItemAssembler.fromLongIds(ids, ItemClass::new, ItemClass::setId)`，禁止手写 for 循环构建子项列表。
- 含回落逻辑的 `private static` 工具方法（如采购单位换算比回落）须有 JavaDoc 说明回落规则。
- `toCreateCommand` 中 Command 的 item 结构：先构造 `XxxCommand.XxxItemCommand item`，逐字段赋值后再 `command.setItem(item)` 返回。

### D. Request / DTO 类规范

- **出参禁止 JSON 串透出（P0，见《代码生成通用规范》§0.17 / B.6.2）**：Response/列表行 DTO **默认禁止** `*Json` 等 `String` 整段 JSON 字段；须在 Mapper 装配为结构化子 DTO。**仅当**用户明示「用 JSON 返回」或 AS/AS-SDD 对该 IF 单列豁免时可保留。

- 类注解：`@Data @Accessors(chain = true) @Schema(description = "...", requiredProperties = {...})`。
- 每个字段须有 `/** 中文说明 */` + `@Schema(description = "...")` 注解。
- `Long` 类型 ID 字段：加 `@JsonSerialize(using = ToStringSerializer.class)` 防止前端精度丢失。
- 时间字段：加 `@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")`。
- 字典码字段须有对应 `{fieldName}_dictText` 展示文本伴生字段；枚举状态字段须有 `{fieldName}_enumText`。
- Request 与 DTO **必须分开建文件**，禁止复用同一个类；禁止在 Request/DTO 类中放任何业务逻辑。
- **每个 Request/Response/DTO 类必须单独建立一个独立的 `.java` 顶层类文件**——禁止将多个契约类合并至一个聚合文件（例如，用 `XxxRequests.java` 内嵌多个 `public static class` 或用 `XxxDtos.java` 聚合多个 DTO 的方式均被禁止）。AS-SDD `§2.1` 包路径列出几个 Request/DTO 就建几个独立文件，文件名与类名一一对应。
- 必填项：`@NotBlank(message = "xxx 不能为空")` + `@Schema(required = true)`。

### E. QueryService 与 module 组件（速查）

> 细则见《代码生成通用规范》**B.6**。

**分页**：`AbstractPageQuery` → `PageQueryBinder` → 出参 **`PagedResultBuilder`**（禁止 `getNumber()+1`）；AS 规定分页必填时用 **`PageWindowGate`**。

**必用组件（写/读）**：`CurrentOperatorResolver`（写 operatorId）、五类 Resolver（见 B.6.5）、`PatientEncounterConsistencyGate`（AS errorCode 一致时）。

**优先复用**：`BatchCommandItemAssembler`、`TerminologyOptionLoader`、`OrderRequestCatalogWarmAssembler`、`TextValueConverter` 等——先查 `module.common` / `module.querysupport，禁止复制同构 `private`。

**禁止**：`module.common.service.*`；`ICurrentUser` 注入 AppService/Loader；QueryService 作写前判定真源；出参 `*Json` 直透（须结构化 DTO，诊断 JSON 经 `DiagnosisJsonSnapshotParser`）。

---

## 输入与落盘约定

- 目标范围：仅应用层（`AppService`、`request`、`dto`、`mapper`）。
- **默认不生成**：Controller、Repository 实现、Mapper XML、Flyway SQL、前端代码。
- 典型落盘路径：`zxplat-application/.../application/{域}/{子域}/service/`、`request/`、`dto/`、`mapper/`（接口 + 实现类各一个文件）。
- `response/` 是否独立按工程现状，有独立 response 包则独立，否则并入 dto。

---

## 与《代码生成通用规范》的分工

| 类别 | 以何为准 |
|------|----------|
| 注释、JavaDoc、禁三元、批量 I/O、连续闭环、**B.6** 包职责、OptionDto、常量 | 《代码生成通用规范》 |
| IF 契约、Mapper 映射、AppService 编排、Whale 体例、module 组件速查 | 本模板 |

---

## 执行纪律防跑偏（强制）

1. **范围锁定**：仅实现本轮 AS + AS-SDD 指定 IF，不得擅自增删 IF 或改号。
2. **完成定义锁定**：以下项缺任一即未完成：
   - IF 集合与编号一致（AS `§2` vs AS-SDD `§3.1`）
   - Request/Response 字段逐列一致
   - Mapper 两段映射完整，无手工 field 拷贝遗留在 AppService 方法体内
   - 调用步骤顺序与 AS-SDD `§3.x` 一致
   - 白名单领域调用一致（方法名、参数结构）
   - NFR 落点齐全（幂等/并发/容错/脱敏/审计）
   - 编译通过且无新增 lint
   - **《代码生成通用规范》§0 全部 P0 门禁达标**（注释密度、禁三元、for 优先、BusinessException 统一等——见 §0 逐条，每 IF 完成后须主动回扫，见 Rule 10）
3. **禁止阶段收口**：上述项未闭合前，禁止输出"已完成/可下一步"。
4. **仅在剩余差异 = 0 时允许最终收口**。
5. **文档与代码不符时，一律修订代码对齐文档，不擅自改文档**。
6. **未达"剩余差异 = 0"前，禁止请求"是否继续"**（静默闭环）。
7. **默认静默闭环**，除阻断外不输出阶段总结。
8. **出现阻断时仅输出最小阻断信息**（阻断点 + 需用户提供项）。
9. **覆盖率不是完成度**：禁止用"已覆盖 IF-x~y"替代 D6 逐项通过。
10. **逐 IF 自动验收（强制）**：每完成一个 IF 的 AppService 方法体后，必须执行两层检查，均通过才能进入下一 IF：
    - **层一——§0 P0 回扫（逐项主动扫本方法体，发现违规立即原地修复）**：
      - §0.1 注释：逐个 `if/else` 分支、每次领域调用前确认有业务 `//` 注释；逐行比对，禁止"自检全 ✅ 而代码缺注"（A.4 注释验收一致）
      - §0.2/A.8 可读性：识别 stream 长链与嵌套 Optional，改写为 `for + if-else`
      - §0.3 禁三元：搜索 `?:`，有则改成 `if-else`
      - §0.4 禁死常量：AppService 方法体/类内无类级常量字典；**§0.14 / B.10.2.1**：`EX-*`/页签码等不得落在 `zxplat-application/**/constants`，须 `domain.share.constants`
      - §0.5 注解禁令：无 `@Transactional`、无 `@RestController`
      - B.1.1 BusinessException：所有 `throw` 均为 `com.zxplat.framework.core.application.exception.BusinessException`
      - **B.6 增量**：写路径 `requireOperatorId`；无 `ICurrentUser`；无 `getNumber()+1`；跨场景读用 `module.querysupport.*Loader`
    - **层二——D6 业务对齐子集核对**（字段、映射、顺序、白名单调用）
11. **白名单签名一致性（强制）**：除"是否调用"外，还必须核对调用参数结构与 AS-SDD 对应步骤一致，禁止参数位移、缺参、错参的"形式调用"。
12. **Response 来源可追溯（强制）**：每个响应字段必须能追溯到唯一来源（QueryService字段/领域查询结果/应用层计算），禁止无来源补字段。
13. **逐 IF 文件收敛顺序（强制）**：每个 IF 必须按「Request 字段 → MapperImpl 方法 → AppService 方法体」顺序落盘；禁止先铺满 AppService 再回填 Request/DTO 契约。
14. **Mapper 字段逐列核对（强制）**：每个 MapperImpl 方法完成后，须按 AS-SDD 字段表逐列核对映射是否完整；有字段未映射时不得进入下一 IF。
15. **依赖缺失的唯一合法处理协议（强制）**：
    - **生成阶段**：直接按 AS-SDD 给出的类名/方法名编写代码，不验证存在性，不构造兜底实现，不注释掉调用代码，不用其他类替换。
    - **确认缺失后唯一动作**：中断，向用户输出一句话：「`[缺失的类/方法全限定名]` 在 AS-SDD `§[章节]` 中被引用，当前代码库未找到，请补充后告知我继续」——**仅此一句，不超过三行，不附加任何分析或建议**。
    - **用户修复后**：恢复静默闭环，从中断点继续，不重复已完成的 IF。
    - **绝对禁止**：① 自行构造替代实现；② 将调用代码注释掉并继续；③ 用功能相近的其他类"兜底"；④ 输出"我发现了问题，后续我会……"类的预告性文字；⑤ **在方法体内留 `// TODO` 注释，或以 `return new XxxResponse()`（空返回/空对象/空列表）作为占位**——TODO 占位与空返回在逻辑上等价于「构造了替代实现」，**一律触发中断协议**，不得以「稍后补全」为由带 TODO 交付。
    - **"确认缺失"的判定标准**：基于本轮已读取文件可以直接得出结论（如：已读该服务文件，其中不含目标方法）；**禁止**为判断是否缺失而额外发起任何文件扫描或搜索命令。

---

## D6. 成码后与权威文档核对

| 验收项 | 验收要求 |
|--------|----------|
| **§0 P0 门禁全量达标** | 逐项按《代码生成通用规范》§0 回扫：注释密度（§0.1/A.4）、可读性/for 优先（§0.2/A.8）、禁三元（§0.3）、禁死常量（§0.4）、注解禁令（§0.5）、BusinessException 统一（B.1.1）——每项须主动回查代码，禁止"声明遵守"代替实际检查 |
| IF 一致性 | AS `§2` 与 AS-SDD `§3.1` 的 IF 集合、编号、名称、HTTP 完全一致 |
| 方法覆盖 | 每个 IF 对应一个且仅一个 AppService 方法（文档允许复用除外）；无遗漏、无多出 |
| Request 字段 | 逐列对照 AS-SDD `§3.x`；**每字段须有 @Schema(description=&quot;...&quot;)**；Long ID 字段加 `@JsonSerialize`；时间字段加 `@JsonFormat`；字典码有 `_dictText` 伴生 |
| Response/DTO 字段 | 逐列对照 AS-SDD `§3.x`；**每字段须有 @Schema(description=&quot;...&quot;)**；每字段来源可追溯（QueryService/查询/计算）；禁止无来源补字段 |
| Mapper 接口覆盖 | 按 AS-SDD §3.x「领域编排序列」Asm 步骤清点：每个有 Asm 步骤的 IF 均在 Mapper 接口中有且仅有一个方法声明（含 JavaDoc + 完整签名）；已声明方法数 == SDD 全部 Asm 步骤数；无缺漏、无多余未调用方法 |
| MapperImpl 字段完整性 | 逐方法逐列核对；null 检查完整；批量命令使用 `BatchCommandItemAssembler`；无手写 for 循环批量 |
| 编排顺序 | 步骤顺序与 AS-SDD `§3.x` 编排序列**逐行**一致（含 Cmd 写库先后次序）；含条件跳过逻辑（"若 X 为空则跳至步骤 N"）须按文档完整实现，不得遗漏或调换顺序 |
| 白名单签名 | 调用方法名**逐字**比对 AS-SDD `§2.2` 白名单（拼写/大小写完全一致）；**调用了白名单之外的方法须删除或补入白名单**；参数结构与 SDD 步骤约定一致，无参数位移/缺参/错参 |
| 写前门控 | Cmd 写库顺序与 AS-SDD `§5` 写库顺序**逐条**一致；Pre 任一失败禁止进入任何 Cmd；Gate 失败禁止进入 Pre |
| NFR 落点 | AS-SDD `§6` 幂等/并发/容错/脱敏/审计均有明确代码落点 |
| AppService 类结构 | `@Service @Slf4j @RequiredArgsConstructor`；类级 JavaDoc 含职责边界与接入说明；字段单行 JavaDoc；无 `@Transactional`；无 `@RestController` |
| module 复用 / B.6 | 已复用 `module.common` / `module.querysupport；五类 Resolver；`PagedResultBuilder`；无 `module.common.service` |
| QueryService红线 | QueryService未作为写前判定真源；列表/初始化查询口径与文档定义一致 |
| 无 TODO 占位 | 方法体内无 // TODO 注释、无空返回（return null / return new XxxResponse()）占位；任何未完成方法须触发 Rule 15 中断协议 |
| 编译与 lint | 受影响模块编译通过，且无新增 lint 错误 |

**收口规则**：D6 任一项未通过，视为未完成，必须继续消差。

---

## 提示词模板

> **强制声明**：下方 D0~D6 与正文同等效力。任一验收项未通过，禁止收口。

```text
你是资深 DDD 应用层开发助手。请基于 AS + AS-SDD 只生成应用层 Java 代码（不生成前端、SQL、Controller、基础设施实现）。

**强制前置（最高优先级，不可跳过）**：
你必须完整遵守《代码生成通用规范》的全部条款。本 Skill D0~D6 不覆盖、不免除、不弱化《代码生成通用规范》的任何约束——两份规范同时强制执行。必须遵守的核心条款包括但不限于：
- 0. 门禁总览（注释必须且宜充分、禁三元、for 优先、编译通过）
- A.4（方法体内每步、每分支须有业务向 // 注释；密度宁多勿少；禁止超长方法头 JavaDoc 却留大段无注释分支体；**注释验收一致**：每完成一个方法体须逐分支回查实际代码与注释是否一一对应，禁止自检全 ✅ 而代码缺注）
- A.9.1（批量 I/O 禁循环落库；批量唯一性校验须批量查询；禁嵌套 for 循环内存碰撞检验）
- A.10（连续闭环到 0 差异，未达标不得收口，中途禁止征询是否继续）
- B.1（JavaDoc 硬门槛：类、public/protected/private 方法、每个字段均须完整注释；private 方法不得以"内部方法"为由省略 JavaDoc）
- B.6（module.common / module.querysupport / 五类 Resolver / PagedResultBuilder / 禁止 JSON 出参直透）
- B.10.5（OptionDto 统一使用 com.zxplat.framework.core.application.dto.OptionDto，禁止新建 *DictDto/*OptionDto）

**P0 内嵌（与通用规范同效）**：方法体业务向 `//`；禁三元；Request/DTO 每字段 `@Schema`；每类单独 `.java`；禁止 TODO/空返回占位。

AS-SDD 所引用的一切类、接口、方法默认全部视为已存在；禁止为核实任何符号是否存在而执行任何扫描或搜索命令；直接按文档名称生成代码，不构造兜底，不注释调用代码。若在编写过程中基于已读文件可直接确认某个符号缺失，中断并告知用户（一句话），由用户修复后继续。

本消息 D0~D6 仅补充应用层对准，与《代码生成通用规范》冲突时以后者为工程门禁。

---

# D0. 本轮应用层输入（替换方括号）

- 场景名称：[必填，用于按名称自动匹配 AS / AS-SDD]
- 目标应用模块路径：[默认 D:\workSpace\zxplat\zxplat-app\zxplat-application\，可覆盖]
- AS 路径：[默认按名称自动匹配，可显式覆盖]
- AS-SDD 路径：[默认按名称自动匹配，可显式覆盖]
- 目标包段（必填）：[如 basic.medication / outpatient.appointment]
- 须保留存量方法白名单：[无则写"无"]

---

# D1. 权威与冲突

1. 权威顺序：用户裁决 > AS > AS-SDD > 现存代码。
2. 冲突统一留痕 DOC-CONFLICT，代码先对齐文档；文档疑似有误则另立文档修订任务，不得在本轮代码生成中擅自改文档。
3. 开工前完成 IF 集合一致性预检（AS §2 vs AS-SDD §3.1）；不一致时仅输出 DOC-CONFLICT 与修复建议，禁止盲改代码。

---

# D2. Mapper 字段对准（应用层专属，每个 IF 必须执行）

1. 读取 AS-SDD §3.x 中本 IF 的 Request 字段表与 Response/DTO 字段表。
2. 在 MapperImpl 对应方法中逐字段搬运（Request → Command；QueryModel/QueryModel → DTO）。
3. 完成后逐列比对 AS-SDD 字段表，确认无字段遗漏或类型错误。
4. 特别检查点：
   - Long 类型 ID 字段：Request 类须加 `@JsonSerialize(using = ToStringSerializer.class)`
   - 时间字段：须加 `@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")`
   - 字典码：须有 `{fieldName}_dictText` 伴生字段（AS-SDD 要求时）
   - 批量 Command：使用 `BatchCommandItemAssembler.fromLongIds()` 而非手写 for 循环
5. toDto / toDetailDto 方法开头必须 null 检查，null 时直接返回 null。
6. 字段映射不承载业务逻辑：枚举推断、金额换算、业务计算等移至 AppService 私有方法或 `module.common` 组件。
7. 未达到逐列对齐前，禁止进入下一 IF。

---

# D3. AppService 编排锁定（应用层专属）

1. 严格按 AS-SDD §3.x 中列出的步骤顺序调用白名单领域方法（以 AS-SDD `§2.3` 白名单为准，领域方法默认已提供，禁止为验证方法是否存在而扫描领域包）。
2. 写前校验必须先于任何领域写方法调用；单字段 inline `throw new BusinessException(ErrorCode.XXX_INVALID, "...")`，多字段复合校验提取为 private 私有方法；统一使用 `BusinessException`，禁止用 `ValidationException` 等平行异常。
3. QueryService只用于查询展示；写前唯一性/状态校验必须走领域层（DomainService/Repository），不得查QueryService代替。
4. 禁止应用层直接构造领域实体并持久化（须通过 DomainService 方法）。
5. AppService 方法名与 AS-SDD §3.1 方法表完全一致。
6. 批量 id 入口：使用 `GatewayIdParseUtil.requireNonEmptyLongIds(ids, "操作说明：ids 不能为空")`。
7. 私有辅助方法须有完整 JavaDoc；禁止匿名 lambda 承载多步业务逻辑。
8. 读侧分页：Specification + PageRequest + sort 白名单方法；返回经 **`PagedResultBuilder`**（禁止 `getNumber()+1`）。
9. **操作人 Gate**：写 Cmd 前 `Long operatorId = currentOperatorResolver.requireOperatorId(AS 错误码, AS 文案)`；禁止 `currentUser.getCurrentUserId()` 直传命令。
10. **患者/就诊 Pre**：QueryService取就诊快照后，若 AS errorCode/文案为「患者与就诊不一致」类，调用 `patientEncounterConsistencyGate.requireRequestPatientMatchesEncounter(requestPatientId, encounterPatientId, errorCode, message)`（可在 Mapper 封装 `validatePatientEncounter` 委托 Gate）。
11. **分页 Gate**：仅 AS-SDD 写明 `pageNo`/`pageSize` 必填且 ≥1 时，方法入口 `pageWindowGate.requirePositivePageWindow(request, ...)`。

---

# D4. 落盘锁定

- 四类文件独立落盘：service/、request/、dto/、mapper/（接口 + 实现类各一个文件）。
- **每个 Request/Response/DTO 各自一个独立 `.java` 文件**（见 §D. Request / DTO 类规范）；禁止聚合多个类进同一文件。
- 禁止把大量 Request/DTO/Response 内嵌到 AppService 中。
- 落盘顺序：字段（Request/DTO）→ 映射（MapperImpl）→ 编排（AppService）；禁止反向。
- 每个 IF 独立收敛后再进入下一 IF；禁止一次性铺满全部 IF 再统一返工。
- **绝对禁止 TODO 占位交付**：任何方法体若因依赖缺失而无法完整实现，必须执行 Rule 15 中断协议（向用户上报一句话），**禁止**在方法体内写 `// TODO`、`return null`、`return new XxxResponse()` 等占位代码后继续推进——带 TODO 的代码视为未完成，不计入 D6 通过项。

---

# D5. 执行与汇报（连续闭环）

- 默认持续执行"差异识别 → 落盘 → 自检 → 继续消差"，直至剩余差异为 0。
- 中途禁止输出进度播报或征询是否继续。
- 逐 IF 收敛顺序：Request 字段 → MapperImpl 方法 → AppService 方法体 → **§0 P0 回扫**（逐项扫本方法体：注释/A.4注释验收一致、for/A.8、禁三元/§0.3、BusinessException/B.1.1，发现违规立即原地修复）→ D6 子集自检 → 下一 IF。
- 仅允许两种输出：最终汇报（0 差异）或阻断汇报。
- **阻断的唯一合法触发条件**：① AS 或 AS-SDD 文档本身不可读；② 文件系统/权限故障；③ 基于已读文件可直接确认某个被调用类/方法不存在（见 Rule 15）。**SDD 引用类尚未生成、编译预期会报错，均不构成阻断**——直接生成，报错由用户修复。

---

# D6. 成码后与权威文档核对

按正文 **§D6. 成码后与权威文档核对** 验收表逐项通过后才能宣告完成；未达标不得输出「完成」。
```

---

## 快速替换项

- `[场景名称]`：如 `药品目录管理`、`门诊预约`
- `[目标应用模块路径]`（可覆盖默认）
- `[目标包段]`：如 `basic.medication`、`outpatient.appointment`
- `[须保留存量方法白名单]`：无则写"无"
