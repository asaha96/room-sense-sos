package edu.gatech.cs8803.roomsense.data.repo

import androidx.room.withTransaction
import edu.gatech.cs8803.roomsense.data.db.RoomEntity
import edu.gatech.cs8803.roomsense.data.db.RoomSenseDatabase
import edu.gatech.cs8803.roomsense.data.db.RssiJson
import edu.gatech.cs8803.roomsense.data.db.SampleEntity
import edu.gatech.cs8803.roomsense.domain.demo.DemoRadio
import edu.gatech.cs8803.roomsense.domain.map.RoomPalette
import edu.gatech.cs8803.roomsense.domain.model.FingerprintSample
import edu.gatech.cs8803.roomsense.domain.model.RoomModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import kotlin.random.Random

class BuildingRepository(
    private val database: RoomSenseDatabase,
) {
    fun observeRooms(): Flow<List<RoomModel>> {
        return database.roomDao().observe().map { rows -> rows.map { it.toModel() } }
    }

    fun observeSamples(): Flow<List<FingerprintSample>> {
        return database.sampleDao().observe().map { rows ->
            val names = database.roomDao().all().associate { it.id to it.name }
            rows.map { it.toSample(names[it.roomId] ?: "Unknown") }
        }
    }

    suspend fun roomsOnce(): List<RoomModel> = database.roomDao().all().map { it.toModel() }

    suspend fun samplesOnce(): List<FingerprintSample> {
        val names = database.roomDao().all().associate { it.id to it.name }
        return database.sampleDao().all().map { it.toSample(names[it.roomId] ?: "Unknown") }
    }

    suspend fun createRoom(name: String): RoomModel {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty())
        return database.withTransaction {
            val index = database.roomDao().all().size
            val rect = RoomPalette.defaultRect(index)
            val entity = RoomEntity(
                id = UUID.randomUUID().toString(),
                name = trimmed,
                color = RoomPalette.colorFor(index),
                x = rect[0],
                y = rect[1],
                width = rect[2],
                height = rect[3],
                createdAt = System.currentTimeMillis(),
            )
            database.roomDao().upsert(entity)
            entity.toModel()
        }
    }

    suspend fun rename(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val existing = database.roomDao().byId(id) ?: return
        database.roomDao().upsert(existing.copy(name = trimmed))
    }

    suspend fun updateGeometry(room: RoomModel) {
        val existing = database.roomDao().byId(room.id) ?: return
        database.roomDao().upsert(
            existing.copy(
                x = room.x.coerceIn(0f, 1f),
                y = room.y.coerceIn(0f, 1f),
                width = room.width.coerceIn(0.08f, 1f),
                height = room.height.coerceIn(0.08f, 1f),
            ),
        )
    }

    suspend fun deleteRoom(id: String) {
        database.roomDao().delete(id)
    }

    suspend fun clearFingerprints(roomId: String) {
        database.sampleDao().deleteForRoom(roomId)
    }

    suspend fun saveSample(roomId: String, readings: Map<String, Int>, fromCache: Boolean) {
        database.sampleDao().insert(
            SampleEntity(
                id = UUID.randomUUID().toString(),
                roomId = roomId,
                capturedAt = System.currentTimeMillis(),
                fromCache = fromCache,
                readingsJson = RssiJson.encode(readings),
            ),
        )
    }

    suspend fun replaceWithDemoFloor() {
        database.withTransaction {
            database.roomDao().clear()
            val now = System.currentTimeMillis()
            DemoRadio.rooms.forEachIndexed { index, room ->
                database.roomDao().upsert(
                    RoomEntity(
                        id = room.id,
                        name = room.name,
                        color = room.color,
                        x = room.x,
                        y = room.y,
                        width = room.width,
                        height = room.height,
                        createdAt = now + index,
                    ),
                )
                repeat(4) { sampleIndex ->
                    val readings = DemoRadio.fingerprint(
                        room.id,
                        Random(room.id.hashCode() * 31L + sampleIndex),
                    )
                    database.sampleDao().insert(
                        SampleEntity(
                            id = "${room.id}-$sampleIndex",
                            roomId = room.id,
                            capturedAt = now + index * 10L + sampleIndex,
                            fromCache = false,
                            readingsJson = RssiJson.encode(readings),
                        ),
                    )
                }
            }
        }
    }
}

private fun RoomEntity.toModel() = RoomModel(
    id = id,
    name = name,
    color = color,
    x = x,
    y = y,
    width = width,
    height = height,
    createdAt = createdAt,
)

private fun SampleEntity.toSample(roomName: String) = FingerprintSample(
    id = id,
    roomId = roomId,
    roomName = roomName,
    readings = RssiJson.decode(readingsJson),
    capturedAtMillis = capturedAt,
    fromCache = fromCache,
)
