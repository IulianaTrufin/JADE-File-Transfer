package gui;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.io.File;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.WindowConstants;

import agents.ReceiverAgent;

public class ReceiverGui extends JFrame {

	private final ReceiverAgent agent;
	private JTextArea infoArea;
	private JButton chooseFolderButton;
	private JButton askAiButton;

	private File destinationFolder;

	public ReceiverGui(ReceiverAgent agent) {
		super("Receiver - Punctul B");
		this.agent = agent;
		initGui();
	}

	private void initGui() {
		infoArea = new JTextArea(10, 30);
		infoArea.setEditable(false);

		chooseFolderButton = new JButton("Alege folder destinatie");
		askAiButton = new JButton("Intreaba AI");

		JPanel bottomPanel = new JPanel();
		bottomPanel.add(chooseFolderButton);
		bottomPanel.add(askAiButton);

		JScrollPane scroll = new JScrollPane(infoArea);

		JPanel panel = new JPanel(new BorderLayout());
		panel.add(scroll, BorderLayout.CENTER);
		panel.add(bottomPanel, BorderLayout.SOUTH);

		chooseFolderButton.addActionListener(e -> onChooseFolder(e));
		askAiButton.addActionListener(e -> onAskAi(e));

		setContentPane(panel);
		pack();
		setLocationRelativeTo(null);
		setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		setVisible(true);
	}

	private void onChooseFolder(ActionEvent e) {
		JFileChooser chooser = new JFileChooser();
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

		int result = chooser.showOpenDialog(this);
		if (result == JFileChooser.APPROVE_OPTION) {
			destinationFolder = chooser.getSelectedFile();
			agent.setDestinationFolder(destinationFolder);
			appendInfo("Folder destinație setat: " + destinationFolder.getAbsolutePath());
		}
	}

	private void onAskAi(ActionEvent e) {
		agent.askAiForHelp();
	}

	public void appendInfo(String text) {
		infoArea.append(text + "\n");
	}

	public boolean confirmReception(String fileName, long size) {
		String message = "Doresti sa primesti fisierul:\n" + fileName + " (" + size + " bytes) ?";
		int res = JOptionPane.showConfirmDialog(this, message, "Confirmare primire fisier", JOptionPane.YES_NO_OPTION);
		return res == JOptionPane.YES_OPTION;
	}
}
