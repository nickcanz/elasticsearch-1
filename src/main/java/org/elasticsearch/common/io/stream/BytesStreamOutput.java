/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.common.io.stream;

import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.bytes.PagedBytesReference;
import org.elasticsearch.common.io.BytesStream;
import org.elasticsearch.common.util.BigArrays;
import org.elasticsearch.common.util.ByteArray;

import java.io.IOException;

/**
 * A @link {@link StreamOutput} that uses{@link BigArrays} to acquire pages of
 * bytes, which avoids frequent reallocation & copying of the internal data.
 */
public class BytesStreamOutput extends StreamOutput implements BytesStream {

    protected final BigArrays bigarrays;

    protected ByteArray bytes;
    protected int count;

    /**
     * Create a non recycling {@link BytesStreamOutput} with 1 initial page acquired.
     */
    public BytesStreamOutput() {
        this(BigArrays.PAGE_SIZE_IN_BYTES);
    }

    /**
     * Create a non recycling {@link BytesStreamOutput} with enough initial pages acquired
     * to satisfy the capacity given by expected size.
     * 
     * @param expectedSize the expected maximum size of the stream in bytes.
     */
    public BytesStreamOutput(int expectedSize) {
        this(expectedSize, BigArrays.NON_RECYCLING_INSTANCE);
    }

    protected BytesStreamOutput(int expectedSize, BigArrays bigarrays) {
        this.bigarrays = bigarrays;
        this.bytes = bigarrays.newByteArray(expectedSize);
    }

    @Override
    public long position() throws IOException {
        return count;
    }

    @Override
    public void writeByte(byte b) throws IOException {
        ensureCapacity(count+1);
        bytes.set(count, b);
        count++;
    }

    @Override
    public void writeBytes(byte[] b, int offset, int length) throws IOException {
        // nothing to copy
        if (length == 0) {
            return;
        }

        // illegal args: offset and/or length exceed array size
        if (b.length < (offset + length)) {
            throw new IllegalArgumentException("Illegal offset " + offset + "/length " + length + " for byte[] of length " + b.length);
        }

        // get enough pages for new size
        ensureCapacity(count+length);

        // bulk copy
        bytes.set(count, b, offset, length);

        // advance
        count += length;
    }

    @Override
    public void reset() {
        // shrink list of pages
        if (bytes.size() > BigArrays.PAGE_SIZE_IN_BYTES) {
            bytes = bigarrays.resize(bytes, BigArrays.PAGE_SIZE_IN_BYTES);
        }

        // go back to start
        count = 0;
    }

    @Override
    public void flush() throws IOException {
        // nothing to do
    }

    @Override
    public void seek(long position) throws IOException {
        if (position > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("position " + position + " > Integer.MAX_VALUE");
        }

        count = (int)position;
        ensureCapacity(count);
    }

    public void skip(int length) {
        count += length;
        ensureCapacity(count);
    }

    @Override
    public void close() throws IOException {
        // empty for now.
    }

    /**
     * Returns the current size of the buffer.
     * 
     * @return the value of the <code>count</code> field, which is the number of valid
     *         bytes in this output stream.
     * @see java.io.ByteArrayOutputStream#count
     */
    public int size() {
        return count;
    }

    @Override
    public BytesReference bytes() {
        return new PagedBytesReference(bigarrays, bytes, count);
    }

    /**
     * Returns the number of bytes used by the underlying {@link org.elasticsearch.common.util.ByteArray}
     * @see org.elasticsearch.common.util.ByteArray#ramBytesUsed()
     */
    public long ramBytesUsed() {
        return bytes.ramBytesUsed();
    }

    private void ensureCapacity(int offset) {
        bytes = bigarrays.grow(bytes, offset);
    }

}
