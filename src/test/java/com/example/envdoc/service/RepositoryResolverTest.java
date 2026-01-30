package com.example.envdoc.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RepositoryResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldResolveLocalRepositoryPath() {
        BitBucketService bitBucketService = mock(BitBucketService.class);
        RepositoryResolver resolver = new RepositoryResolver(bitBucketService);

        RepositoryHandle handle = resolver.resolve(tempDir.toString(), null, null);

        assertNotNull(handle);
        assertEquals(tempDir, handle.getPath());
        assertEquals(tempDir.getFileName().toString(), handle.getProjectName());
        handle.close();

        verifyNoInteractions(bitBucketService);
    }

    @Test
    void shouldResolveLocalRepositoryFileScheme() {
        BitBucketService bitBucketService = mock(BitBucketService.class);
        RepositoryResolver resolver = new RepositoryResolver(bitBucketService);

        String fileUrl = tempDir.toUri().toString();
        RepositoryHandle handle = resolver.resolve(fileUrl, null, null);

        assertNotNull(handle);
        assertEquals(tempDir, handle.getPath());
        handle.close();

        verifyNoInteractions(bitBucketService);
    }

    @Test
    void shouldCleanupClonedRepositoryOnClose() {
        BitBucketService bitBucketService = mock(BitBucketService.class);
        Path repoPath = tempDir.resolve("cloned-repo");
        RepositoryHandle handle = new RepositoryHandle(repoPath, "cloned-repo", true, bitBucketService);

        handle.close();

        verify(bitBucketService, times(1)).cleanupRepository(repoPath);
    }
}
