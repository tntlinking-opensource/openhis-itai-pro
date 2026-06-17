<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type {
  RegistrationPolicyDto,
  ReleasePolicyDto,
  ScheduleChangePolicyDto,
} from '#/api/booking/booking-policy';

import { onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import {
  deleteReleasePolicyApi,
  loadRegistrationPolicyApi,
  loadReleasePolicyApi,
  loadScheduleChangePolicyApi,
  pageReleasePoliciesApi,
  saveRegistrationPolicyApi,
  saveReleasePolicyApi,
  saveScheduleChangePolicyApi,
} from '#/api/booking/booking-policy';
import { useBookingOrganization } from '#/views/booking/composables/use-booking-organization';
import { useRequest } from '#/hooks/use-request';
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElSwitch,
  ElTabPane,
  ElTabs,
} from 'element-plus';

defineOptions({ name: 'BookingPolicy' });

const { organizationId } = useBookingOrganization();
const activeTab = ref('release');
const list = ref<ReleasePolicyDto[]>([]);
const total = ref(0);
const editVisible = ref(false);
const editForm = ref<Partial<ReleasePolicyDto>>({});

const regPolicy = ref<RegistrationPolicyDto>({
  walkInSlotMode: 'APPOINTMENT_FIRST',
  autoCheckInOnRegister: false,
  onCancelPolicy: 'RELEASE_SLOT_ONLY',
  allowEditPractitionerFromAppointment: false,
});

const stopPolicy = ref<ScheduleChangePolicyDto>({
  onPractitionerStopMode: 'MANUAL',
  transferMatchRule: 'SAME_SERVICE_SAME_SLOT',
  transferRequireSameService: true,
  transferAllowCrossPractitioner: true,
  transferPatientConfirmMode: 'NONE',
  blockDeactivateIfUnhandled: true,
  notifyOnTransfer: true,
  notifyOnCancelStop: true,
  freezeSourceSlotOnPreview: true,
  freezeTtlMinutes: 15,
});
const stopPolicyPersisted = ref(false);

const query = reactive({
  pageNo: 1,
  pageSize: 20,
  keyword: '',
});

const formItems: FormItemConfig[] = [
  { prop: 'keyword', label: '关键词', type: 'input', placeholder: '策略编码/名称' },
];

const columns: TableColumn[] = [
  { prop: 'policyCode', label: '策略编码', minWidth: 120 },
  { prop: 'policyName', label: '策略名称', minWidth: 140 },
  { prop: 'advanceDays', label: '提前天数', width: 90 },
  { prop: 'slotMinutes', label: '时段(分)', width: 90 },
  { prop: 'capacityPerSlot', label: '单段容量', width: 90 },
  { prop: 'actions', label: '操作', width: 140, slot: 'actions' },
];

const walkInModeOptions = [
  { value: 'WALK_IN_NO_SLOT', label: '纯现场不占号' },
  { value: 'APPOINTMENT_FIRST', label: '先约后挂' },
  { value: 'WALK_IN_WITH_SLOT', label: '现场占当日号源' },
];

const cancelPolicyOptions = [
  { value: 'RELEASE_SLOT_ONLY', label: '仅释放号源' },
  { value: 'CANCEL_APPOINTMENT', label: '取消预约' },
  { value: 'KEEP_APPOINTMENT', label: '保留预约' },
];

const stopModeOptions = [
  { value: 'MANUAL', label: '人工处置' },
  { value: 'TRANSFER', label: '自动转交' },
  { value: 'RESCHEDULE', label: '自动改约' },
  { value: 'CANCEL', label: '自动取消预约' },
];

const transferMatchOptions = [
  { value: 'SAME_SERVICE_SAME_SLOT', label: '同时段同号别' },
  { value: 'SAME_SERVICE_NEAREST', label: '同日最近可用时段' },
  { value: 'SAME_DEPT_ANY', label: '同日同科室任意号别' },
];

const confirmModeOptions = [
  { value: 'NONE', label: '无需确认' },
  { value: 'SMS_CONFIRM', label: '短信确认' },
  { value: 'WINDOW_CONFIRM', label: '窗口确认' },
];

