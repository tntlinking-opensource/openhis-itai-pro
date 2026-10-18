<script lang="ts" setup>
import type {
  HongqiResettleBatch,
  HongqiResettleDetail,
  HongqiResettlePatient,
} from '#/api/chs/hongqi-resettle';

import { computed, onMounted, ref } from 'vue';

import { Page } from '@zxplat/common-ui';

import { ElEmpty, ElInput, ElMessage, ElMessageBox, ElTag } from 'element-plus';

import {
  chargeGuojiClinicApi,
  chargeGuojiRegistrationApi,
  listHongqiResettlePatientsApi,
  loadHongqiResettleDetailApi,
  registerGuojiApi,
  revokeHongqiRegistrationApi,
  revokeHongqiSettleApi,
} from '#/api/chs/hongqi-resettle';
import BatchList from '#/chs/hongqi-resettle/batch-list.vue';

defineOptions({ name: 'ChsHongqiResettle' });

const keyword = ref('');
const progressFilter = ref<'all' | 'refundTodo' | 'refundDone' | 'collectTodo' | 'collectDone'>(
  'all',
);
const loadingList = ref(false);
const loadingDetail = ref(false);
const revoking = ref(false);
const patients = ref<HongqiResettlePatient[]>([]);
const selectedId = ref('');
const detail = ref<HongqiResettleDetail | null>(null);

const selected = computed(() =>
  patients.value.find((row) => row.patientId === selectedId.value),
);

const refundDoneCount = computed(
  () => patients.value.filter((row) => row.refundName === '中医院退完成').length,
);
const refundTodoCount = computed(() => patients.value.length - refundDoneCount.value);
const collectDoneCount = computed(
  () => patients.value.filter((row) => row.collectName === '国基收完成').length,
);
const collectTodoCount = computed(() => patients.value.length - collectDoneCount.value);

const visiblePatients = computed(() => {
  const rows = patients.value.filter((row) => {
    if (progressFilter.value === 'refundDone') {
      return row.refundName === '中医院退完成';
    }
    if (progressFilter.value === 'refundTodo') {
      return row.refundName !== '中医院退完成';
    }
    if (progressFilter.value === 'collectDone') {
      return row.collectName === '国基收完成';
    }
    if (progressFilter.value === 'collectTodo') {
      return row.collectName !== '国基收完成';
    }
    return true;
  });
  return [...rows].sort((left, right) => {
    const leftRank = rankPatient(left);
    const rightRank = rankPatient(right);
    return leftRank - rightRank;
  });
});

function rankPatient(row: HongqiResettlePatient) {
  const refundDone = row.refundName === '中医院退完成' ? 1 : 0;
  const collectDone = row.collectName === '国基收完成' ? 1 : 0;
  return refundDone + collectDone;
}

function money(value: number | string | undefined) {
  const amount = Number(value ?? 0);
  if (!Number.isFinite(amount)) {
    return '0.00';
  }
  return amount.toFixed(2);
}

async function loadPatients() {
  loadingList.value = true;
  try {
    const rows = await listHongqiResettlePatientsApi(keyword.value.trim());
    patients.value = Array.isArray(rows) ? rows : [];
    const stillThere = patients.value.some((row) => row.patientId === selectedId.value);
    if (!stillThere) {
      selectedId.value = '';
      detail.value = null;
    }
  } finally {
    loadingList.value = false;
  }
}

async function selectPatient(row: HongqiResettlePatient) {
  const patientId = row.patientId ?? '';
  if (!patientId) {
    return;
  }
  selectedId.value = patientId;
  loadingDetail.value = true;
  try {
    detail.value = await loadHongqiResettleDetailApi(patientId);
  } catch {
    detail.value = null;
  } finally {
    loadingDetail.value = false;
  }
}

async function reloadSelected() {
  const patientId = selectedId.value;
  if (!patientId) {
    return;
  }
  detail.value = await loadHongqiResettleDetailApi(patientId);
  await loadPatients();
}

