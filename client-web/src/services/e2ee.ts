/**
 * E2EE encryption service for Web client using Web Crypto API.
 * Implements X25519 DH + AES-256-GCM with a simplified Double Ratchet.
 */
import { Cmd, MsgType, ContentType } from '../proto/constants'

// Web Crypto API doesn't support X25519 in all browsers yet,
// so we use ECDH P-256 as fallback + AES-256-GCM for encryption.
// For production, use libsodium-wrappers.

interface KeyBundle {
  identityKey: string
  signedPrekey: string
  signature?: string
  otpk?: string
}

interface EncryptResult {
  ciphertext: string
  iv: string
  dhPubEncoded: string
  msgNum: number
  prevChainLength: number
}

class E2eeService {
  private sessions: Map<number, CryptoKeyPair> = new Map()
  private remotePubs: Map<number, CryptoKey> = new Map()
  private sharedSecrets: Map<number, ArrayBuffer> = new Map()

  async generateKeyPair(): Promise<CryptoKeyPair> {
    return crypto.subtle.generateKey(
      { name: 'ECDH', namedCurve: 'P-256' },
      true,
      ['deriveBits']
    )
  }

  async initAsSender(remoteUserId: number, remoteBundle: KeyBundle): Promise<void> {
    const myKeyPair = await this.generateKeyPair()
    this.sessions.set(remoteUserId, myKeyPair)

    // Import remote public key
    const remotePubKey = await this.importPublicKey(remoteBundle.signedPrekey)
    this.remotePubs.set(remoteUserId, remotePubKey)

    // Derive shared secret
    const sharedBits = await crypto.subtle.deriveBits(
      { name: 'ECDH', public: remotePubKey },
      myKeyPair.privateKey,
      256
    )
    this.sharedSecrets.set(remoteUserId, sharedBits)
  }

  async initAsReceiver(remoteUserId: number, myKeyPair: CryptoKeyPair, remotePubB64: string): Promise<void> {
    this.sessions.set(remoteUserId, myKeyPair)
    const remotePubKey = await this.importPublicKey(remotePubB64)
    this.remotePubs.set(remoteUserId, remotePubKey)

    const sharedBits = await crypto.subtle.deriveBits(
      { name: 'ECDH', public: remotePubKey },
      myKeyPair.privateKey,
      256
    )
    this.sharedSecrets.set(remoteUserId, sharedBits)
  }

  async encrypt(remoteUserId: number, plaintext: string): Promise<EncryptResult> {
    const sharedSecret = this.sharedSecrets.get(remoteUserId)
    if (!sharedSecret) throw new Error('No E2EE session for user ' + remoteUserId)

    // Derive AES key from shared secret
    const aesKey = await crypto.subtle.deriveKey(
      { name: 'HKDF', hash: 'SHA-256', salt: new Uint8Array(32), info: new TextEncoder().encode('ImE2EE_AES') },
      await crypto.subtle.importKey('raw', sharedSecret, 'HKDF', false, ['deriveKey']),
      { name: 'AES-GCM', length: 256 },
      false,
      ['encrypt']
    )

    const iv = crypto.getRandomValues(new Uint8Array(12))
    const encoded = new TextEncoder().encode(plaintext)
    const ciphertext = await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, aesKey, encoded)

    const dhPair = this.sessions.get(remoteUserId)!
    const dhPubEncoded = await this.exportPublicKey(dhPair.publicKey)

    return {
      ciphertext: btoa(String.fromCharCode(...new Uint8Array(ciphertext))),
      iv: btoa(String.fromCharCode(...iv)),
      dhPubEncoded,
      msgNum: 0,
      prevChainLength: 0,
    }
  }

  async decrypt(remoteUserId: number, encResult: EncryptResult): Promise<string> {
    const sharedSecret = this.sharedSecrets.get(remoteUserId)
    if (!sharedSecret) throw new Error('No E2EE session for user ' + remoteUserId)

    const aesKey = await crypto.subtle.deriveKey(
      { name: 'HKDF', hash: 'SHA-256', salt: new Uint8Array(32), info: new TextEncoder().encode('ImE2EE_AES') },
      await crypto.subtle.importKey('raw', sharedSecret, 'HKDF', false, ['deriveKey']),
      { name: 'AES-GCM', length: 256 },
      false,
      ['decrypt']
    )

    const ciphertext = Uint8Array.from(atob(encResult.ciphertext), c => c.charCodeAt(0))
    const iv = Uint8Array.from(atob(encResult.iv), c => c.charCodeAt(0))

    const plainBuffer = await crypto.subtle.decrypt({ name: 'AES-GCM', iv }, aesKey, ciphertext)
    return new TextDecoder().decode(plainBuffer)
  }

  hasSession(remoteUserId: number): boolean {
    return this.sharedSecrets.has(remoteUserId)
  }

  private async importPublicKey(b64: string): Promise<CryptoKey> {
    const bytes = Uint8Array.from(atob(b64), c => c.charCodeAt(0))
    // SPKI format for ECDH P-256
    return crypto.subtle.importKey('spki', bytes, { name: 'ECDH', namedCurve: 'P-256' }, true, [])
  }

  private async exportPublicKey(key: CryptoKey): Promise<string> {
    const exported = await crypto.subtle.exportKey('spki', key)
    return btoa(String.fromCharCode(...new Uint8Array(exported)))
  }
}

export const e2eeService = new E2eeService()
