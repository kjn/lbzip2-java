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

import static org.lbzip2.Constants.CHARACTER_BIAS;
import static org.lbzip2.Unsigned.umin;

/**
 * A simple BWT implementation based on LSD radix sort.
 * 
 * @author Mikolaj Izdebski
 */
class RadixSortBWT
    implements BWT
{
    public int transform( byte[] B, int[] P, int n )
    {
        int[] U = new int[256];
        int[] C = new int[256];
        int[] R = new int[n];

        if ( ( n & 1 ) != 0 )
        {
            int[] T = P;
            P = R;
            R = T;
        }

        for ( int i = 0; i < n; i++ )
        {
            C[B[i] + CHARACTER_BIAS]++;
            P[i] = i;
        }

        int cum = 0;
        for ( int i = 0; i < 256; i++ )
            C[i] = ( cum += C[i] ) - C[i];
        assert cum == n;

        for ( int d = 1; d <= n; d++ )
        {
            System.arraycopy( C, 0, U, 0, 256 );

            for ( int i = 0; i < n; i++ )
            {
                int j = P[i];
                int t = umin( j - d, j - d + n );
                R[U[B[t] + CHARACTER_BIAS]++] = j;
            }

            int[] T = P;
            P = R;
            R = T;
        }

        int idx = n;
        for ( int i = 0; i < n; i++ )
        {
            int j = P[i];
            if ( j == 0 )
            {
                assert idx == n;
                idx = i;
                j = n;
            }

            P[i] = B[j - 1] + CHARACTER_BIAS;
        }

        assert idx < n;
        return idx;
    }
}
