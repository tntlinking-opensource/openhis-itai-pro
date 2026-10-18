# 协同单与 Worker — 详细设计

> **状态：已作废 — 勿再引用**  
> 作废日期：2026-07-14  
> **替代真源**：协同单落点与开关见 [13-实现状态与文档对照 §2.3](./13-实现状态与文档对照.md)；业务编排见 [03-门诊医保结算-详细设计](./03-门诊医保结算-详细设计.md)、[04-住院医保结算-详细设计](./04-住院医保结算-详细设计.md)（代码在 `zxplat-core/zxplat-domain（应用编排见 health-app）`，非本稿早期想定）。  
> 下文仅作历史存档，内容可能与现行实现不一致。

> ~~**文档性质**：LLD~~  
> ~~**上游**：[03-概要设计](./05-Whale吉林省医保模块-概要设计.md) | [05-门诊结算 LLD](./03-门诊医保结算-详细设计.md)~~  
> ~~**版本**：v1.0 | **日期**：2026-05-28~~

---

## 1. 领域服务 API

### 1.1 YbCoordinationDomainService

```java
public interface YbCoordinationDomainService {

    /** 创建协同单，state=CREATED，生成 msgid */
    YbCoordination create(CreateYbCoordinationCommand cmd);

    /** 乐观锁推进状态；非法转移抛 INS-012 INVALID_STATE_TRANSITION */
    YbCoordination transition(TransitionYbCoordinationCommand cmd);

    /** 写入 step 行 */
    YbCoordinationStep appendStep(AppendYbCoordinationStepCommand cmd);

    void updateStep(UpdateYbCoordinationStepCommand cmd);

    /** 取消：仅 CREATED/FAILED，且 platform 未 OK */
    void cancel(CancelYbCoordinationCommand cmd);
}
```

### 1.2 CreateYbCoordinationCommand

```java
@Data
public class CreateYbCoordinationCommand {
    private YbCoordinationBizType bizType;
    private InsuranceSchemeCode schemeCode;
    private Long bizId;              // paymentId
    private Long accountId;
    private Long contractId;
    private Long paymentId;
    private String paymentNo;
    private Long encounterId;
    private Long patientId;
    private Long operatorId;
    private YbCoordinationPriority priority;
    private boolean blocking;
    private Long parentId;
}
```

---

## 2. 状态转移表

| 当前状态 | 事件 | 下一状态 | 条件 |
|----------|------|----------|------|
| CREATED | WORKER_PICK | PLATFORM_RUNNING | |
| PLATFORM_RUNNING | PLATFORM_SUCCESS | PLATFORM_OK | infcode=0 |
| PLATFORM_RUNNING | PLATFORM_FAIL | FAILED | infcode=-1 |
| PLATFORM_RUNNING | PLATFORM_TIMEOUT | UNKNOWN | 超时/无 infcode |
| PLATFORM_OK | LOCAL_START | LOCAL_RUNNING | |
| LOCAL_RUNNING | LOCAL_SUCCESS | COMPLETED | 本地落账 OK |
| LOCAL_RUNNING | LOCAL_FAIL | LOCAL_PENDING | 本地异常 |
| LOCAL_PENDING | LOCAL_RETRY_OK | COMPLETED | |
| LOCAL_PENDING | LOCAL_RETRY_FAIL | LOCAL_PENDING | retry++ |
| UNKNOWN | RECOVER_START | RECOVERING | |
| RECOVERING | RECOVER_FOUND_OK | PLATFORM_OK → LOCAL_PENDING | 5203 查到 setl_id |
| RECOVERING | RECOVER_FOUND_FAIL | FAILED | 确认未成功 |
| RECOVERING | RECOVER_STILL_UNKNOWN | UNKNOWN | manual_ticket 条件满足 |
| * | USER_CANCEL | CANCELLED | 无 platform_setl_id |
| PLATFORM_OK / COMPLETED | COMP_START | COMPENSATING | 2208/2601 |
| COMPENSATING | COMP_DONE | COMPENSATED | |

**禁止转移：**

- `COMPLETED` → 任何（除审计修正）  
- `UNKNOWN` → `PLATFORM_RUNNING`（**禁止**新 msgid 再 2207）  
- `PLATFORM_OK` → `PLATFORM_RUNNING`

