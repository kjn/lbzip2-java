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
import static org.lbzip2.impl.Utils.insertion_sort;

import java.util.Arrays;
import java.util.Random;

import org.junit.Test;

/**
 * @author Mikolaj Izdebski
 */
public class UtilsTest
{
    private final Random r = new Random( 7541 );

    @Test
    public void testInsertionSort()
    {
        for ( int nRep = 0; nRep < 1000; nRep++ )
        {
            int n = 50 + r.nextInt( 100 );
            long[] A = new long[n];
            for ( int i = 0; i < n; i++ )
                A[i] = r.nextLong();

            long[] B = new long[n];
            System.arraycopy( A, 0, B, 0, n );
            Arrays.sort( B );

            insertion_sort( A, 0, n );

            for ( int i = 0; i < n; i++ )
                assertEquals( B[n - i - 1], A[i] );
        }
    }
}