/* ***** BEGIN LICENSE BLOCK *****
 * JTransforms
 * Copyright (c) 2007 onward, Piotr Wendykier
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer. 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * ***** END LICENSE BLOCK ***** */
package com.deo.colorcontrol.jtransforms.fft;

import com.deo.colorcontrol.jtransforms.utils.FFTUtils;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import pl.edu.icm.jlargearrays.ConcurrencyUtils;
import pl.edu.icm.jlargearrays.FloatLargeArray;
import pl.edu.icm.jlargearrays.LargeArray;
import pl.edu.icm.jlargearrays.LargeArrayUtils;
import pl.edu.icm.jlargearrays.LongLargeArray;

import static org.apache.commons.math3.util.FastMath.ceil;
import static org.apache.commons.math3.util.FastMath.cos;
import static org.apache.commons.math3.util.FastMath.log;
import static org.apache.commons.math3.util.FastMath.sin;

/**
 * Computes 1D Discrete Fourier Transform (DFT) of complex and real, single
 * precision data. The size of the data can be an arbitrary number. This is a
 * parallel implementation of split-radix and mixed-radix algorithms optimized
 * for SMP systems. <br>
 * <br>
 * This code is derived from General Purpose FFT Package written by Takuya Ooura
 * (http://www.kurims.kyoto-u.ac.jp/~ooura/fft.html) and from JFFTPack written
 * by Baoshe Zhang (http://jfftpack.sourceforge.net/)
 *  
 * @author Piotr Wendykier (piotr.wendykier@gmail.com)
 */
public final class FloatFFT_1D
{

    private enum Plans
    {

        SPLIT_RADIX, MIXED_RADIX, BLUESTEIN
    }

    private final int n;

    private final long nl;

    private int nBluestein;

    private long nBluesteinl;

    private int[] ip;

    private LongLargeArray ipl;

    private float[] w;

    private FloatLargeArray wl;

    private int nw;

    private long nwl;

    private int nc;

    private long ncl;

    private float[] wtable;

    private FloatLargeArray wtablel;

    private float[] wtable_r;

    private FloatLargeArray wtable_rl;

    private float[] bk1;

    private FloatLargeArray bk1l;

    private float[] bk2;

    private FloatLargeArray bk2l;

    private final Plans plan;

    private final boolean useLargeArrays;

    private static final int[] factors = {4, 2, 3, 5};

    private static final float PI = 3.14159265358979311599796346854418516f;

    private static final float TWO_PI = 6.28318530717958623199592693708837032f;

    /**
     * Creates new instance of FloatFFT_1D.
     *  
     * @param n size of data
     */
    public FloatFFT_1D(long n)
    {
        if (n < 1) {
            throw new IllegalArgumentException("n must be greater than 0");
        }
        this.useLargeArrays = (FFTUtils.isUseLargeArrays() || 2 * n > LargeArray.getMaxSizeOf32bitArray());
        this.n = (int) n;
        this.nl = n;
        if (!this.useLargeArrays) {
            if (!FFTUtils.isPowerOf2(n)) {
                if (FFTUtils.getReminder(n, factors) >= 211) {
                    plan = Plans.BLUESTEIN;
                    nBluestein = FFTUtils.nextPow2(this.n * 2 - 1);
                    bk1 = new float[2 * nBluestein];
                    bk2 = new float[2 * nBluestein];
                    this.ip = new int[2 + (int) ceil(2 + (1 << (int) (log(nBluestein + 0.5f) / log(2)) / 2))];
                    this.w = new float[nBluestein];
                    int twon = 2 * nBluestein;
                    nw = twon >> 2;
                    FFTUtils.makewt(nw, ip, w);
                    nc = nBluestein >> 2;
                    FFTUtils.makect(nc, w, nw, ip);
                    bluesteini();
                } else {
                    plan = Plans.MIXED_RADIX;
                    wtable = new float[4 * this.n + 15];
                    wtable_r = new float[2 * this.n + 15];
                    cffti();
                    rffti();
                }
            } else {
                plan = Plans.SPLIT_RADIX;
                this.ip = new int[2 + (int) ceil(2 + (1 << (int) (log(n + 0.5f) / log(2)) / 2))];
                this.w = new float[this.n];
                int twon = 2 * this.n;
                nw = twon >> 2;
                FFTUtils.makewt(nw, ip, w);
                nc = this.n >> 2;
                FFTUtils.makect(nc, w, nw, ip);
            }
        } else if (!FFTUtils.isPowerOf2(nl)) {
            if (FFTUtils.getReminder(nl, factors) >= 211) {
                plan = Plans.BLUESTEIN;
                nBluesteinl = FFTUtils.nextPow2(nl * 2 - 1);
                bk1l = new FloatLargeArray(2L * nBluesteinl);
                bk2l = new FloatLargeArray(2L * nBluesteinl);
                this.ipl = new LongLargeArray(2L + (long) ceil(2L + (1L << (long) (log(nBluesteinl + 0.5f) / log(2.)) / 2)));
                this.wl = new FloatLargeArray(nBluesteinl);
                long twon = 2 * nBluesteinl;
                nwl = twon >> 2L;
                FFTUtils.makewt(nwl, ipl, wl);
                ncl = nBluesteinl >> 2L;
                FFTUtils.makect(ncl, wl, nwl, ipl);
                bluesteinil();
            } else {
                plan = Plans.MIXED_RADIX;
                wtablel = new FloatLargeArray(4 * nl + 15);
                wtable_rl = new FloatLargeArray(2 * nl + 15);
                cfftil();
                rfftil();
            }
        } else {
            plan = Plans.SPLIT_RADIX;
            this.ipl = new LongLargeArray(2L + (long) ceil(2 + (1L << (long) (log(nl + 0.5f) / log(2)) / 2)));
            this.wl = new FloatLargeArray(nl);
            long twon = 2 * nl;
            nwl = twon >> 2L;
            FFTUtils.makewt(nwl, ipl, wl);
            ncl = nl >> 2L;
            FFTUtils.makect(ncl, wl, nwl, ipl);
        }
    }
    
    /**
     * Computes 1D forward DFT of real data leaving the result in <code>a</code>
     * . The physical layout of the output data is as follows:<br>
     *  
     * if n is even then
     *  
     * <pre>
     * a[2*k] = Re[k], 0&lt;=k&lt;n/2 
     * a[2*k+1] = Im[k], 0&lt;k&lt;n/2 
     * a[1] = Re[n/2]
     * </pre>
     *  
     * if n is odd then
     *  
     * <pre>
     * a[2*k] = Re[k], 0&lt;=k&lt;(n+1)/2 
     * a[2*k+1] = Im[k], 0&lt;k&lt;(n-1)/2
     * a[1] = Im[(n-1)/2]
     * </pre>
     *  
     * This method computes only half of the elements of the real transform. The
     * other half satisfies the symmetry condition. If you want the full real
     * forward transform, use <code>realForwardFull</code>. To get back the
     * original data, use <code>realInverse</code> on the output of this method.
     *  
     * @param a data to transform
     */
    public void realForward(float[] a)
    {
        realForward(a, 0);
    }
    
    /**
     * Computes 1D forward DFT of real data leaving the result in <code>a</code>
     * . The physical layout of the output data is as follows:<br>
     *  
     * if n is even then
     *  
     * <pre>
     * a[offa+2*k] = Re[k], 0&lt;=k&lt;n/2 
     * a[offa+2*k+1] = Im[k], 0&lt;k&lt;n/2
     * a[offa+1] = Re[n/2]
     * </pre>
     *  
     * if n is odd then
     *  
     * <pre>
     * a[offa+2*k] = Re[k], 0&lt;=k&lt;(n+1)/2 
     * a[offa+2*k+1] = Im[k], 0&lt;k&lt;(n-1)/2 
     * a[offa+1] = Im[(n-1)/2]
     * </pre>
     *  
     * This method computes only half of the elements of the real transform. The
     * other half satisfies the symmetry condition. If you want the full real
     * forward transform, use <code>realForwardFull</code>. To get back the
     * original data, use <code>realInverse</code> on the output of this method.
     *  
     * @param a    data to transform
     * @param offa index of the first element in array <code>a</code>
     */
    public void realForward(float[] a, int offa)
    {
        if (useLargeArrays) {
            realForward(new FloatLargeArray(a), offa);
        } else {
            if (n == 1) {
                return;
            }

            switch (plan) {
                case SPLIT_RADIX:
                    float xi;

                    if (n > 4) {
                        FFTUtils.cftfsub(n, a, offa, ip, nw, w);
                        FFTUtils.rftfsub(n, a, offa, nc, w, nw);
                    } else if (n == 4) {
                        FFTUtils.cftx020(a, offa);
                    }
                    xi = a[offa] - a[offa + 1];
                    a[offa] += a[offa + 1];
                    a[offa + 1] = xi;
                    break;
                case MIXED_RADIX:
                    rfftf(a, offa);
                    for (int k = n - 1; k >= 2; k--) {
                        int idx = offa + k;
                        float tmp = a[idx];
                        a[idx] = a[idx - 1];
                        a[idx - 1] = tmp;
                    }
                    break;
                case BLUESTEIN:
                    bluestein_real_forward(a, offa);
                    break;
            }
        }
    }

    /**
     * Computes 1D forward DFT of real data leaving the result in <code>a</code>
     * . The physical layout of the output data is as follows:<br>
     *  
     * if n is even then
     *  
     * <pre>
     * a[offa+2*k] = Re[k], 0&lt;=k&lt;n/2 
     * a[offa+2*k+1] = Im[k], 0&lt;k&lt;n/2
     * a[offa+1] = Re[n/2]
     * </pre>
     *  
     * if n is odd then
     *  
     * <pre>
     * a[offa+2*k] = Re[k], 0&lt;=k&lt;(n+1)/2 
     * a[offa+2*k+1] = Im[k], 0&lt;k&lt;(n-1)/2 
     * a[offa+1] = Im[(n-1)/2]
     * </pre>
     *  
     * This method computes only half of the elements of the real transform. The
     * other half satisfies the symmetry condition. If you want the full real
     * forward transform, use <code>realForwardFull</code>. To get back the
     * original data, use <code>realInverse</code> on the output of this method.
     *  
     * @param a    data to transform
     * @param offa index of the first element in array <code>a</code>
     */
    public void realForward(FloatLargeArray a, long offa)
    {
        if (!useLargeArrays) {
            if (!a.isLarge() && !a.isConstant() && offa < Integer.MAX_VALUE) {
                realForward(a.getData(), (int) offa);
            } else {
                throw new IllegalArgumentException("The data array is too big.");
            }
        } else {
            if (nl == 1) {
                return;
            }

            switch (plan) {
                case SPLIT_RADIX:
                    float xi;

                    if (nl > 4) {
                        FFTUtils.cftfsub(nl, a, offa, ipl, nwl, wl);
                        FFTUtils.rftfsub(nl, a, offa, ncl, wl, nwl);
                    } else if (nl == 4) {
                        FFTUtils.cftx020(a, offa);
                    }
                    xi = a.getFloat(offa) - a.getFloat(offa + 1);
                    a.setFloat(offa, a.getFloat(offa) + a.getFloat(offa + 1));
                    a.setFloat(offa + 1, xi);
                    break;
                case MIXED_RADIX:
                    rfftf(a, offa);
                    for (long k = nl - 1; k >= 2; k--) {
                        long idx = offa + k;
                        float tmp = a.getFloat(idx);
                        a.setFloat(idx, a.getFloat(idx - 1));
                        a.setFloat(idx - 1, tmp);
                    }
                    break;
                case BLUESTEIN:
                    bluestein_real_forward(a, offa);
                    break;
            }
        }
    }
    