---

## 3. YbCoordinationWorker

### 3.1 调度

```java
@Component
@ConditionalOnProperty(name = "insurance.coordination.worker-enabled", havingValue = "true", matchIfMissing = true)
public class YbCoordinationWorker {

    @Scheduled(fixedDelayString = "${insurance.coordination.worker-fixed-delay-ms:5000}")
    public void tick() {
        List<YbCoordination> batch = queryService.findRunnable(
            List.of(CREATED, LOCAL_PENDING, RECOVERING),
            Instant.now(),
            20);
        for (YbCoordination c : batch) {
            if (lockService.tryLock("yb:coord:" + c.getId(), 120)) {
                try {
                    dispatch(c);
                } finally {
                    lockService.unlock("yb:coord:" + c.getId());
                }
            }
        }
    }
}
```

**锁：** Redis `SET NX EX 120` 或 PG `SELECT ... FOR UPDATE SKIP LOCKED`。

### 3.2 dispatch 伪代码

```java
void dispatch(YbCoordination coord) {
    YbCoordinationStepExecutor executor = executorRegistry.get(coord.getBizType());
    switch (coord.getState()) {
        case CREATED -> {
            transition(coord, PLATFORM_RUNNING);
            executor.executePlatformChain(coord);  // 2204→2206→2207
        }
        case LOCAL_PENDING -> {
            transition(coord, LOCAL_RUNNING);
            executor.executeLocal(coord);
        }
        case RECOVERING -> recoverService.recover(coord);
        default -> {}
    }
}
```

### 3.3 executePlatformChain（通用模板）

```java
public void executePlatformChain(YbCoordination coord) {
    InsuranceSchemePlugin plugin = registry.require(coord.getContractId());
    InsuranceSettlementContext ctx = loader.loadFromCoordination(coord);
    List<CoordinationStepPlan> plans = plugin.planSteps(coord.getBizType(), ctx);

    for (CoordinationStepPlan plan : plans) {
        YbCoordinationStep step = domainService.appendStep(coord.getId(), plan.getInfno());
        Object input;
        if (coord.getRetryCount() > 0 && step.getRequestSnapshot() != null) {
            input = json.parse(step.getRequestSnapshot());  // 同步骤重试用快照
        } else {
            input = plugin.assembleInput(plan.getInfno(), ctx, step);
            step.setRequestSnapshot(json.stringify(input));
        }
        ResultBody result;
        try {
            result = gateway.invoke(coord.getContractId(), plan.getInfno(), input, ctx.toInvokeContext());
        } catch (Exception ex) {
            handlePlatformException(coord, step, ex);  // → UNKNOWN or retry
            return;
        }
        step.setResponseSnapshot(json.stringify(result));
        if (!"0".equals(result.getInfcode())) {
            transition(coord, FAILED, result.getErrMsg());
            return;
        }
        plugin.absorbOutput(plan.getInfno(), result, ctx);
        domainService.updateStep(step, COMPLETED);
    }
    coord.setPlatformSetlId(ctx.getSetlId());
    transition(coord, PLATFORM_OK);
    executeLocal(coord);
}
```

### 3.4 executeLocal

```java
public void executeLocal(YbCoordination coord) {
    try {
        localSettlementService.complete(coord);
        transition(coord, COMPLETED);
    } catch (Exception ex) {
        coord.setNextRetryAt(backoff(coord.getRetryCount()));
        transition(coord, LOCAL_PENDING, ex.getMessage());
    }
}
```

---

## 4. UNKNOWN 与 YbCoordinationRecoverService

### 4.1 触发

`ChsHttpAccessor` 或 Worker catch：

- SocketTimeoutException  
- HTTP 5xx  
- body 空 / JSON 解析失败  
- `infcode == null`

→ `transition(UNKNOWN)`，记录 `msgid`，**不**递增为新的结算 msgid。

### 4.2 自动查单（5203）

