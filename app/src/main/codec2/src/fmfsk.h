/*---------------------------------------------------------------------------*\

  FILE........: fmfsk.h
  AUTHOR......: Brady O'Brien
  DATE CREATED: 6 February 2016

  C Implementation of 2FSK+Manchester over FM modulator/demodulator, based
  on mancyfsk.m and fmfsk.m

\*---------------------------------------------------------------------------*/

/*
  Copyright (C) 2016 David Rowe

  All rights reserved.

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU Lesser General Public License version 2.1, as
  published by the Free Software Foundation.  This program is
  distributed in the hope that it will be useful, but WITHOUT ANY
  WARRANTY; without even the implied warranty of MERCHANTABILITY or
  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public
  License for more details.

  You should have received a copy of the GNU Lesser General Public License
  along with this program; if not, see <http://www.gnu.org/licenses/>.
*/

#ifndef __C2FMFSK_H
#define __C2FMFSK_H

#include <stdint.h>
#include "comp.h"
#include "modem_stats.h"

#define FMFSK_SCALE 16383

/* 
 * fm-me-2fsk state
 */
struct FMFSK{
    /* Static fmfsk parameters */
    int Rb;             /* Manchester-encoded bitrate */
    int Rs;             /* Raw modem symbol rate */
    int Fs;             /* Sample rate */
    int Ts;             /* Samples-per-symbol */
    int N;              /* Sample processing buffer size */
    int nsym;           /* Number of raw modem symbols processed per demod call */
    int nbit;           /* Number of bits spit out per demod call */
    int nmem;           /* Number of samples kept around between demod calls */
    
    /* State kept by demod */
    int nin;            /* Number of samples to be demod-ed the next cycle */
    int lodd;           /* Last integrated sample for odd bitstream generation */
    float * oldsamps;   /* Memory of old samples to make clock-offset-tolerance possible */
    
    /* Stats generated by demod */
    float norm_rx_timing; /* RX Timing, used to calculate clock offset */
    int ppm;			/* Clock offset in parts-per-million */
    float snr_mean;
    
    /* Modem stat structure */
    struct MODEM_STATS * stats;
};

/*
 * Create a new fmfsk modem instance.
 * 
 * int Fs - sample rate
 * int Rb - non-manchester bitrate
 * returns - new struct FMFSK on sucess, NULL on failure
 */
struct FMFSK * fmfsk_create(int Fs,int Rb);

/*
 * Destroys an fmfsk modem and deallocates memory
 */
void fmfsk_destroy(struct FMFSK *fmfsk);

/*
 * Deposit demod statistics into a MODEM_STATS struct
 */
void fmfsk_get_demod_stats(struct FMFSK *fmfsk,struct MODEM_STATS *stats);

/*
 * Returns the number of samples that must be fed to fmfsk_demod the next
 * cycle
 */
uint32_t fmfsk_nin(struct FMFSK *fmfsk);

/*
 * Modulates nbit bits into N samples to be sent through an FM radio
 * 
 * struct FSK *fsk - FSK config/state struct, set up by fsk_create
 * float mod_out[] - Buffer for N samples of modulated FMFSK
 * uint8_t tx_bits[] - Buffer containing Nbits unpacked bits
 */
void fmfsk_mod(struct FMFSK *fmfsk, float fmfsk_out[],uint8_t bits_in[]);


/*
 * Demodulate some number of FMFSK samples. The number of samples to be 
 *  demodulated can be found by calling fmfsk_nin().
 * 
 * struct FMFSK *fsk - FMFSK config/state struct, set up by fsk_create
 * uint8_t rx_bits[] - Buffer for nbit unpacked bits to be written
 * float fsk_in[] - nin samples of modualted FMFSK from an FM radio
 */
void fmfsk_demod(struct FMFSK *fmfsk, uint8_t rx_bits[],float fmfsk_in[]);

#endif
