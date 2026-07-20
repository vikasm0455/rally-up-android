// Sign up / log in with email OTP — Flow 1 of the frozen design.
// 1:1 port of iOS AuthFlowView.swift (functional against the live API).

package com.badmintonrallyup.app.features.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.badmintonrallyup.app.LocalSession
import com.badmintonrallyup.app.designsystem.AppHaptics
import com.badmintonrallyup.app.designsystem.GlassButton
import com.badmintonrallyup.app.designsystem.RallyTextField
import com.badmintonrallyup.app.designsystem.Theme
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import kotlinx.coroutines.launch

private enum class Step { Details, Code }

@Composable
fun AuthFlowScreen() {
    val session = LocalSession.current
    val scope = rememberCoroutineScope()

    var isSignup by remember { mutableStateOf(true) }
    var step by remember { mutableStateOf(Step.Details) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun sendCode() {
        busy = true
        try {
            session.requestCode(
                email = email.trim(),
                displayName = if (isSignup) name.trim() else null
            )
            step = Step.Code
            error = null
        } catch (e: Exception) {
            error = e.message
        } finally {
            busy = false
        }
    }

    suspend fun verify() {
        busy = true
        try {
            session.verify(email = email.trim(), code = code, isSignup = isSignup)
            AppHaptics.success()
        } catch (e: Exception) {
            error = e.message
        } finally {
            busy = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.chalk)
            .systemBarsPadding()
            .imePadding()
            .padding(Theme.screenGutter),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // MARK: header
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 32.dp)
        ) {
            Text(
                "BADMINTON",
                style = Theme.label().copy(letterSpacing = 2.4.sp),
                color = Theme.court
            )
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Theme.ink)) { append("Rally") }
                    withStyle(SpanStyle(color = Theme.cork)) { append("Up") }
                },
                style = Theme.display(34f)
            )
            Text(
                if (isSignup) "Your badminton crew, organized." else "Welcome back.",
                style = Theme.caption(),
                color = Theme.inkMuted
            )
        }

        if (step == Step.Details) {
            // MARK: details form
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSignup) {
                    RallyTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = "Your name",
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                    )
                }
                RallyTextField(
                    value = email,
                    onValueChange = { email = it },
                    placeholder = "Email",
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        keyboardType = KeyboardType.Email
                    )
                )
                GlassButton(
                    if (busy) "Sending…" else "Send my code",
                    enabled = !(busy || email.isEmpty() || (isSignup && name.isEmpty()))
                ) {
                    scope.launch { sendCode() }
                }
                ConsentLine()
                Text(
                    if (isSignup) "Have an account? Log in" else "New here? Create an account",
                    style = Theme.caption(),
                    color = Theme.court,
                    modifier = Modifier.clickable {
                        isSignup = !isSignup
                        error = null
                    }
                )
            }
        } else {
            // MARK: code form
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Enter the code sent to $email",
                    style = Theme.caption(),
                    color = Theme.inkMuted
                )
                RallyTextField(
                    value = code,
                    onValueChange = { code = it },
                    placeholder = "6-digit code",
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = Theme.display(24f),
                    centered = true
                )
                GlassButton(
                    if (busy) "Verifying…" else "Verify & continue",
                    enabled = !(busy || code.length < 6)
                ) {
                    scope.launch { verify() }
                }
                Text(
                    "Wrong address? Go back",
                    style = Theme.caption(),
                    color = Theme.inkMuted,
                    modifier = Modifier.clickable {
                        step = Step.Details
                        code = ""
                        error = null
                    }
                )
            }
        }

        error?.let {
            Text(it, style = Theme.caption(12f), color = Theme.cork)
        }
        Spacer(Modifier.weight(1f))
    }
}

// Clickwrap consent (store UGC expectation): continuing = agreement.
// Shown on signup AND login, matching iOS.
@Composable
private fun ConsentLine() {
    val court = Theme.court
    val linkStyle = TextLinkStyles(style = SpanStyle(color = court))
    Text(
        buildAnnotatedString {
            append("By continuing, you agree to our ")
            withLink(LinkAnnotation.Url("https://badmintonrallyup.com/terms", linkStyle)) {
                append("Terms")
            }
            append(" and ")
            withLink(LinkAnnotation.Url("https://badmintonrallyup.com/privacy", linkStyle)) {
                append("Privacy Policy")
            }
            append(".")
        },
        style = Theme.caption(11.5f),
        color = Theme.inkMuted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}
