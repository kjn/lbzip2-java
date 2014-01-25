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

import static org.lbzip2.impl.Constants.MAX_CODE_LENGTH;

import java.util.Arrays;

/**
 * This class is an implementation of the Package-Merge algorithm for finding an optimal length-limited prefix-free code
 * set.
 * 
 * @author Mikolaj Izdebski
 */
class PackageMerge
    implements PrefixCoder
{
    private final byte[][] T = new byte[MAX_CODE_LENGTH + 1][];

    private final long[] U = new long[MAX_CODE_LENGTH + 1];

    private final long[] V = new long[MAX_CODE_LENGTH + 1];

    public PackageMerge()
    {
        for ( int k = 1; k <= MAX_CODE_LENGTH; k++ )
            T[k] = new byte[k];
    }

    private void package_merge( int[] C, long[] P, int n, int m )
    {
        U[0] = Long.MAX_VALUE;

        for ( int k = 1; k <= m; k++ )
        {
            Arrays.fill( T[k], (byte) 0 );
            T[k][0] = (byte) 2;
            U[k] = P[n - 1] + P[n - 2];
            V[k] = P[n - 2];
        }

        for ( int i = 2; i < n; i++ )
        {
            C[0] = m;
            C[1] = m;
            for ( int j = 1; j >= 0; j-- )
            {
                int k = C[j];
                int t = n - T[k][0] - 1;
                if ( t >= 0 && U[k - 1] > P[t] )
                {
                    T[k][0]++;
                    U[k] = V[k] + P[t];
                    V[k] = P[t];
                }
                else if ( k != 1 )
                {
                    System.arraycopy( T[k - 1], 0, T[k], 1, k - 1 );
                    U[k] = V[k] + U[k - 1];
                    V[k] = U[k - 1];
                    k--;
                    C[j++] = k;
                    C[j++] = k;
                }
            }
        }

        C[0] = 0;
        for ( int k = 1; k < m; k++ )
            C[k] = T[m][k - 1] - T[m][k];
        C[m] = T[m][m - 1];
        C[m + 1] = 0;
    }

    public void build_tree( int[] C, long[] P, int n )
    {
        package_merge( C, P, n, MAX_CODE_LENGTH );
    }
}
