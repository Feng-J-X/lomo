package com.lomo.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.lomo.data.local.MemoDatabase
import com.lomo.data.local.dao.FileSyncDao
import com.lomo.data.local.dao.MemoDao
import com.lomo.data.parser.MarkdownParser
import com.lomo.data.repository.MemoRepositoryImpl
import com.lomo.domain.repository.MemoRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideMemoDatabase(@ApplicationContext context: Context): MemoDatabase {
        return Room.databaseBuilder(context, MemoDatabase::class.java, "lomo.db")
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .fallbackToDestructiveMigration(true)
                .build()
    }

    @Provides
    @Singleton
    fun provideMemoDao(database: MemoDatabase): MemoDao {
        return database.memoDao()
    }

    @Provides
    @Singleton
    fun provideImageCacheDao(database: MemoDatabase): com.lomo.data.local.dao.ImageCacheDao {
        return database.imageCacheDao()
    }

    @Provides
    @Singleton
    fun provideFileSyncDao(database: MemoDatabase): FileSyncDao {
        return database.fileSyncDao()
    }

    @Provides
    @Singleton
    fun provideMarkdownParser(): MarkdownParser {
        return MarkdownParser()
    }

    @Provides
    @Singleton
    fun provideMemoTextProcessor(): com.lomo.data.util.MemoTextProcessor {
        return com.lomo.data.util.MemoTextProcessor()
    }

    @Provides
    @Singleton
    fun provideFileDataSource(
            @ApplicationContext context: Context,
            dataStore: com.lomo.data.local.datastore.LomoDataStore
    ): com.lomo.data.source.FileDataSource {
        return com.lomo.data.source.FileDataSourceImpl(context, dataStore)
    }

    @Provides
    @Singleton
    fun provideMemoSynchronizer(
            dataSource: com.lomo.data.source.FileDataSource,
            dao: MemoDao,
            fileSyncDao: FileSyncDao,
            parser: MarkdownParser,
            processor: com.lomo.data.util.MemoTextProcessor,
            dataStore: com.lomo.data.local.datastore.LomoDataStore // Injected
    ): com.lomo.data.repository.MemoSynchronizer {
        return com.lomo.data.repository.MemoSynchronizer(
                dataSource,
                dao,
                fileSyncDao,
                parser,
                processor,
                dataStore
        )
    }

    @Provides
    @Singleton
    fun provideMemoRepository(
            dao: MemoDao,
            imageCacheDao: com.lomo.data.local.dao.ImageCacheDao,
            dataSource: com.lomo.data.source.FileDataSource,
            synchronizer: com.lomo.data.repository.MemoSynchronizer,
            parser: MarkdownParser,
            dataStore: com.lomo.data.local.datastore.LomoDataStore
    ): MemoRepository {
        return MemoRepositoryImpl(dao, imageCacheDao, dataSource, synchronizer, parser, dataStore)
    }
}