async function revokeSettle(batch: HongqiResettleBatch) {
  const paymentId = batch.paymentId ?? '';
  const contractBusNo = batch.contractBusNo ?? '';
  if (!paymentId || !contractBusNo) {
    return;
  }
  try {
    await ElMessageBox.confirm(
      `将使用合同号 ${contractBusNo} 调用门诊结算撤销（2208），结算号 ${batch.setlId || '—'}。只撤销中医院医保平台结算，不退现金、不改合同。`,
      '取消中医院收费',
      { type: 'warning', confirmButtonText: '确认撤销', cancelButtonText: '返回' },
    );
  } catch {
    return;
  }
  revoking.value = true;
  try {
    const result = await revokeHongqiSettleApi(paymentId);
    const text = result?.messages?.filter(Boolean).join('；');
    ElMessage.success(text || `已按合同号 ${contractBusNo} 处理`);
    await reloadSelected();
  } finally {
    revoking.value = false;
  }
}

function guojiTarget(batch: HongqiResettleBatch) {
  const city = 'H22010402403-220199';
  const province = 'H22010402403-229900';
  if (batch.contractBusNo === city || batch.contractBusNo === 'H22010202493-220199') {
    return 'H22010202493-220199';
  }
  if (batch.contractBusNo === province || batch.contractBusNo === 'H22010202493-229900') {
    return 'H22010202493-229900';
  }
  return batch.contractBusNo || '国基合同';
}

async function runGuoji(
  batch: HongqiResettleBatch,
  title: string,
  action: string,
  call: (encounterId: string, paymentId: string, contractBusNo: string) => Promise<{ messages?: string[] } | undefined>,
) {
  const encounterId = batch.encounterId ?? '';
  const paymentId = batch.paymentId ?? '';
  const contractBusNo = batch.contractBusNo ?? '';
  if (!encounterId || !contractBusNo) {
    return;
  }
  const target = guojiTarget(batch);
  try {
    await ElMessageBox.confirm(
      `将核对当前合同必须是 ${target}，然后${action}。合同仍是中医院时会拒绝。不另收现金，不改原支付合同号。`,
      title,
      { type: 'warning', confirmButtonText: '确认', cancelButtonText: '返回' },
    );
  } catch {
    return;
  }
  revoking.value = true;
  try {
    const result = await call(encounterId, paymentId, contractBusNo);
    const text = result?.messages?.filter(Boolean).join('；');
    ElMessage.success(text || `已按国基合同号 ${target} 处理`);
    await reloadSelected();
  } finally {
    revoking.value = false;
  }
}

async function registerGuoji(batch: HongqiResettleBatch) {
  await runGuoji(batch, '挂国基号', '用身份证挂国基号（2201）', registerGuojiApi);
}

async function chargeGuoji(batch: HongqiResettleBatch, kind: 'registration' | 'clinic') {
  if (kind === 'registration') {
    await runGuoji(batch, '收取国基挂号费', '收取国基挂号费（2206、2207）', chargeGuojiRegistrationApi);
    return;
  }
  await runGuoji(batch, '收取国基收费', '收取国基门诊费（2206、2207）', chargeGuojiClinicApi);
}

async function revokeRegistration(batch: HongqiResettleBatch) {
  const encounterId = batch.encounterId ?? '';
  const contractBusNo = batch.contractBusNo ?? '';
  if (!encounterId || !contractBusNo) {
    return;
  }
  try {
    await ElMessageBox.confirm(
      `将使用合同号 ${contractBusNo} 调用门诊挂号撤销（2202），就诊号 ${batch.encounterNo || '—'}。若这笔还有未撤销的中医院结算，会先被拒绝。不退现金、不改合同。`,
      '取消中医院挂号',
      { type: 'warning', confirmButtonText: '确认撤销', cancelButtonText: '返回' },
    );
  } catch {
    return;
  }
  revoking.value = true;
  try {
    const result = await revokeHongqiRegistrationApi(encounterId, contractBusNo);
    const text = result?.messages?.filter(Boolean).join('；');
    ElMessage.success(text || `已按合同号 ${contractBusNo} 处理`);
    await reloadSelected();
  } finally {
    revoking.value = false;
  }
}

onMounted(() => {
  void loadPatients();
});
</script>

