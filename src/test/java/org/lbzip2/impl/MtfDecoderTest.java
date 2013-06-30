/*-
 * Copyright (c) 2013 Mikolaj Izdebski
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

import java.util.Random;

import junit.framework.TestCase;

/**
 * Unit test for IMTF implementation.
 * 
 * @author Mikolaj Izdebski
 */
public class MtfDecoderTest
    extends TestCase
{
    /**
     * Seed for PRNG.
     */
    private static final long SEED = 7541;

    /**
     * Number of test repetitions (Monte-Carlo iterations).
     */
    private static final int N_ITER = 100000;

    private final Random random = new Random( SEED );

    MtfDecoder decoder = new MtfDecoder();

    int[] state = new int[256];

    @Override
    protected void setUp()
        throws Exception
    {
        super.setUp();

        for ( int j = 0; j < 256; j++ )
        {
            decoder.imtf_slide[MtfDecoder.CMAP_BASE + j] = (byte) j;
            state[j] = j;
        }

        decoder.initialize();
    }

    private int mtf_one( int x )
    {
        int y = state[x];
        for ( int j = x; j > 0; j-- )
            state[j] = state[j - 1];
        state[0] = y;
        return y;
    }

    /**
     * Test decoding of random MTF stream.
     */
    public void testRandomMtfStream()
    {
        for ( int iter = 0; iter < N_ITER; iter++ )
        {
            int x = random.nextInt( 256 );
            int y = mtf_one( x );
            byte y0 = decoder.mtf_one( (byte) x );

            assertEquals( y, y0 & 0xff );
        }
    }
}
