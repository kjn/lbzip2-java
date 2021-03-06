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

import static org.lbzip2.Constants.GROUP_SIZE;
import static org.lbzip2.Constants.MAX_SELECTORS;
import static org.lbzip2.Constants.MAX_TREES;
import static org.lbzip2.Constants.MIN_TREES;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Mikolaj Izdebski
 */
final class Encoder
{
    private final Logger logger = LoggerFactory.getLogger( Encoder.class );

    private final int[] cmap = new int[256];

    private final int[] order = new int[255];

    private long b;

    private int k;

    private byte[] p;

    private int p_off;

    private final short[] mtfv;

    private int nmtf;

    private final byte[] selector_mtf = new byte[MAX_SELECTORS];

    private int bwt_idx;

    private int out_expect_len;

    private final UncompressedBlock col;

    private final EntropyCoder ec;

    private final BWT bwt = new DivBWT();

    private int block_crc;

    private final boolean[] inuse = new boolean[256];

    private Encoder( UncompressedBlock col, EntropyCoder ec )
    {
        this.col = col;
        this.ec = ec;

        mtfv = new short[col.size + GROUP_SIZE];
    }

    private int do_mtf( int[] SA, int[] mtffreq, int nblock )
    {
        int i;
        int j;
        int k;
        int t;
        int c;
        int u;
        int bwt_off = 0;
        int mtfv_off = 0;

        j = 0;

        for ( i = 0; i < 256; i++ )
        {
            k = col.inuse[i] ? 1 : 0;

            cmap[i] = j;
            j += k;
        }

        int EOB = j + 1;
        assert ( EOB >= 2 );
        assert ( EOB < 258 );

        Arrays.fill( mtffreq, 0 );

        k = 0;
        u = 0;
        for ( i = 0; i < 255; i++ )
            order[i] = i + 1;

        for ( i = 0; i < nblock; i++ )
        {
            if ( ( c = cmap[SA[bwt_off++]] ) == u )
            {
                k++;
                continue;
            }

            while ( k != 0 )
            {
                mtffreq[mtfv[mtfv_off++] = (short) ( --k & 1 )]++;
                k >>= 1;
            }

            int p = 0;
            t = order[0];
            order[0] = u;
            for ( ;; )
            {
                if ( c == t )
                {
                    u = t;
                    break;
                }
                u = order[++p];
                order[p] = t;
                if ( c == u )
                    break;
                t = order[++p];
                order[p] = u;
            }
            t = p + 2;
            mtfv[mtfv_off++] = (short) t;
            mtffreq[t]++;
        }

        while ( k != 0 )
        {
            mtffreq[mtfv[mtfv_off++] = (short) ( --k & 1 )]++;
            k >>= 1;
        }

        mtfv[mtfv_off++] = (short) EOB;
        mtffreq[EOB]++;

        return mtfv_off;
    }

    private int encode()
    {
        int cost;
        int pk;
        int i;
        int sp; /* selector pointer */
        int smp; /* selector MTFV pointer */
        int c; /* value before MTF */
        int j; /* value after MTF */
        int p; /* MTF state */

        /* Sort block. */
        assert ( col.size > 0 );
        int[] SA = new int[col.size + 1];
        bwt_idx = bwt.transform( col.block, SA, col.size );
        nmtf = do_mtf( SA, ec.code[0], col.size );
        logger.debug( "Block info: bs={}, idx={}, nm={}, as={}", col.size, bwt_idx, nmtf, mtfv[nmtf - 1] + 1 );
        SA = null;

        cost = 48 /* header */
            + 32 /* crc */
            + 1 /* rand bit */
            + 24 /* bwt index */
            + 00 /* {cmap} */
            + 3 /* nGroups */
            + 15 /* nSelectors */
            + 00 /* {sel} */
            + 00 /* {tree} */
            + 00; /* {mtfv} */

        cost += ec.generate_prefix_code( mtfv, nmtf );

        sp = 0;
        smp = 0;

        /*
         * A trick that allows to do MTF without branching, using arithmetical and logical operations only. The whole
         * MTF state packed into one 32-bit integer.
         */

        /* Set up initial MTF state. */
        p = 0x543210;

        assert ( ec.selector[0] < MAX_TREES );
        assert ( ec.tmap_old2new[ec.selector[0]] == 0 );

        while ( ( c = ec.selector[sp] ) != MAX_TREES )
        {
            int v, z, l, h;

            c = ec.tmap_old2new[c];
            assert ( c < ec.num_trees );
            assert ( sp < ec.num_selectors );
            v = p ^ ( 0x111111 * c );
            z = ( v + 0xEEEEEF ) & 0x888888;
            l = z ^ ( z - 1 );
            h = ~l;
            p = ( p | l ) & ( ( p << 4 ) | h | c );
            h &= -h;
            j = ( h & 0x01010100 ) != 0 ? 1 : 0;
            h |= h >> 4;
            j |= h >> 11;
            j |= h >> 18;
            j &= 7;
            sp++;
            selector_mtf[smp++] = (byte) j;
            cost += j + 1;
        }

        /*
         * Add zero to seven dummy selectors in order to make block size multiply of 8 bits.
         */
        j = cost & 0x7;
        j = ( 8 - j ) & 0x7;
        ec.num_selectors += j;
        cost += j;
        while ( j-- > 0 )
            selector_mtf[smp++] = 0;
        assert ( cost % 8 == 0 );

        /* Calculate the cost of transmitting character map. */
        for ( i = 0; i < 16; i++ )
        {
            pk = 0;
            for ( j = 0; j < 16; j++ )
                pk |= col.inuse[16 * i + j] ? 16 : 0;
            cost += pk;
        }
        cost += 16; /* Big bucket costs 16 bits on its own. */

        /* Convert cost from bits to bytes. */
        assert ( cost % 8 == 0 );
        cost >>= 3;

        out_expect_len = cost;

        block_crc = col.crc;
        System.arraycopy( col.inuse, 0, inuse, 0, 256 );

        logger.debug( "Block transmission cost is {} bytes", cost );
        logger.debug( "Block CRC is {}", String.format( "%08X", col.crc ^ -1 ) );

        return cost;
    }

