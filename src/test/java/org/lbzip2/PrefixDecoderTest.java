/*-
 * Copyright (c) 2013-2014 Mikolaj Izdebski
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

import static org.lbzip2.Constants.MAX_ALPHA_SIZE;
import static org.lbzip2.Constants.MAX_CODE_LENGTH;

import java.util.Random;

import junit.framework.TestCase;

/**
 * Basic unit test for prefix decoder.
 * 
 * @author Mikolaj Izdebski
 */
public class PrefixDecoderTest
    extends TestCase
{
    /**
     * Seed for PRNG.
     */
    private static final long SEED = 7541;

    /**
     * Number of test repetitions (Monte-Carlo iterations).
     */
    private static final int N_ITER = 1000;

    private final Random random = new Random( SEED );

    public void testIncompleteCode()
    {
        int[] lengths = new int[] { 7, 5, 4, 1 };

        PrefixDecoder decoder = new PrefixDecoder();
        decoder.make_tree( lengths, lengths.length );
        assertNotNull( decoder.error );
        assertTrue( decoder.error.toLowerCase().contains( "incomplete" ) );
    }

    public void testOversubscribedCode()
    {
        int[] lengths = new int[] { 3, 1, 3, 1, 3 };

        PrefixDecoder decoder = new PrefixDecoder();
        decoder.make_tree( lengths, lengths.length );
        assertNotNull( decoder.error );
        assertTrue( decoder.error.toLowerCase().contains( "oversubscribed" ) );
    }

    /**
     * Test if prefix decoder correctly handles random code length sequence.
     */
    public void testRandomLengths()
    {
        for ( int iter = 0; iter < N_ITER; iter++ )
        {
            int as = random.nextInt( MAX_ALPHA_SIZE - 1 ) + 2;
            int[] len = new int[as];

            for ( int v = 0; v < as; v++ )
                len[v] = random.nextInt( MAX_CODE_LENGTH ) + 1;

            double kraftSum = 0;
            for ( int v = 0; v < as; v++ )
            {
                double width = 1;
                for ( int k = 0; k < len[v]; k++ )
                    width *= 0.5;
                kraftSum += width;
            }

            boolean expectException = kraftSum != 1;

            PrefixDecoder decoder = new PrefixDecoder();
            decoder.make_tree( len, len.length );
            assertEquals( expectException, decoder.error != null );
        }
    }
}
