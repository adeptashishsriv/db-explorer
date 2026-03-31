package com.dbexplorer.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.util.concurrent.Callable;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToolBar;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.dbexplorer.model.ConnectionInfo;
import com.dbexplorer.service.DdlExportService;

/**
 * Non-modal dialog that generates and displays DDL for an entire schema.
 * Provides Copy-to-clipboard and Save-to-file actions.
 */
public class DdlExportDialog extends JDialog {

    private final JTextArea textArea;
    private final JLabel    statusLabel;

    public DdlExportDialog(Frame owner, ConnectionInfo info, Connection conn, String schema) {
        super(owner, "DDL Export — " + info.getName()
                + (schema != null ? " / " + schema : ""), false);

        textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        textArea.setLineWrap(false);
        textArea.setText("Generating DDL…");

        JScrollPane scroll = new JScrollPane(textArea,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        // ── Toolbar ───────────────────────────────────────────────────────────
        JToolBar tb = new JToolBar();
        tb.setFloatable(false);

        JButton copyBtn = new JButton("Copy", DbIcons.MENU_COPY);
        copyBtn.setToolTipText("Copy DDL to clipboard");
        copyBtn.addActionListener(e -> copyToClipboard());

        JButton saveBtn = new JButton("Save", DbIcons.MENU_SAVE);
        saveBtn.setToolTipText("Save DDL to a .sql file");
        saveBtn.addActionListener(e -> saveToFile(schema));

        statusLabel = new JLabel("  Generating…");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 11f));
        statusLabel.setForeground(Color.GRAY);

        tb.add(copyBtn);
        tb.add(Box.createHorizontalStrut(6));
        tb.add(saveBtn);
        tb.add(Box.createHorizontalStrut(12));
        tb.add(statusLabel);

        setLayout(new BorderLayout());
        add(tb,     BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);

        setSize(900, 700);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        // Generate DDL on a background thread
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return new DdlExportService().exportSchema(conn, info.getDbType(), schema);
            }
            @Override
            protected void done() {
                try {
                    String ddl = get();
                    textArea.setText(ddl);
                    textArea.setCaretPosition(0);
                    int lines = ddl.split("\n", -1).length;
                    statusLabel.setText("  " + lines + " lines generated");
                    statusLabel.setForeground(UIManager.getColor("Label.foreground"));
                } catch (Exception ex) {
                    textArea.setText("-- Error generating DDL:\n-- " + ex.getMessage());
                    statusLabel.setText("  Error: " + ex.getMessage());
                    statusLabel.setForeground(new Color(200, 50, 50));
                }
            }
        }.execute();
    }

    /**
     * Constructor for single-object DDL export (e.g. a materialized view).
     * The {@code ddlSupplier} is called on a background thread; the result is
     * displayed in the same text area as the schema-level constructor.
     *
     * @param owner       parent frame
     * @param dialogTitle title for the dialog window
     * @param fileName    default file name (without extension) for Save
     * @param ddlSupplier callable that produces the DDL string; runs off the EDT
     */
    public DdlExportDialog(Frame owner, String dialogTitle, String fileName,
                           Callable<String> ddlSupplier) {
        super(owner, dialogTitle, false);

        textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        textArea.setLineWrap(false);
        textArea.setText("Generating DDL…");

        JScrollPane scroll = new JScrollPane(textArea,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        JToolBar tb = new JToolBar();
        tb.setFloatable(false);

        JButton copyBtn = new JButton("Copy", DbIcons.MENU_COPY);
        copyBtn.setToolTipText("Copy DDL to clipboard");
        copyBtn.addActionListener(e -> copyToClipboard());

        JButton saveBtn = new JButton("Save", DbIcons.MENU_SAVE);
        saveBtn.setToolTipText("Save DDL to a .sql file");
        saveBtn.addActionListener(e -> saveToFile(fileName));

        statusLabel = new JLabel("  Generating…");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 11f));
        statusLabel.setForeground(Color.GRAY);

        tb.add(copyBtn);
        tb.add(Box.createHorizontalStrut(6));
        tb.add(saveBtn);
        tb.add(Box.createHorizontalStrut(12));
        tb.add(statusLabel);

        setLayout(new BorderLayout());
        add(tb,     BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);

        setSize(900, 700);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return ddlSupplier.call();
            }
            @Override
            protected void done() {
                try {
                    String ddl = get();
                    textArea.setText(ddl);
                    textArea.setCaretPosition(0);
                    int lines = ddl.split("\n", -1).length;
                    statusLabel.setText("  " + lines + " lines generated");
                    statusLabel.setForeground(UIManager.getColor("Label.foreground"));
                } catch (Exception ex) {
                    textArea.setText("-- Error generating DDL:\n-- " + ex.getMessage());
                    statusLabel.setText("  Error: " + ex.getMessage());
                    statusLabel.setForeground(new Color(200, 50, 50));
                }
            }
        }.execute();
    }

    private void copyToClipboard() {
        String text = textArea.getText();
        if (text == null || text.isBlank()) return;
        Toolkit.getDefaultToolkit()
               .getSystemClipboard()
               .setContents(new StringSelection(text), null);
        statusLabel.setText("  Copied to clipboard");
    }

    private void saveToFile(String schema) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Save DDL");
        fc.setSelectedFile(new java.io.File((schema != null ? schema : "schema") + "_ddl.sql"));
        fc.setFileFilter(new FileNameExtensionFilter("SQL files (*.sql)", "sql"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        java.io.File file = fc.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".sql"))
            file = new java.io.File(file.getAbsolutePath() + ".sql");

        try (FileWriter fw = new FileWriter(file)) {
            fw.write(textArea.getText());
            statusLabel.setText("  Saved to " + file.getName());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to save file:\n" + ex.getMessage(),
                    "Save Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
