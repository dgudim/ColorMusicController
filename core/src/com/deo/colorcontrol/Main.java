package com.deo.colorcontrol;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.AudioRecorder;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.List;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.deo.colorcontrol.jtransforms.fft.FloatFFT_1D;
import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;

import java.util.Timer;
import java.util.TimerTask;

import static com.badlogic.gdx.math.MathUtils.clamp;
import static com.deo.colorcontrol.LogLevel.ERROR;
import static com.deo.colorcontrol.LogLevel.INFO;
import static com.deo.colorcontrol.LogLevel.NOTHING;
import static com.deo.colorcontrol.LogLevel.WARNING;
import static com.deo.colorcontrol.Main.log;
import static com.deo.colorcontrol.Utils.applyLinearScale;
import static com.deo.colorcontrol.Utils.generateRegion;
import static com.deo.colorcontrol.Utils.setDrawableDimensions;
import static com.deo.colorcontrol.Utils.shiftArray;
import static com.deo.colorcontrol.Utils.smoothArray;
import static java.lang.StrictMath.abs;
import static java.lang.StrictMath.max;

enum LogLevel {NOTHING, INFO, WARNING, ERROR}

public class Main extends ApplicationAdapter {
    
    final String fontChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz1234567890\"!`?'.,;:()[]{}<>|/@\\^$€-%+=#_&~*ёйцукенгшщзхъэждлорпавыфячсмитьбюЁЙЦУКЕНГШЩЗХЪЭЖДЛОРПАВЫФЯЧСМИТЬБЮ";
    BitmapFont font;
    TextureAtlas uiAtlas;
    TextureAtlas uiAtlas_buttons;
    Skin uiTextures;
    ShapeRenderer shapeRenderer;
    SpriteBatch spriteBatch;
    Stage stage;
    
    Array<Float> floatingMaxSampleSmoothingArray = new Array<>();
    Array<float[]> smoothingArray_volume = new Array<>();
    Array<Float> smoothingArray_channelDiff = new Array<>();
    Array<float[]> smoothingArray_deltaVolume = new Array<>();
    Array<float[]> smoothingArray_lowFrequency = new Array<>();
    Array<float[]> smoothingArray_midFrequency = new Array<>();
    Array<float[]> smoothingArray_highFrequency = new Array<>();
    float[] currentVolume = new float[2];
    float currentChannelDiff;
    float[] currentVolumeDelta = new float[2];
    float[] currentBassFrequencyValue = new float[2];
    float[] currentMidFrequencyValue = new float[2];
    float[] currentHighFrequencyValue = new float[2];
    int[] bassFrequencies = new int[]{0, 200};
    int[] midFrequencies = new int[]{500, 2000};
    int[] highFrequencies = new int[]{8000, 20000};
    float[] previousVolume = new float[2];
    AudioRecorder audioRecorder;
    private FloatFFT_1D fft;
    final int frameSize = 2048;
    short[] byteBuffer = new short[frameSize];
    
    final int fftFrameSize = frameSize / 2;
    float[][] current_fftSamples = new float[2][fftFrameSize];
    float[][] fftSamples = new float[2][fftFrameSize];
    float fftDecaySpeed = 3.5f;
    float frequencyToFftSampleConversionStep = fftFrameSize / 20000f;
    
    final int numLeds = 120;
    final float ledStep = 800 / (float) numLeds;
    float ledPosToFftSampleConversionStep = fftFrameSize / (float) numLeds;
    float[] redChannel = new float[numLeds];
    float[] greenChannel = new float[numLeds];
    float[] blueChannel = new float[numLeds];
    byte[] mergedColorBuffer = new byte[numLeds * 3 + 2];
    
    static String logBuffer = "";
    
