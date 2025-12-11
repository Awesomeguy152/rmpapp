package com.nano.min.ui.theme

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction

val KeyboardPassword = KeyboardOptions(
    autoCorrect = false,
    keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
    imeAction = ImeAction.Done
)