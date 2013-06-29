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

import static org.lbzip2.impl.Constants.MAX_ALPHA_SIZE;
import static org.lbzip2.impl.Constants.MAX_CODE_LENGTH;

/**
 * Decodes canonical prefix code.
 * <p>
 * Notes on prefix code decoding:
 * <ol>
 * <li>Width of a tree node is defined as 2<sup>-d</sup>, where {@code d} is depth of that node. A prefix tree is said
 * to be complete iff all leaf widths sum to 1. If this sum is less (greater) than 1, we say the tree is incomplete
 * (oversubscribed). See also: Kraft's inequality.
 * <li>In this implementation, malformed trees (oversubscribed or incomplete) aren't rejected directly at creation
 * (that's the moment when both bad cases are detected). Instead, invalid trees cause decode error only when they are
 * actually used to decode a group. This is nonconforming behavior &ndash; the original <em>bzip2</em>, which serves as
 * a reference implementation, accepts malformed trees as long as nonexistent codes don't appear in compressed stream.
 * Neither bzip2 nor any alternative implementation I know produces such trees, so this behavior seems sane.
 * <li>When held in variables, codes are usually in left-justified form, meaning that they occupy consecutive most
 * significant bits of the variable they are stored in, while less significant bits of variable are padded with zeroes.
 * <p>
 * Such form allows for easy lexicographical comparison of codes using unsigned arithmetic comparison operators, without
 * the need for normalization.
 * 
 * @author Mikolaj Izdebski
 */
class PrefixDecoder
{
    /**
     * Number of bits in lookup table.
     * <p>
     * Prefix code decoding is performed using a multilevel table lookup. The fastest way to decode is to simply build a
     * lookup table whose size is determined by the longest code. However, the time it takes to build this table can
     * also be a factor if the data being decoded is not very long. The most common codes are necessarily the shortest
     * codes, so those codes dominate the decoding time, and hence the speed. The idea is you can have a shorter table
     * that decodes the shorter, more probable codes, and then point to subsidiary tables for the longer codes. The time
     * it costs to decode the longer codes is then traded against the time it takes to make longer tables.
     * <p>
     * This result of this trade are in this constant. {@code HUFF_START_WIDTH} is the number of bits the first level
     * table can decode in one step. Subsequent tables always decode one bit at time. The current value of %{code
     * HUFF_START_WIDTH} was determined with a series of benchmarks. The optimum value may differ though from machine to
     * machine, and possibly even between compilers. Your mileage may vary.
     */
    private static final int HUFF_START_WIDTH = 10;

    /**
     * Decoding start point.
     * <p>
     * {@code k = start[c] & 0x1F} is code length. If {@code k <= HUFF_START_WIDTH} then {@code s = start[c] >> 5} is
     * the immediate symbol value. If {@code k > HUFF_START_WIDTH} then {@code s} is undefined, but code starting with
     * {@code c} is guaranteed to be at least {@code k} bits long.
     */
    final short[] start;

    /**
     * Base codes.
     * <p>
     * For {@code k} in 1..20, {@code base[k]} is either the first code of length {@code k} or it is equal to
     * {@code base[k+1]} if there are no codes of length {@code k}. The other 2 elements are sentinels: {@code base[0]}
     * is always zero, {@code base[MAX_CODE_LENGTH+1]} is plus infinity (represented as {@code -1L}).
     */
    final long[] base;

    /**
     * cumulative code length counts.
     * <p>
     * For {@code k} in 1..20, {@code count[k]} is the number of symbols which codes are shorter than {@code k} bits;
     * {@code count[0]} is a sentinel (always zero).
     */
    final int[] count;

    /**
     * Sorting permutation.
     * <p>
     * The rules of canonical prefix coding require that the source alphabet is sorted stably by ascending code length
     * (the order of symbols of the same code length is preserved). The {@code perm} table holds the sorting
     * permutation.
     */
    final short[] perm;

    public PrefixDecoder()
    {
        this.start = new short[1 << HUFF_START_WIDTH];
        this.base = new long[MAX_CODE_LENGTH + 2];
        this.count = new int[MAX_CODE_LENGTH + 1];
        this.perm = new short[MAX_ALPHA_SIZE];
    }
}