<template>
  <Page auto-content-height>
    <div class="resettle">
      <aside class="resettle-list">
        <div class="resettle-list-head">
          <div class="resettle-title">红旗合同结算患者</div>
          <div class="progress-summary">
            共 {{ patients.length }} 人 · 中医院退完成 {{ refundDoneCount }} 人 · 国基收完成
            {{ collectDoneCount }} 人
          </div>
          <div class="progress-filter">
            <button
              type="button"
              :class="{ 'is-on': progressFilter === 'all' }"
              @click="progressFilter = 'all'"
            >
              全部 {{ patients.length }}
            </button>
            <button
              type="button"
              :class="{ 'is-on': progressFilter === 'refundTodo' }"
              @click="progressFilter = 'refundTodo'"
            >
              中医院退未完成 {{ refundTodoCount }}
            </button>
            <button
              type="button"
              :class="{ 'is-on': progressFilter === 'refundDone' }"
              @click="progressFilter = 'refundDone'"
            >
              中医院退完成 {{ refundDoneCount }}
            </button>
            <button
              type="button"
              :class="{ 'is-on': progressFilter === 'collectTodo' }"
              @click="progressFilter = 'collectTodo'"
            >
              国基收未完成 {{ collectTodoCount }}
            </button>
            <button
              type="button"
              :class="{ 'is-on': progressFilter === 'collectDone' }"
              @click="progressFilter = 'collectDone'"
            >
              国基收完成 {{ collectDoneCount }}
            </button>
          </div>
          <p class="progress-hint">挂号只统计诊查费。门诊费用只看收费撤销和国基收费。</p>
          <ElInput
            v-model="keyword"
            clearable
            placeholder="姓名或电话"
            @clear="loadPatients"
            @keyup.enter="loadPatients"
          />
        </div>
        <div v-loading="loadingList" class="resettle-list-body">
          <button
            v-for="row in visiblePatients"
            :key="row.patientId"
            type="button"
            class="patient-card"
            :class="{ 'is-active': row.patientId === selectedId }"
            @click="selectPatient(row)"
          >
            <div class="patient-card-top">
              <span>
                <span class="patient-name">{{ row.name }}</span>
                <span class="patient-gender">{{ row.gender }}</span>
              </span>
              <span class="patient-tags">
                <ElTag size="small" :type="row.refundName === '中医院退完成' ? 'success' : 'danger'">
                  {{ row.refundName || '中医院退未完成' }}
                </ElTag>
                <ElTag size="small" :type="row.collectName === '国基收完成' ? 'success' : 'warning'">
                  {{ row.collectName || '国基收未完成' }}
                </ElTag>
              </span>
            </div>
            <div class="patient-meta">{{ row.progressDetail || '收费 0/0 · 挂号 0/0' }}</div>
            <div class="patient-meta">{{ row.phone || '无电话' }}</div>
            <div class="patient-meta">{{ row.departments || '未分科室' }}</div>
            <div class="patient-meta">{{ row.lastVisitTime || row.firstVisitTime }}</div>
            <div class="patient-card-foot">
              <ElTag size="small">{{ row.contracts }}</ElTag>
              <span>{{ row.paymentCount }} 笔 · {{ money(row.medfeeSum) }} 元</span>
            </div>
          </button>
          <ElEmpty
            v-if="!loadingList && visiblePatients.length === 0"
            description="没有红旗合同门诊结算"
          />
        </div>
      </aside>

      <main v-loading="loadingDetail" class="resettle-detail">
        <ElEmpty v-if="!selected" description="请选择左侧患者" />
        <template v-else>
          <header class="detail-head">
            <div>
              <div class="resettle-title">
                {{ selected.name }}
                <span class="patient-gender">{{ selected.gender }}</span>
                <ElTag size="small" :type="selected.refundName === '中医院退完成' ? 'success' : 'danger'">
                  {{ selected.refundName || '中医院退未完成' }}
                </ElTag>
                <ElTag size="small" :type="selected.collectName === '国基收完成' ? 'success' : 'warning'">
                  {{ selected.collectName || '国基收未完成' }}
                </ElTag>
              </div>
              <div class="patient-meta">{{ selected.progressDetail }}</div>
              <div class="patient-meta">
                {{ selected.phone || '无电话' }} · {{ selected.contracts }} ·
                {{ selected.firstVisitTime }} 至 {{ selected.lastVisitTime }}
              </div>
            </div>
            <div class="detail-sums">
              <span>挂号费 {{ money(detail?.registrationTotal) }} 元</span>
              <span>门诊费用 {{ money(detail?.clinicTotal) }} 元</span>
            </div>
          </header>

          <section class="fee-block">
            <h2>重新结算挂号费</h2>
            <p class="fee-hint">含诊查费的笔才取消中医院挂号、挂国基号。0 元号不用挂号。</p>
            <BatchList
              kind="registration"
              :batches="detail?.registrationBatches ?? []"
              :busy="revoking"
              empty-text="没有挂号费"
              @revoke-settle="revokeSettle"
              @revoke-registration="revokeRegistration"
              @register-guoji="registerGuoji"
              @charge-guoji="chargeGuoji($event, 'registration')"
            />
          </section>

          <section class="fee-block">
            <h2>重新结算门诊费用</h2>
            <p class="fee-hint">门诊项目只取消中医院收费、收取国基收费，不撤销挂号。</p>
            <BatchList
              kind="clinic"
              :batches="detail?.clinicBatches ?? []"
              :busy="revoking"
              empty-text="没有门诊费用"
              @revoke-settle="revokeSettle"
              @revoke-registration="revokeRegistration"
              @register-guoji="registerGuoji"
              @charge-guoji="chargeGuoji($event, 'clinic')"
            />
          </section>
        </template>
      </main>
    </div>
  </Page>
