package com.fortress.poc;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.StandardProtocolFamily;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.channels.ServerSocketChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;

public final class CameraViewerApp {
    private static final int DEFAULT_EVENT_PORT = 7777;
    private static final int DEFAULT_COMMAND_PORT = 7778;
    private static final String DEFAULT_CAMERA_HOST = "192.168.1.111";
    private static final String EVENT_HEADER = "MADEYE_EVENT";
    private static final int EVENT_HEADER_SIZE = 32;
    private static final int ACCEPT_TIMEOUT_MILLIS = 1000;
    private static final int IDLE_TIMEOUT_MILLIS = 2000;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ExecutorService eventExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService commandExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean appRunning = new AtomicBoolean(true);
    private final CommandClient commandClient = new CommandClient();

    private final JFrame frame = new JFrame("Fortress Camera Controller");
    private final JPanel imageSection = new JPanel(new BorderLayout());
    private final JPanel imagePanel = new JPanel(new BorderLayout());
    private final JLabel imageLabel = new JLabel("Waiting for camera events", SwingConstants.CENTER);
    private final JLabel statusLabel = new JLabel("Ready", SwingConstants.CENTER);
    private final JLabel detailLabel = new JLabel("", SwingConstants.CENTER);
    private final JLabel uiStatusLabel = new JLabel("Ready");

    private final JTextField cameraHostField = new JTextField(DEFAULT_CAMERA_HOST, 16);
    private final JTextField commandPortField = new JTextField(String.valueOf(DEFAULT_COMMAND_PORT), 6);
    private final JTextField eventPortField = new JTextField(String.valueOf(DEFAULT_EVENT_PORT), 6);
    private final JLabel macIpLabel = new JLabel();
    private final JLabel listenerStatusLabel = new JLabel();

    private final JLabel firmwareVersionValue = new JLabel("-");
    private final JLabel faceVersionValue = new JLabel("-");
    private final JLabel osVersionValue = new JLabel("-");

    private final JTextField videoWidthField = new JTextField(8);
    private final JTextField videoHeightField = new JTextField(8);
    private final JComboBox<String> videoRotationCombo = new JComboBox<>(new String[]{"-90", "0", "90", "180"});
    private final JComboBox<String> videoCameraCombo = new JComboBox<>(new String[]{"RGB", "NIR"});
    private final JComboBox<String> videoBalanceCombo = new JComboBox<>(new String[]{"Off", "On"});

    private final JTextField faceThresholdField = new JTextField(8);
    private final JTextField faceAttemptsField = new JTextField(8);
    private final JComboBox<String> faceLivenessCombo = new JComboBox<>(new String[]{"Off", "On"});
    private final JTextField faceLivenessThresholdField = new JTextField(8);
    private final JComboBox<String> faceMinimumCombo = new JComboBox<>(new String[]{"Off", "On"});
    private final JTextField faceSizeField = new JTextField(8);

    private final JTextField networkAddressField = new JTextField(12);
    private final JTextField networkGatewayField = new JTextField(12);
    private final JTextField networkMaskField = new JTextField(12);

    private final JTextField commHostField = new JTextField(14);
    private final JTextField commEventPortField = new JTextField(6);
    private final JTextField commCommandPortField = new JTextField(6);

    private final JTextField userIdField = new JTextField(14);
    private final JLabel userFaceFileLabel = new JLabel("No face file selected");
    private final JLabel firmwareFileLabel = new JLabel("No firmware file selected");
    private final JLabel firmwareMd5Label = new JLabel("No md5 file selected");
    private final JLabel databaseFileLabel = new JLabel("No database file selected");
    private final JLabel databaseMd5Label = new JLabel("No md5 file selected");
    private final JLabel eventCountLabel = new JLabel("0");
    private final JLabel lastEventSourceLabel = new JLabel("-");

    private volatile ServerSocket serverSocket;
    private volatile boolean listenerRunning;
    private volatile long lastEventAtMillis;
    private volatile boolean idleShown;
    private volatile BufferedImage lastImage;
    private volatile int eventCount;
    private volatile Path userFacePath;
    private volatile Path firmwarePath;
    private volatile Path firmwareMd5Path;
    private volatile Path databasePath;
    private volatile Path databaseMd5Path;

    public static void main(String[] args) {
        System.setProperty("java.net.preferIPv4Stack", "true");
        int eventPort = DEFAULT_EVENT_PORT;
        if (args.length > 0) {
            eventPort = Integer.parseInt(args[0]);
        }
        int finalEventPort = eventPort;
        SwingUtilities.invokeLater(() -> new CameraViewerApp().start(finalEventPort));
    }

    private void start(int eventPort) {
        configureLookAndFeel();
        configureUi(eventPort);
        restartEventListener(eventPort);
        AppLog.log("Desktop controller started log=" + AppLog.path());
        setUiStatus("Desktop controller started");
    }

