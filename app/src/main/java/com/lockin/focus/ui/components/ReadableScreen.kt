package com.lockin.focus.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Constrains a screen to a readable column and centres it.
 *
 * Without this, a landscape phone or a tablet lays a single sentence out across
 * 2400px, which is the fastest way to make an app look unfinished on any device
 * that is not a portrait phone.
 */
@Composable
fun ReadableScreen(
    modifier: Modifier = Modifier,
    maxWidth: Dp = 720.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        // fillMaxHeight, not fillMaxSize: a trailing fillMaxSize would override the
        // width cap and the column would stretch back across the whole screen.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = maxWidth)
                .fillMaxHeight(),
            content = content,
        )
    }
}