```java
public void recover(YbCoordination coord) {
    InsuranceSettlementContext ctx = loader.loadFromCoordination(coord);
    Query5203Input input = new Query5203Input();
    input.setPsnNo(ctx.getPsnNo());
    input.setMdtrtId(ctx.getMdtrtId());
    // setl_id 未知时可仅 psn_no + mdtrt_id + 时间窗口（若平台支持）
    ResultBody result = gateway.invoke(coord.getContractId(), "5203", input, ctx.toInvokeContext());
    if (timeout(result)) {
        scheduleNextRecover(coord);  // 30s,1m,5m,15m,15m
        if (coord.getRetryCount() >= 5) coord.setManualTicket(true);
        return;
    }
    if (foundSetlId(result)) {
        ctx.setSetlId(parseSetlId(result));
        coord.setPlatformSetlId(ctx.getSetlId());
        transition(coord, PLATFORM_OK);
        transition(coord, LOCAL_PENDING);
    } else {
        transition(coord, FAILED, "平台未找到结算记录");
    }
}
```

**备选：** 若 5203 需 `setl_id`，改用 **5204 费用明细** + `msgid` 对照（院方联调确认）。

### 4.3 人工三按钮

| 按钮 | Domain 动作 |
|------|-------------|
| 确认平台已成功 | 手工填入 `setl_id` → PLATFORM_OK → LOCAL_PENDING |
| 确认平台未成功 | → FAILED → CANCELLED |
| 发起冲正 | 新建 `PLATFORM_COMP` 子单 parent_id=原单 |

---

## 5. 重试与 next_retry_at

| biz_type | 平台重试 | 本地重试 | 退避 |
|----------|----------|----------|------|
| OP_SETTLE / IP_SETTLE | 0（UNKNOWN 走 recover） | 5 次 | 30s×2^n |
| OP_FEE_UPLOAD | 3 | — | 1m |
| DISPENSE_UPLOAD | 10 | — | 5m |

**SETTLE 平台步骤失败（明确 infcode=-1）：** 不自动重试，直接 FAILED。

---

## 6. afterCommit 触发（可选加速）

```java
@Component
public class YbCoordinationCreatedListener {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCreated(YbCoordinationCreatedEvent event) {
        worker.dispatchAsync(event.getCoordinationId());
    }
}
```

与 `@Scheduled` 双保险：afterCommit 低延迟，Scheduled 兜底宕机恢复。

---

## 7. YbCoordinationStepExecutor 注册

```java
@Component
public class YbCoordinationStepExecutorRegistry {
    private final Map<YbCoordinationBizType, YbCoordinationStepExecutor> map;

    public YbCoordinationStepExecutorRegistry(List<YbCoordinationStepExecutor> executors) {
        this.map = executors.stream().collect(toMap(YbCoordinationStepExecutor::bizType, e -> e));
    }
}
```

| 实现 | bizType |
|------|---------|
| `JilinOpSettleCoordinationExecutor` | OP_SETTLE |
| `JilinOpSettleReverseExecutor` | OP_SETTLE_REVERSE |
| `JilinDispenseUploadExecutor` | DISPENSE_UPLOAD |
| `JilinReconDailyExecutor` | RECON_DAILY |
| … | Wave 2–3 补充 |

---

## 8. 实体 JPA 映射要点

```java
@Entity
@Table(name = "yb_coordination")
public class YbCoordination extends BaseAuditedEntity {
    @Enumerated(EnumType.STRING)
    private YbCoordinationBizType bizType;
    @Enumerated(EnumType.STRING)
    private InsuranceSchemeCode schemeCode;
    @Enumerated(EnumType.STRING)
    private YbCoordinationState state;
    @Version
    private Integer version;
    // ...
}
```

**Repository：**

```java
@Query("""
    SELECT c FROM YbCoordination c
    WHERE c.state IN :states AND c.isDeleted = false
      AND (c.nextRetryAt IS NULL OR c.nextRetryAt <= :now)
    ORDER BY c.priority, c.createdAt
    """)
List<YbCoordination> findRunnable(...);
```

---

## 9. 运维查询 SQL（参考）

```sql
-- P0 超时
SELECT * FROM yb_coordination
 WHERE state IN ('UNKNOWN','LOCAL_PENDING','PLATFORM_RUNNING')
   AND updated_at < now() - interval '30 minutes'
   AND is_deleted = false;

-- 按 payment 查协同单
SELECT * FROM yb_coordination WHERE payment_id = ? ORDER BY id;
```

---

## 10. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-05-28 | 初版 |
