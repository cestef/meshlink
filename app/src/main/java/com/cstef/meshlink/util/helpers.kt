package com.cstef.meshlink.util

// Generate a random english adjective-noun pair with 3 random digits separated by either a dash, a dot
// 977*2*1844*10^3 possibilities
fun generateFriendlyId(): String {
  val adjective = adjectives.random()
  val noun = nouns.random()
  val random = (0 until 10).random().toString() + (0 until 10).random().toString() + (0 until 10).random().toString()
  val separator = listOf(".", "-").random()

  return "$adjective$separator$noun$separator$random"
}