    final void cffti()
    {
        if (n == 1) {
            return;
        }

        final int twon = 2 * n;
        final int fourn = 4 * n;
        float argh;
        int idot, ntry = 0, i, j;
        float argld;
        int i1, k1, l1, l2, ib;
        float fi;
        int ld, ii, nf, ipll, nll, nq, nr;
        float arg;
        int ido, ipm;

        nll = n;
        nf = 0;
        j = 0;

        factorize_loop:
        while (true) {
            j++;
            if (j <= 4) {
                ntry = factors[j - 1];
            } else {
                ntry += 2;
            }
            do {
                nq = nll / ntry;
                nr = nll - ntry * nq;
                if (nr != 0) {
                    continue factorize_loop;
                }
                nf++;
                wtable[nf + 1 + fourn] = ntry;
                nll = nq;
                if (ntry == 2 && nf != 1) {
                    for (i = 2; i <= nf; i++) {
                        ib = nf - i + 2;
                        int idx = ib + fourn;
                        wtable[idx + 1] = wtable[idx];
                    }
                    wtable[2 + fourn] = 2;
                }
            } while (nll != 1);
            break;
        }
        wtable[fourn] = n;
        wtable[1 + fourn] = nf;
        argh = TWO_PI / (float) n;
        i = 1;
        l1 = 1;
        for (k1 = 1; k1 <= nf; k1++) {
            ipll = (int) wtable[k1 + 1 + fourn];
            ld = 0;
            l2 = l1 * ipll;
            ido = n / l2;
            idot = ido + ido + 2;
            ipm = ipll - 1;
            for (j = 1; j <= ipm; j++) {
                i1 = i;
                wtable[i - 1 + twon] = 1;
                wtable[i + twon] = 0;
                ld += l1;
                fi = 0;
                argld = ld * argh;
                for (ii = 4; ii <= idot; ii += 2) {
                    i += 2;
                    fi += 1;
                    arg = fi * argld;
                    int idx = i + twon;
                    wtable[idx - 1] = (float) cos(arg);
                    wtable[idx] = (float) sin(arg);
                }
                if (ipll > 5) {
                    int idx1 = i1 + twon;
                    int idx2 = i + twon;
                    wtable[idx1 - 1] = wtable[idx2 - 1];
                    wtable[idx1] = wtable[idx2];
                }
            }
            l1 = l2;
        }

    }

    final void cfftil()
    {
        if (nl == 1) {
            return;
        }

        final long twon = 2 * nl;
        final long fourn = 4 * nl;
        float argh;
        long idot, ntry = 0, i, j;
        float argld;
        long i1, k1, l1, l2, ib;
        float fi;
        long ld, ii, nf, ipll, nl2, nq, nr;
        float arg;
        long ido, ipm;

        nl2 = nl;
        nf = 0;
        j = 0;

        factorize_loop:
        while (true) {
            j++;
            if (j <= 4) {
                ntry = factors[(int) (j - 1)];
            } else {
                ntry += 2;
            }
            do {
                nq = nl2 / ntry;
                nr = nl2 - ntry * nq;
                if (nr != 0) {
                    continue factorize_loop;
                }
                nf++;
                wtablel.setFloat(nf + 1 + fourn, ntry);
                nl2 = nq;
                if (ntry == 2 && nf != 1) {
                    for (i = 2; i <= nf; i++) {
                        ib = nf - i + 2;
                        long idx = ib + fourn;
                        wtablel.setFloat(idx + 1, wtablel.getFloat(idx));
                    }
                    wtablel.setFloat(2 + fourn, 2);
                }
            } while (nl2 != 1);
            break;
        }
        wtablel.setFloat(fourn, nl);
        wtablel.setFloat(1 + fourn, nf);
        argh = TWO_PI / (float) nl;
        i = 1;
        l1 = 1;
        for (k1 = 1; k1 <= nf; k1++) {
            ipll = (long) wtablel.getFloat(k1 + 1 + fourn);
            ld = 0;
            l2 = l1 * ipll;
            ido = nl / l2;
            idot = ido + ido + 2;
            ipm = ipll - 1;
            for (j = 1; j <= ipm; j++) {
                i1 = i;
                wtablel.setFloat(i - 1 + twon, 1);
                wtablel.setFloat(i + twon, 0);
                ld += l1;
                fi = 0;
                argld = ld * argh;
                for (ii = 4; ii <= idot; ii += 2) {
                    i += 2;
                    fi += 1;
                    arg = fi * argld;
                    long idx = i + twon;
                    wtablel.setFloat(idx - 1, (float) cos(arg));
                    wtablel.setFloat(idx, (float) sin(arg));
                }
                if (ipll > 5) {
                    long idx1 = i1 + twon;
                    long idx2 = i + twon;
                    wtablel.setFloat(idx1 - 1, wtablel.getFloat(idx2 - 1));
                    wtablel.setFloat(idx1, wtablel.getFloat(idx2));
                }
            }
            l1 = l2;
        }

    }

    void rffti()
    {

        if (n == 1) {
            return;
        }
        final int twon = 2 * n;
        float argh;
        int ntry = 0, i, j;
        float argld;
        int k1, l1, l2, ib;
        float fi;
        int ld, ii, nf, ipll, nll, is, nq, nr;
        float arg;
        int ido, ipm;
        int nfm1;

        nll = n;
        nf = 0;
        j = 0;

        factorize_loop:
        while (true) {
            ++j;
            if (j <= 4) {
                ntry = factors[j - 1];
            } else {
                ntry += 2;
            }
            do {
                nq = nll / ntry;
                nr = nll - ntry * nq;
                if (nr != 0) {
                    continue factorize_loop;
                }
                ++nf;
                wtable_r[nf + 1 + twon] = ntry;

                nll = nq;
                if (ntry == 2 && nf != 1) {
                    for (i = 2; i <= nf; i++) {
                        ib = nf - i + 2;
                        int idx = ib + twon;
                        wtable_r[idx + 1] = wtable_r[idx];
                    }
                    wtable_r[2 + twon] = 2;
                }
            } while (nll != 1);
            break;
        }
        wtable_r[twon] = n;
        wtable_r[1 + twon] = nf;
        argh = TWO_PI / (float) (n);
        is = 0;
        nfm1 = nf - 1;
        l1 = 1;
        if (nfm1 == 0) {
            return;
        }
        for (k1 = 1; k1 <= nfm1; k1++) {
            ipll = (int) wtable_r[k1 + 1 + twon];
            ld = 0;
            l2 = l1 * ipll;
            ido = n / l2;
            ipm = ipll - 1;
            for (j = 1; j <= ipm; ++j) {
                ld += l1;
                i = is;
                argld = (float) ld * argh;

                fi = 0;
                for (ii = 3; ii <= ido; ii += 2) {
                    i += 2;
                    fi += 1;
                    arg = fi * argld;
                    int idx = i + n;
                    wtable_r[idx - 2] = (float) cos(arg);
                    wtable_r[idx - 1] = (float) sin(arg);
                }
                is += ido;
            }
            l1 = l2;
        }
    }

    void rfftil()
    {

        if (nl == 1) {
            return;
        }
        final long twon = 2 * nl;
        float argh;
        long ntry = 0, i, j;
        float argld;
        long k1, l1, l2, ib;
        float fi;
        long ld, ii, nf, ipll, nl2, is, nq, nr;
        float arg;
        long ido, ipm;
        long nfm1;

        nl2 = nl;
        nf = 0;
        j = 0;

        factorize_loop:
        while (true) {
            ++j;
            if (j <= 4) {
                ntry = factors[(int) (j - 1)];
            } else {
                ntry += 2;
            }
            do {
                nq = nl2 / ntry;
                nr = nl2 - ntry * nq;
                if (nr != 0) {
                    continue factorize_loop;
                }
                ++nf;
                wtable_rl.setFloat(nf + 1 + twon, ntry);

                nl2 = nq;
                if (ntry == 2 && nf != 1) {
                    for (i = 2; i <= nf; i++) {
                        ib = nf - i + 2;
                        long idx = ib + twon;
                        wtable_rl.setFloat(idx + 1, wtable_rl.getFloat(idx));
                    }
                    wtable_rl.setFloat(2 + twon, 2);
                }
            } while (nl2 != 1);
            break;
        }
        wtable_rl.setFloat(twon, nl);
        wtable_rl.setFloat(1 + twon, nf);
        argh = TWO_PI / (float) (nl);
        is = 0;
        nfm1 = nf - 1;
        l1 = 1;
        if (nfm1 == 0) {
            return;
        }
        for (k1 = 1; k1 <= nfm1; k1++) {
            ipll = (long) wtable_rl.getFloat(k1 + 1 + twon);
            ld = 0;
            l2 = l1 * ipll;
            ido = nl / l2;
            ipm = ipll - 1;
            for (j = 1; j <= ipm; ++j) {
                ld += l1;
                i = is;
                argld = (float) ld * argh;

                fi = 0;
                for (ii = 3; ii <= ido; ii += 2) {
                    i += 2;
                    fi += 1;
                    arg = fi * argld;
                    long idx = i + nl;
                    wtable_rl.setFloat(idx - 2, (float) cos(arg));
                    wtable_rl.setFloat(idx - 1, (float) sin(arg));
                }
                is += ido;
            }
            l1 = l2;
        }
    }

    private void bluesteini()
    {
        int k = 0;
        float arg;
        float pi_n = PI / n;
        bk1[0] = 1;
        bk1[1] = 0;
        for (int i = 1; i < n; i++) {
            k += 2 * i - 1;
            if (k >= 2 * n) {
                k -= 2 * n;
            }
            arg = pi_n * k;
            bk1[2 * i] = (float) cos(arg);
            bk1[2 * i + 1] = (float) sin(arg);
        }
        float scale = 1.0f / nBluestein;
        bk2[0] = bk1[0] * scale;
        bk2[1] = bk1[1] * scale;
        for (int i = 2; i < 2 * n; i += 2) {
            bk2[i] = bk1[i] * scale;
            bk2[i + 1] = bk1[i + 1] * scale;
            bk2[2 * nBluestein - i] = bk2[i];
            bk2[2 * nBluestein - i + 1] = bk2[i + 1];
        }
        FFTUtils.cftbsub(2 * nBluestein, bk2, 0, ip, nw, w);
    }

    private void bluesteinil()
    {
        long k = 0;
        float arg;
        float pi_n = PI / nl;
        bk1l.setFloat(0, 1);
        bk1l.setFloat(1, 0);
        for (int i = 1; i < nl; i++) {
            k += 2 * i - 1;
            if (k >= 2 * nl) {
                k -= 2 * nl;
            }
            arg = pi_n * k;
            bk1l.setFloat(2 * i, (float) cos(arg));
            bk1l.setFloat(2 * i + 1, (float) sin(arg));
        }
        float scale = 1.0f / nBluesteinl;
        bk2l.setFloat(0, bk1l.getFloat(0) * scale);
        bk2l.setFloat(1, bk1l.getFloat(1) * scale);
        for (int i = 2; i < 2 * nl; i += 2) {
            bk2l.setFloat(i, bk1l.getFloat(i) * scale);
            bk2l.setFloat(i + 1, bk1l.getFloat(i + 1) * scale);
            bk2l.setFloat(2 * nBluesteinl - i, bk2l.getFloat(i));
            bk2l.setFloat(2 * nBluesteinl - i + 1, bk2l.getFloat(i + 1));
        }
        FFTUtils.cftbsub(2 * nBluesteinl, bk2l, 0, ipl, nwl, wl);
    }
    
