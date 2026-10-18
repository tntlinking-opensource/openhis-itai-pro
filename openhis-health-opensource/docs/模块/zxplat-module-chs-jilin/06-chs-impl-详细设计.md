# chs-jilin 网关 — 详细设计

> **架构真源**：[01-概要设计.md](./01-概要设计.md) · [13-实现状态与文档对照.md](./13-实现状态与文档对照.md)

> **文档性质**：LLD（zxplat-module-chs-jilin 网关实现）  
> **上游**：[03-概要设计](./05-Whale吉林省医保模块-概要设计.md) | [04-多方案骨架](./17-Whale医保多方案扩展骨架.md)  
> **版本**：v1.0 | **日期**：2026-05-28

---

## 1. 模块职责边界

| 在 chs-jilin-application（网关层） | 不在网关层 |
|-------------|---------------|
| OAuth Token | 协同单状态 |
| 9001/9002 sign_no | ChargeItem 组装 |
| TransBody 报文头 | 2207 业务字段映射 |
| SM2/SM4 | fin_contract 查询 |
| HTTP POST FSI | payment SETTLING |

---

## 2. 配置模型

### 2.1 ContractConfig 扩展

```java
@Data
public class ContractConfig {
    private Long contractId;           // 与 fin_contract.id 对应（可选）
    private String busNo;              // 配置键，对齐 fin_contract.bus_no
    private String contractName;
    private String admVs;              // mdtrtarea_admvs
    private String baseUrl;
    private String fixmedinsCode;
    private String fixmedinsName;
    private String clientId;
    private String clientSecret;
    private String username;
    private String password;
    private String scope;              // server
    private String grantType;          // password
    private String cliPrvKey;          // SM2 私钥 PEM/Base64
    private String cliPubKey;
    private String serverPubKey;
    private boolean encrypt;           // 生产 true
    private int httpTimeoutMs;         // default 40000
}
```

### 2.2 chs-contract.properties 示例

```properties
# 键为 fin_contract.bus_no，无 scheme 字段
contract.H22010402403.base_url=http://ddjk.jlhs.gov.cn:20215
contract.H22010402403.fixmedins_code=H22010402403
contract.1.fixmedins_name=示例机构
contract.1.adm_vs=229900
contract.1.client_id=${CHS_C1_CLIENT_ID}
contract.1.client_secret=${CHS_C1_CLIENT_SECRET}
contract.1.username=${CHS_C1_USERNAME}
contract.1.password=${CHS_C1_PASSWORD}
contract.1.cli_prv_key=${CHS_C1_SM2_PRIVATE}
contract.1.server_pub_key=${CHS_C1_SM2_SERVER_PUBLIC}
contract.1.encrypt=true
```

**环境变量覆盖：** `ContractConfigLoader` 支持 `${ENV}` 占位符解析（Spring 或简单替换）。

---

## 3. InsuranceGatewayClient 实现

### 3.1 装配

```java
@Configuration
public class InsuranceGatewayConfiguration {

    @Bean
    @ConditionalOnProperty(name = "insurance.gateway.mode", havingValue = "local", matchIfMissing = true)
    public InsuranceGatewayClient insuranceGatewayClientLocal(ChsHttpAccessor accessor) {
        return new InsuranceGatewayClientLocalImpl(accessor);
    }

    @Bean
    @ConditionalOnProperty(name = "insurance.gateway.mode", havingValue = "remote")
    public InsuranceGatewayClient insuranceGatewayClientRemote(ChsRemoteProperties props) {
        return new InsuranceGatewayClientRemoteImpl(props);
    }
}
```

### 3.2 LocalImpl

```java
@Service
public class InsuranceGatewayClientLocalImpl implements InsuranceGatewayClient {
    private final ChsHttpAccessor accessor;
    private final InsuranceSignSessionService signSession;

    @Override
    public ResultBody invoke(Long contractId, String infNo, Object input, InvokeContext ctx) {
        if (!ServicePathConstants.INFNO_9001.equals(infNo)) {
            signSession.ensureSigned(contractId, ctx);
        }
        return accessor.send(contractId, infNo, input, ctx);
    }
}
```

