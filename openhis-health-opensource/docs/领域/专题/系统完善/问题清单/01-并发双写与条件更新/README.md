# 并发双写与条件更新

共 **43** 条问题。

| 编号 | 标题 | 严重度 | 优先级 | 状态 |
| --- | --- | --- | --- | --- |
| [GAP-ATD-001](./GAP-ATD-001.md) | 分床/换床目标空床读 Gate TOCTOU | **高危** | P0 | 待实施 |
| [GAP-ATD-002](./GAP-ATD-002.md) | `Location.occupyBed` 领域未断言床态 + 无乐观锁 | **高危** | P0 | 待实施 |
| [GAP-ATD-003](./GAP-ATD-003.md) | 互换床（SWAP）双床同时 TOCTOU | 中危 | P1 | **已实施** |
| [GAP-ATD-004](./GAP-ATD-004.md) | PLANNED 住院登记/有申请登记双窗 | 中危 | P2 | **已实施** |
| [GAP-ATD-005](./GAP-ATD-005.md) | 出院/清床/召回并发 | 中危（已知） | P2 | **部分已实施** |
| [GAP-ATD-006](./GAP-ATD-006.md) | 转科/转病区 ON_HOLD 幂等窗口 | 低危～中危 | P3 | 待评估 |
| [GAP-ATD-007](./GAP-ATD-007.md) | booking 预留与分床竞态 | 中危 | P1 | **已实施** |
| [GAP-ATD-008](./GAP-ATD-008.md) | IF-IP-AUD-03 转科/出院退回未回滚 ON_HOLD | 中危 | P2 | **已实施** |
| [GAP-BKG-001](./GAP-BKG-001.md) | 同日同患者同服务防重 TOCTOU | 中危 | P1 | **已实施** |
| [GAP-BKG-002](./GAP-BKG-002.md) | 末号源双请求（占号竞态） | 低危～中危 | P2 | **已实施** |
| [GAP-BKG-003](./GAP-BKG-003.md) | 床位预留同日同床防重 TOCTOU | 中危 | P1 | **已实施** |
| [GAP-BKG-004](./GAP-BKG-004.md) | 虚拟号源 `virtualSlotKey` 物化竞态 | 中危 | P2 | **已实施** |
| [GAP-BKG-005](./GAP-BKG-005.md) | 候补提升与取消/改约释放竞态 | 低危～中危 | P2 | **已实施** |
| [GAP-BKG-006](./GAP-BKG-006.md) | booking 床位预留 vs health 分床占床未联动 | 中危 | P2 | **已实施** |
| [GAP-BKG-007](./GAP-BKG-007.md) | `Appointment` 无 `@Version` / 无自然键唯一 | 低危 | P3 | **已实施** |
| [GAP-EXE-001](./GAP-EXE-001.md) | 住院库存 Gate TOCTOU（全局预占读时不锁） | 中危 | P1 | **已实施** |
| [GAP-EXE-002](./GAP-EXE-002.md) | 住院同一 planTime 并发双执行 | 中危 | P1 | **已实施** |
| [GAP-EXE-003](./GAP-EXE-003.md) | 核发断言不含全局待发预占 | 中危 | P1 | **已实施** |
| [GAP-EXE-004](./GAP-EXE-004.md) | 住院绑定耗材执行无药嘱级 Gate、当场扣库 | 中危 | P2 | **已实施** |
| [GAP-EXE-005](./GAP-EXE-005.md) | 门诊签发 FIFO 同就诊 TOCTOU | 中危 | P2 | **已实施** |
| [GAP-EXE-006](./GAP-EXE-006.md) | 门诊护士执行次数 netCount 非原子 | 低危～中危 | P2 | **已实施** |
| [GAP-EXE-007](./GAP-EXE-007.md) | 门诊耗材自动扣库并发（BR-NUR-008 静默 skip） | 中危（已知限制） | P2 | **已实施** |
| [GAP-EXE-008](./GAP-EXE-008.md) | 长期嘱写路径不校验 planTime ∈ 频次展开 | 低危～中危 | P3 | 待评估 |
| [GAP-EXE-009](./GAP-EXE-009.md) | `MedicationDispense` 表有 version 列但实体未映射乐观锁 | 低危 | P3 | **已实施** |
| [GAP-EXE-010](./GAP-EXE-010.md) | 长期嘱自然月累计发放 Gate（EX-IP-EXE-305） | 低危（设计） | P3 | 已实施 |
| [GAP-FIN-001](./GAP-FIN-001.md) | 正式结算 ChargeItem / Payment 双提交（读时防重 TOCTOU） | **高危** | P0 | 待实施 |
| [GAP-FIN-002](./GAP-FIN-002.md) | 账户余额 `adjustAdvanceDepositBalance` 丢失更新 | **高危** | P0 | 待实施 |
| [GAP-FIN-003](./GAP-FIN-003.md) | 门诊 `settleChargeItems` 不校验源状态（PLANNED） | **高危** | P0 | 待实施 |
| [GAP-FIN-004](./GAP-FIN-004.md) | 财务实体 DB 有 `version` 但 JPA 未映射乐观锁 | 中危 | P1 | 待实施 |
| [GAP-FIN-005](./GAP-FIN-005.md) | 退款受理窗口 `enterRefundProcessingWindow` 并发 | 中危 | P1 | 待实施 |
| [GAP-FIN-006](./GAP-FIN-006.md) | 门诊挂号当日同科室防重 TOCTOU | 中危 | P2 | 待实施 |
| [GAP-FIN-007](./GAP-FIN-007.md) | 住院预结算多草稿 / BR-IP-STL-010 作废竞态 | 低危～中危 | P2 | 待实施 |
| [GAP-FIN-008](./GAP-FIN-008.md) | 住院预交金并发收/退（文档已知） | 中危（已知） | P2 | 待实施 |
| [GAP-FIN-009](./GAP-FIN-009.md) | 收费员日结 payment 挂载并发 | 中危（已知） | P2 | 文档化 |
| [GAP-FIN-010](./GAP-FIN-010.md) | 护士执行/划价重复写 Charge（→ GAP-EXE-002 等） | 中危 | P1 | 见 SYS-IMP-001 |
| [GAP-FIN-011](./GAP-FIN-011.md) | 住院反结 / 门诊退费并发（文档已知） | 中危（已知） | P2 | 文档化 |
| [GAP-FIN-012](./GAP-FIN-012.md) | 转医保/转自费批量切账户无 ChargeItem 行锁 | 低危～中危 | P3 | 待评估 |
| [GAP-INV-001](./GAP-INV-001.md) | 进货/出库「同意」读 Gate TOCTOU | 中危 | P1 | 待实施 |
| [GAP-INV-002](./GAP-INV-002.md) | 盘点/损益同意与单据编辑并发 | 中危 | P2 | 待实施 |
| [GAP-INV-003](./GAP-INV-003.md) | 汇总领药双写（同 dispenseId） | 中危 | P1 | 已实施 |
| [GAP-INV-004](./GAP-INV-004.md) | 汇总核发双提交（EX-IPS-201） | 中危 | P1 | 已实施 |
| [GAP-INV-005](./GAP-INV-005.md) | `MedicationDispense` 汇总/核发无乐观锁 | 低危～中危 | P2 | 见 SYS-IMP-001 |
| [GAP-INV-006](./GAP-INV-006.md) | 订货/进货/出库单据编辑 EX-*-105 未统一重试语义 | 低危 | P3 | 待实施 |
| [GAP-INV-007](./GAP-INV-007.md) | 医保 3505 盘点上报与库存同意顺序 | 低危～中危 | P3 | 见 SYS-IMP-006 |

[← 问题清单总索引](../README.md)
