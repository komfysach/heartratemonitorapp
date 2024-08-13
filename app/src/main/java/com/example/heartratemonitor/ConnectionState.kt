package com.example.heartratemonitor

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

object ConnectionState {
    var isConnected: MutableState<Boolean> = mutableStateOf(false)
}