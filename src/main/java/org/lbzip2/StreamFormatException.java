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
package org.lbzip2;

import java.io.IOException;

/**
 * This exception indicates that compressed <em>bz2</em> stream has errors preventing it from being correctly decoded.
 * 
 * @author Mikolaj Izdebski
 */
public class StreamFormatException
    extends IOException
{
    private static final long serialVersionUID = 5802379288030740393L;

    public StreamFormatException( String detail )
    {
        super( detail );
    }
}
