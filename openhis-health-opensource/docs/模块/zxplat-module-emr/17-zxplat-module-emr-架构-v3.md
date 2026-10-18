# zxplat-module-emr 架构 v3 — Domain-Only

> **文档性质**：`zxplat-module-emr` 重架构决策  
> **更新**：2026-09-24（合入 ZXPlat Wave3 M30）  
> **文档目录**：`docs/模块/zxplat-module-emr/`  
> **代码**：`zxplat-plugin/zxplat-module-emr/` · [代码仓库-README.md](./代码仓库-README.md)

---

## 1. 与历史版本差异

| 项 | 历史 | 现行（ZXPlat） |
|----|------|----------------|
| 父工程 | `zxplat-module-emr` / `zxplat-module-emr` | **`zxplat-plugin/zxplat-module-emr`** |
| HIS 集成层 | `module-emr-bridge` | **`module-emr-bridge`** |
| 契约层 | `emr-contracts` | **无**；端口在 `module-emr-domain` |
| ExtensionImpl 包 | `com.zxplat.domain.infrastructure.integration.emr` | **`com.zxplat.plugin.emr.bridge.integration`** |
| 业务包 | `com.zxplat.plugin.emr` | **`com.zxplat.plugin.emr`** |
| 领域依赖 | `health-domain` | **`zxplat-domain`** |
| 框架依赖 | `zxplat-framework` | **`zxplat-framework`** |
| 开关 | `zxplat.modules.emr.enabled` | **`zxplat.modules.emr.enabled`** |
| gateway | 无 | 仍 **无** |

---

## 2. bridge 读写 zxplat-domain

```text
EmrAdmissionContextLoaderImpl
  → PatientQueryService / EncounterQueryService / PractitionerQueryService
  → OrganizationQueryService
  → EmrDiagnosisMetadataContributor

EmrDiagnosisSyncOrchestrator
  → EncounterQueryService / ConditionQueryService / ConditionDefinitionQueryService
  → DocumentManager / DocumentStructureManager（module-emr-domain）
```

**禁止**：`zxplat-module-support`、`module-emr-application`、bridge 内自写 SQL。

---

## 3. 模块依赖

```text
module-emr-domain        → zxplat-framework
module-emr-bridge        → module-emr-domain, zxplat-domain, zxplat-framework
module-emr-application   → module-emr-domain, zxplat-framework
module-emr-starter       → bridge + application + domain
```

bridge enforcer 禁止：`module-emr-application`。  
application enforcer 禁止：`module-emr-bridge`。

---

## 4. 包结构（与源码一致）

```text
module-emr-domain/
  com.zxplat.plugin.emr.domain/
    document/  template/  metadata/  loader/  enums/

module-emr-bridge/
  com.zxplat.plugin.emr.bridge/
    metadata/context/  diagnosis/workflow/  config/  module/
  com.zxplat.plugin.emr.bridge.integration/
    diagnosis/  mrms/

module-emr-application/
  com.zxplat.plugin.emr.application/
    document/ documentType/ template/ metadataField/
    encounterMetadata/ metadata/support/ pdf/ config/
```

---

## 5. 明确不做

- **contracts 层**：不引入 `emr-contracts`
- **gateway 层**：EMR 无出站 HTTP
- **bridge 依赖 support**：HIS 读写只经 `zxplat-domain` 公开服务

---

## 6. 迁移状态（M30）

| 阶段 | 状态 |
|------|------|
| 源码迁入 `zxplat-plugin/zxplat-module-emr` | ✅ |
| 包名改写 `com.zxplat.plugin.emr.*` | ✅ |
| 同伴文档 `docs/模块/zxplat-module-emr` | ✅ |
| `compile` + ArchitectureTest | ✅（2026-09-24） |
| `zxplat-app` profile `with-emr` | ⏳ Wave4 |
