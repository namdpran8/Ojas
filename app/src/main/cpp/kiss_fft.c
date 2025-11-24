#include "kiss_fft.h"

#define MAXFACTORS 32

struct kiss_fft_state {
    int nfft;
    int inverse;
    int factors[2*MAXFACTORS];
    kiss_fft_cpx twiddles[1];
};

static void kf_bfly2(kiss_fft_cpx * Fout, const size_t fstride, const kiss_fft_cfg st, int m) {
    kiss_fft_cpx * Fout2;
    kiss_fft_cpx * tw1 = st->twiddles;
    kiss_fft_cpx t;
    Fout2 = Fout + m;
    do {
        t.r = Fout2->r * tw1->r - Fout2->i * tw1->i;
        t.i = Fout2->r * tw1->i + Fout2->i * tw1->r;

        Fout2->r = Fout->r - t.r;
        Fout2->i = Fout->i - t.i;
        Fout->r += t.r;
        Fout->i += t.i;

        tw1 += fstride;
        ++Fout2;
        ++Fout;
    } while (--m);
}

static void kf_work(kiss_fft_cpx * Fout, const kiss_fft_cpx * f, const size_t fstride,
                    int in_stride, int * factors, const kiss_fft_cfg st) {
    kiss_fft_cpx * Fout_beg = Fout;
    const int p = *factors++;
    const int m = *factors++;
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
        default:
            for (int j = 0; j < m; j++) {
                for (int k = 0; k < p; k++) {
                    int idx = j + k*m;
                    Fout[idx] = Fout_beg[j + k*m];
                }
            }
            break;
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
    size_t memneeded = sizeof(struct kiss_fft_state) + sizeof(kiss_fft_cpx)*(nfft-1);

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

        for (int i = 0; i < nfft; ++i) {
            const double pi = 3.141592653589793238462643383279502884197169399375105820974944;
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
    // No-op in this implementation
}

void kiss_fft_free(kiss_fft_cfg cfg) {
    if (cfg) {
        KISS_FFT_FREE(cfg);
    }
}