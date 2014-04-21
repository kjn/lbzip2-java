/*-
  divbwt.c -- Burrows-Wheeler transformation

  Copyright (C) 2012, 2014 Mikolaj Izdebski
  Copyright (c) 2012 Yuta Mori

  This file is part of lbzip2.

  lbzip2 is free software: you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  lbzip2 is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with lbzip2.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.lbzip2.impl;
import static java.lang.Math.*;

class DivBWT {
private static final boolean DEBUG = true;

/*- Settings -*/
private static final int SS_INSERTIONSORT_THRESHOLD = 8;
private static final int SS_BLOCKSIZE = 1024;
private static final int ALPHABET_SIZE = 256;

/* minstacksize = log(SS_BLOCKSIZE) / log(3) * 2 */
private static final int SS_MISORT_STACKSIZE = 16;
private static final int SS_SMERGE_STACKSIZE = 32;
/* for trsort.c */
private static final int TR_INSERTIONSORT_THRESHOLD = 8;
private static final int TR_STACKSIZE = 64;

/*- Macros -*/
private int
STACK_PUSH(int[] stack, int ssize, int a, int b, int c, int d)
  {
    stack[ssize] = a;
    stack[ssize + 1] = b;
    stack[ssize + 2] = c;
    stack[ssize + 3] = d;
    return ssize + 4;
  }
private int
STACK_PUSH5(int[] stack, int ssize, int a, int b, int c, int d, int e)
  {
    stack[ssize] = a;
    stack[ssize + 1] = b;
    stack[ssize + 2] = c;
    stack[ssize + 3] = d;
    stack[ssize + 4] = e;
    return ssize + 5;
  }
/* for divsufsort.c */
private int
BUCKET_A(int[] bucket, int c0)
{
  return bucket[c0 + ALPHABET_SIZE * ALPHABET_SIZE];
}
private int
BUCKET_B(int[] bucket, int c0, int c1)
{
  return bucket[(c1 << 8) | c0];
}
private int
BUCKET_BSTAR(int[] bucket, int c0, int c1)
{
  return bucket[(c0 << 8) | c1];
}
/* for trsort.c */
private int
TR_GETC(int[] SA, int depth, int num_bstar, int p)
{
  return (p < (num_bstar - depth)) ? SA[num_bstar + p + depth] : SA[num_bstar + (p + depth) % num_bstar];
}
/* for sssort.c and trsort.c */
private final int lg_table[256]= {
 -1,0,1,1,2,2,2,2,3,3,3,3,3,3,3,3,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,4,
  5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,5,
  6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,
  6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,6,
  7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,
  7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,
  7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,
  7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7,7
};


/*---- sssort ----*/

/*- Private Functions -*/

private
int
ss_ilg(int n) {
  return (n & 0xff00) ?
          8 + lg_table[(n >> 8) & 0xff] :
          0 + lg_table[(n >> 0) & 0xff];
}


private final int sqq_table[256] = {
  0,  16,  22,  27,  32,  35,  39,  42,  45,  48,  50,  53,  55,  57,  59,  61,
 64,  65,  67,  69,  71,  73,  75,  76,  78,  80,  81,  83,  84,  86,  87,  89,
 90,  91,  93,  94,  96,  97,  98,  99, 101, 102, 103, 104, 106, 107, 108, 109,
110, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 123, 124, 125, 126,
128, 128, 129, 130, 131, 132, 133, 134, 135, 136, 137, 138, 139, 140, 141, 142,
143, 144, 144, 145, 146, 147, 148, 149, 150, 150, 151, 152, 153, 154, 155, 155,
156, 157, 158, 159, 160, 160, 161, 162, 163, 163, 164, 165, 166, 167, 167, 168,
169, 170, 170, 171, 172, 173, 173, 174, 175, 176, 176, 177, 178, 178, 179, 180,
181, 181, 182, 183, 183, 184, 185, 185, 186, 187, 187, 188, 189, 189, 190, 191,
192, 192, 193, 193, 194, 195, 195, 196, 197, 197, 198, 199, 199, 200, 201, 201,
202, 203, 203, 204, 204, 205, 206, 206, 207, 208, 208, 209, 209, 210, 211, 211,
212, 212, 213, 214, 214, 215, 215, 216, 217, 217, 218, 218, 219, 219, 220, 221,
221, 222, 222, 223, 224, 224, 225, 225, 226, 226, 227, 227, 228, 229, 229, 230,
230, 231, 231, 232, 232, 233, 234, 234, 235, 235, 236, 236, 237, 237, 238, 238,
239, 240, 240, 241, 241, 242, 242, 243, 243, 244, 244, 245, 245, 246, 246, 247,
247, 248, 248, 249, 249, 250, 250, 251, 251, 252, 252, 253, 253, 254, 254, 255
};

private
int
ss_isqrt(int x) {
  int y, e;

  if(x >= (SS_BLOCKSIZE * SS_BLOCKSIZE)) { return SS_BLOCKSIZE; }
  e = (x & 0xffff0000) ?
        ((x & 0xff000000) ?
          24 + lg_table[(x >> 24) & 0xff] :
          16 + lg_table[(x >> 16) & 0xff]) :
        ((x & 0x0000ff00) ?
           8 + lg_table[(x >>  8) & 0xff] :
           0 + lg_table[(x >>  0) & 0xff]);

  if(e >= 16) {
    y = sqq_table[x >> ((e - 6) - (e & 1))] << ((e >> 1) - 7);
    if(e >= 24) { y = (y + 1 + x / y) >> 1; }
    y = (y + 1 + x / y) >> 1;
  } else if(e >= 8) {
    y = (sqq_table[x >> ((e - 6) - (e & 1))] >> (7 - (e >> 1))) + 1;
  } else {
    return sqq_table[x] >> 4;
  }

  return (x < (y * y)) ? y - 1 : y;
}


/*---------------------------------------------------------------------------*/

/* Compares two suffixes. */
private
int
ss_compare(byte[] T, int[] SA,
           int p1, int p2,
           int depth) {
  int U1, U2, U1n, U2n;

  for(U1 = depth + SA[p1],
      U2 = depth + SA[p2],
      U1n = SA[p1 + 1] + 2,
      U2n = SA[p2 + 1] + 2;
      (U1 < U1n) && (U2 < U2n) && (T[U1] == T[U2]);
      ++U1, ++U2) {
  }

  return U1 < U1n ?
        (U2 < U2n ? T[U1] - T[U2] : 1) :
        (U2 < U2n ? -1 : 0);
}

private
int
ss_compare_last(byte[] T, int[] SA, int xpa,
                int p1, int p2,
                int depth, int size) {
  int U1, U2, U1n, U2n;

  for(U1 = depth + SA[p1],
      U2 = depth + SA[p2],
      U1n = size,
      U2n = SA[p2 + 1] + 2;
      (U1 < U1n) && (U2 < U2n) && (T[U1] == T[U2]);
      ++U1, ++U2) {
  }

  if(U1 < U1n) { return (U2 < U2n) ? T[U1] - T[U2] : 1; }
  else if(U2 == U2n) { return 1; }

  for(U1 = U1 % size, U1n = SA[xpa] + 2;
      (U1 < U1n) && (U2 < U2n) && (T[U1] == T[U2]);
      ++U1, ++U2) {
  }

  return U1 < U1n ?
        (U2 < U2n ? T[U1] - T[U2] : 1) :
        (U2 < U2n ? -1 : 0);

}


/*---------------------------------------------------------------------------*/

/* Insertionsort for small size groups */
private
void
ss_insertionsort(byte[] T, int[] SA, int xpa,
                 int first, int last, int depth) {
  int i, j;
  int t;
  int r;

  for(i = last - 2; first <= i; --i) {
    for(t = SA[i], j = i + 1; 0 < (r = ss_compare(T, SA, xpa + t, xpa + SA[j], depth));) {
      do { SA[j - 1] = SA[j]; } while((++j < last) && (SA[j] < 0));
      if(last <= j) { break; }
    }
    if(r == 0) { SA[j] = ~SA[j]; }
    SA[j - 1] = t;
  }
}


/*---------------------------------------------------------------------------*/

