/*-
 * Copyright (c) 2014 Mikolaj Izdebski
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
package org.lbzip2;

import static org.lbzip2.Constants.MAX_BLOCK_SIZE;

/**
 * @author Mikolaj Izdebski
 */
public class UncompressedBlock
    extends AbstractDataSink
{
    final Collector collector;

    /**
     * {@code true} iff there is some data pending in this block, i.e. the block is not empty.
     */
    private boolean pending;

    /**
     * {@code true} iff no more data can be added to this block, i.e. the block is full.
     */
    private boolean full;

    private final int[] p_buf_sz = new int[1];

    public UncompressedBlock()
    {
        this( MAX_BLOCK_SIZE );
    }

    public UncompressedBlock( int maxBlockSize )
    {
        collector = new Collector( maxBlockSize );
    }

    /**
     * Check whether this block is empty.
     * 
     * @return {@code false} if there is some data pending in this block, {@code true} otherwise.
     */
    public boolean isEmpty()
    {
        return !pending;
    }

    /**
     * Check whether this block is full.
     * 
     * @return {@code true} if no more data can be added to this block, {@code false} otherwise.
     */
    public boolean isFull()
    {
        return full;
    }

    public int write( byte[] buf, int off, int len )
    {
        p_buf_sz[0] = len;

        if ( len > 0 )
        {
            pending = true;
            full = collector.collect( buf, off, p_buf_sz );
        }

        return len - p_buf_sz[0];
    }

    public CompressedBlock compress()
    {
        collector.finish();
        CompressedBlock compressedBlock = new CompressedBlock( this );
        collector.reset();
        pending = false;
        return compressedBlock;

    }
}
