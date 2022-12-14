package com.cstef.meshlink.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.cstef.meshlink.util.struct.BeamAvatarData
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow

// Generate a random english adjective-noun pair with 3 random digits separated by either a dash, a dot
// 977*2*1844*10^3 possibilities
fun generateFriendlyId(): String {
  val adjective = adjectives.random()
  val noun = nouns.random()
  val random =
    (0 until 10).random().toString() + (0 until 10).random().toString() + (0 until 10).random()
      .toString()
  val separator = listOf(".", "-").random()

  return "$adjective$separator$noun$separator$random"
}

fun hashCode(name: String): Int {
  var hash = 0
  for (i in 0 until name.length) {
    val character = name[i].code
    hash = ((hash shl 5) - hash) + character
    hash = hash and hash
  }
  return abs(hash)
}

fun getDigit(number: Int, ntn: Int): Int {
  return floor(number / (10.0.pow(ntn - 1)) % 10).toInt()
}

fun getBoolean(number: Int, ntn: Int): Boolean {
  return (getDigit(number, ntn)) % 2 != 0
}

fun getUnit(number: Int, range: Int, index: Int = 0): Int {
  val value = number % range

  return if (index != 0 && ((getDigit(number, index) % 2) == 0)) {
    -value
  } else value
}

fun getRandomColor(number: Int, colors: List<String>, range: Int): String {
  return colors[number % range]
}

fun getContrast(color: String): String {
  val r = color.substring(1, 3).toInt(16)
  val g = color.substring(3, 5).toInt(16)
  val b = color.substring(5, 7).toInt(16)
  val yiq = (r * 299 + g * 587 + b * 114) / 1000
  return if (yiq >= 128) "#000000" else "#FFFFFF"
}

fun generateBeamAvatarData(name: String, colors: List<String>): BeamAvatarData {
  val numFromName = hashCode(name)
  val range = colors.size
  val wrapperColor = getRandomColor(numFromName, colors, range)
  val preTranslateX = getUnit(numFromName, 10, 1)
  val wrapperTranslateX = if (preTranslateX < 5) preTranslateX + AVATAR_SIZE / 9 else preTranslateX
  val preTranslateY = getUnit(numFromName, 10, 2)
  val wrapperTranslateY = if (preTranslateY < 5) preTranslateY + AVATAR_SIZE / 9 else preTranslateY

  return BeamAvatarData(
    wrapperColor = wrapperColor,
    faceColor = getContrast(wrapperColor),
    backgroundColor = getRandomColor(numFromName + 13, colors, range),
    wrapperTranslateX = wrapperTranslateX,
    wrapperTranslateY = wrapperTranslateY,
    wrapperRotate = getUnit(numFromName, 360),
    wrapperScale = 1 + getUnit(numFromName, AVATAR_SIZE / 12) / 10,
    isMouthOpen = getBoolean(numFromName, 2),
    isCircle = getBoolean(numFromName, 1),
    eyeSpread = getUnit(numFromName, 5),
    mouthSpread = getUnit(numFromName, 3),
    faceRotate = getUnit(numFromName, 10, 3),
    faceTranslateX = if (wrapperTranslateX > AVATAR_SIZE / 6) wrapperTranslateX / 2 else getUnit(
      numFromName, 8, 1
    ),
    faceTranslateY = if (wrapperTranslateY > AVATAR_SIZE / 6) wrapperTranslateY / 2 else getUnit(
      numFromName, 7, 2
    ),
  )
}

fun generateBeamSVG(
  name: String, colors: List<String>, rect: Boolean = false, size: RectF = RectF(0f, 0f, 128f, 128f)
): String {
  val data = generateBeamAvatarData(name, colors)
  return """
    <svg
      viewBox="0 0 $AVATAR_SIZE $AVATAR_SIZE"
      fill="none"
      role="img"
      xmlns="http://www.w3.org/2000/svg"
      width="${size.width()}"
      height="${size.height()}"
    >
      <mask id="mask__beam" maskUnits="userSpaceOnUse" x="0" y="0" width="$AVATAR_SIZE" height="$AVATAR_SIZE">
        <rect width="$AVATAR_SIZE" height="$AVATAR_SIZE" rx="${if (rect) "" else AVATAR_SIZE * 2}" fill="#FFFFFF" />
      </mask>
      <g mask="url(#mask__beam)">
        <rect width="$AVATAR_SIZE" height="$AVATAR_SIZE" fill="${data.backgroundColor}" />
        <rect
          x="0"
          y="0"
          width="$AVATAR_SIZE"
          height="$AVATAR_SIZE"
          transform="translate(${data.wrapperTranslateX} ${data.wrapperTranslateY}) rotate(${data.wrapperRotate} ${AVATAR_SIZE / 2} ${AVATAR_SIZE / 2}) scale(${data.wrapperScale})"
          fill="${data.wrapperColor}"
          rx="${if (data.isCircle) AVATAR_SIZE else AVATAR_SIZE / 6}"
        />
        <g
          transform="translate(${data.faceTranslateX} ${data.faceTranslateY}) rotate(${data.faceRotate} ${AVATAR_SIZE / 2} ${AVATAR_SIZE / 2})"
        >
          ${
    if (data.isMouthOpen) """
            <path
              d="M15 ${19 + data.mouthSpread}c2 1 4 1 6 0"
              stroke="${data.faceColor}"
              fill="none"
              strokeLinecap="round"
            />
          """ else """
            <path
              d="M13,${19 + data.mouthSpread} a1,0.75 0 0,0 10,0"
              fill="${data.faceColor}"
            />
          """
  }
          <rect
            x="${14 - data.eyeSpread}"
            y="14"
            width="1.5"
            height="2"
            rx="1"
            stroke="none"
            fill="${data.faceColor}"
          />
          <rect
            x="${20 + data.eyeSpread}"
            y="14"
            width="1.5"
            height="2"
            rx="1"
            stroke="none"
            fill="${data.faceColor}"
          />
        </g>
      </g>
    </svg>
  """
}
