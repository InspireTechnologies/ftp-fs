/*
 * AbstractFTPFileSystemTest.java
 * Copyright 2016 Rob Spoor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.robtimus.filesystems.ftp;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.Mockito.spy;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.OpenOption;
import java.util.Collection;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.mockftpserver.fake.FakeFtpServer;
import org.mockftpserver.fake.UserAccount;
import org.mockftpserver.fake.filesystem.DirectoryEntry;
import org.mockftpserver.fake.filesystem.FileEntry;
import org.mockftpserver.fake.filesystem.FileSystem;
import org.mockftpserver.fake.filesystem.FileSystemEntry;
import org.mockftpserver.fake.filesystem.UnixFakeFileSystem;
import com.github.robtimus.filesystems.ftp.server.ExtendedUnixFakeFileSystem;
import com.github.robtimus.filesystems.ftp.server.ListHiddenFilesCommandHandler;
import com.github.robtimus.filesystems.ftp.server.MDTMCommandHandler;
import com.github.robtimus.filesystems.ftp.server.SymbolicLinkEntry;

@SuppressWarnings({ "nls", "javadoc" })
public abstract class AbstractFTPFileSystemTest {

    private static final String USERNAME = "TEST_USER";
    private static final String PASSWORD = "TEST_PASSWORD";
    private static final String HOME_DIR = "/home/test";

    private static FakeFtpServer unixFtpServer;
    private static FakeFtpServer nonUnixFtpServer;
    private static ExceptionFactoryWrapper exceptionFactory;
    private static FTPFileSystem unixFtpFileSystem;
    private static FTPFileSystem nonUnixFileSystem;
    private static FTPFileSystem unixFtpFileSystemWithNoAbsolutePathSupport;
    private static FTPFileSystem nonUnixFtpFileSystemWithNoAbsolutePathSupport;
    private static FTPFileSystem multiClientUnixFtpFileSystem;
    private static FTPFileSystem multiClientNonUnixFtpFileSystem;
    private static FTPFileSystem multiClientUnixFtpFileSystemWithNoAbsolutePathSupport;
    private static FTPFileSystem multiClientNonUnixFtpFileSystemWithNoAbsolutePathSupport;

    private FileSystem fs;

    private final boolean useUnixFtpServer;
    private final boolean supportAbsoluteFilePaths;

    protected final FTPFileSystem fileSystem;
    protected final FTPFileSystem multiClientFileSystem;

    public AbstractFTPFileSystemTest(boolean useUnixFtpServer, boolean supportAbsoluteFilePaths) {
        this.useUnixFtpServer = useUnixFtpServer;
        this.supportAbsoluteFilePaths = supportAbsoluteFilePaths;

        fileSystem = useUnixFtpServer
                ? (supportAbsoluteFilePaths ? unixFtpFileSystem : unixFtpFileSystemWithNoAbsolutePathSupport)
                : (supportAbsoluteFilePaths ? nonUnixFileSystem : nonUnixFtpFileSystemWithNoAbsolutePathSupport);
        multiClientFileSystem = useUnixFtpServer ? multiClientUnixFtpFileSystem : multiClientNonUnixFtpFileSystem;
    }

    @BeforeAll
    public static void setupClass() throws IOException {
        unixFtpServer = new FakeFtpServer();
        unixFtpServer.setSystemName("UNIX");
        unixFtpServer.setServerControlPort(0);

        nonUnixFtpServer = new FakeFtpServer();
        nonUnixFtpServer.setSystemName("UNIX");
        nonUnixFtpServer.setServerControlPort(0);

        FileSystem initFileSystem = new UnixFakeFileSystem();
        initFileSystem.add(new DirectoryEntry(HOME_DIR));
        unixFtpServer.setFileSystem(initFileSystem);
        nonUnixFtpServer.setFileSystem(initFileSystem);

        UserAccount userAccount = new UserAccount(USERNAME, PASSWORD, HOME_DIR);
        unixFtpServer.addUserAccount(userAccount);
        nonUnixFtpServer.addUserAccount(userAccount);

        unixFtpServer.setCommandHandler("LIST", new ListHiddenFilesCommandHandler(true));
        unixFtpServer.setCommandHandler("MDTM", new MDTMCommandHandler());
        nonUnixFtpServer.setCommandHandler("LIST", new ListHiddenFilesCommandHandler(false));
        nonUnixFtpServer.setCommandHandler("MDTM", new MDTMCommandHandler());

        unixFtpServer.start();
        nonUnixFtpServer.start();

        exceptionFactory = new ExceptionFactoryWrapper();
        unixFtpFileSystem = createFileSystem(unixFtpServer.getServerControlPort(), true);
        nonUnixFileSystem = createFileSystem(nonUnixFtpServer.getServerControlPort(), true);
        unixFtpFileSystemWithNoAbsolutePathSupport = createFileSystem(unixFtpServer.getServerControlPort(), false);
        nonUnixFtpFileSystemWithNoAbsolutePathSupport = createFileSystem(nonUnixFtpServer.getServerControlPort(), false);
        multiClientUnixFtpFileSystem = createFileSystem(unixFtpServer.getServerControlPort(), 3, true);
        multiClientNonUnixFtpFileSystem = createFileSystem(nonUnixFtpServer.getServerControlPort(), 3, true);
        multiClientUnixFtpFileSystemWithNoAbsolutePathSupport = createFileSystem(unixFtpServer.getServerControlPort(), 3, false);
        multiClientNonUnixFtpFileSystemWithNoAbsolutePathSupport = createFileSystem(nonUnixFtpServer.getServerControlPort(), 3, false);
    }

    @AfterAll
    public static void cleanupClass() throws IOException {
        unixFtpFileSystem.close();
        nonUnixFileSystem.close();
        unixFtpFileSystemWithNoAbsolutePathSupport.close();
        nonUnixFtpFileSystemWithNoAbsolutePathSupport.close();
        multiClientUnixFtpFileSystem.close();
        multiClientNonUnixFtpFileSystem.close();
        multiClientUnixFtpFileSystemWithNoAbsolutePathSupport.close();
        multiClientNonUnixFtpFileSystemWithNoAbsolutePathSupport.close();

        unixFtpServer.stop();
        unixFtpServer = null;

        nonUnixFtpServer.stop();
        nonUnixFtpServer = null;
    }

    private static FTPFileSystem createFileSystem(int port, boolean supportAbsoluteFilePaths) throws IOException {
        Map<String, ?> env = createEnv(supportAbsoluteFilePaths);
        return (FTPFileSystem) new FTPFileSystemProvider().newFileSystem(URI.create("ftp://localhost:" + port), env);
    }

    private static FTPFileSystem createFileSystem(int port, int clientConnectionCount, boolean supportAbsoluteFilePaths) throws IOException {
        Map<String, ?> env = createEnv(supportAbsoluteFilePaths).withClientConnectionCount(clientConnectionCount);
        return (FTPFileSystem) new FTPFileSystemProvider().newFileSystem(URI.create("ftp://localhost:" + port), env);
    }

    protected static FTPEnvironment createEnv(boolean supportAbsoluteFilePaths) {
        return new FTPEnvironment()
                .withCredentials(USERNAME, PASSWORD.toCharArray())
                .withClientConnectionCount(1)
                .withFTPFileStrategyFactory(supportAbsoluteFilePaths ? FTPFileStrategyFactory.AUTO_DETECT : FTPFileStrategyFactory.NON_UNIX)
                .withFileSystemExceptionFactory(exceptionFactory);
    }

    @BeforeEach
    public void setup() {
        fs = new ExtendedUnixFakeFileSystem();
        fs.add(new DirectoryEntry(HOME_DIR));

        unixFtpServer.setFileSystem(fs);
        nonUnixFtpServer.setFileSystem(fs);

        exceptionFactory.delegate = spy(DefaultFileSystemExceptionFactory.INSTANCE);
    }

    @AfterEach
    public void cleanup() {
        exceptionFactory.delegate = null;

        unixFtpServer.setFileSystem(null);
        nonUnixFtpServer.setFileSystem(null);
        fs = null;
    }

    protected final boolean useUnixFtpServer() {
        return useUnixFtpServer;
    }

    protected final boolean supportAbsoluteFilePaths() {
        return supportAbsoluteFilePaths;
    }

    protected final String getBaseUrl() {
        FakeFtpServer ftpServer = useUnixFtpServer ? unixFtpServer : nonUnixFtpServer;
        return "ftp://" + USERNAME + "@localhost:" + ftpServer.getServerControlPort();
    }

    protected final URI getURI() {
        FakeFtpServer ftpServer = useUnixFtpServer ? unixFtpServer : nonUnixFtpServer;
        return URI.create("ftp://localhost:" + ftpServer.getServerControlPort());
    }

    protected final FTPPath createPath(String path) {
        return new FTPPath(fileSystem, path);
    }

    protected final FTPPath createPath(FTPFileSystem fs, String path) {
        return new FTPPath(fs, path);
    }

    protected final FileSystemExceptionFactory getExceptionFactory() {
        return exceptionFactory.delegate;
    }

    protected final FileSystemEntry getFileSystemEntry(String path) {
        return fs.getEntry(path);
    }

    protected final FileEntry getFile(String path) {
        return getFileSystemEntry(path, FileEntry.class);
    }

    protected final DirectoryEntry getDirectory(String path) {
        return getFileSystemEntry(path, DirectoryEntry.class);
    }

    protected final SymbolicLinkEntry getSymLink(String path) {
        return getFileSystemEntry(path, SymbolicLinkEntry.class);
    }

    protected final <T extends FileSystemEntry> T getFileSystemEntry(String path, Class<T> cls) {
        FileSystemEntry entry = getFileSystemEntry(path);
        assertThat(entry, instanceOf(cls));
        return cls.cast(entry);
    }

    protected final FileEntry addFile(String path) {
        FileEntry file = new FileEntry(path);
        fs.add(file);
        return file;
    }

    protected final DirectoryEntry addDirectory(String path) {
        DirectoryEntry directory = new DirectoryEntry(path);
        fs.add(directory);
        return directory;
    }

    protected final SymbolicLinkEntry addSymLink(String path, FileSystemEntry target) {
        SymbolicLinkEntry symLink = new SymbolicLinkEntry(path, target);
        fs.add(symLink);
        return symLink;
    }

    protected final boolean delete(String path) {
        return fs.delete(path);
    }

    protected final int getChildCount(String path) {
        return fs.listFiles(path).size();
    }

    protected final byte[] getContents(FileEntry file) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = file.createInputStream()) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
        }
        return out.toByteArray();
    }

    protected final String getStringContents(FileEntry file) throws IOException {
        StringBuilder sb = new StringBuilder((int) file.getSize());
        try (Reader in = new InputStreamReader(file.createInputStream(), StandardCharsets.UTF_8)) {
            char[] buffer = new char[1024];
            int len;
            while ((len = in.read(buffer)) != -1) {
                sb.append(buffer, 0, len);
            }
        }
        return sb.toString();
    }

    protected final long getTotalSize() {
        return getTotalSize(fs.getEntry("/"));
    }

    private long getTotalSize(FileSystemEntry entry) {
        long size = entry.getSize();
        if (entry instanceof DirectoryEntry) {
            for (Object o : fs.listFiles(entry.getPath())) {
                size += getTotalSize((FileSystemEntry) o);
            }
        }
        return size;
    }

    private static class ExceptionFactoryWrapper implements FileSystemExceptionFactory {

        private FileSystemExceptionFactory delegate;

        @Override
        public FileSystemException createGetFileException(String file, int replyCode, String replyString) {
            return delegate.createGetFileException(file, replyCode, replyString);
        }

        @Override
        public FileSystemException createChangeWorkingDirectoryException(String directory, int replyCode, String replyString) {
            return delegate.createChangeWorkingDirectoryException(directory, replyCode, replyString);
        }

        @Override
        public FileSystemException createCreateDirectoryException(String directory, int replyCode, String replyString) {
            return delegate.createCreateDirectoryException(directory, replyCode, replyString);
        }

        @Override
        public FileSystemException createDeleteException(String file, int replyCode, String replyString, boolean isDirectory) {
            return delegate.createDeleteException(file, replyCode, replyString, isDirectory);
        }

        @Override
        public FileSystemException createNewInputStreamException(String file, int replyCode, String replyString) {
            return delegate.createNewInputStreamException(file, replyCode, replyString);
        }

        @Override
        public FileSystemException createNewOutputStreamException(String file, int replyCode, String replyString,
                Collection<? extends OpenOption> options) {
            return delegate.createNewOutputStreamException(file, replyCode, replyString, options);
        }

        @Override
        public FileSystemException createCopyException(String file, String other, int replyCode, String replyString) {
            return delegate.createCopyException(file, other, replyCode, replyString);
        }

        @Override
        public FileSystemException createMoveException(String file, String other, int replyCode, String replyString) {
            return delegate.createMoveException(file, other, replyCode, replyString);
        }
    }
}
