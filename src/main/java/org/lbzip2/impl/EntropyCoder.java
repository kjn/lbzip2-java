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

import static java.lang.Math.max;
import static java.lang.Math.min;
import static org.lbzip2.impl.Constants.GROUP_SIZE;
import static org.lbzip2.impl.Constants.MAX_ALPHA_SIZE;
import static org.lbzip2.impl.Constants.MAX_CODE_LENGTH;
import static org.lbzip2.impl.Constants.MAX_HUFF_CODE_LENGTH;
import static org.lbzip2.impl.Constants.MAX_TREES;
import static org.lbzip2.impl.Constants.MIN_ALPHA_SIZE;
import static org.lbzip2.impl.Constants.MIN_TREES;
import static org.lbzip2.impl.Utils.insertion_sort;

import java.util.Arrays;

/**
 * @author Mikolaj Izdebski
 */
class EntropyCoder
{
    // TODO: use SLF4J
    interface Logger
    {
        void trace( String msg, Object... args );

        boolean isTraceEnabled();
    }

    private Logger logger;

    long b;

    int k;

    byte[] p;

    int p_off;

    private short[] mtfv;

    private byte[] sp;

    private int nmtf;

    private int block_crc;

    private int bwt_idx;

    private boolean[] inuse;

    private int num_trees;

    private int num_selectors;

    private int[] tmap_new2old;

    private byte[][] length;

    private int[] selector;

    private int[][] code;

    private int out_expect_len;

    private int cluster_factor;

    private int[][] frequency;

    private int[] tmap_old2new;

    private long weight_add( long w1, long w2 )
    {
        return ( ( w1 + w2 ) & ~0xFFFFFFFFL ) + max( w1 & 0xFF000000L, w2 & 0xFF000000L ) + 0x01000000L;
    }

    /**
     * Build a prefix-free tree. Because the source alphabet is already sorted, we need not to maintain a priority queue
     * -- two normal FIFO queues (one for leaves and one for internal nodes) will suffice.
     */
    void build_tree( int[] tree, long[] weight, int as )
    {
        int r; /* index of the next tree in the queue */
        int s; /* index of the next singleton leaf */
        int t; /**/
        long w1, w2;

        r = as;
        s = as; /* Start with the last singleton tree. */

        for ( t = as - 1; t > 0; t-- )
        {
            if ( s < 1 || ( r > t + 2 && weight[r - 2] < weight[s - 1] ) )
            {
                /* Select two internal nodes. */
                tree[r - 1] = t;
                tree[r - 2] = t;
                w1 = weight[r - 1];
                w2 = weight[r - 2];
                r -= 2;
            }
            else if ( r < t + 2 || ( s > 1 && weight[s - 2] <= weight[r - 1] ) )
            {
                /* Select two singleton leaf nodes. */
                w1 = weight[s - 1];
                w2 = weight[s - 2];
                s -= 2;
            }
            else
            {
                /* Select one internal node and one singleton leaf node. */
                tree[r - 1] = t;
                w1 = weight[r - 1];
                w2 = weight[s - 1];
                s--;
                r--;
            }

            weight[t] =
                ( weight[t] & 0xFFFF ) + ( ( w1 + w2 ) & ~0xFF00FFFFL ) + max( w1 & 0xFF000000L, w2 & 0xFF000000L )
                    + 0x01000000;
        }
        assert ( r == 2 );
        assert ( s == 0 );
        assert ( t == 0 );
    }

    /* Compute counts from given Huffman tree. The tree itself is clobbered. */
    void compute_depths( int[] count, int[] tree, int as )
    {
        int avail; /* total number of nodes at current level */
        int used; /* number of internal nodes */
        int node; /* current tree node */
        int depth; /* current node depth */

        tree[1] = 0; /* The root always has depth of 0. */
        count[0] = 0; /* There are no zero-length codes in bzip2. */
        node = 2; /* The root is done, advance to the next node (index 2). */
        depth = 1; /* The root was the last node at depth 0, go deeper. */
        avail = 2; /* At depth of 1 there are always exactly 2 nodes. */

        /* Repeat while we have more nodes. */
        while ( depth <= MAX_HUFF_CODE_LENGTH )
        {
            used = 0; /* So far we haven't seen any internal nodes at this level. */

            while ( node < as && tree[tree[node]] + 1 == depth )
            {
                assert ( avail > used );
                used++;
                tree[node++] = depth; /* Overwrite parent pointer with node depth. */
            }

            count[depth] = avail - used;
            depth++;
            avail = used << 1;
        }

        assert ( avail == 0 );
    }

