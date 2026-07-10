package com.dhanuk.govphoto_resizer.di

import com.dhanuk.govphoto_resizer.data.ml.MlKitSegmenterClient
import com.dhanuk.govphoto_resizer.data.ml.SegmenterClient
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder().create()
    }

    @Provides
    @Singleton
    fun provideSegmenterClient(): SegmenterClient = MlKitSegmenterClient()
}