    private void bluestein_real_forward(final float[] a, final int offa)
    {
        final float[] ak = new float[2 * nBluestein];
        int threads = ConcurrencyUtils.getNumberOfThreads();
        if ((threads > 1) && (n >= FFTUtils.getThreadsBeginN_1D_FFT_2Threads())) {
            int nthreads = 2;
            if ((threads >= 4) && (n >= FFTUtils.getThreadsBeginN_1D_FFT_4Threads())) {
                nthreads = 4;
            }
            Future<?>[] futures = new Future[nthreads];
            int k = n / nthreads;
            for (int i = 0; i < nthreads; i++) {
                final int firstIdx = i * k;
                final int lastIdx = (i == (nthreads - 1)) ? n : firstIdx + k;
                futures[i] = ConcurrencyUtils.submit(new Runnable()
                {
                    public void run()
                    {
                        for (int i = firstIdx; i < lastIdx; i++) {
                            int idx1 = 2 * i;
                            int idx2 = idx1 + 1;
                            int idx3 = offa + i;
                            ak[idx1] = a[idx3] * bk1[idx1];
                            ak[idx2] = -a[idx3] * bk1[idx2];
                        }
                    }
                });
            }
            try {
                ConcurrencyUtils.waitForCompletion(futures);
            } catch (InterruptedException | ExecutionException ex) {
                Logger.getLogger(FloatFFT_1D.class.getName()).log(Level.SEVERE, null, ex);
            }
    
            FFTUtils.cftbsub(2 * nBluestein, ak, 0, ip, nw, w);

            k = nBluestein / nthreads;
            for (int i = 0; i < nthreads; i++) {
                final int firstIdx = i * k;
                final int lastIdx = (i == (nthreads - 1)) ? nBluestein : firstIdx + k;
                futures[i] = ConcurrencyUtils.submit(new Runnable()
                {
                    public void run()
                    {
                        for (int i = firstIdx; i < lastIdx; i++) {
                            int idx1 = 2 * i;
                            int idx2 = idx1 + 1;
                            float im = ak[idx1] * bk2[idx2] + ak[idx2] * bk2[idx1];
                            ak[idx1] = ak[idx1] * bk2[idx1] - ak[idx2] * bk2[idx2];
                            ak[idx2] = im;
                        }
                    }
                });
            }
            try {
                ConcurrencyUtils.waitForCompletion(futures);
            } catch (InterruptedException | ExecutionException ex) {
                Logger.getLogger(FloatFFT_1D.class.getName()).log(Level.SEVERE, null, ex);
            }
    
        } else {

            for (int i = 0; i < n; i++) {
                int idx1 = 2 * i;
                int idx2 = idx1 + 1;
                int idx3 = offa + i;
                ak[idx1] = a[idx3] * bk1[idx1];
                ak[idx2] = -a[idx3] * bk1[idx2];
            }

            FFTUtils.cftbsub(2 * nBluestein, ak, 0, ip, nw, w);

            for (int i = 0; i < nBluestein; i++) {
                int idx1 = 2 * i;
                int idx2 = idx1 + 1;
                float im = ak[idx1] * bk2[idx2] + ak[idx2] * bk2[idx1];
                ak[idx1] = ak[idx1] * bk2[idx1] - ak[idx2] * bk2[idx2];
                ak[idx2] = im;
            }
        }

        FFTUtils.cftfsub(2 * nBluestein, ak, 0, ip, nw, w);

        if (n % 2 == 0) {
            a[offa] = bk1[0] * ak[0] + bk1[1] * ak[1];
            a[offa + 1] = bk1[n] * ak[n] + bk1[n + 1] * ak[n + 1];
            for (int i = 1; i < n / 2; i++) {
                int idx1 = 2 * i;
                int idx2 = idx1 + 1;
                a[offa + idx1] = bk1[idx1] * ak[idx1] + bk1[idx2] * ak[idx2];
                a[offa + idx2] = -bk1[idx2] * ak[idx1] + bk1[idx1] * ak[idx2];
            }
        } else {
            a[offa] = bk1[0] * ak[0] + bk1[1] * ak[1];
            a[offa + 1] = -bk1[n] * ak[n - 1] + bk1[n - 1] * ak[n];
            for (int i = 1; i < (n - 1) / 2; i++) {
                int idx1 = 2 * i;
                int idx2 = idx1 + 1;
                a[offa + idx1] = bk1[idx1] * ak[idx1] + bk1[idx2] * ak[idx2];
                a[offa + idx2] = -bk1[idx2] * ak[idx1] + bk1[idx1] * ak[idx2];
            }
            a[offa + n - 1] = bk1[n - 1] * ak[n - 1] + bk1[n] * ak[n];
        }

    }

    private void bluestein_real_forward(final FloatLargeArray a, final long offa)
    {
        final FloatLargeArray ak = new FloatLargeArray(2 * nBluesteinl);
        int threads = ConcurrencyUtils.getNumberOfThreads();
        if ((threads > 1) && (nl > FFTUtils.getThreadsBeginN_1D_FFT_2Threads())) {
            int nthreads = 2;
            if ((threads >= 4) && (nl > FFTUtils.getThreadsBeginN_1D_FFT_4Threads())) {
                nthreads = 4;
            }
            Future<?>[] futures = new Future[nthreads];
            long k = nl / nthreads;
            for (int i = 0; i < nthreads; i++) {
                final long firstIdx = i * k;
                final long lastIdx = (i == (nthreads - 1)) ? nl : firstIdx + k;
                futures[i] = ConcurrencyUtils.submit(new Runnable()
                {
                    public void run()
                    {
                        for (long i = firstIdx; i < lastIdx; i++) {
                            long idx1 = 2 * i;
                            long idx2 = idx1 + 1;
                            long idx3 = offa + i;
                            ak.setFloat(idx1, a.getFloat(idx3) * bk1l.getFloat(idx1));
                            ak.setFloat(idx2, -a.getFloat(idx3) * bk1l.getFloat(idx2));
                        }
                    }
                });
            }
            try {
                ConcurrencyUtils.waitForCompletion(futures);
            } catch (InterruptedException | ExecutionException ex) {
                Logger.getLogger(FloatFFT_1D.class.getName()).log(Level.SEVERE, null, ex);
            }
    
            FFTUtils.cftbsub(2 * nBluesteinl, ak, 0, ipl, nwl, wl);

            k = nBluesteinl / nthreads;
            for (int i = 0; i < nthreads; i++) {
                final long firstIdx = i * k;
                final long lastIdx = (i == (nthreads - 1)) ? nBluesteinl : firstIdx + k;
                futures[i] = ConcurrencyUtils.submit(new Runnable()
                {
                    public void run()
                    {
                        for (long i = firstIdx; i < lastIdx; i++) {
                            long idx1 = 2 * i;
                            long idx2 = idx1 + 1;
                            float im = ak.getFloat(idx1) * bk2l.getFloat(idx2) + ak.getFloat(idx2) * bk2l.getFloat(idx1);
                            ak.setFloat(idx1, ak.getFloat(idx1) * bk2l.getFloat(idx1) - ak.getFloat(idx2) * bk2l.getFloat(idx2));
                            ak.setFloat(idx2, im);
                        }
                    }
                });
            }
            try {
                ConcurrencyUtils.waitForCompletion(futures);
            } catch (InterruptedException | ExecutionException ex) {
                Logger.getLogger(FloatFFT_1D.class.getName()).log(Level.SEVERE, null, ex);
            }
    
        } else {

            for (long i = 0; i < nl; i++) {
                long idx1 = 2 * i;
                long idx2 = idx1 + 1;
                long idx3 = offa + i;
                ak.setFloat(idx1, a.getFloat(idx3) * bk1l.getFloat(idx1));
                ak.setFloat(idx2, -a.getFloat(idx3) * bk1l.getFloat(idx2));
            }

            FFTUtils.cftbsub(2 * nBluesteinl, ak, 0, ipl, nwl, wl);

            for (long i = 0; i < nBluesteinl; i++) {
                long idx1 = 2 * i;
                long idx2 = idx1 + 1;
                float im = ak.getFloat(idx1) * bk2l.getFloat(idx2) + ak.getFloat(idx2) * bk2l.getFloat(idx1);
                ak.setFloat(idx1, ak.getFloat(idx1) * bk2l.getFloat(idx1) - ak.getFloat(idx2) * bk2l.getFloat(idx2));
                ak.setFloat(idx2, im);
            }
        }

        FFTUtils.cftfsub(2 * nBluesteinl, ak, 0, ipl, nwl, wl);

        if (nl % 2 == 0) {
            a.setFloat(offa, bk1l.getFloat(0) * ak.getFloat(0) + bk1l.getFloat(1) * ak.getFloat(1));
            a.setFloat(offa + 1, bk1l.getFloat(nl) * ak.getFloat(nl) + bk1l.getFloat(nl + 1) * ak.getFloat(nl + 1));
            for (long i = 1; i < nl / 2; i++) {
                long idx1 = 2 * i;
                long idx2 = idx1 + 1;
                a.setFloat(offa + idx1, bk1l.getFloat(idx1) * ak.getFloat(idx1) + bk1l.getFloat(idx2) * ak.getFloat(idx2));
                a.setFloat(offa + idx2, -bk1l.getFloat(idx2) * ak.getFloat(idx1) + bk1l.getFloat(idx1) * ak.getFloat(idx2));
            }
        } else {
            a.setFloat(offa, bk1l.getFloat(0) * ak.getFloat(0) + bk1l.getFloat(1) * ak.getFloat(1));
            a.setFloat(offa + 1, -bk1l.getFloat(nl) * ak.getFloat(nl - 1) + bk1l.getFloat(nl - 1) * ak.getFloat(nl));
            for (long i = 1; i < (nl - 1) / 2; i++) {
                long idx1 = 2 * i;
                long idx2 = idx1 + 1;
                a.setFloat(offa + idx1, bk1l.getFloat(idx1) * ak.getFloat(idx1) + bk1l.getFloat(idx2) * ak.getFloat(idx2));
                a.setFloat(offa + idx2, -bk1l.getFloat(idx2) * ak.getFloat(idx1) + bk1l.getFloat(idx1) * ak.getFloat(idx2));
            }
            a.setFloat(offa + nl - 1, bk1l.getFloat(nl - 1) * ak.getFloat(nl - 1) + bk1l.getFloat(nl) * ak.getFloat(nl));
        }

    }
    
    /*---------------------------------------------------------
     rfftf1: further processing of Real forward FFT
     --------------------------------------------------------*/
    void rfftf(final float[] a, final int offa)
    {
        if (n == 1) {
            return;
        }
        int l1, l2, na, kh, nf, ipll, iw, ido, idl1;

        final float[] ch = new float[n];
        final int twon = 2 * n;
        nf = (int) wtable_r[1 + twon];
        na = 1;
        l2 = n;
        iw = twon - 1;
        for (int k1 = 1; k1 <= nf; ++k1) {
            kh = nf - k1;
            ipll = (int) wtable_r[kh + 2 + twon];
            l1 = l2 / ipll;
            ido = n / l2;
            idl1 = ido * l1;
            iw -= (ipll - 1) * ido;
            na = 1 - na;
            switch (ipll) {
                case 2:
                    if (na == 0) {
                        radf2(ido, l1, a, offa, ch, 0, iw);
                    } else {
                        radf2(ido, l1, ch, 0, a, offa, iw);
                    }
                    break;
                case 3:
                    if (na == 0) {
                        radf3(ido, l1, a, offa, ch, 0, iw);
                    } else {
                        radf3(ido, l1, ch, 0, a, offa, iw);
                    }
                    break;
                case 4:
                    if (na == 0) {
                        radf4(ido, l1, a, offa, ch, 0, iw);
                    } else {
                        radf4(ido, l1, ch, 0, a, offa, iw);
                    }
                    break;
                case 5:
                    if (na == 0) {
                        radf5(ido, l1, a, offa, ch, 0, iw);
                    } else {
                        radf5(ido, l1, ch, 0, a, offa, iw);
                    }
                    break;
                default:
                    if (ido == 1) {
                        na = 1 - na;
                    }
                    if (na == 0) {
                        radfg(ido, ipll, l1, idl1, a, offa, ch, 0, iw);
                        na = 1;
                    } else {
                        radfg(ido, ipll, l1, idl1, ch, 0, a, offa, iw);
                        na = 0;
                    }
                    break;
            }
            l2 = l1;
        }
        if (na == 1) {
            return;
        }
        System.arraycopy(ch, 0, a, offa, n);
    }