private
void
ss_fixdown(byte[] T, int depth, int[] SA, int xpa,
           int root, int i, int size) {
  int j, k;
  int v;
  int c, d, e;

  for(v = SA[root + i], c = T[SA[xpa + v] + depth]; (j = 2 * i + 1) < size; SA[root + i] = SA[root + k], i = k) {
    d = T[SA[xpa + SA[root + k = j++]] + depth];
    if(d < (e = T[SA[xpa + SA[root + j]] + depth])) { k = j; d = e; }
    if(d <= c) { break; }
  }
  SA[root + i] = v;
}

/* Simple top-down heapsort. */
private
void
ss_heapsort(byte[] T, int depth, int[] SA, int xpa, int root, int size) {
  int i, m;
  int t;

  m = size;
  if((size % 2) == 0) {
    m--;
    if(T[SA[xpa + SA[root + m / 2]] + depth] < T[SA[xpa + SA[root + m]] + depth]) { t = SA[root + m]; SA[root + m] = SA[root + m / 2]; SA[root + m / 2] = t; }
  }

  for(i = m / 2 - 1; 0 <= i; --i) { ss_fixdown(T, depth, SA, xpa, root, i, m); }
  if((size % 2) == 0) { t = SA[root]; SA[root] = SA[root + m]; SA[root + m] = t; ss_fixdown(T, depth, SA, xpa, root, 0, m); }
  for(i = m - 1; 0 < i; --i) {
    t = SA[root], SA[root] = SA[root + i];
    ss_fixdown(T, depth, SA, xpa, root, 0, i);
    SA[root + i] = t;
  }
}


/*---------------------------------------------------------------------------*/

/* Returns the median of three elements. */
private
int
ss_median3(byte[] T, int depth, int[] SA, int xpa,
           int v1, int v2, int v3) {
  int t;
  if(T[SA[xpa + SA[v1]] + depth] > T[SA[xpa + SA[v2]] + depth]) { t = v1; v1 = v2; v2 = t; }
  if(T[SA[xpa + SA[v2]] + depth] > T[SA[xpa + SA[v3]] + depth]) {
    if(T[SA[xpa + SA[v1]] + depth] > T[SA[xpa + SA[v3]] + depth]) { return v1; }
    else { return v3; }
  }
  return v2;
}

/* Returns the median of five elements. */
private
int
ss_median5(byte[] T, int depth, int[] SA, int xpa,
           int v1, int v2, int v3, int v4, int v5) {
  int t;
  if(T[SA[xpa + SA[v2]] + depth] > T[SA[xpa + SA[v3]] + depth]) { t = v2; v2 = v3; v3 = t; }
  if(T[SA[xpa + SA[v4]] + depth] > T[SA[xpa + SA[v5]] + depth]) { t = v4; v4 = v5; v5 = t; }
  if(T[SA[xpa + SA[v2]] + depth] > T[SA[xpa + SA[v4]] + depth]) { t = v2; v2 = v4; v4 = t; t = v3; v3 = v5; v5 = t; }
  if(T[SA[xpa + SA[v1]] + depth] > T[SA[xpa + SA[v3]] + depth]) { t = v1; v1 = v3; v3 = t; }
  if(T[SA[xpa + SA[v1]] + depth] > T[SA[xpa + SA[v4]] + depth]) { t = v1; v1 = v4; v4 = t; t = v3; v3 = v5; v5 = t; }
  if(T[SA[xpa + SA[v3]] + depth] > T[SA[xpa + SA[v4]] + depth]) { return v4; }
  return v3;
}

/* Returns the pivot element. */
private
int
ss_pivot(byte[] T, int depth, int[] SA, int xpa, int first, int last) {
  int middle;
  int t;

  t = last - first;
  middle = first + t / 2;

  if(t <= 512) {
    if(t <= 32) {
      return ss_median3(T, depth, SA, xpa, first, middle, last - 1);
    } else {
      t >>= 2;
      return ss_median5(T, depth, SA, xpa, first, first + t, middle, last - 1 - t, last - 1);
    }
  }
  t >>= 3;
  first  = ss_median3(T, depth, SA, xpa, first, first + t, first + (t << 1));
  middle = ss_median3(T, depth, SA, xpa, middle - t, middle, middle + t);
  last   = ss_median3(T, depth, SA, xpa, last - 1 - (t << 1), last - 1 - t, last - 1);
  return ss_median3(T, depth, SA, xpa, first, middle, last);
}


/*---------------------------------------------------------------------------*/

/* Binary partition for substrings. */
private
int
ss_partition(int[] SA, int xpa,
                    int first, int last, int depth) {
  int a, b;
  int t;
  for(a = first - 1, b = last;;) {
    for(; (++a < b) && ((SA[xpa + SA[a]] + depth) >= (SA[xpa + SA[a] + 1] + 1));) { SA[a] = ~SA[a]; }
    for(; (a < --b) && ((SA[xpa + SA[b]] + depth) <  (SA[xpa + SA[b] + 1] + 1));) { }
    if(b <= a) { break; }
    t = ~SA[b];
    SA[b] = SA[a];
    SA[a] = t;
  }
  if(first < a) { SA[first] = ~SA[first]; }
  return a;
}

/* Multikey introsort for medium size groups. */
private
void
ss_mintrosort(byte[] T, int[] SA, int xpa,
              int first, int last,
              int depth) {
  int[] stack = new int[4 * SS_MISORT_STACKSIZE];
  int a, b, c, d, e, f;
  int s, t;
  int ssize;
  int limit;
  int v, x = 0;

  for(ssize = 0, limit = ss_ilg(last - first);;) {

    if((last - first) <= SS_INSERTIONSORT_THRESHOLD) {
      if(1 < (last - first)) { ss_insertionsort(T, SA, xpa, first, last, depth); }
      // STACK_POP(first, last, depth, limit)
      if(ssize == 0) return;
      first = stack[ssize - 4];
      last = stack[ssize - 3];
      depth = stack[ssize - 2];
      limit = stack[ssize - 1];
      ssize -= 4;
      continue;
    }

    if(limit-- == 0) { ss_heapsort(T, depth, SA, xpa, first, last - first); }
    if(limit < 0) {
      for(a = first + 1, v = T[SA[xpa + SA[first]] + depth]; a < last; ++a) {
        if((x = T[SA[xpa + SA[a]] + depth]) != v) {
          if(1 < (a - first)) { break; }
          v = x;
          first = a;
        }
      }
      if(T[SA[xpa + SA[first]] - 1 + depth] < v) {
        first = ss_partition(SA, xpa, first, a, depth);
      }
      if((a - first) <= (last - a)) {
        if(1 < (a - first)) {
          ssize = STACK_PUSH(stack, ssize, a, last, depth, -1);
          last = a, depth += 1, limit = ss_ilg(a - first);
        } else {
          first = a, limit = -1;
        }
      } else {
        if(1 < (last - a)) {
          ssize = STACK_PUSH(stack, ssize, first, a, depth + 1, ss_ilg(a - first));
          first = a, limit = -1;
        } else {
          last = a, depth += 1, limit = ss_ilg(a - first);
        }
      }
      continue;
    }

    /* choose pivot */
    a = ss_pivot(T, depth, SA, xpa, first, last);
    v = T[SA[xpa + SA[a]] + depth];
    t = SA[first]; SA[first] = SA[a]; SA[a] = t;

    /* partition */
    for(b = first; (++b < last) && ((x = T[SA[xpa + SA[b]] + depth]) == v);) { }
    if(((a = b) < last) && (x < v)) {
      for(; (++b < last) && ((x = T[SA[xpa + SA[b]] + depth]) <= v);) {
        if(x == v) { t = SA[b]; SA[b] = SA[a]; SA[a] = t; ++a; }
      }
    }
    for(c = last; (b < --c) && ((x = T[SA[xpa + SA[c]] + depth]) == v);) { }
    if((b < (d = c)) && (x > v)) {
      for(; (b < --c) && ((x = T[SA[xpa + SA[c]] + depth]) >= v);) {
        if(x == v) { t = SA[c]; SA[c] = SA[d]; SA[d] = t; --d; }
      }
    }
    for(; b < c;) {
      t = SA[b]; SA[b] = SA[c]; SA[c] = t;
      for(; (++b < c) && ((x = T[SA[xpa + SA[b]] + depth]) <= v);) {
        if(x == v) { t = SA[b]; SA[b] = SA[a]; SA[a] = t; ++a; }
      }
      for(; (b < --c) && ((x = T[SA[xpa + SA[c]] + depth]) >= v);) {
        if(x == v) { t = SA[c]; SA[c] = SA[d]; SA[d] = t; --d; }
      }
    }

    if(a <= d) {
      c = b - 1;

      if((s = a - first) > (t = b - a)) { s = t; }
      for(e = first, f = b - s; 0 < s; --s, ++e, ++f) { t = SA[e]; SA[e] = SA[f]; SA[f] = t; }
      if((s = d - c) > (t = last - d - 1)) { s = t; }
      for(e = b, f = last - s; 0 < s; --s, ++e, ++f) { t = SA[e]; SA[e] = SA[f]; SA[f] = t; }

      a = first + (b - a), c = last - (d - c);
      b = (v <= T[SA[xpa + SA[a]] - 1 + depth]) ? a : ss_partition(SA, xpa, a, c, depth);

      if((a - first) <= (last - c)) {
        if((last - c) <= (c - b)) {
          ssize = STACK_PUSH(stack, ssize, b, c, depth + 1, ss_ilg(c - b));
          ssize = STACK_PUSH(stack, ssize, c, last, depth, limit);
          last = a;
        } else if((a - first) <= (c - b)) {
          ssize = STACK_PUSH(stack, ssize, c, last, depth, limit);
          ssize = STACK_PUSH(stack, ssize, b, c, depth + 1, ss_ilg(c - b));
          last = a;
        } else {
          ssize = STACK_PUSH(stack, ssize, c, last, depth, limit);
          ssize = STACK_PUSH(stack, ssize, first, a, depth, limit);
          first = b, last = c, depth += 1, limit = ss_ilg(c - b);
        }
      } else {
        if((a - first) <= (c - b)) {
          ssize = STACK_PUSH(stack, ssize, b, c, depth + 1, ss_ilg(c - b));
          ssize = STACK_PUSH(stack, ssize, first, a, depth, limit);
          first = c;
        } else if((last - c) <= (c - b)) {
          ssize = STACK_PUSH(stack, ssize, first, a, depth, limit);
          ssize = STACK_PUSH(stack, ssize, b, c, depth + 1, ss_ilg(c - b));
          first = c;
        } else {
          ssize = STACK_PUSH(stack, ssize, first, a, depth, limit);
          ssize = STACK_PUSH(stack, ssize, c, last, depth, limit);
          first = b, last = c, depth += 1, limit = ss_ilg(c - b);
        }
      }
    } else {
      limit += 1;
      if(T[SA[xpa + SA[first]] - 1 + depth] < v) {
        first = ss_partition(SA, xpa, first, last, depth);
        limit = ss_ilg(last - first);
      }
      depth += 1;
    }
  }
}


