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

/**
 * @author Mikolaj Izdebski
 */
public class PackageMergeTest
    extends AbstractPrefixCoderTest
{
    public PackageMergeTest()
    {
        super( new PackageMerge() );
    }

    @Test
    public void testFibonacci()
    {
        long[] P =
            new long[] { 165580141, 102334155, 63245986, 39088169, 24157817, 14930352, 9227465, 5702887, 3524578,
                2178309, 1346269, 832040, 514229, 317811, 196418, 121393, 75025, 46368, 28657, 17711, 10946, 6765,
                4181, 2584, 1597, 987, 610, 377, 233, 144, 89, 55, 34, 21, 13, 8, 5, 3, 2, 1, };

        int[] C = doTheTest( P );

        assertEquals( 0, C[1] );
        for ( int i = 2; i <= 19; i++ )
            assertEquals( 2, C[i] );
        assertEquals( 4, C[20] );
    }
}
