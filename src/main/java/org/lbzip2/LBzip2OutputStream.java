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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author Mikolaj Izdebski
 */
public class LBzip2OutputStream
    extends OutputStream
{
    private final OutputStream os;

    private final byte[] buf1 = new byte[1];

    private final Collector col;

    private final EntropyCoder ec;

    private final Encoder enc;

    private boolean pending;

    private int combined_crc;

    public LBzip2OutputStream( OutputStream os, int max_block_size, int cluster_factor )
        throws IOException
    {
        this.os = os;

        col = new Collector( max_block_size );
        col.reset();

        ec = new EntropyCoder( cluster_factor );

        enc = new Encoder( col, ec );

        writeHeader( max_block_size );
    }

    @Override
    public void write( int b )
        throws IOException
    {
        buf1[0] = (byte) b;
        write( buf1, 0, 1 );
    }

    private void writeHeader( int max_block_size )
        throws IOException
    {
        byte[] buffer = new byte[4];
        buffer[0] = 0x42;
        buffer[1] = 0x5A;
        buffer[2] = 0x68;
        buffer[3] = (byte) ( 0x30 + ( max_block_size + 100000 - 1 ) / 100000 );
        os.write( buffer );
    }

    private void writeTrailer()
        throws IOException
    {
        byte[] buffer = new byte[10];
        buffer[0] = 0x17;
        buffer[1] = 0x72;
        buffer[2] = 0x45;
        buffer[3] = 0x38;
        buffer[4] = 0x50;
        buffer[5] = (byte) 0x90;
        buffer[6] = (byte) ( combined_crc >> 24 );
        buffer[7] = (byte) ( combined_crc >> 16 );
        buffer[8] = (byte) ( combined_crc >> 8 );
        buffer[9] = (byte) combined_crc;
        os.write( buffer );
    }

    @Override
    public void write( byte[] b, int off, int avail )
        throws IOException
    {
        int[] p_buf_sz = new int[1];

        while ( avail > 0 )
        {
            pending = true;
            p_buf_sz[0] = avail;
            boolean full = col.collect( b, off, p_buf_sz );
            off += avail - p_buf_sz[0];
            avail = p_buf_sz[0];

            if ( full )
            {
                flushBlock();
            }
        }
    }

    private void flushBlock()
        throws IOException
    {
        if ( pending )
        {
            int[] p_crc = new int[1];
            col.finish();
            int size = enc.encode( p_crc );
            combined_crc = ( ( combined_crc << 1 ) ^ ( combined_crc >>> 31 ) ^ p_crc[0] ^ -1 );
            byte[] buf = new byte[size];
            enc.transmit( buf );
            os.write( buf );
            pending = false;
            col.reset();
        }
    }

    @Override
    public void flush()
        throws IOException
    {
        flushBlock();
        os.flush();
    }

    @Override
    public void close()
        throws IOException
    {
        flushBlock();
        writeTrailer();
        os.close();
    }

    public static void main( String[] args )
    {
        try
        {
            InputStream is = System.in;
            OutputStream os = new LBzip2OutputStream( System.out, 900000, 10 );
            byte[] buf = new byte[4096];
            int n;
            while ( ( n = is.read( buf ) ) > 0 )
            {
                os.write( buf, 0, n );
            }
            is.close();
            os.close();
        }
        catch ( Throwable e )
        {
            e.printStackTrace();
        }
    }
}