/*---------------------------------------------------------------------------*/

private
void
ss_blockswap(int a, int b, int n) {
  int t;
  for(; 0 < n; --n, ++a, ++b) {
    t = SA[a], SA[a] = SA[b], SA[b] = t;
  }
}

private
void
ss_rotate(int first, int middle, int last) {
  int a, b, t;
  int l, r;
  l = middle - first, r = last - middle;
  for(; (0 < l) && (0 < r);) {
    if(l == r) { ss_blockswap(first, middle, l); break; }
    if(l < r) {
      a = last - 1, b = middle - 1;
      t = SA[a];
      do {
        SA[a]-- = SA[b], SA[b]-- = SA[a];
        if(b < first) {
          SA[a] = t;
          last = a;
          if((r -= l + 1) <= l) { break; }
          a -= 1, b = middle - 1;
          t = SA[a];
        }
      } while(1);
    } else {
      a = first, b = middle;
      t = SA[a];
      do {
        SA[a]++ = SA[b], SA[b]++ = SA[a];
        if(last <= b) {
          SA[a] = t;
          first = a + 1;
          if((l -= r + 1) <= r) { break; }
          a += 1, b = middle;
          t = SA[a];
        }
      } while(1);
    }
  }
}


/*---------------------------------------------------------------------------*/

private
void
ss_inplacemerge(byte[] T, int[] SA, int xpa,
                int first, int middle, int last,
                int depth) {
  int p;
  int a, b;
  int len, half;
  int q, r;
  int x;

  for(;;) {
    if(SA[last - 1] < 0) { x = 1; p = xpa + ~SA[last - 1]; }
    else                { x = 0; p = xpa +  SA[last - 1]; }
    for(a = first, len = middle - first, half = len >> 1, r = -1;
        0 < len;
        len = half, half >>= 1) {
      b = a + half;
      q = ss_compare(T, SA, xpa + ((0 <= SA[b]) ? SA[b] : ~SA[b]), p, depth);
      if(q < 0) {
        a = b + 1;
        half -= (len & 1) ^ 1;
      } else {
        r = q;
      }
    }
    if(a < middle) {
      if(r == 0) { SA[a] = ~SA[a]; }
      ss_rotate(a, middle, last);
      last -= middle - a;
      middle = a;
      if(first == middle) { break; }
    }
    --last;
    if(x != 0) { while(SA[--last] < 0) { } }
    if(middle == last) { break; }
  }
}


/*---------------------------------------------------------------------------*/

/* Merge-forward with internal buffer. */
private
void
ss_mergeforward(byte[] T, int[] SA, int xpa,
                int first, int middle, int last,
                int buf, int depth) {
  int a, b, c, bufend;
  int t;
  int r;

  bufend = buf + (middle - first) - 1;
  ss_blockswap(buf, first, middle - first);

  for(t = SA[a = first], b = buf, c = middle;;) {
    r = ss_compare(T, SA, xpa + SA[b], xpa + SA[c], depth);
    if(r < 0) {
      do {
        SA[a]++ = SA[b];
        if(bufend <= b) { SA[bufend] = t; return; }
        SA[b]++ = SA[a];
      } while(SA[b] < 0);
    } else if(r > 0) {
      do {
        SA[a]++ = SA[c], SA[c]++ = SA[a];
        if(last <= c) {
          while(b < bufend) { SA[a]++ = SA[b], SA[b]++ = SA[a]; }
          SA[a] = SA[b], SA[b] = t;
          return;
        }
      } while(SA[c] < 0);
    } else {
      SA[c] = ~SA[c];
      do {
        SA[a]++ = SA[b];
        if(bufend <= b) { SA[bufend] = t; return; }
        SA[b]++ = SA[a];
      } while(SA[b] < 0);

      do {
        SA[a]++ = SA[c], SA[c]++ = SA[a];
        if(last <= c) {
          while(b < bufend) { SA[a]++ = SA[b], SA[b]++ = SA[a]; }
          SA[a] = SA[b], SA[b] = t;
          return;
        }
      } while(SA[c] < 0);
    }
  }
}