    /*---------------------------------------------------------
     rfftf1: further processing of Real forward FFT
     --------------------------------------------------------*/
    void rfftf(final FloatLargeArray a, final long offa)
    {
        if (nl == 1) {
            return;
        }
        long l1, l2, na, kh, nf, iw, ido, idl1;
        int ipll;

        final FloatLargeArray ch = new FloatLargeArray(nl);
        final long twon = 2 * nl;
        nf = (long) wtable_rl.getFloat(1 + twon);
        na = 1;
        l2 = nl;
        iw = twon - 1;
        for (long k1 = 1; k1 <= nf; ++k1) {
            kh = nf - k1;
            ipll = (int) wtable_rl.getFloat(kh + 2 + twon);
            l1 = l2 / ipll;
            ido = nl / l2;
            idl1 = ido * l1;
            iw -= (ipll - 1) * ido;
            na = 1 - na;
            switch (ipll) {
                case 2:
                    if (na == 0) {
                        radf2(ido, l1, a, offa, ch, 0, iw);
                    } else {
                        radf2(ido, l1, ch, 0, a, offa, iw);
                    }
                    break;
                case 3:
                    if (na == 0) {
                        radf3(ido, l1, a, offa, ch, 0, iw);
                    } else {
                        radf3(ido, l1, ch, 0, a, offa, iw);
                    }
                    break;
                case 4:
                    if (na == 0) {
                        radf4(ido, l1, a, offa, ch, 0, iw);
                    } else {
                        radf4(ido, l1, ch, 0, a, offa, iw);
                    }
                    break;
                case 5:
                    if (na == 0) {
                        radf5(ido, l1, a, offa, ch, 0, iw);
                    } else {
                        radf5(ido, l1, ch, 0, a, offa, iw);
                    }
                    break;
                default:
                    if (ido == 1) {
                        na = 1 - na;
                    }
                    if (na == 0) {
                        radfg(ido, ipll, l1, idl1, a, offa, ch, 0, iw);
                        na = 1;
                    } else {
                        radfg(ido, ipll, l1, idl1, ch, 0, a, offa, iw);
                        na = 0;
                    }
                    break;
            }
            l2 = l1;
        }
        if (na == 1) {
            return;
        }
        LargeArrayUtils.arraycopy(ch, 0, a, offa, nl);
    }
    
    /*-------------------------------------------------
     radf2: Real FFT's forward processing of factor 2
     -------------------------------------------------*/
    void radf2(final int ido, final int l1, final float[] in, final int in_off, final float[] out, final int out_off, final int offset)
    {
        int i, ic, idx0, idx1, idx2, idx3, idx4;
        float t1i, t1r, w1r, w1i;
        int iw1;
        iw1 = offset;
        idx0 = l1 * ido;
        idx1 = 2 * ido;
        for (int k = 0; k < l1; k++) {
            int oidx1 = out_off + k * idx1;
            int oidx2 = oidx1 + idx1 - 1;
            int iidx1 = in_off + k * ido;
            int iidx2 = iidx1 + idx0;

            float i1r = in[iidx1];
            float i2r = in[iidx2];

            out[oidx1] = i1r + i2r;
            out[oidx2] = i1r - i2r;
        }
        if (ido < 2) {
            return;
        }
        if (ido != 2) {
            for (int k = 0; k < l1; k++) {
                idx1 = k * ido;
                idx2 = 2 * idx1;
                idx3 = idx2 + ido;
                idx4 = idx1 + idx0;
                for (i = 2; i < ido; i += 2) {
                    ic = ido - i;
                    int widx1 = i - 1 + iw1;
                    int oidx1 = out_off + i + idx2;
                    int oidx2 = out_off + ic + idx3;
                    int iidx1 = in_off + i + idx1;
                    int iidx2 = in_off + i + idx4;

                    float a1i = in[iidx1 - 1];
                    float a1r = in[iidx1];
                    float a2i = in[iidx2 - 1];
                    float a2r = in[iidx2];

                    w1r = wtable_r[widx1 - 1];
                    w1i = wtable_r[widx1];

                    t1r = w1r * a2i + w1i * a2r;
                    t1i = w1r * a2r - w1i * a2i;

                    out[oidx1] = a1r + t1i;
                    out[oidx1 - 1] = a1i + t1r;

                    out[oidx2] = t1i - a1r;
                    out[oidx2 - 1] = a1i - t1r;
                }
            }
            if (ido % 2 == 1) {
                return;
            }
        }
        idx2 = 2 * idx1;
        for (int k = 0; k < l1; k++) {
            idx1 = k * ido;
            int oidx1 = out_off + idx2 + ido;
            int iidx1 = in_off + ido - 1 + idx1;

            out[oidx1] = -in[iidx1 + idx0];
            out[oidx1 - 1] = in[iidx1];
        }
    }

    /*-------------------------------------------------
     radf2: Real FFT's forward processing of factor 2
     -------------------------------------------------*/
    void radf2(final long ido, final long l1, final FloatLargeArray in, final long in_off, final FloatLargeArray out, final long out_off, final long offset)
    {
        long i, ic, idx0, idx1, idx2, idx3, idx4;
        float t1i, t1r, w1r, w1i;
        long iw1;
        iw1 = offset;
        idx0 = l1 * ido;
        idx1 = 2 * ido;
        for (long k = 0; k < l1; k++) {
            long oidx1 = out_off + k * idx1;
            long oidx2 = oidx1 + idx1 - 1;
            long iidx1 = in_off + k * ido;
            long iidx2 = iidx1 + idx0;

            float i1r = in.getFloat(iidx1);
            float i2r = in.getFloat(iidx2);

            out.setFloat(oidx1, i1r + i2r);
            out.setFloat(oidx2, i1r - i2r);
        }
        if (ido < 2) {
            return;
        }
        if (ido != 2) {
            for (long k = 0; k < l1; k++) {
                idx1 = k * ido;
                idx2 = 2 * idx1;
                idx3 = idx2 + ido;
                idx4 = idx1 + idx0;
                for (i = 2; i < ido; i += 2) {
                    ic = ido - i;
                    long widx1 = i - 1 + iw1;
                    long oidx1 = out_off + i + idx2;
                    long oidx2 = out_off + ic + idx3;
                    long iidx1 = in_off + i + idx1;
                    long iidx2 = in_off + i + idx4;

                    float a1i = in.getFloat(iidx1 - 1);
                    float a1r = in.getFloat(iidx1);
                    float a2i = in.getFloat(iidx2 - 1);
                    float a2r = in.getFloat(iidx2);

                    w1r = wtable_rl.getFloat(widx1 - 1);
                    w1i = wtable_rl.getFloat(widx1);

                    t1r = w1r * a2i + w1i * a2r;
                    t1i = w1r * a2r - w1i * a2i;

                    out.setFloat(oidx1, a1r + t1i);
                    out.setFloat(oidx1 - 1, a1i + t1r);

                    out.setFloat(oidx2, t1i - a1r);
                    out.setFloat(oidx2 - 1, a1i - t1r);
                }
            }
            if (ido % 2 == 1) {
                return;
            }
        }
        idx2 = 2 * idx1;
        for (long k = 0; k < l1; k++) {
            idx1 = k * ido;
            long oidx1 = out_off + idx2 + ido;
            long iidx1 = in_off + ido - 1 + idx1;

            out.setFloat(oidx1, -in.getFloat(iidx1 + idx0));
            out.setFloat(oidx1 - 1, in.getFloat(iidx1));
        }
    }
    
    /*-------------------------------------------------
     radf3: Real FFT's forward processing of factor 3 
     -------------------------------------------------*/
    void radf3(final int ido, final int l1, final float[] in, final int in_off, final float[] out, final int out_off, final int offset)
    {
        final float taur = -0.5f;
        final float taui = 0.866025403784438707610604524234076962f;
        int i, ic;
        float ci2, di2, di3, cr2, dr2, dr3, ti2, ti3, tr2, tr3, w1r, w2r, w1i, w2i;
        int iw1, iw2;
        iw1 = offset;
        iw2 = iw1 + ido;

        int idx0 = l1 * ido;
        for (int k = 0; k < l1; k++) {
            int idx1 = k * ido;
            int idx3 = 2 * idx0;
            int idx4 = (3 * k + 1) * ido;
            int iidx1 = in_off + idx1;
            int iidx2 = iidx1 + idx0;
            int iidx3 = iidx1 + idx3;
            float i1r = in[iidx1];
            float i2r = in[iidx2];
            float i3r = in[iidx3];
            cr2 = i2r + i3r;
            out[out_off + 3 * idx1] = i1r + cr2;
            out[out_off + idx4 + ido] = taui * (i3r - i2r);
            out[out_off + ido - 1 + idx4] = i1r + taur * cr2;
        }
        if (ido == 1) {
            return;
        }
        for (int k = 0; k < l1; k++) {
            int idx3 = k * ido;
            int idx4 = 3 * idx3;
            int idx5 = idx3 + idx0;
            int idx6 = idx5 + idx0;
            int idx7 = idx4 + ido;
            int idx8 = idx7 + ido;
            for (i = 2; i < ido; i += 2) {
                ic = ido - i;
                int widx1 = i - 1 + iw1;
                int widx2 = i - 1 + iw2;

                w1r = wtable_r[widx1 - 1];
                w1i = wtable_r[widx1];
                w2r = wtable_r[widx2 - 1];
                w2i = wtable_r[widx2];

                int idx9 = in_off + i;
                int idx10 = out_off + i;
                int idx11 = out_off + ic;
                int iidx1 = idx9 + idx3;
                int iidx2 = idx9 + idx5;
                int iidx3 = idx9 + idx6;

                float i1i = in[iidx1 - 1];
                float i1r = in[iidx1];
                float i2i = in[iidx2 - 1];
                float i2r = in[iidx2];
                float i3i = in[iidx3 - 1];
                float i3r = in[iidx3];

                dr2 = w1r * i2i + w1i * i2r;
                di2 = w1r * i2r - w1i * i2i;
                dr3 = w2r * i3i + w2i * i3r;
                di3 = w2r * i3r - w2i * i3i;
                cr2 = dr2 + dr3;
                ci2 = di2 + di3;
                tr2 = i1i + taur * cr2;
                ti2 = i1r + taur * ci2;
                tr3 = taui * (di2 - di3);
                ti3 = taui * (dr3 - dr2);

                int oidx1 = idx10 + idx4;
                int oidx2 = idx11 + idx7;
                int oidx3 = idx10 + idx8;

                out[oidx1 - 1] = i1i + cr2;
                out[oidx1] = i1r + ci2;
                out[oidx2 - 1] = tr2 - tr3;
                out[oidx2] = ti3 - ti2;
                out[oidx3 - 1] = tr2 + tr3;
                out[oidx3] = ti2 + ti3;
            }
        }
    }

    /*-------------------------------------------------
     radf3: Real FFT's forward processing of factor 3 
     -------------------------------------------------*/
    void radf3(final long ido, final long l1, final FloatLargeArray in, final long in_off, final FloatLargeArray out, final long out_off, final long offset)
    {
        final float taur = -0.5f;
        final float taui = 0.866025403784438707610604524234076962f;
        long i, ic;
        float ci2, di2, di3, cr2, dr2, dr3, ti2, ti3, tr2, tr3, w1r, w2r, w1i, w2i;
        long iw1, iw2;
        iw1 = offset;
        iw2 = iw1 + ido;

        long idx0 = l1 * ido;
        for (long k = 0; k < l1; k++) {
            long idx1 = k * ido;
            long idx3 = 2 * idx0;
            long idx4 = (3 * k + 1) * ido;
            long iidx1 = in_off + idx1;
            long iidx2 = iidx1 + idx0;
            long iidx3 = iidx1 + idx3;
            float i1r = in.getFloat(iidx1);
            float i2r = in.getFloat(iidx2);
            float i3r = in.getFloat(iidx3);
            cr2 = i2r + i3r;
            out.setFloat(out_off + 3 * idx1, i1r + cr2);
            out.setFloat(out_off + idx4 + ido, taui * (i3r - i2r));
            out.setFloat(out_off + ido - 1 + idx4, i1r + taur * cr2);
        }
        if (ido == 1) {
            return;
        }
        for (long k = 0; k < l1; k++) {
            long idx3 = k * ido;
            long idx4 = 3 * idx3;
            long idx5 = idx3 + idx0;
            long idx6 = idx5 + idx0;
            long idx7 = idx4 + ido;
            long idx8 = idx7 + ido;
            for (i = 2; i < ido; i += 2) {
                ic = ido - i;
                long widx1 = i - 1 + iw1;
                long widx2 = i - 1 + iw2;

                w1r = wtable_rl.getFloat(widx1 - 1);
                w1i = wtable_rl.getFloat(widx1);
                w2r = wtable_rl.getFloat(widx2 - 1);
                w2i = wtable_rl.getFloat(widx2);

                long idx9 = in_off + i;
                long idx10 = out_off + i;
                long idx11 = out_off + ic;
                long iidx1 = idx9 + idx3;
                long iidx2 = idx9 + idx5;
                long iidx3 = idx9 + idx6;

                float i1i = in.getFloat(iidx1 - 1);
                float i1r = in.getFloat(iidx1);
                float i2i = in.getFloat(iidx2 - 1);
                float i2r = in.getFloat(iidx2);
                float i3i = in.getFloat(iidx3 - 1);
                float i3r = in.getFloat(iidx3);

                dr2 = w1r * i2i + w1i * i2r;
                di2 = w1r * i2r - w1i * i2i;
                dr3 = w2r * i3i + w2i * i3r;
                di3 = w2r * i3r - w2i * i3i;
                cr2 = dr2 + dr3;
                ci2 = di2 + di3;
                tr2 = i1i + taur * cr2;
                ti2 = i1r + taur * ci2;
                tr3 = taui * (di2 - di3);
                ti3 = taui * (dr3 - dr2);

                long oidx1 = idx10 + idx4;
                long oidx2 = idx11 + idx7;
                long oidx3 = idx10 + idx8;

                out.setFloat(oidx1 - 1, i1i + cr2);
                out.setFloat(oidx1, i1r + ci2);
                out.setFloat(oidx2 - 1, tr2 - tr3);
                out.setFloat(oidx2, ti3 - ti2);
                out.setFloat(oidx3 - 1, tr2 + tr3);
                out.setFloat(oidx3, ti2 + ti3);
            }
        }
    }
    
