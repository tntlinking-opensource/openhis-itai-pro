# zxplat-module-work-injury · 工伤联网对接（WORK_INJURY）

> **文档目录**：`docs/模块/zxplat-module-work-injury/`（与代码工程同名）  
> **方案码**：`InsuranceSchemeCode.WORK_INJURY`  
> **状态**：二期 — 架构与 MVP 已按院点生产日志及 Q1～Q20 固化，待编码  
> **★ v3 真源**：[17-zxplat-module-work-injury-架构-v3.md](./17-zxplat-module-work-injury-架构-v3.md) · [13-实现状态与文档对照.md](./13-实现状态与文档对照.md)  
> **代码**：[`zxplat-module-work-injury/代码仓库-README.md`](../zxplat-module-work-injury/代码仓库-README.md)（独立 Module，非 `zxplat-module-chs-jilin` 子目录）  
> **省医保上级**：[17-Whale医保多方案扩展骨架.md](../zxplat-module-chs-jilin/17-Whale医保多方案扩展骨架.md)（文档目录 [zxplat-module-chs-jilin/](../zxplat-module-chs-jilin/) · 代码 `zxplat-plugin/zxplat-module-chs-jilin/`）  
> **业务背景**：[08-概要设计.md](./08-概要设计.md)（v2 HLD，包名以 17 为准） · [15-编码规范与Cursor规则对照.md](./15-编码规范与Cursor规则对照.md)

## 1. 与省医保差异（已确认）

| 项 | 工伤 | 省医保 |
|----|------|--------|
| 接入 | **纯 DLL** `JLSiInterface.dll` | FSI HTTP + 国密 |
| 签到 | **9001**（DLL） | 9001（HTTP） |
| `fixmedins_code` | 院内/HIS **`H22010402403`**；DLL 出站 **`JL2201992002`** | 同左 |
| `recer_sys_code` | **`LNYTH`**（院方确认） | `YBXT` 等 |
| 费用性质 | `WORKER_INSURANCE` | `PROVINCIAL_INSURANCE` |
| 持久化 | 共用 `yb_invoke_log`、`yb_settlement_record`；**不用** `yb_coordination` | `CHS_NATIONAL_JILIN` |
| 进销存 350x | **不走工伤 DLL** | FSI 3501–3513 |

## 2. 设计文档

| 文档 | 内容 |
|------|------|
| [01-接入架构决策.md](./01-接入架构决策.md) | 不共用 FSI；**OpenHIS.Win 出站**；编排分流 |
| [02-infno清单.md](./02-infno清单.md) | 交易清单、Whale 产品链（**9102 P0 + 2301 兜底**） |
| [03-fin_contract映射示例.md](./03-fin_contract映射示例.md) | 双契约配置、`bus_no` / account 样例 |
| [04-院点生产日志与MVP范围.md](./04-院点生产日志与MVP范围.md) | **NEUSI 日志统计、P0/P1 边界** |
| [05-用户操作说明.md](./05-用户操作说明.md) | **用户流程、BR-WI 已定稿** |
| [14-院方确认决策.md](./14-院方确认决策.md) | **Q1～Q20 决策真源** |
| [06-扩展组件与隔离策略.md](./06-扩展组件与隔离策略.md) | **可选模块、NoOp、未装回归** |
| [07-OpenHIS.Win工伤本地桥接.md](./07-OpenHIS.Win工伤本地桥接.md) | **OpenHIS.Win.WorkInjury、HTTP API** |
| **[17-zxplat-module-work-injury-架构-v3.md](./17-zxplat-module-work-injury-架构-v3.md)** | **v3：独立 Module、module-wi-jilin-bridge、OpenHIS.Win** |
| **[08-概要设计.md](./08-概要设计.md)** | **HLD（v2 业务背景）：Extension、invoke 调用链** |
| **[09-门诊工伤结算-详细设计.md](./09-门诊工伤结算-详细设计.md)** | **LLD：挂号/收费/逆流程/API** |
| **[10-P0-infno字段映射.md](./10-P0-infno字段映射.md)** | **P0 infno ↔ DTO** |
| **[11-客户端invoke会话-详细设计.md](./11-客户端invoke会话-详细设计.md)** | **prepare/submit 会话** |
| **[12-成码前Checklist.md](./12-成码前Checklist.md)** | **开码门禁** |
| **[15-编码规范与Cursor规则对照.md](./15-编码规范与Cursor规则对照.md)** | **成码合规：.cursor Rule/Skill 索引** |
| **[16-成码提示词模板.md](./16-成码提示词模板.md)** | **复制即用的 Cursor 成码提示词** |
| **[17-工伤代码清单与评审测试.md](./17-工伤代码清单与评审测试.md)** | **全量代码索引、评审顺序、T1–T12 联调矩阵** |
| **[吉林朝阳中医院/工伤联调测试清单.md](../吉林朝阳中医院/工伤联调测试清单.md)** | **院点逐项勾选联调（前置 / T1～T12 / 最小验收包）** |
| [13-实现状态与文档对照.md](./13-实现状态与文档对照.md) | 编码进度 |

