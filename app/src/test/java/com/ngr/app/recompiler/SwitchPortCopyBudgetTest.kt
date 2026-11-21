package com.ngr.app.recompiler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class SwitchPortCopyBudgetTest {
    @Test fun acceptsExactConfiguredLimits() {
        val budget = SwitchPortCopyBudget(
            maxFiles = 2,
            maxEntries = 3,
            maxTotalBytes = 8,
            maxSingleFileBytes = 6
        )
        budget.recordEntry(isFile = true)
        budget.recordBytes(fileBytesAfterChunk = 4, chunkBytes = 4)
        budget.recordEntry(isFile = false)
        budget.recordEntry(isFile = true)
        budget.recordBytes(fileBytesAfterChunk = 4, chunkBytes = 4)
        budget.validateDepth(128)
        assertEquals(2, budget.files)
        assertEquals(3, budget.entries)
        assertEquals(8L, budget.totalBytes)
    }

    @Test fun rejectsTooManyFiles() {
        val budget = SwitchPortCopyBudget(maxFiles = 1)
        budget.recordEntry(isFile = true)
        assertThrows(IOException::class.java) { budget.recordEntry(isFile = true) }
    }

    @Test fun rejectsTooManyDirectoryEntries() {
        val budget = SwitchPortCopyBudget(maxEntries = 1)
        budget.recordEntry(isFile = false)
        assertThrows(IOException::class.java) { budget.recordEntry(isFile = false) }
    }

    @Test fun rejectsSingleFileOverLimit() {
        val budget = SwitchPortCopyBudget(maxSingleFileBytes = 4)
        assertThrows(IOException::class.java) {
            budget.recordBytes(fileBytesAfterChunk = 5, chunkBytes = 5)
        }
    }

    @Test fun rejectsAggregateDataOverLimit() {
        val budget = SwitchPortCopyBudget(maxTotalBytes = 5)
        budget.recordBytes(fileBytesAfterChunk = 3, chunkBytes = 3)
        assertThrows(IOException::class.java) {
            budget.recordBytes(fileBytesAfterChunk = 3, chunkBytes = 3)
        }
    }

    @Test fun rejectsExcessiveDirectoryDepth() {
        val budget = SwitchPortCopyBudget()
        assertThrows(IOException::class.java) { budget.validateDepth(129) }
    }
}
