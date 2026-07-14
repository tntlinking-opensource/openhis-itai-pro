<script setup lang="ts">
import type { RegistrationPolicyDto, ScheduleChangePolicyDto } from '#/api/booking/booking-policy';

import { onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import {
  loadRegistrationPolicyApi,
  loadScheduleChangePolicyApi,
  saveRegistrationPolicyApi,
  saveScheduleChangePolicyApi,
} from '#/api/booking/booking-policy';
import { useBookingOrganization } from '#/views/booking/composables/use-booking-organization';
import { useRequest } from '#/hooks/use-request';
import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElSelect,
  ElSwitch,
  ElTabPane,
  ElTabs,
} from 'element-plus';

defineOptions({ name: 'BookingPolicy' });

const { organizationId } = useBookingOrganization();
const activeTab = ref('registration');

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

async function loadRegPolicy() {
  if (!organizationId.value) return;
  await runLoadReg({ organizationId: organizationId.value });
}

async function loadStopPolicy() {
  if (!organizationId.value) return;
  await runLoadStop({ organizationId: organizationId.value });
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
  await loadRegPolicy();
  await loadStopPolicy();
});
</script>

<template>
  <Page auto-content-height>
    <div class="booking-policy-page">
      <p class="booking-policy-page__intro">
        挂号与停诊策略按科室配置，与「排班设置」中的出诊模板相互独立。
      </p>
      <ElTabs v-model="activeTab">
        <ElTabPane label="挂号策略" name="registration">
          <div v-loading="regLoading" class="booking-policy-page__form">
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
          <div v-loading="stopLoading" class="booking-policy-page__form">
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
    </div>
  </Page>
</template>

<style scoped>
.booking-policy-page {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.booking-policy-page__intro {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.booking-policy-page__form {
  max-width: 720px;
  padding: 12px 0;
}

.booking-policy-page__hint {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>
