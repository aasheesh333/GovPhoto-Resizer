package com.dhanuk.govphoto_resizer.di

import android.content.Context
import androidx.room.Room
import com.dhanuk.govphoto_resizer.data.local.GovPhotoDatabase
import com.dhanuk.govphoto_resizer.data.local.dao.PhotoHistoryDao
import com.dhanuk.govphoto_resizer.data.local.dao.RecentPresetDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): GovPhotoDatabase =
        Room.databaseBuilder(context, GovPhotoDatabase::class.java, "govphoto.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun providePhotoHistoryDao(db: GovPhotoDatabase): PhotoHistoryDao = db.photoHistoryDao()

    @Provides
    fun provideRecentPresetDao(db: GovPhotoDatabase): RecentPresetDao = db.recentPresetDao()
}
