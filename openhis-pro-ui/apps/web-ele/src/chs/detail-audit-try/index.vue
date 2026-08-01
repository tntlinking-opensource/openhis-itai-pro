<script setup lang="ts">
import type { ChargeType } from '#/api/core/common';
import type {
  AuditAnalyzeResult,
  AuditViolation,
  AuditViolationDetail,
} from '#/chs/api/detail-audit-try';

import { computed, onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import {
  ElAlert,
  ElButton,
  ElCard,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElRadio,
  ElRadioGroup,
  ElSelect,
  ElTabPane,
  ElTabs,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { getChargeTypeListApi } from '#/api/core/common';
import {
  analyze3101Api,
  analyze3101FromEncounterApi,
  feedback3103Api,
} from '#/chs/api/detail-audit-try';
import {
  DETAIL_AUDIT_DSPO_WAY_OPTIONS,
  DETAIL_AUDIT_TRIG_SCEN_OPTIONS,
  DETAIL_AUDIT_WARN_TYPE_OPTIONS,
  formatSevDeg,
} from '#/chs/constants/detail-audit-try';
import {
  filterInsuranceChargeTypes,
  resolveDefaultInsuranceBusNo,
} from '#/chs/constants/insurance-contract';
import { ensureInsuranceSignIn } from '#/chs/utils/ensure-insurance-sign-in';
import { useRequest } from '#/hooks/use-request';

defineOptions({ name: 'ChsDetailAuditTryPage' });

type SourceTab = 'encounter' | 'json';

const sourceTab = ref<SourceTab>('encounter');
const contractBusNo = ref<string>();
const contractId = ref<string>();
const chargeTypeOptions = ref<ChargeType[]>([]);

const trigScen = ref('7');
const syscode = ref('');
const encounterId = ref('');
const chargeItemIdsText = ref('');
const mdtrtId = ref('');
const medinsType = ref('A');
const medinsLv = ref('11');
const brdy = ref('');

const commandJson = ref('');

const analyzeResult = ref<AuditAnalyzeResult | null>(null);
const selectedViolation = ref<AuditViolation | null>(null);

const warnType = ref('1');
const dspoWay = ref('2');
const dspoWayRea = ref('');

const selectedContract = computed(() =>
  chargeTypeOptions.value.find((item) => item.busNo === contractBusNo.value),
);

const violationRows = computed(() => analyzeResult.value?.violations ?? []);

const detailRows = computed<AuditViolationDetail[]>(() => {
  const details = selectedViolation.value?.details;
  if (!details || details.length === 0) {
    return [];
  }
  return details;
});

/** 试调：契约 bus_no 必填，未选时抛错（由页面 catch Toast）。 */
function requireContractBusNo(): string {
  const busNo = contractBusNo.value?.trim();
  if (!busNo) {
    throw new Error('请先选择医保契约');
  }
  return busNo;
}

/** 将当前选中契约同步到 contractId 表单字段。 */
function syncContractId() {
  const selected = selectedContract.value;
  contractId.value = selected?.value ? String(selected.value) : '';
}

/**
 * 解析收费明细 id 文本（逗号/空白分隔）；空串返回 undefined 表示不按明细过滤。
 */
function parseChargeItemIds(): Array<number | string> | undefined {
  const text = chargeItemIdsText.value.trim();
  if (!text) {
    return undefined;
  }
  return text
    .split(/[,，\s]+/)
    .map((s) => s.trim())
    .filter(Boolean);
}

/** 违规表行选中，驱动右侧明细区。 */
function onSelectViolation(row: AuditViolation | null) {
  selectedViolation.value = row;
}

const { run: loadChargeTypes, loading: loadingChargeTypes } = useRequest(async () => {
  const list = await getChargeTypeListApi();
  const eligible = filterInsuranceChargeTypes(list);
  chargeTypeOptions.value = eligible;
  contractBusNo.value = resolveDefaultInsuranceBusNo(eligible, contractBusNo.value);
  syncContractId();
});

const { run: runAnalyzeFromEncounter, loading: loadingEncounter } = useRequest(async () => {
  syncContractId();
  const busNo = requireContractBusNo();
  if (!encounterId.value.trim()) {
    throw new Error('请填写 HIS 就诊 ID');
  }
  if (!mdtrtId.value.trim()) {
    throw new Error('请填写医保就诊 ID（mdtrtId）');
  }
  if (!trigScen.value) {
    throw new Error('请选择触发场景');
  }
  if (!medinsType.value.trim() || !medinsLv.value.trim()) {
    throw new Error('请填写机构类型 medinsType 与等级 medinsLv');
  }
  await ensureInsuranceSignIn(busNo);
  const result = await analyze3101FromEncounterApi({
    contractBusNo: busNo,
    contractId: contractId.value || undefined,
    encounterId: encounterId.value.trim(),
    chargeItemIds: parseChargeItemIds(),
    mdtrtId: mdtrtId.value.trim(),
    trigScen: trigScen.value,
    syscode: syscode.value.trim() || undefined,
    medinsType: medinsType.value.trim(),
    medinsLv: medinsLv.value.trim(),
    brdy: brdy.value.trim() || undefined,
  });
  analyzeResult.value = result;
  selectedViolation.value = result.violations?.[0] ?? null;
  const n = result.violations?.length ?? 0;
  if (n === 0) {
    ElMessage.success('3101 成功：无违规提醒');
  } else {
    ElMessage.warning(`3101 成功：发现 ${n} 条规则提醒`);
  }
});

const { run: runAnalyzeJson, loading: loadingJson } = useRequest(async () => {
  syncContractId();
  const busNo = requireContractBusNo();
  const raw = commandJson.value.trim();
  if (!raw) {
    throw new Error('请粘贴 3101 Command JSON');
  }
  let parsed: Record<string, unknown>;
  try {
    parsed = JSON.parse(raw) as Record<string, unknown>;
  } catch {
    throw new Error('Command JSON 解析失败');
  }
  if (!parsed.contractBusNo) {
    parsed.contractBusNo = busNo;
  }
  if (!parsed.contractId && contractId.value) {
    parsed.contractId = contractId.value;
  }
  await ensureInsuranceSignIn(busNo);
  const result = await analyze3101Api(parsed);
  analyzeResult.value = result;
  selectedViolation.value = result.violations?.[0] ?? null;
  const n = result.violations?.length ?? 0;
  if (n === 0) {
    ElMessage.success('3101 成功：无违规提醒');
  } else {
    ElMessage.warning(`3101 成功：发现 ${n} 条规则提醒`);
  }
});

const { run: runFeedback, loading: loadingFeedback } = useRequest(async () => {
  syncContractId();
  const busNo = requireContractBusNo();
  const violations = analyzeResult.value?.violations ?? [];
  if (violations.length === 0) {
    throw new Error('当前无违规结果，无需反馈 3103');
  }
  if (dspoWay.value === '1' && !dspoWayRea.value.trim()) {
    throw new Error('继续执行时须填写处置原因');
  }
  const warns: Array<{
    warnRsltId: string;
    dspoWay: string;
    dspoWayRea?: string;
    jrId?: string;
  }> = [];
  for (const v of violations) {
    const details = v.details ?? [];
    if (details.length === 0) {
      if (v.jrId) {
        warns.push({
          warnRsltId: v.jrId,
          dspoWay: dspoWay.value,
          dspoWayRea: dspoWayRea.value.trim() || undefined,
          jrId: v.jrId,
        });
      }
      continue;
    }
    for (const d of details) {
      const rsltId = d.jrdId || v.jrId;
      if (!rsltId) {
        continue;
      }
      warns.push({
        warnRsltId: rsltId,
        dspoWay: dspoWay.value,
        dspoWayRea: dspoWayRea.value.trim() || undefined,
        jrId: v.jrId,
      });
    }
  }
  if (warns.length === 0) {
    throw new Error('无法生成 warns（缺少 jrdId/jrId）');
  }
  await ensureInsuranceSignIn(busNo);
  await feedback3103Api({
    contractBusNo: busNo,
    contractId: contractId.value || undefined,
    warnType: warnType.value,
    warns,
  });
  ElMessage.success(`3103 反馈成功（${warns.length} 条）`);
});

onMounted(() => {
  void loadChargeTypes();
});
</script>

<template>
  <Page
    auto-content-height
    description="运维试调：从就诊组装或粘贴 Command 调用 3101，再对违规明细反馈 3103。不进入收费资金链。"
    title="事前提醒试调"
  >
    <ElAlert
      :closable="false"
      class="mb-3"
      show-icon
      title="须配置 openhis.chs.audit.syscode；3103 的 warn_rslt_id 填明细 jrd_id。平台失败将阻断，不可静默跳过。"
      type="info"
    />

    <ElCard class="mb-3" shadow="never">
      <ElForm :inline="true" label-width="96px" @submit.prevent>
        <ElFormItem label="医保契约">
          <ElSelect
            v-model="contractBusNo"
            :loading="loadingChargeTypes"
            clearable
            filterable
            placeholder="选择契约"
            style="width: 220px"
            @change="syncContractId"
          >
            <ElOption
              v-for="item in chargeTypeOptions"
              :key="String(item.busNo)"
              :label="`${item.displayName || item.busNo}（${item.busNo}）`"
              :value="item.busNo"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="syscode">
          <ElInput
            v-model="syscode"
            clearable
            placeholder="可空，回落服务端配置"
            style="width: 180px"
          />
        </ElFormItem>
      </ElForm>
    </ElCard>

    <ElCard class="mb-3" shadow="never">
      <ElTabs v-model="sourceTab">
        <ElTabPane label="从就诊组装" name="encounter">
          <ElForm label-width="120px" @submit.prevent>
            <ElFormItem label="触发场景" required>
              <ElSelect v-model="trigScen" style="width: 240px">
                <ElOption
                  v-for="opt in DETAIL_AUDIT_TRIG_SCEN_OPTIONS"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="HIS 就诊 ID" required>
              <ElInput
                v-model="encounterId"
                clearable
                placeholder="encounterId"
                style="width: 240px"
              />
            </ElFormItem>
            <ElFormItem label="收费项 ID">
              <ElInput
                v-model="chargeItemIdsText"
                clearable
                placeholder="可选，逗号分隔"
                style="width: 360px"
              />
            </ElFormItem>
            <ElFormItem label="医保就诊 ID" required>
              <ElInput
                v-model="mdtrtId"
                clearable
                placeholder="mdtrtId（须已平台挂号）"
                style="width: 240px"
              />
            </ElFormItem>
            <ElFormItem label="机构类型/等级" required>
              <ElInput
                v-model="medinsType"
                clearable
                placeholder="medinsType 如 A"
                style="width: 120px"
              />
              <ElInput
                v-model="medinsLv"
                class="ml-2"
                clearable
                placeholder="medinsLv 如 11"
                style="width: 120px"
              />
            </ElFormItem>
            <ElFormItem label="出生日期">
              <ElInput
                v-model="brdy"
                clearable
                placeholder="yyyy-MM-dd 可选"
                style="width: 180px"
              />
            </ElFormItem>
            <ElFormItem>
              <ElButton :loading="loadingEncounter" type="primary" @click="runAnalyzeFromEncounter">
                发送 3101
              </ElButton>
            </ElFormItem>
          </ElForm>
        </ElTabPane>

        <ElTabPane label="粘贴 Command JSON" name="json">
          <ElForm label-width="120px" @submit.prevent>
            <ElFormItem label="Command JSON">
              <ElInput
                v-model="commandJson"
                :rows="12"
                placeholder="粘贴 AuditAnalyze3101Command JSON（可含 patients 树）"
                type="textarea"
              />
            </ElFormItem>
            <ElFormItem>
              <ElButton :loading="loadingJson" type="primary" @click="runAnalyzeJson">
                发送 3101
              </ElButton>
            </ElFormItem>
          </ElForm>
        </ElTabPane>
      </ElTabs>
    </ElCard>

    <ElCard class="mb-3" shadow="never">
      <template #header>
        <span>
          3101 结果
          <template v-if="analyzeResult">
            · 共 {{ violationRows.length }} 条
            <template v-if="analyzeResult.contractBusNo">
              · {{ analyzeResult.contractBusNo }}
            </template>
            <template v-if="analyzeResult.trigScen"> · trig={{ analyzeResult.trigScen }} </template>
          </template>
        </span>
      </template>

      <ElTable
        :data="violationRows"
        highlight-current-row
        max-height="320"
        stripe
        @current-change="onSelectViolation"
      >
        <ElTableColumn label="严重程度" min-width="100">
          <template #default="{ row }">
            {{ formatSevDeg(row.sevDeg) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="规则名称" min-width="180" prop="ruleName" />
        <ElTableColumn label="违规内容" min-width="280" prop="volaCont" show-overflow-tooltip />
        <ElTableColumn label="违规金额" min-width="100" prop="volaAmt" />
        <ElTableColumn label="就诊ID" min-width="120" prop="mdtrtId" />
        <ElTableColumn label="jrId" min-width="220" prop="jrId" show-overflow-tooltip />
      </ElTable>

      <div class="mb-1 mt-3 text-sm text-gray-500">选中行 · 违规明细</div>
      <ElTable :data="detailRows" max-height="220" stripe>
        <ElTableColumn label="jrdId" min-width="220" prop="jrdId" show-overflow-tooltip />
        <ElTableColumn label="明细类型" min-width="90" prop="volaItemType" />
        <ElTableColumn label="处方/医嘱ID" min-width="140" prop="rxId" />
        <ElTableColumn label="金额" min-width="90" prop="volaAmt" />
        <ElTableColumn label="就诊ID" min-width="120" prop="mdtrtId" />
      </ElTable>
    </ElCard>

    <ElCard shadow="never">
      <template #header>
        <span>模拟反馈 3103（批量：全部违规明细 jrd_id）</span>
      </template>
      <ElForm label-width="120px" @submit.prevent>
        <ElFormItem label="提醒类型">
          <ElSelect v-model="warnType" style="width: 160px">
            <ElOption
              v-for="opt in DETAIL_AUDIT_WARN_TYPE_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="处置方式">
          <ElRadioGroup v-model="dspoWay">
            <ElRadio
              v-for="opt in DETAIL_AUDIT_DSPO_WAY_OPTIONS"
              :key="opt.value"
              :value="opt.value"
            >
              {{ opt.label }}
            </ElRadio>
          </ElRadioGroup>
        </ElFormItem>
        <ElFormItem label="处置原因">
          <ElInput
            v-model="dspoWayRea"
            :rows="2"
            placeholder="dspo_way=1 时必填"
            style="width: 420px"
            type="textarea"
          />
        </ElFormItem>
        <ElFormItem>
          <ElButton
            :disabled="violationRows.length === 0"
            :loading="loadingFeedback"
            type="warning"
            @click="runFeedback"
          >
            确认并反馈 3103
          </ElButton>
        </ElFormItem>
      </ElForm>
    </ElCard>
  </Page>
</template>
