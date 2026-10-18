# GAP-ATD-003 互换床（SWAP）双床 TOCTOU

> **问题类型**：并发双写与条件更新
> **严重度**：中危
> **影响范围**：住
> **优先级**：P1
> **状态**：**已实施**（2026-06-28）
> **父文档**：[SYS-IMP-004](../01-并发双写与条件更新/SYS-IMP-004-入出转分床与床位运营并发边界.md)

---

## 1. 问题描述

互换床（SWAP）双床同时 TOCTOU

## 2. 发生场景

互换涉及两床 `release` + 两床 `occupy`；第三方同时对其中一床分床。

## 3. 根因分析

见父文档；核心为读 Gate + 无源态条件更新或 Gate 缺失。原编排先 `releaseBed` 再 `occupyBed`，两 Cmd 之间目标床短暂 `NOT_IN_USE`，第三方可分床占入。

## 4. 业务影响

账实/账价不一致，或合规与安全风险。

## 5. 修改方案

对参与 SWAP 的两 `bedLocationId` 按 **id 排序** `FOR UPDATE` 再 release/occupy；或四步均用条件 UPDATE。

**已采用**：双床排序悲观锁 + `swapBedsOperational` 单次 `saveAll`（消除 release 与 occupy 间空窗）；`releaseBed` 对齐占床路径加锁与 `assertOccupiedForRelease`。

## 6. 涉及代码

- `Location.java`
- `LocationDomainService.java`（`releaseBed`、`swapBedsOperational`）
- `NurseStationATDManagerAppService#executeSwapBeds`

## 7. 解决日志

| 项 | 说明 |
| --- | --- |
| **实施日期** | 2026-06-28 |
| **采用方案** | 双床 `locationId` 升序 `PESSIMISTIC_WRITE`；锁内 release + 交叉 occupy；**一次** `saveAll` |
| **释床硬门禁** | `assertOccupiedForRelease()`；`releaseBed` 按床 id 排序行锁（换床/清床路径同步受益） |
| **占床硬门禁** | 复用 GAP-ATD-001/002：`assertVacantForOccupy` + `@Version` |
| **编排调整** | **SWAP**：`swapInpatientBeds` → `swapBedsOperational`（替代分步 `releaseBed` + `occupyBed`） |
| **错误码** | 床态冲突 / 乐观锁 → **EX-IP-ATD-201**（ATD-02 既有映射） |
| **关联** | [GAP-ATD-001](./GAP-ATD-001.md) 行锁与占床断言；[GAP-ATD-002](./GAP-ATD-002.md) `@Version` |

**变更文件**

| 模块 | 文件 | 要点 |
| --- | --- | --- |
| zxplat-domain | `Location.java` | `assertOccupiedForRelease()` |
| zxplat-domain | `LocationDomainService.java` | `releaseBed` 行锁 + 断言；新增 `swapBedsOperational` |
| health-application | `NurseStationATDManagerAppService.java` | `executeSwapBeds` 改调 `swapBedsOperational` |

## 8. 验收用例

- SWAP 执行中，第三方对其中一床分床 → 阻塞至 SWAP 提交或失败；不得出现一床两患。
- SWAP 两床 + 第三方占其中一床 → 明确失败 **EX-IP-ATD-201**（SYS-IMP-004 T3）。
- 正常互换：双方床态对调，就诊轨迹与床态一致提交或整单回滚。

## 9. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
| v1.1 | 2026-06-28 | `swapBedsOperational` + `releaseBed` 行锁；状态 → 已实施 |