const { run: runPage, loading } = useRequest(pageReleasePoliciesApi, {
  manual: true,
  onSuccess: (res) => {
    list.value = res.content ?? [];
    total.value = Number(res.totalCount ?? 0);
  },
});

const { run: runLoadReg, loading: regLoading } = useRequest(loadRegistrationPolicyApi, {
  manual: true,
  onSuccess: (res) => {
    regPolicy.value = {
      walkInSlotMode: res?.walkInSlotMode ?? 'APPOINTMENT_FIRST',
      autoCheckInOnRegister: !!res?.autoCheckInOnRegister,
      onCancelPolicy: res?.onCancelPolicy ?? 'RELEASE_SLOT_ONLY',
      allowEditPractitionerFromAppointment: !!res?.allowEditPractitionerFromAppointment,
    };
  },
});

const { run: runSaveReg, loading: saveRegLoading } = useRequest(saveRegistrationPolicyApi, {
  manual: true,
  onSuccess: () => ElMessage.success('挂号策略已保存'),
});

const { run: runLoadStop, loading: stopLoading } = useRequest(loadScheduleChangePolicyApi, {
  manual: true,
  onSuccess: (res) => {
    stopPolicy.value = {
      id: res?.id,
      organizationId: res?.organizationId,
      onPractitionerStopMode: res?.onPractitionerStopMode ?? 'MANUAL',
      transferMatchRule: res?.transferMatchRule ?? 'SAME_SERVICE_SAME_SLOT',
      transferRequireSameService: res?.transferRequireSameService ?? true,
      transferAllowCrossPractitioner: res?.transferAllowCrossPractitioner ?? true,
      transferPatientConfirmMode: res?.transferPatientConfirmMode ?? 'NONE',
      blockDeactivateIfUnhandled: res?.blockDeactivateIfUnhandled ?? true,
      notifyOnTransfer: res?.notifyOnTransfer ?? true,
      notifyOnCancelStop: res?.notifyOnCancelStop ?? true,
      freezeSourceSlotOnPreview: res?.freezeSourceSlotOnPreview ?? true,
      freezeTtlMinutes: res?.freezeTtlMinutes ?? 15,
    };
    stopPolicyPersisted.value = !!res?.persisted;
  },
});

const { run: runSaveStop, loading: saveStopLoading } = useRequest(saveScheduleChangePolicyApi, {
  manual: true,
  onSuccess: (res) => {
    stopPolicy.value.id = res?.id;
    stopPolicyPersisted.value = true;
    ElMessage.success('停诊策略已保存');
  },
});

async function loadReleaseList() {
  await runPage({
    keyword: String(query.keyword || '').trim() || undefined,
    pageNo: query.pageNo,
    pageSize: query.pageSize,
  });
}

async function loadRegPolicy() {
  if (!organizationId.value) return;
  await runLoadReg({ organizationId: organizationId.value });
}

async function loadStopPolicy() {
  if (!organizationId.value) return;
  await runLoadStop({ organizationId: organizationId.value });
}

function handleSearch() {
  query.pageNo = 1;
  void loadReleaseList();
}

function handlePagination(pagination: { limit: number; page: number }) {
  query.pageNo = pagination.page;
  query.pageSize = pagination.limit;
  void loadReleaseList();
}

function openCreate() {
  editForm.value = {
    advanceDays: 7,
    slotMinutes: 30,
    capacityPerSlot: 10,
  };
  editVisible.value = true;
}

async function openEdit(row: ReleasePolicyDto) {
  if (!row.id) return;
  const detail = await loadReleasePolicyApi({ id: row.id });
  editForm.value = { ...detail };
  editVisible.value = true;
}

async function handleSaveRelease() {
  const form = editForm.value;
  if (!form.policyCode?.trim() || !form.policyName?.trim()) {
    ElMessage.warning('请填写策略编码与名称');
    return;
  }
  await saveReleasePolicyApi({
    id: form.id,
    policyCode: form.policyCode.trim(),
    policyName: form.policyName.trim(),
    advanceDays: Number(form.advanceDays ?? 7),
    slotMinutes: Number(form.slotMinutes ?? 30),
    capacityPerSlot: Number(form.capacityPerSlot ?? 10),
    channelQuotaJson: form.channelQuotaJson,
  });
  ElMessage.success('放号策略已保存');
  editVisible.value = false;
  await loadReleaseList();
}

