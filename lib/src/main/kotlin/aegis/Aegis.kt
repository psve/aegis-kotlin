@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package aegis

const val KEYSIZE = 16
const val NONCESIZE = 16

private val CONST0: UIntArray = uintArrayOf(0x02010100u, 0xd080503u, 0x59372215u, 0x6279e990u)
private val CONST1: UIntArray = uintArrayOf(0x55183ddbu, 0xf12fc26du, 0x42311120u, 0xdd28b573u)
private var t = Array<UIntArray>(4) { UIntArray(256) { 0u } }

private fun init() {
  if (t[0][0] != 0u) {
    return
  }

  var d = Array(256) { _ -> 0u}
  var th = Array(256) { _ -> 0u}

  for (i in 0u until 256u) {
    val v = (i shl 1) xor (i shr 7)*283u
    d[i.toInt()] = v
    th[(v xor i).toInt()] = i
  }

  var x: UInt = 0u
  var xInv: UInt = 0u

  for (j in 0 until 256) {
    var s = xInv xor (xInv shl 1) xor (xInv shl 2) xor (xInv shl 3) xor (xInv shl 4)
    s = (s shr 8) xor (s and 255u) xor 99u
    val x2 = d[x.toInt()]
    var tEnc = (d[s.toInt()]*0x101u) xor (s*0x1010100u)

    for (i in 0 until 4) {
      tEnc = (tEnc shl 24) xor (tEnc shr 8)
      t[i][x.toInt()] = tEnc
    }

    if (x2 == 0u) {
      x = x xor 1u
    } else {
      x = x xor x2
    }
    if (th[xInv.toInt()] == 0u) {
      xInv = 1u
    } else {
      xInv = th[xInv.toInt()]
    }
  }

  // Swap byte order
  for (i in 0 until 4) {
    for (j in 0 until 256) {
      val b = t[i][j]
      t[i][j] = ((b shl 24) and 0xff000000u) xor ((b shl 8) and 0x00ff0000u) xor ((b shr 8) and 0x0000ff00u) xor ((b shr 24) and 0x000000ffu)
    }
  }
}

private fun UIntArray.toUByteArray(): UByteArray {
  val output = UByteArray(4 * this.size){ 0u }

  for ((i, v) in this.withIndex()) {
    output[4*i+0] = v.toUByte()
    output[4*i+1] = (v shr 8).toUByte()
    output[4*i+2] = (v shr 16).toUByte()
    output[4*i+3] = (v shr 24).toUByte()
  }

  return output
}

private fun UByteArray.toUIntArray(): UIntArray {
  var size = this.size / 4
  if (this.size % 4 != 0) {
    size++
  }
  val output = UIntArray(size){ 0u }

  for ((i, v) in this.withIndex()) {
    output[i/4] = output[i/4] xor (v.toUInt() shl 8*(i%4))
  }

  return output
}

private fun xor128Into(value: UIntArray, dst: UIntArray, dstOffset: Int) {
  dst[dstOffset + 0] = dst[dstOffset + 0] xor value[0]
  dst[dstOffset + 1] = dst[dstOffset + 1] xor value[1]
  dst[dstOffset + 2] = dst[dstOffset + 2] xor value[2]
  dst[dstOffset + 3] = dst[dstOffset + 3] xor value[3]
}

private fun extract(a: UIntArray, b: UIntArray, c: UIntArray, d: UIntArray): UIntArray {
  return uintArrayOf(
    (a[0] and b[0]) xor c[0] xor d[0],
    (a[1] and b[1]) xor c[1] xor d[1],
    (a[2] and b[2]) xor c[2] xor d[2],
    (a[3] and b[3]) xor c[3] xor d[3],
  )
}

private fun zeroPad(data: UByteArray): UIntArray {
  if (data.size % 32 == 0) {
    return data.toUIntArray()
  }
  val padLen = 32 - data.size % 32
  return (data + UByteArray(padLen){ 0u }).toUIntArray()
}

private fun UIntArray.getByte(idx: Int): Int {
  return ((this[idx/4] shr 8*(idx%4)) and 0xffu).toInt()
}

private fun round(src: UIntArray, key: UIntArray, dst: UIntArray) {
  dst[0] = key[0] xor t[0][src.getByte(0)]  xor t[1][src.getByte(5)]  xor t[2][src.getByte(10)] xor t[3][src.getByte(15)]
  dst[1] = key[1] xor t[0][src.getByte(4)]  xor t[1][src.getByte(9)]  xor t[2][src.getByte(14)] xor t[3][src.getByte(3)]
  dst[2] = key[2] xor t[0][src.getByte(8)]  xor t[1][src.getByte(13)] xor t[2][src.getByte(2)]  xor t[3][src.getByte(7)]
  dst[3] = key[3] xor t[0][src.getByte(12)] xor t[1][src.getByte(1)]  xor t[2][src.getByte(6)]  xor t[3][src.getByte(11)]
}

private fun tagValid(a: UByteArray, b: UByteArray): Boolean {
  var equal = true
  for (i in 0 until a.size) {
    equal = equal and (a[i] == b[i])
  }
  return equal
}

