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
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
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
import static com.deo.colorcontrol.Utils.generateRegion;
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
    
    Array<Float> floatingMaxSampleSmoothingArray;
    Array<Float> smoothingArray_left;
    Array<Float> smoothingArray_right;
    Array<Float> smoothingArray_channelDiff;
    short[] byteBuffer;
    float currentVolume_left;
    float currentVolume_right;
    float currentChannelDiff;
    AudioRecorder audioRecorder;
    
    static String logBuffer = "";
    
    private static SelectBox<String> uvModesSelectionBox;
    private static SelectBox<String> lightModesSelectionBox;
    private static SelectBox<String> arduinoModes;
    String[] arduinoDisplayModes = {"Volume bar", "Rainbow bar", "5 frequency bands", "3 frequency bands", "1 frequency band", "Light", "Running frequencies", "Worm", "Running worm"};
    String[] pcArduinoDisplayModes = {"Volume bar"};
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
    
    @Override
    public void create() {
        
        openPort();
        
        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("font.ttf"));
        FreeTypeFontGenerator.FreeTypeFontParameter parameter = new FreeTypeFontGenerator.FreeTypeFontParameter();
        parameter.size = 20;
        parameter.characters = fontChars;
        font = generator.generateFont(parameter);
        generator.dispose();
        font.getData().markupEnabled = true;
        
        audioRecorder = Gdx.audio.newAudioRecorder(44100, false);
        byteBuffer = new short[2048];
        floatingMaxSampleSmoothingArray = new Array<>();
        smoothingArray_left = new Array<>();
        smoothingArray_right = new Array<>();
        smoothingArray_channelDiff = new Array<>();
        
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
                if(arduinoModeInitialized){
                    sendData((byte)'l', (byte) lightModesSelectionBox.getSelectedIndex());
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
                if(arduinoModeInitialized){
                    sendData((byte)'u', (byte) uvModesSelectionBox.getSelectedIndex());
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
                    } else if (musicNotPlayingTimer < targetFps) { //seconds of silence
                        byte[] colorArray = new byte[362];
                        colorArray[0] = (byte) 'f'; // command byte
                        colorArray[colorArray.length - 1] = 10; // ending byte
                        switch (currentPcArduinoDisplayMode) {
                            case (0):
                            default:
                                for (int i = 1; i < (colorArray.length - 1) * ((currentVolume_right + currentVolume_left) / 2); i++) {
                                    if ((i - 1) % 3 == 0) {
                                        colorArray[i] = (byte) 128;
                                    }
                                }
                                break;
                            case (1):
                                break;
                        }
                        sendData(colorArray);
                        try {
                            Thread.sleep(targetDelta - 2); //2ms processing delay
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }, 0, 1);
    }
    
    void setDrawableDimensions(float width, float height, Drawable... drawables) {
        for (Drawable drawable : drawables) {
            drawable.setMinWidth(width);
            drawable.setMinHeight(height);
        }
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
    
    float addValueToAShiftingArray(int maxSize, float valueToAdd, Array<Float> targetArray) {
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
        return average / (float) targetArray.size;
    }
    
    void processSamples() {
        audioRecorder.read(byteBuffer, 0, byteBuffer.length);
        float sum_right = 0;
        float sum_left = 0;
        float maxSample = 0;
        float currentSample;
        for (int i = 0; i < byteBuffer.length; i++) {
            currentSample = abs(byteBuffer[i]) > 15 ? abs(byteBuffer[i]) : 0;
            maxSample = max(currentSample, maxSample);
            if (i % 2 == 0) {
                sum_left += currentSample;
            } else {
                sum_right += currentSample;
            }
        }
        maxSample /= 2;
        if(maxSample == 0){
            musicNotPlayingTimer ++;
        }else{
            musicNotPlayingTimer = 0;
        }
        float averageMaxSample = addValueToAShiftingArray(1000, maxSample, floatingMaxSampleSmoothingArray);
        
        currentVolume_right = clamp(addValueToAShiftingArray(2, sum_right / (float) (byteBuffer.length / 2 * averageMaxSample), smoothingArray_right), 0, 1);
        currentVolume_left = clamp(addValueToAShiftingArray(2, sum_left / (float) (byteBuffer.length / 2 * averageMaxSample), smoothingArray_left), 0, 1);
        
        currentChannelDiff = addValueToAShiftingArray(2, abs(currentVolume_right - currentVolume_left), smoothingArray_channelDiff);
    }
    
    @Override
    public void render() {
        
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(Color.valueOf("#a4ff63"));
        shapeRenderer.rectLine(5, 0, 5, currentVolume_left * 480, 5);
        shapeRenderer.rectLine(11, 0, 11, currentVolume_right * 480, 5);
        shapeRenderer.setColor(Color.valueOf("#ffc163"));
        shapeRenderer.rectLine(17, 0, 17, currentChannelDiff * 480, 5);
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