    private static SelectBox<String> uvModesSelectionBox;
    private static SelectBox<String> lightModesSelectionBox;
    private static SelectBox<String> arduinoModes;
    String[] arduinoDisplayModes = {"Volume bar", "Rainbow bar", "5 frequency bands", "3 frequency bands", "1 frequency band", "Light", "Running frequencies", "Worm", "Running worm"};
    String[] pcArduinoDisplayModes = {"Volume bar", "Running beat", "Frequency flash", "Running frequencies", "Basic fft"};
    String[] uvModes = {"0", "1", "2"};
    String[] lightModes = {"0", "1", "2"};
    private int currentPcArduinoDisplayMode = 0;
    SerialPort arduinoPort;
    int baudRate = 1_000_000;
    int pcModeSafeDelay = 100;
    int targetFps = 60;
    int targetDelta = 1000 / targetFps;
    boolean pcControlled;
    static int errorCount;
    boolean sendingData;
    static boolean arduinoModeInitialized;
    int musicNotPlayingTimer;
    
    Timer updateThread;
    
    public Main() {
    
    }
    
    @Override
    public void create() {
        
        mergedColorBuffer[0] = (byte) 'f'; //starting byte
        mergedColorBuffer[mergedColorBuffer.length - 1] = 1; //ending byte(can be any byte)
        openPort();
        
        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("font.ttf"));
        FreeTypeFontGenerator.FreeTypeFontParameter parameter = new FreeTypeFontGenerator.FreeTypeFontParameter();
        parameter.size = 20;
        parameter.characters = fontChars;
        font = generator.generateFont(parameter);
        generator.dispose();
        font.getData().markupEnabled = true;
        
        audioRecorder = Gdx.audio.newAudioRecorder(44100, false);
        fft = new FloatFFT_1D(fftFrameSize);
        
        spriteBatch = new SpriteBatch();
        shapeRenderer = new ShapeRenderer();
        shapeRenderer.setAutoShapeType(true);
        
        uiAtlas = new TextureAtlas(Gdx.files.internal("ui.atlas"));
        uiAtlas_buttons = new TextureAtlas(Gdx.files.internal("workshop.atlas"));
        uiTextures = new Skin();
        uiTextures.addRegions(uiAtlas);
        uiTextures.addRegions(uiAtlas_buttons);
        
        TextureRegionDrawable BarBackgroundBlank = generateRegion(100, 30, Color.DARK_GRAY);
        TextureRegionDrawable BarBackgroundGrey = generateRegion(100, 30, Color.valueOf("#333333FF"));
        TextureRegionDrawable BarBackgroundEmpty = generateRegion(100, 30, Color.valueOf("#00000000"));
        
        TextButtonStyle textButtonStyle = new TextButtonStyle();
        textButtonStyle.up = uiTextures.getDrawable("blank_shopButton_disabled");
        textButtonStyle.over = uiTextures.getDrawable("blank_shopButton_over");
        textButtonStyle.down = uiTextures.getDrawable("blank_shopButton_enabled");
        textButtonStyle.font = font;
        