    private void SEND( int n, int v )
    {
        long b = this.b;
        int k = this.k;

        b = ( b << n ) | ( v & 0xFFFFFFFFL );
        if ( ( k += n ) >= 32 )
        {
            p[p_off++] = (byte) ( b >> ( k -= 8 ) );
            p[p_off++] = (byte) ( b >> ( k -= 8 ) );
            p[p_off++] = (byte) ( b >> ( k -= 8 ) );
            p[p_off++] = (byte) ( b >> ( k -= 8 ) );
        }

        this.b = b;
        this.k = k;
    }

    private void transmit( byte[] buf )
    {
        int t;
        int v;
        int ns;
        int as;

        /* Initialize bit buffer. */
        b = 0;
        k = 0;
        p = buf;
        p_off = 0;

        as = mtfv[nmtf - 1] + 1;
        ns = ( nmtf + GROUP_SIZE - 1 ) / GROUP_SIZE;

        /* Transmit block metadata. */
        SEND( 24, 0x314159 );
        SEND( 24, 0x265359 );
        SEND( 32, block_crc ^ -1 );
        SEND( 1, 0 ); /* non-rand */
        SEND( 24, bwt_idx ); /* bwt primary index */

        /* Transmit character map. */
        {
            int[] pack = new int[16];
            int big = 0;

            for ( int i = 0; i < 16; i++ )
            {
                big <<= 1;

                int small = 0;
                for ( int j = 0; j < 16; j++ )
                {
                    small <<= 1;
                    if ( inuse[16 * i + j] )
                    {
                        small |= 1;
                        big |= 1;
                    }
                }
                pack[i] = small;
            }

            SEND( 16, big );
            for ( int i = 0; i < 16; i++ )
                if ( pack[i] != 0 )
                    SEND( 16, pack[i] );
        }

        /* Transmit selectors. */
        assert ( ec.num_trees >= MIN_TREES && ec.num_trees <= MAX_TREES );
        SEND( 3, ec.num_trees );
        t = ec.num_selectors;
        SEND( 15, t );
        int sp = 0;
        while ( t-- > 0 )
        {
            v = 1 + selector_mtf[sp++];
            SEND( v, ( 1 << v ) - 2 );
        }

        /* Transmit prefix trees. */
        for ( t = 0; t < ec.num_trees; t++ )
        {
            byte[] len = ec.length[ec.tmap_new2old[t]];

            int a = len[0];
            SEND( 6, a << 1 );
            for ( v = 1; v < as; v++ )
            {
                int c = len[v];
                while ( a < c )
                {
                    SEND( 2, 2 );
                    a++;
                }
                while ( a > c )
                {
                    SEND( 2, 3 );
                    a--;
                }
                SEND( 1, 0 );
            }
        }

        int mtfv_off = 0;
        /* Transmit prefix codes. */
        for ( int gr = 0; gr < ns; gr++ )
        {
            int[] L; /* symbol-to-code lookup table */
            byte[] B; /* code lengths */
            int mv; /* symbol (MTF value) */

            t = ec.selector[gr];
            L = ec.code[t];
            B = ec.length[t];

            for ( int i = 0; i < GROUP_SIZE; i++ )
            {
                mv = mtfv[mtfv_off++];
                SEND( B[mv], L[mv] );
            }
        }

        /* Flush */
        while ( k > 0 )
        {
            p[p_off++] = (byte) ( b << ( 56 - ( k -= 8 ) ) >> 56 );
        }
        assert ( p_off == out_expect_len );
    }

    static CompressedBlock encode( UncompressedBlock uncompressedBlock )
    {
        Encoder encoder = new Encoder( uncompressedBlock, new EntropyCoder( 10 ) );
        int blockSize = uncompressedBlock.size;
        int compressedSize = encoder.encode();
        byte[] buffer = new byte[compressedSize];
        int crc = encoder.block_crc;
        encoder.transmit( buffer );
        return new CompressedBlock( buffer, blockSize, crc );
    }
}
