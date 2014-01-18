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

import org.junit.Test;

public abstract class AbstractBWTTest
{
    private final BWT bwt;

    public AbstractBWTTest( BWT bwt )
    {
        this.bwt = bwt;
    }

    @Test
    public void testTrivialTransformation()
    {
        int[] P = new int[] { 'M', 'I', 'S', 'S', 'I', 'S', 'S', 'I', 'P', 'P', 'I', 0 };

        int idx = bwt.transform( P, 11 );

        assertEquals( 'P', P[0] );
        assertEquals( 'S', P[1] );
        assertEquals( 'S', P[2] );
        assertEquals( 'M', P[3] );
        assertEquals( 'I', P[4] );
        assertEquals( 'P', P[5] );
        assertEquals( 'I', P[6] );
        assertEquals( 'S', P[7] );
        assertEquals( 'S', P[8] );
        assertEquals( 'I', P[9] );
        assertEquals( 'I', P[10] );

        assertEquals( 4, idx );
    }

    @Test
    public void testFibonacciWords()
    {
        int[] f2 = new int[] { 'A' };
        int[] f1 = new int[] { 'A', 'B' };

        int x2 = 0;
        int x1 = 1;
        int d = 0;

        for ( int j = 2; j <= 20; j++ )
        {
            int[] f0 = new int[f1.length + f2.length];
            System.arraycopy( f1, 0, f0, 0, f1.length );
            System.arraycopy( f2, 0, f0, f1.length, f2.length );

            int x0 = x2 + x1;

            int[] P = new int[f0.length + 1];
            System.arraycopy( f0, 0, P, 0, f0.length );
            int idx = bwt.transform( P, f0.length );
            assertEquals( x0 + d, idx );

            for ( int i = 0; i < f2.length; i++ )
                assertEquals( "i=" + i, 'B', P[i] );
            for ( int i = f2.length; i < f0.length; i++ )
                assertEquals( "i=" + i, 'A', P[i] );

            f2 = f1;
            f1 = f0;

            x2 = x1;
            x1 = x0;
            d ^= -1;
        }
    }
}
