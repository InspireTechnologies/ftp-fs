/*
 * FTPFileSystemDirectoryStreamTest.java
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
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
@SuppressWarnings({ "nls", "javadoc" })
public class FTPFileSystemDirectoryStreamTest extends AbstractFTPFileSystemTest {

    public FTPFileSystemDirectoryStreamTest(boolean useUnixFtpServer, boolean supportAbsoluteFilePaths) {
        super(useUnixFtpServer, supportAbsoluteFilePaths);
    }

    @Parameters(name = "Use UNIX FTP server: {0}; support absolute file paths: {1}")
    public static List<Object[]> getParameters() {
        Object[][] parameters = {
            { true, true, },
            { true, false, },
            { false, true, },
            { false, false, },
        };
        return Arrays.asList(parameters);
    }

    @Test
    public void testIterator() throws IOException {
        final int count = 100;

        List<Matcher<? super String>> matchers = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            matchers.add(equalTo("file" + i));
            addFile("/foo/file" + i);
        }

        List<String> names = new ArrayList<>();
        try (DirectoryStream<Path> stream = fileSystem.newDirectoryStream(createPath("/foo"), AcceptAllFilter.INSTANCE)) {
            for (Iterator<Path> iterator = stream.iterator(); iterator.hasNext(); ) {
                names.add(iterator.next().getFileName().toString());
            }
        }
        assertThat(names, containsInAnyOrder(matchers));
    }

    @Test
    public void testFilteredIterator() throws IOException {
        final int count = 100;

        List<Matcher<? super String>> matchers = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (i % 2 == 1) {
                matchers.add(equalTo("file" + i));
            }
            addFile("/foo/file" + i);
        }

        List<String> names = new ArrayList<>();
        Filter<Path> filter = new PatternFilter("file\\d*[13579]");
        try (DirectoryStream<Path> stream = fileSystem.newDirectoryStream(createPath("/foo"), filter)) {
            for (Iterator<Path> iterator = stream.iterator(); iterator.hasNext(); ) {
                names.add(iterator.next().getFileName().toString());
            }
        }
        assertThat(names, containsInAnyOrder(matchers));
    }

    @Test
    public void testCloseWhileIterating() throws IOException {
        final int count = 100;

        // there is no guaranteed order, just a count
        for (int i = 0; i < count; i++) {
            addFile("/foo/file" + i);
        }
        Matcher<String> matcher = new TypeSafeDiagnosingMatcher<String>() {
            private final Pattern pattern = Pattern.compile("file\\d+");

            @Override
            protected boolean matchesSafely(String item, Description mismatchDescription) {
                return item != null && pattern.matcher(item).matches();
            }

            @Override
            public void describeTo(Description description) {
                description
                        .appendText("matches ")
                        .appendValue(pattern);
            }
        };
        int expectedCount = count / 2;

        List<String> names = new ArrayList<>();
        try (DirectoryStream<Path> stream = fileSystem.newDirectoryStream(createPath("/foo"), AcceptAllFilter.INSTANCE)) {

            int index = 0;
            for (Iterator<Path> iterator = stream.iterator(); iterator.hasNext(); ) {
                if (++index == count / 2) {
                    stream.close();
                }
                names.add(iterator.next().getFileName().toString());
            }
        }
        assertEquals(expectedCount, names.size());
        assertThat(names, everyItem(matcher));
    }

    @Test(expected = IllegalStateException.class)
    public void testIteratorAfterClose() throws IOException {
        try (DirectoryStream<Path> stream = fileSystem.newDirectoryStream(createPath("/"), AcceptAllFilter.INSTANCE)) {
            stream.close();
            stream.iterator();
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testIteratorAfterIterator() throws IOException {
        boolean iteratorCalled = false;
        try (DirectoryStream<Path> stream = fileSystem.newDirectoryStream(createPath("/"), AcceptAllFilter.INSTANCE)) {
            stream.iterator();
            iteratorCalled = true;
            stream.iterator();
        } finally {
            assertTrue(iteratorCalled);
        }
    }

    @Test
    public void testDeleteWhileIterating() throws IOException {
        final int count = 100;

        List<Matcher<? super String>> matchers = new ArrayList<>();
        addDirectory("/foo");
        for (int i = 0; i < count; i++) {
            matchers.add(equalTo("file" + i));
            addFile("/foo/file" + i);
        }

        List<String> names = new ArrayList<>();
        try (DirectoryStream<Path> stream = fileSystem.newDirectoryStream(createPath("/foo"), AcceptAllFilter.INSTANCE)) {

            int index = 0;
            for (Iterator<Path> iterator = stream.iterator(); iterator.hasNext(); ) {
                if (++index < count / 2) {
                    delete("/foo");
                }
                names.add(iterator.next().getFileName().toString());
            }
        }
        assertThat(names, containsInAnyOrder(matchers));
    }

    @Test
    public void testDeleteChildrenWhileIterating() throws IOException {
        final int count = 100;

        List<Matcher<? super String>> matchers = new ArrayList<>();
        addDirectory("/foo");
        for (int i = 0; i < count; i++) {
            matchers.add(equalTo("file" + i));
            addFile("/foo/file" + i);
        }

        List<String> names = new ArrayList<>();
        try (DirectoryStream<Path> stream = fileSystem.newDirectoryStream(createPath("/foo"), AcceptAllFilter.INSTANCE)) {

            int index = 0;
            for (Iterator<Path> iterator = stream.iterator(); iterator.hasNext(); ) {
                if (++index < count / 2) {
                    for (int i = 0; i < count; i++) {
                        delete("/foo/file" + i);
                    }
                    assertEquals(0, getChildCount("/foo"));
                }
                names.add(iterator.next().getFileName().toString());
            }
        }
        assertThat(names, containsInAnyOrder(matchers));
    }

    @Test
    public void testDeleteBeforeIterator() throws IOException {
        final int count = 100;

        List<Matcher<? super String>> matchers = new ArrayList<>();
        addDirectory("/foo");
        for (int i = 0; i < count; i++) {
            // the entries are collected before the iteration starts
            matchers.add(equalTo("file" + i));
            addFile("/foo/file" + i);
        }

        List<String> names = new ArrayList<>();
        try (DirectoryStream<Path> stream = fileSystem.newDirectoryStream(createPath("/foo"), AcceptAllFilter.INSTANCE)) {

            delete("/foo");
            for (Iterator<Path> iterator = stream.iterator(); iterator.hasNext(); ) {
                names.add(iterator.next().getFileName().toString());
            }
        }
        assertThat(names, containsInAnyOrder(matchers));
    }

    @Test(expected = DirectoryIteratorException.class)
    public void testThrowWhileIterating() throws IOException {
        addFile("/foo");

        try (DirectoryStream<Path> stream = fileSystem.newDirectoryStream(createPath("/"), ThrowingFilter.INSTANCE)) {
            for (Iterator<Path> iterator = stream.iterator(); iterator.hasNext(); ) {
                iterator.next();
            }
        }
    }

    private static final class AcceptAllFilter implements Filter<Path> {

        private static final AcceptAllFilter INSTANCE = new AcceptAllFilter();

        @Override
        public boolean accept(Path entry) {
            return true;
        }
    }

    private static final class PatternFilter implements Filter<Path> {

        private final Pattern pattern;

        private PatternFilter(String regex) {
            pattern = Pattern.compile(regex);
        }

        @Override
        public boolean accept(Path entry) {
            return pattern.matcher(entry.getFileName().toString()).matches();
        }
    }

    private static final class ThrowingFilter implements Filter<Path> {

        private static final ThrowingFilter INSTANCE = new ThrowingFilter();

        @Override
        public boolean accept(Path entry) throws IOException {
            throw new IOException();
        }
    }
}
