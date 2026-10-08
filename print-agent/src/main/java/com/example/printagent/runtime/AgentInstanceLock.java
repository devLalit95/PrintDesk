package com.example.printagent.runtime;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;

public final class AgentInstanceLock implements AutoCloseable {

    private final FileChannel channel;
    private final FileLock lock;

    private AgentInstanceLock(FileChannel channel, FileLock lock) {
        this.channel = channel;
        this.lock = lock;
    }

    public static AgentInstanceLock acquire(Path workDirectory) {
        workDirectory = workDirectory.toAbsolutePath().normalize();
        Path homeDirectory = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
        if (workDirectory.getParent() == null
                || homeDirectory.startsWith(workDirectory)) {
            throw new IllegalStateException("The agent work directory must be a dedicated directory, not a system or home root.");
        }
        Path lockPath = workDirectory.resolve(".agent-instance.lock");
        FileChannel channel = null;
        try {
            boolean createPrivateDirectory = !Files.exists(workDirectory, LinkOption.NOFOLLOW_LINKS);
            Files.createDirectories(workDirectory);
            if (Files.isSymbolicLink(workDirectory)
                    || !Files.isDirectory(workDirectory, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalStateException("The agent work path must be a real directory, not a symbolic link.");
            }
            if (createPrivateDirectory) {
                setPermissions(workDirectory, EnumSet.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE));
            } else {
                requirePrivatePermissions(workDirectory);
            }
            if (Files.exists(lockPath, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isRegularFile(lockPath, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalStateException("The agent lock path is not a regular file.");
            }
            channel = FileChannel.open(
                    lockPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE);
            setPermissions(lockPath, EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE));
            FileLock lock;
            try {
                lock = channel.tryLock();
            } catch (OverlappingFileLockException exception) {
                channel.close();
                channel = null;
                throw new IllegalStateException("Another PrintDesk agent process already holds this work directory.");
            }
            if (lock == null) {
                channel.close();
                channel = null;
                throw new IllegalStateException("Another PrintDesk agent process already holds this work directory.");
            }
            return new AgentInstanceLock(channel, lock);
        } catch (IOException exception) {
            if (channel != null) {
                try {
                    channel.close();
                } catch (IOException closeException) {
                    exception.addSuppressed(closeException);
                }
            }
            throw new IllegalStateException("The agent work directory could not be locked.", exception);
        }
    }

    private static void setPermissions(
            Path path, java.util.Set<PosixFilePermission> permissions) throws IOException {
        try {
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException ignored) {
            // Windows ACLs are managed by the current user account.
        }
    }

    private static void requirePrivatePermissions(Path directory) throws IOException {
        try {
            var permissions = Files.getPosixFilePermissions(directory, LinkOption.NOFOLLOW_LINKS);
            if (permissions.contains(PosixFilePermission.GROUP_READ)
                    || permissions.contains(PosixFilePermission.GROUP_WRITE)
                    || permissions.contains(PosixFilePermission.GROUP_EXECUTE)
                    || permissions.contains(PosixFilePermission.OTHERS_READ)
                    || permissions.contains(PosixFilePermission.OTHERS_WRITE)
                    || permissions.contains(PosixFilePermission.OTHERS_EXECUTE)) {
                throw new IllegalStateException(
                        "The existing agent work directory is not private; restrict it to the current user.");
            }
        } catch (UnsupportedOperationException ignored) {
            // Windows ACLs are managed by the current user account.
        }
    }

    @Override
    public void close() {
        IOException failure = null;
        try {
            lock.release();
        } catch (IOException exception) {
            failure = exception;
        }
        try {
            channel.close();
        } catch (IOException exception) {
            if (failure == null) {
                failure = exception;
            } else {
                failure.addSuppressed(exception);
            }
        }
        if (failure != null) {
            throw new IllegalStateException("The agent work-directory lock could not be released.", failure);
        }
    }
}
