package com.dbexplorer.ui;

import com.dbexplorer.model.ConnectionInfo;
import com.dbexplorer.model.DatabaseType;
import com.dbexplorer.service.ConnectionManager;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

import com.dbexplorer.model.ConnectionInfo;
import com.dbexplorer.model.DatabaseType;
import com.dbexplorer.service.ConnectionManager;

public class ConnectionDialog extends JDialog {

    private final JTextField nameField = new JTextField(20);
    private final JComboBox<DatabaseType> dbTypeCombo = new JComboBox<>(DatabaseType.values());

    // JDBC fields
    private final JTextField hostField = new JTextField("localhost", 20);
    private final JTextField portField = new JTextField(6);
    private final JTextField databaseField = new JTextField(20);
    private final JTextField usernameField = new JTextField(20);
    private final JPasswordField passwordField = new JPasswordField(20);
    private final JTextField driverPathField = new JTextField(30);

    // SQLite fields
    private final JTextField sqliteFileField = new JTextField(30);
    private final JButton sqliteBrowseBtn = new JButton("Browse…");

    // AWS DynamoDB fields
    private final JTextField awsRegionField = new JTextField("us-east-1", 20);
    private final JTextField awsAccessKeyField = new JTextField(20);
    private final JPasswordField awsSecretKeyField = new JPasswordField(20);
    private final JTextField awsEndpointField = new JTextField(30);

    // Panels for toggling visibility
    private final List<Component[]> jdbcRows = new ArrayList<>();
    private final List<Component[]> sqliteRows = new ArrayList<>();
    private final List<Component[]> dynamoRows = new ArrayList<>();
    private JPanel formPanel;

    private final ConnectionManager connectionManager;
    private ConnectionInfo result;
    private ConnectionInfo editing;

    public ConnectionDialog(Frame owner, ConnectionManager connectionManager, ConnectionInfo editing) {
        super(owner, editing == null ? "New Connection" : "Edit Connection", true);
        this.connectionManager = connectionManager;
        this.editing = editing;
        initUI();
        if (editing != null) populateFields(editing);
        toggleFieldVisibility();
        pack();
        setLocationRelativeTo(owner);
    }

