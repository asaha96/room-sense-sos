package edu.gatech.cs8803.roomsense.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "rooms")
data class RoomEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val createdAt: Long,
)

@Entity(
    tableName = "samples",
    foreignKeys = [
        ForeignKey(
            entity = RoomEntity::class,
            parentColumns = ["id"],
            childColumns = ["roomId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("roomId")],
)
data class SampleEntity(
    @PrimaryKey val id: String,
    val roomId: String,
    val capturedAt: Long,
    val fromCache: Boolean,
    val readingsJson: String,
)
