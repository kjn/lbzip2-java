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

import junit.framework.TestCase;

import org.lbzip2.StreamFormatException;

/**
 * Basic unit test for prefix decoder.
 * 
 * @author Mikolaj Izdebski
 */
public class PrefixDecoderTest
    extends TestCase
{
    public void testIncompleteCode()
    {
        int[] lengths = new int[] { 7, 5, 4, 1 };

        try
        {
            PrefixDecoder decoder = new PrefixDecoder();
            decoder.make_tree( lengths, lengths.length );
            fail();
        }
        catch ( StreamFormatException e )
        {
            assertTrue( e.getMessage().toLowerCase().contains( "incomplete" ) );
        }
    }

    public void testOversubscribedCode()
    {
        int[] lengths = new int[] { 3, 1, 3, 1, 3 };

        try
        {
            PrefixDecoder decoder = new PrefixDecoder();
            decoder.make_tree( lengths, lengths.length );
            fail();
        }
        catch ( StreamFormatException e )
        {
            assertTrue( e.getMessage().toLowerCase().contains( "oversubscribed" ) );
        }
    }
}
