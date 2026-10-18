# chs-jilin-domain

吉林省医保模块领域层：`yb_*` 表实体、DomainService、QueryService、Command/Result 与出站端口。

## 边界

| 允许 | 禁止 |
|------|------|
| `yb_*` 读写、结算/上传状态机 | `import com.zxplat.plugin.chs.gateway.*` |
| 出站端口 `access` / 上下文 `loader` | health-domain 实体直接修改 |
| 值对象、枚举、不变量校验 | application / bridge 实现细节 |

HIS 主数据（就诊、收费项、品目）在 health-domain，由 bridge/application 组装 Command 后传入。

## 包结构

| 包 | 职责 |
|----|------|
| `{cap}/entity` | JPA 实体 ↔ `yb_*` 表 |
| `{cap}/service` | `*DomainService` 写侧、`*QueryService` 读侧 |
| `{cap}/repository` | Spring Data / 自定义查询 |
| `{cap}/command` `result` | 编排入参 / 出站结果 |
| `{cap}/access` | 跨模块出站端口（bridge 实现） |
| `{cap}/loader` | HIS→Command enrichment 端口（bridge 实现） |
| `{cap}/support` | 无 IO 的领域规则与回填 |
| `share` | 错误码、文本工具 |
| `contract` | 契约快照值对象 |

## JavaDoc 约定

与 bridge 对齐，注释说明「做什么、依赖哪张表、副作用」。

| 类型 | 类注释 | 方法注释 |
|------|--------|----------|
| `*DomainService` | 负责的 `yb_*` 表及写侧职责 | 每个 public/private：`@param` + `@return` |
| `*QueryService` | 读侧查询范围 | 同上 |
| `access` / `loader` | 端口用途 + bridge 实现类 | 每个接口方法 |
| `*Support` / Validator | 业务规则，**无 IO** | 含 private static |
| `entity` / `command` / `result` | 表名或业务场景一行 | 不写 getter/setter |
| `enum` | 枚举业务含义 | — |
| `repository` | 查询职责 | 自定义查询方法 |

出站端口示例：`Med5206QueryInvoker` — application 与 bridge Extension 共用，避免重复 Invoker。
