package hu.bbara.breakthesnooze.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import hu.bbara.breakthesnooze.ui.alarm.dismiss.AlarmDismissTaskType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {

    private val tempDir: File = Files.createTempDirectory("settings-repo-test").toFile()

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `settings flow emits updated values after modifications`() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(tempDir, "settings.preferences_pb") }
        )
        val repository = SettingsRepository(dataStore)

        val initial = repository.settings.first()
        assertEquals(AlarmDismissTaskType.DEFAULT, initial.defaultDismissTask)
        assertNull(initial.defaultRingtoneUri)
        assertFalse(initial.debugModeEnabled)

        repository.setDefaultDismissTask(AlarmDismissTaskType.QR_BARCODE_SCAN)
        repository.setDefaultRingtone("content://tone")
        repository.setDebugModeEnabled(true)
        advanceUntilIdle()

        val updated = repository.settings.first { it.debugModeEnabled }
        assertEquals(AlarmDismissTaskType.QR_BARCODE_SCAN, updated.defaultDismissTask)
        assertEquals("content://tone", updated.defaultRingtoneUri)
        assertTrue(updated.debugModeEnabled)

        repository.setDefaultRingtone(null)
        advanceUntilIdle()

        val cleared = repository.settings.first { it.debugModeEnabled && it.defaultRingtoneUri == null }
        assertNull(cleared.defaultRingtoneUri)
    }
}