    /**
     * This class is an implementation of the Package-Merge algorithm for finding an optimal length-limited prefix-free
     * code set.
     */
    void package_merge( short[][] tree, int[] count, long[] leaf_weight, int as )
    {
        long[] pkg_weight = new long[MAX_CODE_LENGTH + 1];
        long[] prev_weight = new long[MAX_CODE_LENGTH + 1];
        long[] curr_weight = new long[MAX_CODE_LENGTH + 1];
        int width;
        int next_depth;
        int depth;

        pkg_weight[0] = -1;

        for ( depth = 1; depth <= MAX_CODE_LENGTH; depth++ )
        {
            tree[depth][0] = 2;
            pkg_weight[depth] = weight_add( leaf_weight[as], leaf_weight[as - 1] );
            prev_weight[depth] = leaf_weight[as - 1];
            curr_weight[depth] = leaf_weight[as - 2];
        }

        for ( width = 2; width < as; width++ )
        {
            count[0] = MAX_CODE_LENGTH;
            depth = MAX_CODE_LENGTH;
            next_depth = 1;
            for ( ;; )
            {
                if ( pkg_weight[depth - 1] <= curr_weight[depth] )
                {
                    if ( depth != 1 )
                    {
                        System.arraycopy( tree[depth - 1], 0, tree[depth], 1, MAX_CODE_LENGTH );
                        pkg_weight[depth] = weight_add( prev_weight[depth], pkg_weight[depth - 1] );
                        prev_weight[depth] = pkg_weight[depth - 1];
                        depth--;
                        count[next_depth++] = depth;
                        continue;
                    }
                }
                else
                {
                    tree[depth][0]++;
                    pkg_weight[depth] = weight_add( prev_weight[depth], curr_weight[depth] );
                    prev_weight[depth] = curr_weight[depth];
                    curr_weight[depth] = leaf_weight[as - tree[depth][0]];
                }
                if ( next_depth == 0 )
                    break;
                next_depth--;
                depth = count[next_depth];
            }
        }
    }

    void make_code_lengths( byte[] length, int[] frequency, int as )
    {
        int i;
        int k;
        int d;
        int c;
        long[] weight = new long[MAX_ALPHA_SIZE];
        int[] V = new int[MAX_ALPHA_SIZE];
        int[] count = new int[MAX_HUFF_CODE_LENGTH + 2];

        assert ( as >= MIN_ALPHA_SIZE );
        assert ( as <= MAX_ALPHA_SIZE );

        /*
         * Label weights with sequence numbers. Labelling has two main purposes: firstly it allows to sort pairs of
         * weight and sequence number more easily; secondly: the package-merge algorithm requires weights to be strictly
         * monotonous and putting unique values in lower bits assures that.
         */
        for ( i = 0; i < as; i++ )
        {
            /*
             * FFFFFFFF00000000 - symbol frequency 00000000FF000000 - node depth 0000000000FF0000 - initially one
             * 000000000000FFFF - symbol
             */
            weight[i] = ( ( max( frequency[i], 1 ) << 32 ) | 0x10000 | ( MAX_ALPHA_SIZE - i ) );
        }

        /* Sort weights and sequence numbers together. */
        insertion_sort( weight, 0, as );

        build_tree( V, weight, as );
        compute_depths( count, V, as );

        /* Generate code lengths. */
        i = 0;
        c = 0;
        for ( d = 0; d <= MAX_HUFF_CODE_LENGTH; d++ )
        {
            k = count[d];

            c = ( c + k ) << 1;

            while ( k != 0 )
            {
                assert ( i < as );
                length[(int) ( MAX_ALPHA_SIZE - ( weight[i] & 0xFFFF ) )] = (byte) d;
                i++;
                k--;
            }
        }
        assert ( c == ( 1L << ( MAX_HUFF_CODE_LENGTH + 1 ) ) );
        assert ( i == as );
    }

