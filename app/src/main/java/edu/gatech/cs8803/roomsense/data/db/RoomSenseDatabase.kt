package edu.gatech.cs8803.roomsense.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [RoomEntity::class, SampleEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class RoomSenseDatabase : RoomDatabase() {
    abstract fun roomDao(): RoomDao
    abstract fun sampleDao(): SampleDao

    companion object {
        fun create(context: Context): RoomSenseDatabase {
            return Room.databaseBuilder(context, RoomSenseDatabase::class.java, "roomsense.db")
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
