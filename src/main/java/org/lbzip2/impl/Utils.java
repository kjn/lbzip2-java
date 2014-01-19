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

/**
 * @author Mikolaj Izdebski
 */
public class Utils
{
    static void insertion_sort( long[] P, int first, int last )
    {
        for ( int a = first + 1; a < last; ++a )
        {
            int b1 = a;
            long t = P[b1];
            for ( int b = b1 - 1; P[b] < t; --b )
            {
                P[b1] = P[b];
                if ( ( b1 = b ) == first )
                    break;
            }
            P[b1] = t;
        }
    }
}
