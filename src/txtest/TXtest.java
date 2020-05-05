/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package txtest;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Window;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortEventListener;
import jssc.SerialPortException;
import jssc.SerialPortList;

/**
 *
 * @author Fedorenko Aleksandr
 */
public class TXtest extends javax.swing.JFrame {

    private static final Logger LOGGER = Logger.getLogger(TXtest.class.getName());
    private static FileHandler fh = null;

    public final int NOP = 0;
    public final int GETTX = 1;
    public final int GETTP = 2;
    public final int GETPAINFO = 3;
    public final int CLION = 4;
    public final int CLIOFF = 5;
    public final int CDMGR = 6;
    public final int SETTX = 7;
    public final int SETTP = 8;
    public final int SETPAINFO031 = 9;
    public final int SETPAINFO130 = 10;

    final String TX_POWER = "TX Power(mW): ";
    final String TX_FREQ = "TX frequency(Hz): ";
    final String PA_INFO = "Phy Channel Enable = ";
    final String PA_INFO_START = "TxCtrl Enable = ";

    final String GETTXTP_END = "\n";
    final String GETPAINFO_END = "%>";
    final String PHY_CH_OFF = "Phy Channel Enable = 0";
    final String PHY_CH_ON = "Phy Channel Enable = 1";
    final String CLI_ON_OK = "Hytera CHU board CLI v3.0 starts  (Version 5.1.04.008)";
    final String BAD_COMMAND_INPUT = "Bad command input";
    final String CLI_OFF_OK = "Hytera CHU board CLI v5.0 stops";
    final String CD_MGR_OK = "temperature control\n"
            + "%>";
    final String SET_SUCCESS = "set success!";
    final String ERROR_CODE = "error code";
    final String SET_PHYINFO_SUCCESS = "set PHYInfo success!";

    SerialPort serialPort;
    String portName;
    int baudRate;
    int dataBits;
    int stopBits;
    int parity;

    int command = NOP;

    String answerCollector;

    // Стили редактора
    private Style boldRed = null; // стиль полужирного красного текста
    private Style boldGreen = null; // стиль полужирного зеленого текста
    private Style normal = null; // стиль обычного текста

    private final String STYLE_heading = "heading",
            STYLE_normal = "normal",
            FONT_style = "Monospaced";

    String[] ports;
    boolean portIsOpen = false;
    RefreshPortsThread rpt;
    RefreshTXStateThread refreshTXState;
    Thread rst1;

    Color bgColor;
    Color powerOnColor = Color.GREEN;
    Color powerOffColor = Color.GREEN;

    boolean isOK = false;
    boolean isError = false;

    boolean isAutoCommand = false;
    boolean isScanOn = true;
    boolean clion = false;
    boolean cdmgr = false;

    ArrayList<Image> imageList;

    /**
     * Creates new form MainJFrame
     */
    public TXtest() {
        answerCollector = "";
        try {
            fh = new FileHandler(LocalDateTime.now().format(DateTimeFormatter.ofPattern("uuuuMMddHHmmss")) + "TXtest.log", false);
            Logger l = Logger.getLogger("");
            fh.setFormatter(new SimpleFormatter());
            l.addHandler(fh);
            l.setLevel(Level.CONFIG);
            
            Image img1 = new ImageIcon(this.getClass().getResource("icons/icons8-sea-radio-16.png")).getImage();
            Image img2 = new ImageIcon(this.getClass().getResource("icons/icons8-sea-radio-32.png")).getImage();
            Image img3 = new ImageIcon(this.getClass().getResource("icons/icons8-sea-radio-48.png")).getImage();
            Image img4 = new ImageIcon(this.getClass().getResource("icons/icons8-sea-radio-96.png")).getImage();
            Image img5 = new ImageIcon(this.getClass().getResource("icons/icons8-sea-radio-256.png")).getImage();

            imageList = new ArrayList<>();
            imageList.add(img1);
            imageList.add(img2);
            imageList.add(img3);
            imageList.add(img4);
            imageList.add(img5);
            initComponents();
            bgColor = jPanel1.getBackground();
            powerOffColor = bgColor;           

            createStyles(jTextPaneLog);
            isAutoCommand = getAutoCommandState();
            isScanOn = getScanState();
//        refreshPorts();
            rpt = new RefreshPortsThread();
            rpt.start();

            rst1 = new RunningStringThread2(jPanel3, jLabelRunningString);
            rst1.start();
            buttonsOn(false);
        } catch (IOException | SecurityException ex) {
            LOGGER.log(Level.SEVERE, "Ошибка в конструкторе.", ex);

        }
    }

    private void buttonsOn(boolean switchState) {
        jButtonCdMgr.setEnabled(switchState);
        jButtonCliOff.setEnabled(switchState);
        jButtonCliOn.setEnabled(switchState);
        jButtonInfo.setEnabled(switchState);
        jButtonPowerOFF.setEnabled(switchState);
        jButtonPowerON.setEnabled(switchState);
        jButtonReadAll.setEnabled(switchState);
        jButtonReadFreq.setEnabled(switchState);
        jButtonReadPower.setEnabled(switchState);
        jButtonSendFreq.setEnabled(switchState);
        jButtonSendPower.setEnabled(switchState);
        jButtonSendPower50W.setEnabled(switchState);
        jButtonSendCommand.setEnabled(switchState);
    }

    private boolean getAutoCommandState() {
        return jCheckBoxAutoCommand.isSelected();
    }

    private boolean getScanState() {
        return jCheckBoxScanOn.isSelected();
    }

    public class RefreshPortsThread extends Thread {

