-- Whale PACS Imaging · Orthanc OnStableStudy → C-STORE 回调（序号 58）
--
-- 院点部署时请改成实际值：
--   WHALE_NOTIFY_URL   Whale 可达地址（容器内访宿主机常用 host.docker.internal）
--   WHALE_NOTIFY_TOKEN 与 openhis.modules.pacs.imaging.notify.token 一致
--   WHALE_TENANT_ID    院点租户；也可依赖 Whale 侧 defaultTenantId 而留空
--
-- 选用 OnStableStudy：检查稳定后再回写，避免每实例多次通知。
-- 若改用 OnStoredInstance，领域层 bindFromCStore 仍幂等，但会多次 HTTP。

local WHALE_NOTIFY_URL = "http://host.docker.internal:8080/api/integration/pacs-imaging/cstore-notify"
local WHALE_NOTIFY_TOKEN = "whale-imaging-notify"
local WHALE_TENANT_ID = "1"

function OnStableStudy(studyId, tags, metadata)
  if tags == nil then
    print("[whale] OnStableStudy: tags is nil, studyId=" .. tostring(studyId))
    return
  end

  local accession = tags["AccessionNumber"] or ""
  local studyUid = tags["StudyInstanceUID"] or ""
  local modality = tags["Modality"] or ""
  local seriesUid = tags["SeriesInstanceUID"] or ""

  if accession == "" or studyUid == "" then
    print("[whale] OnStableStudy skip: missing AccessionNumber or StudyInstanceUID, studyId="
      .. tostring(studyId))
    return
  end

  local payload = {}
  payload["accessionNumber"] = accession
  payload["studyInstanceUid"] = studyUid
  if modality ~= "" then
    payload["modalityCode"] = modality
  end
  if seriesUid ~= "" then
    payload["seriesInstanceUid"] = seriesUid
  end

  local body = DumpJson(payload)
  local headers = {}
  headers["Content-Type"] = "application/json"
  headers["X-Imaging-Notify-Token"] = WHALE_NOTIFY_TOKEN
  if WHALE_TENANT_ID ~= nil and WHALE_TENANT_ID ~= "" then
    headers["X-Tenant-Id"] = WHALE_TENANT_ID
  end

  print("[whale] OnStableStudy notify studyId=" .. tostring(studyId)
    .. " accession=" .. accession
    .. " studyUid=" .. studyUid)

  -- Orthanc Lua: HttpPost(url, body [, isVerbose [, headers]])
  -- 若当前镜像签名不含 headers 表，见 deploy/README 备选启用方式。
  local ok, err = pcall(function()
    HttpPost(WHALE_NOTIFY_URL, body, false, headers)
  end)
  if not ok then
    print("[whale] OnStableStudy HttpPost failed: " .. tostring(err))
  end
end
