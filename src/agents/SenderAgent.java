package agents;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import gui.SenderGui;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;

public class SenderAgent extends Agent {

	private transient SenderGui gui;
	private File selectedFile;
	private String currentFileName;
	private volatile boolean receiverReady;
	private AID cachedReceiver = null;

	@Override
	protected void setup() {
		appendToGui(getLocalName() + " pornit.");

		registerService("file-sender", "JADE-file-sender");

		gui = new SenderGui(this);

		addBehaviour(new OneShotBehaviour() {
			@Override
			public void action() {
				cachedReceiver = searchService("file-receiver");
				if (cachedReceiver != null) {
					appendToGui("Receiver gasit in DF si cache-uit: " + cachedReceiver.getLocalName());
				} else {
					appendToGui("Receiver NU a fost gasit in DF la startup.");
				}
			}
		});

		addBehaviour(new CyclicBehaviour(this) {
			@Override
			public void action() {
				ACLMessage msg = receive();
				if (msg != null) {
					handleIncomingMessage(msg);
				} else {
					block();
				}
			}
		});
	}

	public void appendToGui(String text) {
		System.out.println(text);

		if (gui != null) {
			SwingUtilities.invokeLater(() -> gui.appendInfo(text));
		}
	}

	@Override
	protected void takeDown() {
		appendToGui(getLocalName() + " se opreste.");
		try {
			DFService.deregister(this);
		} catch (Exception ignored) {
		}

		if (gui != null) {
			SwingUtilities.invokeLater(() -> {
				try {
					gui.dispose();
				} catch (Exception ignored) {
				}
			});
		}
	}

	private void registerService(String type, String name) {
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());

		ServiceDescription sd = new ServiceDescription();
		sd.setType(type);
		sd.setName(name);

