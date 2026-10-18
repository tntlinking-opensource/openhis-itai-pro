# 01 · 并发双写与条件更新

> **问题类型**：多请求/多 Job 几乎同时写同一行或同一资源，依赖**读时 Gate**，缺少 **条件 UPDATE / 行锁 / 乐观锁** 导致丢失更新或重复写入。

## 本类典型根因

- `SELECT` 校验通过后 `save()`，无 `WHERE status_enum IN (…)`
- DB 有 `version` 列但 JPA 未 `@Version`
- 读探测幂等（`exists*`）无唯一索引

## 文档

| 编号 | 文档 | GAP 前缀 | 资产域 |
| --- | --- | --- | --- |
| SYS-IMP-001 | [SYS-IMP-001-护士站执行与发药并发防超发.md](./SYS-IMP-001-护士站执行与发药并发防超发.md) | `GAP-EXE-*` | 执行、核发、库存预占 |
| SYS-IMP-002 | [SYS-IMP-002-收费结算与账户并发边界.md](./SYS-IMP-002-收费结算与账户并发边界.md) | `GAP-FIN-*` | Charge、Payment、Account |
| SYS-IMP-003 | [SYS-IMP-003-预约号源与床位预留并发边界.md](./SYS-IMP-003-预约号源与床位预留并发边界.md) | `GAP-BKG-*` | Slot、预约、床位预留 |
| SYS-IMP-004 | [SYS-IMP-004-入出转分床与床位运营并发边界.md](./SYS-IMP-004-入出转分床与床位运营并发边界.md) | `GAP-ATD-*` | Location 床态 |
| SYS-IMP-005 | [SYS-IMP-005-进销存单据与汇总发药并发边界.md](./SYS-IMP-005-进销存单据与汇总发药并发边界.md) | `GAP-INV-*` | 药库单据、汇总核发 |

## 关联类型

- **Stale**（IMP-007）：并发双点 vs 跨角色旧页，修复手段部分重叠（条件 UPDATE）
- **外部集成**（IMP-006）：医保 confirm 双提交是「分布式并发」

[← 返回总索引](../README.md)
