package com.hypertap.autoclicker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.hypertap.autoclicker.model.TapAction
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName = "macros")
data class MacroEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val repeatCount: Int = 1,
    val intervalBetweenCyclesMs: Long = 500L,
    val actionsJson: String,
    val createdAt: Long = System.currentTimeMillis()
)

class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromActionsList(actions: List<TapAction>): String {
        return json.encodeToString(actions)
    }

    @TypeConverter
    fun toActionsList(actionsJson: String): List<TapAction> {
        return try {
            json.decodeFromString<List<TapAction>>(actionsJson)
        } catch (e: Exception) {
            emptyList()
        }
    }
}

