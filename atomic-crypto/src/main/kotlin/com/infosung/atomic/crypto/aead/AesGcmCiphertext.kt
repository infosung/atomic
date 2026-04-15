package com.infosung.atomic.crypto.aead

data class AesGcmCiphertext(
    val iv: ByteArray,
    val ciphertext: ByteArray,
)
