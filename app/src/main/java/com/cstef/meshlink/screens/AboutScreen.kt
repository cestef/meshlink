package com.cstef.meshlink.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import nl.dionsegijn.konfetti.compose.KonfettiView
import nl.dionsegijn.konfetti.compose.OnParticleSystemUpdateListener
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.PartySystem
import nl.dionsegijn.konfetti.core.emitter.Emitter
import java.util.concurrent.TimeUnit

@Composable
fun AboutScreen() {
  val (isConfetti, setConfetti) = remember { mutableStateOf(false) }
  val party = remember {
    Party(
      emitter = Emitter(duration = 100, TimeUnit.MILLISECONDS).max(100),
    )
  }
  if (isConfetti) {
    KonfettiView(
      parties = listOf(party),
      modifier = Modifier.fillMaxSize(),
      updateListener = object : OnParticleSystemUpdateListener {
        override fun onParticleSystemEnded(system: PartySystem, activeSystems: Int) {
          if (activeSystems == 0) {
            setConfetti(false)
          }
        }
      }
    )
  }
  Box(modifier = Modifier.fillMaxSize()) {
    Button(onClick = {
      setConfetti(true)
    }) {
      Text(text = "Click me!")
    }
  }
}
