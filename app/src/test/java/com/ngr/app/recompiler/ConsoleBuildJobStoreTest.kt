package com.ngr.app.recompiler

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class ConsoleBuildJobStoreTest {
    private fun job(id: String = "job-1", target: ConsoleTargetId = ConsoleTargetId.XBOX_360) =
        ConsoleBuildJob(
            jobId = id, targetId = target,
            sourceUri = "content://com.android.providers.downloads.documents/document/42",
            sourceDisplayName = if (target == ConsoleTargetId.XBOX_360) "owned-game.iso" else "owned-game.nsp",
            sourceGrantFlags = 3, profileId = "default", workspaceId = "workspace-1"
        )

    @Test fun persistsBothSupportedConsoleJobs() {
        val root = Files.createTempDirectory("ngr-jobs-").toFile()
        try {
            val store = ConsoleBuildJobStore(root)
            assertTrue(store.create(job()).success)
            assertTrue(store.create(job("switch-1", ConsoleTargetId.NINTENDO_SWITCH)).success)
            assertEquals(setOf(ConsoleTargetId.XBOX_360, ConsoleTargetId.NINTENDO_SWITCH),
                store.loadAll().jobs.map { it.targetId }.toSet())
            assertTrue(store.loadAll().errors.isEmpty())
        } finally { root.deleteRecursively() }
    }

    @Test fun rejectsPathsNonContentUrisAndMalformedFingerprints() {
        val root = Files.createTempDirectory("ngr-jobs-").toFile()
        try {
            val store = ConsoleBuildJobStore(root)
            listOf("/sdcard/game.iso", "file:///sdcard/game.iso", "https://example/game.iso",
                "content://user:pass@authority/doc/1").forEach {
                assertFalse(store.create(job().copy(sourceUri = it)).success)
            }
            assertFalse(store.create(job().copy(jobId = "../escape")).success)
            assertFalse(store.create(job().copy(sourceSha256 = "not-sha256")).success)
        } finally { root.deleteRecursively() }
    }

    @Test fun validatesTargetSpecificStagesAndSuccessInvariant() {
        assertTrue(job().copy(stage = ConsoleBuildStage.REWRITE_NSO).validate().isNotEmpty())
        assertTrue(job(target = ConsoleTargetId.NINTENDO_SWITCH).copy(stage = ConsoleBuildStage.RECOMPILE_PPC).validate().isNotEmpty())
        assertTrue(job().copy(state = ConsoleBuildJobState.SUCCEEDED).validate().isNotEmpty())
        assertTrue(job().copy(stage = ConsoleBuildStage.COMPLETE, state = ConsoleBuildJobState.SUCCEEDED,
            progressPercent = 100).validate().isEmpty())
    }

    @Test fun updatesAreMonotonicAndSourceIdentityIsImmutable() {
        val root = Files.createTempDirectory("ngr-jobs-").toFile()
        try {
            val store = ConsoleBuildJobStore(root)
            val first = job()
            assertTrue(store.create(first).success)
            val running = first.copy(state = ConsoleBuildJobState.RUNNING, updatedAtEpochMillis = first.updatedAtEpochMillis + 1)
            assertTrue(store.update(running).success)
            assertFalse(store.update(running.copy(sourceUri = "content://provider/other",
                updatedAtEpochMillis = running.updatedAtEpochMillis + 1)).success)
            val next = running.copy(stage = ConsoleBuildStage.EXTRACT,
                completedStages = listOf(ConsoleBuildStage.VALIDATE_SOURCE), progressPercent = 20,
                updatedAtEpochMillis = running.updatedAtEpochMillis + 1)
            assertTrue(store.update(next).success)
            assertFalse(store.update(next.copy(stage = ConsoleBuildStage.VALIDATE_SOURCE,
                updatedAtEpochMillis = next.updatedAtEpochMillis + 1)).success)
        } finally { root.deleteRecursively() }
    }

    @Test fun cancellationAndInterruptedWorkerRecovery() {
        val root = Files.createTempDirectory("ngr-jobs-").toFile()
        try {
            val store = ConsoleBuildJobStore(root)
            val queued = job()
            val running = job("running")
            assertTrue(store.create(queued).success); assertTrue(store.create(running).success)
            assertTrue(store.update(running.copy(state = ConsoleBuildJobState.RUNNING,
                updatedAtEpochMillis = running.updatedAtEpochMillis + 1)).success)
            assertTrue(store.requestCancel(queued.jobId, queued.createdAtEpochMillis + 1).success)
            assertEquals(ConsoleBuildJobState.CANCELLED, store.get(queued.jobId)?.state)
            assertEquals(1, store.recoverInterruptedJobs(running.createdAtEpochMillis + 2))
            assertEquals(ConsoleBuildJobState.QUEUED, store.get("running")?.state)
        } finally { root.deleteRecursively() }
    }

    @Test fun logsAreBoundedAndControlCharactersAndSecretsAreRedacted() {
        val root = Files.createTempDirectory("ngr-jobs-").toFile()
        try {
            val store = ConsoleBuildJobStore(root)
            assertTrue(store.create(job()).success)
            assertTrue(store.appendLog("job-1", "INFO", "loaded\nprod.keys=supersecret").success)
            val log = store.readLog("job-1")!!
            assertEquals(1, log.trim().lines().size)
            assertTrue(log.contains("prod.keys=[REDACTED]"))
            assertFalse(log.contains("supersecret"))
            assertFalse(store.appendLog("job-1", "TRACE", "bad").success)
        } finally { root.deleteRecursively() }
    }

    @Test fun corruptMetadataIsReported() {
        val root = Files.createTempDirectory("ngr-jobs-").toFile()
        try {
            val dir = File(File(root, "console-build-jobs"), "metadata")
            dir.mkdirs(); File(dir, "broken.json").writeText("{broken")
            val result = ConsoleBuildJobStore(root).loadAll()
            assertTrue(result.jobs.isEmpty()); assertEquals(1, result.errors.size)
        } finally { root.deleteRecursively() }
    }
}