/* Merge-backward with internal buffer. */
private
void
ss_mergebackward(byte[] T, int[] SA, int xpa,
                 int first, int middle, int last,
                 int buf, int depth) {
  int p1, p2;
  int a, b, c, bufend;
  int t;
  int r;
  int x;

  bufend = buf + (last - middle) - 1;
  ss_blockswap(buf, middle, last - middle);

  x = 0;
  if(SA[bufend] < 0)       { p1 = xpa + ~SA[bufend]; x |= 1; }
  else                  { p1 = xpa +  SA[bufend]; }
  if(SA[middle - 1] < 0) { p2 = xpa + ~SA[middle - 1]; x |= 2; }
  else                  { p2 = xpa +  SA[middle - 1]; }
  for(t = SA[a = last - 1], b = bufend, c = middle - 1;;) {
    r = ss_compare(T, SA, p1, p2, depth);
    if(0 < r) {
      if(x & 1) { do { SA[a]-- = SA[b], SA[b]-- = SA[a]; } while(SA[b] < 0); x ^= 1; }
      SA[a]-- = SA[b];
      if(b <= buf) { SA[buf] = t; break; }
      SA[b]-- = SA[a];
      if(SA[b] < 0) { p1 = xpa + ~SA[b]; x |= 1; }
      else       { p1 = xpa +  SA[b]; }
    } else if(r < 0) {
      if(x & 2) { do { SA[a]-- = SA[c], SA[c]-- = SA[a]; } while(SA[c] < 0); x ^= 2; }
      SA[a]-- = SA[c], SA[c]-- = SA[a];
      if(c < first) {
        while(buf < b) { SA[a]-- = SA[b], SA[b]-- = SA[a]; }
        SA[a] = SA[b], SA[b] = t;
        break;
      }
      if(SA[c] < 0) { p2 = xpa + ~SA[c]; x |= 2; }
      else       { p2 = xpa +  SA[c]; }
    } else {
      if(x & 1) { do { SA[a]-- = SA[b], SA[b]-- = SA[a]; } while(SA[b] < 0); x ^= 1; }
      SA[a]-- = ~SA[b];
      if(b <= buf) { SA[buf] = t; break; }
      SA[b]-- = SA[a];
      if(x & 2) { do { SA[a]-- = SA[c], SA[c]-- = SA[a]; } while(SA[c] < 0); x ^= 2; }
      SA[a]-- = SA[c], SA[c]-- = SA[a];
      if(c < first) {
        while(buf < b) { SA[a]-- = SA[b], SA[b]-- = SA[a]; }
        SA[a] = SA[b], SA[b] = t;
        break;
      }
      if(SA[b] < 0) { p1 = xpa + ~SA[b]; x |= 1; }
      else       { p1 = xpa +  SA[b]; }
      if(SA[c] < 0) { p2 = xpa + ~SA[c]; x |= 2; }
      else       { p2 = xpa +  SA[c]; }
    }
  }
}

private int
GETIDX(int a)
{
  return 0 <= a ? a : ~a;
}

private void
MERGE_CHECK(byte[] T, int[] SA, int xpa, int depth, int a, int b, int c)
{
    if((c & 1) != 0 ||
       ((c & 2) != 0 && (ss_compare(T, SA, xpa + GETIDX(SA[a - 1]), xpa + SA[a], depth) == 0))) {
      SA[a] = ~SA[a];
    }
    if((c & 4) != 0 && ((ss_compare(T, SA, xpa + GETIDX(SA[b - 1]), xpa + SA[b], depth) == 0))) {
      SA[b] = ~SA[b];
    }
}

/* D&C based merge. */
private
void
ss_swapmerge(byte[] T, int[] SA, int xpa,
             int first, int middle, int last,
             int buf, int bufsize, int depth) {
  int[] stack = new int[4 * SS_SMERGE_STACKSIZE];
  int l, r, lm, rm;
  int m, len, half;
  int ssize;
  int check, next;

  for(check = 0, ssize = 0;;) {
    if((last - middle) <= bufsize) {
      if((first < middle) && (middle < last)) {
        ss_mergebackward(T, SA, xpa, first, middle, last, buf, depth);
      }
      MERGE_CHECK(T, SA, xpa, depth, first, last, check);
      // STACK_POP(first, middle, last, check)
      if(ssize == 0) return;
      first = stack[ssize - 4];
      middle = stack[ssize - 3];
      last = stack[ssize - 2];
      check = stack[ssize - 1];
      ssize -= 4;
      continue;
    }

    if((middle - first) <= bufsize) {
      if(first < middle) {
        ss_mergeforward(T, SA, xpa, first, middle, last, buf, depth);
      }
      MERGE_CHECK(T, SA, xpa, depth, first, last, check);
      // STACK_POP(first, middle, last, check)
      if(ssize == 0) return;
      first = stack[ssize - 4];
      middle = stack[ssize - 3];
      last = stack[ssize - 2];
      check = stack[ssize - 1];
      ssize -= 4;
      continue;
    }

    for(m = 0, len = min(middle - first, last - middle), half = len >> 1;
        0 < len;
        len = half, half >>= 1) {
      if(ss_compare(T, SA, xpa + GETIDX(SA[middle + m + half]),
                       xpa + GETIDX(SA[middle - m - half - 1]), depth) < 0) {
        m += half + 1;
        half -= (len & 1) ^ 1;
      }
    }

    if(0 < m) {
      lm = middle - m, rm = middle + m;
      ss_blockswap(lm, middle, m);
      l = r = middle, next = 0;
      if(rm < last) {
        if(SA[rm] < 0) {
          SA[rm] = ~SA[rm];
          if(first < lm) { for(; SA[--l] < 0;) { } next |= 4; }
          next |= 1;
        } else if(first < lm) {
          for(; SA[r] < 0; ++r) { }
          next |= 2;
        }
      }

      if((l - first) <= (last - r)) {
        ssize = STACK_PUSH(stack, ssize, r, rm, last, (next & 3) | (check & 4));
        middle = lm, last = l, check = (check & 3) | (next & 4);
      } else {
        if((next & 2) && (r == middle)) { next ^= 6; }
        ssize = STACK_PUSH(stack, ssize, first, lm, l, (check & 3) | (next & 4));
        first = r, middle = rm, check = (next & 3) | (check & 4);
      }
    } else {
      if(ss_compare(T, SA, xpa + GETIDX(SA[middle - 1]), xpa + SA[middle], depth) == 0) {
        SA[middle] = ~SA[middle];
      }
      MERGE_CHECK(T, SA, xpa, depth, first, last, check);
      // STACK_POP(first, middle, last, check)
      if(ssize == 0) return;
      first = stack[ssize - 4];
      middle = stack[ssize - 3];
      last = stack[ssize - 2];
      check = stack[ssize - 1];
      ssize -= 4;
    }
  }
}


/*---------------------------------------------------------------------------*/

/*- Function -*/

/* Substring sort */
private
void
sssort(byte[] T, int[] SA, int xpa,
       int first, int last,
       int buf, int bufsize,
       int depth, int n, int lastsuffix) {
  int a;
  int b, middle, curbuf;
  int j, k, curbufsize, limit;
  int i;

  if(lastsuffix != 0) { ++first; }

  if((bufsize < SS_BLOCKSIZE) &&
      (bufsize < (last - first)) &&
      (bufsize < (limit = ss_isqrt(last - first)))) {
    if(SS_BLOCKSIZE < limit) { limit = SS_BLOCKSIZE; }
    buf = middle = last - limit, bufsize = limit;
  } else {
    middle = last, limit = 0;
  }
  for(a = first, i = 0; SS_BLOCKSIZE < (middle - a); a += SS_BLOCKSIZE, ++i) {
    ss_mintrosort(T, SA, xpa, a, a + SS_BLOCKSIZE, depth);
    curbufsize = last - (a + SS_BLOCKSIZE);
    curbuf = a + SS_BLOCKSIZE;
    if(curbufsize <= bufsize) { curbufsize = bufsize, curbuf = buf; }
    for(b = a, k = SS_BLOCKSIZE, j = i; j & 1; b -= k, k <<= 1, j >>= 1) {
      ss_swapmerge(T, SA, xpa, b - k, b, b + k, curbuf, curbufsize, depth);
    }
  }
  ss_mintrosort(T, SA, xpa, a, middle, depth);
  for(k = SS_BLOCKSIZE; i != 0; k <<= 1, i >>= 1) {
    if(i & 1) {
      ss_swapmerge(T, SA, xpa, a - k, a, middle, buf, bufsize, depth);
      a -= k;
    }
  }
  if(limit != 0) {
    ss_mintrosort(T, SA, xpa, middle, last, depth);
    ss_inplacemerge(T, SA, xpa, first, middle, last, depth);
  }

  if(lastsuffix != 0) {
    /* Insert last type B* suffix. */
    int r;
    for(a = first, i = SA[first - 1], r = 1;
        (a < last) && ((SA[a] < 0) || (0 < (r = ss_compare_last(T, SA, xpa, xpa + i, xpa + SA[a], depth, n))));
        ++a) {
      SA[a - 1] = SA[a];
    }
    if(r == 0) { SA[a] = ~SA[a]; }
    SA[a - 1] = i;
  }
}


/*---- trsort ----*/

/*- Private Functions -*/

private
int
tr_ilg(int n) {
  return (n & 0xffff0000) ?
          ((n & 0xff000000) ?
            24 + lg_table[(n >> 24) & 0xff] :
            16 + lg_table[(n >> 16) & 0xff]) :
          ((n & 0x0000ff00) ?
             8 + lg_table[(n >>  8) & 0xff] :
             0 + lg_table[(n >>  0) & 0xff]);
}


