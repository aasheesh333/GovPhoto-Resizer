package com.dhanuk.govphoto.ui.diagnostics

import com.dhanuk.govphoto.data.push.PushRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Public entry point so any composable / dialog can reach the singleton
 * [PushRepository] without threading it through every ViewModel.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PushEntryPoint {
    fun pushRepository(): PushRepository
}
