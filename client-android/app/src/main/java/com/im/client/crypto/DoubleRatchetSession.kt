package com.im.client.crypto

import android.util.Log
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.nio.ByteBuffer
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.util.ArrayDeque

/**
 * Simplified Double Ratchet Session for Android using X25519 + AES-256-GCM.
 * Mirrors the server-side DoubleRatchetSession logic.
 */
class DoubleRatchetSession private constructor() {

    companion object {
        private const val TAG = "DoubleRatchet"
        private const val CURVE = "X25519"
        private const val AES_GCM = "AES/GCM/NoPadding"
        private const val KEY_LEN = 32
        private const val MAX_SKIP = 1000

        fun generateKeyPair(): KeyPair {
            val kpg = KeyPairGenerator.getInstance(CURVE)
            return kpg.generateKeyPair()
        }
    }

    private var rootKey: ByteArray = ByteArray(0)
    private var sendChainKey: ByteArray = ByteArray(0)
    private var recvChainKey: ByteArray = ByteArray(0)
    private var sendCount = 0
    private var recvCount = 0
    private var prevSendCount = 0
    private var dhPair: KeyPair? = null
    private var remoteDhPub: PublicKey? = null

    data class EncryptResult(
        val ciphertext: ByteArray,
        val iv: ByteArray,
        val dhPubEncoded: ByteArray,
        val msgNum: Int,
        val prevChainLength: Int
    )

    fun initAsSender(
        identityKey: KeyPair, ephemeralKey: KeyPair,
        remoteIdentity: PublicKey, remoteSignedPrekey: PublicKey,
        remoteOtpk: ByteArray? = null
    ) {
        val dh1 = dh(identityKey.private, remoteSignedPrekey)
        val dh2 = dh(ephemeralKey.private, remoteIdentity)
        val dh3 = dh(ephemeralKey.private, remoteSignedPrekey)
        val dh4 = remoteOtpk?.let { dh(ephemeralKey.private, bytesToPubKey(it)) } ?: ByteArray(0)

        val sharedSecret = concat(dh1, dh2, dh3, dh4)
        rootKey = hkdf(ByteArray(0), sharedSecret, "ImE2EE_X3DH")
        dhPair = ephemeralKey
        remoteDhPub = remoteSignedPrekey

        val dhOut = dh(dhPair!!.private, remoteDhPub!!)
        val out = hkdf(rootKey, dhOut, "ImE2EE_RatchetStep")
        val buf = ByteBuffer.wrap(out)
        rootKey = ByteArray(KEY_LEN); sendChainKey = ByteArray(KEY_LEN)
        buf.get(rootKey); buf.get(sendChainKey)
        recvChainKey = ByteArray(0)
        sendCount = 0; recvCount = 0
    }

    fun encrypt(plaintext: ByteArray): EncryptResult {
        if (sendChainKey.isEmpty()) {
            prevSendCount = sendCount
            dhPair = generateKeyPair()
            val dhOut = dh(dhPair!!.private, remoteDhPub!!)
            val out = hkdf(rootKey, dhOut, "ImE2EE_RatchetStep")
            val buf = ByteBuffer.wrap(out)
            rootKey = ByteArray(KEY_LEN); sendChainKey = ByteArray(KEY_LEN)
            buf.get(rootKey); buf.get(sendChainKey)
            sendCount = 0
        }

        val msgKey = hmac(sendChainKey, intToBytes(0x01))
        sendChainKey = hmac(sendChainKey, intToBytes(0x02))

        val iv = ByteArray(12); SecureRandom().nextBytes(iv)
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(msgKey, "AES"), GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plaintext)
        sendCount++

        return EncryptResult(ciphertext, iv, dhPair!!.public.encoded, sendCount - 1, prevSendCount)
    }

    fun decrypt(ciphertext: ByteArray, iv: ByteArray, remoteDhPubEncoded: ByteArray, msgNum: Int, prevChainLength: Int): ByteArray {
        val currentRemoteEncoded = remoteDhPub!!.encoded
        if (!currentRemoteEncoded.contentEquals(remoteDhPubEncoded)) {
            // DH ratchet step
            if (prevChainLength > recvCount) skipMessages(prevChainLength)
            remoteDhPub = bytesToPubKey(remoteDhPubEncoded)
            val dhOut1 = dh(dhPair!!.private, remoteDhPub!!)
            val out1 = hkdf(rootKey, dhOut1, "ImE2EE_RatchetStep")
            val buf1 = ByteBuffer.wrap(out1)
            rootKey = ByteArray(KEY_LEN); recvChainKey = ByteArray(KEY_LEN)
            buf1.get(rootKey); buf1.get(recvChainKey)
            recvCount = 0

            dhPair = generateKeyPair()
            val dhOut2 = dh(dhPair!!.private, remoteDhPub!!)
            val out2 = hkdf(rootKey, dhOut2, "ImE2EE_RatchetStep")
            val buf2 = ByteBuffer.wrap(out2)
            prevSendCount = sendCount
            rootKey = ByteArray(KEY_LEN); sendChainKey = ByteArray(KEY_LEN)
            buf2.get(rootKey); buf2.get(sendChainKey)
            sendCount = 0
        }

        skipMessages(msgNum)
        val msgKey = hmac(recvChainKey, intToBytes(0x01))
        recvChainKey = hmac(recvChainKey, intToBytes(0x02))
        recvCount++

        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(msgKey, "AES"), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun skipMessages(until: Int) {
        if (until - recvCount > MAX_SKIP) throw SecurityException("too many skipped messages")
        while (recvCount < until) {
            recvChainKey = hmac(recvChainKey, intToBytes(0x02))
            recvCount++
        }
    }

    private fun dh(priv: PrivateKey, pub: PublicKey): ByteArray {
        val ka = KeyAgreement.getInstance("X25519")
        ka.init(priv); ka.doPhase(pub, true)
        return ka.generateSecret()
    }

    private fun hkdf(salt: ByteArray, input: ByteArray, info: String): ByteArray {
        val prk = if (salt.isNotEmpty()) salt else ByteArray(KEY_LEN)
        val t1 = hmac(prk, input)
        val okm1 = hmac(t1, concat(info.toByteArray(), byteArrayOf(0x01)))
        val okm2 = hmac(t1, concat(okm1, info.toByteArray(), byteArrayOf(0x02)))
        val buf = ByteBuffer.wrap(concat(okm1, okm2))
        val k1 = ByteArray(KEY_LEN); val k2 = ByteArray(KEY_LEN)
        buf.get(k1); buf.get(k2)
        return concat(k1, k2)
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun bytesToPubKey(encoded: ByteArray): PublicKey {
        val kf = KeyFactory.getInstance("X25519")
        return kf.generatePublic(X509EncodedKeySpec(encoded))
    }

    private fun intToBytes(v: Int): ByteArray = ByteBuffer.allocate(4).putInt(v).array()
    private fun concat(vararg arrays: ByteArray): ByteArray {
        val len = arrays.sumOf { it.size }
        val buf = ByteBuffer.allocate(len)
        arrays.forEach { buf.put(it) }
        return buf.array()
    }
}
