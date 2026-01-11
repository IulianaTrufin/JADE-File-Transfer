package agents;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.SwingUtilities;

import gui.ReceiverGui;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;

public class ReceiverAgent extends Agent {

	private String destinationFolder = null;
	private boolean readySent = false;

	private ConcurrentHashMap<String, FileBuffer> buffers = new ConcurrentHashMap<>();

	// referinta la GUI
	private transient ReceiverGui gui;

	@Override
	protected void setup() {

		appendToGui("ReceiverAgent " + getLocalName() + " pornit.");
		appendToGui("ATENTIE: Selecteaza un folder de destinatie inainte de a primi fisiere.");

		// PORNESTE GUI-UL RECEIVERULUI
		gui = new ReceiverGui(this);

		registerService("file-receiver", "JADE-file-receiver");

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

		sendReadyMessage();
	}

	@Override
	protected void takeDown() {
		try {
			DFService.deregister(this);
		} catch (Exception ignored) {
		}

		appendToGui(getLocalName() + " se opreste.");

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

	private void sendReadyMessage() {
		if (readySent) {
			return;
		}

		AID sender = searchService("file-sender");
		if (sender == null) {
			appendToGui("Nu am gasit inca SenderAgent in DF pentru a trimite receiver-ready.");
			return;
		}

		ACLMessage ready = new ACLMessage(ACLMessage.INFORM);
		ready.setOntology("receiver-ready");
		ready.setContent(getAID().getName());
		ready.addReceiver(sender);
		send(ready);

		readySent = true;
	}

	private AID searchService(String type) {
		DFAgentDescription template = new DFAgentDescription();
		ServiceDescription sd = new ServiceDescription();
		sd.setType(type);
		template.addServices(sd);

		try {
			DFAgentDescription[] result = DFService.search(this, template);
			if (result != null && result.length > 0) {
				return result[0].getName();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	private void handleIncomingMessage(ACLMessage msg) {
		String ontology = msg.getOntology();
		int perf = msg.getPerformative();

		if (perf == ACLMessage.REQUEST && "file-offer".equals(ontology)) {
			handleFileOffer(msg);
		} else if (perf == ACLMessage.INFORM && "file-chunk-to-write".equals(ontology)) {
			handleChunk(msg);
		} else if (perf == ACLMessage.REQUEST && "shutdown".equals(ontology)) {
			doDelete();
		} else {
			appendToGui("Mesaj necunoscut: " + msg);
		}
	}

	private void handleFileOffer(ACLMessage msg) {
		String content = msg.getContent(); // FILENAME:xxx;SIZE:yyy
		String[] parts = content.split(";");
		String filename = parts[0].split(":")[1];
		long size = Long.parseLong(parts[1].split(":")[1]);

		ACLMessage reply = msg.createReply();

		// 0. Daca nu exista folder de destinatie -> REFUZAM
		if (destinationFolder == null) {
			appendToGui("FISIERUL \"" + filename + "\" A FOST RESPINS. SETEAZA DESTINATIA!");

			reply.setPerformative(ACLMessage.REFUSE);
			reply.setOntology("file-offer");
			reply.setContent("NO_DESTINATION_FOLDER");
			send(reply);
			return;
		}

		File dest = new File(destinationFolder, filename);

		// 1. Daca fisierul exista deja
		if (dest.exists()) {
			reply.setPerformative(ACLMessage.INFORM);
			reply.setOntology("file-exists");
			reply.setContent(filename);
			send(reply);

			appendToGui("Fisierul \"" + filename + "\" exista deja. Am trimis file-exists catre SenderAgent.");
			return;
		}

		// 2. Cerem confirmarea utilizatorului
		boolean accept = gui.confirmReception(filename, size);

		if (!accept) {
			reply.setPerformative(ACLMessage.REFUSE);
			reply.setOntology("file-offer");
			reply.setContent("REFUZAT");
			send(reply);

			appendToGui("Utilizatorul a refuzat primirea fisierului: " + filename);
			return;
		}

		// 3. Utilizatorul accepta
		reply.setPerformative(ACLMessage.AGREE);
		reply.setOntology("file-offer");
		reply.setContent("ACCEPT");
		send(reply);

		appendToGui("Utilizatorul a acceptat primirea fisierului: " + filename);
	}

	private void handleChunk(ACLMessage msg) {
		String filename = msg.getUserDefinedParameter("filename");
		int index = Integer.parseInt(msg.getUserDefinedParameter("chunk-index"));
		int total = Integer.parseInt(msg.getUserDefinedParameter("total-chunks"));

		byte[] data = Base64.getDecoder().decode(msg.getContent());

		FileBuffer buffer = buffers.computeIfAbsent(filename, f -> new FileBuffer(total));
		buffer.storeChunk(index, data);

		appendToGui("Chunk " + (index + 1) + " din " + total + " primit si stocat pentru fisierul " + filename + ".");

		if (buffer.isComplete()) {
			writeFileToDisk(filename, buffer);
			buffers.remove(filename);
		}
	}

	private void writeFileToDisk(String filename, FileBuffer buffer) {
		File out = new File(destinationFolder, filename);

		try (FileOutputStream fos = new FileOutputStream(out)) {
			for (byte[] chunk : buffer.getChunks()) {
				fos.write(chunk);
			}
			fos.flush();

			appendToGui("Fisier complet scris pe disk: " + filename);

			ACLMessage done = new ACLMessage(ACLMessage.INFORM);
			done.setOntology("file-write-complete");
			done.setContent(filename);

			AID fileManager = searchService("file-manager");
			if (fileManager != null) {
				done.addReceiver(fileManager);
				send(done);
			}

		} catch (IOException e) {
			e.printStackTrace();
			appendToGui("Eroare la scrierea fisierului " + filename + ": " + e.getMessage());
		}
	}

	public void setDestinationFolder(File folder) {
		if (folder != null && folder.isDirectory()) {
			this.destinationFolder = folder.getAbsolutePath();
			appendToGui("Folder destinatie setat: " + this.destinationFolder);
		} else {
			appendToGui("Folder invalid primit in setDestinationFolder().");
		}
	}

	public void askAiForHelp() {
		try {
			URL url = new URL("http://localhost:8000/assistant/explain");
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("POST");
			conn.setDoOutput(true);
			conn.setRequestProperty("Content-Type", "application/json");

			String safeDestination = (destinationFolder == null) ? "nesetata" : destinationFolder.replace("\\", "\\\\");

			String json = "{" + "\"sender\":\"ReceiverAgent\"," + "\"receiver\":\"ReceiverAgent\","
					+ "\"filename\":\"necunoscut\"," + "\"size\":0," + "\"status\":\"receiver-state\"," + "\"logs\":["
					+ "\"ReceiverAgent ruleaza si gestioneaza transferuri.\"," + "\"Destinatie: " + safeDestination
					+ "\"" + "]" + "}";

			OutputStream os = conn.getOutputStream();
			os.write(json.getBytes("UTF-8"));
			os.flush();
			os.close();

			int code = conn.getResponseCode();
			if (code == 200) {
				BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
				StringBuilder sb = new StringBuilder();
				String line;
				while ((line = br.readLine()) != null) {
					sb.append(line);
				}
				br.close();
				appendToGui("Raspuns AI: " + sb.toString());
			} else {
				appendToGui("AI server error, code=" + code);
			}

		} catch (Exception e) {
			appendToGui("Nu am putut contacta serverul AI: " + e.getMessage());
		}
	}

	// metoda pentru a scrie in UI (si in consola)
	public void appendToGui(String text) {
		System.out.println(text);

		if (gui != null) {
			SwingUtilities.invokeLater(() -> gui.appendInfo(text));
		}
	}

	private static class FileBuffer {
		private final byte[][] chunks;
		private int received = 0;

		public FileBuffer(int total) {
			chunks = new byte[total][];
		}

		public synchronized void storeChunk(int index, byte[] data) {
			if (chunks[index] == null) {
				chunks[index] = data;
				received++;
			}
		}

		public boolean isComplete() {
			return received == chunks.length;
		}

		public byte[][] getChunks() {
			return chunks;
		}
	}
}
