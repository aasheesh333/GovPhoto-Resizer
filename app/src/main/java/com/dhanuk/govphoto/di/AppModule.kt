package com.dhanuk.govphoto.di

import com.dhanuk.govphoto.BuildConfig
import com.dhanuk.govphoto.data.ads.AdStateProvider
import com.dhanuk.govphoto.data.ml.FaceDetectorClient
import com.dhanuk.govphoto.data.ml.MlKitFaceDetectorClient
import com.dhanuk.govphoto.data.ml.MlKitSegmenterClient
import com.dhanuk.govphoto.data.ml.SegmenterClient
import com.dhanuk.govphoto.data.subscription.SubscriptionRepository
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

    @Provides
    @Singleton
    fun provideFaceDetectorClient(): FaceDetectorClient = MlKitFaceDetectorClient()

    @Provides
    @Singleton
    fun provideAdStateProvider(
        @dagger.hilt.android.qualifiers.ApplicationContext ctx: android.content.Context,
        subscriptionRepository: SubscriptionRepository,
    ): AdStateProvider =
        object : AdStateProvider {
            override val isPro: Boolean get() = subscriptionRepository.isPro.value
            override val adFreeUntilMs: Long get() {
                return ctx.getSharedPreferences("govphoto_ad_free", android.content.Context.MODE_PRIVATE)
                    .getLong("ad_free_until_ms", 0L)
            }
            override val forceNoAds: Boolean get() = BuildConfig.DEBUG
        }
}