    /*-------------------------------------------------
     radf4: Real FFT's forward processing of factor 4
     -------------------------------------------------*/
    void radf4(final int ido, final int l1, final float[] in, final int in_off, final float[] out, final int out_off, final int offset)
    {
        final float hsqt2 = 0.707106781186547572737310929369414225f;
        int i, ic;
        float ci2, ci3, ci4, cr2, cr3, cr4, ti1, ti2, ti3, ti4, tr1, tr2, tr3, tr4, w1r, w1i, w2r, w2i, w3r, w3i;
        int iw1, iw2, iw3;
        iw1 = offset;
        iw2 = offset + ido;
        iw3 = iw2 + ido;
        int idx0 = l1 * ido;
        for (int k = 0; k < l1; k++) {
            int idx1 = k * ido;
            int idx2 = 4 * idx1;
            int idx3 = idx1 + idx0;
            int idx4 = idx3 + idx0;
            int idx5 = idx4 + idx0;
            int idx6 = idx2 + ido;
            float i1r = in[in_off + idx1];
            float i2r = in[in_off + idx3];
            float i3r = in[in_off + idx4];
            float i4r = in[in_off + idx5];

            tr1 = i2r + i4r;
            tr2 = i1r + i3r;

            int oidx1 = out_off + idx2;
            int oidx2 = out_off + idx6 + ido;

            out[oidx1] = tr1 + tr2;
            out[oidx2 - 1 + ido + ido] = tr2 - tr1;
            out[oidx2 - 1] = i1r - i3r;
            out[oidx2] = i4r - i2r;
        }
        if (ido < 2) {
            return;
        }
        if (ido != 2) {
            for (int k = 0; k < l1; k++) {
                int idx1 = k * ido;
                int idx2 = idx1 + idx0;
                int idx3 = idx2 + idx0;
                int idx4 = idx3 + idx0;
                int idx5 = 4 * idx1;
                int idx6 = idx5 + ido;
                int idx7 = idx6 + ido;
                int idx8 = idx7 + ido;
                for (i = 2; i < ido; i += 2) {
                    ic = ido - i;
                    int widx1 = i - 1 + iw1;
                    int widx2 = i - 1 + iw2;
                    int widx3 = i - 1 + iw3;
                    w1r = wtable_r[widx1 - 1];
                    w1i = wtable_r[widx1];
                    w2r = wtable_r[widx2 - 1];
                    w2i = wtable_r[widx2];
                    w3r = wtable_r[widx3 - 1];
                    w3i = wtable_r[widx3];

                    int idx9 = in_off + i;
                    int idx10 = out_off + i;
                    int idx11 = out_off + ic;
                    int iidx1 = idx9 + idx1;
                    int iidx2 = idx9 + idx2;
                    int iidx3 = idx9 + idx3;
                    int iidx4 = idx9 + idx4;

                    float i1i = in[iidx1 - 1];
                    float i1r = in[iidx1];
                    float i2i = in[iidx2 - 1];
                    float i2r = in[iidx2];
                    float i3i = in[iidx3 - 1];
                    float i3r = in[iidx3];
                    float i4i = in[iidx4 - 1];
                    float i4r = in[iidx4];

                    cr2 = w1r * i2i + w1i * i2r;
                    ci2 = w1r * i2r - w1i * i2i;
                    cr3 = w2r * i3i + w2i * i3r;
                    ci3 = w2r * i3r - w2i * i3i;
                    cr4 = w3r * i4i + w3i * i4r;
                    ci4 = w3r * i4r - w3i * i4i;
                    tr1 = cr2 + cr4;
                    tr4 = cr4 - cr2;
                    ti1 = ci2 + ci4;
                    ti4 = ci2 - ci4;
                    ti2 = i1r + ci3;
                    ti3 = i1r - ci3;
                    tr2 = i1i + cr3;
                    tr3 = i1i - cr3;

                    int oidx1 = idx10 + idx5;
                    int oidx2 = idx11 + idx6;
                    int oidx3 = idx10 + idx7;
                    int oidx4 = idx11 + idx8;

                    out[oidx1 - 1] = tr1 + tr2;
                    out[oidx4 - 1] = tr2 - tr1;
                    out[oidx1] = ti1 + ti2;
                    out[oidx4] = ti1 - ti2;
                    out[oidx3 - 1] = ti4 + tr3;
                    out[oidx2 - 1] = tr3 - ti4;
                    out[oidx3] = tr4 + ti3;
                    out[oidx2] = tr4 - ti3;
                }
            }
            if (ido % 2 == 1) {
                return;
            }
        }
        for (int k = 0; k < l1; k++) {
            int idx1 = k * ido;
            int idx2 = 4 * idx1;
            int idx3 = idx1 + idx0;
            int idx4 = idx3 + idx0;
            int idx5 = idx4 + idx0;
            int idx6 = idx2 + ido;
            int idx7 = idx6 + ido;
            int idx8 = idx7 + ido;
            int idx9 = in_off + ido;
            int idx10 = out_off + ido;

            float i1i = in[idx9 - 1 + idx1];
            float i2i = in[idx9 - 1 + idx3];
            float i3i = in[idx9 - 1 + idx4];
            float i4i = in[idx9 - 1 + idx5];

            ti1 = -hsqt2 * (i2i + i4i);
            tr1 = hsqt2 * (i2i - i4i);

            out[idx10 - 1 + idx2] = tr1 + i1i;
            out[idx10 - 1 + idx7] = i1i - tr1;
            out[out_off + idx6] = ti1 - i3i;
            out[out_off + idx8] = ti1 + i3i;
        }
    }

    /*-------------------------------------------------
     radf4: Real FFT's forward processing of factor 4
     -------------------------------------------------*/
    void radf4(final long ido, final long l1, final FloatLargeArray in, final long in_off, final FloatLargeArray out, final long out_off, final long offset)
    {
        final float hsqt2 = 0.707106781186547572737310929369414225f;
        long i, ic;
        float ci2, ci3, ci4, cr2, cr3, cr4, ti1, ti2, ti3, ti4, tr1, tr2, tr3, tr4, w1r, w1i, w2r, w2i, w3r, w3i;
        long iw1, iw2, iw3;
        iw1 = offset;
        iw2 = offset + ido;
        iw3 = iw2 + ido;
        long idx0 = l1 * ido;
        for (long k = 0; k < l1; k++) {
            long idx1 = k * ido;
            long idx2 = 4 * idx1;
            long idx3 = idx1 + idx0;
            long idx4 = idx3 + idx0;
            long idx5 = idx4 + idx0;
            long idx6 = idx2 + ido;
            float i1r = in.getFloat(in_off + idx1);
            float i2r = in.getFloat(in_off + idx3);
            float i3r = in.getFloat(in_off + idx4);
            float i4r = in.getFloat(in_off + idx5);

            tr1 = i2r + i4r;
            tr2 = i1r + i3r;

            long oidx1 = out_off + idx2;
            long oidx2 = out_off + idx6 + ido;

            out.setFloat(oidx1, tr1 + tr2);
            out.setFloat(oidx2 - 1 + ido + ido, tr2 - tr1);
            out.setFloat(oidx2 - 1, i1r - i3r);
            out.setFloat(oidx2, i4r - i2r);
        }
        if (ido < 2) {
            return;
        }
        if (ido != 2) {
            for (long k = 0; k < l1; k++) {
                long idx1 = k * ido;
                long idx2 = idx1 + idx0;
                long idx3 = idx2 + idx0;
                long idx4 = idx3 + idx0;
                long idx5 = 4 * idx1;
                long idx6 = idx5 + ido;
                long idx7 = idx6 + ido;
                long idx8 = idx7 + ido;
                for (i = 2; i < ido; i += 2) {
                    ic = ido - i;
                    long widx1 = i - 1 + iw1;
                    long widx2 = i - 1 + iw2;
                    long widx3 = i - 1 + iw3;
                    w1r = wtable_rl.getFloat(widx1 - 1);
                    w1i = wtable_rl.getFloat(widx1);
                    w2r = wtable_rl.getFloat(widx2 - 1);
                    w2i = wtable_rl.getFloat(widx2);
                    w3r = wtable_rl.getFloat(widx3 - 1);
                    w3i = wtable_rl.getFloat(widx3);

                    long idx9 = in_off + i;
                    long idx10 = out_off + i;
                    long idx11 = out_off + ic;
                    long iidx1 = idx9 + idx1;
                    long iidx2 = idx9 + idx2;
                    long iidx3 = idx9 + idx3;
                    long iidx4 = idx9 + idx4;

                    float i1i = in.getFloat(iidx1 - 1);
                    float i1r = in.getFloat(iidx1);
                    float i2i = in.getFloat(iidx2 - 1);
                    float i2r = in.getFloat(iidx2);
                    float i3i = in.getFloat(iidx3 - 1);
                    float i3r = in.getFloat(iidx3);
                    float i4i = in.getFloat(iidx4 - 1);
                    float i4r = in.getFloat(iidx4);

                    cr2 = w1r * i2i + w1i * i2r;
                    ci2 = w1r * i2r - w1i * i2i;
                    cr3 = w2r * i3i + w2i * i3r;
                    ci3 = w2r * i3r - w2i * i3i;
                    cr4 = w3r * i4i + w3i * i4r;
                    ci4 = w3r * i4r - w3i * i4i;
                    tr1 = cr2 + cr4;
                    tr4 = cr4 - cr2;
                    ti1 = ci2 + ci4;
                    ti4 = ci2 - ci4;
                    ti2 = i1r + ci3;
                    ti3 = i1r - ci3;
                    tr2 = i1i + cr3;
                    tr3 = i1i - cr3;

                    long oidx1 = idx10 + idx5;
                    long oidx2 = idx11 + idx6;
                    long oidx3 = idx10 + idx7;
                    long oidx4 = idx11 + idx8;

                    out.setFloat(oidx1 - 1, tr1 + tr2);
                    out.setFloat(oidx4 - 1, tr2 - tr1);
                    out.setFloat(oidx1, ti1 + ti2);
                    out.setFloat(oidx4, ti1 - ti2);
                    out.setFloat(oidx3 - 1, ti4 + tr3);
                    out.setFloat(oidx2 - 1, tr3 - ti4);
                    out.setFloat(oidx3, tr4 + ti3);
                    out.setFloat(oidx2, tr4 - ti3);
                }
            }
            if (ido % 2 == 1) {
                return;
            }
        }
        for (long k = 0; k < l1; k++) {
            long idx1 = k * ido;
            long idx2 = 4 * idx1;
            long idx3 = idx1 + idx0;
            long idx4 = idx3 + idx0;
            long idx5 = idx4 + idx0;
            long idx6 = idx2 + ido;
            long idx7 = idx6 + ido;
            long idx8 = idx7 + ido;
            long idx9 = in_off + ido;
            long idx10 = out_off + ido;

            float i1i = in.getFloat(idx9 - 1 + idx1);
            float i2i = in.getFloat(idx9 - 1 + idx3);
            float i3i = in.getFloat(idx9 - 1 + idx4);
            float i4i = in.getFloat(idx9 - 1 + idx5);

            ti1 = -hsqt2 * (i2i + i4i);
            tr1 = hsqt2 * (i2i - i4i);

            out.setFloat(idx10 - 1 + idx2, tr1 + i1i);
            out.setFloat(idx10 - 1 + idx7, i1i - tr1);
            out.setFloat(out_off + idx6, ti1 - i3i);
            out.setFloat(out_off + idx8, ti1 + i3i);
        }
    }
    
