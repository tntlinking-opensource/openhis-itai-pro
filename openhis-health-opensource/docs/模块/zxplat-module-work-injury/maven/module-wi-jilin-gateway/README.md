# module-wi-jilin-gateway

吉林省工伤 gateway：**仅 scheme**（无 HTTP client；出站经 OpenHIS.Win）。

## 包结构（P3 ✅）

```text
com.zxplat.plugin.wi.gateway.scheme/
  register/model/     Wi9102*、Wi2301*
  register/parser/    Wi9102OutputParser、Wi2301OutputParser
  settle/model/       Wi2201–Wi2208*
  settle/parser/      Wi2206OutputParser、Wi2207OutputParser
  recon/model/        Wi1320–1322*
  shared/model/       WiInfnoConstants、WiPhase、WiJsonSupport、…
  shared/assembler/   WiPlatformInputAssembler
```

P4 ✅ `{cap}/model/` · scheme 字段注释对照 [`10-P0-infno字段映射.md`](../../../docs/模块/zxplat-module-work-injury/10-P0-infno字段映射.md)。

## JavaDoc 约定

| 类型 | 说明 |
|------|------|
| `Wi*Input` / `Wi*Output` | `【2206】` 等 infno 前缀 + 10-P0 章节引用 |
| `WiPlatformInputAssembler` | 类注释写「Whale 只下发 input，Win 注入外壳」；`assemble*` 写 `@param` |
| `*OutputParser` | `【infno】` + 「Win output Map → scheme DTO」 |
| `WiInfnoConstants` | 每个 infno 常量一行中文 |
| `WiPhase` / `WiNextAction` | 编排枚举；枚举值一行 |

## 脚本

`scripts/wi-p3-scheme-cap.ps1` · `scripts/wi-p4-scheme-model.ps1`
