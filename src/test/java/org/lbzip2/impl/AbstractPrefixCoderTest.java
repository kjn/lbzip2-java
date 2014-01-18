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

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;

/**
 * @author Mikolaj Izdebski
 */
public abstract class AbstractPrefixCoderTest
{
    private final PrefixCoder coder;

    public AbstractPrefixCoderTest( PrefixCoder coder )
    {
        this.coder = coder;
    }

    protected int[] doTheTest( long[] P )
    {
        int n = P.length;
        int[] C = new int[22];

        // Just in case...
        Arrays.fill( C, 0xDeadBeef );

        coder.build_tree( C, P, n );

        // C[0] is a sentinel and always must be 0
        assertEquals( 0, C[0] );

        return C;
    }

    @Test
    public void basicTest()
    {
        // Sorted version of: 7, 5, 4, 1, 6, 8, 9
        long[] P = new long[] { 9, 8, 7, 6, 5, 4, 1 };

        int[] C = doTheTest( P );

        // Let's build Huffman tree by hand:
        // 7, 5, 4, 1, 6, 8, 9
        // 7, 5, 5(4,1), 6, 8, 9
        // 7, 10(5,5(4,1)), 6, 8, 9
        // 13(7,6) 10(5,5(4,1)), 8, 9
        // 13(7,6) 10(5,5(4,1)), 17(8,9)
        // 23(13(7,6),10(5,5(4,1))), 17(8,9)
        // 40(23(13(7,6),10(5,5(4,1))),17(8,9))

        // Code lengths: 3, 3, 3, 4, 4, 2, 2
        // Code length counts: C[2]=2, C[3]=3, C[4]=2
        // Kraft sum: 2*2^-2 + 3*2^-3 + 2*2^-4 = 1

        assertEquals( 0, C[1] );
        assertEquals( 2, C[2] );
        assertEquals( 3, C[3] );
        assertEquals( 2, C[4] );
        for ( int i = 5; i <= 21; i++ )
            assertEquals( 0, C[i] );
    }
}
