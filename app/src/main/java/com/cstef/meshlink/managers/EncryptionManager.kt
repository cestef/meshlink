package com.cstef.meshlink.managers

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.math.BigInteger
import java.security.*
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

class EncryptionManager {
  companion object {
    const val KEY_ALIAS = "com.cstef.meshlink"
    const val RSA_KEY_SIZE = 2048
  }

  private var keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply {
    load(null)
  }
  var publicKey: PublicKey

  init {
    if (!hasAsymmetricKeyPair()) createAsymmetricKeyPair()
    val keyPair = getAsymmetricKeyPair()
    if (keyPair != null) publicKey = keyPair.public
    else throw Exception("Key pair is null")
  }


  private fun createAsymmetricKeyPair(): KeyPair {
    val generator: KeyPairGenerator

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
      val builder = KeyGenParameterSpec.Builder(
        KEY_ALIAS,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
      )
        .setBlockModes(KeyProperties.BLOCK_MODE_ECB)
        //.setUserAuthenticationRequired(true)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)

      generator.initialize(builder.build())
    } else {
      generator = KeyPairGenerator.getInstance("RSA")
      generator.initialize(RSA_KEY_SIZE)
    }

    return generator.generateKeyPair()
  }

  private fun getAsymmetricKeyPair(): KeyPair? {

    val privateKey = keyStore.getKey(KEY_ALIAS, null) as PrivateKey?
    val publicKey = keyStore.getCertificate(KEY_ALIAS)?.publicKey

    return if (privateKey != null && publicKey != null) {
      KeyPair(publicKey, privateKey)
    } else {
      null
    }
  }

  private fun hasAsymmetricKeyPair(): Boolean {
    val keyStore = KeyStore.getInstance("AndroidKeyStore")
    keyStore.load(null)

    return keyStore.containsAlias(KEY_ALIAS)
  }

  fun encrypt(data: String, publicKey: Key?): String {
    val generator: KeyGenerator = KeyGenerator.getInstance("AES")
    generator.init(128)
    val secKey: SecretKey = generator.generateKey()
    val aesCipher = Cipher.getInstance("AES")
    aesCipher.init(Cipher.ENCRYPT_MODE, secKey)

    val rsaCipher: Cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
    rsaCipher.init(Cipher.ENCRYPT_MODE, publicKey)

    val bytes = aesCipher.doFinal(data.toByteArray())

    val encryptedKey = rsaCipher.doFinal(secKey.encoded)

    return Base64.encodeToString(encryptedKey, Base64.DEFAULT) + ":" + Base64.encodeToString(
      bytes,
      Base64.DEFAULT
    )
  }

  fun decrypt(data: String): String {
    getAsymmetricKeyPair()?.let {
      val parts = data.split(":")
      if (parts.size != 2) return data // The data is not encrypted (or not encrypted with this app)
      val encryptedKey = Base64.decode(parts[0], Base64.DEFAULT)
      val encryptedData = Base64.decode(parts[1], Base64.DEFAULT)

      val rsaCipher: Cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
      rsaCipher.init(Cipher.DECRYPT_MODE, it.private)

      val decryptedKey = rsaCipher.doFinal(encryptedKey)

      val aesCipher = Cipher.getInstance("AES")
      aesCipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(decryptedKey, "AES"))

      return String(aesCipher.doFinal(encryptedData))
    }
    return "Failed to decrypt"
  }

  fun sign(data: String): String {
    getAsymmetricKeyPair()?.let {
      val signature = Signature.getInstance("SHA256withRSA")
      signature.initSign(it.private)
      signature.update(data.toByteArray())
      return Base64.encodeToString(signature.sign(), Base64.DEFAULT)
    }
    throw Exception("Key pair is null")
  }

  fun verify(data: String, signature: String, publicKey: PublicKey?): Boolean {
    val signatureInstance = Signature.getInstance("SHA256withRSA")
    signatureInstance.initVerify(publicKey)
    signatureInstance.update(data.toByteArray())
    return signatureInstance.verify(Base64.decode(signature, Base64.DEFAULT))
  }


  fun md5(input: ByteArray): String {
    val md = MessageDigest.getInstance("MD5")
    return BigInteger(1, md.digest(input)).toString(16).padStart(32, '0')
  }
}
