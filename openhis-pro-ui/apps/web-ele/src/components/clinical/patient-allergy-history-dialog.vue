<script setup lang="ts">
import { ref } from 'vue';

import { ElButton } from 'element-plus';

import AppDialog from '#/components/app-dialog.vue';
import PatientAllergyHistoryPanel from '#/components/clinical/patient-allergy-history-panel.vue';

defineOptions({ name: 'PatientAllergyHistoryDialog' });

interface Props {
  encounterId?: string;
  patientId?: string;
  defaultVerificationStatus?: string;
  readOnly?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  encounterId: '',
  patientId: '',
  defaultVerificationStatus: 'CONFIRMED',
  readOnly: false,
});

const visible = defineModel<boolean>({ default: false });

const panelRef = ref<{ persist: (silent?: boolean) => Promise<void> } | null>(null);

/** 保存过敏史文本后关窗 */
async function handleSave() {
  await panelRef.value?.persist();
  visible.value = false;
}

/** 只读时直接关窗 */
function handleClose() {
  visible.value = false;
}
</script>

<template>
  <AppDialog v-model="visible" title="过敏史" width="480px" destroy-on-close>
    <PatientAllergyHistoryPanel
      v-if="visible"
      ref="panelRef"
      :patient-id="props.patientId"
      :encounter-id="props.encounterId"
      :default-verification-status="props.defaultVerificationStatus"
      :read-only="props.readOnly"
    />
    <template #footer>
      <ElButton v-if="props.readOnly" @click="handleClose">关闭</ElButton>
      <template v-else>
        <ElButton @click="handleClose">取消</ElButton>
        <ElButton type="primary" @click="handleSave">保存</ElButton>
      </template>
    </template>
  </AppDialog>
</template>
