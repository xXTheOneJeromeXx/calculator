package com.tbce.calc.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.Modifier

/**
 * A control that acts normally on a tap and triggers the hidden gesture on a long press.
 * Used by the Dictionary's Search button for its arm/submit gesture.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.faceHoldable(onTap: () -> Unit, onHold: () -> Unit): Modifier =
    this.combinedClickable(onClick = onTap, onLongClick = onHold)
