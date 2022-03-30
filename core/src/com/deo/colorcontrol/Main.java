package com.deo.colorcontrol;

import static com.badlogic.gdx.graphics.Color.DARK_GRAY;
import static com.badlogic.gdx.graphics.Color.WHITE;
import static com.badlogic.gdx.math.MathUtils.clamp;
import static com.deo.colorcontrol.LogLevel.ERROR;
import static com.deo.colorcontrol.LogLevel.INFO;
import static com.deo.colorcontrol.LogLevel.NOTHING;
import static com.deo.colorcontrol.LogLevel.WARNING;
import static com.deo.colorcontrol.Utils.absoluteArray;
import static com.deo.colorcontrol.Utils.addValueToAShiftingArray;
import static com.deo.colorcontrol.Utils.applyLinearScale;
import static com.deo.colorcontrol.Utils.clampArray;
import static com.deo.colorcontrol.Utils.fillArray;
import static com.deo.colorcontrol.Utils.findAverageValueInAnArray;
import static com.deo.colorcontrol.Utils.findMaxValueInAnArray;
import static com.deo.colorcontrol.Utils.generateRegion;
import static com.deo.colorcontrol.Utils.scaleArray;
import static com.deo.colorcontrol.Utils.setActorColor;
import static com.deo.colorcontrol.Utils.setActorTouchable;
import static com.deo.colorcontrol.Utils.setDrawableDimensions;
import static com.deo.colorcontrol.Utils.shiftArray;
import static com.deo.colorcontrol.Utils.smoothArray;
import static java.lang.StrictMath.abs;
import static java.lang.StrictMath.max;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
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
import com.badlogic.gdx.scenes.scene2d.Touchable;
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
    private static SelectBox<String> uvModesSelectionBox;
    private static SelectBox<String> lightModesSelectionBox;
    private static SelectBox<String> arduinoModes;
    private TextButton closePortButton;
    private CheckBox powerCheckBox, pcControlCheckBox;
    
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
    
    final int previewHeight = 7;
    
    final int numLeds = 120;
    final float ledStep = 800 / (float) numLeds;
    float ledPosToFftSampleConversionStep = (fftFrameSize / 2f) / (float) numLeds; //limit range to 0 - 10Khz, looks better
    float[] redChannel = new float[numLeds];
    float[] greenChannel = new float[numLeds];
    float[] blueChannel = new float[numLeds];
    byte[] mergedColorBuffer = new byte[numLeds * 3 + 2];
    
    static String logBuffer = "";
    
    String[] arduinoDisplayModes = {"Volume bar", "Rainbow bar", "5 frequency bands", "3 frequency bands", "1 frequency band", "Light", "Running frequencies", "Worm", "Running worm"};
    String[] pcArduinoDisplayModes = {"Volume bar", "Running beat blue", "Running beat green", "Running beat red", "Frequency flash", "Running frequencies", "Basic fft"};
    String[] uvModes = {"Basic", "Running volume", "Running volume 2"};
    String[] lightModes = {"Basic", "Color shift", "Color flow"};
    private int currentPcArduinoDisplayMode = 0;
    SerialPort arduinoPort;
    int baudRate = 1_500_000;
    int targetFps = 30;
    int targetDelta = 1000 / targetFps;
    boolean applyLinearScale = true;
    boolean pcControlled;
    static int errorCount;
    boolean sendingData;
    static boolean arduinoInitialized;
    int musicNotPlayingTimer;
    
    Timer updateThread;
    Timer shutdownListenerThread;
    
    public Main() {
    
    }
    
    @Override
    public void create() {
        
        mergedColorBuffer[0] = (byte) 'f'; //starting byte
        mergedColorBuffer[mergedColorBuffer.length - 1] = 1; //ending byte(can be any byte)
        openPort();
        
        final FileHandle shutdownFlag = Gdx.files.absolute("C:\\Users\\kloud\\Documents\\Projects\\ColorMusicController\\desktop\\build\\libs\\shutdown");
        if (shutdownFlag.exists()) {
            shutdownFlag.delete(); //if the flag for some reason is present on startup, delete it
        }
        final Preferences prefs = Gdx.app.getPreferences("ArduinoColorMusicPrefs");
        
        currentPcArduinoDisplayMode = prefs.getInteger("currentPcArduinoDisplayMode", 0);
        processArduinoLog(INFO, "Current pc arduino mode: " + pcArduinoDisplayModes[currentPcArduinoDisplayMode]);
        
        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("font.ttf"));
        FreeTypeFontGenerator.FreeTypeFontParameter parameter = new FreeTypeFontGenerator.FreeTypeFontParameter();
        parameter.size = 17;
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
        
        TextureRegionDrawable BarBackgroundBlank = generateRegion(100, 30, DARK_GRAY);
        TextureRegionDrawable BarBackgroundGrey = generateRegion(100, 30, Color.valueOf("#333333FF"));
        TextureRegionDrawable BarBackgroundEmpty = generateRegion(100, 30, Color.valueOf("#00000000"));
        
        TextButtonStyle textButtonStyle = new TextButtonStyle();
        textButtonStyle.up = uiTextures.getDrawable("blank_shopButton_disabled");
        textButtonStyle.over = uiTextures.getDrawable("blank_shopButton_over");
        textButtonStyle.down = uiTextures.getDrawable("blank_shopButton_enabled");
        textButtonStyle.font = font;
        
        SelectBox.SelectBoxStyle selectBoxStyle = new SelectBox.SelectBoxStyle(font, WHITE, BarBackgroundBlank,
                new ScrollPane.ScrollPaneStyle(BarBackgroundGrey, BarBackgroundEmpty, BarBackgroundEmpty, BarBackgroundEmpty, BarBackgroundEmpty),
                new List.ListStyle(font, Color.CORAL, Color.SKY, BarBackgroundGrey));
        
        Label.LabelStyle labelStyle = new Label.LabelStyle(font, WHITE);
        
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
        
        TextButton openPortButton = new TextButton("Open port", textButtonStyle);
        openPortButton.setBounds(0, 445, 140, 30);
        openPortButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                openPort();
            }
        });
        closePortButton = new TextButton("Close port", textButtonStyle);
        closePortButton.setBounds(0, 405, 140, 30);
        closePortButton.addListener(new ClickListener() {
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
                if (arduinoInitialized) {
                    sendData((byte) 'm', (byte) arduinoModes.getSelectedIndex());
                }
            }
        });
        arduinoModes.setPosition(25, 340);
        arduinoModes.setWidth(300);
        Label arduinoModeLabel = new Label("Arduino mode", labelStyle);
        arduinoModeLabel.setPosition(25, 370);
        
        final SelectBox<String> pcArduinoModes = new SelectBox<>(selectBoxStyle);
        pcArduinoModes.setItems(pcArduinoDisplayModes);
        pcArduinoModes.setSelectedIndex(prefs.getInteger("currentPcArduinoDisplayMode"));
        pcArduinoModes.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                currentPcArduinoDisplayMode = pcArduinoModes.getSelectedIndex();
                prefs.putInteger("currentPcArduinoDisplayMode", currentPcArduinoDisplayMode);
                prefs.flush();
                for (int i = 0; i < numLeds; i++) {
                    redChannel[i] = 0;
                    greenChannel[i] = 0;
                    blueChannel[i] = 0;
                }
            }
        });
        pcArduinoModes.setPosition(25, 270);
        pcArduinoModes.setWidth(300);
        Label pcArduinoModeLabel = new Label("Pc mode", labelStyle);
        pcArduinoModeLabel.setPosition(25, 300);
        
        lightModesSelectionBox = new SelectBox<>(selectBoxStyle);
        lightModesSelectionBox.setItems(lightModes);
        lightModesSelectionBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (arduinoInitialized) {
                    sendData((byte) 'l', (byte) lightModesSelectionBox.getSelectedIndex());
                }
            }
        });
        lightModesSelectionBox.setPosition(25, 200);
        lightModesSelectionBox.setWidth(300);
        Label lightModeLabel = new Label("Light mode", labelStyle);
        lightModeLabel.setPosition(25, 230);
        
        uvModesSelectionBox = new SelectBox<>(selectBoxStyle);
        uvModesSelectionBox.setItems(uvModes);
        uvModesSelectionBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (arduinoInitialized) {
                    sendData((byte) 'u', (byte) uvModesSelectionBox.getSelectedIndex());
                }
            }
        });
        uvModesSelectionBox.setPosition(25, 130);
        uvModesSelectionBox.setWidth(300);
        Label uvModeLabel = new Label("Volume bar mode", labelStyle);
        uvModeLabel.setPosition(25, 160);
        uvModeLabel.setAlignment(Align.right);
        
        powerCheckBox = new CheckBox("on ", checkBoxStyle);
        powerCheckBox.setChecked(true);
        powerCheckBox.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                powerCheckBox.setText(powerCheckBox.isChecked() ? "on" : "off");
                sendData((byte) 'p');
            }
        });
        powerCheckBox.setPosition(330, 430);
        powerCheckBox.align(Align.left);
        pcControlCheckBox = new CheckBox("pc override", checkBoxStyle);
        pcControlCheckBox.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                pcControlled = pcControlCheckBox.isChecked();
                if (pcControlled) {
                    toggleControl(false);
                } else {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Thread.sleep(1300);
                                toggleControl(true);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }).start();
                }
                sendingData = false;
            }
        });
        pcControlCheckBox.setPosition(330, 380);
        pcControlCheckBox.align(Align.left);
        
        final CheckBox linearScaleCheckBox = new CheckBox("linear scale", checkBoxStyle);
        linearScaleCheckBox.setChecked(true);
        linearScaleCheckBox.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                applyLinearScale = !applyLinearScale;
            }
        });
        linearScaleCheckBox.setPosition(330, 330);
        linearScaleCheckBox.align(Align.left);
        
        stage = new Stage();
        stage.addActor(openPortButton);
        stage.addActor(closePortButton);
        stage.addActor(arduinoModes);
        stage.addActor(arduinoModeLabel);
        stage.addActor(pcArduinoModes);
        stage.addActor(pcArduinoModeLabel);
        stage.addActor(lightModesSelectionBox);
        stage.addActor(lightModeLabel);
        stage.addActor(powerCheckBox);
        stage.addActor(pcControlCheckBox);
        stage.addActor(linearScaleCheckBox);
        stage.addActor(uvModesSelectionBox);
        stage.addActor(uvModeLabel);
        toggleUi(false);
        
        Gdx.input.setInputProcessor(stage);
        
        updateThread = new Timer();
        updateThread.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                processSamples();
                if (pcControlled && musicNotPlayingTimer < targetFps * 3) { // 3 seconds of silence
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
        }, 0, targetDelta);
        
        shutdownListenerThread = new Timer();
        shutdownListenerThread.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (shutdownFlag.exists()) {
                    if (pcControlled) {
                        try {
                            if (pcControlCheckBox.isChecked()) {
                                pcControlCheckBox.setChecked(false);
                                pcControlled = false;
                                Thread.sleep(1300);
                                toggleControl(true);
                                Thread.sleep(100);
                            }
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    if (powerCheckBox.isChecked()) {
                        powerCheckBox.setChecked(false);
                        sendData((byte) 'p');
                    }
                    shutdownFlag.delete();
                    Gdx.app.exit();
                }
            }
        }, 0, 500);
        
    }
    
    void toggleControl(boolean active) {
        setActorColor(active ? WHITE : DARK_GRAY, uvModesSelectionBox, lightModesSelectionBox, arduinoModes, powerCheckBox.getLabel(), closePortButton);
        setActorTouchable(active ? Touchable.enabled : Touchable.disabled, uvModesSelectionBox, lightModesSelectionBox, arduinoModes, powerCheckBox, closePortButton);
    }
    
    void toggleUi(boolean active) {
        toggleControl(active);
        setActorColor(active ? WHITE : DARK_GRAY, pcControlCheckBox.getLabel());
        setActorTouchable(active ? Touchable.enabled : Touchable.disabled, pcControlCheckBox);
    }
    
    void closePort() {
        processArduinoLog(INFO, "Closing port");
        if (arduinoPort != null) {
            if (arduinoPort.isOpen()) {
                arduinoPort.removeDataListener();
                arduinoPort.closePort();
                processArduinoLog(INFO, "Closed " + arduinoPort.getSystemPortName());
            } else {
                processArduinoLog(INFO, "Port already closed");
            }
            arduinoPort = null;
        } else {
            processArduinoLog(WARNING, "Couldn't close, port is null");
        }
    }
    
    void openPort() {
        closePort();
        processArduinoLog(INFO, "Opening port");
        SerialPort[] ports = SerialPort.getCommPorts();
        for (SerialPort port : ports) {
            if (port.getDescriptivePortName().toLowerCase().contains("arduino") || port.getSystemPortName().toLowerCase().contains("ttyacm")) {
                arduinoPort = port;
                boolean opened = arduinoPort.openPort(20);
                if (opened) {
                    processArduinoLog(INFO, "Opened " + arduinoPort.getSystemPortName());
                    arduinoPort.setComPortParameters(baudRate, 8, 1, SerialPort.NO_PARITY);
                    arduinoPort.addDataListener(new SerialDataListener(arduinoPort, this));
                } else {
                    processArduinoLog(ERROR, "Error opening " + arduinoPort.getSystemPortName() + ", port busy");
                    arduinoPort = null;
                }
                break;
            }
        }
        if (arduinoPort == null) {
            processArduinoLog(ERROR, "Couldn't open port");
        }
    }
    
    void sendData(final byte... data) {
        if (arduinoPort != null && data != null) {
            arduinoPort.writeBytes(data, data.length, 0);
        }
    }
    
    void sendColorArray() {
        for (int i = 1; i < mergedColorBuffer.length - 3; i += 3) {
            mergedColorBuffer[i] = (byte) clamp(redChannel[(i - 1) / 3], 0, 255);
            mergedColorBuffer[i + 1] = (byte) clamp(greenChannel[(i - 1) / 3], 0, 255);
            mergedColorBuffer[i + 2] = (byte) clamp(blueChannel[(i - 1) / 3], 0, 255);
        }
        sendData(mergedColorBuffer);
    }
    
    void processArduinoLog(LogLevel logLevel, String message) {
        if (message.startsWith("mm")) {
            String[] settings = message.replace("mm", "").trim().split("_");
            arduinoModes.setSelectedIndex(Integer.parseInt(settings[0]));
            uvModesSelectionBox.setSelectedIndex(Integer.parseInt(settings[1]));
            lightModesSelectionBox.setSelectedIndex(Integer.parseInt(settings[2]));
            arduinoInitialized = true;
            toggleUi(true);
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
        
        if (applyLinearScale) {
            applyLinearScale(fftSamples[0], 0.015f);
            applyLinearScale(fftSamples[1], 0.015f);
        }
        
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
            shapeRenderer.rectLine(i * step, previewHeight, i * step, fftSamples_display_copy[0][i] * 100 + previewHeight, step);
        }
        shapeRenderer.setColor(Color.valueOf("#0000ff33"));
        for (int i = 0; i < fftFrameSize / 2; i++) {
            shapeRenderer.rectLine(i * step, previewHeight, i * step, fftSamples_display_copy[1][i] * 100 + previewHeight, step);
        }
        shapeRenderer.setColor(Color.valueOf("#00ff0033"));
        for (int i = 0; i < fftFrameSize / 2; i++) {
            shapeRenderer.rectLine(i * step, previewHeight, i * step, abs(fftSamples_display_copy[1][i] - fftSamples_display_copy[0][i]) * 100 + previewHeight, step);
        }
        shapeRenderer.setColor(Color.valueOf("#a4ff63"));
        shapeRenderer.rectLine(5, previewHeight, 5, currentVolume[0] * 465 + previewHeight, 5);
        shapeRenderer.rectLine(11, previewHeight, 11, currentVolume[1] * 465 + previewHeight, 5);
        shapeRenderer.setColor(Color.valueOf(currentChannelDiff > 0 ? "#ffc163" : "#63c1ff"));
        shapeRenderer.rectLine(17, previewHeight, 17, abs(currentChannelDiff) * 465 + previewHeight, 5);
        shapeRenderer.setColor(Color.valueOf(currentVolumeDelta[0] > 0 ? "#5563ff" : "#ff6355"));
        shapeRenderer.rectLine(23, previewHeight, 23, abs(currentVolumeDelta[0]) * 465 + previewHeight, 5);
        shapeRenderer.setColor(Color.valueOf(currentVolumeDelta[1] > 0 ? "#5563ff" : "#ff6355"));
        shapeRenderer.rectLine(29, previewHeight, 29, abs(currentVolumeDelta[1]) * 465 + previewHeight, 5);
        for (int i = 0; i < numLeds; i++) {
            shapeRenderer.setColor(new Color(Color.rgba8888(clamp(redChannel[i] / 255f, 0, 1), clamp(greenChannel[i] / 255f, 0, 1), clamp(blueChannel[i] / 255f, 0, 1), 1)));
            shapeRenderer.rectLine(i * ledStep, previewHeight / 2f, (i + 1) * ledStep, previewHeight / 2f, 7);
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
            font.draw(spriteBatch, "[#ffae63]Arduino not\nconnected", 150, 470, 800, -1, false);
        }
        
        font.draw(spriteBatch, logBuffer, 500, 480, 300, -1, true);
        
        spriteBatch.end();
        stage.draw();
        stage.act();
    }
    
    @Override
    public void dispose() {
        closePort();
        audioRecorder.dispose();
        updateThread.cancel();
        shutdownListenerThread.cancel();
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
    Main application;
    
    public SerialDataListener(SerialPort serialPort, Main application) {
        this.serialPort = serialPort;
        this.application = application;
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
                application.processArduinoLog(NOTHING, messageBuffer.toString());
                messageBuffer.delete(0, messageBuffer.length());
            }
        }
    }
}
