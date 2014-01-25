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
package org.lbzip2.impl;

import static org.lbzip2.impl.Status.FINISH;
import static org.lbzip2.impl.Status.MORE;
import static org.lbzip2.impl.Status.OK;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.lbzip2.StreamFormatException;

public class MBC
{
    private final InputStream in;

    private final OutputStream out;

    private final BitStream bs = new BitStream();

    public MBC( InputStream in, OutputStream out )
    {
        this.in = in;
        this.out = out;

        bs.ptr = new byte[1];
        bs.off = 1;
        bs.len = 1;
    }

    private static void err( String msg )
        throws StreamFormatException
    {
        throw new StreamFormatException( msg );
    }

    /* Read a single byte from stdin. */
    private int read()
        throws IOException
    {
        return in.read();
    }

    /* Print an error message and terminate. */
    private static void bad()
        throws StreamFormatException
    {
        err( "Data error" );
    }

    private final Decoder ds = new Decoder();

    private int get( int n )
        throws IOException
    {
        Status s;
        while ( ( s = bs.need( n ) ) == MORE )
        {
            if ( in.read( bs.ptr ) < 0 )
                bs.eof = true;
            else
                bs.off = 0;
        }
        if ( s == FINISH )
            bad();

        int x = bs.peek( n );
        bs.dump( n );
        return x;
    }

    private void decode_and_emit()
        throws StreamFormatException, IOException
    {
        if ( ds.bwt_idx >= ds.block_size )
            bad();

        ds.decode();

        byte[] buf = new byte[4096];
        int[] len = new int[1];

        Status status;
        do
        {
            len[0] = buf.length;
            status = ds.emit( buf, 0, len );
            out.write( buf, 0, buf.length - len[0] );
        }
        while ( status == MORE );
        assert ( status == OK );
    }

    /* Parse stream and bock headers, decompress any blocks found. */
    public void expand()
        throws StreamFormatException, IOException
    {
        int t, c;
        if ( get( 24 ) != 0x425A68 )
            bad();
        t = ( get( 8 ) - 0x31 ) & 0xFF;
        if ( t >= 9 )
            bad();
        do
        {
            Retriever r = new Retriever();
            r.setMbs( 100000 * ( t + 1 ) );
            c = 0;
            while ( ( t = get( 16 ) ) == 0x3141 )
            {
                if ( get( 32 ) != 0x59265359 )
                    bad();
                t = get( 32 );

                Status s;
                while ( ( s = r.retr( ds, bs ) ) == MORE )
                {
                    if ( in.read( bs.ptr ) < 0 )
                        bs.eof = true;
                    else
                        bs.off = 0;
                }
                if ( s == FINISH )
                    bad();

                decode_and_emit();
                if ( ds.crc != t )
                    bad();
                c = ( c << 1 ) ^ ( c >>> 31 ) ^ t;
            }
            if ( t != 0x1772 )
                bad();
            if ( get( 32 ) != 0x45385090 )
                bad();
            if ( get( 32 ) != c )
                bad();
            bs.align();
        }
        while ( read() == 0x42 && read() == 0x5A && read() == 0x68 && ( t = get( 8 ) - 0x31 ) < 9 );
    }
}
