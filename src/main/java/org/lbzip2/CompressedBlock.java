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

/**
 * @author Mikolaj Izdebski
 */
public class CompressedBlock
{
    final byte[] buffer;

    final int blockSize;

    final int crc;

    CompressedBlock( byte[] buffer, int blockSize, int crc )
    {
        this.buffer = buffer;
        this.blockSize = blockSize;
        this.crc = crc;
    }
}
