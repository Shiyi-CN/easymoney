package com.jiyixia.app.util

import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM 加密工具
 * 用于备份文件加密/解密
 */
object CryptoUtil {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val KEY_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val KEY_LENGTH = 256
    private const val ITERATION_COUNT = 100_000
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH = 128

    /**
     * 使用密码加密数据流
     * @param input 输入流（原始数据）
     * @param output 输出流（加密后的数据）
     * @param password 用户密码
     * @return 是否成功
     */
    fun encrypt(input: InputStream, output: OutputStream, password: String): Boolean {
        return try {
            // 生成随机盐和 IV
            val salt = ByteArray(SALT_LENGTH).apply { SecureRandom().nextBytes(this) }
            val iv = ByteArray(IV_LENGTH).apply { SecureRandom().nextBytes(this) }

            // 从密码派生密钥
            val key = deriveKey(password, salt)

            // 初始化加密器
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH, iv))

            // 写入盐和 IV（恢复时需要）
            output.write(salt)
            output.write(iv)

            // 加密数据
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                val encrypted = cipher.update(buffer, 0, bytesRead)
                if (encrypted != null) output.write(encrypted)
            }
            val finalBytes = cipher.doFinal()
            if (finalBytes != null) output.write(finalBytes)

            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 使用密码解密数据流
     * @param input 输入流（加密数据）
     * @param output 输出流（解密后的数据）
     * @param password 用户密码
     * @return 是否成功（密码错误也会返回 false）
     */
    fun decrypt(input: InputStream, output: OutputStream, password: String): Boolean {
        return try {
            // 读取盐和 IV
            val salt = ByteArray(SALT_LENGTH)
            val iv = ByteArray(IV_LENGTH)
            if (input.read(salt) != SALT_LENGTH) return false
            if (input.read(iv) != IV_LENGTH) return false

            // 从密码派生密钥
            val key = deriveKey(password, salt)

            // 初始化解密器
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH, iv))

            // 解密数据
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                val decrypted = cipher.update(buffer, 0, bytesRead)
                if (decrypted != null) output.write(decrypted)
            }
            val finalBytes = cipher.doFinal()
            if (finalBytes != null) output.write(finalBytes)

            true
        } catch (e: Exception) {
            // 密码错误或数据损坏
            false
        }
    }

    /**
     * 从密码派生密钥
     */
    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance(KEY_ALGORITHM)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }
}
