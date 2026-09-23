package com.example.imiq

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(
    onSignUpComplete: () -> Unit = {},
    viewModel: LoginViewModel = viewModel()
) {
    val s = LocalStrings.current
    var code by remember { mutableStateOf("") }
    val loginState by viewModel.loginState.collectAsState()
    val uriHandler = LocalUriHandler.current
    val loading = loginState is LoginState.Loading

    MobBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(0.8f))

            Image(
                painter = painterResource(id = R.drawable.imiq_logo),
                contentDescription = "IMIQ",
                modifier = Modifier.size(108.dp).clip(RoundedCornerShape(24.dp)),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.height(22.dp))
            Text("IMIQ Mobility", color = Mob.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                s.signupTagline,
                color = Mob.textSecondary, fontSize = 14.sp
            )

            Spacer(Modifier.height(40.dp))

            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text(s.accessCode) },
                placeholder = { Text("123-456-789", color = Mob.textMuted) },
                singleLine = true,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Mob.primary,
                    unfocusedBorderColor = Mob.border,
                    focusedContainerColor = Mob.surfaceHi,
                    unfocusedContainerColor = Mob.surface,
                    focusedTextColor = Mob.textPrimary,
                    unfocusedTextColor = Mob.textPrimary,
                    focusedLabelColor = Mob.primary,
                    unfocusedLabelColor = Mob.textMuted,
                    cursorColor = Mob.primary
                )
            )

            if (loginState is LoginState.Error) {
                Spacer(Modifier.height(10.dp))
                Text(
                    (loginState as LoginState.Error).message,
                    color = Mob.danger, fontSize = 13.sp
                )
            }

            Spacer(Modifier.height(20.dp))

            // Primary action with loading state
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (code.isNotBlank() && !loading) Mob.primary else Mob.surfaceHi)
                    .clickable(enabled = code.isNotBlank() && !loading) { viewModel.login(code) }
                    .padding(vertical = 17.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (loading) {
                    CircularProgressIndicator(color = Mob.primary, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        s.getStarted,
                        color = if (code.isNotBlank()) Mob.onPrimary else Mob.textMuted,
                        fontSize = 16.sp, fontWeight = FontWeight.Bold
                    )
                    Text("  →", color = if (code.isNotBlank()) Mob.onPrimary else Mob.textMuted, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(20.dp))

            val annotated = buildAnnotatedString {
                withStyle(SpanStyle(color = Mob.textSecondary)) { append(s.noCodePrefix) }
                pushStringAnnotation("URL", "https://imiq.ovgu.de/anmeldung")
                withStyle(SpanStyle(color = Mob.primary, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)) {
                    append(s.registerHere)
                }
                pop()
            }
            ClickableText(text = annotated, onClick = { offset ->
                annotated.getStringAnnotations("URL", offset, offset).firstOrNull()?.let { uriHandler.openUri(it.item) }
            })

            Spacer(Modifier.weight(1f))
        }
    }

    LaunchedEffect(loginState) {
        if (loginState is LoginState.Success) {
            onSignUpComplete()
            viewModel.resetState()
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF07090D)
@Composable
private fun SignUpPreview() {
    SignUpScreen()
}
