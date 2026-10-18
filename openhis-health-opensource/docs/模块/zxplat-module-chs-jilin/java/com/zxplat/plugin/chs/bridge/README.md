# chs-jilin-bridge

HIS ↔ 吉林省医保平台 Extension 集成层。

## 双根包

| 根包 | 职责 |
|------|------|
| `com.zxplat.plugin.chs.bridge.integration.insurance.*` | ExtensionImpl、IntegrationMapper（HIS 边界） |
| `com.zxplat.plugin.chs.bridge.*` | ContextLoader、Orchestrator、Invoker |

## 能力切片

| 包 | README | Extension |
|----|--------|-----------|
| `outpatient/` | [README](./outpatient/README.md) | 门诊收银/挂号 |
| `inpatient/` | [README](./inpatient/README.md) | 住院收银 |
| `benefit/` | [README](./benefit/README.md) | 读卡/待遇 |
| `medicalinfo/` | [README](./medicalinfo/README.md) | 5201–5301 信息查询 |
| `reconciliation/` | [README](./reconciliation/README.md) | 3201–3209A 对账清算 |
| `settlement/` | [README](./settlement/README.md) | 2601 落账冲正 |
| `inventory/` | [README](./inventory/README.md) | 3501–3506 上报 |
| `medcas/` | [README](./medcas/README.md) | 4401 病案 |
| `catalog/` | — | 契约读取实现 |

## 端口约定（P9）

| 类型 | 定义 | 实现 |
|------|------|------|
| HIS SPI | `health.infrastructure.integration.insurance` | `health.integration.insurance.*ExtensionImpl` |
| Command enrichment | `chs.domain.{cap}.loader` | `chs.bridge.{cap}.context` |
| 单 infno 出站 | `chs.domain.*.access`（可选） | `chs.bridge.{cap}.invoker` |

**禁止**：`bridge/port/`、`health-module-support`、AppService 入 bridge。

脚本：`scripts/p9-bridge-restructure.ps1`

## JavaDoc 约定

与 application 层对齐，注释说明「做什么、调谁、副作用」，不写实现细节。

| 类型 | 类注释 | 方法注释 |
|------|--------|----------|
| `*ExtensionImpl` | SPI 接口、Health 调用方 AppService、Bridge 委托目标 | 每个 `@Override public` 一行：infno 或「本地」+ 副作用 |
| `*Invoker` | `【infno】` + 业务名；`<p>出站：InsuranceGatewayClient；副作用：读/写 yb_* 表</p>` | `invoke` 等入口可省略（类注释已覆盖） |
| `*Orchestrator` | 多步链路（列出 Invoker）；**本类不调 gateway** | 公开入口方法一行 |
| `*ContextLoader` | HIS  enrichment 范围、health-domain 依赖、实现的 `domain.loader` 接口 | 公开 enrich/build 方法 |
| `*Assembler` / `*Mapper` | 映射方向（HIS↔Command、Extension↔Domain）；**无 IO** | 非显然的 public 静态方法 |

infno 书写：`【2206】` 而非 `【206】`；编排链示例：`【2201→2203→2204→2206】`。
