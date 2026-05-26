import { createI18n } from 'vue-i18n'
import en from '../i18n/locales/en'
import zh from '../i18n/locales/zh'

const i18n = createI18n({
  legacy: false,
  locale: localStorage.getItem('im_locale') || 'zh',
  fallbackLocale: 'en',
  messages: { en, zh },
})

export default i18n
