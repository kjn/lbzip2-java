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
import static org.lbzip2.impl.MtfDecoder.CMAP_BASE;
import static org.lbzip2.impl.PrefixDecoder.EOB;
import static org.lbzip2.impl.PrefixDecoder.HUFF_START_WIDTH;
import static org.lbzip2.impl.PrefixDecoder.RUN_A;
import static org.lbzip2.impl.Status.MORE;
import static org.lbzip2.impl.Status.OK;
import static org.lbzip2.impl.Unsigned.uge;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import org.lbzip2.StreamFormatException;

public class MBC
{
    private final InputStream in;

    private final OutputStream out;

    public MBC( InputStream in, OutputStream out )
    {
        this.in = in;
        this.out = out;

        for ( int t = 0; t < 6; t++ )
            tree[t] = new PrefixDecoder();
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

    private int mbs; /* maximal block size (100k-900k in 100k steps) */

    private final Decoder ds = new Decoder();

    private int as; /*
                     * alphabet size (number of distinct prefix codes, 3-258)
                     */

    private int nt; /*
                     * number of prefix trees used for current block (2-6)
                     */

    private int ns; /* number of selectors (1-32767) */

    private final byte[] sel = new byte[32767]; /* selector MTF values */

    private final MtfDecoder mtf = new MtfDecoder();

    private PrefixDecoder pd;

    private final PrefixDecoder[] tree = new PrefixDecoder[6];

    private short m_b;

    private int m_i;

    private int m_g;

    private final byte[] m_len = new byte[259];

    private int m_t;

    private int m_s;

    private int m_r;

    private int m_h;

    private int m_c;

    private final int[] m_mtf = new int[6];

    private void need( int n )
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

    private int take( int n )
    {
        assert ( bk >= n );
        int x = peek( n );
        dump( n );
        return x;
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

    /* Retrieve block. */
    private void retr()
        throws StreamFormatException, IOException
    {
        need( 1 + 24 + 16 + 16 );
        ds.rand = take( 1 ) != 0;
        ds.bwt_idx = take( 24 );

        /* Retrieve bitmap. */
        m_b = (short) take( 16 );
        as = 0;
        mtf.initialize();
        for ( m_i = 0; m_i < 16; m_i++ )
        {
            if ( m_b < 0 )
            {
                short s = (short) take( 16 );
                for ( int j = 0; j < 16; j++ )
                {
                    if ( s < 0 )
                        mtf.imtf_slide[CMAP_BASE + as++] = (byte) ( 16 * m_i + j );
                    s *= 2;
                }
            }
            m_b *= 2;
            need( 3 + 15 + 6 );
        }
        if ( as == 0 )
            bad();
        as += 2;

        nt = take( 3 );
        if ( nt < 2 || nt > 6 )
            bad();
        ns = take( 15 );

        for ( m_g = 0; m_g < ns; m_g++ )
        {
            sel[m_g] = 0;
            while ( sel[m_g] < nt && take( 1 ) != 0 )
                sel[m_g]++;
            if ( sel[m_g] == nt )
                bad();
            need( 5 + 1 + 1 );
        }
        if ( ns > 18001 )
            ns = 18001;

        m_t = 0;
        m_s = 0;
        m_len[0] = (byte) take( 5 );

        for ( ;; )
        {
            if ( take( 1 ) != 0 )
            {
                m_len[m_s] += 1 - 2 * take( 1 );
                if ( m_len[m_s] < 1 || m_len[m_s] > 20 )
                    bad();
            }
            else
            {
                m_len[m_s + 1] = m_len[m_s];
                if ( ++m_s >= as )
                {
                    tree[m_t].make_tree( m_len, as );
                    if ( ++m_t >= nt )
                        break;
                    m_s = 0;
                    m_len[0] = (byte) take( 5 );
                }
            }
            need( 1 + MAX_CODE_LENGTH );
        }

        /* Retrieve block MTF values and apply IMTF transformation. */
        m_g = 0;
        ds.block_size = m_r = m_h = 0;
        Arrays.fill( ds.ftab, 0 );
        m_c = mtf.imtf_slide[mtf.imtf_row[0]] & 0xFF;
        for ( int i = 0; i < 6; i++ )
            m_mtf[i] = i;
        m_i = 0;
        for ( ;; )
        {
            if ( m_i == 0 )
            {
                if ( m_g++ >= ns )
                    bad();
                int i = sel[m_g - 1];
                int t = m_mtf[i];
                while ( i-- > 0 )
                    m_mtf[i + 1] = m_mtf[i];
                m_mtf[0] = t;
                pd = tree[t];
                if ( pd.error != null )
                    throw new StreamFormatException( pd.error );
                m_i = 50;
            }
            int s = get_sym();
            if ( s >= RUN_A )
            {
                m_r += 1 << ( m_h + s - RUN_A );
                m_h++;
                if ( m_r < 0 )
                    bad();
            }
            else
            {
                int r = m_r;
                if ( ds.block_size + r > mbs )
                    bad();
                ds.ftab[m_c] += r;
                while ( r-- != 0 )
                    ds.tt[ds.block_size++] = m_c;
                if ( s == EOB )
                    return;
                m_c = mtf.mtf_one( s );
                m_h = 0;
                m_r = 1;
            }
            m_i--;
            need( MAX_CODE_LENGTH );
        }
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
            c = 0;
            mbs = 100000 * ( t + 1 );
            while ( ( t = get( 16 ) ) == 0x3141 )
            {
                if ( get( 32 ) != 0x59265359 )
                    bad();
                t = get( 32 );
                retr();
                decode_and_emit();
                if ( ds.crc != t )
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
