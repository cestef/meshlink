package com.cstef.meshlink.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.cstef.meshlink.BuildConfig
import nl.dionsegijn.konfetti.compose.KonfettiView
import nl.dionsegijn.konfetti.compose.OnParticleSystemUpdateListener
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.PartySystem
import nl.dionsegijn.konfetti.core.emitter.Emitter
import java.util.concurrent.TimeUnit

@Composable
fun AboutScreen() {
  val (isConfetti, setConfetti) = remember { mutableStateOf(false) }
  val colors = MaterialTheme.colorScheme
  val party = remember {
    Party(
      emitter = Emitter(duration = 100, TimeUnit.MILLISECONDS).max(100),
      colors = listOf(
        colors.primary,
        colors.secondary,
        colors.tertiary,
        colors.onPrimary,
        colors.onSecondary,
        colors.onTertiary,
      ).map {
        it.toArgb()
      },
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
      },
    )
  }
  Column(modifier = Modifier.fillMaxSize()) {
    Spacer(modifier = Modifier.padding(top = 64.dp))
    Text(
      text = "MeshLink",
      style = MaterialTheme.typography.titleLarge,
      modifier = Modifier
        .padding(16.dp)
        .align(Alignment.CenterHorizontally),
      color = MaterialTheme.colorScheme.onBackground,
    )
    Text(
      text = "Version ${BuildConfig.VERSION_NAME}",
      style = MaterialTheme.typography.bodyLarge,
      modifier = Modifier
        .padding(8.dp)
        .align(Alignment.CenterHorizontally)
        .clickable { setConfetti(true) },
      color = MaterialTheme.colorScheme.onBackground,
    )
    Text(
      text = "Made with ❤️ by cstef",
      style = MaterialTheme.typography.bodyLarge,
      modifier = Modifier
        .padding(8.dp)
        .align(Alignment.CenterHorizontally),
      color = MaterialTheme.colorScheme.onBackground,
    )
    val annotatedString = buildAnnotatedString {
      withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.onBackground)) {
        append("Source code available on ")
      }
      pushStringAnnotation(tag = "github", annotation = "https://github.com/cestef/meshlink")
      withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary)) {
        append("GitHub")
      }
      pop()
    }
    val uriHandler = LocalUriHandler.current
    ClickableText(
      modifier = Modifier
        .padding(8.dp)
        .align(Alignment.CenterHorizontally),
      text = annotatedString,
      style = MaterialTheme.typography.bodyLarge,
      onClick = { offset ->
        annotatedString.getStringAnnotations(tag = "github", start = offset, end = offset)
          .firstOrNull()?.let {
            uriHandler.openUri(it.item)
          }
      })
  }
}
