#include "kiss_fft.h"

// Define PI for precision
#ifndef M_PI
#define M_PI 3.141592653589793238462643383279502884197169399375105820974944
#endif

#define MAXFACTORS 32

struct kiss_fft_state {
    int nfft;
    int inverse;
    int factors[2*MAXFACTORS];
    kiss_fft_cpx *scratch; // Scratch buffer for generic butterflies
    kiss_fft_cpx twiddles[1];
};

// --- Radix-2 Butterfly (Fastest) ---
static void kf_bfly2(kiss_fft_cpx * Fout, const size_t fstride, const kiss_fft_cfg st, int m) {
    kiss_fft_cpx * Fout2;
    kiss_fft_cpx * tw1 = st->twiddles;
    kiss_fft_cpx t;
    Fout2 = Fout + m;
    do {
        // Multiply by twiddle factor
        t.r = Fout2->r * tw1->r - Fout2->i * tw1->i;
        t.i = Fout2->r * tw1->i + Fout2->i * tw1->r;

        // Butterfly operation
        Fout2->r = Fout->r - t.r;
        Fout2->i = Fout->i - t.i;
        Fout->r += t.r;
        Fout->i += t.i;

        tw1 += fstride;
        ++Fout2;
        ++Fout;
    } while (--m);
}

// --- Radix-4 Butterfly (Highly Optimized for Speed) ---
static void kf_bfly4(kiss_fft_cpx * Fout, const size_t fstride, const kiss_fft_cfg st, int m) {
    kiss_fft_cpx *tw1, *tw2, *tw3;
    kiss_fft_cpx scratch[6];
    int k = m;
    const int N = st->nfft;
    kiss_fft_cpx *Fout_beg = Fout;

    tw3 = tw2 = tw1 = st->twiddles;

    do {
        kiss_fft_cpx s0, s1, s2, s3, t0, t1, t2, t3;

        t0 = Fout[0];
        t1 = Fout[m];
        t2 = Fout[2*m];
        t3 = Fout[3*m];

        // Apply twiddle factors
        s0 = t0;

        s1.r = t1.r * tw1->r - t1.i * tw1->i;
        s1.i = t1.r * tw1->i + t1.i * tw1->r;

        s2.r = t2.r * tw2->r - t2.i * tw2->i;
        s2.i = t2.r * tw2->i + t2.i * tw2->r;

        s3.r = t3.r * tw3->r - t3.i * tw3->i;
        s3.i = t3.r * tw3->i + t3.i * tw3->r;

        // Butterfly calcs
        kiss_fft_cpx v0, v1, v2, v3;
        v0.r = s0.r + s2.r;
        v0.i = s0.i + s2.i;
        v1.r = s0.r - s2.r;
        v1.i = s0.i - s2.i;
        v2.r = s1.r + s3.r;
        v2.i = s1.i + s3.i;
        v3.r = s1.r - s3.r;
        v3.i = s1.i - s3.i;

        Fout[0].r = v0.r + v2.r;
        Fout[0].i = v0.i + v2.i;
        Fout[2*m].r = v0.r - v2.r;
        Fout[2*m].i = v0.i - v2.i;

        // Inverse transform handling usually handled by conjugation in data prep or twiddles
        // Standard forward FFT sign convention:
        Fout[m].r = v1.r + v3.i;
        Fout[m].i = v1.i - v3.r;
        Fout[3*m].r = v1.r - v3.i;
        Fout[3*m].i = v1.i + v3.r;

        Fout++;
        tw1 += fstride;
        tw2 += fstride * 2;
        tw3 += fstride * 3;
    } while (--k);
}

