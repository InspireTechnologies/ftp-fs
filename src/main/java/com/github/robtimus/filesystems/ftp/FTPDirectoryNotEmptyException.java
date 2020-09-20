/*
 * FTPDirectoryNotEmptyException.java
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

import java.nio.file.DirectoryNotEmptyException;

/**
 * An exception that is thrown if an FTP command does not execute successfully because a directory is not empty.
 *
 * @author Rob Spoor
 */
public class FTPDirectoryNotEmptyException extends DirectoryNotEmptyException implements FTPResponse {

    private static final long serialVersionUID = -4185269855522877588L;

    private final int replyCode;
    private final String replyString;

    /**
     * Creates a new {@code FTPDirectoryNotEmptyException}.
     *
     * @param file A string identifying the file, or {@code null} if not known.
     * @param replyCode The integer value of the reply code of the last FTP reply that triggered this exception.
     * @param replyString The entire text from the last FTP response that triggered this exception. It will be used as the exception's reason.
     */
    public FTPDirectoryNotEmptyException(String file, int replyCode, String replyString) {
        super(file);
        this.replyCode = replyCode;
        this.replyString = replyString;
    }

    @Override
    public int getReplyCode() {
        return replyCode;
    }

    @Override
    public String getReplyString() {
        return replyString;
    }

    @Override
    public String getReason() {
        return getReplyString();
    }

    @Override
    public String getMessage() {
        return getReplyString();
    }
}
