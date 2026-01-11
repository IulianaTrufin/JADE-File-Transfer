package agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;

public class FileManagerAgent extends Agent {

	@Override
	protected void setup() {
		System.out.println(getLocalName() + " pornit.");

		registerService("file-manager", "JADE-file-manager");

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

	@Override
	protected void takeDown() {
		try {
			DFService.deregister(this);
		} catch (Exception ignored) {
		}
		System.out.println(getLocalName() + " se opreste.");
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

		if (perf == ACLMessage.INFORM && "file-chunk".equals(ontology)) {
			forwardChunkToReceiver(msg);
		} else if (perf == ACLMessage.INFORM && "file-write-complete".equals(ontology)) {
			System.out.println("Receiver a raportat finalizarea scrierii: " + msg.getContent());
		} else if (perf == ACLMessage.REQUEST && "shutdown".equals(ontology)) {
			doDelete();
		} else {
			System.out.println(getLocalName() + " a primit: " + msg);
		}
	}

	private void forwardChunkToReceiver(ACLMessage msg) {
		AID receiver = searchService("file-receiver");
		if (receiver == null) {
			System.out.println("Nu gasesc receiver pentru a trimite chunk-ul");
			return;
		}

		String filename = msg.getUserDefinedParameter("filename");
		String chunkIndex = msg.getUserDefinedParameter("chunk-index");
		String totalChunks = msg.getUserDefinedParameter("total-chunks");
		String base64Data = msg.getContent();

		ACLMessage forward = new ACLMessage(ACLMessage.INFORM);
		forward.addReceiver(receiver);
		forward.setConversationId("file-transfer-chunks");
		forward.setOntology("file-chunk-to-write");

		forward.addUserDefinedParameter("filename", filename);
		forward.addUserDefinedParameter("chunk-index", chunkIndex);
		forward.addUserDefinedParameter("total-chunks", totalChunks);
		forward.setContent(base64Data);

		send(forward);
	}
}