    /*-------------------------------------------------
     radf5: Real FFT's forward processing of factor 5
     -------------------------------------------------*/
    void radf5(final int ido, final int l1, final float[] in, final int in_off, final float[] out, final int out_off, final int offset)
    {
        final float tr11 = 0.309016994374947451262869435595348477f;
        final float ti11 = 0.951056516295153531181938433292089030f;
        final float tr12 = -0.809016994374947340240566973079694435f;
        final float ti12 = 0.587785252292473248125759255344746634f;
        int i, ic;
        float ci2, di2, ci4, ci5, di3, di4, di5, ci3, cr2, cr3, dr2, dr3, dr4, dr5, cr5, cr4, ti2, ti3, ti5, ti4, tr2, tr3, tr4, tr5, w1r, w1i, w2r, w2i, w3r, w3i, w4r, w4i;
        int iw1, iw2, iw3, iw4;
        iw1 = offset;
        iw2 = iw1 + ido;
        iw3 = iw2 + ido;
        iw4 = iw3 + ido;

        int idx0 = l1 * ido;
        for (int k = 0; k < l1; k++) {
            int idx1 = k * ido;
            int idx2 = 5 * idx1;
            int idx3 = idx2 + ido;
            int idx4 = idx3 + ido;
            int idx5 = idx4 + ido;
            int idx6 = idx5 + ido;
            int idx7 = idx1 + idx0;
            int idx8 = idx7 + idx0;
            int idx9 = idx8 + idx0;
            int idx10 = idx9 + idx0;
            int idx11 = out_off + ido - 1;

            float i1r = in[in_off + idx1];
            float i2r = in[in_off + idx7];
            float i3r = in[in_off + idx8];
            float i4r = in[in_off + idx9];
            float i5r = in[in_off + idx10];

            cr2 = i5r + i2r;
            ci5 = i5r - i2r;
            cr3 = i4r + i3r;
            ci4 = i4r - i3r;

            out[out_off + idx2] = i1r + cr2 + cr3;
            out[idx11 + idx3] = i1r + tr11 * cr2 + tr12 * cr3;
            out[out_off + idx4] = ti11 * ci5 + ti12 * ci4;
            out[idx11 + idx5] = i1r + tr12 * cr2 + tr11 * cr3;
            out[out_off + idx6] = ti12 * ci5 - ti11 * ci4;
        }
        if (ido == 1) {
            return;
        }
        for (int k = 0; k < l1; ++k) {
            int idx1 = k * ido;
            int idx2 = 5 * idx1;
            int idx3 = idx2 + ido;
            int idx4 = idx3 + ido;
            int idx5 = idx4 + ido;
            int idx6 = idx5 + ido;
            int idx7 = idx1 + idx0;
            int idx8 = idx7 + idx0;
            int idx9 = idx8 + idx0;
            int idx10 = idx9 + idx0;
            for (i = 2; i < ido; i += 2) {
                int widx1 = i - 1 + iw1;
                int widx2 = i - 1 + iw2;
                int widx3 = i - 1 + iw3;
                int widx4 = i - 1 + iw4;
                w1r = wtable_r[widx1 - 1];
                w1i = wtable_r[widx1];
                w2r = wtable_r[widx2 - 1];
                w2i = wtable_r[widx2];
                w3r = wtable_r[widx3 - 1];
                w3i = wtable_r[widx3];
                w4r = wtable_r[widx4 - 1];
                w4i = wtable_r[widx4];

                ic = ido - i;
                int idx15 = in_off + i;
                int idx16 = out_off + i;
                int idx17 = out_off + ic;

                int iidx1 = idx15 + idx1;
                int iidx2 = idx15 + idx7;
                int iidx3 = idx15 + idx8;
                int iidx4 = idx15 + idx9;
                int iidx5 = idx15 + idx10;

                float i1i = in[iidx1 - 1];
                float i1r = in[iidx1];
                float i2i = in[iidx2 - 1];
                float i2r = in[iidx2];
                float i3i = in[iidx3 - 1];
                float i3r = in[iidx3];
                float i4i = in[iidx4 - 1];
                float i4r = in[iidx4];
                float i5i = in[iidx5 - 1];
                float i5r = in[iidx5];

                dr2 = w1r * i2i + w1i * i2r;
                di2 = w1r * i2r - w1i * i2i;
                dr3 = w2r * i3i + w2i * i3r;
                di3 = w2r * i3r - w2i * i3i;
                dr4 = w3r * i4i + w3i * i4r;
                di4 = w3r * i4r - w3i * i4i;
                dr5 = w4r * i5i + w4i * i5r;
                di5 = w4r * i5r - w4i * i5i;

                cr2 = dr2 + dr5;
                ci5 = dr5 - dr2;
                cr5 = di2 - di5;
                ci2 = di2 + di5;
                cr3 = dr3 + dr4;
                ci4 = dr4 - dr3;
                cr4 = di3 - di4;
                ci3 = di3 + di4;

                tr2 = i1i + tr11 * cr2 + tr12 * cr3;
                ti2 = i1r + tr11 * ci2 + tr12 * ci3;
                tr3 = i1i + tr12 * cr2 + tr11 * cr3;
                ti3 = i1r + tr12 * ci2 + tr11 * ci3;
                tr5 = ti11 * cr5 + ti12 * cr4;
                ti5 = ti11 * ci5 + ti12 * ci4;
                tr4 = ti12 * cr5 - ti11 * cr4;
                ti4 = ti12 * ci5 - ti11 * ci4;

                int oidx1 = idx16 + idx2;
                int oidx2 = idx17 + idx3;
                int oidx3 = idx16 + idx4;
                int oidx4 = idx17 + idx5;
                int oidx5 = idx16 + idx6;

                out[oidx1 - 1] = i1i + cr2 + cr3;
                out[oidx1] = i1r + ci2 + ci3;
                out[oidx3 - 1] = tr2 + tr5;
                out[oidx2 - 1] = tr2 - tr5;
                out[oidx3] = ti2 + ti5;
                out[oidx2] = ti5 - ti2;
                out[oidx5 - 1] = tr3 + tr4;
                out[oidx4 - 1] = tr3 - tr4;
                out[oidx5] = ti3 + ti4;
                out[oidx4] = ti4 - ti3;
            }
        }
    }

    /*-------------------------------------------------
     radf5: Real FFT's forward processing of factor 5
     -------------------------------------------------*/
    void radf5(final long ido, final long l1, final FloatLargeArray in, final long in_off, final FloatLargeArray out, final long out_off, final long offset)
    {
        final float tr11 = 0.309016994374947451262869435595348477f;
        final float ti11 = 0.951056516295153531181938433292089030f;
        final float tr12 = -0.809016994374947340240566973079694435f;
        final float ti12 = 0.587785252292473248125759255344746634f;
        long i, ic;
        float ci2, di2, ci4, ci5, di3, di4, di5, ci3, cr2, cr3, dr2, dr3, dr4, dr5, cr5, cr4, ti2, ti3, ti5, ti4, tr2, tr3, tr4, tr5, w1r, w1i, w2r, w2i, w3r, w3i, w4r, w4i;
        long iw1, iw2, iw3, iw4;
        iw1 = offset;
        iw2 = iw1 + ido;
        iw3 = iw2 + ido;
        iw4 = iw3 + ido;

        long idx0 = l1 * ido;
        for (long k = 0; k < l1; k++) {
            long idx1 = k * ido;
            long idx2 = 5 * idx1;
            long idx3 = idx2 + ido;
            long idx4 = idx3 + ido;
            long idx5 = idx4 + ido;
            long idx6 = idx5 + ido;
            long idx7 = idx1 + idx0;
            long idx8 = idx7 + idx0;
            long idx9 = idx8 + idx0;
            long idx10 = idx9 + idx0;
            long idx11 = out_off + ido - 1;

            float i1r = in.getFloat(in_off + idx1);
            float i2r = in.getFloat(in_off + idx7);
            float i3r = in.getFloat(in_off + idx8);
            float i4r = in.getFloat(in_off + idx9);
            float i5r = in.getFloat(in_off + idx10);

            cr2 = i5r + i2r;
            ci5 = i5r - i2r;
            cr3 = i4r + i3r;
            ci4 = i4r - i3r;

            out.setFloat(out_off + idx2, i1r + cr2 + cr3);
            out.setFloat(idx11 + idx3, i1r + tr11 * cr2 + tr12 * cr3);
            out.setFloat(out_off + idx4, ti11 * ci5 + ti12 * ci4);
            out.setFloat(idx11 + idx5, i1r + tr12 * cr2 + tr11 * cr3);
            out.setFloat(out_off + idx6, ti12 * ci5 - ti11 * ci4);
        }
        if (ido == 1) {
            return;
        }
        for (long k = 0; k < l1; ++k) {
            long idx1 = k * ido;
            long idx2 = 5 * idx1;
            long idx3 = idx2 + ido;
            long idx4 = idx3 + ido;
            long idx5 = idx4 + ido;
            long idx6 = idx5 + ido;
            long idx7 = idx1 + idx0;
            long idx8 = idx7 + idx0;
            long idx9 = idx8 + idx0;
            long idx10 = idx9 + idx0;
            for (i = 2; i < ido; i += 2) {
                long widx1 = i - 1 + iw1;
                long widx2 = i - 1 + iw2;
                long widx3 = i - 1 + iw3;
                long widx4 = i - 1 + iw4;
                w1r = wtable_rl.getFloat(widx1 - 1);
                w1i = wtable_rl.getFloat(widx1);
                w2r = wtable_rl.getFloat(widx2 - 1);
                w2i = wtable_rl.getFloat(widx2);
                w3r = wtable_rl.getFloat(widx3 - 1);
                w3i = wtable_rl.getFloat(widx3);
                w4r = wtable_rl.getFloat(widx4 - 1);
                w4i = wtable_rl.getFloat(widx4);

                ic = ido - i;
                long idx15 = in_off + i;
                long idx16 = out_off + i;
                long idx17 = out_off + ic;

                long iidx1 = idx15 + idx1;
                long iidx2 = idx15 + idx7;
                long iidx3 = idx15 + idx8;
                long iidx4 = idx15 + idx9;
                long iidx5 = idx15 + idx10;

                float i1i = in.getFloat(iidx1 - 1);
                float i1r = in.getFloat(iidx1);
                float i2i = in.getFloat(iidx2 - 1);
                float i2r = in.getFloat(iidx2);
                float i3i = in.getFloat(iidx3 - 1);
                float i3r = in.getFloat(iidx3);
                float i4i = in.getFloat(iidx4 - 1);
                float i4r = in.getFloat(iidx4);
                float i5i = in.getFloat(iidx5 - 1);
                float i5r = in.getFloat(iidx5);

                dr2 = w1r * i2i + w1i * i2r;
                di2 = w1r * i2r - w1i * i2i;
                dr3 = w2r * i3i + w2i * i3r;
                di3 = w2r * i3r - w2i * i3i;
                dr4 = w3r * i4i + w3i * i4r;
                di4 = w3r * i4r - w3i * i4i;
                dr5 = w4r * i5i + w4i * i5r;
                di5 = w4r * i5r - w4i * i5i;

                cr2 = dr2 + dr5;
                ci5 = dr5 - dr2;
                cr5 = di2 - di5;
                ci2 = di2 + di5;
                cr3 = dr3 + dr4;
                ci4 = dr4 - dr3;
                cr4 = di3 - di4;
                ci3 = di3 + di4;

                tr2 = i1i + tr11 * cr2 + tr12 * cr3;
                ti2 = i1r + tr11 * ci2 + tr12 * ci3;
                tr3 = i1i + tr12 * cr2 + tr11 * cr3;
                ti3 = i1r + tr12 * ci2 + tr11 * ci3;
                tr5 = ti11 * cr5 + ti12 * cr4;
                ti5 = ti11 * ci5 + ti12 * ci4;
                tr4 = ti12 * cr5 - ti11 * cr4;
                ti4 = ti12 * ci5 - ti11 * ci4;

                long oidx1 = idx16 + idx2;
                long oidx2 = idx17 + idx3;
                long oidx3 = idx16 + idx4;
                long oidx4 = idx17 + idx5;
                long oidx5 = idx16 + idx6;

                out.setFloat(oidx1 - 1, i1i + cr2 + cr3);
                out.setFloat(oidx1, i1r + ci2 + ci3);
                out.setFloat(oidx3 - 1, tr2 + tr5);
                out.setFloat(oidx2 - 1, tr2 - tr5);
                out.setFloat(oidx3, ti2 + ti5);
                out.setFloat(oidx2, ti5 - ti2);
                out.setFloat(oidx5 - 1, tr3 + tr4);
                out.setFloat(oidx4 - 1, tr3 - tr4);
                out.setFloat(oidx5, ti3 + ti4);
                out.setFloat(oidx4, ti4 - ti3);
            }
        }
    }
    