---

## 4. ChsHttpAccessor 流程

```text
send(contractId, infNo, input, ctx)
  1. ContractConfig cfg = ContractConfigLoader.getByContractId(contractId)
  2. signNo = signSession.getSignNo(cfg, ctx)   // 9001 时为空
  3. insuplcAdmdvs = JilinInsuplcAdmdvsRule.resolve(infNo, input, ctx, cfg)
  4. inputJson = JSON.toJSONString(input)
  5. if (cfg.encrypt) { sign + SM4 encrypt input }
  6. TransBody = build(infNo, msgid, cfg, ctx, signNo, inputOrCipher)
  7. POST {baseUrl}/mbs/fmi/fsi/api/callFsiService/callService
  8. parse ResultBody; if encrypt decrypt output
  9. return ResultBody
```

### 4.1 URL 常量

```java
public final class ServicePathConstants {
    public static final String TOKEN_PATH = "/auth/oauth/token";
    public static final String CALL_SERVICE_PATH = "/mbs/fmi/fsi/api/callFsiService/callService";
    public static final String INFNO_9001 = "9001";
    public static final String INFNO_9002 = "9002";
    public static final String INFNO_1101 = "1101";
}
```

### 4.2 msgid

```java
public static String nextMsgId(String fixmedinsCode) {
    return fixmedinsCode + DateUtil.format(new Date(), "yyyyMMddHHmmss") + RandomUtil.randomNumbers(4);
}
```

---

## 5. InsuranceSignSessionService

### 5.1 9001 签到

```java
public String ensureSigned(Long contractId, InvokeContext ctx) {
    String key = signKey(contractId, ctx);
    String cached = cache.get(key);
    if (cached != null) return cached;
    Sign9001Input in = Sign9001Input.builder()
        .opterNo(ctx.getOperatorId())
        .mac("WHALE")
        .ip("127.0.0.1")
        .build();
    ResultBody r = accessor.send(contractId, "9001", in, ctx);
    String signNo = parseSignNo(r);
    cache.set(key, signNo, Duration.ofHours(24));
    return signNo;
}
```

### 5.2 Redis 键

```text
CHS:SIGN:{contractId}:{admVs}:{operatorId}
CHS:TOKEN:{contractId}:{admVs}:{clientId}
```

单机可 `ConcurrentHashMap` + TTL；集群 **必须 Redis**。

### 5.3 9002 签退

```java
public void signOut(Long contractId, InvokeContext ctx) {
    // invoke 9002
    cache.delete(signKey(contractId, ctx));
}
```

---

## 6. ChsSmCryptoService

从 ybapp `EasyGmUtils` 迁入：

```java
public interface ChsSmCryptoService {
    /** 对 input JSON 签名为 cainfo */
    String signSm2(String plainJson, ContractConfig cfg);
    /** SM4 加密 → Base64 字符串 */
    String encryptSm4(String plainJson, ContractConfig cfg);
    /** 解密 ResultBody.output */
    String decryptSm4(String cipherOutput, ContractConfig cfg);
}
```

**encrypt=false（开发）：** `cainfo=""`，`input` 为明文 JSON 对象。

---

## 7. JilinInsuplcAdmdvsRule

```java
private static final Set<String> USE_MDTRT_AREA = Set.of("9001","1101","3203A","3206A","3301","3302");

public String resolve(String infNo, Object input, InvokeContext ctx, ContractConfig cfg) {
    if (ctx != null && isNotBlank(ctx.getInsuplcAdmdvs())) return ctx.getInsuplcAdmdvs();
    if (USE_MDTRT_AREA.contains(infNo)) return cfg.getAdmVs();
    return extractFromInput(input).orElse(cfg.getAdmVs());
}
```

---

## 8. Remote 模式

### 8.1 health-app 侧 RemoteImpl

