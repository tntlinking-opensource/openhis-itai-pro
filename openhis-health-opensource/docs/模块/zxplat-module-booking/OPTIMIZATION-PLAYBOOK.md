# zxplat-module-booking 结构优化手册

> **类型**：领域型（无 gateway / host / infno scheme）  
> **对照样板**：[`zxplat-module-chs-jilin/OPTIMIZATION-PLAYBOOK.md`](../zxplat-module-chs-jilin/OPTIMIZATION-PLAYBOOK.md) §10 领域型变体  
> 架构真源：`docs/模块/zxplat-module-booking/17-zxplat-module-booking-架构-v3.md`

---

## 0. 模块画像

| 项 | 预约（booking） |
|----|----------------|
| 模块类型 | **领域型** |
| 出站方式 | **无**外部平台 HTTP；渠道通知 P2 占位（消息中心） |
| application 层 | 有 `module-booking-application`（多页 AppService、Flyway） |
| host 层 | **无** |
| cap 切片 | outpatient · inpatient/bed · schedule · slot · queue · channel · medtech · policy · appointment · healthcheckup · template · calendarexception |
| 当前 Java 规模 | domain 189 · bridge 29 · application 282 ≈ **500** |
| Maven 子模块 | **`module-booking-*`**（P17 ✅ 自 `booking-*` 重命名） |

### 与 CHS playbook 的差异

| CHS 约定 | booking 处理 |
|----------|--------------|
| gateway / host / scheme | **跳过** P10 全系列 |
| application `invoker/` | **无** GatewayClient；跳过 P7–P8 Invoker 阶段 |
| bridge Invoker | **无**；用 ExtensionImpl + Orchestrator + ContextLoader |
| Orchestrator | **保留**在 bridge `{cap}/workflow/`（HIS 多步查询/写回） |
| contracts | **已废止**；Command/Result 在 domain ✅ |

---

## 1. 当前问题 → 目标

| 问题 | 现状 | 目标 |
|------|------|------|
| v3 脚手架 | 无 contracts、4×Extension、Enforcer | ✅ 已落地 |
| domain cap | 多 cap 已有（`appointment/`、`schedule/`…）但根级 `dto/`、`command/`、`query/` 混放 | 各 cap 内 `command|result|entity|service`；根级仅 `share/` |
| bridge 包 | `orchestration/`、`internal/`、`reader/`、`writer/`、`service/` 扁平 | `{cap}/workflow/` + `{cap}/context/`；reader/writer → context loader |
| application | 多数页已有 `request/response` | 扫尾根级 `dto`；统一 `{cap}/service|request|response|mapper` |
| 渠道通知 | `BookingChannelNotifyAccess` 占位 | domain `access` + bridge no-op ✅ |
| 文档 / 脚本 | 根 README | 各层 README + 本 playbook + `scripts/` |

**成功标准**：按预约场景（门诊/床位/医技）找 Extension → Orchestrator 不超过 3 次点击。

---

## 2. cap 切片表（bounded context）

| cap | 场景 | domain 核心 | bridge 归属 | application 归属 |
|-----|------|-------------|-------------|------------------|
| **outpatient** | 门诊预约查询/挂号 | `appointment/`、`slot/` | `OutpatientBooking*Extension` → `BookingOutpatient*Orchestrator` | `outpatient/`、`register/` |
| **inpatient** | 床位预约查询/占床 | `bed/` | `InpatientBedBooking*Extension` → `BookingInpatientBed*Orchestrator` | `bed/` |
| **schedule** | 排班、模板 | `schedule/` | ContextLoader + domain Service | `outpatient/`、`template/` |
| **slot** | 号源管理 | `slot/` | `BookingSlotReaderImpl`（→ `slot/context`） | `slot/` |
| **queue** | 叫号看板 | `queue/` | — | `queue/` |
| **channel** | 预约渠道 | `channel/` | 渠道通知 access（P2 占位） | `channel/` |
| **medtech** | 医技预约 | `medtech/` | `BookingServiceRequest*` | `medtech/` |
| **policy** | 预约策略 | `policy/` | — | `policy/` |
| **healthcheckup** | 体检预约 | `healthcheckup/` | — | `healthcheckup/` |
| **calendarexception** | 日历例外 | `calendarexception/` | — | `calendarexception/` |
| **masterdata** | 主数据查询端口 | `masterdata/service` | bridge `service/*QueryServiceImpl` | `share/` |