async function handleDelete(row: ReleasePolicyDto) {
  if (!row.id) return;
  try {
    await ElMessageBox.confirm(`确认删除策略「${row.policyName}」？`, '删除放号策略');
    await deleteReleasePolicyApi({ id: row.id });
    ElMessage.success('已删除');
    await loadReleaseList();
  } catch {
    // cancel
  }
}

async function handleSaveRegPolicy() {
  if (!organizationId.value) {
    ElMessage.warning('请先选择登录科室');
    return;
  }
  await runSaveReg({
    organizationId: organizationId.value,
    walkInSlotMode: regPolicy.value.walkInSlotMode ?? 'APPOINTMENT_FIRST',
    autoCheckInOnRegister: regPolicy.value.autoCheckInOnRegister,
    onCancelPolicy: regPolicy.value.onCancelPolicy,
    allowEditPractitionerFromAppointment: regPolicy.value.allowEditPractitionerFromAppointment,
  });
}

async function handleSaveStopPolicy() {
  if (!organizationId.value) {
    ElMessage.warning('请先选择登录科室');
    return;
  }
  const form = stopPolicy.value;
  await runSaveStop({
    id: form.id,
    organizationId: organizationId.value,
    onPractitionerStopMode: form.onPractitionerStopMode ?? 'MANUAL',
    transferMatchRule: form.transferMatchRule ?? 'SAME_SERVICE_SAME_SLOT',
    transferRequireSameService: !!form.transferRequireSameService,
    transferAllowCrossPractitioner: !!form.transferAllowCrossPractitioner,
    transferPatientConfirmMode: form.transferPatientConfirmMode ?? 'NONE',
    blockDeactivateIfUnhandled: !!form.blockDeactivateIfUnhandled,
    notifyOnTransfer: !!form.notifyOnTransfer,
    notifyOnCancelStop: !!form.notifyOnCancelStop,
    freezeSourceSlotOnPreview: !!form.freezeSourceSlotOnPreview,
    freezeTtlMinutes: Number(form.freezeTtlMinutes ?? 15),
  });
}

onMounted(async () => {
  await loadReleaseList();
  await loadRegPolicy();
  await loadStopPolicy();
});
</script>

