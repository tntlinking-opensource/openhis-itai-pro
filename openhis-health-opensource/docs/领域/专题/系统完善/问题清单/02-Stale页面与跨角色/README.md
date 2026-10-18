# Stale页面与跨角色

共 **7** 条问题。

| 编号 | 标题 | 严重度 | 优先级 | 状态 |
| --- | --- | --- | --- | --- |
| [GAP-STALE-001](./GAP-STALE-001.md) | 门诊：预结算后医生签退，款台用旧 draft 正式结算 | **高危** | P0 | 已实施 |
| [GAP-STALE-002](./GAP-STALE-002.md) | 门诊/住院：正式结算或核发双提交（Stale + 并发） | **高危** | P0 | 见 SYS-IMP-002 |
| [GAP-STALE-003](./GAP-STALE-003.md) | 住院：取消执行软删 Dispense 后，药房仍可对同一 id 配药/核发 | 中危 | P1 | 已实施 |
| [GAP-STALE-004](./GAP-STALE-004.md) | 住院：药房已配药（IN_PROGRESS/PREPARED）后护士取消执行 | 中危 | P1 | 已实施 |
| [GAP-STALE-005](./GAP-STALE-005.md) | 住院：医生签退已执行未结算医嘱（无执行/发药/费用 Gate） | 中危 | P2 | 已实施 |
| [GAP-STALE-006](./GAP-STALE-006.md) | 汇总/核发 Stale 页双操作 | 中危 | P2 | 已实施 |
| [GAP-STALE-007](./GAP-STALE-007.md) | 仅前端 disable、写路径不重读锚点态 | 中危 | P2 | 已实施 |

**Gate 矩阵**：[SYS-IMP-007-跨角色写接口Gate矩阵](../02-Stale页面与跨角色/SYS-IMP-007-跨角色写接口Gate矩阵.md)

[← 问题清单总索引](../README.md)
