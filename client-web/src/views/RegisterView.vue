<template>
  <div class="min-h-screen bg-[var(--im-bg)] flex items-center justify-center px-4">
    <div class="w-full max-w-sm">
      <div class="text-center mb-8">
        <div class="w-16 h-16 rounded-2xl bg-[var(--im-green)] text-white flex items-center justify-center text-3xl font-bold mx-auto mb-4">W</div>
        <h1 class="text-xl font-semibold">{{ $t('auth.register') }}</h1>
      </div>
      <form @submit.prevent="handleRegister" class="space-y-4">
        <input v-model="username" type="text" :placeholder="$t('auth.username')" required class="im-input" />
        <input v-model="password" type="password" :placeholder="$t('auth.password')" required class="im-input" />
        <input v-model="confirmPassword" type="password" :placeholder="$t('auth.confirmPassword')" required class="im-input" />
        <div v-if="error" class="text-red-500 text-xs text-center">{{ error }}</div>
        <button type="submit" :disabled="loading" class="im-btn-primary w-full py-2.5">
          {{ loading ? $t('auth.creating') : $t('auth.register') }}
        </button>
      </form>
      <p class="text-center mt-4 text-xs text-[var(--im-text-secondary)]">
        {{ $t('auth.hasAccount') }}
        <router-link to="/login" class="text-[var(--im-green)]">{{ $t('auth.signInInstead') }}</router-link>
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const username = ref('')
const password = ref('')
const confirmPassword = ref('')
const error = ref('')
const loading = ref(false)

async function handleRegister() {
  error.value = ''
  if (password.value !== confirmPassword.value) { error.value = 'Passwords do not match'; return }
  loading.value = true
  try {
    const res = await auth.register(username.value, password.value)
    if (res.code === 0) router.push('/')
    else error.value = res.msg || 'Registration failed'
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : 'Network error'
  } finally { loading.value = false }
}
</script>
