# module-wi-jilin-domain

吉林省工伤领域层：`yb_wi_*` 表实体、DomainService、Command/Result 与出站端口。

## 边界

| 允许 | 禁止 |
|------|------|
| `yb_wi_*` 读写、invoke 会话状态 | `import com.zxplat.plugin.wi.gateway.*` |
| Command/Result、`access` / `loader` 端口 | bridge/application 实现细节 |
| 复用 CHS `yb_invoke_log` / `yb_settlement_record` | 直接改 health-domain 实体 |

HIS 主数据（就诊、收费项、品目）在 health-domain，由 bridge `ChsWorkInjurySettleContextLoader` 组装 `WiSettleContext` 后传入。

## 包结构（P2 ✅）

```text
com.zxplat.plugin.wi.domain/
  share/              WiErrorCodes、WorkInjuryConstants
  entity/ repository/ service/
  loader/             WorkInjurySettleContextLoader
  register/command|access/
  invoke/command|result/
  settle/command|result/
  recon/command/
```

## JavaDoc 约定

与 CHS domain 对齐，注释说明「做什么、依赖哪张表、副作用」。

| 类型 | 类注释 | 方法注释 |
|------|--------|----------|
| `*DomainService` | 负责的 `yb_*` 表及写侧职责 | 每个 public 方法：`@param` + `@return`；含 infno 时前缀 `【2206】` |
| `access` / `loader` | 端口用途 + bridge 实现类 | 每个接口方法 |
| `entity` / `command` / `result` | 表名或业务场景一行；关键字段单行 | 不写 getter/setter |
| `share` | 错误码/常量注册表 | 每个常量一行 |
| `repository` | 查询职责 | 自定义查询方法 |

出站端口示例：`Build2301PayloadInvoker` — UI 预览 2301 input 与 Extension 共用。

标杆类：`YbWiEncounterAnchorDomainService`、`YbWiPreSettleDomainService`、`Build2301PayloadInvoker`。

## 脚本

`scripts/wi-p2-domain-rename.ps1`