    /*---------------------------------------------------------
     radfg: Real FFT's forward processing of general factor
     --------------------------------------------------------*/
    void radfg(final int ido, final int ip, final int l1, final int idl1, final float[] in, final int in_off, final float[] out, final int out_off, final int offset)
    {
        int idij, ipph, j2, ic, jc, lc, is, nbd;
        float dc2, ai1, ai2, ar1, ar2, ds2, dcp, arg, dsp, ar1h, ar2h, w1r, w1i;
    
        arg = TWO_PI / (float) ip;
        dcp = (float) cos(arg);
        dsp = (float) sin(arg);
        ipph = (ip + 1) / 2;
        nbd = (ido - 1) / 2;
        if (ido != 1) {
            for (int ik = 0; ik < idl1; ik++) {
                out[out_off + ik] = in[in_off + ik];
            }
            for (int j = 1; j < ip; j++) {
                int idx1 = j * l1 * ido;
                for (int k = 0; k < l1; k++) {
                    int idx2 = k * ido + idx1;
                    out[out_off + idx2] = in[in_off + idx2];
                }
            }
            if (nbd <= l1) {
                is = -ido;
                for (int j = 1; j < ip; j++) {
                    is += ido;
                    idij = is - 1;
                    int idx1 = j * l1 * ido;
                    for (int i = 2; i < ido; i += 2) {
                        idij += 2;
                        int idx2 = idij + offset;
                        int idx4 = in_off + i;
                        int idx5 = out_off + i;
                        w1r = wtable_r[idx2 - 1];
                        w1i = wtable_r[idx2];
                        for (int k = 0; k < l1; k++) {
                            int idx3 = k * ido + idx1;
                            int oidx1 = idx5 + idx3;
                            int iidx1 = idx4 + idx3;
                            float i1i = in[iidx1 - 1];
                            float i1r = in[iidx1];

                            out[oidx1 - 1] = w1r * i1i + w1i * i1r;
                            out[oidx1] = w1r * i1r - w1i * i1i;
                        }
                    }
                }
            } else {
                is = -ido;
                for (int j = 1; j < ip; j++) {
                    is += ido;
                    int idx1 = j * l1 * ido;
                    for (int k = 0; k < l1; k++) {
                        idij = is - 1;
                        int idx3 = k * ido + idx1;
                        for (int i = 2; i < ido; i += 2) {
                            idij += 2;
                            int idx2 = idij + offset;
                            w1r = wtable_r[idx2 - 1];
                            w1i = wtable_r[idx2];
                            int oidx1 = out_off + i + idx3;
                            int iidx1 = in_off + i + idx3;
                            float i1i = in[iidx1 - 1];
                            float i1r = in[iidx1];

                            out[oidx1 - 1] = w1r * i1i + w1i * i1r;
                            out[oidx1] = w1r * i1r - w1i * i1i;
                        }
                    }
                }
            }
            if (nbd >= l1) {
                for (int j = 1; j < ipph; j++) {
                    jc = ip - j;
                    int idx1 = j * l1 * ido;
                    int idx2 = jc * l1 * ido;
                    for (int k = 0; k < l1; k++) {
                        int idx3 = k * ido + idx1;
                        int idx4 = k * ido + idx2;
                        for (int i = 2; i < ido; i += 2) {
                            int idx5 = in_off + i;
                            int idx6 = out_off + i;
                            int iidx1 = idx5 + idx3;
                            int iidx2 = idx5 + idx4;
                            int oidx1 = idx6 + idx3;
                            int oidx2 = idx6 + idx4;
                            float o1i = out[oidx1 - 1];
                            float o1r = out[oidx1];
                            float o2i = out[oidx2 - 1];
                            float o2r = out[oidx2];

                            in[iidx1 - 1] = o1i + o2i;
                            in[iidx1] = o1r + o2r;

                            in[iidx2 - 1] = o1r - o2r;
                            in[iidx2] = o2i - o1i;
                        }
                    }
                }
            } else {
                for (int j = 1; j < ipph; j++) {
                    jc = ip - j;
                    int idx1 = j * l1 * ido;
                    int idx2 = jc * l1 * ido;
                    for (int i = 2; i < ido; i += 2) {
                        int idx5 = in_off + i;
                        int idx6 = out_off + i;
                        for (int k = 0; k < l1; k++) {
                            int idx3 = k * ido + idx1;
                            int idx4 = k * ido + idx2;
                            int iidx1 = idx5 + idx3;
                            int iidx2 = idx5 + idx4;
                            int oidx1 = idx6 + idx3;
                            int oidx2 = idx6 + idx4;
                            float o1i = out[oidx1 - 1];
                            float o1r = out[oidx1];
                            float o2i = out[oidx2 - 1];
                            float o2r = out[oidx2];

                            in[iidx1 - 1] = o1i + o2i;
                            in[iidx1] = o1r + o2r;
                            in[iidx2 - 1] = o1r - o2r;
                            in[iidx2] = o2i - o1i;
                        }
                    }
                }
            }
        } else {
            System.arraycopy(out, out_off, in, in_off, idl1);
        }
        for (int j = 1; j < ipph; j++) {
            jc = ip - j;
            int idx1 = j * l1 * ido;
            int idx2 = jc * l1 * ido;
            for (int k = 0; k < l1; k++) {
                int idx3 = k * ido + idx1;
                int idx4 = k * ido + idx2;
                int oidx1 = out_off + idx3;
                int oidx2 = out_off + idx4;
                float o1r = out[oidx1];
                float o2r = out[oidx2];

                in[in_off + idx3] = o1r + o2r;
                in[in_off + idx4] = o2r - o1r;
            }
        }

        ar1 = 1;
        ai1 = 0;
        int idx0 = (ip - 1) * idl1;
        for (int l = 1; l < ipph; l++) {
            lc = ip - l;
            ar1h = dcp * ar1 - dsp * ai1;
            ai1 = dcp * ai1 + dsp * ar1;
            ar1 = ar1h;
            int idx1 = l * idl1;
            int idx2 = lc * idl1;
            for (int ik = 0; ik < idl1; ik++) {
                int idx3 = out_off + ik;
                int idx4 = in_off + ik;
                out[idx3 + idx1] = in[idx4] + ar1 * in[idx4 + idl1];
                out[idx3 + idx2] = ai1 * in[idx4 + idx0];
            }
            dc2 = ar1;
            ds2 = ai1;
            ar2 = ar1;
            ai2 = ai1;
            for (int j = 2; j < ipph; j++) {
                jc = ip - j;
                ar2h = dc2 * ar2 - ds2 * ai2;
                ai2 = dc2 * ai2 + ds2 * ar2;
                ar2 = ar2h;
                int idx3 = j * idl1;
                int idx4 = jc * idl1;
                for (int ik = 0; ik < idl1; ik++) {
                    int idx5 = out_off + ik;
                    int idx6 = in_off + ik;
                    out[idx5 + idx1] += ar2 * in[idx6 + idx3];
                    out[idx5 + idx2] += ai2 * in[idx6 + idx4];
                }
            }
        }
        for (int j = 1; j < ipph; j++) {
            int idx1 = j * idl1;
            for (int ik = 0; ik < idl1; ik++) {
                out[out_off + ik] += in[in_off + ik + idx1];
            }
        }

        if (ido >= l1) {
            for (int k = 0; k < l1; k++) {
                int idx1 = k * ido;
                int idx2 = idx1 * ip;
                for (int i = 0; i < ido; i++) {
                    in[in_off + i + idx2] = out[out_off + i + idx1];
                }
            }
        } else {
            for (int i = 0; i < ido; i++) {
                for (int k = 0; k < l1; k++) {
                    int idx1 = k * ido;
                    in[in_off + i + idx1 * ip] = out[out_off + i + idx1];
                }
            }
        }
        int idx01 = ip * ido;
        for (int j = 1; j < ipph; j++) {
            jc = ip - j;
            j2 = 2 * j;
            int idx1 = j * l1 * ido;
            int idx2 = jc * l1 * ido;
            int idx3 = j2 * ido;
            for (int k = 0; k < l1; k++) {
                int idx4 = k * ido;
                int idx5 = idx4 + idx1;
                int idx6 = idx4 + idx2;
                int idx7 = k * idx01;
                in[in_off + ido - 1 + idx3 - ido + idx7] = out[out_off + idx5];
                in[in_off + idx3 + idx7] = out[out_off + idx6];
            }
        }
        if (ido == 1) {
            return;
        }
        if (nbd >= l1) {
            for (int j = 1; j < ipph; j++) {
                jc = ip - j;
                j2 = 2 * j;
                int idx1 = j * l1 * ido;
                int idx2 = jc * l1 * ido;
                int idx3 = j2 * ido;
                for (int k = 0; k < l1; k++) {
                    int idx4 = k * idx01;
                    int idx5 = k * ido;
                    for (int i = 2; i < ido; i += 2) {
                        ic = ido - i;
                        int idx6 = in_off + i;
                        int idx7 = in_off + ic;
                        int idx8 = out_off + i;
                        int iidx1 = idx6 + idx3 + idx4;
                        int iidx2 = idx7 + idx3 - ido + idx4;
                        int oidx1 = idx8 + idx5 + idx1;
                        int oidx2 = idx8 + idx5 + idx2;
                        float o1i = out[oidx1 - 1];
                        float o1r = out[oidx1];
                        float o2i = out[oidx2 - 1];
                        float o2r = out[oidx2];

                        in[iidx1 - 1] = o1i + o2i;
                        in[iidx2 - 1] = o1i - o2i;
                        in[iidx1] = o1r + o2r;
                        in[iidx2] = o2r - o1r;
                    }
                }
            }
        } else {
            for (int j = 1; j < ipph; j++) {
                jc = ip - j;
                j2 = 2 * j;
                int idx1 = j * l1 * ido;
                int idx2 = jc * l1 * ido;
                int idx3 = j2 * ido;
                for (int i = 2; i < ido; i += 2) {
                    ic = ido - i;
                    int idx6 = in_off + i;
                    int idx7 = in_off + ic;
                    int idx8 = out_off + i;
                    for (int k = 0; k < l1; k++) {
                        int idx4 = k * idx01;
                        int idx5 = k * ido;
                        int iidx1 = idx6 + idx3 + idx4;
                        int iidx2 = idx7 + idx3 - ido + idx4;
                        int oidx1 = idx8 + idx5 + idx1;
                        int oidx2 = idx8 + idx5 + idx2;
                        float o1i = out[oidx1 - 1];
                        float o1r = out[oidx1];
                        float o2i = out[oidx2 - 1];
                        float o2r = out[oidx2];

                        in[iidx1 - 1] = o1i + o2i;
                        in[iidx2 - 1] = o1i - o2i;
                        in[iidx1] = o1r + o2r;
                        in[iidx2] = o2r - o1r;
                    }
                }
            }
        }
    }