		dfd.addServices(sd);
		try {
			DFService.register(this, dfd);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private AID searchService(String type) {
		DFAgentDescription template = new DFAgentDescription();
		ServiceDescription sd = new ServiceDescription();
		sd.setType(type);
		template.addServices(sd);

		try {
			DFAgentDescription[] result = DFService.search(this, template);
			if (result != null && result.length > 0) {
				AID found = result[0].getName();
				if ("file-receiver".equals(type)) {
					cachedReceiver = found;
				}
				return found;
			} else {
				appendToGui(getLocalName() + ": niciun agent de tip " + type + " gasit in DF.");
			}
		} catch (Exception e) {
			appendToGui(getLocalName() + ": eroare DF: " + e.getMessage());
		}

		return null;
	}

	public void setSelectedFile(File file) {
		this.selectedFile = file;
		if (file != null) {
			this.currentFileName = file.getName();
		} else {
			this.currentFileName = null;
		}
	}

	public void initiateFileTransfer() {
		if (selectedFile == null || !selectedFile.exists()) {
			appendToGui("Niciun fisier selectat sau fisierul nu exista.");
			return;
		}

		if (!receiverReady) {
			System.out.println("Receiver-ul nu a semnalat inca ca este READY.");
			return;
		}

		if (currentFileName == null || currentFileName.isEmpty()) {
			currentFileName = selectedFile.getName();
		}

		AID receiverAgent = (cachedReceiver != null) ? cachedReceiver : searchService("file-receiver");
		if (receiverAgent == null) {
			appendToGui("Nu am gasit un agent de tip file-receiver.");
			return;
		}

		ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
		msg.addReceiver(receiverAgent);
		msg.setConversationId("file-transfer");
		msg.setOntology("file-offer");
		msg.setContent("FILENAME:" + currentFileName + ";SIZE:" + selectedFile.length());
		send(msg);

		System.out.println("Cerere de transfer trimisa catre " + receiverAgent.getLocalName() + " pentru fisierul "
				+ currentFileName);
	}

	private void handleIncomingMessage(ACLMessage msg) {
		String ontology = msg.getOntology();
		int perf = msg.getPerformative();

		if (perf == ACLMessage.INFORM && "receiver-ready".equals(ontology)) {
			receiverReady = true;
			try {
				AID receiverAID = new AID(msg.getContent(), AID.ISGUID);
				cachedReceiver = receiverAID;
				appendToGui("Receiver-ready primit. AID cache-uit: " + cachedReceiver.getName());
			} catch (Exception e) {
				appendToGui("Receiver-ready primit, dar AID invalid: " + msg.getContent());
			}
		} else if (perf == ACLMessage.AGREE && "file-offer".equals(ontology)) {
			appendToGui("Receiver a ACCEPTAT transferul. Incep trimiterea datelor.");
			sendFileChunks();
		} else if (perf == ACLMessage.REFUSE && "file-offer".equals(ontology)) {
			appendToGui("Receiver a REFUZAT transferul.");
		} else if (perf == ACLMessage.INFORM && "file-exists".equals(ontology)) {
			handleFileExists(msg);
		} else if (perf == ACLMessage.INFORM) {
			appendToGui("INFORM de la " + msg.getSender().getLocalName() + ": " + msg.getContent());
		} else if (perf == ACLMessage.REQUEST && "shutdown".equals(ontology)) {
			doDelete();
		} else {
			appendToGui("Mesaj necunoscut: " + msg);
		}
	}

	private void handleFileExists(ACLMessage msg) {
		String existingName = msg.getContent();
		appendToGui("Fisierul \"" + existingName + "\" exista deja la destinatie.");

		SwingUtilities.invokeLater(() -> {
			String suggested = suggestNewName(existingName);
			String newName = JOptionPane.showInputDialog(null, "Fisierul exista deja.\nIntroduceti un nume nou:",
					suggested);

			if (newName != null) {
				newName = newName.trim();
			}

			if (newName == null || newName.isEmpty()) {
				appendToGui("Nu s-a introdus un nume nou. Transferul anulat.");
				return;
			}

			currentFileName = newName;
			appendToGui("Transfer reluat cu numele: " + currentFileName);
			initiateFileTransfer();
		});
	}

	private String suggestNewName(String original) {
		int dot = original.lastIndexOf('.');
		if (dot > 0 && dot < original.length() - 1) {
			return original.substring(0, dot) + " (1)" + original.substring(dot);
		} else {
			return original + " (1)";
		}
	}

	private void sendFileChunks() {
		if (selectedFile == null) {
			return;
		}

		AID fileManager = searchService("file-manager");
		if (fileManager == null) {
			appendToGui("Nu am gasit FileManagerAgent!");
			return;
		}

		try {
			byte[] allBytes = Files.readAllBytes(selectedFile.toPath());
			int chunkSize = 4096;
			int totalChunks = (int) Math.ceil(allBytes.length / (double) chunkSize);

			for (int i = 0; i < totalChunks; i++) {
				int start = i * chunkSize;
				int end = Math.min(allBytes.length, (i + 1) * chunkSize);

				byte[] chunk = new byte[end - start];
				System.arraycopy(allBytes, start, chunk, 0, end - start);

				ACLMessage chunkMsg = new ACLMessage(ACLMessage.INFORM);
				chunkMsg.addReceiver(fileManager);
				chunkMsg.setConversationId("file-transfer-chunks");
				chunkMsg.setOntology("file-chunk");
				chunkMsg.addUserDefinedParameter("filename", currentFileName);
				chunkMsg.addUserDefinedParameter("chunk-index", String.valueOf(i));
				chunkMsg.addUserDefinedParameter("total-chunks", String.valueOf(totalChunks));
				chunkMsg.setContent(java.util.Base64.getEncoder().encodeToString(chunk));

				send(chunkMsg);
			}

			appendToGui("Toate chunk-urile trimise catre FileManagerAgent.");

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void askAiForHelp() {
		try {
			URL url = new URL("http://localhost:8000/assistant/explain");
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("POST");
			conn.setDoOutput(true);
			conn.setRequestProperty("Content-Type", "application/json");

			String fileName = currentFileName != null ? currentFileName
					: (selectedFile != null ? selectedFile.getName() : "nespecificat");
			long size = selectedFile != null ? selectedFile.length() : 0L;

			String json = "{" + "\"sender\":\"SenderAgent\"," + "\"receiver\":\"ReceiverAgent\"," + "\"filename\":\""
					+ fileName + "\"," + "\"size\":" + size + "," + "\"status\":\"attempted\","
					+ "\"logs\":[\"Transfer initiat din GUI.\"]" + "}";

			OutputStream os = conn.getOutputStream();
			os.write(json.getBytes("UTF-8"));
			os.close();

			if (conn.getResponseCode() == 200) {
				BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
				appendToGui("Raspuns AI: " + br.readLine());
				br.close();
			} else {
				appendToGui("AI server error.");
			}
		} catch (Exception e) {
			appendToGui("Nu am putut contacta serverul AI: " + e.getMessage());
		}
	}
}
