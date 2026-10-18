# GAP-INT-001 3505 半成功态

> **问题类型**：外部集成与分布式顺序
> **严重度**：中危（监管账与 HIS 账不一致）
> **影响范围**：药库
> **优先级**：P1
> **状态**：待实施
> **父文档**：[SYS-IMP-011](../06-外部集成与分布式顺序/SYS-IMP-011-外部集成最终一致性与补偿.md)
> **关联 IF/BR**：3505/3502 进销存上传；**SYS-IMP-005 GAP-INV-007**

---

## 1. 问题描述

3505/3502 本地成功后上传失败无强制补偿

## 2. 发生场景

发药/盘点本地 `COMPLETED` 后，`ChsInventoryAppService#uploadSale3505` 逐行调平台；单行失败 **不回滚** 本地库存与 Dispense；失败行可能仅日志/结果列表。

## 3. 根因分析

分布式事务不可行；缺统一 **Integration Outbox** 与重试。

## 4. 业务影响

医保进销存监管平台缺单；飞检对不上。

## 5. 修改方案

1. **Outbox 表** `int_outbox`：`(sourceType, sourceLineId, infNo, payload, status, retryCount)`；
2. 本地成功后 **同事务** 写 outbox PENDING；异步 worker 调 3505，SUCCESS 标记；FAIL 指数退避；
3. **运维台**：按 encounter/dispense 查上传态，支持手工重传；
4. 与盘点同意（IMP-005）共用 worker。


## 6. 涉及代码

- `ChsInventoryAppService.java`
- `InventoryUploadContextLoader.java`
- 新建 `IntegrationOutboxWorker`

## 7. 验收用例

- 模拟平台 500 → 本地仍 COMPLETED，outbox RETRY → 最终 SUCCESS。
- 重传幂等：同 sourceLineId 不双上传。

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
