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

import static org.lbzip2.impl.Constants.MAX_CODE_LENGTH;
import static org.lbzip2.impl.Constants.crc_table;
import static org.lbzip2.impl.Constants.rand_table;
import static org.lbzip2.impl.PrefixDecoder.EOB;
import static org.lbzip2.impl.PrefixDecoder.HUFF_START_WIDTH;
import static org.lbzip2.impl.PrefixDecoder.RUN_A;
import static org.lbzip2.impl.Unsigned.uge;

import java.io.IOException;

import org.lbzip2.StreamFormatException;

public class MBC
{
    private static void err( String msg )
        throws StreamFormatException
    {
        throw new StreamFormatException( msg );
    }

    /* Read a single byte from stdin. */
    private static int read()
        throws IOException
    {
        return System.in.read();
    }

    /* Write a single byte to stdout. */
    private static void write( int c )
    {
        System.out.write( c );
    }

    /* Print an error message and terminate. */
    private static void bad()
        throws StreamFormatException
    {
        err( "Data error" );
    }

    private long bb; /* the bit-buffer (left-justified) */

    private int bk; /* number of bits remaining in the `bb' bit-buffer */

    private final int[] tt = new int[900000]; /* IBWT linked cyclic list */

    private int crc; /* CRC32 computed so far */

    private int mbs; /* maximal block size (100k-900k in 100k steps) */

    private boolean rnd; /* is current block randomized? (0 or 1) */

    private int bs; /* current block size (1-900000) */

    private int idx; /* BWT primary index (0-899999) */

    private int as; /*
                     * alphabet size (number of distinct prefix codes, 3-258)
                     */

    private int nt; /*
                     * number of prefix trees used for current block (2-6)
                     */

    private int ns; /* number of selectors (1-32767) */

    private int nm; /* number of MTF values */

    private final byte[] blk = new byte[900000]; /* reconstructed block */

    private final byte[][] len = new byte[6][259]; /*
                                                    * code lengths for different trees (element 258 is a sentinel)
                                                    */

    private final byte[] sel = new byte[32767]; /* selector MTF values */

    private final byte[] mtf = new byte[256]; /* IMTF register */

    private final PrefixDecoder pd = new PrefixDecoder();

    private final short[] mv = new short[900050]; /*
                                                   * MTF values (elements 900000-900049 are sentinels)
                                                   */

