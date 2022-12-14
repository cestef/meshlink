package com.cstef.meshlink.screens

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordScreen(
  firstTime: Boolean,
  isDatabaseOpening: Boolean,
  openDatabase: (password: String) -> Unit,
) {
  val context = LocalContext.current
  if (isDatabaseOpening) {
    AlertDialog(onDismissRequest = {},
      title = { Text(text = "Opening database...") },
      confirmButton = {})
  } else {
    // Prompt the user to enter the master database password
    val (masterPassword, setMasterPassword) = remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = {},
      title = {
        Text(text = if (firstTime) "Set Master Password" else "Enter Master Password")
      },
      text = {
        var passwordVisible by rememberSaveable { mutableStateOf(false) }
        Column {
          if (firstTime) {
            Text(
              text = "This password will be used to encrypt your database.",
              modifier = Modifier.padding(bottom = 16.dp)
            )
          }
          OutlinedTextField(
            value = masterPassword,
            onValueChange = { setMasterPassword(it) },
            label = { Text(text = "Master password") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
              val image = if (passwordVisible)
                Icons.Filled.Visibility
              else Icons.Filled.VisibilityOff
              val description = if (passwordVisible) "Hide password" else "Show password"
              IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(imageVector = image, description)
              }
            }
          )
          if (firstTime) {
            Text(
              text = "You can change it later in your user profile.",
              modifier = Modifier.padding(top = 16.dp)
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (firstTime) {
              val sharedPreferences =
                context.getSharedPreferences("USER_SETTINGS", Context.MODE_PRIVATE)
              if (masterPassword.isEmpty()) {
                sharedPreferences.edit().putBoolean("is_default_password", true).apply()
              } else {
                sharedPreferences.edit().putBoolean("is_default_password", false).apply()
              }
            }
            openDatabase(masterPassword)
          },
          content = { Text(text = "Confirm") }
        )
      }
    )
  }
}
