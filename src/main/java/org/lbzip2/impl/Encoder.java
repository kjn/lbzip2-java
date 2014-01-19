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

import java.util.Arrays;

/**
 * @author Mikolaj Izdebski
 */
class Encoder
{
    private final int[] cmap = new int[256];

    private final int[] order = new int[255];

    int EOB;

    int do_mtf( int[] bwt, short[] mtfv, int[] mtffreq, boolean[] inuse, int nblock )
    {
        int i;
        int j;
        int k;
        int t;
        int c;
        int u;
        int bwt_off = 0;
        int mtfv_off = 0;

        j = 0;

        for ( i = 0; i < 256; i++ )
        {
            k = inuse[i] ? 1 : 0;

            cmap[i] = j;
            j += k;
        }

        EOB = j;

        Arrays.fill( mtffreq, 0 );

        k = 0;
        u = 0;
        for ( i = 0; i < 255; i++ )
            order[i] = i + 1;

        for ( i = 0; i < nblock; i++ )
        {
            if ( ( c = cmap[bwt[bwt_off++]] ) == u )
            {
                k++;
                continue;
            }

            while ( k != 0 )
            {
                mtffreq[mtfv[mtfv_off++] = (short) ( --k & 1 )]++;
                k >>= 1;
            }

            int p = 0;
            t = order[0];
            order[0] = u;
            for ( ;; )
            {
                if ( c == t )
                {
                    u = t;
                    break;
                }
                u = order[++p];
                order[p] = t;
                if ( c == u )
                    break;
                t = order[++p];
                order[p] = u;
            }
            t = p + 2;
            mtfv[mtfv_off++] = (short) t;
            mtffreq[t]++;
        }

        while ( k != 0 )
        {
            mtffreq[mtfv[mtfv_off++] = (short) ( --k & 1 )]++;
            k >>= 1;
        }

        mtfv[mtfv_off++] = (short) EOB;
        mtffreq[EOB]++;

        return mtfv_off;
    }
}
