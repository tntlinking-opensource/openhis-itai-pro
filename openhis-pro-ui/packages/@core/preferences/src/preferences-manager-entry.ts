import { preferencesManager } from './preferences';

const preferences = preferencesManager.getPreferences.apply(preferencesManager);
const updatePreferences = preferencesManager.updatePreferences.bind(preferencesManager);
const resetPreferences = preferencesManager.resetPreferences.bind(preferencesManager);
const clearPreferencesCache = preferencesManager.clearCache.bind(preferencesManager);
const initPreferences = preferencesManager.initPreferences.bind(preferencesManager);

export {
  clearPreferencesCache,
  initPreferences,
  preferences,
  preferencesManager,
  resetPreferences,
  updatePreferences,
};
