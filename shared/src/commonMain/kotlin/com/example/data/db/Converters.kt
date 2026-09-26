package com.example.data.db

import androidx.room.TypeConverter
import com.example.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromKeyframeList(value: List<PropertyKeyframe>): String {
        return json.encodeToString(value)
    }

    @TypeConverter
    fun toKeyframeList(value: String): List<PropertyKeyframe> {
        return try {
            json.decodeFromString(value)
        } catch (e: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromLayerEffectList(value: List<LayerEffect>): String {
        return json.encodeToString(value)
    }

    @TypeConverter
    fun toLayerEffectList(value: String): List<LayerEffect> {
        return try {
            json.decodeFromString(value)
        } catch (e: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromRenderEngineType(value: RenderEngineType): String = value.name

    @TypeConverter
    fun toRenderEngineType(value: String): RenderEngineType = try {
        RenderEngineType.valueOf(value)
    } catch (e: Exception) {
        RenderEngineType.FUSION_2
    }
}