    /**
     * Create initial mapping of symbols to trees.
     * <p>
     * The goal is to divide all as symbols [0,as) into nt equivalence classes (EC) [0,nt) such that standard deviation
     * of symbol frequencies in classes is minimal. We use a kind of a heuristic to achieve that. There might exist a
     * better way to achieve that, but this one seems to be good (and fast) enough.
     * <p>
     * If the symbol v belongs to the equivalence class t then set length[t][v] to zero. Otherwise set it to 1.
     */
    void generate_initial_trees( int nm, int nt )
    {
        int a, b; /* range [a,b) of symbols forming current EC */
        int freq; /* symbol frequency */
        int cum; /* cumulative frequency */
        int as; /*
                 * effective alphabet size (alphabet size minus number of symbols with frequency equal to zero)
                 */
        int t; /* current tree */

        /* Equivalence classes are initially empty. */
        Arrays.fill( length, 1 );

        /* Determine effective alphabet size. */
        as = 0;
        for ( a = 0, cum = 0; cum < nm; a++ )
        {
            freq = code[0][a];
            cum += freq;
            as += min( freq, 1 );
        }
        assert ( cum == nm );

        /*
         * Bound number of EC by number of symbols. Each EC is non-empty, so number of symbol EC must be <= number of
         * symbols.
         */
        nt = min( nt, as );

        /* For each equivalence class: */
        a = 0;
        for ( t = 0; nt > 0; t++, nt-- )
        {
            assert ( nm > 0 );
            assert ( as >= nt );

            /*
             * Find a range of symbols which total count is roughly proportional to one nt-th of all values.
             */
            freq = code[0][a];
            cum = freq;
            as -= min( freq, 1 );
            b = a + 1;
            while ( as > nt - 1 && cum * nt < nm )
            {
                freq = code[0][b];
                cum += freq;
                as -= min( freq, 1 );
                b++;
            }
            if ( cum > freq && ( 2 * cum - freq ) * nt > 2 * nm )
            {
                cum -= freq;
                as += min( freq, 1 );
                b--;
            }
            assert ( a < b );
            assert ( cum > 0 );
            assert ( cum <= nm );
            assert ( as >= nt - 1 );
            logger.trace( "Tree %u: EC=[{},{}), |EC|={}, cum={}", t, a, b, b - a, cum );

            /* Now [a,b) is our range -- assign it to equivalence class t. */
            while ( a < b )
                length[t][a++] = 0;
            nm -= cum;
        }
        assert ( as == 0 );
        assert ( nm == 0 );
    }

    /**
     * Find the tree which takes the least number of bits to encode current group. Return number from 0 to nt-1
     * identifying the selected tree.
     */
    int find_best_tree( short[] mtfv, int gs, int nt, long[] len_pack )
    {
        long c, bc; /* code length, best code length */
        int t, bt; /* tree, best tree */
        long cp; /* cost packed */

        /*
         * Compute how many bits it takes to encode current group by each of trees. Utilize vector operations for best
         * performance. Let's hope the compiler unrolls the loop for us.
         */
        cp = 0;
        for ( int i = 0; i < GROUP_SIZE; i++ )
            cp += len_pack[mtfv[gs++]];

        /* At the beginning assume the first tree is the best. */
        bc = cp & 0x3ff;
        bt = 0;

        /*
         * Iterate over other trees (starting from second one) to see which one is the best to encode current group.
         */
        for ( t = 1; t < nt; t++ )
        {
            cp >>= 10;
            c = cp & 0x3ff;
            if ( c < bc )
            {
                bc = c;
                bt = t;
            }
        }

        /* Return our favorite. */
        return bt;
    }