步骤链真源：4× `booking-bridge/orchestration/*Orchestrator`（迁移后 → `{cap}/workflow/`）。

---

## 3. 推荐阶段（booking 定制 P 表）

| 优先级 | 代号 | 动作 | 脚本 | 状态 |
|--------|------|------|------|------|
| 1 | **P0** | 各层 README + 本 playbook + `scripts/` | — | ✅ |
| 2 | **P2** | domain 根级 `dto/`、`command/`、`query/` 收入各 cap | `booking-p2-domain-cap.ps1` | ✅ |
| 3 | **P5–P6** | bridge `orchestration/` → `{cap}/workflow/` | `booking-p5-bridge-workflow.ps1` | ✅ |
| 4 | **P9** | bridge `internal/`、`reader/`、`writer/` → `{cap}/context/` | `booking-p9-bridge-context.ps1` | ✅ |
| 5 | **P1–P3** | application 扫尾 `dto` → `request/response` | `booking-p1-app-rename.ps1` | ✅ N/A |
| 7 | **P17** | Maven `booking-*` → `module-booking-*` + Enforcer/ArchTest | `booking-p17-maven-rename.ps1` | ✅ |
| 8 | **P2-channel** | 渠道通知 `BookingChannelNotifyAccess` 占位 | — | ✅ scaffold |
| 9 | **Doc** | JavaDoc + 各 cap README | — | ✅ |

**明确跳过**：gateway P10 全系列、host P10-A、application P7–P8 Invoker（无外部平台出站）。

**v3 已落地**：无 contracts、4×ExtensionImpl、Enforcer 禁 `health-module-support` ✅。

---

## 4. 目标包结构

### domain

```text
com.zxplat.plugin.booking.domain/
  outpatient/...          或并入 appointment + slot（保持现有 cap 名）
  appointment/command|entity|repository|service|queries/
  bed/entity|repository|service/
  schedule/command|entity|repository|service/
  slot/entity|repository|service/
  queue/ channel/ medtech/ policy/ healthcheckup/ calendarexception/
  channel/command|access/  BookingChannelNotifyAccess（P2-channel ✅）
  masterdata/service/     查询端口接口
  share/                  错误码、常量
```

### bridge

```text
com.zxplat.plugin.booking.bridge.integration/
  outpatient/             OutpatientBooking*ExtensionImpl
  inpatient/              InpatientBedBooking*ExtensionImpl
  mapper/

com.zxplat.plugin.booking.bridge/
  outpatient/workflow/    BookingOutpatientQueryOrchestrator 等
  inpatient/workflow/     BookingInpatientBed*Orchestrator
  slot/context/           BookingSlotReaderImpl（原 internal）
  masterdata/context/ service/
  channel/access/         NoOpBookingChannelNotifyAccess
  config/
```

### application

```text
com.zxplat.plugin.booking.application/
  {cap}/service|request|response|mapper/   bed、channel、medtech、outpatient、policy、queue、slot、template…
  share/                  ScheduleActorMasterDataValidator 等
  config/
```

---

## 5. 脚本化迁移

1. **P2 domain**：根级 `domain/dto/*` 按引用关系迁入各 cap `result/` 或 `queries/model/`
2. **P9 bridge**：`internal/*` + `reader|writer` 重命名为 `{cap}/context/*ContextLoader`
3. **无 gateway 脚本**：勿复制 CHS `p10-*`；仅 fork `p2-domain`、`p9-bridge` 变体
4. **每步** `mvn compile -pl zxplat-module-booking -am`
5. PowerShell **禁止 UTF-8 BOM**；`scripts/fix-bom.ps1` ✅

