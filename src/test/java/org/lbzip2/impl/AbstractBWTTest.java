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
}
