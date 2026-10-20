package edu.gatech.cs8803.roomsense.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RoomDao {
    @Query("SELECT * FROM rooms ORDER BY createdAt ASC")
    fun observe(): Flow<List<RoomEntity>>

    @Query("SELECT * FROM rooms ORDER BY createdAt ASC")
    suspend fun all(): List<RoomEntity>

    @Query("SELECT * FROM rooms WHERE id = :id")
    suspend fun byId(id: String): RoomEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(room: RoomEntity)

    @Query("DELETE FROM rooms WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM rooms")
    suspend fun clear()
}

@Dao
interface SampleDao {
    @Query("SELECT * FROM samples ORDER BY capturedAt DESC")
    fun observe(): Flow<List<SampleEntity>>

    @Query("SELECT * FROM samples")
    suspend fun all(): List<SampleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sample: SampleEntity)

    @Query("DELETE FROM samples WHERE roomId = :roomId")
    suspend fun deleteForRoom(roomId: String)
}