/*---------------------------------------------------------------------------*/

/* Simple insertionsort for small size groups. */
private
void
tr_insertionsort(int[] SA, int depth, int num_bstar,
                 int first, int last) {
  int a, b;
  int t, r;

  for(a = first + 1; a < last; ++a) {
    for(t = SA[a], b = a - 1; 0 > (r = TR_GETC(SA, depth, num_bstar, t) - TR_GETC(SA, depth, num_bstar, SA[b]));) {
      do { SA[b + 1] = SA[b]; } while((first <= --b) && (SA[b] < 0));
      if(b < first) { break; }
    }
    if(r == 0) { SA[b] = ~SA[b]; }
    SA[b + 1] = t;
  }
}


/*---------------------------------------------------------------------------*/

private
void
tr_fixdown(int[] SA, int depth, int num_bstar,
           int root, int i, int size) {
  int j, k;
  int v;
  int c, d, e;

  for(v = SA[root + i], c = TR_GETC(SA, depth, num_bstar, v); (j = 2 * i + 1) < size; SA[root + i] = SA[root + k], i = k) {
    k = j++;
    d = TR_GETC(SA, depth, num_bstar, SA[root + k]);
    if(d < (e = TR_GETC(SA, depth, num_bstar, SA[root + j]))) { k = j; d = e; }
    if(d <= c) { break; }
  }
  SA[root + i] = v;
}

/* Simple top-down heapsort. */
private
void
tr_heapsort(int[] SA, int depth, int num_bstar,
            int root, int size) {
  int i, m;
  int t;

  m = size;
  if((size % 2) == 0) {
    m--;
    if(TR_GETC(SA, depth, num_bstar, SA[root + m / 2]) < TR_GETC(SA, depth, num_bstar, SA[root + m])) { t = SA[root + m]; SA[root + m] = SA[root + m / 2]; SA[root + m / 2] = t; }
  }

  for(i = m / 2 - 1; 0 <= i; --i) { tr_fixdown(SA, depth, num_bstar, root, i, m); }
  if((size % 2) == 0) { t = SA[root]; SA[root] = SA[root + m]; SA[root + m] = t; tr_fixdown(SA, depth, num_bstar, root, 0, m); }
  for(i = m - 1; 0 < i; --i) {
    t = SA[root], SA[root] = SA[root + i];
    tr_fixdown(SA, depth, num_bstar, root, 0, i);
    SA[root + i] = t;
  }
}


/*---------------------------------------------------------------------------*/

/* Returns the median of three elements. */
private
int
tr_median3(int[] SA, int depth, int num_bstar,
           int v1, int v2, int v3) {
  int t;
  if(TR_GETC(SA, depth, num_bstar, SA[v1]) > TR_GETC(SA, depth, num_bstar, SA[v2])) { t = v1; v1 = v2; v2 = t; }
  if(TR_GETC(SA, depth, num_bstar, SA[v2]) > TR_GETC(SA, depth, num_bstar, SA[v3])) {
    if(TR_GETC(SA, depth, num_bstar, SA[v1]) > TR_GETC(SA, depth, num_bstar, SA[v3])) { return v1; }
    else { return v3; }
  }
  return v2;
}

/* Returns the median of five elements. */
private
int
tr_median5(int[] SA, int depth, int num_bstar,
           int v1, int v2, int v3, int v4, int v5) {
  int t;
  if(TR_GETC(SA, depth, num_bstar, SA[v2]) > TR_GETC(SA, depth, num_bstar, SA[v3])) { t = v2; v2 = v3; v3 = t; }
  if(TR_GETC(SA, depth, num_bstar, SA[v4]) > TR_GETC(SA, depth, num_bstar, SA[v5])) { t = v4; v4 = v5; v5 = t; }
  if(TR_GETC(SA, depth, num_bstar, SA[v2]) > TR_GETC(SA, depth, num_bstar, SA[v4])) { t = v2; v2 = v4; v4 = t; t = v3; v3 = v5; v5 = t; }
  if(TR_GETC(SA, depth, num_bstar, SA[v1]) > TR_GETC(SA, depth, num_bstar, SA[v3])) { t = v1; v1 = v3; v3 = t; }
  if(TR_GETC(SA, depth, num_bstar, SA[v1]) > TR_GETC(SA, depth, num_bstar, SA[v4])) { t = v1; v1 = v4; v4 = t; t = v3; v3 = v5; v5 = t; }
  if(TR_GETC(SA, depth, num_bstar, SA[v3]) > TR_GETC(SA, depth, num_bstar, SA[v4])) { return v4; }
  return v3;
}

/* Returns the pivot element. */
private
int
tr_pivot(int[] SA, int depth, int num_bstar,
         int first, int last) {
  int middle;
  int t;

  t = last - first;
  middle = first + t / 2;

  if(t <= 512) {
    if(t <= 32) {
      return tr_median3(SA, depth, num_bstar, first, middle, last - 1);
    } else {
      t >>= 2;
      return tr_median5(SA, depth, num_bstar, first, first + t, middle, last - 1 - t, last - 1);
    }
  }
  t >>= 3;
  first  = tr_median3(SA, depth, num_bstar, first, first + t, first + (t << 1));
  middle = tr_median3(SA, depth, num_bstar, middle - t, middle, middle + t);
  last   = tr_median3(SA, depth, num_bstar, last - 1 - (t << 1), last - 1 - t, last - 1);
  return tr_median3(SA, depth, num_bstar, first, middle, last);
}


/*---------------------------------------------------------------------------*/

  int chance;
  int remain;
  int incval;
  int count;

private
void
trbudget_init(int chance, int incval) {
  this.chance = chance;
  this.remain = this.incval = incval;
}

private
int
trbudget_check(int size) {
  if(size <= this.remain) { this.remain -= size; return 1; }
  if(this.chance == 0) { this.count += size; return 0; }
  this.remain += this.incval - size;
  this.chance -= 1;
  return 1;
}


/*---------------------------------------------------------------------------*/

private
long
tr_partition(int[] SA, int depth, int num_bstar,
             int first, int middle, int last,
             int v) {
  int a, b, c, d, e, f;
  int t, s;
  int x = 0;

  for(b = middle - 1; (++b < last) && ((x = TR_GETC(SA, depth, num_bstar, SA[b])) == v);) { }
  if(((a = b) < last) && (x < v)) {
    for(; (++b < last) && ((x = TR_GETC(SA, depth, num_bstar, SA[b])) <= v);) {
      if(x == v) { t = SA[b]; SA[b] = SA[a]; SA[a] = t; ++a; }
    }
  }
  for(c = last; (b < --c) && ((x = TR_GETC(SA, depth, num_bstar, SA[c])) == v);) { }
  if((b < (d = c)) && (x > v)) {
    for(; (b < --c) && ((x = TR_GETC(SA, depth, num_bstar, SA[c])) >= v);) {
      if(x == v) { t = SA[c]; SA[c] = SA[d]; SA[d] = t; --d; }
    }
  }
  for(; b < c;) {
    t = SA[b]; SA[b] = SA[c]; SA[c] = t;
    for(; (++b < c) && ((x = TR_GETC(SA, depth, num_bstar, SA[b])) <= v);) {
      if(x == v) { t = SA[b]; SA[b] = SA[a]; SA[a] = t; ++a; }
    }
    for(; (b < --c) && ((x = TR_GETC(SA, depth, num_bstar, SA[c])) >= v);) {
      if(x == v) { t = SA[c]; SA[c] = SA[d]; SA[d] = t; --d; }
    }
  }

  if(a <= d) {
    c = b - 1;
    if((s = a - first) > (t = b - a)) { s = t; }
    for(e = first, f = b - s; 0 < s; --s, ++e, ++f) { t = SA[e]; SA[e] = SA[f]; SA[f] = t; }
    if((s = d - c) > (t = last - d - 1)) { s = t; }
    for(e = b, f = last - s; 0 < s; --s, ++e, ++f) { t = SA[e]; SA[e] = SA[f]; SA[f] = t; }
    first += (b - a), last -= (d - c);
  }
  return ((long)first << 32) + last;
}