        @Override
        public void run() {
            while (true) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ex) {
                    javax.swing.JOptionPane.showMessageDialog(null, "Ошибка в RefreshPortsThread: " + ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
                    appendBoldRedText("Ошибка в RefreshPortsThread: " + ex.getMessage());
                    printStackTraceElements(ex);
                }
                if (!portIsOpen) {
                    refreshPorts();
                }
            }
        }
    }

    private void refreshPorts() {
//        if (jComboBoxPortName.isPopupVisible()) {//            
//            return;
//        }
//        
        String[] serialPortNames = SerialPortList.getPortNames();
        for (String port : serialPortNames) {
            addNewItem(port, jComboBoxPortName);
        }
        int itemCount = jComboBoxPortName.getItemCount();
//        String[] oldSerialPortNames = new String[itemCount];
//        for (int i = 0; i < itemCount; i++) {
//            oldSerialPortNames[i] = jComboBoxPortName.getItemAt(i);
//        }

        for (int i = 0; i < itemCount; i++) {
            String itemAt = jComboBoxPortName.getItemAt(i);
            if (!isItem(itemAt, serialPortNames)) {
                jComboBoxPortName.removeItemAt(i);
                i = 0;
                itemCount = jComboBoxPortName.getItemCount();
            }
        }
    }

    void readFreq() {
        command = GETTX;
        sendString("gettx\r\n");
    }

    void readPower() {
        command = GETTP;
        sendString("gettp\r\n");
    }

    void readPAinfo() {
        command = GETPAINFO;
        sendString("getpainfo\r\n");
    }

    void readPAinfoSilent() {
        command = GETPAINFO;
        sendSilentString("getpainfo\r\n");
    }

    void readAll() {
        readFreq();
        waitSomeTime(1);
        readPower();
        waitSomeTime(1);
        readPAinfo();
//        waitSomeTime(1);
    }

    void cliOn() {
        command = CLION;
        sendString("cli on\r\n");
        clion = true;
    }

    void cliOff() {
        clion = false;
        cdmgr = false;
        command = CLIOFF;
        sendString("cli off\r\n");
        changeBackgroundColor(bgColor);
    }

    void cdmgr() {
        command = CDMGR;
        sendString("cd mgr\r\n");
        cdmgr = true;
    }

    void powerOn() {
        command = SETPAINFO031;
        sendString("setpainfo 0 3 1\r\n");
    }

    void powerOff() {
        command = SETPAINFO130;
        sendString("setpainfo 1 3 0\r\n");
    }

    void setPower() {
        String tmpStr;
        //        tmpStr = jTextFieldPower.getText().trim();
        tmpStr = jComboBoxPower.getSelectedItem().toString().trim();
        try {
            int tmpInt = Integer.parseInt(tmpStr);
            if (tmpInt < 1000) {
                javax.swing.JOptionPane.showMessageDialog(this, "Слишком маленькая мощность.", "Ошибка", JOptionPane.ERROR_MESSAGE);
                appendBoldRedText("Слишком маленькая мощность.");
                return;
            }
            if (tmpInt > 50000) {
                javax.swing.JOptionPane.showMessageDialog(this, "Слишком большая мощность.", "Ошибка", JOptionPane.ERROR_MESSAGE);
                appendBoldRedText("Слишком большая мощность.");
                return;
            }
            addNewItem(tmpStr, jComboBoxPower);
            if (!jButtonSendPower.isEnabled()) {
                return;
            }
            command = SETTP;
            sendString("settp " + tmpStr + "\r\n");
            waitSomeTime(1);
            if (isAutoCommand) {
                readPower();
            }
        } catch (NumberFormatException ex) {
            javax.swing.JOptionPane.showMessageDialog(this, tmpStr + " Не могу преобразовать в число.", "Ошибка", JOptionPane.ERROR_MESSAGE);
            appendBoldRedText(tmpStr + " Не могу преобразовать в число.");
            //            printStackTraceElements(ex);
        }
    }

    void setFreq() {
        String tmpStr;
        tmpStr = jComboBoxFreq.getSelectedItem().toString().trim();
        try {
            int tmpInt = Integer.parseInt(tmpStr);
            if (tmpInt < 136000000) {
                javax.swing.JOptionPane.showMessageDialog(this, "Слишком маленькая частота.", "Ошибка", JOptionPane.ERROR_MESSAGE);
                appendBoldRedText("Слишком маленькая частота.");
                return;
            }
            if (tmpInt > 174000000) {
                javax.swing.JOptionPane.showMessageDialog(this, "Слишком большая частота.", "Ошибка", JOptionPane.ERROR_MESSAGE);
                appendBoldRedText("Слишком большая частота.");
                return;
            }
            addNewItem(tmpStr, jComboBoxFreq);
            if (!jButtonSendFreq.isEnabled()) {
                return;
            }
            command = SETTX;
            sendString("settx " + tmpStr + "\r\n");
            waitSomeTime(1);
            if (isAutoCommand) {
                readFreq();
            }
        } catch (NumberFormatException ex) {
            javax.swing.JOptionPane.showMessageDialog(this, tmpStr + " Не могу преобразовать в число.", "Ошибка", JOptionPane.ERROR_MESSAGE);
            appendBoldRedText(tmpStr + " Не могу преобразовать в число.");
            //            printStackTraceElements(ex);
        }
    }

    void sendCommand() {
        String tmpStr;
        tmpStr = jComboBoxCommand.getSelectedItem().toString().trim();
        try {
            addNewItem(tmpStr, jComboBoxCommand);
            if (!jButtonSendCommand.isEnabled()) {
                return;
            }
            command = NOP;
            sendString(tmpStr + "\r\n");
            waitSomeTime(0);
        } catch (NumberFormatException ex) {
            javax.swing.JOptionPane.showMessageDialog(this, tmpStr + " Не могу преобразовать в число.", "Ошибка", JOptionPane.ERROR_MESSAGE);
            appendBoldRedText(tmpStr + " Не могу преобразовать в число.");
            //            printStackTraceElements(ex);
        }
    }

    public class SetPortSettingsThread extends Thread {

        @Override
        public void run() {
            jButtonOpenClosePort.setEnabled(false);
            if (setPortSettings()) {
                jButtonOpenClosePort.setText("Закрыть порт");
                jComboBoxPortName.setEnabled(false);
                buttonsOn(true);
            }
            jButtonOpenClosePort.setEnabled(true);
        }
    }

    public class RefreshTXStateThread extends Thread {

        @Override
        public void run() {
            while (true) {
                try {
                    if (isAutoCommand) {
                        if (isScanOn) {
                            if (clion) {
                                if (cdmgr) {
                                    if (command == NOP) {
                                        readPAinfoSilent();
                                    } else {
                                        appendBoldRedText("Запрос состояния передатчика отклонен.");
                                    }
                                }
                            }
                        }
                    }
                    Thread.sleep(2000);
                } catch (InterruptedException ex) {
                    javax.swing.JOptionPane.showMessageDialog(null, "Ошибка в RefreshPortsThread: " + ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
                    appendBoldRedText("Ошибка в RefreshPortsThread: " + ex.getMessage());
                    printStackTraceElements(ex);
                }
            }
        }
    }

    private void appendText(String str) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                insertText(jTextPaneLog, LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS ")) + str + "\r\n", normal);
                LOGGER.log(Level.INFO, str);
            }
        });

    }

    private void appendBoldRedText(String str) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                insertText(jTextPaneLog, LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS ")) + str + "\r\n", boldRed);
                LOGGER.log(Level.WARNING, str);
            }
        });

    }

    private void appendBoldGreenText(String str) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                
                insertText(jTextPaneLog, LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS ")) + str + "\r\n", boldGreen);
                LOGGER.log(Level.WARNING, str);
            }
        });

    }

    /**
     * Процедура формирования стилей редактора
     *
     * @param editor редактор
     */
    private void createStyles(JTextPane editor) {
        // Создание стилей
        normal = editor.addStyle(STYLE_normal, null);
        StyleConstants.setFontFamily(normal, FONT_style);
        StyleConstants.setFontSize(normal, 12);
        // Наследуем свойстdо FontFamily
        boldRed = editor.addStyle(STYLE_heading, normal);
//        StyleConstants.setFontSize(heading, 12);
        StyleConstants.setBold(boldRed, true);
        StyleConstants.setForeground(boldRed, Color.red);
        boldGreen = editor.addStyle(STYLE_heading, normal);
//        StyleConstants.setFontSize(heading, 12);
        StyleConstants.setBold(boldGreen, true);
        StyleConstants.setForeground(boldGreen, Color.GREEN);

    }

    /**
     * Процедура добавления в редактор строки определенного стиля
     *
     * @param editor редактор
     * @param string строка
     * @param style стиль
     */
    private void insertText(JTextPane editor, String string, Style style) {
        try {
            Document doc = editor.getDocument();
            doc.insertString(doc.getLength(), string, style);
        } catch (BadLocationException ex) {
            javax.swing.JOptionPane.showMessageDialog(this, "Ошибка в insertText(): " + ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
            appendBoldRedText("Ошибка в insertText(): " + ex.getMessage());
            printStackTraceElements(ex);
        }
    }

    void waitSomeTime(int waitTime) {
        appendText("Жду " + waitTime + " секунд(ы)");
        try {
            Thread.sleep(waitTime * 1000);
        } catch (InterruptedException ex) {
            //Logger.getLogger(TXtest.class.getName()).log(Level.SEVERE, null, ex);
            javax.swing.JOptionPane.showMessageDialog(this, "Ошибка в waitSomeTime(): " + ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
            appendBoldRedText("Ошибка в waitSomeTime(): " + ex.getMessage());
            printStackTraceElements(ex);
        }
    }

    void enableEventListener() throws SerialPortException {
        /*
        Добавляем прослушиватель событий.
        В качестве параметров в методе задаются:
            1) объект типа "SerialPortEventListener" 
        Этот объект должен быть должным образом описан, как он 
        будет отвечать за обработку произошедших событий.
            2) маска событий. чтобы сделать ее нужно 
        использовать переменные с префиксом "MASK_" например 
        "MASK_RXCHAR"
         */
        serialPort.addEventListener(new Reader(), SerialPort.MASK_RXCHAR //Здесь объекту serialPort передается ссылка на анонимный объект класса Reader,
                | SerialPort.MASK_RXFLAG //который является обработчиком событий и реализует интерфейс 
                | SerialPort.MASK_CTS //SerialPortEventListener
                | SerialPort.MASK_DSR
                | SerialPort.MASK_RLSD);

    }

    boolean setPortSettings() {
        try {
            portName = jComboBoxPortName.getSelectedItem().toString();
            baudRate = SerialPort.BAUDRATE_115200;
            dataBits = SerialPort.DATABITS_8;
            stopBits = SerialPort.STOPBITS_1;
            parity = SerialPort.PARITY_NONE;
            serialPort = new SerialPort(portName); // в переменную serialPort заносим выбраный COM-порт
            if (serialPort.openPort()) { // Пытаемся открыть порт, если он открывается, то
                appendText("Порт " + portName + " открыт.");
                if (serialPort.setParams(baudRate, dataBits, stopBits, parity)) { // пытаемся установить параметры порта, если они устанавливаются, то 
                    appendText("Параметры порта установлены.");
                    portIsOpen = true;
                    enableEventListener();
                    if (isAutoCommand) {
                        appendText("Включаем режим теста.");
                        cliOn();
                        waitSomeTime(1);
                        cdmgr();
                        waitSomeTime(2);
                        appendText("Выключаем передачу.");
                        powerOff();
                        waitSomeTime(1);
                        readAll();
                    }
                    refreshTXState = new RefreshTXStateThread();
                    refreshTXState.start();

                    //jButtonOpenPort.setText("Close port"); // меняем надпись на кнопке на "Close port"
                    /*
                        Добавляем прослушиватель событий.
                        В качестве параметров в методе задаются:
                            1) объект типа "SerialPortEventListener" 
                        Этот объект должен быть должным образом описан, как он 
                        будет отвечать за обработку произошедших событий.
                            2) маска событий. чтобы сделать ее нужно 
                        использовать переменные с префиксом "MASK_" например 
                        "MASK_RXCHAR"
                     */
//                        serialPort.addEventListener(new Reader(), SerialPort.MASK_RXCHAR |
//                                                                  SerialPort.MASK_RXFLAG |
//                                                                  SerialPort.MASK_CTS |
//                                                                  SerialPort.MASK_DSR |
//                                                                  SerialPort.MASK_RLSD);
                    //enableControls(true);
//                        if(serialPort.isCTS()){
//                            jLabelCTS.setBorder(NimbusGui.borderStatusOn);
//                            jLabelCTS.setBackground(NimbusGui.colorStatusOnBG);
//                        }
//                        else {
//                            jLabelCTS.setBorder(NimbusGui.borderStatusOff);
//                            jLabelCTS.setBackground(NimbusGui.colorStatusOffBG);
//                        }
//                        if(serialPort.isDSR()){
//                            jLabelDSR.setBorder(NimbusGui.borderStatusOn);
//                            jLabelDSR.setBackground(NimbusGui.colorStatusOnBG);
//                        }
//                        else {
//                            jLabelDSR.setBorder(NimbusGui.borderStatusOff);
//                            jLabelDSR.setBackground(NimbusGui.colorStatusOffBG);
//                        }
//                        if(serialPort.isRLSD()){
//                            jLabelRLSD.setBorder(NimbusGui.borderStatusOn);
//                            jLabelRLSD.setBackground(NimbusGui.colorStatusOnBG);
//                        }
//                        else {
//                            jLabelRLSD.setBorder(NimbusGui.borderStatusOff);
//                            jLabelRLSD.setBackground(NimbusGui.colorStatusOffBG);
//                        }
//                        if(serialPort.setRTS(true)){
//                            jToggleButtonRTS.setSelected(true);
//                        }
//                        if(serialPort.setDTR(true)){
//                            jToggleButtonDTR.setSelected(true);
//                        }
                } else {
                    //DialogMessage dialogMessage = new DialogMessage(this, DialogMessage.TYPE_ERROR, "Setting parameters", "Can't set selected parameters.");
                    serialPort.closePort();
                    appendText("Порт " + portName + " закрыт");
                }
            }
        } catch (SerialPortException ex) {
            javax.swing.JOptionPane.showMessageDialog(this, "Ошибка в setPortSettings(): " + ex.getExceptionType(), "Порт: " + ex.getPortName(), JOptionPane.ERROR_MESSAGE);
            appendBoldRedText("Ошибка в setPortSettings(): " + ex.getMessage());
            printStackTraceElements(ex);
            return false;
        } catch (Exception ex) {
            //DialogMessage dialogMessage = new DialogMessage(this, DialogMessage.TYPE_ERROR, "Writing data", "Error occurred while writing data.");
            javax.swing.JOptionPane.showMessageDialog(this, "Ошибка в setPortSettings(): " + ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
            appendBoldRedText("Ошибка в setPortSettings(): " + ex.getMessage());
            printStackTraceElements(ex);
            return false;
        }
        return true;
    }

    private void sendString(String str) {
        sendSilentString(str);
        appendText("Отправлена команда: \n" + str);
    }

    private synchronized void sendSilentString(String str) {
        //String str = jTextFieldOut.getText();
        if (serialPort == null || serialPort.getPortName() == null) {
            appendBoldRedText("COM порт не установлен.");
            return;
        }
        if (str.length() > 0) {
            try {
                isOK = false;
                isError = false;
                serialPort.writeBytes(str.getBytes());
//                str = "";
//                waitAnswer();
            } catch (SerialPortException ex) {
                //DialogMessage dialogMessage = new DialogMessage(this, DialogMessage.TYPE_ERROR, "Writing data", "Error occurred while writing data.");
                javax.swing.JOptionPane.showMessageDialog(this, "Ошибка в sendString(): " + ex.getExceptionType(), "Порт: " + ex.getPortName(), JOptionPane.ERROR_MESSAGE);
                appendBoldRedText("Ошибка в sendString(): " + ex.getMessage());
                printStackTraceElements(ex);
            } catch (Exception ex) {
                //DialogMessage dialogMessage = new DialogMessage(this, DialogMessage.TYPE_ERROR, "Writing data", "Error occurred while writing data.");
                javax.swing.JOptionPane.showMessageDialog(this, "Ошибка в sendString(): " + ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
                appendBoldRedText("Ошибка в sendString(): " + ex.getMessage());
                printStackTraceElements(ex);
            }
        }
    }

    void waitAnswer() {
        try {
            Thread.sleep(1000);
//            while (isOK == false & isError == false) {
//                Thread.sleep(100);
////                appendBoldRedText("Жду ответа...");
//            }
//            if (isOK) {
//                appendBoldRedText("Команда успешно выполнена.");
//                return;
//            }
//            if (isError) {
//                appendBoldRedText("Ошибка при выполнении команды.");
//                return;
//            }
        } catch (Exception ex) {
            Logger.getLogger(TXtest.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public class ClosePortThread extends Thread {

        @Override
        public void run() {
            closePort();
        }
    }

    void closePort() {
        jButtonOpenClosePort.setEnabled(false);
        if (serialPort != null && serialPort.isOpened()) {
            try {
                if (isAutoCommand) {
                    powerOff();
                    waitSomeTime(1);
                    cliOff();
                    waitSomeTime(1);
                }
                serialPort.closePort();
                buttonsOn(false);
            } catch (SerialPortException ex) {
                //DialogMessage dialogMessage = new DialogMessage(this, DialogMessage.TYPE_ERROR, "Writing data", "Error occurred while writing data.");
                javax.swing.JOptionPane.showMessageDialog(this, "Ошибка в closePort(): " + ex.getExceptionType(), "Порт: " + ex.getPortName(), JOptionPane.ERROR_MESSAGE);
                appendBoldRedText("Ошибка в closePort(): " + ex.getMessage());
                printStackTraceElements(ex);
            } catch (Exception ex) {
                //DialogMessage dialogMessage = new DialogMessage(this, DialogMessage.TYPE_ERROR, "Writing data", "Error occurred while writing data.");
                javax.swing.JOptionPane.showMessageDialog(this, "Ошибка в closePort(): " + ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
                appendBoldRedText("Ошибка в closePort(): " + ex.getMessage());
                printStackTraceElements(ex);
            }
            appendText("Порт " + portName + " закрыт");
            System.out.println("Порт " + portName + " закрыт");
        }
        portIsOpen = false;
        jComboBoxPortName.setEnabled(true);
        jButtonOpenClosePort.setText("Открыть порт");
        jButtonOpenClosePort.setEnabled(true);
    }

    private Object makeObj(final String item) {
        return new Object() {
            public String toString() {
                return item;
            }
        };
    }

    private void addNewItem(String newString, JComboBox comboBox) {
        int itemCount = comboBox.getItemCount();
        String itemAt;
        for (int i = 0; i < itemCount; i++) {
            itemAt = comboBox.getItemAt(i).toString();
            if (itemAt.equals(newString)) {
                return;
            }
        }
//        comboBox.addItem((newString));
        comboBox.insertItemAt(newString, 0);
    }

    private boolean isItem(String oldString, String[] newStringArray) {
        int itemCount = newStringArray.length;
        String itemAt;
        for (int i = 0; i < itemCount; i++) {
            itemAt = newStringArray[i];
            if (itemAt.equals(oldString)) {
                return true;
            }
        }
        return false;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPopupMenu1 = new javax.swing.JPopupMenu();
        jMenuItem1 = new javax.swing.JMenuItem();
        jPanel1 = new javax.swing.JPanel();
        jPanel2 = new javax.swing.JPanel();
        jPanelPortSelector = new javax.swing.JPanel();
        jCheckBoxAutoCommand = new javax.swing.JCheckBox();
        jComboBoxPortName = new javax.swing.JComboBox<>(SerialPortList.getPortNames());
        jButtonOpenClosePort = new javax.swing.JButton();
        jPanelCommands = new javax.swing.JPanel();
        jButtonCliOn = new javax.swing.JButton();
        jButtonCdMgr = new javax.swing.JButton();
        jButtonCliOff = new javax.swing.JButton();
        jButtonReadAll = new javax.swing.JButton();
        jComboBoxCommand = new javax.swing.JComboBox<>();
        jButtonSendCommand = new javax.swing.JButton();
        jPanelFreq = new javax.swing.JPanel();
        jButtonReadFreq = new javax.swing.JButton();
        jComboBoxFreq = new javax.swing.JComboBox<>();
        jLabelFreqUnit = new javax.swing.JLabel();
        jButtonSendFreq = new javax.swing.JButton();
        jPanelPower = new javax.swing.JPanel();
        jButtonReadPower = new javax.swing.JButton();
        jComboBoxPower = new javax.swing.JComboBox<>();
        jLabelPowerUnit = new javax.swing.JLabel();
        jButtonSendPower = new javax.swing.JButton();
        jButtonSendPower50W = new javax.swing.JButton();
        jPanelTransmitDriver = new javax.swing.JPanel();
        jButtonPowerON = new javax.swing.JButton();
        jButtonPowerOFF = new javax.swing.JButton();
        jButtonInfo = new javax.swing.JButton();
        jTextFieldInfo = new javax.swing.JTextField();
        jCheckBoxScanOn = new javax.swing.JCheckBox();
        jPanelLog = new javax.swing.JPanel();
        jScrollPane2 = new javax.swing.JScrollPane();
        jTextPaneLog = new javax.swing.JTextPane();
        jPanel3 = new javax.swing.JPanel();
        jLabelRunningString = new javax.swing.JLabel();

        jMenuItem1.setText("Очистить");
        jMenuItem1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItem1ActionPerformed(evt);
            }
        });
        jPopupMenu1.add(jMenuItem1);

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("TX Тест");
        setIconImages(imageList);
        setMinimumSize(new java.awt.Dimension(800, 530));
        setSize(new java.awt.Dimension(974, 590));
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });
        addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                formKeyPressed(evt);
            }
            public void keyTyped(java.awt.event.KeyEvent evt) {
                formKeyTyped(evt);
            }
        });

        jPanel1.setMinimumSize(new java.awt.Dimension(0, 0));
        jPanel1.setPreferredSize(new java.awt.Dimension(0, 0));
        jPanel1.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyTyped(java.awt.event.KeyEvent evt) {
                jPanel1KeyTyped(evt);
            }
        });

        jPanel2.setPreferredSize(new java.awt.Dimension(300, 209));

        jPanelPortSelector.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createEtchedBorder(), "Выбор COM порта:"));

        jCheckBoxAutoCommand.setSelected(true);
        jCheckBoxAutoCommand.setText("Автокоманды");
        jCheckBoxAutoCommand.setToolTipText("Дополнять команды записи, командами проверки того, что записано.");
        jCheckBoxAutoCommand.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                jCheckBoxAutoCommandItemStateChanged(evt);
            }
        });

        jComboBoxPortName.setModel(new javax.swing.DefaultComboBoxModel<>(SerialPortList.getPortNames()));

        jButtonOpenClosePort.setText("Открыть порт");
        jButtonOpenClosePort.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonOpenClosePortActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanelPortSelectorLayout = new javax.swing.GroupLayout(jPanelPortSelector);
        jPanelPortSelector.setLayout(jPanelPortSelectorLayout);
        jPanelPortSelectorLayout.setHorizontalGroup(
            jPanelPortSelectorLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelPortSelectorLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jComboBoxPortName, javax.swing.GroupLayout.PREFERRED_SIZE, 62, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jButtonOpenClosePort)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jCheckBoxAutoCommand)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanelPortSelectorLayout.setVerticalGroup(
            jPanelPortSelectorLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelPortSelectorLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelPortSelectorLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jComboBoxPortName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jButtonOpenClosePort)
                    .addComponent(jCheckBoxAutoCommand))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanelCommands.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createEtchedBorder(), "Команды:"));

        jButtonCliOn.setText("cli on");
        jButtonCliOn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonCliOnActionPerformed(evt);
            }
        });

        jButtonCdMgr.setText("cd mgr");
        jButtonCdMgr.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonCdMgrActionPerformed(evt);
            }
        });

        jButtonCliOff.setText("cli off");
        jButtonCliOff.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonCliOffActionPerformed(evt);
            }
        });

        jButtonReadAll.setText("Прочитать все");
        jButtonReadAll.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonReadAllActionPerformed(evt);
            }
        });

        jComboBoxCommand.setEditable(true);
        jComboBoxCommand.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "cli on" }));
        jComboBoxCommand.setToolTipText("Здесь можно ввести произвольную команду.");
        jComboBoxCommand.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jComboBoxCommandActionPerformed(evt);
            }
        });

        jButtonSendCommand.setText("Отправить");
        jButtonSendCommand.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonSendCommandActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanelCommandsLayout = new javax.swing.GroupLayout(jPanelCommands);
        jPanelCommands.setLayout(jPanelCommandsLayout);
        jPanelCommandsLayout.setHorizontalGroup(
            jPanelCommandsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelCommandsLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelCommandsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(jPanelCommandsLayout.createSequentialGroup()
                        .addComponent(jButtonCliOn)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jButtonCdMgr)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jButtonCliOff)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jButtonReadAll))
                    .addComponent(jComboBoxCommand, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jButtonSendCommand)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanelCommandsLayout.setVerticalGroup(
            jPanelCommandsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelCommandsLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelCommandsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButtonCliOn)
                    .addComponent(jButtonCdMgr)
                    .addComponent(jButtonCliOff)
                    .addComponent(jButtonReadAll))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanelCommandsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jComboBoxCommand, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jButtonSendCommand))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanelFreq.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createEtchedBorder(), "Частота:"));

        jButtonReadFreq.setText("Считать");
        jButtonReadFreq.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonReadFreqActionPerformed(evt);
            }
        });

        jComboBoxFreq.setEditable(true);
        jComboBoxFreq.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jComboBoxFreqActionPerformed(evt);
            }
        });

        jLabelFreqUnit.setText("Гц");

        jButtonSendFreq.setText("Записать");
        jButtonSendFreq.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonSendFreqActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanelFreqLayout = new javax.swing.GroupLayout(jPanelFreq);
        jPanelFreq.setLayout(jPanelFreqLayout);
        jPanelFreqLayout.setHorizontalGroup(
            jPanelFreqLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelFreqLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jButtonReadFreq)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jComboBoxFreq, javax.swing.GroupLayout.PREFERRED_SIZE, 90, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabelFreqUnit)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jButtonSendFreq)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanelFreqLayout.setVerticalGroup(
            jPanelFreqLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelFreqLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelFreqLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButtonReadFreq)
                    .addComponent(jComboBoxFreq, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabelFreqUnit)
                    .addComponent(jButtonSendFreq))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanelPower.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createEtchedBorder(), "Мощность:"));

        jButtonReadPower.setText("Считать");
        jButtonReadPower.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonReadPowerActionPerformed(evt);
            }
        });

        jComboBoxPower.setEditable(true);
        jComboBoxPower.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "28000", "50000" }));
        jComboBoxPower.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jComboBoxPowerActionPerformed(evt);
            }
        });

        jLabelPowerUnit.setText("мВт");

        jButtonSendPower.setText("Записать");
        jButtonSendPower.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonSendPowerActionPerformed(evt);
            }
        });

        jButtonSendPower50W.setText("Записать 50Вт");
        jButtonSendPower50W.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonSendPower50WActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanelPowerLayout = new javax.swing.GroupLayout(jPanelPower);
        jPanelPower.setLayout(jPanelPowerLayout);
        jPanelPowerLayout.setHorizontalGroup(
            jPanelPowerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelPowerLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jButtonReadPower)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jComboBoxPower, javax.swing.GroupLayout.PREFERRED_SIZE, 65, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabelPowerUnit)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jButtonSendPower)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jButtonSendPower50W)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanelPowerLayout.setVerticalGroup(
            jPanelPowerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelPowerLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelPowerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButtonReadPower)
                    .addComponent(jComboBoxPower, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabelPowerUnit)
                    .addComponent(jButtonSendPower)
                    .addComponent(jButtonSendPower50W))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanelTransmitDriver.setBorder(javax.swing.BorderFactory.createTitledBorder(javax.swing.BorderFactory.createEtchedBorder(), "Управление передачей:"));

        jButtonPowerON.setText("Включить");
        jButtonPowerON.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonPowerONActionPerformed(evt);
            }
        });

        jButtonPowerOFF.setText("Выключить");
        jButtonPowerOFF.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonPowerOFFActionPerformed(evt);
            }
        });

        jButtonInfo.setText("Запросить состояние");
        jButtonInfo.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonInfoActionPerformed(evt);
            }
        });

        jTextFieldInfo.setEditable(false);

        jCheckBoxScanOn.setSelected(true);
        jCheckBoxScanOn.setText("Периодически запрашивать состояние передатчика");
        jCheckBoxScanOn.setToolTipText("Отправлять команду getpainfo каждые 2 секунды");
        jCheckBoxScanOn.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                jCheckBoxScanOnItemStateChanged(evt);
            }
        });

        javax.swing.GroupLayout jPanelTransmitDriverLayout = new javax.swing.GroupLayout(jPanelTransmitDriver);
        jPanelTransmitDriver.setLayout(jPanelTransmitDriverLayout);
        jPanelTransmitDriverLayout.setHorizontalGroup(
            jPanelTransmitDriverLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelTransmitDriverLayout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(jPanelTransmitDriverLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanelTransmitDriverLayout.createSequentialGroup()
                        .addComponent(jButtonPowerON)
                        .addGap(18, 18, 18)
                        .addComponent(jButtonPowerOFF))
                    .addGroup(jPanelTransmitDriverLayout.createSequentialGroup()
                        .addComponent(jButtonInfo)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jCheckBoxScanOn))
                    .addComponent(jTextFieldInfo, javax.swing.GroupLayout.PREFERRED_SIZE, 370, javax.swing.GroupLayout.PREFERRED_SIZE)))
        );
        jPanelTransmitDriverLayout.setVerticalGroup(
            jPanelTransmitDriverLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelTransmitDriverLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelTransmitDriverLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButtonPowerON)
                    .addComponent(jButtonPowerOFF))
                .addGap(18, 18, 18)
                .addGroup(jPanelTransmitDriverLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButtonInfo)
                    .addComponent(jCheckBoxScanOn))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jTextFieldInfo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanelPortSelector, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(jPanelCommands, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(jPanelTransmitDriver, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(jPanelFreq, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(jPanelPower, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                .addComponent(jPanelPortSelector, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanelCommands, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanelFreq, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanelPower, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanelTransmitDriver, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        jPanelLog.setPreferredSize(new java.awt.Dimension(200, 459));

        new SmartScroller(jScrollPane2);

        jTextPaneLog.setEditable(false);
        jTextPaneLog.setComponentPopupMenu(jPopupMenu1);
        jTextPaneLog.setMinimumSize(new java.awt.Dimension(1, 20));
        jTextPaneLog.setPreferredSize(new java.awt.Dimension(1, 20));
        jScrollPane2.setViewportView(jTextPaneLog);

        javax.swing.GroupLayout jPanelLogLayout = new javax.swing.GroupLayout(jPanelLog);
        jPanelLog.setLayout(jPanelLogLayout);
        jPanelLogLayout.setHorizontalGroup(
            jPanelLogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelLogLayout.createSequentialGroup()
                .addComponent(jScrollPane2)
                .addGap(0, 0, 0))
        );
        jPanelLogLayout.setVerticalGroup(
            jPanelLogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane2)
        );

        jPanel3.setPreferredSize(new java.awt.Dimension(974, 14));

        jLabelRunningString.setText(" Программа для проведения измерений в режиме передачи для оборудования Hytera DS-6210. Автор программы: Федоренко Александр. ");
        jLabelRunningString.setLocation((jPanel3.getSize().width) - 1, jLabelRunningString.getY());

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addGap(1, 1, 1)
                .addComponent(jLabelRunningString)
                .addGap(500, 500, 500))
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jLabelRunningString, javax.swing.GroupLayout.Alignment.TRAILING)
        );

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, 458, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanelLog, javax.swing.GroupLayout.DEFAULT_SIZE, 500, Short.MAX_VALUE)
                .addContainerGap())
            .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(jPanelLog, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, 455, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, 974, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, 490, Short.MAX_VALUE)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        // TODO add your handling code here:
//        javax.swing.JOptionPane.showMessageDialog(jPanel1, "Внимание!", "Производится закрытие программы." , JOptionPane.INFORMATION_MESSAGE);
//        closePort();  
        Thread closePort = new ClosePortThread();
        closePort.start();
        this.setVisible(false);
        try {
            closePort.join();
        } catch (InterruptedException ex) {
            Logger.getLogger(TXtest.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_formWindowClosing

    private void jCheckBoxScanOnItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_jCheckBoxScanOnItemStateChanged
        // TODO add your handling code here:
        isScanOn = getScanState();
    }//GEN-LAST:event_jCheckBoxScanOnItemStateChanged

    private void jButtonInfoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonInfoActionPerformed
        // TODO add your handling code here:
        readPAinfo();
    }//GEN-LAST:event_jButtonInfoActionPerformed

    private void jButtonPowerOFFActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonPowerOFFActionPerformed
        // TODO add your handling code here:
        powerOff();
        waitSomeTime(1);
        if (isAutoCommand) {
            readPAinfo();
        }
    }//GEN-LAST:event_jButtonPowerOFFActionPerformed

    private void jButtonPowerONActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonPowerONActionPerformed
        // TODO add your handling code here:
        powerOn();
        waitSomeTime(1);
        if (isAutoCommand) {
            readPAinfo();
        }
    }//GEN-LAST:event_jButtonPowerONActionPerformed

    private void jButtonSendPower50WActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonSendPower50WActionPerformed
        // TODO add your handling code here:
        command = SETTP;
        sendString("settp 50000\r\n");
        waitSomeTime(1);
        if (isAutoCommand) {
            readPower();
        }
    }//GEN-LAST:event_jButtonSendPower50WActionPerformed

    private void jButtonSendPowerActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonSendPowerActionPerformed
        // TODO add your handling code here:
        setPower();
    }//GEN-LAST:event_jButtonSendPowerActionPerformed

    private void jButtonReadPowerActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonReadPowerActionPerformed
        // TODO add your handling code here:
        readPower();
    }//GEN-LAST:event_jButtonReadPowerActionPerformed

    private void jButtonSendFreqActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonSendFreqActionPerformed
        // TODO add your handling code here:
        setFreq();
        jComboBoxFreq.removeActionListener(jComboBoxFreq);
    }//GEN-LAST:event_jButtonSendFreqActionPerformed

    private void jButtonReadFreqActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonReadFreqActionPerformed
        // TODO add your handling code here:
        readFreq();
    }//GEN-LAST:event_jButtonReadFreqActionPerformed

    private void jButtonReadAllActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonReadAllActionPerformed
        // TODO add your handling code here:
        appendText("Читаем установленные значения.");
        readAll();
    }//GEN-LAST:event_jButtonReadAllActionPerformed

    private void jButtonCliOffActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonCliOffActionPerformed
        // TODO add your handling code here:
        //        powerOff();
        cliOff();
    }//GEN-LAST:event_jButtonCliOffActionPerformed

    private void jButtonCdMgrActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonCdMgrActionPerformed
        // TODO add your handling code here:
        cdmgr();
    }//GEN-LAST:event_jButtonCdMgrActionPerformed

    private void jButtonCliOnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonCliOnActionPerformed
        // TODO add your handling code here:
        cliOn();
    }//GEN-LAST:event_jButtonCliOnActionPerformed

    private void jButtonOpenClosePortActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonOpenClosePortActionPerformed
        // TODO add your handling code here:
        if (serialPort == null || !serialPort.isOpened()) {
            SetPortSettingsThread sps = new SetPortSettingsThread();
            sps.start();
        } else {
            Thread closePort = new ClosePortThread();
            closePort.start();
//            closePort();
        }
    }//GEN-LAST:event_jButtonOpenClosePortActionPerformed

    private void jCheckBoxAutoCommandItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_jCheckBoxAutoCommandItemStateChanged
        // TODO add your handling code here:
        //        appendText(getAutoCommandState() + "");
        isAutoCommand = getAutoCommandState();
        if (!isAutoCommand) {
            isScanOn = getScanState();
            jCheckBoxScanOn.setEnabled(false);
        } else {
            jCheckBoxScanOn.setSelected(isScanOn);
            jCheckBoxScanOn.setEnabled(true);
        }
    }//GEN-LAST:event_jCheckBoxAutoCommandItemStateChanged

    private void jButtonSendCommandActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonSendCommandActionPerformed
        // TODO add your handling code here:
        sendCommand();
    }//GEN-LAST:event_jButtonSendCommandActionPerformed

    private void jComboBoxFreqActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jComboBoxFreqActionPerformed
        // TODO add your handling code here:        
