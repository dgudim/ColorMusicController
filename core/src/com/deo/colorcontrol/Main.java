package com.deo.colorcontrol;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.AudioRecorder;
import com.badlogic.gdx.files.FileHandle;
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
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
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
import static com.deo.colorcontrol.Utils.absoluteArray;
import static com.deo.colorcontrol.Utils.addValueToAShiftingArray;
import static com.deo.colorcontrol.Utils.applyLinearScale;
import static com.deo.colorcontrol.Utils.clampArray;
import static com.deo.colorcontrol.Utils.fillArray;
import static com.deo.colorcontrol.Utils.findAverageValueInAnArray;
import static com.deo.colorcontrol.Utils.findMaxValueInAnArray;
import static com.deo.colorcontrol.Utils.formatNumber;
import static com.deo.colorcontrol.Utils.generateRegion;
import static com.deo.colorcontrol.Utils.scaleArray;
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
    
    Array<Float> floatingMaxVolumeSmoothingArray = new Array<>();
    
    Array<float[]> smoothingArray_volume = new Array<>();
    Array<Float> smoothingArray_channelDiff = new Array<>();
    Array<float[]> smoothingArray_deltaVolume = new Array<>();
    
    Array<float[]> smoothingArray_lowFrequency = new Array<>();
    Array<float[]> smoothingArray_midFrequency = new Array<>();
    Array<float[]> smoothingArray_highFrequency = new Array<>();
    
    Array<float[]> fftSamplesLeftSmoothingArray = new Array<>();
    Array<float[]> fftSamplesRightSmoothingArray = new Array<>();
    
    Array<Float> floatingMaxSampleSmoothingArray = new Array<>();
    
    float[] currentVolume = new float[2]; // range 0 - 1
    float currentChannelDiff; // range -1 - 1
    float[] currentVolumeDelta = new float[2]; // range -1 - 1
    float[] currentBassFrequencyValue = new float[2]; //range 0 - 1
    float[] currentMidFrequencyValue = new float[2]; //range 0 - 1
    float[] currentHighFrequencyValue = new float[2]; //range 0 - 1
    int[] bassFrequencies = new int[]{0, 200};
    int[] midFrequencies = new int[]{500, 2000};
    int[] highFrequencies = new int[]{8000, 20000};
    float[] previousVolume = new float[2];
    AudioRecorder audioRecorder;
    private FloatFFT_1D fft;
    final int frameSize = 2048;
    short[] byteBuffer = new short[frameSize];
    
    final int fftFrameSize = frameSize / 2;
    float[][] fftSamples = new float[2][fftFrameSize];  //range 0 - 1
    
    float[][] fftSamples_display_copy = new float[2][fftFrameSize];
    
    float frequencyToFftSampleConversionStep = fftFrameSize / 20000f;
    
    final int numLeds = 120;
    final float ledStep = 800 / (float) numLeds;
    float ledPosToFftSampleConversionStep = (fftFrameSize / 2f) / (float) numLeds; //limit range to 0 - 10Khz, looks better
    float[] redChannel = new float[numLeds];
    float[] greenChannel = new float[numLeds];
    float[] blueChannel = new float[numLeds];
    byte[] mergedColorBuffer = new byte[numLeds * 3 + 2];
    
    static String logBuffer = "";
    
    private static SelectBox<String> uvModesSelectionBox;
    private static SelectBox<String> lightModesSelectionBox;
    private static SelectBox<String> arduinoModes;
    String[] arduinoDisplayModes = {"Volume bar", "Rainbow bar", "5 frequency bands", "3 frequency bands", "1 frequency band", "Light", "Running frequencies", "Worm", "Running worm"};
    String[] pcArduinoDisplayModes = {"Volume bar", "Running beat blue", "Running beat green", "Running beat red", "Frequency flash", "Running frequencies", "Basic fft"};
    String[] uvModes = {"Basic", "Running volume", "Running volume 2"};
    String[] lightModes = {"Basic", "Color shift", "Color flow"};
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
    
    int targetAnimationBufferSize = 1;
    Array<byte[]> animationBuffer;
    
    Timer updateThread;
    
    public Main() {
    
    }
    
    @Override
    public void create() {
        
        mergedColorBuffer[0] = (byte) 'f'; //starting byte
        mergedColorBuffer[mergedColorBuffer.length - 1] = 1; //ending byte(can be any byte)
        openPort();
        
        FileHandle shutdownFlag = Gdx.files.absolute("C:\\Users\\kloud\\Documents\\Projects\\ColorMusicController\\desktop\\build\\libs\\shutdown");
        
        if (shutdownFlag.exists()) {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            shutdownFlag.delete();
            sendData((byte) 'p');
            closePort();
            System.exit(3);
        }
        
        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("font.ttf"));
        FreeTypeFontGenerator.FreeTypeFontParameter parameter = new FreeTypeFontGenerator.FreeTypeFontParameter();
        parameter.size = 20;
        parameter.characters = fontChars;
        font = generator.generateFont(parameter);
        generator.dispose();
        font.getData().markupEnabled = true;
        
        initDisplayBuffer();
        
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
        
        SelectBox.SelectBoxStyle selectBoxStyle = new SelectBox.SelectBoxStyle(font, Color.WHITE, BarBackgroundBlank,
                new ScrollPane.ScrollPaneStyle(BarBackgroundGrey, BarBackgroundEmpty, BarBackgroundEmpty, BarBackgroundEmpty, BarBackgroundEmpty),
                new List.ListStyle(font, Color.CORAL, Color.SKY, BarBackgroundGrey));
        
        Label.LabelStyle labelStyle = new Label.LabelStyle(font, Color.WHITE);
        
        CheckBox.CheckBoxStyle checkBoxStyle = new CheckBox.CheckBoxStyle();
        checkBoxStyle.checkboxOff = uiTextures.getDrawable("checkBox_disabled");
        checkBoxStyle.checkboxOver = uiTextures.getDrawable("checkBox_disabled_over");
        checkBoxStyle.checkboxOn = uiTextures.getDrawable("checkBox_enabled");
        checkBoxStyle.checkboxOnOver = uiTextures.getDrawable("checkBox_enabled_over");
        setDrawableDimensions(50, 50, checkBoxStyle.checkboxOff, checkBoxStyle.checkboxOver, checkBoxStyle.checkboxOn, checkBoxStyle.checkboxOnOver);
        checkBoxStyle.font = font;
        
        Slider.SliderStyle sliderStyle = new Slider.SliderStyle();
        sliderStyle.background = uiTextures.getDrawable("progressBarBg");
        setDrawableDimensions(120, 50, sliderStyle.background);
        sliderStyle.knob = uiTextures.getDrawable("progressBarKnob");
        sliderStyle.knobOver = uiTextures.getDrawable("progressBarKnob_over");
        sliderStyle.knobDown = uiTextures.getDrawable("progressBarKnob_enabled");
        setDrawableDimensions(30, 55, sliderStyle.knob, sliderStyle.knobOver, sliderStyle.knobDown);
        
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
        
        final Slider delaySlider = new Slider(1, targetFps + 1, 1, false, sliderStyle);
        delaySlider.setValue(targetAnimationBufferSize);
        delaySlider.setBounds(30, 25, 300, 10);
        final Label delaySliderLabel = new Label("Animation delay:" + (targetAnimationBufferSize - 1) + "(" + getAnimationDelayInSeconds() + "s)", labelStyle);
        delaySliderLabel.setPosition(340, 15);
        delaySlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                delaySliderLabel.setText("Animation delay:" + ((int) delaySlider.getValue() - 1) + "(" + formatNumber(((int) delaySlider.getValue() - 1) / (float) targetFps, 2) + "s)");
            }
        });
        delaySlider.addListener(new ClickListener() {
            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                targetAnimationBufferSize = (int) delaySlider.getValue();
                super.touchUp(event, x, y, pointer, button);
            }
        });
        
        stage = new Stage();
        stage.addActor(openPort);
        stage.addActor(closePort);
        stage.addActor(arduinoModes);
        stage.addActor(arduinoModeLabel);
        stage.addActor(pcArduinoModes);
        stage.addActor(pcArduinoModeLabel);
        stage.addActor(lightModesSelectionBox);
        stage.addActor(lightModeLabel);
        stage.addActor(powerCheckBox);
        stage.addActor(pcControlCheckBox);
        stage.addActor(uvModesSelectionBox);
        stage.addActor(uvModeLabel);
        stage.addActor(delaySlider);
        stage.addActor(delaySliderLabel);
        
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
                    } else if (musicNotPlayingTimer < targetFps * 3) { // 3 seconds of silence
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
                                fillArray(blueChannel, 127 * clamp(currentVolume[1], 0, 1));
                                shiftArray(1, redChannel);
                                shiftArray(4, greenChannel);
                                break;
                            case (2):
                                blueChannel[0] = (byte) (255 * clamp((currentVolumeDelta[0]) * 5, 0, 1));
                                redChannel[0] = (byte) (255 * clamp((currentVolumeDelta[1]) * 5, 0, 1));
                                fillArray(greenChannel, 127 * clamp(currentVolume[1], 0, 1));
                                shiftArray(1, blueChannel);
                                shiftArray(4, redChannel);
                                break;
                            case (3):
                                greenChannel[0] = (byte) (255 * clamp((currentVolumeDelta[0]) * 5, 0, 1));
                                blueChannel[0] = (byte) (255 * clamp((currentVolumeDelta[1]) * 5, 0, 1));
                                fillArray(redChannel, 127 * clamp(currentVolume[1], 0, 1));
                                shiftArray(1, greenChannel);
                                shiftArray(4, blueChannel);
                                break;
                            case (4):
                                fillArray(redChannel, currentBassFrequencyValue[0] * 255);
                                fillArray(greenChannel, currentMidFrequencyValue[0] * 255);
                                fillArray(blueChannel, currentHighFrequencyValue[0] * 255);
                                break;
                            case (5):
                                redChannel[0] = currentBassFrequencyValue[0] * 255;
                                greenChannel[0] = currentMidFrequencyValue[0] * 255;
                                blueChannel[0] = currentHighFrequencyValue[0] * 255;
                                shiftArray(4, redChannel);
                                shiftArray(6, greenChannel);
                                shiftArray(8, blueChannel);
                                break;
                            case (6):
                                for (int i = 0; i < numLeds; i++) {
                                    redChannel[i] = fftSamples[0][(int) (i * ledPosToFftSampleConversionStep)] * 200;
                                }
                                for (int i = 0; i < numLeds; i++) {
                                    greenChannel[i] = fftSamples[1][(int) (i * ledPosToFftSampleConversionStep)] * 200;
                                }
                                break;
                        }
                        sendColorArray();
                    }
                }
            }
        }, 0, targetDelta);
    }
    
    private void resizeAnimationBuffer() {
        if (animationBuffer.size != targetAnimationBufferSize) {
            initDisplayBuffer();
            log(INFO, "Resized animation buffer to " + animationBuffer.size + "(" + getAnimationDelayInSeconds() + "s)");
        }
    }
    
    private void initDisplayBuffer() {
        animationBuffer = new Array<>(targetAnimationBufferSize);
        for (int i = 0; i < targetAnimationBufferSize; i++) {
            animationBuffer.add(null);
        }
    }
    
    float getAnimationDelayInSeconds() {
        return formatNumber((animationBuffer.size - 1) / (float) targetFps, 2);
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
    
    void sendData(final byte... data) {
        if (arduinoPort != null && data != null) {
            arduinoPort.writeBytes(data, data.length, 0);
        }
    }
    
    void sendColorArray() {
        for (int i = 1; i < mergedColorBuffer.length - 4; i += 3) {
            mergedColorBuffer[i] = (byte) clamp(redChannel[i / 3], 0, 255);
            mergedColorBuffer[i + 1] = (byte) clamp(greenChannel[i / 3], 0, 255);
            mergedColorBuffer[i + 2] = (byte) clamp(blueChannel[i / 3], 0, 255);
        }
        resizeAnimationBuffer();
        animationBuffer.set(targetAnimationBufferSize - 1, mergedColorBuffer);
        sendData(animationBuffer.get(0));
        for (int i = 0; i < animationBuffer.size - 1; i++) {
            animationBuffer.set(i, animationBuffer.get(i + 1));
        }
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
    
    void processFFtSamples() {
        for (int i = 0; i < frameSize; i += 2) {
            fftSamples[0][i / 2] = byteBuffer[i];
            fftSamples[1][i / 2] = byteBuffer[i + 1];
        }
        
        fft.realForward(fftSamples[0]);
        fft.realForward(fftSamples[1]);
        
        applyLinearScale(fftSamples[0], 0.015f);
        applyLinearScale(fftSamples[1], 0.015f);
        
        absoluteArray(fftSamples[0]);
        absoluteArray(fftSamples[1]);
        
        smoothArray(fftSamples[0], 2, 10);
        smoothArray(fftSamples[1], 2, 10);
        
        fftSamples[0] = addValueToAShiftingArray(3, fftSamples[0], fftSamplesLeftSmoothingArray, false);
        fftSamples[1] = addValueToAShiftingArray(3, fftSamples[1], fftSamplesRightSmoothingArray, false);
        
        float maxFrequencyVolume = adaptiveVolumeGain(
                targetFps * 10,
                max(findMaxValueInAnArray(fftSamples[0]), findMaxValueInAnArray(fftSamples[1])),
                floatingMaxSampleSmoothingArray);
        
        scaleArray(fftSamples[0], maxFrequencyVolume);
        scaleArray(fftSamples[1], maxFrequencyVolume);
        clampArray(fftSamples[0], 0, 1);
        clampArray(fftSamples[1], 0, 1);
        
        for (int c = 0; c < fftSamples.length; c++) {
            System.arraycopy(fftSamples[c], 0, fftSamples_display_copy[c], 0, fftSamples[c].length);
        }
        
        currentBassFrequencyValue = addValueToAShiftingArray(2, getFrequencyVolume(bassFrequencies), smoothingArray_lowFrequency, false);
        currentMidFrequencyValue = addValueToAShiftingArray(2, getFrequencyVolume(midFrequencies), smoothingArray_midFrequency, false);
        currentHighFrequencyValue = addValueToAShiftingArray(2, getFrequencyVolume(highFrequencies), smoothingArray_highFrequency, false);
    }
    
    float adaptiveVolumeGain(int maxSize, float valueToAdd, Array<Float> targetArray) {
        if (findAverageValueInAnArray(targetArray) < valueToAdd / 3f) {
            valueToAdd *= 7;
        }
        return addValueToAShiftingArray(maxSize, valueToAdd, targetArray, false);
    }
    
    float[] getFrequencyVolume(int[] frequencyRange) {
        float[] frequencyVolume = new float[2];
        int from = (int) (frequencyRange[0] * frequencyToFftSampleConversionStep);
        int to = (int) (frequencyRange[1] * frequencyToFftSampleConversionStep);
        for (int i = from; i < to; i++) {
            frequencyVolume[0] += fftSamples[0][i];
            frequencyVolume[1] += fftSamples[1][i];
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
        float averageMaxSample = adaptiveVolumeGain(targetFps * 10, maxSample, floatingMaxVolumeSmoothingArray);
        scaleArray(sum, byteBuffer.length / 2f * averageMaxSample);
        
        currentVolume = addValueToAShiftingArray(2, sum, smoothingArray_volume, true);
        
        currentVolumeDelta = addValueToAShiftingArray(2, new float[]{
                        currentVolume[0] - previousVolume[0],
                        currentVolume[1] - previousVolume[1]},
                smoothingArray_deltaVolume, false);
        
        currentChannelDiff = addValueToAShiftingArray(2, currentVolume[1] - currentVolume[0], smoothingArray_channelDiff, false);
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
            shapeRenderer.rectLine(i * step, 15, i * step, fftSamples_display_copy[0][i] * 100 + 15, step);
        }
        shapeRenderer.setColor(Color.valueOf("#0000ff33"));
        for (int i = 0; i < fftFrameSize / 2; i++) {
            shapeRenderer.rectLine(i * step, 15, i * step, fftSamples_display_copy[1][i] * 100 + 15, step);
        }
        shapeRenderer.setColor(Color.valueOf("#00ff0033"));
        for (int i = 0; i < fftFrameSize / 2; i++) {
            shapeRenderer.rectLine(i * step, 15, i * step, abs(fftSamples_display_copy[1][i] - fftSamples_display_copy[0][i]) * 100 + 15, step);
        }
        shapeRenderer.setColor(Color.valueOf("#a4ff63"));
        shapeRenderer.rectLine(5, 15, 5, currentVolume[0] * 465 + 15, 5);
        shapeRenderer.rectLine(11, 15, 11, currentVolume[1] * 465 + 15, 5);
        shapeRenderer.setColor(Color.valueOf(currentChannelDiff > 0 ? "#ffc163" : "#63c1ff"));
        shapeRenderer.rectLine(17, 15, 17, abs(currentChannelDiff) * 465 + 15, 5);
        shapeRenderer.setColor(Color.valueOf(currentVolumeDelta[0] > 0 ? "#5563ff" : "#ff6355"));
        shapeRenderer.rectLine(23, 15, 23, abs(currentVolumeDelta[0]) * 465 + 15, 5);
        shapeRenderer.setColor(Color.valueOf(currentVolumeDelta[1] > 0 ? "#5563ff" : "#ff6355"));
        shapeRenderer.rectLine(29, 15, 29, abs(currentVolumeDelta[1]) * 465 + 15, 5);
        for (int i = 0; i < numLeds; i++) {
            shapeRenderer.setColor(new Color(Color.rgba8888(clamp(redChannel[i] / 255f, 0, 1), clamp(greenChannel[i] / 255f, 0, 1), clamp(blueChannel[i] / 255f, 0, 1), 1)));
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