        final TextButton openPort = new TextButton("Open port", textButtonStyle);
        openPort.setBounds(0, 440, 140, 40);
        openPort.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                openPort();
            }
        });
        final TextButton closePort = new TextButton("Close port", textButtonStyle);
        closePort.setBounds(0, 390, 140, 40);
        closePort.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                closePort();
            }
        });
        stage = new Stage();
        stage.addActor(openPort);
        stage.addActor(closePort);
        
        SelectBox.SelectBoxStyle selectBoxStyle = new SelectBox.SelectBoxStyle(font, Color.WHITE, BarBackgroundBlank,
                new ScrollPane.ScrollPaneStyle(BarBackgroundGrey, BarBackgroundEmpty, BarBackgroundEmpty, BarBackgroundEmpty, BarBackgroundEmpty),
                new List.ListStyle(font, Color.CORAL, Color.SKY, BarBackgroundGrey));
        
        Label.LabelStyle labelStyle = new Label.LabelStyle(font, Color.WHITE);
        
        arduinoModes = new SelectBox<>(selectBoxStyle);
        arduinoModes.setItems(arduinoDisplayModes);
        arduinoModes.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (arduinoModeInitialized) {
                    sendData((byte) 'm', (byte) arduinoModes.getSelectedIndex());
                }
            }
        });
        arduinoModes.setPosition(150, 260);
        arduinoModes.setWidth(300);
        Label arduinoModeLabel = new Label("Arduino mode", labelStyle);
        arduinoModeLabel.setPosition(0, 262);
        stage.addActor(arduinoModes);
        stage.addActor(arduinoModeLabel);
        
        final SelectBox<String> pcArduinoModes = new SelectBox<>(selectBoxStyle);
        pcArduinoModes.setItems(pcArduinoDisplayModes);
        pcArduinoModes.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                currentPcArduinoDisplayMode = pcArduinoModes.getSelectedIndex();
                for (int i = 0; i < numLeds; i++) {
                    redChannel[i] = 0;
                    greenChannel[i] = 0;
                    blueChannel[i] = 0;
                }
            }
        });
        pcArduinoModes.setPosition(150, 220);
        pcArduinoModes.setWidth(300);
        Label pcArduinoModeLabel = new Label("Pc mode", labelStyle);
        pcArduinoModeLabel.setPosition(61, 222);
        stage.addActor(pcArduinoModes);
        stage.addActor(pcArduinoModeLabel);
        
        lightModesSelectionBox = new SelectBox<>(selectBoxStyle);
        lightModesSelectionBox.setItems(lightModes);
        lightModesSelectionBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (arduinoModeInitialized) {
                    sendData((byte) 'l', (byte) lightModesSelectionBox.getSelectedIndex());
                }
            }
        });
        lightModesSelectionBox.setPosition(150, 150);
        lightModesSelectionBox.setWidth(300);
        Label lightModeLabel = new Label("Light mode", labelStyle);
        lightModeLabel.setPosition(26, 152);
        stage.addActor(lightModesSelectionBox);
        stage.addActor(lightModeLabel);
        
        uvModesSelectionBox = new SelectBox<>(selectBoxStyle);
        uvModesSelectionBox.setItems(uvModes);
        uvModesSelectionBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (arduinoModeInitialized) {
                    sendData((byte) 'u', (byte) uvModesSelectionBox.getSelectedIndex());
                }
            }
        });
        uvModesSelectionBox.setPosition(150, 110);
        uvModesSelectionBox.setWidth(300);
        Label uvModeLabel = new Label("Volume bar\nmode", labelStyle);
        uvModeLabel.setPosition(20, 100);
        uvModeLabel.setAlignment(Align.right);
        stage.addActor(uvModesSelectionBox);
        stage.addActor(uvModeLabel);
        
        CheckBox.CheckBoxStyle checkBoxStyle = new CheckBox.CheckBoxStyle();
        checkBoxStyle.checkboxOff = uiTextures.getDrawable("checkBox_disabled");
        checkBoxStyle.checkboxOver = uiTextures.getDrawable("checkBox_disabled_over");
        checkBoxStyle.checkboxOn = uiTextures.getDrawable("checkBox_enabled");
        checkBoxStyle.checkboxOnOver = uiTextures.getDrawable("checkBox_enabled_over");
        setDrawableDimensions(50, 50, checkBoxStyle.checkboxOff, checkBoxStyle.checkboxOver, checkBoxStyle.checkboxOn, checkBoxStyle.checkboxOnOver);
        checkBoxStyle.font = font;
        
        final CheckBox powerCheckBox = new CheckBox("on ", checkBoxStyle);
        powerCheckBox.setChecked(true);
        powerCheckBox.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                powerCheckBox.setText(powerCheckBox.isChecked() ? "on" : "off");
                sendData((byte) 'p');
            }
        });
        powerCheckBox.setPosition(150, 350);
        powerCheckBox.align(Align.left);
        final CheckBox pcControlCheckBox = new CheckBox("arduino", checkBoxStyle);
        pcControlCheckBox.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                pcControlled = pcControlCheckBox.isChecked();
                sendingData = false;
                pcControlCheckBox.setText(pcControlCheckBox.isChecked() ? "pc" : "arduino");
                sendData((byte) 'c');
            }
        });
        pcControlCheckBox.setPosition(250, 350);
        pcControlCheckBox.align(Align.left);
        final CheckBox brightnessSyncCheckBox = new CheckBox("Brightness sync", checkBoxStyle);
        brightnessSyncCheckBox.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                sendData((byte) 'a');
            }
        });
        brightnessSyncCheckBox.setPosition(150, 300);
        brightnessSyncCheckBox.align(Align.left);
        stage.addActor(powerCheckBox);
        stage.addActor(pcControlCheckBox);
        stage.addActor(brightnessSyncCheckBox);
        
        Gdx.input.setInputProcessor(stage);
        
        updateThread = new Timer();
        updateThread.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                processSamples();
                if (pcControlled) {
                    if (!sendingData) {
                        sendingData = true;
                        try {
                            Thread.sleep(pcModeSafeDelay);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    } else if (musicNotPlayingTimer < targetFps) { //second of silence
                        switch (currentPcArduinoDisplayMode) {
                            case (0):
                            default:
                                for (int i = 0; i < numLeds; i++) {
                                    if (i < numLeds * ((currentVolume[0] + currentVolume[1]) / 2)) {
                                        redChannel[i] = 128;
                                    } else {
                                        redChannel[i] = 0;
                                    }
                                }
                                break;
                            case (1):
                                redChannel[0] = (byte) (255 * clamp((currentVolumeDelta[0]) * 5, 0, 1));
                                greenChannel[0] = (byte) (255 * clamp((currentVolumeDelta[1]) * 5, 0, 1));
                                for (int i = 0; i < numLeds; i++) {
                                    blueChannel[i] = 127 * clamp(currentVolume[1], 0, 1);
                                }
                                shiftArray(1, redChannel);
                                shiftArray(4, greenChannel);
                                break;
                            case (2):
                                for (int i = 0; i < numLeds; i++) {
                                    redChannel[i] = currentBassFrequencyValue[0] / 2048f * 200;
                                    greenChannel[i] = currentMidFrequencyValue[0] / 2048f * 200;
                                    blueChannel[i] = currentHighFrequencyValue[0] / 2048f * 200;
                                }
                                break;
                            case (3):
                                redChannel[0] = currentBassFrequencyValue[0] / 2048f * 200;
                                greenChannel[0] = currentMidFrequencyValue[0] / 2048f * 200;
                                blueChannel[0] = currentHighFrequencyValue[0] / 2048f * 200;
                                shiftArray(4, redChannel);
                                shiftArray(6, greenChannel);
                                shiftArray(8, blueChannel);
                                break;
                            case (4):
                                for (int i = 0; i < numLeds; i++) {
                                    redChannel[i] = current_fftSamples[0][(int) (i * ledPosToFftSampleConversionStep)] / 2048f * 200;
                                }
                                for (int i = 0; i < numLeds; i++) {
                                    greenChannel[i] = current_fftSamples[1][(int) (i * ledPosToFftSampleConversionStep)] / 2048f * 200;
                                }
                                break;
                        }
                        sendColorArray();
                    }
                }
            }
        }, 0, targetDelta);
    }
    
    void closePort() {
        log(INFO, "Closing port");
        if (arduinoPort != null) {
            if (arduinoPort.isOpen()) {
                arduinoPort.removeDataListener();
                arduinoPort.closePort();
                log(INFO, "Closed " + arduinoPort.getSystemPortName());
            } else {
                log(INFO, "Port already closed");
            }
            arduinoPort = null;
        } else {
            log(WARNING, "Couldn't close, port is null");
        }
    }
    
    void openPort() {
        closePort();
        log(INFO, "Opening port");
        SerialPort[] ports = SerialPort.getCommPorts();
        for (SerialPort port : ports) {
            if (port.getDescriptivePortName().toLowerCase().contains("arduino")) {
                arduinoPort = port;
                boolean opened = arduinoPort.openPort(20);
                if (opened) {
                    log(INFO, "Opened " + arduinoPort.getSystemPortName());
                    arduinoPort.setComPortParameters(baudRate, 8, 1, SerialPort.NO_PARITY);
                    arduinoPort.addDataListener(new SerialDataListener(arduinoPort));
                } else {
                    log(ERROR, "Error opening " + arduinoPort.getSystemPortName() + ", port busy");
                    arduinoPort = null;
                }
                break;
            }
        }
        if (arduinoPort == null) {
            log(ERROR, "Couldn't open port");
        }
    }
    
    void sendData(byte... data) {
        if (arduinoPort != null) {
            arduinoPort.writeBytes(data, data.length, 0);
        }
    }
    
    void sendColorArray() {
        for (int i = 1; i < mergedColorBuffer.length - 4; i += 3) {
            mergedColorBuffer[i] = (byte) redChannel[i / 3];
            mergedColorBuffer[i + 1] = (byte) greenChannel[i / 3];
            mergedColorBuffer[i + 2] = (byte) blueChannel[i / 3];
        }
        sendData(mergedColorBuffer);
    }
    
    static void log(LogLevel logLevel, String message) {
        if (message.startsWith("mm")) {
            String[] settings = message.replace("mm", "").trim().split("_");
            arduinoModes.setSelectedIndex(Integer.parseInt(settings[0]));
            uvModesSelectionBox.setSelectedIndex(Integer.parseInt(settings[1]));
            lightModesSelectionBox.setSelectedIndex(Integer.parseInt(settings[2]));
            arduinoModeInitialized = true;
        } else if (message.trim().equals("e")) {
            errorCount++;
        } else {
            String logLevelString;
            switch (logLevel) {
                case NOTHING:
                default:
                    logLevelString = "";
                    break;
                case INFO:
                    logLevelString = "[#63c8ff]INFO:";
                    break;
                case WARNING:
                    logLevelString = "[#ffd063]WARNING:";
                    break;
                case ERROR:
                    logLevelString = "[#ff6363]ERROR:";
                    break;
            }
            logBuffer += logLevelString + message + "\n";
            if (logBuffer.length() > 500) {
                logBuffer = logBuffer.substring(logBuffer.indexOf("\n") + 1);
            }
        }
    }
    
    float addValueToAShiftingArray(int maxSize, float valueToAdd, Array<Float> targetArray, boolean clamp) {
        float average = 0;
        if (targetArray.size < maxSize) {
            targetArray.add(valueToAdd);
            for (int i = 0; i < targetArray.size; i++) {
                average += targetArray.get(i);
            }
        } else {
            for (int i = 0; i < targetArray.size - 1; i++) {
                targetArray.set(i, targetArray.get(i + 1));
                average += targetArray.get(i + 1);
            }
            targetArray.set(targetArray.size - 1, valueToAdd);
            average += valueToAdd;
        }
        average /= (float) targetArray.size;
        if (clamp) {
            average = clamp(average, 0, 1);
        }
        return average;
    }
    
    float[] addValueToAShiftingArray(int maxSize, float[] valueToAdd, Array<float[]> targetArray, boolean clamp) {
        float[] average = new float[valueToAdd.length];
        if (targetArray.size < maxSize) {
            targetArray.add(valueToAdd);
            for (int i = 0; i < targetArray.size; i++) {
                for (int a = 0; a < average.length; a++) {
                    average[a] += targetArray.get(i)[a];
                }
            }
        } else {
            for (int i = 0; i < targetArray.size - 1; i++) {
                targetArray.set(i, targetArray.get(i + 1));
                for (int a = 0; a < average.length; a++) {
                    average[a] += targetArray.get(i + 1)[a];
                }
            }
            targetArray.set(targetArray.size - 1, valueToAdd);
            for (int a = 0; a < average.length; a++) {
                average[a] += valueToAdd[a];
            }
        }
        for (int a = 0; a < average.length; a++) {
            average[a] /= (float) targetArray.size;
            if (clamp) {
                average[a] = clamp(average[a], 0, 1);
            }
        }
        return average;
    }
    
    void processFFtSamples() {
        for (int i = 0; i < frameSize; i += 2) {
            fftSamples[0][i / 2] = byteBuffer[i];
            fftSamples[1][i / 2] = byteBuffer[i + 1];
        }
        fft.realForward(fftSamples[0]);
        fft.realForward(fftSamples[1]);
        applyLinearScale(fftSamples[0], 0.015f);
        applyLinearScale(fftSamples[1], 0.015f);
        fftSamples[0] = smoothArray(fftSamples[0], 2, 10, false);
        fftSamples[1] = smoothArray(fftSamples[1], 2, 10, false);
        for (int i = 0; i < fftFrameSize; i++) {
            current_fftSamples[0][i] += fftSamples[0][i] * fftDecaySpeed;
            current_fftSamples[1][i] += fftSamples[1][i] * fftDecaySpeed;
            current_fftSamples[0][i] /= fftDecaySpeed;
            current_fftSamples[1][i] /= fftDecaySpeed;
        }
        currentBassFrequencyValue = addValueToAShiftingArray(2, getFrequencyVolume(bassFrequencies), smoothingArray_lowFrequency, false);
        currentMidFrequencyValue = addValueToAShiftingArray(2, getFrequencyVolume(midFrequencies), smoothingArray_midFrequency, false);
        currentHighFrequencyValue = addValueToAShiftingArray(2, getFrequencyVolume(highFrequencies), smoothingArray_highFrequency, false);
    }
    
    float[] getFrequencyVolume(int[] frequencyRange) {
        float[] frequencyVolume = new float[2];
        int from = (int) (frequencyRange[0] * frequencyToFftSampleConversionStep);
        int to = (int) (frequencyRange[1] * frequencyToFftSampleConversionStep);
        for (int i = from; i < to; i++) {
            frequencyVolume[0] += current_fftSamples[0][i];
            frequencyVolume[1] += current_fftSamples[1][i];
        }
        frequencyVolume[0] /= (float) (to - from);
        frequencyVolume[1] /= (float) (to - from);
        return frequencyVolume;
    }
    
    void processSamples() {
        audioRecorder.read(byteBuffer, 0, byteBuffer.length);
        processFFtSamples();
        float[] sum = new float[2];
        float maxSample = 0;
        float currentSample;
        for (int i = 0; i < frameSize; i++) {
            currentSample = abs(byteBuffer[i]) > 15 ? abs(byteBuffer[i]) : 0;
            maxSample = max(currentSample, maxSample);
            if (i % 2 == 0) {
                sum[0] += currentSample;
            } else {
                sum[1] += currentSample;
            }
        }
        maxSample /= 2;
        if (maxSample == 0) {
            musicNotPlayingTimer++;
        } else {
            musicNotPlayingTimer = 0;
        }
        float averageMaxSample = addValueToAShiftingArray(targetFps * 10, maxSample, floatingMaxSampleSmoothingArray, false);
        sum[0] /= (byteBuffer.length / 2f * averageMaxSample);
        sum[1] /= (byteBuffer.length / 2f * averageMaxSample);
        
        currentVolume = addValueToAShiftingArray(2, sum, smoothingArray_volume, true);
        
        currentVolumeDelta = addValueToAShiftingArray(2, new float[]{
                        currentVolume[0] - previousVolume[0],
                        currentVolume[1] - previousVolume[1]},
                smoothingArray_deltaVolume, false);
        
        currentChannelDiff = addValueToAShiftingArray(2, abs(currentVolume[1] - currentVolume[0]), smoothingArray_channelDiff, false);
        previousVolume = currentVolume;
    }
    
    @Override
    public void render() {
        
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        float step = 3200 / (float) frameSize;
        shapeRenderer.setColor(Color.valueOf("#ff000033"));
        for (int i = 0; i < fftFrameSize / 2; i++) {
            shapeRenderer.rectLine(i * step, 15, i * step, abs(current_fftSamples[0][i]) / 200 + 15, step);
        }
        shapeRenderer.setColor(Color.valueOf("#0000ff33"));
        for (int i = 0; i < fftFrameSize / 2; i++) {
            shapeRenderer.rectLine(i * step, 15, i * step, abs(current_fftSamples[1][i]) / 200 + 15, step);
        }
        shapeRenderer.setColor(Color.valueOf("#00ff0033"));
        for (int i = 0; i < fftFrameSize / 2; i++) {
            shapeRenderer.rectLine(i * step, 15, i * step, abs(current_fftSamples[1][i] - current_fftSamples[0][i]) / 200 + 15, step);
        }
        shapeRenderer.setColor(Color.valueOf("#a4ff63"));
        shapeRenderer.rectLine(5, 15, 5, currentVolume[0] * 465 + 15, 5);
        shapeRenderer.rectLine(11, 15, 11, currentVolume[1] * 465 + 15, 5);
        shapeRenderer.setColor(Color.valueOf("#ffc163"));
        shapeRenderer.rectLine(17, 15, 17, currentChannelDiff * 465 + 15, 5);
        shapeRenderer.setColor(Color.valueOf("#5563ff"));
        shapeRenderer.rectLine(23, 15, 23, abs(currentVolumeDelta[0]) * 465 + 15, 5);
        shapeRenderer.rectLine(29, 15, 29, abs(currentVolumeDelta[1]) * 465 + 15, 5);
        for (int i = 0; i < numLeds; i++) {
            shapeRenderer.setColor(new Color(Color.rgba8888(redChannel[i] / 255f, greenChannel[i] / 255f, blueChannel[i] / 255f, 1)));
            shapeRenderer.rectLine(i * ledStep, 7.5f, (i + 1) * ledStep, 7.5f, 15);
        }
        shapeRenderer.end();
        spriteBatch.begin();
        
        if (arduinoPort != null) {
            font.draw(spriteBatch,
                    "[#b9ff63]" + arduinoPort.getDescriptivePortName()
                            + "\n Baud rate: " + baudRate / 1000 + "k"
                            + "\n Errors: " + errorCount,
                    150, 470, 800, -1, false);
        } else {
            font.draw(spriteBatch, "[#ffae63]Arduino not connected", 150, 470, 800, -1, false);
        }
        
        font.getData().setScale(0.6f);
        font.draw(spriteBatch, logBuffer, 500, 480, 300, -1, true);
        font.getData().setScale(1);
        
        spriteBatch.end();
        stage.draw();
        stage.act();
    }
    
    @Override
    public void dispose() {
        closePort();
        audioRecorder.dispose();
        updateThread.cancel();
        shapeRenderer.dispose();
        spriteBatch.dispose();
        uiAtlas.dispose();
        uiAtlas_buttons.dispose();
        uiTextures.dispose();
    }
}

class SerialDataListener implements SerialPortDataListener {
    
    SerialPort serialPort;
    StringBuilder messageBuffer = new StringBuilder();
    
    public SerialDataListener(SerialPort serialPort) {
        this.serialPort = serialPort;
    }
    
    @Override
    public int getListeningEvents() {
        return SerialPort.LISTENING_EVENT_DATA_AVAILABLE;
    }
    
    @Override
    public void serialEvent(SerialPortEvent event) {
        if (event.getEventType() != SerialPort.LISTENING_EVENT_DATA_AVAILABLE) {
            return;
        }
        
        byte[] buffer = new byte[serialPort.bytesAvailable()];
        serialPort.readBytes(buffer, buffer.length);
        
        for (byte b : buffer) {
            if (b != (byte) 10) {
                messageBuffer.append((char) b);
            } else {
                log(NOTHING, messageBuffer.toString());
                messageBuffer.delete(0, messageBuffer.length());
            }
        }
    }
}
