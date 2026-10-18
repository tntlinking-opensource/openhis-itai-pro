# zxplat-module-emr 结构优化手册

> **类型**：领域型（无 gateway / host / infno scheme）  
> **对照样板**：`docs/模块/zxplat-module-chs-jilin/OPTIMIZATION-PLAYBOOK.md`（Wave3 待迁） §10 领域型变体  
> 架构真源：`docs/模块/zxplat-module-emr/17-zxplat-module-emr-架构-v3.md`

---

## 0. 模块画像

| 项 | 电子病历（EMR） |
|----|----------------|
| 模块类型 | **领域型** |
| 出站方式 | **无**外部平台 HTTP |
| application 层 | 有 `module-emr-application`（文档/模板/元数据 AppService、Flyway、PDF） |
| host 层 | **无** |
| cap 切片 | document · template · metadata · diagnosis · pdf |
| 当前 Java 规模 | domain 20 · bridge 11 · application 83 ≈ **114** |
| Maven 子模块 | **`module-emr-*`**（P17 ✅ 自 `emr-*` 重命名） |

### 与 CHS playbook 的差异

| CHS 约定 | EMR 处理 |
|----------|----------|
| gateway / host / scheme | **跳过** P10 全系列 |
| application `invoker/` | **无** GatewayClient；跳过 P7–P8 Invoker |
| bridge 端口 | `EmrAdmissionContextLoader` 在 domain `loader/`；bridge `context/` 实现 |
| Orchestrator | `EmrDiagnosisSyncOrchestrator` 保留 bridge `diagnosis/workflow/` |
| contracts | **已废止**；loader 端口在 domain ✅ |

---

## 1. 当前问题 → 目标

| 问题 | 现状 | 目标 |
|------|------|------|
| v3 脚手架 | 无 contracts、Extension 链、Enforcer | ✅ 已落地 |
| domain cap | 扁平 `entity/`、`repository/`、`service/`、`metadata/` | `{cap}/entity|repository|service` + `loader/` 端口 |
| bridge 包 | `context/`、`diagnosis/` 部分切片 | `metadata/context/`、`diagnosis/workflow/`、`mrms/` Extension |
| application | 多数页有 `request/response`；service 内局部 `*Dto` 构建 | 禁止包名 `dto`；统一 `{cap}/service|request|response` |
| 元数据链 | `EmrMetadataContextBuilder` → loader | ✅ 保持 |
| bridge→application PDF | `EmrDocumentExtensionImpl` 直依赖 application | ✅ domain `EmrPdfRenderer` + Enforcer |
| 文档 / 脚本 | 根 README | 各层 README + 本 playbook + `scripts/` |

**成功标准**：住院诊断回写链 Extension → Orchestrator → DocumentManager 不超过 3 次点击。

---

## 2. cap 切片表（bounded context）

| cap | 场景 | domain 核心 | bridge 归属 | application 归属 |
|-----|------|-------------|-------------|------------------|
| **document** | 文档 CRUD、版本 | `DocumentManager`、`DocumentStructureManager` | `EmrDocumentExtensionImpl` | `document/service` |
| **template** | 模板维护 | `TemplateRepository`、实体 | — | `template/service` |
| **metadata** | 元数据字段、解析 | `metadata/`、`MetadataFieldDefRepository` | `EmrAdmissionContextLoaderImpl`、`EmrDiagnosisMetadataContributor` | `metadataField/`、`encounterMetadata/`、`metadata/support/` |
| **diagnosis** | 住院诊断同步 | `DocumentManager` 写回 | `EmrDiagnosisSyncOrchestrator` + `EmrAdmissionDiagnosisSyncExtensionImpl` | —（zxplat-app 触发） |
| **pdf** | 文档 PDF 渲染 | `document/access` `EmrPdfRenderer` | `EmrDocumentExtensionImpl` 注入端口 | `EmrPdfRendererAdapter` |
| **mrms** | 病案相关 Extension | — | `EmrAdmissionConditionExtensionImpl` 等 | — |

步骤链真源：`EmrAdmissionDiagnosisSyncExtensionImpl` → `EmrDiagnosisSyncOrchestrator` → `DocumentManager`。

---

## 3. 推荐阶段（EMR 定制 P 表）

| 优先级 | 代号 | 动作 | 脚本 | 状态 |
|--------|------|------|------|------|
| 1 | **P0** | 各层 README + 本 playbook + `scripts/` | — | ✅ |
| 2 | **P2** | domain 扁平包 → `{document|template|metadata}/entity|repository|service` | `emr-p2-domain-cap.ps1` | ✅ |
| 3 | **P5–P6** | bridge `diagnosis/` → `diagnosis/workflow/`；`context/` → `metadata/context/` | `emr-p5-bridge-cap.ps1` | ✅ |
| 4 | **P9** | bridge Extension 按 cap 分包；loader 实现归 `metadata/context/` | `emr-p9-bridge-context.ps1` | ✅ |
| 5 | **P1–P3** | application 扫尾内联 `*Dto` → `response/` 类型 | `emr-p1-app-rename.ps1` | ✅ N/A |
| 6 | **P17** | Maven `emr-*` → `module-emr-*` + bridge 禁 application | `emr-p17-maven-rename.ps1` | ✅ |
| 7 | **Doc** | JavaDoc（loader 端口、Orchestrator 副作用） | — | ✅ |

**明确跳过**：gateway P10 全系列、host P10-A、application P7–P8 Invoker。