    private void initUI() {
        formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 6, 12));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        int row = 0;
        addRow(formPanel, gbc, row++, "Connection Name:", nameField, null);
        addRow(formPanel, gbc, row++, "Database Type:", dbTypeCombo, null);

        // JDBC-specific rows
        addRow(formPanel, gbc, row++, "Host:", hostField, jdbcRows);
        addRow(formPanel, gbc, row++, "Port:", portField, jdbcRows);
        addRow(formPanel, gbc, row++, "Database:", databaseField, jdbcRows);
        addRow(formPanel, gbc, row++, "Username:", usernameField, jdbcRows);
        addRow(formPanel, gbc, row++, "Password:", passwordField, jdbcRows);
        addRow(formPanel, gbc, row++, "Driver Path (optional):", driverPathField, jdbcRows);

        // SQLite-specific row (file path + browse button)
        JPanel sqliteFilePanel = new JPanel(new BorderLayout(4, 0));
        sqliteFilePanel.add(sqliteFileField, BorderLayout.CENTER);
        sqliteFilePanel.add(sqliteBrowseBtn, BorderLayout.EAST);
        sqliteBrowseBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle("Select SQLite Database File");
            fc.setFileFilter(new FileNameExtensionFilter(
                    "SQLite files (*.db, *.sqlite, *.sqlite3)", "db", "sqlite", "sqlite3"));
            fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                sqliteFileField.setText(fc.getSelectedFile().getAbsolutePath());
            }
        });
        addRow(formPanel, gbc, row++, "Database File:", sqliteFilePanel, sqliteRows);

        // DynamoDB-specific rows
        addRow(formPanel, gbc, row++, "AWS Region:", awsRegionField, dynamoRows);
        addRow(formPanel, gbc, row++, "Access Key ID:", awsAccessKeyField, dynamoRows);
        addRow(formPanel, gbc, row++, "Secret Access Key:", awsSecretKeyField, dynamoRows);
        addRow(formPanel, gbc, row++, "Endpoint (optional):", awsEndpointField, dynamoRows);

        // Toggle fields when DB type changes
        dbTypeCombo.addActionListener(e -> {
            toggleFieldVisibility();
            pack();
        });
        portField.setText(String.valueOf(
                ((DatabaseType) dbTypeCombo.getSelectedItem()).getDefaultPort()));

        // Buttons
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setBorder(BorderFactory.createEmptyBorder(6, 12, 12, 12));

        JButton testBtn = new JButton("Test Connection");
        testBtn.addActionListener(e -> testConnection());
        JButton saveBtn = new JButton("Save");
        saveBtn.addActionListener(e -> save());
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());

        buttons.add(testBtn);
        buttons.add(Box.createHorizontalStrut(20));
        buttons.add(saveBtn);
        buttons.add(cancelBtn);

        setLayout(new BorderLayout());
        add(formPanel, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        setResizable(false);
    }

    private void addRow(JPanel panel, GridBagConstraints gbc, int row,
                        String label, JComponent field, List<Component[]> group) {
        JLabel lbl = new JLabel(label);
        gbc.gridx = 0; gbc.gridy = row; gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        panel.add(lbl, gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        panel.add(field, gbc);
        if (group != null) group.add(new Component[]{lbl, field});
    }

    private void toggleFieldVisibility() {
        DatabaseType sel = (DatabaseType) dbTypeCombo.getSelectedItem();
        boolean isDynamo = sel == DatabaseType.DYNAMODB;
        boolean isSQLite = sel == DatabaseType.SQLITE;

        for (Component[] pair : jdbcRows) {
            pair[0].setVisible(!isDynamo && !isSQLite);
            pair[1].setVisible(!isDynamo && !isSQLite);
        }
        for (Component[] pair : sqliteRows) {
            pair[0].setVisible(isSQLite);
            pair[1].setVisible(isSQLite);
        }
        for (Component[] pair : dynamoRows) {
            pair[0].setVisible(isDynamo);
            pair[1].setVisible(isDynamo);
        }

        if (!isDynamo && !isSQLite && sel != null) {
            portField.setText(String.valueOf(sel.getDefaultPort()));
        }
    }

    private void populateFields(ConnectionInfo info) {
        nameField.setText(info.getName());
        dbTypeCombo.setSelectedItem(info.getDbType());
        if (info.getDbType() == DatabaseType.SQLITE) {
            sqliteFileField.setText(info.getDatabase() != null ? info.getDatabase() : "");
        } else {
            hostField.setText(info.getHost());
            portField.setText(String.valueOf(info.getPort()));
            databaseField.setText(info.getDatabase());
            usernameField.setText(info.getUsername());
            passwordField.setText(info.getPassword());
            if (info.getDriverPath() != null) driverPathField.setText(info.getDriverPath());
        }
        if (info.getAwsRegion() != null) awsRegionField.setText(info.getAwsRegion());
        if (info.getAwsAccessKey() != null) awsAccessKeyField.setText(info.getAwsAccessKey());
        if (info.getAwsSecretKey() != null) awsSecretKeyField.setText(info.getAwsSecretKey());
        if (info.getAwsEndpoint() != null) awsEndpointField.setText(info.getAwsEndpoint());
    }

    private ConnectionInfo buildFromFields() {
        ConnectionInfo info = editing != null ? editing : new ConnectionInfo();
        info.setName(nameField.getText().trim());
        DatabaseType dbType = (DatabaseType) dbTypeCombo.getSelectedItem();
        info.setDbType(dbType);

        if (dbType == DatabaseType.DYNAMODB) {
            info.setAwsRegion(awsRegionField.getText().trim());
            info.setAwsAccessKey(awsAccessKeyField.getText().trim());
            info.setAwsSecretKey(new String(awsSecretKeyField.getPassword()));
            String ep = awsEndpointField.getText().trim();
            info.setAwsEndpoint(ep.isEmpty() ? null : ep);
        } else if (dbType == DatabaseType.SQLITE) {
            info.setDatabase(sqliteFileField.getText().trim());
        } else {
            info.setHost(hostField.getText().trim());
            info.setPort(Integer.parseInt(portField.getText().trim()));
            info.setDatabase(databaseField.getText().trim());
            info.setUsername(usernameField.getText().trim());
            info.setPassword(new String(passwordField.getPassword()));
            String dp = driverPathField.getText().trim();
            info.setDriverPath(dp.isEmpty() ? null : dp);
        }
        return info;
    }

    private void testConnection() {
        try {
            ConnectionInfo info = buildFromFields();
            if (info.getDbType() == DatabaseType.DYNAMODB) {
                connectionManager.testDynamoConnection(info);
            } else {
                connectionManager.testConnection(info);
            }
            JOptionPane.showMessageDialog(this, "Connection successful!",
                    "Test", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Connection failed:\n" + ex.getMessage(),
                    "Test Failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void save() {
        if (nameField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Connection name is required.");
            return;
        }
        DatabaseType sel = (DatabaseType) dbTypeCombo.getSelectedItem();
        if (sel == DatabaseType.SQLITE && sqliteFileField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select a SQLite database file.");
            return;
        }
        try {
            result = buildFromFields();
            dispose();
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Port must be a valid number.");
        }
    }

    public ConnectionInfo getResult() { return result; }
}
