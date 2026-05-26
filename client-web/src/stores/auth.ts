import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as apiLogin, register as apiRegister } from '../services/api'
import { decodeJsonBody } from '../proto/im'
import { wsService } from '../services/websocket'
import { Cmd } from '../proto/constants'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('im_token') || '')
  const userId = ref(Number(localStorage.getItem('im_userId')) || 0)
  const username = ref(localStorage.getItem('im_username') || '')
  const nickname = ref(localStorage.getItem('im_nickname') || '')
  const isConnected = ref(false)

  const isLoggedIn = computed(() => !!token.value && userId.value > 0)

  async function login(user: string, password: string) {
    const res = await apiLogin(user, password)
    if (res.code === 0) {
      token.value = res.token
      userId.value = res.userId
      username.value = res.username
      nickname.value = res.nickname || res.username
      localStorage.setItem('im_token', res.token)
      localStorage.setItem('im_userId', String(res.userId))
      localStorage.setItem('im_username', res.username)
      localStorage.setItem('im_nickname', res.nickname || res.username)
      connectWs()
    }
    return res
  }

  async function register(user: string, password: string) {
    const res = await apiRegister(user, password)
    if (res.code === 0) {
      token.value = res.token
      userId.value = res.userId
      username.value = res.username
      nickname.value = res.nickname || res.username
      localStorage.setItem('im_token', res.token)
      localStorage.setItem('im_userId', String(res.userId))
      localStorage.setItem('im_username', res.username)
      localStorage.setItem('im_nickname', res.nickname || res.username)
      connectWs()
    }
    return res
  }

  function connectWs() {
    wsService.connect(token.value)

    wsService.on(Cmd.AUTH_ACK, (packet) => {
      const resp = decodeJsonBody<{ code: number }>(packet.body)
      isConnected.value = resp.code === 0
    })

    wsService.on(Cmd.HEARTBEAT_ACK, () => {
      isConnected.value = true
    })
  }

  function logout() {
    wsService.disconnect()
    token.value = ''
    userId.value = 0
    username.value = ''
    nickname.value = ''
    isConnected.value = false
    localStorage.removeItem('im_token')
    localStorage.removeItem('im_userId')
    localStorage.removeItem('im_username')
    localStorage.removeItem('im_nickname')
  }

  function tryReconnect() {
    if (token.value) connectWs()
  }

  return { token, userId, username, nickname, isLoggedIn, isConnected, login, register, logout, tryReconnect }
})