    private void configureLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
    }

    private void configureUi(int eventPort) {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(1450, 840));

        imagePanel.setBackground(new Color(22, 26, 30));
        imageLabel.setForeground(Color.WHITE);
        imageLabel.setFont(new Font("SansSerif", Font.BOLD, 28));
        imagePanel.add(imageLabel, BorderLayout.CENTER);
        imagePanel.setBorder(createStatusBorder(EventType.IDLE));
        imageSection.add(imagePanel, BorderLayout.CENTER);
        imagePanel.setPreferredSize(new Dimension(480, 640));
        imagePanel.setMinimumSize(new Dimension(480, 640));
        imagePanel.setMaximumSize(new Dimension(480, 640));
        imageSection.setPreferredSize(new Dimension(480, 640));
        imageSection.setMinimumSize(new Dimension(480, 640));
        imageSection.setMaximumSize(new Dimension(480, 640));

        statusLabel.setFont(new Font("SansSerif", Font.BOLD, 28));
        detailLabel.setFont(new Font("SansSerif", Font.PLAIN, 16));

        JPanel viewerContainer = new JPanel(new BorderLayout(0, 12));
        viewerContainer.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 8));
        viewerContainer.add(imageSection, BorderLayout.NORTH);
        viewerContainer.add(new JPanel(), BorderLayout.CENTER);
        viewerContainer.add(createViewerFooter(), BorderLayout.SOUTH);
        viewerContainer.setPreferredSize(new Dimension(520, 840));

        eventPortField.setText(String.valueOf(eventPort));
        macIpLabel.setText(resolveLocalIpSummary());
        listenerStatusLabel.setText("Listener not started");

        JPanel controls = new JPanel(new BorderLayout(0, 12));
        controls.setBorder(BorderFactory.createEmptyBorder(16, 8, 16, 16));
        controls.add(createConnectionPanel(), BorderLayout.NORTH);
        controls.add(createActionGrid(), BorderLayout.CENTER);
        controls.add(uiStatusLabel, BorderLayout.SOUTH);
        controls.setPreferredSize(new Dimension(1080, 840));

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, viewerContainer, controls);
        splitPane.setResizeWeight(0.34);
        splitPane.setDividerLocation(520);

        frame.setContentPane(splitPane);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                appRunning.set(false);
                stopEventListener();
                eventExecutor.shutdownNow();
                commandExecutor.shutdownNow();
            }
        });

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        updateUi(new CameraState(EventType.IDLE, "Ready", "Waiting for camera events", null));
    }

    private JPanel createViewerFooter() {
        JPanel footer = new JPanel();
        footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
        footer.add(statusLabel);
        footer.add(Box.createVerticalStrut(6));
        footer.add(detailLabel);
        return footer;
    }

    private JPanel createConnectionPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Connection"),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));

        JButton restartListenerButton = new JButton("Restart Listener");
        restartListenerButton.addActionListener(event -> {
            try {
                int port = Integer.parseInt(eventPortField.getText().trim());
                restartEventListener(port);
                setUiStatus("Restarted event listener on port " + port);
            } catch (NumberFormatException e) {
                showError("Event port must be a number");
            }
        });

        int row = 0;
        addRow(panel, row++, "Camera Host", cameraHostField, null);
        addRow(panel, row++, "Command Port", commandPortField, null);
        addRow(panel, row++, "Listen Port", eventPortField, restartListenerButton);
        addRow(panel, row++, "PC", macIpLabel, null);
        addRow(panel, row, "Listener", listenerStatusLabel, null);
        return panel;
    }

    private JPanel createVersionPanel() {
        JPanel panel = createFormPanel("Device Control");

        int row = 0;
        addRow(panel, row++, "Firmware", firmwareVersionValue, null);
        addRow(panel, row++, "Face", faceVersionValue, null);
        addRow(panel, row, "OS", osVersionValue, null);
        return panel;
    }

    private JPanel createVideoTab() {
        JPanel panel = createFormPanel("Video Settings");
        int row = 0;
        addRow(panel, row++, "Frame Width", videoWidthField, null);
        addRow(panel, row++, "Frame Height", videoHeightField, null);
        addRow(panel, row++, "Rotation", videoRotationCombo, null);
        addRow(panel, row++, "Camera", videoCameraCombo, null);
        addRow(panel, row, "Balance", videoBalanceCombo, null);
        return panel;
    }

    private JPanel createFaceTab() {
        JPanel panel = createFormPanel("Face Settings");
        int row = 0;
        addRow(panel, row++, "Threshold", faceThresholdField, null);
        addRow(panel, row++, "Attempts", faceAttemptsField, null);
        addRow(panel, row++, "Liveness", faceLivenessCombo, null);
        addRow(panel, row++, "Liveness Threshold", faceLivenessThresholdField, null);
        addRow(panel, row++, "Face Minimum", faceMinimumCombo, null);
        addRow(panel, row, "Face Size", faceSizeField, null);
        return panel;
    }

    private JPanel createNetworkTab() {
        JPanel panel = createFormPanel("Network Settings");
        int row = 0;
        addRow(panel, row++, "Address", networkAddressField, null);
        addRow(panel, row++, "Gateway", networkGatewayField, null);
        addRow(panel, row, "Mask", networkMaskField, null);
        return panel;
    }

    private JPanel createCommTab() {
        JPanel panel = createFormPanel("Comm Settings");
        commHostField.setText(preferredListenerIp());
        commEventPortField.setText(eventPortField.getText());
        commCommandPortField.setText(commandPortField.getText());

        int row = 0;
        addRow(panel, row++, "Event Host", commHostField, null);
        addRow(panel, row++, "Event Port", commEventPortField, null);
        addRow(panel, row++, "Command Port", commCommandPortField, null);
        return panel;
    }

    private JPanel createAddUserPanel() {
        JPanel panel = createFormPanel("Add User");
        JButton chooseFaceButton = new JButton("Choose Face File");
        chooseFaceButton.addActionListener(event -> {
            Path file = chooseFile("Choose Face File", new FileNameExtensionFilter("Binary files", "bin"));
            if (file != null) {
                userFacePath = file;
                userFaceFileLabel.setText(file.toString());
            }
        });

        int row = 0;
        addRow(panel, row++, "User ID", userIdField, null);
        addRow(panel, row, "Face File", userFaceFileLabel, chooseFaceButton);
        return panel;
    }

    private JPanel createDeleteUserPanel() {
        JPanel panel = createFormPanel("Delete User");
        addRow(panel, 0, "User ID", userIdField, null);
        return panel;
    }

    private JPanel createListUsersPanel() {
        JPanel panel = createFormPanel("List All Users");
        javax.swing.JTextArea userListArea = new javax.swing.JTextArea(12, 24);
        userListArea.setEditable(false);
        userListArea.setLineWrap(true);
        userListArea.setWrapStyleWord(true);

        addRow(panel, 0, "Users", new JLabel("User list"), null);
        GridBagConstraints areaConstraints = baseConstraints();
        areaConstraints.gridx = 0;
        areaConstraints.gridy = 1;
        areaConstraints.gridwidth = 3;
        areaConstraints.fill = GridBagConstraints.BOTH;
        areaConstraints.weightx = 1.0;
        areaConstraints.weighty = 1.0;
        panel.add(new JScrollPane(userListArea), areaConstraints);
        return panel;
    }

    private JPanel createDatabaseGetPanel() {
        JPanel panel = createFormPanel("Database Get");
        JLabel help = new JLabel("Download the database and save the .sql and .md5 files.");
        JButton downloadButton = new JButton("Download Database");
        downloadButton.addActionListener(event -> runCommand("Download database", () -> {
            CommandClient.DatabaseDownload result = commandClient.databaseGet(currentHost(), currentCommandPort());
            Path sqlPath = chooseSaveFile("Save Database SQL", "camera_database.sql", new FileNameExtensionFilter("SQL files", "sql"));
            if (sqlPath == null) {
                return "Database download canceled";
            }
            Files.write(sqlPath, result.database());
            Path md5Path = replaceExtension(sqlPath, ".md5");
            Files.writeString(md5Path, result.md5());
            return "Database saved to " + sqlPath + " and " + md5Path;
        }));

        addRow(panel, 0, "Action", help, downloadButton);
        return panel;
    }

    private JPanel createDatabaseSetPanel() {
        JPanel panel = createFormPanel("Database Set");
        JButton chooseDatabaseButton = new JButton("Choose Database");
        chooseDatabaseButton.addActionListener(event -> {
            Path file = chooseFile("Choose Database SQL", new FileNameExtensionFilter("SQL files", "sql"));
            if (file != null) {
                databasePath = file;
                databaseFileLabel.setText(file.toString());
            }
        });

        JButton chooseDatabaseMd5Button = new JButton("Choose Database MD5");
        chooseDatabaseMd5Button.addActionListener(event -> {
            Path file = chooseFile("Choose Database MD5", new FileNameExtensionFilter("MD5 files", "md5"));
            if (file != null) {
                databaseMd5Path = file;
                databaseMd5Label.setText(file.toString());
            }
        });

        JButton uploadDatabaseButton = new JButton("Upload Database");
        uploadDatabaseButton.addActionListener(event -> runCommand("Upload database", () -> {
            Path database = requirePath(databasePath, "Select a database sql file first");
            String md5 = readMd5(requirePath(databaseMd5Path, "Select a database md5 file first"));
            byte[] databaseBytes = Files.readAllBytes(database);
            verifyMd5(databaseBytes, md5, "Database");
            commandClient.databaseSet(currentHost(), currentCommandPort(), databaseBytes, md5);
            return "Database upload command sent";
        }));

        int row = 0;
        addRow(panel, row++, "Database File", databaseFileLabel, chooseDatabaseButton);
        addRow(panel, row++, "Database MD5", databaseMd5Label, chooseDatabaseMd5Button);
        addRow(panel, row, "Upload", uploadDatabaseButton, null);
        return panel;
    }

    private JPanel createFirmwarePanel() {
        JPanel panel = createFormPanel("Firmware Update");
        JButton chooseFirmwareButton = new JButton("Choose Firmware");
        chooseFirmwareButton.addActionListener(event -> {
            Path file = chooseFile("Choose Firmware Zip", new FileNameExtensionFilter("Zip files", "zip"));
            if (file != null) {
                firmwarePath = file;
                firmwareFileLabel.setText(file.toString());
            }
        });

        JButton chooseFirmwareMd5Button = new JButton("Choose Firmware MD5");
        chooseFirmwareMd5Button.addActionListener(event -> {
            Path file = chooseFile("Choose Firmware MD5", new FileNameExtensionFilter("MD5 files", "md5"));
            if (file != null) {
                firmwareMd5Path = file;
                firmwareMd5Label.setText(file.toString());
            }
        });

        JButton uploadFirmwareButton = new JButton("Upload Firmware");
        uploadFirmwareButton.addActionListener(event -> runCommand("Upload firmware", () -> {
            Path firmware = requirePath(firmwarePath, "Select a firmware zip first");
            String md5 = readMd5(requirePath(firmwareMd5Path, "Select a firmware md5 file first"));
            byte[] firmwareData = Files.readAllBytes(firmware);
            verifyMd5(firmwareData, md5, "Firmware");
            commandClient.firmwareUpdate(currentHost(), currentCommandPort(), firmwareData, md5);
            return "Firmware upload command sent";
        }));

        int row = 0;
        addRow(panel, row++, "Firmware File", firmwareFileLabel, chooseFirmwareButton);
        addRow(panel, row++, "Firmware MD5", firmwareMd5Label, chooseFirmwareMd5Button);
        addRow(panel, row, "Upload", uploadFirmwareButton, null);
        return panel;
    }

    private JPanel createActionGrid() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Commands"),
                BorderFactory.createEmptyBorder(20, 20, 20, 20)
        ));

        JButton versionButton = actionButton("Version", this::openVersionDialog);
        JButton videoButton = actionButton("Video", this::openVideoDialog);
        JButton faceButton = actionButton("Face", this::openFaceDialog);
        JButton networkButton = actionButton("Network", this::openNetworkDialog);
        JButton commButton = actionButton("Communication", this::openCommDialog);

        JButton addUserButton = actionButton("Add User", this::openAddUserDialog);
        JButton deleteUserButton = actionButton("Delete User", this::openDeleteUserDialog);
        JButton deleteAllUsersButton = actionButton("Delete All Users", this::confirmDeleteAllUsers);
        JButton listUsersButton = actionButton("List All Users", this::openListUsersDialog);

        JButton databaseGetButton = actionButton("Database Get", this::openDatabaseGetDialog);
        JButton databaseSetButton = actionButton("Database Set", this::openDatabaseSetDialog);
        JButton firmwareButton = actionButton("Firmware Update", this::openFirmwareDialog);

        JButton cameraOnButton = actionButton("Camera On", () -> runCommand("Camera on", () -> {
            commandClient.cameraOn(currentHost(), currentCommandPort());
            return "Camera on command succeeded";
        }));
        JButton cameraOffButton = actionButton("Camera Off", () -> runCommand("Camera off", () -> {
            commandClient.cameraOff(currentHost(), currentCommandPort());
            return "Camera off command succeeded";
        }));
        panel.add(createButtonColumn(versionButton, videoButton, faceButton, networkButton, commButton));
        panel.add(Box.createHorizontalStrut(20));
        panel.add(createButtonColumn(addUserButton, deleteUserButton, deleteAllUsersButton, listUsersButton));
        panel.add(Box.createHorizontalStrut(20));
        panel.add(createButtonColumn(databaseGetButton, databaseSetButton));
        panel.add(Box.createHorizontalStrut(20));
        panel.add(createButtonColumn(firmwareButton));
        panel.add(Box.createHorizontalStrut(20));
        panel.add(createButtonColumn(cameraOnButton, cameraOffButton));
        panel.add(Box.createHorizontalGlue());
        return panel;
    }

    private JButton actionButton(String label, Runnable action) {
        JButton button = new JButton(label);
        button.setFont(new Font("SansSerif", Font.PLAIN, 14));
        button.setFocusPainted(false);
        button.setHorizontalAlignment(SwingConstants.CENTER);
        button.setPreferredSize(new Dimension(150, 54));
        button.setMinimumSize(new Dimension(150, 54));
        button.setMaximumSize(new Dimension(150, 54));
        button.addActionListener(event -> action.run());
        return button;
    }

    private JPanel createButtonColumn(JButton... buttons) {
        JPanel column = new JPanel();
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.setOpaque(false);
        for (int i = 0; i < buttons.length; i++) {
            buttons[i].setAlignmentX(Component.LEFT_ALIGNMENT);
            column.add(buttons[i]);
            if (i < buttons.length - 1) {
                column.add(Box.createVerticalStrut(12));
            }
        }
        return column;
    }

    private void openModalDialog(String title, JPanel panel, int width, int height) {
        JDialog dialog = new JDialog(frame, title, true);
        JPanel container = new JPanel(new BorderLayout(0, 12));
        container.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        container.add(new JScrollPane(panel), BorderLayout.CENTER);

        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(event -> dialog.dispose());
        JPanel footer = new JPanel();
        footer.add(closeButton);
        container.add(footer, BorderLayout.SOUTH);

        dialog.setContentPane(container);
        dialog.setSize(width, height);
        dialog.setMinimumSize(new Dimension(width, height));
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    private void openVersionDialog() {
        JPanel panel = createVersionPanel();
        JDialog dialog = createDialog("Version", panel, 520, 260);
        JButton exitButton = footerButton("Exit", dialog::dispose);
        setDialogFooter(dialog, buttonRow(exitButton));
        runCommandIntoDialog("Get versions", () -> {
            CommandClient.VersionInfo info = commandClient.versionGet(currentHost(), currentCommandPort());
            SwingUtilities.invokeLater(() -> {
                firmwareVersionValue.setText(info.firmware());
                faceVersionValue.setText(info.face());
                osVersionValue.setText(info.os());
            });
            return "Firmware " + info.firmware() + ", Face " + info.face() + ", OS " + info.os();
        }, false, null);
        dialog.setVisible(true);
    }

    private void openVideoDialog() {
        JPanel panel = createVideoTab();
        JDialog dialog = createDialog("Video", panel, 560, 420);
        JButton getButton = footerButton("Get", () -> runCommandIntoDialog("Get video settings", () -> {
            CommandClient.VideoSettings settings = commandClient.videoGet(currentHost(), currentCommandPort());
            SwingUtilities.invokeLater(() -> {
                videoWidthField.setText(String.valueOf(settings.width()));
                videoHeightField.setText(String.valueOf(settings.height()));
                videoRotationCombo.setSelectedIndex(settings.rotation());
                videoCameraCombo.setSelectedIndex(settings.camera());
                videoBalanceCombo.setSelectedIndex(settings.balance());
            });
            return "Video settings loaded";
        }, false, null));
        JButton setButton = footerButton("Set", () -> runCommandIntoDialog("Set video settings", () -> {
            CommandClient.VideoSettings settings = new CommandClient.VideoSettings(
                    parseInteger(videoWidthField.getText(), "video width"),
                    parseInteger(videoHeightField.getText(), "video height"),
                    videoRotationCombo.getSelectedIndex(),
                    videoCameraCombo.getSelectedIndex(),
                    videoBalanceCombo.getSelectedIndex()
            );
            commandClient.videoSet(currentHost(), currentCommandPort(), settings);
            return "Video settings updated";
        }, true, "Video settings updated"));
        JButton exitButton = footerButton("Exit", dialog::dispose);
        setDialogFooter(dialog, buttonRow(getButton, setButton, exitButton));
        dialog.setVisible(true);
    }

    private void openFaceDialog() {
        JPanel panel = createFaceTab();
        JDialog dialog = createDialog("Face", panel, 580, 460);
        JButton getButton = footerButton("Get", () -> runCommandIntoDialog("Get face settings", () -> {
            CommandClient.FaceSettings settings = commandClient.faceGet(currentHost(), currentCommandPort());
            SwingUtilities.invokeLater(() -> {
                faceThresholdField.setText(String.valueOf(settings.threshold()));
                faceAttemptsField.setText(String.valueOf(settings.attempts()));
                faceLivenessCombo.setSelectedIndex(settings.liveness());
                faceLivenessThresholdField.setText(String.valueOf(settings.livenessThreshold()));
                faceMinimumCombo.setSelectedIndex(settings.faceMinimum());
                faceSizeField.setText(String.valueOf(settings.faceSize()));
            });
            return "Face settings loaded";
        }, false, null));
        JButton setButton = footerButton("Set", () -> runCommandIntoDialog("Set face settings", () -> {
            CommandClient.FaceSettings settings = new CommandClient.FaceSettings(
                    parseFloat(faceThresholdField.getText(), "threshold"),
                    parseInteger(faceAttemptsField.getText(), "attempts"),
                    faceLivenessCombo.getSelectedIndex(),
                    parseFloat(faceLivenessThresholdField.getText(), "liveness threshold"),
                    faceMinimumCombo.getSelectedIndex(),
                    parseInteger(faceSizeField.getText(), "face size")
            );
            commandClient.faceSet(currentHost(), currentCommandPort(), settings);
            return "Face settings updated";
        }, true, "Face settings updated"));
        JButton exitButton = footerButton("Exit", dialog::dispose);
        setDialogFooter(dialog, buttonRow(getButton, setButton, exitButton));
        dialog.setVisible(true);
    }

    private void openNetworkDialog() {
        JPanel panel = createNetworkTab();
        JDialog dialog = createDialog("Network", panel, 560, 320);
        JButton getButton = footerButton("Get", () -> runCommandIntoDialog("Get network settings", () -> {
            CommandClient.NetworkSettings settings = commandClient.networkGet(currentHost(), currentCommandPort());
            SwingUtilities.invokeLater(() -> {
                networkAddressField.setText(settings.address());
                networkGatewayField.setText(settings.gateway());
                networkMaskField.setText(settings.mask());
            });
            return "Network settings loaded";
        }, false, null));
        JButton setButton = footerButton("Set", () -> runCommandIntoDialog("Set network settings", () -> {
            commandClient.networkSet(
                    currentHost(),
                    currentCommandPort(),
                    new CommandClient.NetworkSettings(
                            networkAddressField.getText().trim(),
                            networkGatewayField.getText().trim(),
                            networkMaskField.getText().trim()
                    )
            );
            return "Network settings updated";
        }, true, "Network settings updated"));
        JButton exitButton = footerButton("Exit", dialog::dispose);
        setDialogFooter(dialog, buttonRow(getButton, setButton, exitButton));
        dialog.setVisible(true);
    }

    private void openCommDialog() {
        JPanel panel = createCommTab();
        JDialog dialog = createDialog("Communication", panel, 580, 320);
        JButton getButton = footerButton("Get", () -> runCommandIntoDialog("Get comm settings", () -> {
            CommandClient.CommSettings settings = commandClient.commGet(currentHost(), currentCommandPort());
            SwingUtilities.invokeLater(() -> {
                commHostField.setText(settings.host());
                commEventPortField.setText(String.valueOf(settings.eventPort()));
                commCommandPortField.setText(String.valueOf(settings.commandPort()));
            });
            return "Comm settings loaded";
        }, false, null));
        JButton setButton = footerButton("Set", () -> runCommandIntoDialog("Set comm settings", () -> {
            CommandClient.CommSettings settings = new CommandClient.CommSettings(
                    commHostField.getText().trim(),
                    parseInteger(commEventPortField.getText(), "event port"),
                    parseInteger(commCommandPortField.getText(), "command port")
            );
            commandClient.commSet(currentHost(), currentCommandPort(), settings);
            return "Comm settings updated";
        }, true, "Communication settings updated"));
        JButton usePcButton = footerButton("Use This PC", () -> {
            commHostField.setText(preferredListenerIp());
            commEventPortField.setText(eventPortField.getText().trim());
        });
        JButton exitButton = footerButton("Exit", dialog::dispose);
        setDialogFooter(dialog, buttonRow(getButton, setButton, usePcButton, exitButton));
        dialog.setVisible(true);
    }

    private void openAddUserDialog() {
        JPanel panel = createAddUserPanel();
        JDialog dialog = createDialog("Add User", panel, 720, 260);
        JButton okButton = footerButton("OK", () -> runCommandIntoDialog("Add user", () -> {
            if (userFacePath == null) {
                throw new IllegalArgumentException("Select a face file first");
            }
            String userId = userIdField.getText().trim();
            if (userId.isEmpty()) {
                throw new IllegalArgumentException("User ID is required");
            }
            byte[] faceData = Files.readAllBytes(userFacePath);
            commandClient.userAdd(currentHost(), currentCommandPort(), userId, faceData);
            return "User added: " + userId;
        }, true, "User add succeeded"));
        JButton exitButton = footerButton("Exit", dialog::dispose);
        setDialogFooter(dialog, buttonRow(okButton, exitButton));
        dialog.setVisible(true);
    }

    private void openDeleteUserDialog() {
        JPanel panel = createDeleteUserPanel();
        JDialog dialog = createDialog("Delete User", panel, 460, 220);
        JButton okButton = footerButton("OK", () -> runCommandIntoDialog("Delete user", () -> {
            String userId = userIdField.getText().trim();
            if (userId.isEmpty()) {
                throw new IllegalArgumentException("User ID is required");
            }
            commandClient.userDelete(currentHost(), currentCommandPort(), userId);
            return "User deleted: " + userId;
        }, true, "User delete succeeded"));
        JButton exitButton = footerButton("Exit", dialog::dispose);
        setDialogFooter(dialog, buttonRow(okButton, exitButton));
        dialog.setVisible(true);
    }

    private void openListUsersDialog() {
        JPanel panel = createListUsersPanel();
        JTextArea userListArea = findFirstTextArea(panel);
        JDialog dialog = createDialog("List All Users", panel, 560, 440);
        JButton refreshButton = footerButton("Refresh", () -> runCommandIntoDialog("List users", () -> {
            CommandClient.UserListResult result = commandClient.userList(currentHost(), currentCommandPort());
            SwingUtilities.invokeLater(() -> userListArea.setText(formatUserList(result)));
            return "Loaded " + result.count() + " users";
        }, false, null));
        JButton exitButton = footerButton("Exit", dialog::dispose);
        setDialogFooter(dialog, buttonRow(refreshButton, exitButton));
        runCommandIntoDialog("List users", () -> {
            CommandClient.UserListResult result = commandClient.userList(currentHost(), currentCommandPort());
            SwingUtilities.invokeLater(() -> userListArea.setText(formatUserList(result)));
            return "Loaded " + result.count() + " users";
        }, false, null);
        dialog.setVisible(true);
    }

    private void openDatabaseGetDialog() {
        JPanel panel = createDatabaseGetPanel();
        JDialog dialog = createDialog("Database Get", panel, 560, 220);
        JButton getButton = footerButton("Get", () -> runCommandIntoDialog("Download database", () -> {
            CommandClient.DatabaseDownload result = commandClient.databaseGet(currentHost(), currentCommandPort());
            Path sqlPath = chooseSaveFile("Save Database SQL", "camera_database.sql", new FileNameExtensionFilter("SQL files", "sql"));
            if (sqlPath == null) {
                return "Database download canceled";
            }
            Files.write(sqlPath, result.database());
            Path md5Path = replaceExtension(sqlPath, ".md5");
            Files.writeString(md5Path, result.md5());
            return "Database saved to " + sqlPath + " and " + md5Path;
        }, true, "Database download complete"));
        JButton exitButton = footerButton("Exit", dialog::dispose);
        setDialogFooter(dialog, buttonRow(getButton, exitButton));
        dialog.setVisible(true);
    }

    private void openDatabaseSetDialog() {
        JPanel panel = createDatabaseSetPanel();
        JDialog dialog = createDialog("Database Set", panel, 720, 320);
        JButton setButton = footerButton("Set", () -> runCommandIntoDialog("Upload database", () -> {
            Path database = requirePath(databasePath, "Select a database sql file first");
            String md5 = readMd5(requirePath(databaseMd5Path, "Select a database md5 file first"));
            byte[] databaseBytes = Files.readAllBytes(database);
            verifyMd5(databaseBytes, md5, "Database");
            commandClient.databaseSet(currentHost(), currentCommandPort(), databaseBytes, md5);
            return "Database upload command sent";
        }, true, "Database upload command sent"));
        JButton exitButton = footerButton("Exit", dialog::dispose);
        setDialogFooter(dialog, buttonRow(setButton, exitButton));
        dialog.setVisible(true);
    }

    private void openFirmwareDialog() {
        JPanel panel = createFirmwarePanel();
        JDialog dialog = createDialog("Firmware Update", panel, 720, 320);
        JButton setButton = footerButton("Set", () -> runCommandIntoDialog("Upload firmware", () -> {
            Path firmware = requirePath(firmwarePath, "Select a firmware zip first");
            String md5 = readMd5(requirePath(firmwareMd5Path, "Select a firmware md5 file first"));
            byte[] firmwareData = Files.readAllBytes(firmware);
            verifyMd5(firmwareData, md5, "Firmware");
            commandClient.firmwareUpdate(currentHost(), currentCommandPort(), firmwareData, md5);
            return "Firmware upload command sent";
        }, true, "Firmware upload command sent"));
        JButton exitButton = footerButton("Exit", dialog::dispose);
        setDialogFooter(dialog, buttonRow(setButton, exitButton));
        dialog.setVisible(true);
    }

    private JDialog createDialog(String title, JPanel panel, int width, int height) {
        JDialog dialog = new JDialog(frame, title, true);
        JPanel container = new JPanel(new BorderLayout(0, 12));
        container.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        container.add(new JScrollPane(panel), BorderLayout.CENTER);
        dialog.setContentPane(container);
        dialog.setSize(width, height);
        dialog.setMinimumSize(new Dimension(width, height));
        dialog.setLocationRelativeTo(frame);
        return dialog;
    }

    private void setDialogFooter(JDialog dialog, JPanel footer) {
        ((JPanel) dialog.getContentPane()).add(footer, BorderLayout.SOUTH);
    }

    private JButton footerButton(String label, Runnable action) {
        JButton button = new JButton(label);
        button.setPreferredSize(new Dimension(120, 42));
        button.addActionListener(event -> action.run());
        return button;
    }

    private JTextArea findFirstTextArea(Component component) {
        if (component instanceof JTextArea area) {
            return area;
        }
        if (component instanceof JScrollPane scrollPane && scrollPane.getViewport().getView() instanceof JTextArea area) {
            return area;
        }
        if (component instanceof java.awt.Container container) {
            for (Component child : container.getComponents()) {
                JTextArea found = findFirstTextArea(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private void confirmDeleteAllUsers() {
        int choice = JOptionPane.showConfirmDialog(frame, "Delete all users on the camera?", "Confirm", JOptionPane.OK_CANCEL_OPTION);
        if (choice == JOptionPane.OK_OPTION) {
            runCommand("Delete all users", () -> {
                commandClient.userDeleteAll(currentHost(), currentCommandPort());
                return "All users deleted";
            });
        }
    }

    private JPanel createUsersTab() {
        return createAddUserPanel();
    }

    private JPanel createDataTab() {
        return createFirmwarePanel();
    }

    private JPanel createUsersTabLegacy() {
        JPanel panel = createFormPanel("User Management");
        JButton deleteAllButton = new JButton("Delete All");
        deleteAllButton.addActionListener(event -> {
            int choice = JOptionPane.showConfirmDialog(frame, "Delete all users on the camera?", "Confirm", JOptionPane.OK_CANCEL_OPTION);
            if (choice == JOptionPane.OK_OPTION) {
                runCommand("Delete all users", () -> {
                    commandClient.userDeleteAll(currentHost(), currentCommandPort());
                    return "All users deleted";
                });
            }
        });

        return panel;
    }

    private JPanel createFormPanel(String title) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(title),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
        return panel;
    }

    private JPanel buttonRow(JButton... buttons) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.add(Box.createHorizontalGlue());
        for (int i = 0; i < buttons.length; i++) {
            panel.add(buttons[i]);
            if (i < buttons.length - 1) {
                panel.add(Box.createHorizontalStrut(6));
            }
        }
        panel.add(Box.createHorizontalGlue());
        return panel;
    }

    private void addRow(JPanel panel, int row, String label, Component valueComponent, Component actionComponent) {
        GridBagConstraints left = baseConstraints();
        left.gridx = 0;
        left.gridy = row;
        left.anchor = GridBagConstraints.NORTHWEST;
        panel.add(new JLabel(label), left);

        GridBagConstraints center = baseConstraints();
        center.gridx = 1;
        center.gridy = row;
        center.fill = GridBagConstraints.HORIZONTAL;
        center.weightx = 1.0;
        panel.add(valueComponent, center);

        if (actionComponent != null) {
            GridBagConstraints right = baseConstraints();
            right.gridx = 2;
            right.gridy = row;
            right.anchor = GridBagConstraints.NORTHEAST;
            panel.add(actionComponent, right);
        }
    }

    private GridBagConstraints baseConstraints() {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(10, 10, 10, 10);
        return constraints;
    }

    private void runCommand(String description, CommandAction action) {
        frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        AppLog.log("UI command click: " + description + " host=" + currentHost() + " port=" + currentCommandPort());
        commandExecutor.execute(() -> {
            try {
                String result = action.run();
                AppLog.log("UI command success: " + description + " result=" + result);
                setUiStatus(description + ": " + result);
            } catch (Exception e) {
                AppLog.log("UI command failed: " + description, e);
                setUiStatus(description + " failed: " + e.getMessage());
                showError(description + " failed: " + e.getMessage());
            } finally {
                SwingUtilities.invokeLater(() -> frame.setCursor(Cursor.getDefaultCursor()));
            }
        });
    }

    private void runCommandIntoDialog(String description, CommandAction action, boolean showSuccessDialog, String successMessage) {
        frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        AppLog.log("UI command click: " + description + " host=" + currentHost() + " port=" + currentCommandPort());
        commandExecutor.execute(() -> {
            try {
                String result = action.run();
                AppLog.log("UI command success: " + description + " result=" + result);
                setUiStatus(description + ": " + result);
                if (showSuccessDialog) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                            frame,
                            successMessage != null ? successMessage : result,
                            "Success",
                            JOptionPane.INFORMATION_MESSAGE
                    ));
                }
            } catch (Exception e) {
                AppLog.log("UI command failed: " + description, e);
                setUiStatus(description + " failed: " + e.getMessage());
                showError(description + " failed: " + e.getMessage());
            } finally {
                SwingUtilities.invokeLater(() -> frame.setCursor(Cursor.getDefaultCursor()));
            }
        });
    }

    private void restartEventListener(int port) {
        stopEventListener();
        listenerRunning = true;
        listenerStatusLabel.setText("Listening on 0.0.0.0:" + port);
        AppLog.log("Restarting event listener on port " + port);
        eventExecutor.execute(() -> runServer(port));
    }

    private void stopEventListener() {
        listenerRunning = false;
        AppLog.log("Stopping event listener");
        closeServerSocket();
    }

    private void runServer(int port) {
        lastEventAtMillis = 0L;
        idleShown = false;
        eventCount = 0;
        SwingUtilities.invokeLater(() -> {
            eventCountLabel.setText("0");
            lastEventSourceLabel.setText("-");
        });

        try (ServerSocketChannel channel = ServerSocketChannel.open(StandardProtocolFamily.INET);
             ServerSocket socket = channel.socket()) {
            serverSocket = socket;
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress("0.0.0.0", port));
            socket.setSoTimeout(ACCEPT_TIMEOUT_MILLIS);
            AppLog.log("Event listener bound on 0.0.0.0:" + port);

            SwingUtilities.invokeLater(() -> listenerStatusLabel.setText("Listening on 0.0.0.0:" + port));
            while (appRunning.get() && listenerRunning) {
                try (Socket client = socket.accept()) {
                    AppLog.log("Accepted event connection from " + client.getRemoteSocketAddress());
                    try {
                        handleClient(client);
                    } catch (IOException e) {
                        AppLog.log("Event client handling failed", e);
                        SwingUtilities.invokeLater(() -> listenerStatusLabel.setText("Event parse failed: " + e.getMessage()));
                    }
                } catch (java.net.SocketTimeoutException ignored) {
                    maybeShowIdle();
                }
            }
        } catch (IOException e) {
            AppLog.log("Event listener failed on port " + port, e);
            if (appRunning.get() && listenerRunning) {
                updateUi(new CameraState(EventType.ERROR, "Listener Error", e.getMessage(), lastImage));
                SwingUtilities.invokeLater(() -> listenerStatusLabel.setText("Listener error: " + e.getMessage()));
            }
        } finally {
            AppLog.log("Event listener stopped on port " + port);
            serverSocket = null;
        }
    }

    private void handleClient(Socket client) throws IOException {
        client.setSoTimeout(ACCEPT_TIMEOUT_MILLIS);
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(client.getInputStream()))) {
            byte[] header = new byte[EVENT_HEADER_SIZE];
            input.readFully(header);
            String headerText = new String(header, 0, EVENT_HEADER.length(), StandardCharsets.US_ASCII);
            if (!EVENT_HEADER.equals(headerText)) {
                throw new IOException("Unexpected event header");
            }

            int payloadSize = readInt(header, 16);
            AppLog.log("Event packet header=" + toHex(header) + " payloadSize=" + payloadSize
                    + " remote=" + client.getRemoteSocketAddress());
            byte[] payload = new byte[payloadSize];
            input.readFully(payload);

            CameraState state = parsePayload(payload);
            lastEventAtMillis = System.currentTimeMillis();
            idleShown = false;
            eventCount++;
            SwingUtilities.invokeLater(() ->
                    {
                        String source = client.getInetAddress().getHostAddress() + " at " + TIME_FORMAT.format(LocalTime.now());
                        listenerStatusLabel.setText("Last event from " + source);
                        eventCountLabel.setText(String.valueOf(eventCount));
                        lastEventSourceLabel.setText(source);
                    }
            );
            if (state.image != null) {
                lastImage = state.image;
            } else if (lastImage != null) {
                state = new CameraState(state.eventType, state.headline, state.detail, lastImage);
            }
            updateUi(state);
        } catch (EOFException ignored) {
            AppLog.log("Event client closed before full payload from " + client.getRemoteSocketAddress());
            maybeShowIdle();
        } finally {
            AppLog.log("Closing event client " + client.getRemoteSocketAddress());
        }
    }

    private CameraState parsePayload(byte[] payload) throws IOException {
        int offset = 0;
        int jpgSize = readInt(payload, offset);
        offset += 4;
        if (jpgSize < 0 || offset + jpgSize > payload.length) {
            throw new IOException("Invalid jpg size");
        }

        BufferedImage image = null;
        if (jpgSize > 0) {
            image = ImageIO.read(new ByteArrayInputStream(payload, offset, jpgSize));
        }
        offset += jpgSize;

        int detectStatus = offset < payload.length ? payload[offset++] & 0xFF : 0;
        if (detectStatus > 0 && offset + 8 <= payload.length) {
            offset += 8;
        }
        int identifyStatus = offset < payload.length ? payload[offset++] & 0xFF : 0;
        String identifyId = "";
        float identifyScore = 0.0f;
        if (identifyStatus != 0 && offset < payload.length) {
            int idLength = payload[offset++] & 0xFF;
            if (offset + idLength <= payload.length) {
                identifyId = new String(payload, offset, idLength, StandardCharsets.UTF_8).trim();
                offset += idLength;
            }
            if (offset + 4 <= payload.length) {
                identifyScore = readInt(payload, offset) / 10000.0f;
            }
        }

        if (identifyStatus == 1) {
            String detail = identifyId.isEmpty()
                    ? "Recognised at " + TIME_FORMAT.format(LocalTime.now())
                    : "Recognised user " + identifyId + String.format(Locale.US, "  •  score %.4f", identifyScore);
            return new CameraState(EventType.ACCESS_GRANTED, "Access Granted", detail, image);
        }
        if (identifyStatus == 2) {
            AppLog.log("Parsed event detectStatus=" + detectStatus + " identifyStatus=" + identifyStatus + " id=not-recognised");
            return new CameraState(EventType.ACCESS_DENIED, "Access Denied", "User not recognised", image);
        }

        AppLog.log("Parsed event detectStatus=" + detectStatus + " identifyStatus=" + identifyStatus
                + " id=" + identifyId + " score=" + identifyScore + " jpgBytes=" + jpgSize);
        return switch (detectStatus) {
            case 1 -> new CameraState(EventType.FACE_TOO_SMALL, "Move Closer", "Face too small", image);
            case 2 -> new CameraState(EventType.HEAD_POSE_WRONG, "Adjust Head Pose", "Head pose wrong", image);
            case 3 -> new CameraState(EventType.FACE_DETECTED, "Face Detected", "Face detected by camera", image);
            default -> new CameraState(EventType.IDLE, "Ready", "Waiting for camera events", image);
        };
    }

    private void maybeShowIdle() {
        long now = System.currentTimeMillis();
        if (!idleShown && (lastEventAtMillis == 0L || now - lastEventAtMillis >= IDLE_TIMEOUT_MILLIS)) {
            idleShown = true;
            updateUi(new CameraState(EventType.IDLE, "Ready", "Waiting for camera events", lastImage));
        }
    }

    private void updateUi(CameraState state) {
        SwingUtilities.invokeLater(() -> {
            imagePanel.setBorder(createStatusBorder(state.eventType));
            statusLabel.setText(state.headline);
            detailLabel.setText(state.detail);
            if (state.image != null) {
                BufferedImage rotated = rotate90Clockwise(state.image);
                imageLabel.setIcon(new javax.swing.ImageIcon(scaleImage(rotated, 480, 640)));
                imageLabel.setText("");
            } else {
                imageLabel.setIcon(null);
                imageLabel.setText("Waiting for camera events");
            }
        });
    }

    private javax.swing.border.Border createStatusBorder(EventType eventType) {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(colorFor(eventType), 14),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)
        );
    }

    private Image scaleImage(BufferedImage image, int targetWidth, int targetHeight) {
        double scale = Math.min((double) targetWidth / image.getWidth(), (double) targetHeight / image.getHeight());
        int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scaled.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.drawImage(image.getScaledInstance(width, height, Image.SCALE_SMOOTH), 0, 0, null);
        graphics.dispose();
        return scaled;
    }

    private BufferedImage rotate90Clockwise(BufferedImage image) {
        BufferedImage rotated = new BufferedImage(image.getHeight(), image.getWidth(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rotated.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        AffineTransform transform = new AffineTransform();
        transform.translate(image.getHeight(), 0);
        transform.rotate(Math.toRadians(90));
        graphics.drawImage(image, transform, null);
        graphics.dispose();
        return rotated;
    }

    private Color colorFor(EventType eventType) {
        return switch (eventType) {
            case FACE_TOO_SMALL -> new Color(217, 140, 43);
            case HEAD_POSE_WRONG -> new Color(204, 107, 44);
            case FACE_DETECTED -> new Color(244, 197, 66);
            case ACCESS_GRANTED -> new Color(46, 173, 98);
            case ACCESS_DENIED -> new Color(198, 69, 69);
            case ERROR -> new Color(123, 30, 30);
            case IDLE -> new Color(38, 50, 56);
        };
    }

    private String currentHost() {
        return cameraHostField.getText().trim();
    }

    private int currentCommandPort() {
        return parseInteger(commandPortField.getText(), "command port");
    }

    private int parseInteger(String value, String fieldName) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " must be a number");
        }
    }

    private float parseFloat(String value, String fieldName) {
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " must be a decimal number");
        }
    }

    private String resolveLocalIpSummary() {
        String preferred = preferredListenerIp();
        List<String> ips = collectLocalIpv4Addresses();
        if (ips.isEmpty()) {
            return "No LAN IP found";
        }
        return "Use " + preferred + "  •  All: " + String.join("  •  ", ips);
    }

    private String preferredListenerIp() {
        List<String> ips = collectLocalIpv4Addresses();
        for (String ip : ips) {
            if (ip.startsWith("192.168.1.")) {
                return ip;
            }
        }
        return ips.isEmpty() ? "192.168.1.101" : ips.get(0);
    }

    private List<String> collectLocalIpv4Addresses() {
        List<String> addresses = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()) {
                    continue;
                }
                Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress address = inetAddresses.nextElement();
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        addresses.add(address.getHostAddress());
                    }
                }
            }
        } catch (SocketException ignored) {
        }
        return addresses;
    }

    private void setUiStatus(String message) {
        AppLog.log("UI status: " + message);
        SwingUtilities.invokeLater(() -> uiStatusLabel.setText(message));
    }

    private String toHex(byte[] data) {
        if (data == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(data.length * 2);
        for (byte value : data) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private void showError(String message) {
        AppLog.log("UI error: " + message);
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame, message, "Error", JOptionPane.ERROR_MESSAGE));
    }

    private Path chooseFile(String title, FileNameExtensionFilter filter) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setFileFilter(filter);
        return chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile().toPath() : null;
    }

    private Path chooseSaveFile(String title, String suggestedName, FileNameExtensionFilter filter) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setSelectedFile(new java.io.File(suggestedName));
        chooser.setFileFilter(filter);
        return chooser.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile().toPath() : null;
    }

    private Path chooseDirectory(String title) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        return chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile().toPath() : null;
    }

    private Path requirePath(Path path, String message) {
        if (path == null) {
            throw new IllegalArgumentException(message);
        }
        return path;
    }

    private String readMd5(Path path) throws IOException {
        return Files.readString(path).trim();
    }

    private void verifyMd5(byte[] data, String expectedMd5, String label) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            String actual = toHex(md5.digest(data));
            if (!actual.equalsIgnoreCase(expectedMd5)) {
                throw new IllegalArgumentException(label + " md5 checksum does not match");
            }
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 algorithm unavailable");
        }
    }

    private Path replaceExtension(Path path, String extension) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String baseName = dot >= 0 ? name.substring(0, dot) : name;
        return path.resolveSibling(baseName + extension);
    }

    private String formatUserList(CommandClient.UserListResult result) {
        String raw = result.rawList();
        if (raw.indexOf('\0') >= 0) {
            raw = raw.replace('\0', '\n');
        }
        return "Count: " + result.count() + "\n\n" + raw.trim();
    }

    private void closeServerSocket() {
        ServerSocket socket = serverSocket;
        if (socket == null) {
            return;
        }
        try {
            AppLog.log("Closing listener server socket");
            socket.close();
        } catch (IOException ignored) {
            AppLog.log("Ignoring listener close exception");
        }
    }

    private static int readInt(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24)
                | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
    }

    @FunctionalInterface
    private interface CommandAction {
        String run() throws Exception;
    }

    private enum EventType {
        IDLE,
        FACE_TOO_SMALL,
        HEAD_POSE_WRONG,
        FACE_DETECTED,
        ACCESS_GRANTED,
        ACCESS_DENIED,
        ERROR
    }

    private record CameraState(EventType eventType, String headline, String detail, BufferedImage image) { }
}