private
void
tr_copy(int[] SA, int num_bstar,
        int first, int a, int b, int last,
        int depth) {
  /* sort suffixes of middle partition
     by using sorted order of suffixes of left and right partition. */
  int c, d, e;
  int s, v;

  v = b - SA - 1;
  for(c = first, d = a - 1; c <= d; ++c) {
    if((s = SA[c] - depth) < 0) { s += num_bstar; }
    if(SA[num_bstar + s] == v) {
      SA[++d] = s;
      SA[num_bstar + s] = d - SA;
    }
  }
  for(c = last - 1, e = d + 1, d = b; e < d; --c) {
    if((s = SA[c] - depth) < 0) { s += num_bstar; }
    if(SA[num_bstar + s] == v) {
      SA[--d] = s;
      SA[num_bstar + s] = d - SA;
    }
  }
}

private
void
tr_partialcopy(int[] SA, int num_bstar,
               int first, int a, int b, int last,
               int depth) {
  int c, d, e;
  int s, v, t;
  int rank, lastrank, newrank = -1;

  v = b - SA - 1;
  lastrank = -1;
  for(c = first, d = a - 1; c <= d; ++c) {
    t = SA[c];
    if((s = t - depth) < 0) { s += num_bstar; }
    if(SA[num_bstar + s] == v) {
      SA[++d] = s;
      rank = SA[num_bstar + t];
      if(lastrank != rank) { lastrank = rank; newrank = d - SA; }
      SA[num_bstar + s] = newrank;
    }
  }

  lastrank = -1;
  for(e = d; first <= e; --e) {
    rank = SA[num_bstar + SA[e]];
    if(lastrank != rank) { lastrank = rank; newrank = e - SA; }
    if(newrank != rank) { SA[num_bstar + SA[e]] = newrank; }
  }

  lastrank = -1;
  for(c = last - 1, e = d + 1, d = b; e < d; --c) {
    t = SA[c];
    if((s = t - depth) < 0) { s += num_bstar; }
    if(SA[num_bstar + s] == v) {
      SA[--d] = s;
      rank = SA[num_bstar + t];
      if(lastrank != rank) { lastrank = rank; newrank = d - SA; }
      SA[num_bstar + s] = newrank;
    }
  }
}

private
void
tr_introsort(int[] SA, int depth, int num_bstar,
             int first, int last,
             ) {
  int[] stack = new int[5 * TR_STACKSIZE];
  int a, b, c;
  int t;
  int v, x = 0;
  int incr = depth;
  int limit, next;
  int ssize, trlink = -1;
  long range;

  for(ssize = 0, limit = tr_ilg(last - first);;) {
    assert((depth < num_bstar) || (limit == -3));

    if(limit < 0) {
      if(limit == -1) {
        /* tandem repeat partition */
        range = tr_partition(SA, depth - incr, num_bstar, first, first, last, last - SA - 1);
        a = (int)(range >> 32);
        b = (int)range;

        /* update ranks */
        if(a < last) {
          for(c = first, v = a - SA - 1; c < a; ++c) { SA[num_bstar + SA[c]] = v; }
        }
        if(b < last) {
          for(c = a, v = b - SA - 1; c < b; ++c) { SA[num_bstar + SA[c]] = v; }
        }

        /* push */
        if(1 < (b - a)) {
          ssize = STACK_PUSH5(stack, ssize, NULL, a, b, 0, 0);
          ssize = STACK_PUSH5(stack, ssize, depth - incr, first, last, -2, trlink);
          trlink = ssize - 10;
        }
        if((a - first) <= (last - b)) {
          if(1 < (a - first)) {
            ssize = STACK_PUSH5(stack, ssize, depth, b, last, tr_ilg(last - b), trlink);
            last = a, limit = tr_ilg(a - first);
          } else if(1 < (last - b)) {
            first = b, limit = tr_ilg(last - b);
          } else {
            // STACK_POP5(depth, first, last, limit, trlink)
            if(ssize == 0) return;
            depth = stack[ssize - 5];
            first = stack[ssize - 4];
            last = stack[ssize - 3];
            limit = stack[ssize - 2];
            trlink = stack[ssize - 1];
            ssize -= 5;
          }
        } else {
          if(1 < (last - b)) {
            ssize = STACK_PUSH5(stack, ssize, depth, first, a, tr_ilg(a - first), trlink);
            first = b, limit = tr_ilg(last - b);
          } else if(1 < (a - first)) {
            last = a, limit = tr_ilg(a - first);
          } else {
            // STACK_POP5(depth, first, last, limit, trlink)
            if(ssize == 0) return;
            depth = stack[ssize - 5];
            first = stack[ssize - 4];
            last = stack[ssize - 3];
            limit = stack[ssize - 2];
            trlink = stack[ssize - 1];
            ssize -= 5;
          }
        }
      } else if(limit == -2) {
        /* tandem repeat copy */
        // STACK_POP5(_, a, b, limit, _)
        if(ssize == 0) return;
        a = stack[ssize - 4];
        b = stack[ssize - 3];
        limit = stack[ssize - 2];
        ssize -= 5;
        if(limit == 0) {
          tr_copy(SA, num_bstar, first, a, b, last, depth);
        } else {
          if(0 <= trlink) { stack[trlink + 3] = -1; }
          tr_partialcopy(SA, num_bstar, first, a, b, last, depth);
        }
        // STACK_POP5(depth, first, last, limit, trlink)
        if(ssize == 0) return;
        depth = stack[ssize - 5];
        first = stack[ssize - 4];
        last = stack[ssize - 3];
        limit = stack[ssize - 2];
        trlink = stack[ssize - 1];
        ssize -= 5;
      } else {
        /* sorted partition */
        if(0 <= SA[first]) {
          a = first;
          do { SA[num_bstar + SA[a]] = a - SA; } while((++a < last) && (0 <= SA[a]));
          first = a;
        }
        if(first < last) {
          a = first; do { SA[a] = ~SA[a]; } while(SA[++a] < 0);
          next = (incr < (num_bstar - depth)) ? ((SA[num_bstar + SA[a]] != TR_GETC(SA, depth, num_bstar, SA[a])) ? tr_ilg(a - first + 1) : -1) : -3;
          if(++a < last) { for(b = first, v = a - SA - 1; b < a; ++b) { SA[num_bstar + SA[b]] = v; } }

          /* push */
          if(trbudget_check(a - first)) {
            if((a - first) <= (last - a)) {
              ssize = STACK_PUSH5(stack, ssize, depth, a, last, -3, trlink);
              depth += incr, last = a, limit = next;
            } else {
              if(1 < (last - a)) {
                ssize = STACK_PUSH5(stack, ssize, depth + incr, first, a, next, trlink);
                first = a, limit = -3;
              } else {
                depth += incr, last = a, limit = next;
              }
            }
          } else {
            if(0 <= trlink) { stack[trlink + 3] = -1; }
            if(1 < (last - a)) {
              first = a, limit = -3;
            } else {
              // STACK_POP5(depth, first, last, limit, trlink)
              if(ssize == 0) return;
              depth = stack[ssize - 5];
              first = stack[ssize - 4];
              last = stack[ssize - 3];
              limit = stack[ssize - 2];
              trlink = stack[ssize - 1];
              ssize -= 5;
            }
          }
        } else {
          // STACK_POP5(depth, first, last, limit, trlink)
          if(ssize == 0) return;
          depth = stack[ssize - 5];
          first = stack[ssize - 4];
          last = stack[ssize - 3];
          limit = stack[ssize - 2];
          trlink = stack[ssize - 1];
          ssize -= 5;
        }
      }
      continue;
    }

    if((last - first) <= TR_INSERTIONSORT_THRESHOLD) {
      tr_insertionsort(SA, depth, num_bstar, first, last);
      limit = -3;
      continue;
    }

    if(limit-- == 0) {
      tr_heapsort(SA, depth, num_bstar, first, last - first);
      for(a = last - 1; first < a; a = b) {
        for(x = TR_GETC(SA, depth, num_bstar, SA[a]), b = a - 1; (first <= b) && (TR_GETC(SA, depth, num_bstar, SA[b]) == x); --b) { SA[b] = ~SA[b]; }
      }
      limit = -3;
      continue;
    }

    /* choose pivot */
    a = tr_pivot(SA, depth, num_bstar, first, last);
    t = SA[first]; SA[first] = SA[a]; SA[a] = t;
    v = TR_GETC(SA, depth, num_bstar, SA[first]);

    /* partition */
    range = tr_partition(SA, depth, num_bstar, first, first + 1, last, v);
    a = (int)(range >> 32);
    b = (int)range;
    if((last - first) != (b - a)) {
      next = (incr < (num_bstar - depth)) ? ((SA[num_bstar + SA[a]] != v) ? tr_ilg(b - a) : -1) : -3;

      /* update ranks */
      for(c = first, v = a - SA - 1; c < a; ++c) { SA[num_bstar + SA[c]] = v; }
      if(b < last) { for(c = a, v = b - SA - 1; c < b; ++c) { SA[num_bstar + SA[c]] = v; } }

      /* push */
      if((1 < (b - a)) && (trbudget_check(b - a))) {
        if((a - first) <= (last - b)) {
          if((last - b) <= (b - a)) {
            if(1 < (a - first)) {
              ssize = STACK_PUSH5(stack, ssize, depth + incr, a, b, next, trlink);
              ssize = STACK_PUSH5(stack, ssize, depth, b, last, limit, trlink);
              last = a;
            } else if(1 < (last - b)) {
              ssize = STACK_PUSH5(stack, ssize, depth + incr, a, b, next, trlink);
              first = b;
            } else {
              depth += incr, first = a, last = b, limit = next;
            }
          } else if((a - first) <= (b - a)) {
            if(1 < (a - first)) {
              ssize = STACK_PUSH5(stack, ssize, depth, b, last, limit, trlink);
              ssize = STACK_PUSH5(stack, ssize, depth + incr, a, b, next, trlink);
              last = a;
            } else {
              ssize = STACK_PUSH5(stack, ssize, depth, b, last, limit, trlink);
              depth += incr, first = a, last = b, limit = next;
            }
          } else {
            ssize = STACK_PUSH5(stack, ssize, depth, b, last, limit, trlink);
            ssize = STACK_PUSH5(stack, ssize, depth, first, a, limit, trlink);
            depth += incr, first = a, last = b, limit = next;
          }
        } else {
          if((a - first) <= (b - a)) {
            if(1 < (last - b)) {
              ssize = STACK_PUSH5(stack, ssize, depth + incr, a, b, next, trlink);
              ssize = STACK_PUSH5(stack, ssize, depth, first, a, limit, trlink);
              first = b;
            } else if(1 < (a - first)) {
              ssize = STACK_PUSH5(stack, ssize, depth + incr, a, b, next, trlink);
              last = a;
            } else {
              depth += incr, first = a, last = b, limit = next;
            }
          } else if((last - b) <= (b - a)) {
            if(1 < (last - b)) {
              ssize = STACK_PUSH5(stack, ssize, depth, first, a, limit, trlink);
              ssize = STACK_PUSH5(stack, ssize, depth + incr, a, b, next, trlink);
              first = b;
            } else {
              ssize = STACK_PUSH5(stack, ssize, depth, first, a, limit, trlink);
              depth += incr, first = a, last = b, limit = next;
            }
          } else {
            ssize = STACK_PUSH5(stack, ssize, depth, first, a, limit, trlink);
            ssize = STACK_PUSH5(stack, ssize, depth, b, last, limit, trlink);
            depth += incr, first = a, last = b, limit = next;
          }
        }
      } else {
        if((1 < (b - a)) && (0 <= trlink)) { stack[trlink + 3] = -1; }
        if((a - first) <= (last - b)) {
          if(1 < (a - first)) {
            ssize = STACK_PUSH5(stack, ssize, depth, b, last, limit, trlink);
            last = a;
          } else if(1 < (last - b)) {
            first = b;
          } else {
            // STACK_POP5(depth, first, last, limit, trlink)
            if(ssize == 0) return;
            depth = stack[ssize - 5];
            first = stack[ssize - 4];
            last = stack[ssize - 3];
            limit = stack[ssize - 2];
            trlink = stack[ssize - 1];
            ssize -= 5;
          }
        } else {
          if(1 < (last - b)) {
            ssize = STACK_PUSH5(stack, ssize, depth, first, a, limit, trlink);
            first = b;
          } else if(1 < (a - first)) {
            last = a;
          } else {
            // STACK_POP5(depth, first, last, limit, trlink)
            if(ssize == 0) return;
            depth = stack[ssize - 5];
            first = stack[ssize - 4];
            last = stack[ssize - 3];
            limit = stack[ssize - 2];
            trlink = stack[ssize - 1];
            ssize -= 5;
          }
        }
      }
    } else {
      if(trbudget_check(last - first)) {
        limit = (incr < (num_bstar - depth)) ? ((SA[num_bstar + SA[first]] != TR_GETC(SA, depth, num_bstar, SA[first])) ? (limit + 1) : -1) : -3;
        depth += incr;
      } else {
        if(0 <= trlink) { stack[trlink + 3] = -1; }
        // STACK_POP5(depth, first, last, limit, trlink)
        if(ssize == 0) return;
        depth = stack[ssize - 5];
        first = stack[ssize - 4];
        last = stack[ssize - 3];
        limit = stack[ssize - 2];
        trlink = stack[ssize - 1];
        ssize -= 5;
      }
    }
  }
}


