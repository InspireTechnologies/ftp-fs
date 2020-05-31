/*
 * FTPFileSystemLoggingTest.java
 * Copyright 2019 Rob Spoor
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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.log4j.Appender;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.varia.NullAppender;
import org.hamcrest.Description;
import org.hamcrest.Matchers;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@SuppressWarnings({ "nls", "javadoc" })
public class FTPFileSystemLoggingTest extends AbstractFTPFileSystemTest {

    private static Logger logger;
    private static Level originalLevel;
    private static List<Appender> originalAppenders;
    private static List<Appender> originalRootAppenders;

    private Appender appender;

    public FTPFileSystemLoggingTest() {
        // there's no need to test the FTP file system itself, so the logging
        super(true, true);
    }

    @BeforeAll
    public static void setupLogging() {
        logger = LogManager.getLogger(FTPClientPool.class);
        originalLevel = logger.getLevel();
        logger.setLevel(Level.TRACE);
        originalAppenders = getAllAppenders(logger);
        logger.removeAllAppenders();

        Logger root = LogManager.getRootLogger();
        originalRootAppenders = getAllAppenders(root);
        root.removeAllAppenders();
    }

    private static List<Appender> getAllAppenders(Logger l) {
        List<Appender> appenders = new ArrayList<>();
        for (@SuppressWarnings("unchecked") Enumeration<Appender> e = l.getAllAppenders(); e.hasMoreElements(); ) {
            appenders.add(e.nextElement());
        }
        return appenders;
    }

    @AfterAll
    public static void clearLogging() {
        logger.setLevel(originalLevel);
        for (Appender appender : originalAppenders) {
            logger.addAppender(appender);
        }

        Logger root = LogManager.getRootLogger();
        for (Appender appender : originalRootAppenders) {
            root.addAppender(appender);
        }
    }

    @BeforeEach
    public void setupAppender() {
        appender = spy(new NullAppender());
        logger.addAppender(appender);
    }

    @AfterEach
    public void clearAppender() {
        logger.removeAppender(appender);
    }

    @Test
    public void testLogging() throws IOException {
        URI uri = getURI();
        try (FileSystem fs = FileSystems.newFileSystem(uri, createEnv(true))) {
            FTPFileSystemProvider.keepAlive(fs);
        }
        URI brokenUri;
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            brokenUri = URI.create("ftp://localhost:" + serverSocket.getLocalPort());
        }
        assertThrows(IOException.class, () -> FileSystems.newFileSystem(brokenUri, createEnv(true)));

        ArgumentCaptor<LoggingEvent> captor = ArgumentCaptor.forClass(LoggingEvent.class);
        verify(appender, atLeast(1)).doAppend(captor.capture());
        List<Object> debugMessages = new ArrayList<>();
        List<Object> traceMessages = new ArrayList<>();
        Set<String> names = new HashSet<>();
        Set<Level> levels = new HashSet<>();
        List<Throwable> thrown = new ArrayList<>();
        for (LoggingEvent event : captor.getAllValues()) {
            names.add(event.getLoggerName());
            levels.add(event.getLevel());
            if (Level.DEBUG.equals(event.getLevel())) {
                debugMessages.add(event.getMessage());
            } else if (Level.TRACE.equals(event.getLevel())) {
                traceMessages.add(event.getMessage());
            }
            if (event.getThrowableInformation() != null) {
                thrown.add(event.getThrowableInformation().getThrowable());
            }
        }

        assertThat(names, contains(FTPClientPool.class.getName()));
        assertThat(levels, containsInAnyOrder(Level.DEBUG, Level.TRACE));

        String hostname = uri.getHost();
        int port = uri.getPort();
        if (port == -1) {
            assertThat(debugMessages, hasItem("Creating FTPClientPool to " + hostname + " with poolSize 1 and poolWaitTimeout 0"));
            assertThat(debugMessages, hasItem("Created FTPClientPool to " + hostname + " with poolSize 1"));
        } else {
            assertThat(debugMessages, hasItem("Creating FTPClientPool to " + hostname + ":" + port + " with poolSize 1 and poolWaitTimeout 0"));
            assertThat(debugMessages, hasItem("Created FTPClientPool to " + hostname + ":" + port + " with poolSize 1"));
        }
        assertThat(debugMessages, hasItem("Failed to create FTPClientPool, disconnecting all created clients"));
        assertThat(debugMessages, hasItem(new RegexMatcher("Created new client with id 'client-\\d+' \\(pooled: true\\)")));
        assertThat(debugMessages, hasItem(new RegexMatcher("Took client 'client-\\d+' from pool, current pool size: 0")));
        assertThat(debugMessages, hasItem(new RegexMatcher("Reference count for client 'client-\\d+' increased to 1")));
        assertThat(debugMessages, hasItem(new RegexMatcher("Reference count for client 'client-\\d+' decreased to 0")));
        assertThat(debugMessages, hasItem(new RegexMatcher("Returned client 'client-\\d+' to pool, current pool size: 1")));
        assertThat(debugMessages, hasItem("Drained pool for keep alive"));
        assertThat(debugMessages, hasItem("Drained pool for close"));
        assertThat(debugMessages, hasItem(new RegexMatcher("Disconnected client 'client-\\d+'")));

        assertThat(traceMessages, hasItem("FTP command: NOOP"));
        assertThat(traceMessages, hasItem("FTP reply: 200 NOOP completed."));

        assertThat(thrown, contains(Matchers.<Throwable>instanceOf(IOException.class)));
    }

    private static final class RegexMatcher extends TypeSafeDiagnosingMatcher<String> {

        private Pattern pattern;

        private RegexMatcher(String regex) {
            pattern = Pattern.compile(regex);
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("matching ").appendValue(pattern);
        }

        @Override
        protected boolean matchesSafely(String actual, Description mismatchDescription) {
            if (!pattern.matcher(actual).matches()) {
                mismatchDescription.appendText("was ").appendValue(actual);
                return false;
            }
            return true;
        }
    }
}
