# 注释专闸（P0 最高优先级）

> **负责人最在乎的门禁**。成码与改码时，注释与逻辑**同轮交付**，禁止「先交无注释代码、后补注释」。  
> **闭环**：注释 pass 是 [SKILL.md §4.1](../SKILL.md) 终止条件之一——**未完成不得向用户汇报或问「要不要继续」**。  
> 细则原文：full-standards **A.3–A.4、B.1**；反例：**anti-patterns §C**。

**「忌废话」≠ 少写**：仅禁止「复述类名/方法名」「整段粘贴 DM」；**不禁止**为审查者读懂而增加业务句。**宁多勿少**。

**私有函数**：见 **§1.1**——`private` / 前端本地函数**必须有**方法头注释；与下行不冲突。

**与 B.5.1.1 的关系**：缺注释时 **在主流程加 `//`**，**禁止**为凑 JavaDoc 拆薄包装 `private`。

---

## 1. 按文件类型的最低要求（TOUCHED 文件逐类验收）

| 类型 | 类级 JavaDoc | 方法 JavaDoc | 体内 `//` |
|------|-------------|-------------|-----------|
| `*AppService` | 职责 + 对应 AS/IF | 每个 `public`/`protected`/**`private`**（含 `static`）：`@param`/`@return`/`@throws` 实质中文 | **每个** Gate/Pre/Cmd/Asm 步骤前；**每个**有意义的 `if`/`else`/`switch` 分支前 |
| `*DomainService` / `*QueryService` | 聚合边界 + 不变量摘要 | 每个 `public`/`protected`/**`private`**（含 `static`）完整 | 写路径、状态迁移、事件发布前 |
| `*MapperImpl` / Invoker / Assembler / Projector 等实现类 | 映射/出站/投影职责 | 每个 `@Override` **写全**（禁 `{@inheritDoc}`）；**每个 `private`/`static` 同要求** | 非平凡字段映射块前一句 |
| 实体 / Command / Query | 聚合/命令语义 | — | 非显然字段在 JavaDoc 说明业务含义 |
| Request / Response / DTO | — | — | **每个字段** JavaDoc（`@Schema` 不能替代 JavaDoc） |
| 枚举 | 枚举类职责 | — | **每个枚举常量**中文 JavaDoc |
| Repository 接口 | 仓储边界 | 每个方法：查询意图 + 规模风险（若适用） | — |
| 前端 `.ts` / `.vue` `<script>`（TOUCHED） | 文件/composable 职责一句 | **每个**本地/`function`/`const fn =`（含未 export 的「私有」函数）须有 JSDoc | 有意义分支前一句业务向注释 |

---

## 1.1 私有函数必须有注释（硬条款）

> **明文强制**：TOUCHED 源文件中**每一个** `private` 方法（含 `private static`）**必须**有方法头 `/** … */`；**不得**以「内部方法 / 私有 / 一眼能懂 / 仅本类调用」为由省略。  
> 与 **full-standards B.1** 同口径；与 **B.5.1.1** 不冲突——缺注释时优先在主流程加 `//`，**禁止**为凑 JavaDoc 拆 1～2 行薄包装 `private`。

**适用范围（Java）**

- `*AppService` / `*DomainService` / `*QueryService` / `*MapperImpl` / Bridge `*Invoker*` / gateway Assembler·Parser / Projector 等本轮新增或修改的类。
- **每个** `private` 实例方法、**每个** `private static` / 包内 `static` 辅助方法：方法头 JavaDoc 写清**调用关系、前提、业务目的**；有参数/返回值则 `@param`/`@return` 齐全。
- JavaDoc **禁止**仅复述方法名（如「私有方法：`foo`」「assembles bar」无业务句）。

**适用范围（前端）**

- `.ts` / `.vue` 中未 export 的本地函数、composable 内 helper：**必须**有 JSDoc（一句业务目的即可；有非显然参数则写 `@param`）。
- **例外**：纯一行透传 getter、或与模板事件同名的极薄 `() => x.value = …` 可只在调用处写 `//`；一旦函数体内有分支/组装/校验 → **必须** JSDoc。

**合格 / 不合格对照**

```java
// BAD — private 无 JavaDoc
private String resolveBusNo(String contractBusNo, Long contractId) {
    return contractReader.requireContractBusNo(...);
}

// GOOD
/**
 * 解析契约 bus_no（busNo 与 contractId 二选一，均空则抛业务异常）。
 *
 * @param contractBusNo 契约编号
 * @param contractId    契约主键
 * @return 非空 bus_no
 */
private String resolveBusNo(String contractBusNo, Long contractId) { ... }
```

**注释 pass 自检**：对每个 TOUCHED `.java`，检索 `private ` 方法声明，确认其上方紧邻 `/**`；对 TOUCHED `.ts`/`.vue`，检索本地 `function ` / `async function `，确认有 JSDoc 或符合上款极薄例外。

---

## 2. 编排类 public 方法：固定步骤注释（AppService / DomainService）

`*AppService` 的每个 `public` 方法体内，**必须**出现下列阶段标记（可合并相邻步骤，但**不可整段缺失**）：

```java
public XxxResponse doSomething(XxxRequest request) {
    // Gate：……（门禁/会话/患者就诊一致/分页窗口等，写清拒绝时对业务意味着什么）
    // Pre：……（装载、Resolver、Loader 预取）
    // Cmd：……（组装 Command、调领域写服务）
    // Asm：……（Mapper 映射、PagedResultBuilder、写 Response）
}
```

**DomainService 写方法**对应：`// 前置校验` → `// 加载/组装聚合` → `// 持久化` → `// 发布事件`（save **先于** publish）。