## 3. 接口规范参考

- [参考/吉林省工伤保险信息系统协议机构接口开发手册-V1.3.md](./参考/吉林省工伤保险信息系统协议机构接口开发手册-V1.3.md) — **V1.3 合订本**

## 4. 院点 MVP 一句话

**P0**：`9001/9002` + `1101` + **`9102`** + `2301` + `2201/2203/2205?/2204/2206/2207` + `2202` + **`2208`** + `1320/**1321**/1322` + 独立 `fin_contract` + **可选扩展模块**（未装不影响现网）。  
**不做**：`2302`、`2209`、`1323`、住院 `240x`、工伤侧 `350x`（进销存走省医保）。

**产品链与 NEUSI 差异**：老日志 **9102=0、挂号即 2301**；Whale **双次 1101、9102 在挂号、2301 双入口、2301 后再 9102、挂号费一律自费、无 qualification_id 阻断 2206、首笔可跳过 2205**（见 [05](./05-用户操作说明.md)、[14](./14-院方确认决策.md)）。

## 5. 代码位置（v3）

```text
zxplat-plugin/zxplat-module-work-injury/
  module-wi-jilin-domain/     com.zxplat.plugin.wi.domain（{cap}/command|result + share/）
  module-wi-jilin-gateway/    scheme/{cap}/model + assembler/parser
  module-wi-jilin-bridge/     ExtensionImpl + {cap}/workflow + invoker
  module-wi-jilin-starter/    Flyway wi/ + 装配

结构优化真源：[OPTIMIZATION-PLAYBOOK.md](../zxplat-module-work-injury/OPTIMIZATION-PLAYBOOK.md)

openhis-pro-win/OpenHIS.Win.WorkInjury/   # DLL 出站（见 07）
health-app/.../WorkInjuryInvokeAppService # prepare/submit UI（见 11）
```

详见 [17-zxplat-module-work-injury-架构-v3.md](./17-zxplat-module-work-injury-架构-v3.md)。

## 6. 实施前确认（院点 2026-06）

- [x] 工伤平台接口规范文档
- [x] 不与省医保共用 FSI 入口（纯 DLL，见 [01](./01-接入架构决策.md)）
- [x] 结算/撤销/对账 infno 清单（见 [02](./02-infno清单.md)、[04](./04-院点生产日志与MVP范围.md)）
- [x] `fin_contract` / account 映射规则（见 [03](./03-fin_contract映射示例.md)）
- [x] 用户操作定稿 C1～C4（见 [05](./05-用户操作说明.md)）
- [x] 院方 Q1～Q20 确认（见 [14](./14-院方确认决策.md)）
- [x] OpenHIS.Win 桥接设计（见 [07](./07-OpenHIS.Win工伤本地桥接.md)）
- [x] HLD/LLD 成码文档（见 [08～12](./12-成码前Checklist.md)）
- [ ] 文档评审签字（12 §7）
- [ ] Whale / Win 编码与 WI 联调（须遵守 [15-编码规范与Cursor规则对照.md](./15-编码规范与Cursor规则对照.md)）
