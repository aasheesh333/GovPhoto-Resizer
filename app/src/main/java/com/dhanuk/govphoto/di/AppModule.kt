package com.dhanuk.govphoto.di

import com.dhanuk.govphoto.BuildConfig
import com.dhanuk.govphoto.data.ads.AdStateProvider
import com.dhanuk.govphoto.data.datastore.CachedIsProStore
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
    fun provideCachedIsProStore(): CachedIsProStore =
        object : CachedIsProStore {
            private var v = false
            override suspend fun getCachedIsPro(): Boolean = v
            override suspend fun setCachedIsPro(value: Boolean) { v = value }
        }

    @Provides
    @Singleton
    fun provideAdStateProvider(
        subscriptionRepository: SubscriptionRepository,
    ): AdStateProvider =
        object : AdStateProvider {
            override val isPro: Boolean get() = subscriptionRepository.isPro.value
            override val adFreeUntilMs: Long get() = 0L        // Re-wired in Task 9
            override val forceNoAds: Boolean get() = BuildConfig.DEBUG
        }

    @Provides
    @Singleton
    fun providePushCategoryStore(): com.dhanuk.govphoto.data.push.PushCategoryStore =
        object : com.dhanuk.govphoto.data.push.PushCategoryStore {
            private val map = com.dhanuk.govphoto.data.push.PushCategory.entries.associateBy { it }.mapValues { it.value.defaultEnabled }.toMutableMap()
            override suspend fun isEnabled(category: com.dhanuk.govphoto.data.push.PushCategory): Boolean = map[category] ?: category.defaultEnabled
            override suspend fun setEnabled(category: com.dhanuk.govphoto.data.push.PushCategory, enabled: Boolean) { map[category] = enabled }
        }
}
