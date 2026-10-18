# ZXPlat PACS Imaging · 中间件联调（序号 57～58）

国基栈：**Orthanc（A1）+ OHIF v3（V1）+ MinIO `key-images`（K4）**，取图协议 **DICOMweb**。  
序号 **58**：C-STORE 稳定检查回调自动入库 + MWL 查询 API（最小可用）。

## 1. 启动

```bash
cd /zxplat-module-pacs-imaging/deploy
docker compose up -d
```

| 服务 | 地址 | 账号 |
|------|------|------|
| Orthanc HTTP | http://localhost:8042 | orthanc / orthanc |
| Orthanc DICOM | `localhost:4242` AE=`WHALE_ORTHANC` | — |
| OHIF | http://localhost:3000 | — |
| MinIO API | http://localhost:9000 | minioadmin / minioadmin |
| MinIO Console | http://localhost:9001 | 同上 |
| 桶 | `key-images`（`minio-init` 自动创建） | — |

挂载：`orthanc.json` + `on-stable-study.lua`；`extra_hosts` 使容器可访问宿主机 ZXPlat。

## 2. ZXPlat 配置

在目标 `application.yml`（或院点覆盖）增加：

```yaml
zxplat:
  modules:
    pacs:
      imaging:
        enabled: true
        mwl-enabled: true
        notify:
          token: zxplat-imaging-notify
          default-tenant-id: 1   # 院点租户；Lua 也可传 X-Tenant-Id
        orthanc:
          enabled: true
          base-url: http://localhost:8042
          username: orthanc
          password: orthanc
        minio:
          enabled: true
          endpoint: http://localhost:9000
          access-key: minioadmin
          secret-key: minioadmin
          bucket: key-images
          presign-expiry-seconds: 600
```

Maven 依赖：`module-pacs-imaging-starter`。未开 `orthanc.enabled` / `minio.enabled` 时仍用 Noop / 内存 stub（单测默认）。

## 3. 阅片端点种子

调用 `POST /api/app/pacsViewerUrlConfig/saveViewerEndpoint`：

```json
{
  "orgId": null,
  "modalityCode": null,
  "baseUrl": "http://localhost:3000",
  "enabled": true
}
```

`launchViewer` 会拼：`{baseUrl}/viewer?StudyInstanceUIDs={uid}&lng=zh&token=...`

## 4. C-STORE 自动入库（序号 58 / CAP-10）

流程：设备 **C-STORE → Orthanc:4242** → 检查稳定（`StableAge`，默认 10s）→ Lua `OnStableStudy` → HTTP POST ZXPlat。

| 项 | 值 |
|----|-----|
| URL | `POST /api/integration/pacs-imaging/cstore-notify` |
| Header | `X-Imaging-Notify-Token: {notify.token}` |
| Header | `X-Tenant-Id` 可选；缺则用 `notify.default-tenant-id` |
| Body | `{ "accessionNumber", "studyInstanceUid", "seriesInstanceUid?", "modalityCode?" }` |
| Security | `permitAll` + 共享密钥；`WebConfig` 已排除租户拦截，由 Controller 设租户 |

Lua 顶部常量（`deploy/orthanc/on-stable-study.lua`）须改成院点值：

- `ZXPLAT_NOTIFY_URL`（容器访宿主机：`http://host.docker.internal:<ZXPlat端口>/api/integration/pacs-imaging/cstore-notify`）
- `ZXPLAT_NOTIFY_TOKEN`
- `WHALE_TENANT_ID`

手工冒烟（无需设备）：

```bash
curl -s -X POST "http://localhost:<ZXPlat端口>/api/integration/pacs-imaging/cstore-notify" \
  -H "Content-Type: application/json" \
  -H "X-Imaging-Notify-Token: zxplat-imaging-notify" \
  -H "X-Tenant-Id: 1" \
  -d "{\"accessionNumber\":\"ACC-xxx\",\"studyInstanceUid\":\"1.2.3.4\"}"
```

期望：返回 `workItemId` / `studyInstanceUid` / `hasMedia=true`；重复调用幂等。

### LuaScripts 无效时

jodogne 镜像通常认配置键 `"LuaScripts": ["/etc/orthanc/on-stable-study.lua"]`。若启动日志未加载脚本：

1. 确认 volume 挂载路径与 `orthanc.json` 一致；
2. 或在容器内用环境变量 / 插件文档启用 Lua（以镜像版本为准）；
3. 临时可改 `OnStoredInstance`（会多次回调；领域 `bindFromCStore` 仍幂等）。

## 5. MWL 查询 API（序号 58 / CAP-07 最小）

本序号**不**实现 DICOM C-FIND MWL，也**不**生成 `.wl` 文件。

| 项 | 值 |
|----|-----|
| AppService | `POST /api/app/pacsImageCapture/listModalityWorklist`（需登录 + 租户） |
| 数据源 | `PacsWorkItemQueryService#searchPendingExecution`（scheduled / registered+accession） |
| 过滤 | `modalityCode?`、`organizationId?`、分页 |
| 消费方 | Orthanc worklists 插件或院点同步脚本拉取本 API 后再写设备侧 worklist |

`mwl-enabled` 仅为院点开关/文档标记，不拦截方法调用。

## 6. 冒烟顺序

见 [联调验收清单.md](./联调验收清单.md)。

## 7. 边界

- ZXPlat **不**自研像素渲染；OHIF 经 Orthanc DICOMweb 取图。
- Domain **不**存 Orthanc 内部 ID；只存 StudyUID / Media objectKey。
- adapter 可用 Orthanc REST 做入库；阅片取像素只用 DICOMweb。
- 生产须反代鉴权，禁止 Orthanc/MinIO 对公网裸奔；回调 token 须院点轮换。
- 真正 DICOM MWL C-FIND 由 Orthanc worklists + 同步脚本完成，不在本序号。
