/*
 * Copyright (C) Olga Protsenko, Alexey Troshnev and Narek Akinyan 2019
 * Bauman Moscow State Technical University
 * IU3
 */
package sample;

/**
 * @authors Olga Protsenko, Alexey Troshnev and Narek Akinyan
 */

import static java.lang.Math.*;


public class FFT {
    // analyzeValues - массив анализируемых данных, size - длина массива должна быть кратна степени 2.
// fftValue - массив полученных значений.

    private static final double DOUBLE_PI = 6.283185307179586;

    public double[] getFFTData(final short[] analyzeValues, int size) {

        double[] fftValue = new double[size];

        int i, j, n, m, Mmax, Istp;
        double Tmpr, Tmpi, Wtmp, Theta;
        double Wpr, Wpi, Wr, Wi;
        double[] Tmvl;

        n = size * 2;
        Tmvl = new double[n];
        for (i = 0; i < n; i += 2) {
            Tmvl[i] = 0;
            Tmvl[i + 1] = analyzeValues[i / 2];

        }

        i = 1;
        j = 1;
        while (i < n) {
            if (j > i) {
                Tmpr = Tmvl[i];
                Tmvl[i] = Tmvl[j];
                Tmvl[j] = Tmpr;
                Tmpr = Tmvl[i + 1];
                Tmvl[i + 1] = Tmvl[j + 1];
                Tmvl[j + 1] = Tmpr;
            }
            i = i + 2;
            m = size;
            while ((m >= 2) && (j > m)) {
                j = j - m;
                m = m >> 1;
            }
            j = j + m;
        }

        Mmax = 2;
        while (n > Mmax) {
            Theta = -DOUBLE_PI / Mmax;
            Wpi = sin(Theta);
            Wtmp = sin(Theta / 2);
            Wpr = Wtmp * Wtmp * 2;
            Istp = Mmax * 2;
            Wr = 1;
            Wi = 0;
            m = 1;

            while (m < Mmax) {
                i = m;
                m = m + 2;
                Tmpr = Wr;
                Tmpi = Wi;
                Wr = Wr - Tmpr * Wpr - Tmpi * Wpi;
                Wi = Wi + Tmpr * Wpi - Tmpi * Wpr;

                while (i < n) {
                    j = i + Mmax;
                    Tmpr = Wr * Tmvl[j] - Wi * Tmvl[j - 1];
                    Tmpi = Wi * Tmvl[j] + Wr * Tmvl[j - 1];
                    Tmvl[j] = Tmvl[i] - Tmpr;
                    Tmvl[j - 1] = Tmvl[i - 1] - Tmpi;
                    Tmvl[i] = Tmvl[i] + Tmpr;
                    Tmvl[i - 1] = Tmvl[i - 1] + Tmpi;
                    i = i + Istp;
                }
            }
            Mmax = Istp;
        }

        for (i = 0; i < size; i++) {
            j = i * 2;
            fftValue[i] = 2 * sqrt(pow(Tmvl[j], 2) + pow(Tmvl[j + 1], 2)) / size;
        }
        return fftValue;
    }

}