    /**
     * Assign prefix-free codes. Return cost of transmitting the tree and all symbols it codes.
     */
    int assign_codes( int[] code, byte[] length, int[] frequency, int as )
    {
        int leaf;
        int avail;
        int height;
        int next_code;
        int symbol;
        long[] leaf_weight = new long[MAX_ALPHA_SIZE + 1];
        int[] count = new int[MAX_HUFF_CODE_LENGTH + 2];
        int[] base_code = new int[MAX_CODE_LENGTH + 1];
        short[][] tree = new short[MAX_CODE_LENGTH + 1][MAX_CODE_LENGTH + 1];
        int best_cost;
        int best_height;
        int depth;
        int cost;

        for ( leaf = 0; leaf < as; leaf++ )
            leaf_weight[leaf + 1] = ( ( (long) frequency[leaf] << 32 ) | 0x10000 | ( MAX_ALPHA_SIZE - leaf ) );
        insertion_sort( leaf_weight, 1, as + 1 );
        leaf_weight[0] = -1;

        for ( int i = 0; i <= MAX_CODE_LENGTH; i++ )
            Arrays.fill( tree[i], (short) 0 );
        package_merge( tree, count, leaf_weight, as );

        best_cost = -1;
        best_height = MAX_CODE_LENGTH;

        for ( height = 2; height <= MAX_CODE_LENGTH; height++ )
        {
            if ( ( 1L << height ) < as )
                continue;
            if ( tree[height][height - 1] == 0 )
            {
                logger.trace( "      (for heights >{} costs is the same as for height={})", height - 1, height - 1 );
                break;
            }

            cost = 0;
            leaf = 0;
            for ( depth = 1; depth <= height; depth++ )
            {
                for ( avail = tree[height][depth - 1] - tree[height][depth]; avail > 0; avail-- )
                {
                    assert ( leaf < as );
                    symbol = (int) ( MAX_ALPHA_SIZE - ( leaf_weight[leaf + 1] & 0xFFFF ) );
                    length[symbol] = (byte) depth;
                    cost += (int) ( leaf_weight[leaf + 1] >> 32 ) * depth;
                    leaf++;
                }
            }

            for ( symbol = 1; symbol < as; symbol++ )
                cost += 2 * max( length[symbol - 1] - length[symbol], length[symbol] - length[symbol - 1] );
            cost += 5 + as;

            logger.trace( "    for height={} transmission cost is {}", height, cost );
            if ( cost < best_cost )
            {
                best_cost = cost;
                best_height = height;
            }
        }
        logger.trace( "  best tree height is {}", best_height );

        /* Generate code lengths and base codes. */
        leaf = 0;
        next_code = 0;
        for ( depth = 1; depth <= best_height; depth++ )
        {
            avail = tree[best_height][depth - 1] - tree[best_height][depth];
            base_code[depth] = next_code;
            next_code = ( next_code + avail ) << 1;

            while ( avail > 0 )
            {
                assert ( leaf < as );
                symbol = (int) ( MAX_ALPHA_SIZE - ( leaf_weight[leaf + 1] & 0xFFFF ) );
                length[symbol] = (byte) depth;
                leaf++;
                avail--;
            }
        }
        assert ( next_code == ( 1L << ( best_height + 1 ) ) );
        assert ( leaf == as );

        /* Assign prefix-free codes. */
        for ( symbol = 0; symbol < as; symbol++ )
            code[symbol] = base_code[length[symbol]]++;

        if ( logger.isTraceEnabled() )
        {
            logger.trace( "  Prefix code dump:" );
            for ( symbol = 0; symbol < as; symbol++ )
            {
                StringBuilder buffer = new StringBuilder( MAX_HUFF_CODE_LENGTH + 2 );
                int len = length[symbol];

                while ( len-- > 0 )
                    buffer.append( ( code[symbol] & ( 1L << len ) ) != 0 ? '1' : '0' );

                logger.trace( "    symbol {} has code {}", symbol, buffer );
            }
        }

        return best_cost;
    }

