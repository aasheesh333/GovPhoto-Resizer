package com.dhanuk.govphoto.di

import android.content.Context
import androidx.room.Room
import com.dhanuk.govphoto.data.local.GovPhotoDatabase
import com.dhanuk.govphoto.data.local.dao.PhotoHistoryDao
import com.dhanuk.govphoto.data.local.dao.RecentPresetDao
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
            // No destructive migration — future schema changes must add explicit Migration objects.
            // For v1 (initial release) there are no migrations needed yet.
            // When adding v2, add: .addMigrations(MIGRATION_1_2) here
            .build()

    @Provides
    fun providePhotoHistoryDao(db: GovPhotoDatabase): PhotoHistoryDao = db.photoHistoryDao()

    @Provides
    fun provideRecentPresetDao(db: GovPhotoDatabase): RecentPresetDao = db.recentPresetDao()
}
