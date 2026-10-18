import type { BasicUserInfo } from '@zxplat/types';

export interface ProfileUserInfo extends BasicUserInfo {
  name?: null | string;
  userName?: string;
  email?: string;
  phoneNumber?: null | string;
}

export interface Props {
  title?: string;
  userInfo: null | ProfileUserInfo;
  tabs: {
    label: string;
    value: string;
  }[];
}

export interface FormSchemaItem {
  description: string;
  fieldName: string;
  label: string;
  value: boolean;
}

export interface SettingProps {
  formSchema: FormSchemaItem[];
}
