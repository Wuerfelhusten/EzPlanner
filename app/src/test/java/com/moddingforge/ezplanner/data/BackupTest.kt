package com.moddingforge.ezplanner.data

import com.moddingforge.ezplanner.data.backup.BackupManager
import com.moddingforge.ezplanner.data.db.DefaultData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BackupTest {
    private val snapshot = Snapshot(DefaultData.expenseTypes, emptyList(), emptyList(), emptyList(), emptyList(), Settings())

    @Test
    fun `round trip keeps expense types`() {
        assertEquals(DefaultData.expenseTypes, BackupManager.fromJson(BackupManager.toJson(snapshot)).expenseTypes)
    }

    @Test
    fun `older backup without the saving flag`() {
        val old = BackupManager.toJson(snapshot).replace(Regex(""",\s*"countsAsSaving": (true|false)"""), "")
        assertFalse(old.contains("countsAsSaving"))
        assertEquals(DefaultData.expenseTypes, BackupManager.fromJson(old).expenseTypes)
    }
}