/*---------------------------------------------------------------------------*/

/*- Function -*/

/* Tandem repeat sort */
private
void
trsort(int[] SA, int n, int depth) {
  int first, last, a;
  int t, skip, unsorted;

  if (-n >= SA[0]) { return; }
  trbudget_init(tr_ilg(n) * 2 / 3, n);
  for(;; depth += depth) {
    assert(n > depth);
    first = SA;
    skip = 0;
    unsorted = 0;
    do {
      if((t = SA[first]) < 0) { first -= t; skip += t; }
      else {
        if(skip != 0) { SA[first + skip] = skip; skip = 0; }
        last = SA + SA[num_bstar + t] + 1;
        if(1 < (last - first)) {
          budget.count = 0;
          tr_introsort(SA, depth, n, first, last, &budget);
          if(budget.count != 0) { unsorted += budget.count; }
          else { skip = first - last; }
        } else if((last - first) == 1) {
          skip = -1;
        }
        first = last;
      }
    } while(first < (SA + n));
    if(skip != 0) { SA[first + skip] = skip; }
    if(unsorted == 0 || -n >= SA[0]) { break; }
    if(n <= (depth) * 2) {
      do {
        if((t = SA[first]) < 0) { first -= t; }
        else {
          last = SA + SA[num_bstar + t] + 1;
          for(a = first; a < last; ++a) { SA[num_bstar + SA[a]] = a - SA; }
          first = last;
        }
      } while(first < (SA + n));
      break;
    }
  }
}


/*---- divsufsort ----*/

/*- Private Functions -*/

