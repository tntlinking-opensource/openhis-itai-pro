<script setup lang="ts">
import { computed } from 'vue';

import { ProfileSecuritySetting } from '@vben/common-ui';
import { useUserStore } from '@vben/stores';

defineOptions({ name: 'ProfileSecuritySettingPage' });

const userStore = useUserStore();

/** 手机号脱敏展示 */
function maskPhone(phone?: null | string): string {
  const value = String(phone ?? '').trim();
  if (!value) {
    return '未绑定手机';
  }
  if (value.length < 7) {
    return value;
  }
  return `${value.slice(0, 3)}****${value.slice(-4)}`;
}

/** 邮箱脱敏展示 */
function maskEmail(email?: null | string): string {
  const value = String(email ?? '').trim();
  if (!value || !value.includes('@')) {
    return value ? value : '未绑定邮箱';
  }
  const [name, domain] = value.split('@');
  const visible = name.slice(0, Math.min(3, name.length));
  return `${visible}***@${domain}`;
}

const formSchema = computed(() => {
  const info = userStore.userInfo;
  const phone = info?.phoneNumber;
  const email = info?.email;
  return [
    {
      value: true,
      fieldName: 'accountPassword',
      label: '账户密码',
      description: '定期修改密码可有效保护账户安全',
    },
    {
      value: Boolean(phone),
      fieldName: 'securityPhone',
      label: '密保手机',
      description: phone ? `已绑定手机：${maskPhone(phone)}` : '未绑定手机',
    },
    {
      value: false,
      fieldName: 'securityQuestion',
      label: '密保问题',
      description: '未设置密保问题，密保问题可有效保护账户安全',
    },
    {
      value: Boolean(email),
      fieldName: 'securityEmail',
      label: '备用邮箱',
      description: email ? `已绑定邮箱：${maskEmail(email)}` : '未绑定邮箱',
    },
    {
      value: false,
      fieldName: 'securityMfa',
      label: 'MFA 设备',
      description: '未绑定 MFA 设备，绑定后，可以进行二次确认',
    },
  ];
});
</script>
<template>
  <ProfileSecuritySetting :form-schema="formSchema" />
</template>