// --- Generic Butterfly (Handles 3, 5, 7, etc.) ---
static void kf_bfly_generic(
        kiss_fft_cpx * Fout,
        const size_t fstride,
        const kiss_fft_cfg st,
        int m,
        int p
) {
    int u, k, q1, q;
    kiss_fft_cpx * twiddles = st->twiddles;
    kiss_fft_cpx t;
    int Norig = st->nfft;
    kiss_fft_cpx * scratch = st->scratch;

    for ( u=0; u<m; ++u ) {
        k=u;
        for ( q1=0 ; q1<p ; ++q1 ) {
            scratch[q1] = Fout[ k ];
            k += m;
        }

        k=u;
        for ( q1=0 ; q1<p ; ++q1 ) {
            int twidx = 0;
            Fout[ k ] = scratch[0];
            for ( q=1; q<p; ++q ) {
                twidx += fstride * k;
                if (twidx >= Norig) twidx -= Norig;

                t.r = scratch[q].r * twiddles[twidx].r - scratch[q].i * twiddles[twidx].i;
                t.i = scratch[q].r * twiddles[twidx].i + scratch[q].i * twiddles[twidx].r;

                Fout[ k ].r += t.r;
                Fout[ k ].i += t.i;
            }
            k += m;
        }
    }
}

// --- Main Recursive Worker ---
static void kf_work(kiss_fft_cpx * Fout, const kiss_fft_cpx * f, const size_t fstride,
                    int in_stride, int * factors, const kiss_fft_cfg st) {
    kiss_fft_cpx * Fout_beg = Fout;
    const int p = *factors++; /* the radix  */
    const int m = *factors++; /* stage's fft length/p */
    const kiss_fft_cpx * Fout_end = Fout + p*m;

    if (m == 1) {
        do {
            *Fout = *f;
            f += fstride*in_stride;
        } while (++Fout != Fout_end);
    } else {
        do {
            kf_work(Fout, f, fstride*p, in_stride, factors, st);
            f += fstride*in_stride;
        } while ((Fout += m) != Fout_end);
    }

    Fout = Fout_beg;

    switch (p) {
        case 2: kf_bfly2(Fout, fstride, st, m); break;
        case 4: kf_bfly4(Fout, fstride, st, m); break; // Optimized!
        default: kf_bfly_generic(Fout, fstride, st, m, p); break; // Fallback
    }
}

static void kf_factor(int n, int * facbuf) {
    int p = 4;
    double floor_sqrt = floor(sqrt((double)n));

    do {
        while (n % p) {
            switch (p) {
                case 4: p = 2; break;
                case 2: p = 3; break;
                default: p += 2; break;
            }
            if (p > floor_sqrt)
                p = n;
        }
        n /= p;
        *facbuf++ = p;
        *facbuf++ = n;
    } while (n > 1);
}

kiss_fft_cfg kiss_fft_alloc(int nfft, int inverse_fft, void * mem, size_t * lenmem) {
    kiss_fft_cfg st = NULL;
    // Allocates extra space for "scratch" buffer to prevent stack overflow
    size_t memneeded = sizeof(struct kiss_fft_state) + sizeof(kiss_fft_cpx)*(nfft-1) + sizeof(kiss_fft_cpx)*nfft;

    if (lenmem == NULL) {
        st = (kiss_fft_cfg)KISS_FFT_MALLOC(memneeded);
    } else {
        if (*lenmem >= memneeded)
            st = (kiss_fft_cfg)mem;
        *lenmem = memneeded;
    }

    if (st) {
        st->nfft = nfft;
        st->inverse = inverse_fft;
        st->scratch = (kiss_fft_cpx*)(st->twiddles + nfft); // Setup scratch pointer

        for (int i = 0; i < nfft; ++i) {
            const double pi = M_PI;
            double phase = -2*pi*i / nfft;
            if (st->inverse)
                phase *= -1;
            st->twiddles[i].r = (float)cos(phase);
            st->twiddles[i].i = (float)sin(phase);
        }

        kf_factor(nfft, st->factors);
    }
    return st;
}

void kiss_fft(kiss_fft_cfg st, const kiss_fft_cpx *fin, kiss_fft_cpx *fout) {
    kf_work(fout, fin, 1, 1, st->factors, st);
}

void kiss_fft_cleanup(void) {
    // No-op
}

void kiss_fft_free(kiss_fft_cfg cfg) {
    if (cfg) {
        KISS_FFT_FREE(cfg);
    }
}