</template>

<style scoped>
.resettle {
  display: flex;
  height: 100%;
  min-height: 0;
  background: #f5f7fa;
}

.resettle-list {
  display: flex;
  flex-direction: column;
  width: 320px;
  min-width: 320px;
  background: #fff;
  border-right: 1px solid #e5e7eb;
}

.resettle-list-head,
.detail-head {
  padding: 16px;
  border-bottom: 1px solid #e5e7eb;
}

.resettle-title {
  margin-bottom: 8px;
  font-size: 16px;
  font-weight: 600;
}

.progress-summary {
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
}

.progress-filter {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}

.progress-filter button {
  padding: 2px 8px;
  color: #4b5563;
  cursor: pointer;
  background: #f3f4f6;
  border: 0;
  border-radius: 999px;
}

.progress-filter button.is-on {
  color: #fff;
  background: #2563eb;
}

.progress-hint {
  margin: 0 0 8px;
  color: #6b7280;
  font-size: 12px;
}

.resettle-list-body,
.resettle-detail {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.patient-card {
  display: block;
  width: 100%;
  padding: 12px 16px;
  text-align: left;
  cursor: pointer;
  background: #fff;
  border: 0;
  border-bottom: 1px solid #f0f0f0;
}

.patient-card.is-active {
  background: #ecf5ff;
}

.patient-card-top,
.patient-card-foot,
.detail-head,
.batch-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.patient-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  justify-content: flex-end;
}

.patient-name {
  font-weight: 600;
}

.patient-gender,
.patient-meta,
.batch-sub {
  color: #6b7280;
  font-size: 12px;
}

.patient-card-foot {
  margin-top: 6px;
  color: #374151;
  font-size: 12px;
}

.resettle-detail {
  padding: 0 16px 24px;
}

.detail-head {
  position: sticky;
  top: 0;
  z-index: 1;
  margin: 0 -16px 8px;
  background: #fff;
}

.detail-sums {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-weight: 600;
  text-align: right;
}

.fee-block h2 {
  margin: 16px 0 4px;
  font-size: 16px;
}

.fee-hint {
  margin: 0 0 8px;
  color: #6b7280;
  font-size: 13px;
}

.batch {
  margin-bottom: 12px;
  padding: 12px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.batch-head {
  margin-bottom: 4px;
}

.batch-head span {
  margin-left: 8px;
}

.batch-money {
  font-weight: 600;
}

.batch-sub {
  margin-bottom: 8px;
}
</style>
