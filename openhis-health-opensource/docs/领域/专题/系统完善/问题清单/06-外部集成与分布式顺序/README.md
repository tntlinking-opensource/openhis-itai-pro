# 外部集成与分布式顺序

共 **16** 条问题。

| 编号 | 标题 | 严重度 | 优先级 | 状态 |
| --- | --- | --- | --- | --- |
| [GAP-CHS-001](./GAP-CHS-001.md) | 同一 ACTIVE 预结算快照双 confirm（2207/2304） | **高危** | P0 | 待实施 |
| [GAP-CHS-002](./GAP-CHS-002.md) | 本地 Payment 已 SUCCESS 但平台 confirm 失败/超时 | **高危** | P0 | 待实施 |
| [GAP-CHS-003](./GAP-CHS-003.md) | 2208/2305 撤销与本地退费/反结顺序 | 中危 | P1 | 待实施 |
| [GAP-CHS-004](./GAP-CHS-004.md) | 预结算 refresh 与 confirm 竞态 | 中危 | P1 | 待实施 |
| [GAP-CHS-005](./GAP-CHS-005.md) | 2201 就诊登记幂等与挂号并发 | 中危 | P2 | 部分已有 |
| [GAP-CHS-006](./GAP-CHS-006.md) | 转医保切账户与医保试算/confirm 交叉 | 中危 | P2 | 见 SYS-IMP-002 |
| [GAP-CHS-007](./GAP-CHS-007.md) | 3505/3506 库存追溯上报重复 | 低危～中危 | P3 | 待评估 |
| [GAP-CHS-008](./GAP-CHS-008.md) | 住院反结 `runInsuranceRevokeBeforeCancel` 未接入主路径 | 中危 | P1 | 待实施 |
| [GAP-INT-001](./GAP-INT-001.md) | 3505/3502 本地成功后上传失败无强制补偿 | 中危 | P1 | 待实施 |
| [GAP-INT-002](./GAP-INT-002.md) | 3505 重复上传幂等键与 Dispense 锚点 | 低危～中危 | P2 | 待评估 |
| [GAP-INT-003](./GAP-INT-003.md) | booking Client 缺失时预约链静默跳过 | 中危 | P2 | 见 SYS-IMP-008 |
| [GAP-INT-004](./GAP-INT-004.md) | 2201/就诊登记与挂号并发（半成功） | 中危 | P2 | 部分已有 |
| [GAP-INT-005](./GAP-INT-005.md) | 电子发票开票/冲红与 Payment 顺序 | 中危 | P2 | 待实施 |
| [GAP-INT-006](./GAP-INT-006.md) | LIS/PACS 申请单已签发外部未接单 | 低危（业务） | P3 | 文档化 |
| [GAP-INT-007](./GAP-INT-007.md) | 外部回调/Webhook 无 idempotency | 中危 | P2 | 待评估 |
| [GAP-INT-008](./GAP-INT-008.md) | 集成失败无运维对账台 | 中危 | P1 | 待实施 |

[← 问题清单总索引](../README.md)