```java
public class InsuranceGatewayClientRemoteImpl implements InsuranceGatewayClient {
    public ResultBody invoke(Long contractId, String infNo, Object input, InvokeContext ctx) {
        ChsRemoteInvokeRequest req = new ChsRemoteInvokeRequest(contractId, infNo, input, ctx);
        return restClient.post(props.getRemoteBaseUrl() + "/chs/gateway/invoke", req, ResultBody.class);
    }
}
```

### 8.2 chs-gateway 独立应用 ChsGatewayController

```java
@RestController
@RequestMapping("/chs/gateway")
public class ChsGatewayController {
    @PostMapping("/invoke")
    public ResultBody invoke(@RequestBody ChsRemoteInvokeRequest req) {
        return localClient.invoke(req.getContractId(), req.getInfNo(), req.getInput(), req.getContext());
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP");
    }
}
```

**安全：** remote 模式内网 + mTLS 或 API Key（Wave 1 文档约定，实现可 `@RequestHeader X-Chs-Api-Key`）。

---

## 9. 调用审计 yb_invoke_log

**实现真源**（非 AOP）：`chs-jilin-application` → `PersistingInsuranceGatewayClient` 装饰 `InsuranceGatewayClient`，在 `invoke` 的 `finally` 中调用 `YbInvokeLogWriter`。

| 要点 | 说明 |
|------|------|
| 落库时机 | 成功、`infcode!=0`、HTTP 异常均写入 |
| 事务 | `YbInvokeLogWriter.write` 使用 `REQUIRES_NEW`，业务 UoW 回滚不丢失败审计 |
| 字段 V12 | `infname`（`ChsInfNo.resolveDisplayName`）、`err_msg`（平台错误或异常摘要） |
| `response_json` | 完整 `ResultBody` JSON（含 `err_msg`），不仅 `output` |
| Flyway | `chs-jilin-application` → `V12__yb_invoke_log_infname_err_msg.sql` |

敏感字段脱敏（待办）：`mdtrt_cert_no`、`certno` 保留后 4 位（当前 request 仍为业务 input 全文）。

---

## 9.1 adapter 主诊断编码（2401/220x）

`chs-jilin-bridge` → `InsuranceDiagnosisCodesupport：从就诊主诊断关联 + `cln_condition_definition.yb_no` 解析 `mainDiagCode` / `dise_codg`（院内 `condition_code` 仅兜底）。入口：`InpatientInsuranceContextLoader`、`OutpatientInsuranceChargeGrouper`。

---

## 10. 异常模型

```java
public class ChsInvokeException extends RuntimeException {
    private final String infNo;
    private final String msgid;
    private final ChsFailureKind kind;  // TIMEOUT, HTTP_ERROR, PARSE_ERROR, BUSINESS_REJECT
}
```

Worker 捕获：

| kind | coordination |
|------|--------------|
| TIMEOUT / HTTP_ERROR / PARSE_ERROR | UNKNOWN |
| BUSINESS_REJECT | FAILED |

---

## 11. 类图（impl.core）

```text
InsuranceGatewayClientLocalImpl
    → InsuranceSignSessionService
    → ChsHttpAccessor
          → ContractConfigLoader
          → ChsSmCryptoService
          → TokenProvider (OAuth)
          → JilinInsuplcAdmdvsRule (scheme/jilin)
```

---

## 12. 构建与依赖

```xml
<!-- chs-impl pom 增加 -->
<dependency>
    <groupId>org.bouncycastle</groupId>
    <artifactId bcprov-jdk18on</artifactId>
</dependency>
<!-- 可选 redis -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
    <optional>true</optional>
</dependency>
```

---

## 13. 测试

| 类型 | 方法 |
|------|------|
| 单元 | Mock HTTP，`encrypt=false`，断言 TransBody 头字段 |
| 集成 | WireMock OAuth + callService |
| 国密 | 使用 ybapp 脱敏样例 round-trip |

---

## 14. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-05-28 | 初版 |
