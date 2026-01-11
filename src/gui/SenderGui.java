package gui;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.io.File;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.WindowConstants;

import agents.SenderAgent;

public class SenderGui extends JFrame {
	private final SenderAgent agent;
	private JTextField filePathField;
	private JButton browseButton;
	private JButton sendButton;
	private JButton askAiButton;
	private JTextArea infoArea;

	public SenderGui(SenderAgent agent) {
		super("Sender - Punctul A");
		this.agent = agent;
		initGui();
	}

	private void initGui() {
		infoArea = new JTextArea(10, 30);
		infoArea.setEditable(false);
		filePathField = new JTextField(25);
		filePathField.setEditable(false);
		browseButton = new JButton("Alege fisier");
		sendButton = new JButton("Trimite fisier");
		askAiButton = new JButton("Intreaba AI (situatie actuala tranfer)");

		JPanel panel = new JPanel(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(5, 5, 5, 5);
		c.fill = GridBagConstraints.HORIZONTAL;

		c.gridx = 0;
		c.gridy = 0;
		c.gridwidth = 2;
		panel.add(filePathField, c);

		c.gridx = 2;
		c.gridy = 0;
		c.gridwidth = 1;
		panel.add(browseButton, c);

		c.gridx = 0;
		c.gridy = 1;
		panel.add(sendButton, c);

		c.gridx = 1;
		c.gridy = 1;
		panel.add(askAiButton, c);
		c.gridx = 0;
		c.gridy = 2;
		c.gridwidth = 3;
		c.fill = GridBagConstraints.BOTH;
		c.weightx = 1.0;
		c.weighty = 1.0;

		panel.add(new javax.swing.JScrollPane(infoArea), c);

		browseButton.addActionListener(new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				onBrowseClicked(e);
			}
		});

		sendButton.addActionListener(new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				onSendClicked(e);
			}
		});

		askAiButton.addActionListener(new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				onAskAiClicked(e);
			}
		});

		setContentPane(panel);
		pack();
		setLocationRelativeTo(null);
		setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		setVisible(true);
	}

	private void onBrowseClicked(ActionEvent e) {
		JFileChooser chooser = new JFileChooser();
		int result = chooser.showOpenDialog(this);
		if (result == JFileChooser.APPROVE_OPTION) {
			File selected = chooser.getSelectedFile();
			filePathField.setText(selected.getAbsolutePath());
			agent.setSelectedFile(selected);
		}
	}

	private void onSendClicked(ActionEvent e) {
		agent.initiateFileTransfer();
	}

	private void onAskAiClicked(ActionEvent e) {
		agent.askAiForHelp();
	}

	public void appendInfo(String text) {
		infoArea.append(text + "\n");
	}
}