//        if (evt.getActionCommand().equals("comboBoxEdited")) {
//            setFreq();
//        }
    }//GEN-LAST:event_jComboBoxFreqActionPerformed

    private void jComboBoxPowerActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jComboBoxPowerActionPerformed
        // TODO add your handling code here:        
//        if (evt.getActionCommand().equals("comboBoxEdited")) {
//            setPower();
//        }
    }//GEN-LAST:event_jComboBoxPowerActionPerformed

    private void jMenuItem1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItem1ActionPerformed
        // TODO add your handling code here:
        jTextPaneLog.setText("");
    }//GEN-LAST:event_jMenuItem1ActionPerformed

    private void jComboBoxCommandActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jComboBoxCommandActionPerformed
        // TODO add your handling code here:

        if (evt.getActionCommand().equals("comboBoxEdited")) {
            sendCommand();
        }
    }//GEN-LAST:event_jComboBoxCommandActionPerformed

    private void formKeyTyped(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_formKeyTyped
        // TODO add your handling code here:
    }//GEN-LAST:event_formKeyTyped

    private void jPanel1KeyTyped(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_jPanel1KeyTyped
        // TODO add your handling code here:
        appendText("" + evt.getExtendedKeyCode());
    }//GEN-LAST:event_jPanel1KeyTyped

    private void formKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_formKeyPressed
        // TODO add your handling code here:
        appendText("" + evt.getExtendedKeyCode());
    }//GEN-LAST:event_formKeyPressed

    private class Reader implements SerialPortEventListener { //Класс Reader реализует интерфейс SerialPortEventListener

        /*
        Метод serialEvent принимает в качестве параметра переменную типа SerialPortEvent
         */
        @Override
        public void serialEvent(SerialPortEvent spe) {
            appendText("Событие произошло, answerCollector = " + answerCollector);
            if (spe.isRXCHAR() || spe.isRXFLAG()) { //Если установлены флаги RXCHAR и  RXFLAG                
                if (spe.getEventValue() > 0) { //Если число байт во входном буффере больше 0, то
                    //jTextAreaIn.append("В буфере есть " + spe.getEventValue() + " символов");
                    try {
                        String newMessage = serialPort.readString();//читаем из COM-порта строку
                        appendText(newMessage);//и тут же выводим то что прочитали
                        String tempStr;
                        //newMessage может быть null
                        if (newMessage != null) {
                            answerCollector = answerCollector.concat(newMessage); //собираем в одну строку то, что пришло из входного буфера
                        } else {
                            newMessage = "";
                        }
                        switch (command) {
                            case NOP:
                                answerCollector = "";
                                break;
                            case GETTX:
                                if (answerCollector.contains(BAD_COMMAND_INPUT)) {
                                    appendBoldRedText("Команда gettx не выполнена.");
                                    answerCollector = "";
                                    command = NOP;
                                    isOK = false;
                                    isError = true;
                                    break;
                                }
                                if (answerCollector.contains(TX_FREQ) && answerCollector.endsWith(GETTXTP_END)) {
                                    tempStr = answerCollector.substring(answerCollector.indexOf(TX_FREQ) + TX_FREQ.length(), answerCollector.length()).trim();
                                    jComboBoxFreq.setSelectedItem(tempStr);
                                    appendBoldGreenText("Команда gettx успешно выполнена.");
                                    answerCollector = "";
                                    command = NOP;
                                    isOK = true;
                                    isError = false;
                                    break;
                                } else {
                                    break;
                                }

                            case GETTP:
                                if (answerCollector.contains(BAD_COMMAND_INPUT)) {
                                    appendBoldRedText("Команда gettp не выполнена.");
                                    answerCollector = "";
                                    command = NOP;
                                    isOK = false;
                                    isError = true;
                                    break;
                                }
                                if (answerCollector.contains(TX_POWER) && answerCollector.endsWith(GETTXTP_END)) {
                                    tempStr = answerCollector.substring(answerCollector.indexOf(TX_POWER) + TX_POWER.length(), answerCollector.length()).trim();
                                    jComboBoxPower.setSelectedItem(tempStr);
                                    appendBoldGreenText("Команда gettp успешно выполнена.");
                                    answerCollector = "";
                                    command = NOP;
                                    isOK = true;
                                    isError = false;
                                    break;
                                } else {
                                    break;
                                }

                            case GETPAINFO:
                                if (answerCollector.contains(BAD_COMMAND_INPUT)) {
                                    appendBoldRedText("Команда getpainfo не выполнена.");
                                    answerCollector = "";
                                    command = NOP;
                                    isOK = false;
                                    isError = true;
                                    break;
                                }
                                if (answerCollector.contains(PA_INFO) && answerCollector.endsWith(GETPAINFO_END)) {
                                    tempStr = answerCollector.replace(GETPAINFO_END, "");
                                    if (tempStr.contains(PHY_CH_OFF)) {
                                        changeBackgroundColor(powerOffColor);
                                    }
                                    if (tempStr.contains(PHY_CH_ON)) {
                                        changeBackgroundColor(powerOnColor);
                                    }
                                    jTextFieldInfo.setText(tempStr.substring(tempStr.indexOf(PA_INFO_START)));
                                    appendBoldGreenText("Команда getpainfo успешно выполнена.");
                                    answerCollector = "";
                                    command = NOP;
                                    isOK = true;
                                    break;
                                } else {
                                    break;
                                }

                            case CLION:
                                if (answerCollector.contains(CLI_ON_OK)) {
                                    appendBoldGreenText("Команда cli on успешно выполнена.");
                                    answerCollector = "";
                                    command = NOP;
                                    isOK = true;
                                    isError = false;
                                    break;
                                }
                                if (answerCollector.contains(BAD_COMMAND_INPUT)) {
                                    appendBoldRedText("Команда cli on не выполнена.");
                                    answerCollector = "";
                                    command = NOP;
                                    isOK = false;
                                    isError = true;
                                    break;
                                }
                                break;
                            case CLIOFF:
                                if (answerCollector.contains(CLI_OFF_OK)) {
                                    appendBoldGreenText("Команда cli off успешно выполнена.");
                                    answerCollector = "";
                                    command = NOP;
                                    isOK = true;
                                    isError = false;
                                    break;
                                }
//                                if (answerCollector.contains(BAD_COMMAND_INPUT)) {
//                                    appendBoldRedText("Команда cli off не выполнена.");
//                                    answerCollector = "";
//                                    isOK = false;
//                                    isError = true;
//                                    break;
//                                }
                                break;
                            case CDMGR:
                                if (answerCollector.endsWith(CD_MGR_OK)) {
                                    appendBoldGreenText("Команда cd mgr успешно выполнена.");
                                    answerCollector = "";
                                    command = NOP;
                                    isOK = true;
                                    isError = false;
                                    break;
                                }
                                if (answerCollector.contains(BAD_COMMAND_INPUT)) {
                                    appendBoldRedText("Команда cd mgr не выполнена.");
                                    answerCollector = "";
                                    command = NOP;
                                    isOK = false;
                                    isError = true;
                                    break;
                                }
                                break;
                            case SETTX:
                                if (answerCollector.contains(SET_SUCCESS)) {
                                    appendBoldGreenText("Команда settx успешно выполнена.");
                                    answerCollector = "";
                                    command = NOP;
                                    isOK = true;
                                    isError = false;
                                    break;
                                }
                                if (answerCollector.contains(BAD_COMMAND_INPUT) || answerCollector.contains(ERROR_CODE)) {
                                    appendBoldRedText("Команда settx не выполнена.");
                                    answerCollector = "";
                                    command = NOP;
                                    isOK = false;
                                    isError = true;
                                    break;
                                }
                                break;
                            case SETTP:
                                if (answerCollector.contains(SET_SUCCESS)) {
                                    appendBoldGreenText("Команда settp успешно выполнена.");
                                    answerCollector = "";
                                    command = NOP;
                                    isOK = true;
                                    isError = false;
                                    break;
                                }
                                if (answerCollector.contains(BAD_COMMAND_INPUT) || answerCollector.contains(ERROR_CODE)) {
                                    appendBoldRedText("Команда settp не выполнена.");
                                    answerCollector = "";
                                    command = NOP;
                                    isOK = false;
                                    isError = true;
                                    break;
                                }
                                break;
                            case SETPAINFO031:
                                if (answerCollector.contains(SET_PHYINFO_SUCCESS)) {
                                    appendBoldGreenText("Команда setpainfo 0 3 1 успешно выполнена.");
                                    answerCollector = "";
                                    command = NOP;
                                    isOK = true;
                                    isError = false;
                                    break;
                                }
                                if (answerCollector.contains(BAD_COMMAND_INPUT)) {
                                    appendBoldRedText("Команда setpainfo 0 3 1 не выполнена.");
                                    answerCollector = "";
                                    command = NOP;
                                    isOK = false;
                                    isError = true;
                                    break;
                                }
                                break;
                            case SETPAINFO130:
                                if (answerCollector.contains(SET_PHYINFO_SUCCESS)) {
                                    appendBoldGreenText("Команда setpainfo 1 3 0 успешно выполнена.");
                                    answerCollector = "";
                                    command = NOP;
                                    isOK = true;
                                    isError = false;
                                    break;
                                }
                                if (answerCollector.contains(BAD_COMMAND_INPUT)) {
                                    appendBoldRedText("Команда setpainfo 0 3 1 не выполнена.");
                                    answerCollector = "";
                                    command = NOP;
                                    isOK = false;
                                    isError = true;
                                    break;
                                }
                                break;
                        }
                    } catch (SerialPortException ex) {
                        //DialogMessage dialogMessage = new DialogMessage(this, DialogMessage.TYPE_ERROR, "Writing data", "Error occurred while writing data.");
                        javax.swing.JOptionPane.showMessageDialog(null, "Ошибка в Reader: " + ex.getExceptionType(), "Порт: " + ex.getPortName(), JOptionPane.ERROR_MESSAGE);
                        appendBoldRedText("Ошибка в Reader: " + ex.getMessage());
                        printStackTraceElements(ex);
                    } catch (Exception ex) {
                        //DialogMessage dialogMessage = new DialogMessage(this, DialogMessage.TYPE_ERROR, "Writing data", "Error occurred while writing data.");
//                        javax.swing.JOptionPane.showMessageDialog(null, "Ошибка в Reader: " + ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
                        appendBoldRedText("Ошибка в Reader: " + ex.getMessage());
                        printStackTraceElements(ex);
                    }

                }
            }

        }
    }

    void changeBackgroundColor(Color newColor) {
        jPanel1.setBackground(newColor);
        jPanel2.setBackground(newColor);
        jPanel3.setBackground(newColor);
        jPanelPortSelector.setBackground(newColor);
        jPanelCommands.setBackground(newColor);
        jPanelFreq.setBackground(newColor);
        jPanelPower.setBackground(newColor);
        jPanelTransmitDriver.setBackground(newColor);
        jCheckBoxAutoCommand.setBackground(newColor);
        jCheckBoxScanOn.setBackground(newColor);
    }

    public class RunningStringThread extends Thread {

        JComponent label;
        JComponent panel;

        public RunningStringThread(JComponent panel, JComponent label) {
            this.panel = panel;
            this.label = label;
        }

        @Override
        public void run() {
            int x;
            int y;
            int direction = -1;
            int xSize = panel.getSize().width - label.getSize().width;

            while (true) {
                x = label.getX();
                y = label.getY();
                if (x == 0) {
                    direction = 1;
                }
                if (x == xSize) {
                    direction = -1;
                }
                x = x + direction;

                label.setLocation(x, y);
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ex) {
                    Logger.getLogger(TXtest.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    public class RunningStringThread2 extends Thread {

        JComponent label;
        JComponent panel;

        public RunningStringThread2(JComponent panel, JComponent label) {
            this.panel = panel;
            this.label = label;
        }

        @Override
        public void run() {
            int x;
            int y;
            int xPanelSize = panel.getSize().width;
            int xLabelSize = label.getSize().width;
//            label.setLocation((xPanelSize) - 1, label.getY());
//            jLabelRunningString.setLocation((jPanel3.getSize().width) - 1, jLabelRunningString.getY());
//            panel.setVisible(true);
            while (true) {
                x = label.getX();
                y = label.getY();
                if (x == -xLabelSize) {
                    xPanelSize = panel.getSize().width;
                    x = xPanelSize;
                }
                x--;
                label.setLocation(x, y);
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ex) {
                    Logger.getLogger(TXtest.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    void printStackTraceElements(Exception ex) {
        StackTraceElement[] stackTraceElements = ex.getStackTrace();
        for (int i = 0; i < stackTraceElements.length; i++) {
            appendBoldRedText(i + ": " + stackTraceElements[i].toString());
        }
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
//                jTextAreaIn.append (info);                
                if ("Windows Classic".equals(info.getName())) { //Windows Classic, Windows, CDE/Motif, Nimbus, Metal
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
//            jTextAreaIn.append ();
//            jTextAreaIn.append ("System Look and feel: " + javax.swing.UIManager.getSystemLookAndFeelClassName());
//            javax.swing.UIManager.setLookAndFeel( javax.swing.UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | javax.swing.UnsupportedLookAndFeelException ex) {
            javax.swing.JOptionPane.showMessageDialog(null, "Ошибка в main(): " + ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
        //</editor-fold>
        //</editor-fold>

        //</editor-fold>
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new TXtest().setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton jButtonCdMgr;
    private javax.swing.JButton jButtonCliOff;
    private javax.swing.JButton jButtonCliOn;
    private javax.swing.JButton jButtonInfo;
    private javax.swing.JButton jButtonOpenClosePort;
    private javax.swing.JButton jButtonPowerOFF;
    private javax.swing.JButton jButtonPowerON;
    private javax.swing.JButton jButtonReadAll;
    private javax.swing.JButton jButtonReadFreq;
    private javax.swing.JButton jButtonReadPower;
    private javax.swing.JButton jButtonSendCommand;
    private javax.swing.JButton jButtonSendFreq;
    private javax.swing.JButton jButtonSendPower;
    private javax.swing.JButton jButtonSendPower50W;
    private javax.swing.JCheckBox jCheckBoxAutoCommand;
    private javax.swing.JCheckBox jCheckBoxScanOn;
    private javax.swing.JComboBox<String> jComboBoxCommand;
    private javax.swing.JComboBox<String> jComboBoxFreq;
    private javax.swing.JComboBox<String> jComboBoxPortName;
    private javax.swing.JComboBox<String> jComboBoxPower;
    private javax.swing.JLabel jLabelFreqUnit;
    private javax.swing.JLabel jLabelPowerUnit;
    private javax.swing.JLabel jLabelRunningString;
    private javax.swing.JMenuItem jMenuItem1;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanelCommands;
    private javax.swing.JPanel jPanelFreq;
    private javax.swing.JPanel jPanelLog;
    private javax.swing.JPanel jPanelPortSelector;
    private javax.swing.JPanel jPanelPower;
    private javax.swing.JPanel jPanelTransmitDriver;
    private javax.swing.JPopupMenu jPopupMenu1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JTextField jTextFieldInfo;
    private javax.swing.JTextPane jTextPaneLog;
    // End of variables declaration//GEN-END:variables
}
