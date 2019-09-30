/*
 * Copyright (C) Olga Protsenko, Alexey Troshnev and Narek Akinyan 2019
 * Bauman Moscow State Technical University
 * IU3
 */
package sample;

/**
 * @authors Olga Protsenko, Alexey Troshnev and Narek Olkinyan
 */

import javax.sound.sampled.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import static sample.FilterInfo.COEFS;

public class Player implements Runnable {

    private static final int DISTORTION_MAX = 500;
    public static final int SEEK_SLIDER_RANGE = 755;
    public static final int REVERB_STEP = 100;  // миллисекунды
    public static final int REVERB_COUNT = 6;   //кол-во повторений
    public static final int VIBRO_PERIOD = 20;  //длина периода синусоиды в отсчетах
    public static final int FFT_COUNT = 2048;
    private File musicFile;
    private boolean stopFlag;
    private Controller controller;
    private double musicVolume;
    private double[] bandsVolume;
    private int initPosition;
    private int rawDataSize;
    private int pos;


    private AudioFormat format;

    public boolean isStopped() {
        return stopFlag;
    }

    /*
     * Конструтор
     */
    public Player(File musicFile, Controller controller, double musicVolume, double[] bandsVolume, int initPosition) {
        this.musicFile = musicFile;
        this.controller = controller;
        this.musicVolume = musicVolume;
        this.bandsVolume = bandsVolume;
        this.initPosition = initPosition;
    }

    public void stop() {
        stopFlag = true;
    }

