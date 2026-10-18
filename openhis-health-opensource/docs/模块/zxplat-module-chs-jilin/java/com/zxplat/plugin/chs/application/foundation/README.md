# foundation 应用层

非业务单据页的基础能力：读卡、签到、会话兼容、平台字典。

| 子包 | AppService | HTTP 前缀 | AS |
|------|------------|-----------|-----|
| `person` | `InsurancePersonAppService` | `insurancePerson` | FND-001 |
| `sign` | `InsuranceSignAppService` | `insuranceSign` | FND-002 |
| `session` | `InsuranceSessionAppService` | `insuranceSession` | FND-002（兼容） |
| `platformdict` | `InsurancePlatformDictAppService` | `insurancePlatformDict` | FND-003 |

**文档**：`docs/模块/zxplat-module-chs-jilin/AppService/AS-CHS-FND-*.md`

## 约定

- 各子包独立 `request/`、`response/`、`mapper/`、`invoker/`、`support/`（按需）
- `session` 仅保留 deprecated 路由，委托 `sign`
