package com.hypertap.autoclicker.data.repository

import com.hypertap.autoclicker.data.local.Converters
import com.hypertap.autoclicker.data.local.MacroDao
import com.hypertap.autoclicker.data.local.MacroEntity
import com.hypertap.autoclicker.model.MacroPattern
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MacroRepository(private val macroDao: MacroDao) {
    private val converters = Converters()

    val allMacros: Flow<List<MacroPattern>> = macroDao.getAllMacros().map { list ->
        list.map { it.toDomain(converters) }
    }

    suspend fun getMacroById(id: Long): MacroPattern? {
        return macroDao.getMacroById(id)?.toDomain(converters)
    }

    suspend fun saveMacro(macro: MacroPattern): Long {
        val entity = MacroEntity(
            id = macro.id,
            name = macro.name,
            repeatCount = macro.repeatCount,
            intervalBetweenCyclesMs = macro.intervalBetweenCyclesMs,
            actionsJson = converters.fromActionsList(macro.actions),
            createdAt = macro.createdAt
        )
        return macroDao.insert(entity)
    }

    suspend fun deleteMacro(macro: MacroPattern) {
        val entity = MacroEntity(
            id = macro.id,
            name = macro.name,
            repeatCount = macro.repeatCount,
            intervalBetweenCyclesMs = macro.intervalBetweenCyclesMs,
            actionsJson = converters.fromActionsList(macro.actions),
            createdAt = macro.createdAt
        )
        macroDao.delete(entity)
    }

    private fun MacroEntity.toDomain(converters: Converters): MacroPattern {
        return MacroPattern(
            id = id,
            name = name,
            repeatCount = repeatCount,
            intervalBetweenCyclesMs = intervalBetweenCyclesMs,
            actions = converters.toActionsList(actionsJson),
            createdAt = createdAt
        )
    }
}