```text
zxplat-module-booking/scripts/
  booking-p2-domain-cap.ps1      ✅
  booking-p5-bridge-workflow.ps1 ✅
  booking-p9-bridge-context.ps1  ✅
  booking-p1-app-rename.ps1      ✅
  fix-bom.ps1                    ✅
```

---

## 6. 检查清单

### 启动前

- [ ] 确认 **不** 引入 gateway/host 子模块
- [ ] cap 表与 Extension 链（4 条）一致
- [ ] 渠道通知标为 P2 占位，勿假造 HTTP client

### 每阶段

- [ ] 脚本 + 层 README 更新
- [ ] `mvn compile -pl zxplat-module-booking -am`
- [x] bridge 无 `health-module-support`、`module-booking-application` 依赖
- [x] application Enforcer 禁 `module-booking-bridge`
- [x] ArchitectureTest：application/bridge 互禁
- [ ] 无 UTF-8 BOM 编译错误

### 收尾

- [ ] bridge 无 `orchestration/`、`internal/` 包段
- [ ] domain 无根级 `dto/` 泄漏
- [ ] docs `13-实现状态` 与包名一致

---

## 7. 已落地阶段一览

| 阶段 | 状态 | 要点 |
|------|------|------|
| v3 迁移（无 contracts） | ✅ | `zxplat-module-booking` 替换 `zxplat-module-booking` |
| 4×Extension + Orchestrator | ✅ | outpatient / inpatient 链 |
| ContextLoader 替代 9 Reader | ✅ | bridge `internal/*`（待 P9 归 cap） |
| 结构优化 P0–P9 | ✅ | domain cap + bridge workflow/context + 层 README |
| P2-channel 占位 | ✅ scaffold | `NoOpBookingChannelNotifyAccess`；编排器写成功后 notify |

---

## 8. 反模式速查

| 反模式 | 应改为 |
|--------|--------|
| 为预约建 gateway 模块 | 领域型：bridge ContextLoader 读 health-domain |
| application 注入外部 HTTP | 渠道通知走 domain `access` 端口 |
| bridge `internal/` 厚层 | `{cap}/context/*ContextLoader` |
| domain 根级 `dto/` | 各 cap `command|result|queries/model` |
| bridge 依赖 application | Enforcer 已禁；共享逻辑下沉 domain |
| 恢复 `booking-contracts` | domain Command/Result |

---

## 9. 与 CHS 差异说明

| 维度 | CHS | booking |
|------|-----|---------|
| 层数 | 六层 | **四层**（domain + bridge + application + starter） |
| 出站 | 医保 HTTP + infno | **无**；仅读/写 HIS + 内部预约表 |
| Invoker | bridge/application 调 GatewayClient | **无** Invoker |
| Orchestrator | 多 infno 页面 | bridge **workflow** 编排 HIS 查询/写回 |
| cap 对齐键 | infno | **bounded context**（门诊/床位/号源…） |

---

## 10. 参考文件

| 文件 | 用途 |
|------|------|
| [`README.md`](./README.md) | Module 总览、Extension 链 |
| [`../zxplat-module-chs-jilin/OPTIMIZATION-PLAYBOOK.md`](../zxplat-module-chs-jilin/OPTIMIZATION-PLAYBOOK.md) | 通用方法论 §10 领域型 |
| [`../zxplat-module-emr/OPTIMIZATION-PLAYBOOK.md`](../zxplat-module-emr/OPTIMIZATION-PLAYBOOK.md) | 同类领域型 playbook |
| [`../../docs/模块/zxplat-module-booking/13-实现状态与文档对照.md`](../../docs/模块/zxplat-module-booking/13-实现状态与文档对照.md) | 文档对照 |
| `docs/模块/zxplat-module-booking/17-zxplat-module-booking-架构-v3.md` | 架构真源 |