/* Sorts suffixes of type B*. */
private
int
sort_typeBstar(byte[] T, int[] SA,
               int[] bucket, int n) {
  int xpa;
  int buf;
  int i, j, k, t, m, bufsize;
  int c0, c1;
  int flag;

  /* Initialize bucket arrays. */
  bzero(bucket, (ALPHABET_SIZE + 1) * ALPHABET_SIZE * sizeof(int));

  /* Count the number of occurrences of the first one or two characters of each
     type A, B and B* suffix. Moreover, store the beginning position of all
     type B* suffixes into the array SA. */
  for(i = 1, flag = 1; i < n; ++i) {
    if(T[i - 1] != T[i]) {
      if(T[i - 1] > T[i]) { flag = 0; }
      break;
    }
  }
  i = n - 1, m = n, c0 = T[n - 1], c1 = T[0];
  if((c0 < c1) || ((c0 == c1) && (flag != 0))) {
    if(flag == 0) { ++BUCKET_BSTAR(bucket, c0, c1); SA[--m] = i; }
    else { ++BUCKET_B(bucket, c0, c1); }
    for(--i, c1 = c0; (0 <= i) && ((c0 = T[i]) <= c1); --i, c1 = c0) { ++BUCKET_B(bucket, c0, c1); }
  }
  for(; 0 <= i;) {
    /* type A suffix. */
    do { ++BUCKET_A(bucket, c1 = c0); } while((0 <= --i) && ((c0 = T[i]) >= c1));
    if(0 <= i) {
      /* type B* suffix. */
      ++BUCKET_BSTAR(bucket, c0, c1);
      SA[--m] = i;
      /* type B suffix. */
      for(--i, c1 = c0; (0 <= i) && ((c0 = T[i]) <= c1); --i, c1 = c0) {
        ++BUCKET_B(bucket, c0, c1);
      }
    }
  }
  m = n - m;
  assert(m <= n/2);
  if(m == 0) { return 0; }
/*
note:
  A type B* suffix is lexicographically smaller than a type B suffix that
  begins with the same first two characters.
*/

  /* Calculate the index of start/end point of each bucket. */
  for(c0 = 0, i = 0, j = 0; c0 < ALPHABET_SIZE; ++c0) {
    t = i + BUCKET_A(bucket, c0);
    BUCKET_A(bucket, c0) = i + j; /* start point */
    i = t + BUCKET_B(bucket, c0, c0);
    for(c1 = c0 + 1; c1 < ALPHABET_SIZE; ++c1) {
      j += BUCKET_BSTAR(bucket, c0, c1);
      BUCKET_BSTAR(bucket, c0, c1) = j; /* end point */
      i += BUCKET_B(bucket, c0, c1);
    }
  }

  /* Sort the type B* suffixes by their first two characters. */
  xpa = n - m;
  for(i = m - 2; 0 <= i; --i) {
    t = SA[xpa + i], c0 = T[t], c1 = T[t + 1];
    SA[--BUCKET_BSTAR(bucket, c0, c1)] = i;
  }
  t = SA[xpa + m - 1], c0 = T[t], c1 = T[t + 1];
  SA[--BUCKET_BSTAR(bucket, c0, c1)] = m - 1;

  /* Sort the type B* substrings using sssort. */
  buf = SA + m, bufsize = n - (2 * m);
  for(c0 = ALPHABET_SIZE - 2, j = m; 0 < j; --c0) {
    for(c1 = ALPHABET_SIZE - 1; c0 < c1; j = i, --c1) {
      i = BUCKET_BSTAR(bucket, c0, c1);
      if(1 < (j - i)) {
        sssort(T, SA, xpa, SA + i, SA + j,
                buf, bufsize, 2, n, SA[i] == (m - 1));
      }
    }
  }

  /* Compute ranks of type B* substrings. */
  for(i = m - 1; 0 <= i; --i) {
    if(0 <= SA[i]) {
      j = i;
      do { SA[SA[i] + m] = i; } while((0 <= --i) && (0 <= SA[i]));
      SA[i + 1] = i - j;
      if(i <= 0) { break; }
    }
    j = i;
    do { SA[(SA[i] = ~SA[i]) + m] = j; } while(SA[--i] < 0);
    SA[SA[i] + m] = j;
  }

  /* Construct the inverse suffix array of type B* suffixes using trsort. */
  trsort(SA, m, 1);

  /* Set the sorted order of type B* suffixes. */
  i = n - 1, j = m, c0 = T[n - 1], c1 = T[0];
  if((c0 < c1) || ((c0 == c1) && (flag != 0))) {
    t = i;
    for(--i, c1 = c0; (0 <= i) && ((c0 = T[i]) <= c1); --i, c1 = c0) { }
    if(flag == 0) { SA[SA[--j + m]] = ((1 < (t - i))) ? t : ~t; }
  }
  for(; 0 <= i;) {
    for(--i, c1 = c0; (0 <= i) && ((c0 = T[i]) >= c1); --i, c1 = c0) { }
    if(0 <= i) {
      t = i;
      for(--i, c1 = c0; (0 <= i) && ((c0 = T[i]) <= c1); --i, c1 = c0) { }
      SA[SA[--j + m]] = ((1 < (t - i))) ? t : ~t;
    }
  }
  if(SA[SA[m]] == ~((int)0)) {
    /* check last type */
    if(T[n - 1] <= T[0]) { /* is type B? */
      SA[SA[m]] = 0;
    }
  }

  if(DEBUG) {
    for(i = m; i < n; ++i) {
      SA[i] = ~n;
    }
  }

  /* Calculate the index of start/end point of each bucket. */
  BUCKET_B(bucket, ALPHABET_SIZE - 1, ALPHABET_SIZE - 1) = n; /* end point */
  for(c0 = ALPHABET_SIZE - 2, k = m - 1; 0 <= c0; --c0) {
    i = BUCKET_A(bucket, c0 + 1) - 1;
    for(c1 = ALPHABET_SIZE - 1; c0 < c1; --c1) {
      t = i - BUCKET_B(bucket, c0, c1);
      BUCKET_B(bucket, c0, c1) = i; /* end point */

      /* Move all type B* suffixes to the correct position. */
      for(i = t, j = BUCKET_BSTAR(bucket, c0, c1);
          j <= k;
          --i, --k) { SA[i] = SA[k]; }
    }
    BUCKET_BSTAR(bucket, c0, c0 + 1) = i - BUCKET_B(bucket, c0, c0) + 1; /* start point */
    BUCKET_B(bucket, c0, c0) = i; /* end point */
  }

  return m;
}

private
int
construct_BWT(byte[] T, int SA,
              int bucket, int n) {
  int i, j, k;
  int s, t, orig = -10;
  int c0, c1, c2;

  /* Construct the sorted order of type B suffixes by using
     the sorted order of type B* suffixes. */
  for(c1 = ALPHABET_SIZE - 2; 0 <= c1; --c1) {
    /* Scan the suffix array from right to left. */
    for(i = SA + BUCKET_BSTAR(bucket, c1, c1 + 1),
        j = SA + BUCKET_A(bucket, c1 + 1) - 1, k = NULL, c2 = -1;
        i <= j;
        --j) {
      if(0 <= (s = SA[j])) {
        assert(s < n);
        assert(T[s] == c1);
        assert(T[s] <= T[((s + 1) < n) ? (s + 1) : (0)]);
        if(s != 0) { t = s - 1; }
        else { t = n - 1; orig = j - SA; }
        assert(T[t] <= T[s]);
        c0 = T[t];
        SA[j] = ~((int)c0);
        if(c0 != c2) {
          if(0 <= c2) { BUCKET_B(bucket, c2, c1) = k - SA; }
          k = SA + BUCKET_B(bucket, c2 = c0, c1);
        }
        assert(k < j);
        SA[k]-- = (((t != 0) ? T[t - 1] : T[n - 1]) > c2) ? ~t : t;
      } else {
        SA[j] = ~s;
        assert(~s < n);
      }
    }
  }

  /* Construct the BWTed string by using
     the sorted order of type B suffixes. */
  k = SA + BUCKET_A(bucket, c2 = 0);
  /* Scan the suffix array from left to right. */
  for(i = SA, j = SA + n; i < j; ++i) {
    if(0 <= (s = SA[i])) {
      if(s != 0) { t = s - 1; }
      else { t = n - 1; orig = i - SA; }
      assert(T[t] >= T[s]);
      c0 = T[t];
      SA[i] = c0;
      if(c0 != c2) {
        BUCKET_A(bucket, c2) = k - SA;
        k = SA + BUCKET_A(bucket, c2 = c0);
      }
      if(t != 0) { c1 = T[t - 1]; }
      else { c1 = T[n - 1]; orig = k - SA; }
      assert(i <= k);
      SA[k]++ = (c1 < c2) ? ~((int)c1) : t;
    } else {
      SA[i] = ~s;
    }
  }

  assert(orig != -10);
  assert((0 <= orig) && (orig < n));
  return orig;
}


/*---------------------------------------------------------------------------*/

/*- Function -*/

int
divbwt(byte[] T, int[] SA, int[] bucket, int n) {
  int m, pidx, i;

  /* Check arguments. */
  assert(n > 0);
  if(n == 1) { SA[0] = T[0]; return 0; }

  T[n] = T[0];

  /* Burrows-Wheeler Transform. */
  m = sort_typeBstar(T, SA, bucket, n);
  if(0 < m) {
    pidx = construct_BWT(T, SA, bucket, n);
  } else {
    pidx = 0;
    for(i = 0; i < n; ++i) { SA[i] = T[0]; }
  }

  return pidx;
}
}