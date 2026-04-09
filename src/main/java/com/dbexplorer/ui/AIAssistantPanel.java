package com.dbexplorer.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

import com.dbexplorer.model.AIConfig;
import com.dbexplorer.model.ConnectionInfo;
import com.dbexplorer.service.AIAssistantService;
import com.dbexplorer.service.AIConfigManager;
import com.dbexplorer.service.ConnectionManager;

/**
 * AI Assistant Panel for generating SQL from natural language descriptions.
 */
public class AIAssistantPanel extends JPanel {
    private final AIAssistantService aiService;
    private final ConnectionManager connectionManager;
    private final AIConfigManager configManager;

    private JLabel connectionContextLabel;
    private JComboBox<AIConfig> modelSelector;
    private JTextArea naturalLanguageTextArea;
    private JTextArea generatedSQLTextArea;
    private JButton generateButton;
    private JButton clearButton;
    private JLabel statusLabel;
    private JProgressBar progressBar;
    private ConnectionInfo currentConnection;
    private String currentSchemaInfo = "";

    public AIAssistantPanel(ConnectionManager connectionManager, AIConfigManager configManager) {
        this.connectionManager = connectionManager;
        this.configManager = configManager;
        this.aiService = new AIAssistantService(configManager);
        initializeUI();
        refreshModelSelector();
    }

