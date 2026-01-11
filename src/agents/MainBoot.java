package agents;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;

public class MainBoot {
	public static void main(String[] args) {
		try {
			Runtime rt = Runtime.instance();
			Profile p = new ProfileImpl();
			p.setParameter(Profile.GUI, "true"); // porneste Jade GUI (RMA)

			ContainerController mainContainer = rt.createMainContainer(p);

			AgentController receiver = mainContainer.createNewAgent("ReceiverAgent", "agents.ReceiverAgent", null);
			receiver.start();

			AgentController fileManager = mainContainer.createNewAgent("FileManagerAgent", "agents.FileManagerAgent",
					null);
			fileManager.start();

			AgentController manager = mainContainer.createNewAgent("ManagerAgent", "agents.ManagerAgent", null);
			manager.start();
			AgentController sender = mainContainer.createNewAgent("SenderAgent", "agents.SenderAgent", null);
			sender.start();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