**v3 已落地**：`EmrAdmissionContextLoader` 端口、诊断同步链、Enforcer ✅。

---

## 4. 目标包结构

### domain

```text
com.zxplat.plugin.emr.domain/
  document/entity|repository|service|access/
  template/entity|repository/
  metadata/entity|repository|model/
  loader/               EmrAdmissionContextLoader（端口）
  share/                错误码、枚举
  enums/
```

### bridge

```text
com.zxplat.plugin.emr.bridge.integration/
  diagnosis/            EmrAdmissionDiagnosisSyncExtensionImpl
  mrms/                 EmrDocumentExtensionImpl、EmrAdmissionConditionExtensionImpl

com.zxplat.plugin.emr.bridge/
  metadata/context/     EmrAdmissionContextLoaderImpl、EmrDiagnosis* 组装/解析
  diagnosis/workflow/   EmrDiagnosisSyncOrchestrator
  config/
```

### application

```text
com.zxplat.plugin.emr.application/
  document/service|request|response/
  documentType/service|request|response/
  template/service|request|response/
  metadataField/service|request|response/
  encounterMetadata/service|request|response/
  metadata/support/     EmrMetadataContextBuilder、NoOpEmrAdmissionContextLoader
  pdf/                  渲染配置与模型
  config/
```

---

## 5. 脚本化迁移

1. **P2 domain**：`entity/Document*.java` 等迁入 `document/entity/`（改动面大，分 cap 脚本）
2. **P5 bridge**：`EmrDiagnosisSyncOrchestrator` 迁至 `diagnosis/workflow/`
3. **无 gateway 脚本**：领域型模块勿引入 `p10-*`
4. **每步** `mvn compile -pl zxplat-module-emr -am`
5. PowerShell **禁止 UTF-8 BOM**；`scripts/fix-bom.ps1` ✅

```text
zxplat-module-emr/scripts/
  emr-p2-domain-cap.ps1      ✅
  emr-p5-bridge-cap.ps1      ✅
  emr-p9-bridge-context.ps1  ✅
  emr-p1-app-rename.ps1      ✅
  fix-bom.ps1                ✅
```

---

## 6. 检查清单

### 启动前

- [ ] 确认 **不** 引入 gateway/host
- [ ] cap 表与 Extension 链一致
- [ ] loader 端口定义在 domain，实现仅在 bridge

### 每阶段

- [ ] 脚本 + 层 README 更新
- [ ] `mvn compile -pl zxplat-module-emr -am`
- [x] bridge 无 `zxplat-module-support`、`module-emr-application` 依赖（PDF 经 domain `EmrPdfRenderer` 端口）
- [x] application Enforcer 禁 `module-emr-bridge`
- [x] ArchitectureTest：application/bridge 互禁
- [ ] 无 UTF-8 BOM 编译错误

### 收尾

- [ ] domain 按 cap 分包（非扁平 `entity/`）
- [ ] application 无 `dto` 包名
- [ ] 本目录 `13-实现状态与文档对照` 与包名一致

---

## 7. 已落地阶段一览

| 阶段 | 状态 | 要点 |
|------|------|------|
| v3 迁移（无 contracts） | ✅ | `zxplat-module-emr` 替换 `zxplat-module-emr` |
| 元数据 loader 链 | ✅ | `EmrAdmissionContextLoader` + bridge 实现 |
| 住院诊断同步 | ✅ | Orchestrator + ExtensionImpl |
| 结构优化 P0–P9、P17 | ✅ | document cap + PDF 端口 + bridge 禁 application |

---

## 8. 反模式速查

| 反模式 | 应改为 |
|--------|--------|
| EMR 建 gateway 出站 | 领域型：bridge 读 zxplat-domain |
| application 直接改 health 实体 | 经 Extension / domain Service |
| bridge `context/` 无限膨胀 | 按 cap 分包 `metadata/context/` |
| 恢复 `emr-contracts` | domain `loader` 端口 |
| application 依赖 bridge 类型 | application 仅注入 domain 端口（同 Boss P16 思路） |
| service 内大量 `*Dto` 类名 | `request/response` 独立类型 |

---

## 9. 与 CHS 差异说明

| 维度 | CHS | emr |
|------|-----|-----|
| 层数 | 六层 | **四层** |
| 出站 | 医保 HTTP | **无**；读 HIS 元数据、写 EMR 文档表 |
| Invoker | 有 | **无** |
| 编排 | 多 infno Workflow | 诊断同步 **Orchestrator**（单进程写回） |
| UI | zxplat-app 或独立 | `module-emr-application` 独立页 + zxplat-app Extension 触发 |

---

## 10. 参考文件

| 文件 | 用途 |
|------|------|
| [`README.md`](./README.md) | Module 总览、Extension 链 |
| `docs/模块/zxplat-module-chs-jilin/OPTIMIZATION-PLAYBOOK.md`（Wave3 待迁） | 通用方法论 §10 领域型 |
| `docs/模块/zxplat-module-booking/OPTIMIZATION-PLAYBOOK.md`（Wave3 待迁） | 同类领域型 playbook |
| [`../../docs/模块/zxplat-module-emr/13-实现状态与文档对照.md`](../../docs/模块/zxplat-module-emr/13-实现状态与文档对照.md) | 文档对照 |
| `docs/模块/zxplat-module-emr/17-zxplat-module-emr-架构-v3.md` | 架构真源 |
