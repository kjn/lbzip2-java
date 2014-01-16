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

    public MBC( InputStream in, OutputStream out )
    {
        this.in = in;
        this.out = out;
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

    private long bb; /* the bit-buffer (left-justified) */

    private int bk; /* number of bits remaining in the `bb' bit-buffer */

    private final Decoder ds = new Decoder();

    private int peek( int n )
    {
        return (int) ( bb >>> ( 64 - n ) );
    }

    private void dump( int n )
    {
        bb <<= n;
        bk -= n;
    }

    /* Read and return `n' bits from the input stream. `n' must be <= 32. */
    private int get( int n )
        throws StreamFormatException, IOException
    {
        while ( bk < n )
        {
            long c = in.read();
            if ( c < 0 )
                bad();
            bk += 8;
            bb += c << ( 64 - bk );
        }
        int x = peek( n );
        dump( n );
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
            Retriever r = new Retriever( 100000 * ( t + 1 ) );
            c = 0;
            while ( ( t = get( 16 ) ) == 0x3141 )
            {
                if ( get( 32 ) != 0x59265359 )
                    bad();
                t = get( 32 );

                r.bb = bb;
                r.bk = bk;
                byte[] buf = new byte[1];
                int s;
                do
                {
                    s = in.read( buf );
                    if ( s < 0 )
                        bad();
                }
                while ( r.retr( ds, buf, 0, s ) == MORE );
                bb = r.bb;
                bk = r.bk;

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
            bk = 0;
        }
        while ( read() == 0x42 && read() == 0x5A && read() == 0x68 && ( t = get( 8 ) - 0x31 ) < 9 );
    }

    public static void main( String[] args )
    {
        try
        {
            MBC mbc = new MBC( System.in, System.out );
            mbc.expand();
        }
        catch ( StreamFormatException e )
        {
            System.err.println( "Data error" );
            System.exit( 1 );
        }
        catch ( IOException e )
        {
            e.printStackTrace();
            System.exit( 2 );
        }
    }
}