<template>
  <Page auto-content-height>
    <div class="booking-policy-page">
      <ElTabs v-model="activeTab">
        <ElTabPane label="放号策略" name="release">
          <TableLayout
            v-model:query-params="query"
            :form-items="formItems"
            :table-columns="columns"
            :table-data="list"
            :total="total"
            :loading="loading"
            @search="handleSearch"
            @pagination="handlePagination"
          >
            <template #toolbar-actions>
              <ElButton type="primary" @click="openCreate">新建策略</ElButton>
            </template>
            <template #actions="{ row }">
              <ElButton link type="primary" @click="openEdit(row)">编辑</ElButton>
              <ElButton link type="danger" @click="handleDelete(row)">删除</ElButton>
            </template>
          </TableLayout>
        </ElTabPane>

        <ElTabPane label="挂号策略" name="registration">
          <div v-loading="regLoading" class="booking-policy-page__reg-form">
            <ElForm label-width="180px">
              <ElFormItem label="现场占号模式">
                <ElSelect v-model="regPolicy.walkInSlotMode" style="width: 320px">
                  <ElOption
                    v-for="item in walkInModeOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </ElSelect>
              </ElFormItem>
              <ElFormItem label="挂号后自动报到">
                <ElSwitch v-model="regPolicy.autoCheckInOnRegister" />
              </ElFormItem>
              <ElFormItem label="退号后预约处理">
                <ElSelect v-model="regPolicy.onCancelPolicy" style="width: 320px">
                  <ElOption
                    v-for="item in cancelPolicyOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </ElSelect>
              </ElFormItem>
              <ElFormItem label="允许改约医师">
                <ElSwitch v-model="regPolicy.allowEditPractitionerFromAppointment" />
              </ElFormItem>
              <ElFormItem>
                <ElButton type="primary" :loading="saveRegLoading" @click="handleSaveRegPolicy">
                  保存挂号策略
                </ElButton>
              </ElFormItem>
            </ElForm>
          </div>
        </ElTabPane>

        <ElTabPane label="停诊策略" name="scheduleChange">
          <div v-loading="stopLoading" class="booking-policy-page__reg-form">
            <p v-if="!stopPolicyPersisted" class="booking-policy-page__hint">
              当前科室尚未保存停诊策略，以下为系统默认值；保存后将写入院点配置。
            </p>
            <ElForm label-width="200px">
              <ElFormItem label="停诊默认处置">
                <ElSelect v-model="stopPolicy.onPractitionerStopMode" style="width: 320px">
                  <ElOption
                    v-for="item in stopModeOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </ElSelect>
              </ElFormItem>
              <ElFormItem label="转交匹配规则">
                <ElSelect v-model="stopPolicy.transferMatchRule" style="width: 320px">
                  <ElOption
                    v-for="item in transferMatchOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </ElSelect>
              </ElFormItem>
              <ElFormItem label="转交须同号源类型">
                <ElSwitch v-model="stopPolicy.transferRequireSameService" />
              </ElFormItem>
              <ElFormItem label="允许跨医师转交">
                <ElSwitch v-model="stopPolicy.transferAllowCrossPractitioner" />
              </ElFormItem>
              <ElFormItem label="患者确认模式">
                <ElSelect v-model="stopPolicy.transferPatientConfirmMode" style="width: 320px">
                  <ElOption
                    v-for="item in confirmModeOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </ElSelect>
              </ElFormItem>
              <ElFormItem label="未处置预约禁止停诊">
                <ElSwitch v-model="stopPolicy.blockDeactivateIfUnhandled" />
              </ElFormItem>
              <ElFormItem label="转交后通知患者">
                <ElSwitch v-model="stopPolicy.notifyOnTransfer" />
              </ElFormItem>
              <ElFormItem label="停诊取消时通知">
                <ElSwitch v-model="stopPolicy.notifyOnCancelStop" />
              </ElFormItem>
              <ElFormItem label="预览后冻结源号源">
                <ElSwitch v-model="stopPolicy.freezeSourceSlotOnPreview" />
              </ElFormItem>
              <ElFormItem label="冻结超时(分钟)">
                <ElInputNumber v-model="stopPolicy.freezeTtlMinutes" :min="1" :max="120" />
              </ElFormItem>
              <ElFormItem>
                <ElButton type="primary" :loading="saveStopLoading" @click="handleSaveStopPolicy">
                  保存停诊策略
                </ElButton>
              </ElFormItem>
            </ElForm>
          </div>
        </ElTabPane>
      </ElTabs>

      <ElDialog
        v-model="editVisible"
        :title="editForm.id ? '编辑放号策略' : '新建放号策略'"
        width="520px"
      >
        <ElForm label-width="100px">
          <ElFormItem label="策略编码" required>
            <ElInput v-model="editForm.policyCode" placeholder="如 DEFAULT_OUTPATIENT" />
          </ElFormItem>
          <ElFormItem label="策略名称" required>
            <ElInput v-model="editForm.policyName" placeholder="门诊默认放号" />
          </ElFormItem>
          <ElFormItem label="提前天数">
            <ElInputNumber v-model="editForm.advanceDays" :min="0" :max="90" />
          </ElFormItem>
          <ElFormItem label="时段粒度">
            <ElInputNumber v-model="editForm.slotMinutes" :min="5" :max="240" :step="5" />
          </ElFormItem>
          <ElFormItem label="单段容量">
            <ElInputNumber v-model="editForm.capacityPerSlot" :min="1" :max="999" />
          </ElFormItem>
          <ElFormItem label="渠道配额">
            <ElInput
              v-model="editForm.channelQuotaJson"
              type="textarea"
              :rows="3"
              placeholder='JSON，如 {"WINDOW":60,"MINIAPP":40}'
            />
          </ElFormItem>
        </ElForm>
        <template #footer>
          <ElButton @click="editVisible = false">取消</ElButton>
          <ElButton type="primary" @click="handleSaveRelease">保存</ElButton>
        </template>
      </ElDialog>
    </div>
  </Page>
</template>

<style scoped>
.booking-policy-page {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.booking-policy-page__reg-form {
  max-width: 720px;
  padding: 12px 0;
}

.booking-policy-page__hint {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>