    /*---------------------------------------------------------
     radfg: Real FFT's forward processing of general factor
     --------------------------------------------------------*/
    void radfg(final long ido, final long ip, final long l1, final long idl1, final FloatLargeArray in, final long in_off, final FloatLargeArray out, final long out_off, final long offset)
    {
        long idij, ipph, j2, ic, jc, lc, is, nbd;
        float dc2, ai1, ai2, ar1, ar2, ds2, dcp, arg, dsp, ar1h, ar2h, w1r, w1i;
    
        arg = TWO_PI / (float) ip;
        dcp = (float) cos(arg);
        dsp = (float) sin(arg);
        ipph = (ip + 1) / 2;
        nbd = (ido - 1) / 2;
        if (ido != 1) {
            for (long ik = 0; ik < idl1; ik++) {
                out.setFloat(out_off + ik, in.getFloat(in_off + ik));
            }
            for (long j = 1; j < ip; j++) {
                long idx1 = j * l1 * ido;
                for (long k = 0; k < l1; k++) {
                    long idx2 = k * ido + idx1;
                    out.setFloat(out_off + idx2, in.getFloat(in_off + idx2));
                }
            }
            if (nbd <= l1) {
                is = -ido;
                for (long j = 1; j < ip; j++) {
                    is += ido;
                    idij = is - 1;
                    long idx1 = j * l1 * ido;
                    for (long i = 2; i < ido; i += 2) {
                        idij += 2;
                        long idx2 = idij + offset;
                        long idx4 = in_off + i;
                        long idx5 = out_off + i;
                        w1r = wtable_rl.getFloat(idx2 - 1);
                        w1i = wtable_rl.getFloat(idx2);
                        for (long k = 0; k < l1; k++) {
                            long idx3 = k * ido + idx1;
                            long oidx1 = idx5 + idx3;
                            long iidx1 = idx4 + idx3;
                            float i1i = in.getFloat(iidx1 - 1);
                            float i1r = in.getFloat(iidx1);

                            out.setFloat(oidx1 - 1, w1r * i1i + w1i * i1r);
                            out.setFloat(oidx1, w1r * i1r - w1i * i1i);
                        }
                    }
                }
            } else {
                is = -ido;
                for (long j = 1; j < ip; j++) {
                    is += ido;
                    long idx1 = j * l1 * ido;
                    for (long k = 0; k < l1; k++) {
                        idij = is - 1;
                        long idx3 = k * ido + idx1;
                        for (long i = 2; i < ido; i += 2) {
                            idij += 2;
                            long idx2 = idij + offset;
                            w1r = wtable_rl.getFloat(idx2 - 1);
                            w1i = wtable_rl.getFloat(idx2);
                            long oidx1 = out_off + i + idx3;
                            long iidx1 = in_off + i + idx3;
                            float i1i = in.getFloat(iidx1 - 1);
                            float i1r = in.getFloat(iidx1);

                            out.setFloat(oidx1 - 1, w1r * i1i + w1i * i1r);
                            out.setFloat(oidx1, w1r * i1r - w1i * i1i);
                        }
                    }
                }
            }
            if (nbd >= l1) {
                for (long j = 1; j < ipph; j++) {
                    jc = ip - j;
                    long idx1 = j * l1 * ido;
                    long idx2 = jc * l1 * ido;
                    for (long k = 0; k < l1; k++) {
                        long idx3 = k * ido + idx1;
                        long idx4 = k * ido + idx2;
                        for (long i = 2; i < ido; i += 2) {
                            long idx5 = in_off + i;
                            long idx6 = out_off + i;
                            long iidx1 = idx5 + idx3;
                            long iidx2 = idx5 + idx4;
                            long oidx1 = idx6 + idx3;
                            long oidx2 = idx6 + idx4;
                            float o1i = out.getFloat(oidx1 - 1);
                            float o1r = out.getFloat(oidx1);
                            float o2i = out.getFloat(oidx2 - 1);
                            float o2r = out.getFloat(oidx2);

                            in.setFloat(iidx1 - 1, o1i + o2i);
                            in.setFloat(iidx1, o1r + o2r);

                            in.setFloat(iidx2 - 1, o1r - o2r);
                            in.setFloat(iidx2, o2i - o1i);
                        }
                    }
                }
            } else {
                for (long j = 1; j < ipph; j++) {
                    jc = ip - j;
                    long idx1 = j * l1 * ido;
                    long idx2 = jc * l1 * ido;
                    for (long i = 2; i < ido; i += 2) {
                        long idx5 = in_off + i;
                        long idx6 = out_off + i;
                        for (long k = 0; k < l1; k++) {
                            long idx3 = k * ido + idx1;
                            long idx4 = k * ido + idx2;
                            long iidx1 = idx5 + idx3;
                            long iidx2 = idx5 + idx4;
                            long oidx1 = idx6 + idx3;
                            long oidx2 = idx6 + idx4;
                            float o1i = out.getFloat(oidx1 - 1);
                            float o1r = out.getFloat(oidx1);
                            float o2i = out.getFloat(oidx2 - 1);
                            float o2r = out.getFloat(oidx2);

                            in.setFloat(iidx1 - 1, o1i + o2i);
                            in.setFloat(iidx1, o1r + o2r);
                            in.setFloat(iidx2 - 1, o1r - o2r);
                            in.setFloat(iidx2, o2i - o1i);
                        }
                    }
                }
            }
        } else {
            LargeArrayUtils.arraycopy(out, out_off, in, in_off, idl1);
        }
        for (long j = 1; j < ipph; j++) {
            jc = ip - j;
            long idx1 = j * l1 * ido;
            long idx2 = jc * l1 * ido;
            for (long k = 0; k < l1; k++) {
                long idx3 = k * ido + idx1;
                long idx4 = k * ido + idx2;
                long oidx1 = out_off + idx3;
                long oidx2 = out_off + idx4;
                float o1r = out.getFloat(oidx1);
                float o2r = out.getFloat(oidx2);

                in.setFloat(in_off + idx3, o1r + o2r);
                in.setFloat(in_off + idx4, o2r - o1r);
            }
        }

        ar1 = 1;
        ai1 = 0;
        long idx0 = (ip - 1) * idl1;
        for (long l = 1; l < ipph; l++) {
            lc = ip - l;
            ar1h = dcp * ar1 - dsp * ai1;
            ai1 = dcp * ai1 + dsp * ar1;
            ar1 = ar1h;
            long idx1 = l * idl1;
            long idx2 = lc * idl1;
            for (long ik = 0; ik < idl1; ik++) {
                long idx3 = out_off + ik;
                long idx4 = in_off + ik;
                out.setFloat(idx3 + idx1, in.getFloat(idx4) + ar1 * in.getFloat(idx4 + idl1));
                out.setFloat(idx3 + idx2, ai1 * in.getFloat(idx4 + idx0));
            }
            dc2 = ar1;
            ds2 = ai1;
            ar2 = ar1;
            ai2 = ai1;
            for (long j = 2; j < ipph; j++) {
                jc = ip - j;
                ar2h = dc2 * ar2 - ds2 * ai2;
                ai2 = dc2 * ai2 + ds2 * ar2;
                ar2 = ar2h;
                long idx3 = j * idl1;
                long idx4 = jc * idl1;
                for (long ik = 0; ik < idl1; ik++) {
                    long idx5 = out_off + ik;
                    long idx6 = in_off + ik;
                    out.setFloat(idx5 + idx1, out.getFloat(idx5 + idx1) + ar2 * in.getFloat(idx6 + idx3));
                    out.setFloat(idx5 + idx2, out.getFloat(idx5 + idx2) + ai2 * in.getFloat(idx6 + idx4));
                }
            }
        }
        for (long j = 1; j < ipph; j++) {
            long idx1 = j * idl1;
            for (long ik = 0; ik < idl1; ik++) {
                out.setFloat(out_off + ik, out.getFloat(out_off + ik) + in.getFloat(in_off + ik + idx1));
            }
        }

        if (ido >= l1) {
            for (long k = 0; k < l1; k++) {
                long idx1 = k * ido;
                long idx2 = idx1 * ip;
                for (long i = 0; i < ido; i++) {
                    in.setFloat(in_off + i + idx2, out.getFloat(out_off + i + idx1));
                }
            }
        } else {
            for (long i = 0; i < ido; i++) {
                for (long k = 0; k < l1; k++) {
                    long idx1 = k * ido;
                    in.setFloat(in_off + i + idx1 * ip, out.getFloat(out_off + i + idx1));
                }
            }
        }
        long idx01 = ip * ido;
        for (long j = 1; j < ipph; j++) {
            jc = ip - j;
            j2 = 2 * j;
            long idx1 = j * l1 * ido;
            long idx2 = jc * l1 * ido;
            long idx3 = j2 * ido;
            for (long k = 0; k < l1; k++) {
                long idx4 = k * ido;
                long idx5 = idx4 + idx1;
                long idx6 = idx4 + idx2;
                long idx7 = k * idx01;
                in.setFloat(in_off + ido - 1 + idx3 - ido + idx7, out.getFloat(out_off + idx5));
                in.setFloat(in_off + idx3 + idx7, out.getFloat(out_off + idx6));
            }
        }
        if (ido == 1) {
            return;
        }
        if (nbd >= l1) {
            for (long j = 1; j < ipph; j++) {
                jc = ip - j;
                j2 = 2 * j;
                long idx1 = j * l1 * ido;
                long idx2 = jc * l1 * ido;
                long idx3 = j2 * ido;
                for (long k = 0; k < l1; k++) {
                    long idx4 = k * idx01;
                    long idx5 = k * ido;
                    for (long i = 2; i < ido; i += 2) {
                        ic = ido - i;
                        long idx6 = in_off + i;
                        long idx7 = in_off + ic;
                        long idx8 = out_off + i;
                        long iidx1 = idx6 + idx3 + idx4;
                        long iidx2 = idx7 + idx3 - ido + idx4;
                        long oidx1 = idx8 + idx5 + idx1;
                        long oidx2 = idx8 + idx5 + idx2;
                        float o1i = out.getFloat(oidx1 - 1);
                        float o1r = out.getFloat(oidx1);
                        float o2i = out.getFloat(oidx2 - 1);
                        float o2r = out.getFloat(oidx2);

                        in.setFloat(iidx1 - 1, o1i + o2i);
                        in.setFloat(iidx2 - 1, o1i - o2i);
                        in.setFloat(iidx1, o1r + o2r);
                        in.setFloat(iidx2, o2r - o1r);
                    }
                }
            }
        } else {
            for (long j = 1; j < ipph; j++) {
                jc = ip - j;
                j2 = 2 * j;
                long idx1 = j * l1 * ido;
                long idx2 = jc * l1 * ido;
                long idx3 = j2 * ido;
                for (long i = 2; i < ido; i += 2) {
                    ic = ido - i;
                    long idx6 = in_off + i;
                    long idx7 = in_off + ic;
                    long idx8 = out_off + i;
                    for (long k = 0; k < l1; k++) {
                        long idx4 = k * idx01;
                        long idx5 = k * ido;
                        long iidx1 = idx6 + idx3 + idx4;
                        long iidx2 = idx7 + idx3 - ido + idx4;
                        long oidx1 = idx8 + idx5 + idx1;
                        long oidx2 = idx8 + idx5 + idx2;
                        float o1i = out.getFloat(oidx1 - 1);
                        float o1r = out.getFloat(oidx1);
                        float o2i = out.getFloat(oidx2 - 1);
                        float o2r = out.getFloat(oidx2);

                        in.setFloat(iidx1 - 1, o1i + o2i);
                        in.setFloat(iidx2 - 1, o1i - o2i);
                        in.setFloat(iidx1, o1r + o2r);
                        in.setFloat(iidx2, o2r - o1r);
                    }
                }
            }
        }
    }
}