    private void initializeUI() {
        setLayout(new BorderLayout(5, 5));
        setBorder(new EmptyBorder(6, 8, 6, 8));

        // Top Context Bar
        JPanel contextBar = new JPanel(new BorderLayout());
        contextBar.setBackground(new Color(240, 240, 240));
        contextBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY),
            BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));
        
        connectionContextLabel = new JLabel("Database Context: None");
        connectionContextLabel.setFont(connectionContextLabel.getFont().deriveFont(Font.BOLD));
        contextBar.add(connectionContextLabel, BorderLayout.WEST);
        add(contextBar, BorderLayout.NORTH);

        // Header panel with model selector
        JPanel centerWrapper = new JPanel(new BorderLayout(5, 5));
        
        JPanel headerPanel = createHeaderPanel();
        centerWrapper.add(headerPanel, BorderLayout.NORTH);

        // Main content panel
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.5);

        JPanel inputPanel = createInputPanel();
        splitPane.setTopComponent(inputPanel);

        JPanel outputPanel = createOutputPanel();
        splitPane.setBottomComponent(outputPanel);

        centerWrapper.add(splitPane, BorderLayout.CENTER);
        add(centerWrapper, BorderLayout.CENTER);

        // Footer panel
        JPanel footerPanel = createFooterPanel();
        add(footerPanel, BorderLayout.SOUTH);

        updateConfigurationStatus();
    }

    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 5));
        panel.setBorder(new EmptyBorder(5, 0, 5, 0));

        JLabel titleLabel = new JLabel("🤖 AI SQL Generator");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        panel.add(titleLabel, BorderLayout.WEST);

        JPanel modelPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        modelPanel.add(new JLabel("Active AI Profile:"));
        modelSelector = new JComboBox<>();
        modelSelector.setPreferredSize(new Dimension(250, 25));
        modelSelector.addActionListener(e -> {
            AIConfig selected = (AIConfig) modelSelector.getSelectedItem();
            if (selected != null) {
                configManager.setLastUsedConfig(selected.getId());
                updateConfigurationStatus();
            }
        });
        modelPanel.add(modelSelector);
        
        panel.add(modelPanel, BorderLayout.EAST);
        return panel;
    }

    public void refreshModelSelector() {
        modelSelector.removeAllItems();
        List<AIConfig> configs = configManager.getConfigs();
        for (AIConfig config : configs) {
            modelSelector.addItem(config);
        }
        
        AIConfig lastUsed = configManager.getLastUsedConfig();
        if (lastUsed != null) {
            modelSelector.setSelectedItem(lastUsed);
        }
    }

    private JPanel createInputPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("Describe Your Query in Natural Language"));

        naturalLanguageTextArea = new JTextArea(4, 40);
        naturalLanguageTextArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        naturalLanguageTextArea.setLineWrap(true);
        naturalLanguageTextArea.setWrapStyleWord(true);
        naturalLanguageTextArea.setText("Example: Get all customers from New York who made purchases in the last 30 days");

        JScrollPane scrollPane = new JScrollPane(naturalLanguageTextArea);
        panel.add(scrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        generateButton = new JButton("Generate SQL");
        generateButton.addActionListener(e -> generateSQL());
        buttonPanel.add(generateButton);

        clearButton = new JButton("Clear");
        clearButton.addActionListener(e -> naturalLanguageTextArea.setText(""));
        buttonPanel.add(clearButton);

        panel.add(buttonPanel, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createOutputPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("Generated SQL"));

        generatedSQLTextArea = new JTextArea(4, 40);
        generatedSQLTextArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        generatedSQLTextArea.setLineWrap(true);
        generatedSQLTextArea.setWrapStyleWord(true);
        generatedSQLTextArea.setBackground(UIManager.getColor("TextArea.background"));

        JScrollPane scrollPane = new JScrollPane(generatedSQLTextArea);
        panel.add(scrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        JButton copyButton = new JButton("Copy to Clipboard");
        copyButton.addActionListener(e -> copyToClipboard());
        buttonPanel.add(copyButton);

        JButton insertButton = new JButton("Insert into Query Tab");
        insertButton.addActionListener(e -> insertIntoQueryTab());
        buttonPanel.add(insertButton);

        panel.add(buttonPanel, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createFooterPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 2));

        statusLabel = new JLabel("Ready");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 11f));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        panel.add(statusLabel, BorderLayout.CENTER);

        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setStringPainted(true);
        progressBar.setString("AI is thinking...");
        progressBar.setVisible(false);
        panel.add(progressBar, BorderLayout.NORTH);

        return panel;
    }

    public void updateConfigurationStatus() {
        AIConfig selected = (AIConfig) modelSelector.getSelectedItem();
        if (selected == null) {
            statusLabel.setText("⚠ No AI profile selected. Configure in Edit → AI Configuration.");
            statusLabel.setForeground(Color.ORANGE);
            generateButton.setEnabled(false);
        } else if (!selected.isEnabled()) {
            statusLabel.setText("⚠ Selected AI profile is disabled.");
            statusLabel.setForeground(Color.ORANGE);
            generateButton.setEnabled(false);
        } else {
            statusLabel.setText("✓ AI Profile: " + selected.getName());
            statusLabel.setForeground(UIManager.getColor("Label.foreground"));
            generateButton.setEnabled(true);
        }
    }

    public void setConnection(ConnectionInfo connectionInfo) {
        this.currentConnection = connectionInfo;
        if (connectionInfo != null) {
            connectionContextLabel.setText("Database Context: " + connectionInfo.getName() + " (" + connectionInfo.getDbType() + ")");
            connectionContextLabel.setForeground(new Color(0, 102, 204));
            extractSchemaInfo(connectionInfo);
        } else {
            connectionContextLabel.setText("Database Context: None");
            connectionContextLabel.setForeground(Color.RED);
        }
    }

    private void extractSchemaInfo(ConnectionInfo connectionInfo) {
        new Thread(() -> {
            try {
                Connection conn = connectionManager.getActiveConnection(connectionInfo.getId());
                if (conn != null) {
                    currentSchemaInfo = buildSchemaInfo(conn);
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("Schema context loaded for " + connectionInfo.getName());
                        statusLabel.setForeground(Color.GREEN.darker());
                    });
                } else {
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("Database not connected. Please connect to enable AI.");
                        statusLabel.setForeground(Color.ORANGE);
                    });
                }
            } catch (SQLException e) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Failed to load schema: " + e.getMessage());
                    statusLabel.setForeground(Color.RED);
                });
            }
        }).start();
    }

    private String buildSchemaInfo(Connection conn) throws SQLException {
        StringBuilder schema = new StringBuilder();
        DatabaseMetaData metaData = conn.getMetaData();

        try (ResultSet tables = metaData.getTables(null, null, "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                schema.append("Table: ").append(tableName).append("\n");
                try (ResultSet columns = metaData.getColumns(null, null, tableName, null)) {
                    while (columns.next()) {
                        String columnName = columns.getString("COLUMN_NAME");
                        String dataType = columns.getString("TYPE_NAME");
                        schema.append("  - ").append(columnName).append(" (").append(dataType).append(")\n");
                    }
                }
                schema.append("\n");
            }
        }
        return schema.toString();
    }

    private void generateSQL() {
        AIConfig selected = (AIConfig) modelSelector.getSelectedItem();
        if (selected == null) return;

        String naturalLanguageQuery = naturalLanguageTextArea.getText().trim();
        if (naturalLanguageQuery.isEmpty() || currentConnection == null) return;

        // Visual update
        generateButton.setEnabled(false);
        progressBar.setVisible(true);
        statusLabel.setText("AI is thinking...");

        new Thread(() -> {
            try {
                String generatedSQL = aiService.generateSQLWithSpecificConfig(
                        selected,
                        naturalLanguageQuery,
                        currentSchemaInfo,
                        currentConnection.getDbType().name()
                );

                SwingUtilities.invokeLater(() -> {
                    generatedSQLTextArea.setText(generatedSQL);
                    statusLabel.setText("SQL generated successfully for " + currentConnection.getName());
                    statusLabel.setForeground(Color.GREEN.darker());
                    generateButton.setEnabled(true);
                    progressBar.setVisible(false);
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    generatedSQLTextArea.setText("Error: " + e.getMessage());
                    statusLabel.setText("Error generating SQL");
                    statusLabel.setForeground(Color.RED);
                    generateButton.setEnabled(true);
                    progressBar.setVisible(false);
                });
            }
        }).start();
    }

    private void copyToClipboard() {
        String sql = generatedSQLTextArea.getText();
        if (!sql.isEmpty()) {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(sql), null);
            statusLabel.setText("SQL copied to clipboard");
        }
    }

    private void insertIntoQueryTab() {
        copyToClipboard();
        JOptionPane.showMessageDialog(this, "SQL copied to clipboard. Paste into query tab with Ctrl+V.");
    }
}
