/*
 * Copyright (C) Oleg Kabanov 2019
 * Bauman Moscow State Technical University
 * IU3
 */
package sample;

/**
 * @authors Oleg Kabanov 2019
 */

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.chart.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

import java.io.*;
import java.math.BigDecimal;

public class Controller {
    public static final double STEP = 0.1;

    //пока по умолчанию
    private File musicFile;

    private Player player;
    private Slider[] bands;
    private boolean lockSeekEvent = false;

    /*Начальная инициализация
     * оси частот(пересчитываем после БПФ)
     * громкости музыки
     * положения перемотки
     * громкости каждой полосы
     * */
    public void initialize() {

        StringConverter<Number> freqConverter = new StringConverter<Number>() {

            @Override
            public String toString(Number object) {
                if (player == null) {
                    return "";
                }
                double i = Math.exp(object.doubleValue());
                double frequency = i * player.getFormat().getSampleRate() / player.FFT_COUNT;//переводим в другие частоты после преобразования Фурье
                BigDecimal bigDecimal = BigDecimal.valueOf(frequency);
                int length = bigDecimal.setScale(0, BigDecimal.ROUND_HALF_UP).toString().length();//округляем
                return bigDecimal.setScale(2 - length, BigDecimal.ROUND_HALF_UP).toPlainString();
            }

            @Override
            public Number fromString(String string) {
                return null;
            }
        };
        ((ValueAxis<Number>) before.getXAxis()).setTickLabelFormatter(freqConverter);
        ((ValueAxis<Number>) after.getXAxis()).setTickLabelFormatter(freqConverter);

        volume.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (player != null) {
                player.setMusicVolume(newValue.doubleValue());
            }
        });

        seekSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (player != null && !lockSeekEvent) {
                player.setPosition(newValue.intValue());
            }
        });

        bands = new Slider[]{band0, band1, band2, band3, band4, band5, band6, band7};
        ChangeListener<Number> bandListener = (observable, oldValue, newValue) -> {
            if (player != null) {
                player.setBandsVolume(collectBandVolumes());
            }
        };

        for (int i = 0; i < FilterInfo.COEFS.length; i++) {
            bands[i].valueProperty().addListener(bandListener);
        }

        before.getStylesheets().add(Main.class.getResource("chart-line-symbol.css").toExternalForm());
        after.getStylesheets().add(Main.class.getResource("chart-line-symbol.css").toExternalForm());
    }

    /*
     * собираем все значения громкостей полос
     * */
    private double[] collectBandVolumes() {
        double[] values = new double[bands.length];
        for (int i = 0; i < bands.length; i++) {
            values[i] = bands[i].getValue();
        }
        return values;
    }

    @FXML
    public void pauseMusic() {
        player.stop();
    }

    @FXML
    Slider volume;

    @FXML
    Button openFile;

    @FXML
    LineChart<Number, Number> before;
    @FXML
    LineChart<Number, Number> after;

    @FXML
    ToggleButton envelopeButton;
    @FXML
    ToggleButton delayButton;
    @FXML
    ToggleButton equalizerOff;

    @FXML
    Slider band0, band1, band2, band3, band4, band5, band6, band7;

    @FXML
    Button play;

    @FXML
    public void playMusic() {
        if (player == null) {
            //плеер создается в потоке
            player = new Player(musicFile, this, volume.getValue(), collectBandVolumes(), (int) seekSlider.getValue());
            Thread thread = new Thread(player);
            //чтобы поток прекращался,когда программа закрылась
            thread.setDaemon(true);
            thread.start();
            play.setText("Pause");
            return;
        }
        if (player.isStopped()) {
            player = null;
            playMusic();
        }
        else {
            play.setText("Play");
            player.stop();
        }

    }

    /*
     * Отображение осей графиков и их преобразования для оптимизации
     * */
    public void showChart(double[] mas, LineChart<Number, Number> lineChart) {

        XYChart.Series currentSeries = new XYChart.Series();
        double curX = 0;
        double curY = 0;
        for (int i = 1; i < mas.length; i++) {

            double x = Math.log(i);//логарифмическая ось, в начале мало точек, в конце слишком много
            if (x - curX < STEP) {//проверяем разницу между шагами оси
                curY = (curY + mas[i]) / 2;//берем среднее каждый раз пока шаг маленький
            } else {
                currentSeries.getData().add(new XYChart.Data((curX + x) / 2, curY));//достигли величины шага и добавили
                curX = x;
                curY = mas[i];
            }
        }

        Platform.runLater(() -> {
            if (!lineChart.getData().isEmpty()) {
                lineChart.getData().remove(0);
            }
            lineChart.getData().add(currentSeries);
        });
    }


    @FXML
    public void openDialog() {
        FileChooser fileChooser = new FileChooser();
        FileChooser.ExtensionFilter extensionFilter = new FileChooser.ExtensionFilter("Аудиофайлы", "*.mp3", "*.wav");

        fileChooser.getExtensionFilters().add(extensionFilter);
        openFile.setOnAction(event -> {
            File track = fileChooser.showOpenDialog(openFile.getScene().getWindow());
            if (track != null) {
                trackInfo(track);
                musicFile = track;
            }
        });
    }

    @FXML
    Label trackName;

    @FXML
    private void trackInfo(File track) {
        trackName.setText("Трек: " + track.getName().substring(0, track.getName().length() - 4));
    }


    @FXML
    Slider seekSlider;

    public void updateSeekSlider(int position) {
        lockSeekEvent = true; //блокируем чтобы два раза не обновлялось
        seekSlider.setValue(position);
        lockSeekEvent = false;
    }

}
