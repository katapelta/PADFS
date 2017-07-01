package jobManagement;

import system.containers.Server;

public interface ForwardableInterface {
	
	public boolean actAsAProxy();
	
	/**
	 * forward this operation to another server
	 * @return
	 */
	public boolean forward(Server s);
}