class Aegis128L {
  private var s: Array<UIntArray> = Array<UIntArray>(8) { UIntArray(4) { 0u } }

  init {
    init()
  }

  private fun update(a: UIntArray, aOffset: Int, b: UIntArray, bOffset: Int) {
    val t = s[7].copyOf()

    round(this.s[6], this.s[7], this.s[7])
    round(this.s[5], this.s[6], this.s[6])
    round(this.s[4], this.s[5], this.s[5])

    this.s[4][0] = this.s[4][0] xor b[bOffset+0]
    this.s[4][1] = this.s[4][1] xor b[bOffset+1]
    this.s[4][2] = this.s[4][2] xor b[bOffset+2]
    this.s[4][3] = this.s[4][3] xor b[bOffset+3]

    round(this.s[3], this.s[4], this.s[4])
    round(this.s[2], this.s[3], this.s[3])
    round(this.s[1], this.s[2], this.s[2])
    round(this.s[0], this.s[1], this.s[1])

    this.s[0][0] = this.s[0][0] xor a[aOffset+0]
    this.s[0][1] = this.s[0][1] xor a[aOffset+1]
    this.s[0][2] = this.s[0][2] xor a[aOffset+2]
    this.s[0][3] = this.s[0][3] xor a[aOffset+3]

    round(t, this.s[0], this.s[0])
  }

  private fun process(key: UByteArray, nonce: UByteArray, msg: UByteArray, data: UByteArray, decrypt: Boolean): UByteArray {
    check(key.size == KEYSIZE) { "invalid key size" }
    check(nonce.size == NONCESIZE) { "invalid nonce size" }

    val msgLen = msg.size
    val dataLen = data.size

    val keyArray = key.toUIntArray()
    val nonceArray = nonce.toUIntArray()
    var msgArray = zeroPad(msg)
    val dataArray = zeroPad(data)

    // Initialize
    this.s = arrayOf(
      keyArray.copyOf(),
      CONST1.copyOf(),
      CONST0.copyOf(),
      CONST1.copyOf(),
      keyArray.copyOf(),
      keyArray.copyOf(),
      keyArray.copyOf(),
      keyArray.copyOf(),
    )

    xor128Into(nonceArray, this.s[0], 0)
    xor128Into(nonceArray, this.s[4], 0)
    xor128Into(CONST0, this.s[5], 0)
    xor128Into(CONST1, this.s[6], 0)
    xor128Into(CONST0, this.s[7], 0)

    for (i in 0 until 10) {
      update(nonceArray, 0, keyArray, 0)
    }

    // Process associated data
    val dataBlocks = dataArray.size/8
    for (i in 0 until dataBlocks) {
      update(dataArray, i*8, dataArray, i*8+4)
    }

    // Process plaintext
    val msgBlocks = msgArray.size/8
    for (i in 0 until msgBlocks) {
      val z0 = extract(this.s[2], this.s[3], this.s[1], this.s[6])
      val z1 = extract(this.s[6], this.s[7], this.s[5], this.s[2])

      if (!decrypt) {
        update(msgArray, i*8, msgArray, i*8+4)
      }

      xor128Into(z0, msgArray, i*8)
      xor128Into(z1, msgArray, i*8+4)

      if (decrypt) {
        // Recreate zero padding if needed
        if (msgLen < (i+1)*32) {
          // TODO: This is very inefficient
          var x = msgArray.toUByteArray()
          for (j in msgLen until x.size) {
            x[j] = 0u
          }
          msgArray = x.toUIntArray()
        }
        update(msgArray, i*8, msgArray, i*8+4)
      }
    }
    var x = msgArray.toUByteArray()
    for (i in 0 until msg.size) {
      msg[i] = x[i]
    }

    // Generate tag. Note that the encoding here restricts data and message length to
    // 2^32 instead of 2^64 as per the specification.
    var tag: UIntArray = uintArrayOf(dataLen.toUInt()*8u, 0u, msgLen.toUInt()*8u, 0u)
    xor128Into(this.s[2], tag, 0)
    for (i in 0 until 7) {
      update(tag, 0, tag, 0)
    }
    tag = this.s[0]
    xor128Into(this.s[1], tag, 0)
    xor128Into(this.s[2], tag, 0)
    xor128Into(this.s[3], tag, 0)
    xor128Into(this.s[4], tag, 0)
    xor128Into(this.s[5], tag, 0)
    xor128Into(this.s[6], tag, 0)
    return tag.toUByteArray()
  }

  fun seal(key: UByteArray, nonce: UByteArray, msg: UByteArray, data: UByteArray): UByteArray {
    return this.process(key, nonce, msg, data, false)
  }

  fun open(key: UByteArray, nonce: UByteArray, msg: UByteArray, data: UByteArray, tag: UByteArray) {
    val tagDecrypt = this.process(key, nonce, msg, data, true)
    if (!tagValid(tag, tagDecrypt)) {
      msg.fill(0u)
      throw Exception("invalid tag")
    }
  }
}
