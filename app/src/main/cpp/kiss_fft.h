#ifndef KISS_FFT_H
#define KISS_FFT_H

#include <stdlib.h>
#include <stdio.h>
#include <math.h>
#include <string.h>

#ifdef __cplusplus
extern "C" {
#endif

#define KISS_FFT_MALLOC malloc
#define KISS_FFT_FREE free

typedef struct {
    float r;
    float i;
} kiss_fft_cpx;

typedef struct kiss_fft_state* kiss_fft_cfg;

kiss_fft_cfg kiss_fft_alloc(int nfft, int inverse_fft, void * mem, size_t * lenmem);
void kiss_fft(kiss_fft_cfg cfg, const kiss_fft_cpx *fin, kiss_fft_cpx *fout);
void kiss_fft_cleanup(void);
void kiss_fft_free(kiss_fft_cfg cfg);

#ifdef __cplusplus
}
#endif

#endif