---

## 3. 分支注释：什么叫「严重不足」（禁止）

```java
// BAD — 整段无注释、if 无业务句
public void save(SaveRequest request) {
    PatientEncounterConsistencyGate.assertSame(request.getPatientId(), request.getEncounterId());
    var cmd = mapper.toCommand(request);
    domainService.save(cmd);
}

// BAD — 只有废话
if (status == null) {
    // 判断 status
    throw new BusinessException(...);
}

// GOOD
public void save(SaveRequest request) {
    // Gate：患者与就诊须同属一次接诊，防止跨就诊误写入医嘱
    PatientEncounterConsistencyGate.assertSame(request.getPatientId(), request.getEncounterId());
    // Pre：请求转为领域命令，不含 HTTP 层字段
    SaveAdviceCommand cmd = mapper.toCommand(request);
    // Cmd：领域层负责不变量与事件时序
    domainService.saveAdvice(cmd);
}
```

---

## 4. JavaDoc：什么叫「严重不足」（禁止）

```java
// BAD
/** 保存。 */
public SaveResponse save(SaveRequest request) { ... }

// BAD
/** {@inheritDoc} */
@Override
public RowDto toRow(QueryModel model) { ... }

// GOOD
/**
 * 保存门诊医嘱草稿行（IF-xxx，AS §4.2）。
 * 已提交行不可覆盖；失败时抛出 {@link BusinessException}，错误码与 AS 一致。
 *
 * @param request 含就诊主键、草稿行列表；须通过 Gate 校验
 * @return 保存后的行 id 与展示状态
 */
public SaveResponse save(SaveRequest request) { ... }
```

**DTO 字段**：

```java
// BAD
private Long patientId;

// GOOD
/** 患者主键 {@code adm_patient.id}，须与 request 中 encounter 归属一致。 */
private Long patientId;
```

---

## 5. 成码顺序（注释不得后置）

1. 读 DM/SDD/AS-SDD 时，在草稿中标记：哪些 **Gate/BR/状态迁移** 必须在 `//` 里点明。  
2. **写逻辑的同时写注释**——禁止先提交大段无注释代码。  
3. **注释补全第二遍（强制）**：TOUCHED 文件全部写完后，**仅做注释 pass**——逐文件打开，按 §1 表补全，再跑 [checklist 注释检索](../checklist.md#p0-注释检索touched-必跑)。  
4. 注释 pass 未通过 → **不得**进入最终汇报。

---

## 6. 密度底线（编排类方法，可量化自检）

对 TOUCHED 的 `*AppService` / `*DomainService` / `*QueryService`（及同轮修改的 Invoker/Assembler 等）中**本轮新增或修改的每个方法**（含 **`private` / `static`**，见 **§1.1**）：

| 检查项 | 底线 |
|--------|------|
| 方法头 JavaDoc | 必须有（`public`/`protected`/**`private`** 同等）；`@param` 与参数个数一致；有返回值则必有 `@return` |
| 阶段 `//` | 方法体 **≥4 行有效逻辑** 时，至少 **2 条**业务向 `//`（含 Gate/Pre/Cmd/Asm 或等价） |
| `if` / `else if` / `else` | **每条分支前** 1 条业务向 `//`（说明「满足/违反什么业务条件」） |
| `for` / `while` | 循环入口 1 条（说明遍历对象及业务目的） |
| 实体/DTO 新增字段 | 每个字段 1 条 JavaDoc |

**头重脚轻禁止**：方法头 JavaDoc 很长，但 `if` 分支体无 `//` → **不合格**。  
**私有漏注禁止**：仅 public 有注、`private` 无注 → **不合格**（§1.1）。

---

## 7. 改码（增量）时

- **只改一行逻辑**也须检查：新增/变更的分支是否补了 `//`；方法签名变更是否更新 `@param`/`@return`。  
- 新增或改动的 **`private` 方法**须同步补/改 JavaDoc（§1.1）；禁止「private 下次再写」。  
- **禁止**「本轮只改 bug，注释下次补」——注释是同一 PR/同一轮交付的一部分。

---

## 8. 交付汇报必须带注释摘要

最终汇报中须含 **「注释专闸」** 小节（3～5 条即可）：

- TOUCHED 编排类文件名 + 已补 Gate/Pre/Cmd/Asm 的方法名  
- **已补 JavaDoc/JSDoc 的 `private`/本地函数**（或写明「本轮无新增 private」）  
- 已写字段 JavaDoc 的 DTO/实体名  
- 注释 pass 已执行（是/否）；§1.1 私有函数检索已过（是/否）

未写该小节 → 视为注释门禁未过。

---

## 9. 仓库真实锚点（成码时对照，勿再犯）

| 模式 | 存量参考 | 常见问题 |
|------|----------|----------|
| DTO 字段注释合格 | `SaveDeviceAdviceDraftLinePayload`、`PrepickedProductKeyDto` | 类/字段 JavaDoc + IF 编号 |
| Request 新增字段易漏注 | `PageInpatientDoctorStationMedicationAdviceRequest` 中 `tcmOnly`/`dispenseStatusEnumIn` 等 | **仅有 @Schema** → 须补 JavaDoc（见 anti-patterns **§C2**） |
| AppService 方法头有注、体内缺注 | `DoctorStationAdviceAppService#listActivityExecutionLocations` | 长循环/if 无 `//` → 见 anti-patterns **§C3** |
| AppService 合格示例 | `DoctorStationAdviceAppService#initDoctorStationAdvice` | Gate + Pre 业务向 `//` |

**增量成码**：新增字段/方法时，以「合格示例」为下限，不得低于「易漏注」行的密度。