    @Override
    public void run() {
        try {
            // создаем входной поток из музыкального файла
            AudioInputStream stream = AudioSystem.getAudioInputStream(musicFile);

            format = stream.getFormat();
            // количестко байт для чтения
            int length = (int) (stream.getFrameLength() * format.getFrameSize());
            // массив байт для чтения
            byte[] samples = new byte[length];
            //преобразуем в поток данных и запишем в наш массик байт
            DataInputStream is = new DataInputStream(stream);
            is.readFully(samples);

            boolean bigEndianFlag = format.isBigEndian();
            ByteBuffer byteBuffer = ByteBuffer.wrap(samples).
                    order(bigEndianFlag ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);

            //размер исходного файла
            rawDataSize = length / 4;
            short[] rawData = new short[rawDataSize];
            //переделываем в моно поток и записываем в rawData
            for (int i = 0; i < rawData.length; i++) {
                rawData[i] = (short) (((int) byteBuffer.getShort() + (int) byteBuffer.getShort()) / 2);
            }
            format = new AudioFormat(format.getSampleRate(), 16, 1, true, bigEndianFlag);

            // use a short, 100ms (1/10th sec) buffer for real-time
            // change to the sound stream
            int bufferSize = Math.round(format.getSampleRate() / 10); //4410
            byte[] buffer = new byte[bufferSize * 2];

            short[] fftBufer = new short[bufferSize];
            FFT fft = new FFT();

            // create a line to play to
            SourceDataLine line;
            try {
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
                line = (SourceDataLine) AudioSystem.getLine(info);
                line.open(format, buffer.length);
            } catch (LineUnavailableException ex) {
                ex.printStackTrace();
                return;
            }

            // start the line
            line.start();

            //находим максимальный размер буфера для ревервирации из массива коэфициентов
            int preBufferSize = Arrays.stream(COEFS)
                    .map((a) -> a.length)
                    .max(Integer::compareTo)
                    .get();
            //буфер для эквалайзера с добавкой для ревербираций
            double[] eqBuffer = new double[preBufferSize + bufferSize];//61+4410
            //создаем массив будущего обработанного буффера (результат)
            double[] effectResult = new double[bufferSize];

            //значения для эффектов
            int reverbStep = (int) format.getSampleRate() / 1000 * REVERB_STEP;
            double vibroMulti = Math.PI / format.getSampleRate() * VIBRO_PERIOD;

            //при изменении ползунка перемотки пользователем, обновляем позицию
            updatePos(initPosition);

            // разбиваем исходный файл на кусочки (буферы) и обрабатываем их
            for (; pos < rawData.length && !stopFlag; pos += bufferSize) {
                //обновляем значение ползунка перемотки по ходу проигрывани
                controller.updateSeekSlider((int) ((long) pos * SEEK_SLIDER_RANGE / rawDataSize));
                //размер буфера
                int size = Math.min(rawData.length - pos, bufferSize);

                //передаем исходные данные для БПФ
                System.arraycopy(rawData, pos, fftBufer, 0, size);
                controller.showChart(fftConvert(fftBufer, fft), controller.before);

                //синхронизируем при перемотке
                synchronized (this) {
                    //Reverberation
                    if (controller.reverberationButton.isSelected()) {
                        for (int i = 0; i < preBufferSize + size; i++) {
                            double sum = 0;

                            for (int k = 0; k < REVERB_COUNT; k++) {
                                int j = this.pos - preBufferSize + i - reverbStep * k;
                                if (j < 0)
                                    continue;
                                //добавляем предыдущие отсчеты с постепенным заглушением
                                sum += rawData[j] * (REVERB_COUNT - k) / (REVERB_COUNT / 2);
                            }
                            eqBuffer[i] = sum;
                        }
                    } else {
                        for (int i = 0; i < preBufferSize + size; i++) {
                            int j = this.pos - preBufferSize + i;
                            eqBuffer[i] = j >= 0 ? rawData[j] : 0;
                        }
                    }

                    //vibrato
                    if (controller.vibratoButton.isSelected()) {
                        for (int i = 0; i < preBufferSize + size; i++) {
                            int j = this.pos - preBufferSize + i;
                            //колеблемся от середины
                            eqBuffer[i] *= (Math.sin(j * vibroMulti) + 2) / 2;
                        }
                    }

                    //обработка каждой полосы фильтра
                    if (!controller.equalizerOff.isSelected()) {
                        for (int c = 0; c < COEFS.length; c++) {
                            double[] coefBand = COEFS[c];
                            //увеличение громкости в Дц, необходимо домножить на коэфициент в степени
                            double pow = Math.pow(1.259D, bandsVolume[c]);
                            //бежим по буферу и перемножаем с нужными коэффициентами и громкостью полосы
                            for (int i = 0; i < size; i++) {
                                int coefSize = coefBand.length;
                                double sumValue = 0;
                                for (int j = 0; j < coefSize; j++) {
                                    int coefPos = preBufferSize + i - coefSize + j;
                                    sumValue += eqBuffer[coefPos] * coefBand[j] * pow / COEFS.length;
                                }
                                //суммируем результаты обработки каждой полосы
                                effectResult[i] += sumValue * 2.5;
                            }
                        }
                    } else {
                        System.arraycopy(eqBuffer, preBufferSize, effectResult, 0, bufferSize);
                    }
                    //домножаем результат на громкость самой музыки
                    for (int i = 0; i < size; i++) {
                        effectResult[i] = (effectResult[i] * musicVolume);
                    }

                    //Distortion effect
                    if (controller.dictortionButton.isSelected()) {
                        for (int i = 0; i < size; i++) {
                            if (effectResult[i] > DISTORTION_MAX) {
                                effectResult[i] = DISTORTION_MAX;
                            }
                        }
                    }
                }

                //переводим в нужный формат
                for (int i = 0; i < size; i++) {
                    short val = (short) effectResult[i];
                    buffer[i * 2] = (byte) val;
                    buffer[i * 2 + 1] = (byte) (val >> 8);
                    fftBufer[i] = val;//закидываем в массив для БПФ в short-ах
                }
                //очищаем результатывный массив
                Arrays.fill(effectResult, 0);

                //передаем для отображения БПФ
                controller.showChart(fftConvert(fftBufer, fft), controller.after);

                //записываем переведенный результат
                line.write(buffer, 0, size * 2);
            }

            // wait until all data is played, then close the line
            line.drain();
            line.close();

        } catch (UnsupportedAudioFileException | IOException ex) {
            ex.printStackTrace();
        }

    }

    /*Вызов БПФ для отображения частотной области
     */
    private double[] fftConvert(short[] fftBufer, FFT fft) {
        double[] fftData = fft.getFFTData(fftBufer, FFT_COUNT);
        double[] tmpArr = new double[fftData.length / 2];//отсекаем половину
        System.arraycopy(fftData, 0, tmpArr, 0, tmpArr.length);
        return tmpArr;
    }

    /*установка громкости музыки*/
    public void setMusicVolume(double musicVolume) {
        this.musicVolume = musicVolume;
    }

    /*установка громкости полос*/
    public void setBandsVolume(double[] bandsVolume) {
        this.bandsVolume = bandsVolume;
    }

    /*установка позиции ползунка*/
    public void setPosition(int position) {
        updatePos(position);
    }

    /*обновление позиции ползунка*/
    private synchronized void updatePos(int position) {
        pos = (int) ((long) rawDataSize * position / SEEK_SLIDER_RANGE);
    }

    public AudioFormat getFormat() {
        return format;
    }

}
