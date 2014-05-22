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
public class StreamComposer
    extends CompoundDataSource
{
    private int combinedCrc;

    private final int maxBlockSize;

    public StreamComposer()
    {
        this( MAX_BLOCK_SIZE );
    }

    public StreamComposer( int maxBlockSize )
    {
        this.maxBlockSize = maxBlockSize;

        byte[] buffer = new byte[4];
        buffer[0] = 0x42;
        buffer[1] = 0x5A;
        buffer[2] = 0x68;
        buffer[3] = (byte) ( 0x30 + ( maxBlockSize + 100000 - 1 ) / 100000 );
        addSource( new ByteArrayDataSource( buffer ) );
    }

    public void addBlock( CompressedBlock block )
    {
        if ( block.blockSize > maxBlockSize )
            throw new IllegalArgumentException( "Block too large to be added to this stream" );

        combinedCrc = ( ( combinedCrc << 1 ) ^ ( combinedCrc >>> 31 ) ^ block.encoder.block_crc ^ -1 );

        byte[] buffer = new byte[block.compressedSize];
        block.encoder.transmit( buffer );
        addSource( new ByteArrayDataSource( buffer ) );
    }

    public void finish()
    {
        byte[] buffer = new byte[10];
        buffer[0] = 0x17;
        buffer[1] = 0x72;
        buffer[2] = 0x45;
        buffer[3] = 0x38;
        buffer[4] = 0x50;
        buffer[5] = (byte) 0x90;
        buffer[6] = (byte) ( combinedCrc >> 24 );
        buffer[7] = (byte) ( combinedCrc >> 16 );
        buffer[8] = (byte) ( combinedCrc >> 8 );
        buffer[9] = (byte) combinedCrc;
        addSource( new ByteArrayDataSource( buffer ) );
    }
}
