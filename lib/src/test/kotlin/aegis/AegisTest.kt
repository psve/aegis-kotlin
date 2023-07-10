@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package aegis

import kotlin.test.Test
import kotlin.test.*
import kotlin.time.measureTime
import kotlin.time.DurationUnit.MILLISECONDS

fun String.hexToArray(): UByteArray {
  check(length % 2 == 0) { "Must have an even length" }

  return UByteArray(length / 2) {
    Integer.parseInt(this, it * 2, (it + 1) * 2, 16).toUByte()
  }
}

class AegisTest {
  @Test fun sanity() {
    val key = "000102030405060708090a0b0c0d0e0f".hexToArray();
    val nonce = "101112131415161718191a1b1c1d1e1f".hexToArray();
    val data = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20212223242526272829".hexToArray();
    val plaintext = "101112131415161718191a1b1c1d1e1f202122232425262728292a2b2c2d2e2f3031323334353637".hexToArray();
    val msg = plaintext.copyOf();

    val c = Aegis128L()

    val tagEncrypt = c.seal(key, nonce, msg, data);
    assertFalse(msg contentEquals plaintext)

    try {
      c.open(key, nonce, msg, data, tagEncrypt);
    } catch (e: Exception) {
      fail("open failed", e)
    }

    assertTrue(msg contentEquals plaintext)
  }

  @Test fun invalidTag() {
    val key = "000102030405060708090a0b0c0d0e0f".hexToArray();
    val nonce = "101112131415161718191a1b1c1d1e1f".hexToArray();
    val data = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20212223242526272829".hexToArray();
    val plaintext = "101112131415161718191a1b1c1d1e1f202122232425262728292a2b2c2d2e2f3031323334353637".hexToArray();
    val msg = plaintext.copyOf();

    val c = Aegis128L()

    val tagEncrypt = c.seal(key, nonce, msg, data);
    tagEncrypt[0] = tagEncrypt[0] xor 1u;

    assertFails({ c.open(key, nonce, msg, data, tagEncrypt) })
    assertTrue(msg.all { it == 0u.toUByte()} )
  }

  @Test fun knownAnswers() {
    val key = arrayOf(
      "10010000000000000000000000000000".hexToArray(),
      "10010000000000000000000000000000".hexToArray(),
      "10010000000000000000000000000000".hexToArray(),
      "10010000000000000000000000000000".hexToArray(),
      "10010000000000000000000000000000".hexToArray(),
    )
    val nonce = arrayOf(
      "10000200000000000000000000000000".hexToArray(),
      "10000200000000000000000000000000".hexToArray(),
      "10000200000000000000000000000000".hexToArray(),
      "10000200000000000000000000000000".hexToArray(),
      "10000200000000000000000000000000".hexToArray(),
    )
    val data = arrayOf(
      "".hexToArray(),
      "".hexToArray(),
      "0001020304050607".hexToArray(),
      "0001020304050607".hexToArray(),
      "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20212223242526272829".hexToArray(),
    )
    val msg = arrayOf(
      "00000000000000000000000000000000".hexToArray(),
      "".hexToArray(),
      "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f".hexToArray(),
      "000102030405060708090a0b0c0d".hexToArray(),
      "101112131415161718191a1b1c1d1e1f202122232425262728292a2b2c2d2e2f3031323334353637".hexToArray(),
    )
    val ct = arrayOf(
      "c1c0e58bd913006feba00f4b3cc3594e".hexToArray(),
      "".hexToArray(),
      "79d94593d8c2119d7e8fd9b8fc77845c5c077a05b2528b6ac54b563aed8efe84".hexToArray(),
      "79d94593d8c2119d7e8fd9b8fc77".hexToArray(),
      "b31052ad1cca4e291abcf2df3502e6bdb1bfd6db36798be3607b1f94d34478aa7ede7f7a990fec10".hexToArray(),
    )
    val tag = arrayOf(
      "abe0ece80c24868a226a35d16bdae37a".hexToArray(),
      "c2b879a67def9d74e6c14f708bbcc9b4".hexToArray(),
      "cc6f3372f6aa1bb82388d695c3962d9a".hexToArray(),
      "5c04b3dba849b2701effbe32c7f0fab7".hexToArray(),
      "7542a745733014f9474417b337399507".hexToArray(),
    )

    val c = Aegis128L()
    for (i in 0 until key.size) {
      val tagEnc = c.seal(key[i], nonce[i], msg[i], data[i])
      assertTrue(msg[i] contentEquals ct[i], "($i) wrong ciphertext: ${msg[i].contentToString()} != ${ct[i].contentToString()}")
      assertTrue(tag[i] contentEquals tagEnc, "($i) wrong tag: ${tag[i].contentToString()} != ${tagEnc.contentToString()}")
    }
  }

  @OptIn(kotlin.time.ExperimentalTime::class)
  @Test fun benchmarkEncrypt() {
    val key = "10010000000000000000000000000000".hexToArray();
    val nonce = "10000200000000000000000000000000".hexToArray();
    val data = UByteArray(0){ 0u };
    val msg = UByteArray(102400){ 0u };
    val n = 1000;
    val c = Aegis128L()

    val d = measureTime({
      for (i in 0 until n) {
        c.seal(key, nonce, msg, data);
      }
    }).toDouble(MILLISECONDS)

    println("Aegis encrypt: Total time: ${d} ms")
    println("Aegis encrypt: Time per op: ${d/n} ms")
    println("Aegis encrypt: Bytes per second: ${(n*msg.size)/(d/n)}")
    println("Aegis encrypt: Nanoseconds per byte: ${(d*1000000)/(n*msg.size)}")
  }

  @OptIn(kotlin.time.ExperimentalTime::class)
  @Test fun benchmarkDecrypt() {
    val key = "10010000000000000000000000000000".hexToArray();
    val nonce = "10000200000000000000000000000000".hexToArray();
    val data = UByteArray(0){ 0u };
    val msg = UByteArray(102400){ 0u };
    val n = 1000;
    val c = Aegis128L()
    val tag = c.seal(key, nonce, msg, data);

    val d = measureTime({
      for (i in 0 until n) {
        val ciphertext = msg.copyOf()
        c.open(key, nonce, ciphertext, data, tag);
      }
    }).toDouble(MILLISECONDS)

    println("Aegis decrypt: Total time: ${d} ms")
    println("Aegis decrypt: Time per op: ${d/n} ms")
    println("Aegis decrypt: Bytes per second: ${(n*msg.size)/(d/n)}")
    println("Aegis decrypt: Nanoseconds per byte: ${(d*1000000)/(n*msg.size)}")
  }
}