    /**
     * The main function generating prefix code for the whole block.
     * <p>
     * Input: MTF values Output: trees and selectors
     * <p>
     * What this function does: 1) decides how many trees to generate 2) divides groups into equivalence classes (using
     * Expectation-Maximization algorithm, which is a heuristic usually giving suboptimal results) 3) generates an
     * optimal prefix tree for each class (with a hubrid algorithm consisting of Huffman algorithm and Package-Merge
     * algorithm) 4) generates selectors 5) sorts trees by their first occurence in selector sequence 6) computes and
     * returns cost (in bits) of transmitting trees and codes
     */
    int generate_prefix_code()
    {
        int as;
        int nt;
        int iter, i;
        int cost;

        int nm = nmtf;

        as = mtfv[nm - 1] + 1; /* the last mtfv is EOB */
        num_selectors = ( nm + GROUP_SIZE - 1 ) / GROUP_SIZE;

        /*
         * Decide how many prefix-free trees to use for current block. The best for compression ratio would be to always
         * use the maximal number of trees. However, the space it takes to transmit these trees can also be a factor,
         * especially if the data being encoded is not very long. If we use less trees for smaller block then the space
         * needed to transmit additional trees is traded against the space saved by using more trees.
         */
        assert ( nm >= 2 );
        nt = ( nm > 2400 ? 6 : nm > 1200 ? 5 : nm > 600 ? 4 : nm > 300 ? 3 : nm > 150 ? 2 : 1 );

        /* Complete the last group with dummy symbols. */
        for ( i = nm; i < num_selectors * GROUP_SIZE; i++ )
            mtfv[i] = (short) as;

        /* Grow up an initial forest. */
        generate_initial_trees( nm, nt );

        /*
         * Perform a few iterations of the Expectation-Maximization algorithm to improve trees.
         */
        iter = cluster_factor;
        while ( iter-- > 0 )
        {
            long[] len_pack = new long[MAX_ALPHA_SIZE + 1];
            int gs;
            int v, t;

            /*
             * Pack code lengths of all trees into 64-bit integers in order to take advantage of 64-bit vector
             * arithmetic. Each group holds at most 50 codes, each code is at most 20 bit long, so each group is coded
             * by at most 1000 bits. We can store that in 10 bits.
             */
            for ( v = 0; v < as; v++ )
                len_pack[v] =
                    ( ( length[0][v] ) + ( (long) length[1][v] << 10 ) + ( (long) length[2][v] << 20 )
                        + ( (long) length[3][v] << 30 ) + ( (long) length[4][v] << 40 ) + ( (long) length[5][v] << 50 ) );
            len_pack[as] = 0;

            int sp_off = 0;
            /* (E): Expectation step -- estimate likehood. */
            for ( t = 0; t < nt; t++ )
                Arrays.fill( frequency[t], 0 );
            for ( gs = 0; gs < nm; gs += GROUP_SIZE )
            {
                /*
                 * Check out which prefix-free tree is the best to encode current group. Then increment symbol
                 * frequencies for the chosen tree and remember the choice in the selector array.
                 */
                t = find_best_tree( mtfv, gs, nt, len_pack );
                assert ( t < nt );
                sp[sp_off++] = (byte) t;
                for ( i = 0; i < GROUP_SIZE; i++ )
                    frequency[t][mtfv[gs + i]]++;
            }

            assert ( sp_off == num_selectors );
            sp[sp_off] = MAX_TREES; /* sentinel */

            /* (M): Maximization step -- maximize expectations. */
            for ( t = 0; t < nt; t++ )
                make_code_lengths( length[t], frequency[t], as );
        }

        cost = 0;

        /* Reorder trees. This also removes unused trees. */
        {
            /*
             * Only lowest nt bits are used, from 0 to nt-1. If i-th bit is set then i-th tree exists but hasn't been
             * seen yet.
             */
            int not_seen = ( 1 << nt ) - 1;
            int t, v;
            int sp_off = 0;

            nt = 0;
            while ( not_seen > 0 && ( t = sp[sp_off++] ) < MAX_TREES )
            {
                if ( ( not_seen & ( 1 << t ) ) != 0 )
                {
                    not_seen -= 1 << t;
                    tmap_old2new[t] = nt;
                    tmap_new2old[nt] = t;
                    nt++;

                    /*
                     * Create lookup tables for this tree. These tables are used by the transmiter to quickly send codes
                     * for MTF values.
                     */
                    cost += assign_codes( code[t], length[t], frequency[t], as );
                    code[t][as] = 0;
                    length[t][as] = 0;
                }
            }

            /*
             * If there is only one prefix tree in current block, we need to create a second dummy tree. This increases
             * the cost of transmitting the block, but unfortunately bzip2 doesn't allow blocks with a single tree.
             */
            assert ( nt >= 1 );
            if ( nt == 1 )
            {
                nt = 2;
                t = tmap_new2old[0] ^ 1;
                tmap_old2new[t] = 1;
                tmap_new2old[1] = t;
                for ( v = 0; v < MAX_ALPHA_SIZE; v++ )
                    length[t][v] = MAX_CODE_LENGTH;
                cost += as + 5;
            }
        }

        num_trees = nt;
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

    void transmit( byte[] buf )
    {
        int t;
        int v;
        int ns;
        int as;

        /* Initialize bit buffer. */
        b = 0;
        k = 0;
        p = buf;

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

            for ( int i = 0, j = 0; i < 16; i++ )
            {
                big <<= 1;

                int small = 0;
                for ( ; j < 16; j++ )
                {
                    small <<= 1;
                    if ( inuse[j] )
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
        assert ( num_trees >= MIN_TREES && num_trees <= MAX_TREES );
        SEND( 3, num_trees );
        t = num_selectors;
        SEND( 15, t );
        int sp_off = 0;
        while ( t-- > 0 )
        {
            v = 1 + sp[sp_off++];
            SEND( v, ( 1 << v ) - 2 );
        }

        /* Transmit prefix trees. */
        for ( t = 0; t < num_trees; t++ )
        {
            byte[] len = length[tmap_new2old[t]];

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

            t = selector[gr];
            L = code[t];
            B = length[t];

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

}