    private void need( int n )
        throws StreamFormatException, IOException
    {
        while ( bk < n )
        {
            long c = System.in.read();
            if ( c < 0 )
                bad();
            bk += 8;
            bb += c << ( 64 - bk );
        }
    }

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
        need( n );
        int x = peek( n );
        dump( n );
        return x;
    }

    /* Decode a single prefix code. The algorithm used is naive and slow. */
    private short get_sym()
        throws StreamFormatException, IOException
    {
        need( MAX_CODE_LENGTH );
        int x = pd.start[peek( HUFF_START_WIDTH )];
        int k = x & 0x1F;
        short s;

        if ( k <= HUFF_START_WIDTH )
        {
            /* Use look-up table in average case. */
            s = (short) ( x >> 5 );
        }
        else
        {
            /*
             * Code length exceeds HUFF_START_WIDTH, use canonical prefix decoding algorithm instead of look-up table.
             */
            while ( uge( bb, pd.base[k + 1] ) )
                k++;
            s = pd.perm[pd.count[k] + (int) ( ( bb - pd.base[k] ) >>> ( 64 - k ) )];
        }

        dump( k );
        return s;
    }

    /* Retrieve bitmap. */
    private void bmp()
        throws StreamFormatException, IOException
    {
        int i, j;
        short b = (short) get( 16 );
        as = 0;
        for ( i = 0; i < 16; i++ )
        {
            if ( b < 0 )
            {
                short s = (short) get( 16 );
                for ( j = 0; j < 16; j++ )
                {
                    if ( s < 0 )
                        mtf[as++] = (byte) ( 16 * i + j );
                    s *= 2;
                }
            }
            b *= 2;
        }
        as += 2;
    }

    /* Retrieve selector MTF values. */
    private void smtf()
        throws StreamFormatException, IOException
    {
        int g;
        for ( g = 0; g < ns; g++ )
        {
            sel[g] = 0;
            while ( sel[g] < nt && get( 1 ) != 0 )
                sel[g]++;
            if ( sel[g] == nt )
                bad();
        }
        if ( ns > 18001 )
            ns = 18001;
    }

    /* Retrieve code lengths. */
    private void trees()
        throws StreamFormatException, IOException
    {
        int t, s;
        for ( t = 0; t < nt; t++ )
        {
            len[t][0] = (byte) get( 5 );
            for ( s = 0; s < as; s++ )
            {
                if ( len[t][s] < 1 || len[t][s] > 20 )
                    bad();
                while ( get( 1 ) != 0 )
                {
                    len[t][s] += 1 - 2 * get( 1 );
                    if ( len[t][s] < 1 || len[t][s] > 20 )
                        bad();
                }
                len[t][s + 1] = len[t][s];
            }
        }
    }

    /* Retrieve block MTF values. */
    private void data()
        throws StreamFormatException, IOException
    {
        int g, i, t;
        int[] m = new int[6];
        for ( i = 0; i < 6; i++ )
            m[i] = i;
        nm = 0;
        for ( g = 0; g < ns; g++ )
        {
            i = sel[g];
            t = m[i];
            while ( i-- > 0 )
                m[i + 1] = m[i];
            m[0] = t;
            pd.make_tree( len[t], as );
            for ( i = 0; i < 50; i++ )
                if ( ( mv[nm++] = get_sym() ) == EOB )
                    return;
        }
        bad();
    }

    /* Retrieve block. */
    private void retr()
        throws StreamFormatException, IOException
    {
        rnd = get( 1 ) != 0;
        idx = get( 24 );
        bmp();
        nt = get( 3 );
        if ( nt < 2 || nt > 6 )
            bad();
        ns = get( 15 );
        smtf();
        trees();
        data();
    }

    /* Apply IMTF transformation. */
    private void imtf()
        throws StreamFormatException
    {
        int i, s, r, h;
        byte t;
        bs = r = h = 0;
        for ( i = 0; i < nm; i++ )
        {
            s = mv[i];
            if ( s >= RUN_A )
            {
                r += 1 << ( h + s - RUN_A );
                h++;
                if ( r < 0 )
                    bad();
            }
            else
            {
                if ( bs + r > mbs )
                    bad();
                while ( r-- != 0 )
                    tt[bs++] = mtf[0] & 0xFF;
                if ( s == EOB )
                    break;
                t = mtf[s];
                while ( s-- > 0 )
                    mtf[s + 1] = mtf[s];
                mtf[0] = t;
                h = 0;
                r = 1;
            }
        }
    }

    /* Apply IBWT transformation. */
    private void ibwt()
        throws StreamFormatException
    {
        int i, c;
        int[] f = new int[256];
        if ( idx >= bs )
            bad();
        for ( i = 0; i < 256; i++ )
            f[i] = 0;
        for ( i = 0; i < bs; i++ )
            f[tt[i]]++;
        for ( i = c = 0; i < 256; i++ )
            f[i] = ( c += f[i] ) - f[i];
        for ( i = 0; i < bs; i++ )
            tt[f[tt[i] & 0xFF]++] |= i << 8;
        idx = tt[idx];
        for ( i = 0; i < bs; i++ )
        {
            idx = tt[idx >> 8];
            blk[i] = (byte) idx;
        }
    }

    /* Derandomize block if it's randomized. */
    private void derand()
    {
        int i = 0, j = 617;
        while ( rnd && j < bs )
        {
            blk[j] ^= 1;
            i = ( i + 1 ) & 0x1FF;
            j += rand_table[i];
        }
    }

    /* Emit block. RLE is undone here. */
    private void emit()
        throws StreamFormatException
    {
        int i, r, c, d;
        r = 0;
        c = -1;
        for ( i = 0; i < bs; i++ )
        {
            d = c;
            c = blk[i] & 0xFF;
            crc = ( ( crc << 8 ) & 0xFFFFFFFF ) ^ crc_table[( crc >>> 24 ) ^ c];
            write( c );
            if ( c != d )
                r = 1;
            else
            {
                r++;
                if ( r >= 4 )
                {
                    int j;
                    if ( ++i == bs )
                        bad();
                    for ( j = 0; j < ( blk[i] & 0xFF ); j++ )
                    {
                        crc = ( ( crc << 8 ) & 0xFFFFFFFF ) ^ crc_table[( crc >>> 24 ) ^ c];
                        write( c );
                    }
                    r = 0;
                }
            }
        }
    }

    /* Parse stream and bock headers, decompress any blocks found. */
    public void expand()
        throws StreamFormatException, IOException
    {
        int t = 0, c;
        if ( get( 24 ) != 0x425A68 )
            bad();
        t = get( 8 ) - 0x31;
        if ( t >= 9 )
            bad();
        do
        {
            c = 0;
            mbs = 100000 * ( t + 1 );
            while ( ( t = get( 16 ) ) == 0x3141 )
            {
                if ( get( 32 ) != 0x59265359 )
                    bad();
                t = get( 32 );
                retr();
                imtf();
                ibwt();
                derand();
                crc = 0xFFFFFFFF;
                emit();
                if ( ( crc ^ 0xFFFFFFFF ) != t )
                    bad();
                c = ( ( c << 1 ) & 0xFFFFFFFF ) ^ ( c >>> 31 ) ^ t;
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
}
